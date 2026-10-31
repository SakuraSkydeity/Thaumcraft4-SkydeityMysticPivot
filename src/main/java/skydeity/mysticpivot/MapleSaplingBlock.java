package skydeity.mysticpivot;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 枫树树苗（第一百六十七批）。
 *
 * <h2>为什么不直接 extends {@code SaplingBlock}</h2>
 * <p>原版 {@code SaplingBlock} 的构造器只收一个 {@code TreeGrower}——那个类内部持
 * {@code ResourceKey<ConfiguredFeature>}，逼着把树注册进动态注册表才能生长。
 * 本 mod 的树形在 {@link MapleTreeGrower} 里现构现种（见那里的说明），所以这里
 * <b>extends {@link BushBlock} 并自行实现 {@link BonemealableBlock}</b>，
 * 把原版树苗的随机 tick / 骨粉 / 两段式 stage 逻辑照搬一遍，只把「长树」那一句换成
 * 自己的生长器。{@code BushBlock} 顺带提供了「只能种在泥土/草方块上、底下被挖就掉」
 * 的原版植株行为。</p>
 *
 * <h2>两段式 stage（照原版）</h2>
 * <p>{@code STAGE=0 → 1} 先走一次 tick，下一次才真正长树——原版这么做是为了让
 * 「骨粉催熟」有反应间隔；本实现保持同样节奏（{@code random.nextInt(7) == 0}
 * 与光照条件也照原版：头顶亮度 ≥ 9）。</p>
 */
public class MapleSaplingBlock extends BushBlock implements BonemealableBlock {

    public static final MapCodec<MapleSaplingBlock> CODEC = simpleCodec(MapleSaplingBlock::new);

    public static final IntegerProperty STAGE = BlockStateProperties.STAGE;

    protected static final float AABB_OFFSET = 6.0F;
    protected static final VoxelShape SHAPE = Block.box(2.0, 0.0, 2.0, 14.0, 12.0, 14.0);

    public MapleSaplingBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(STAGE, Integer.valueOf(0)));
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.isAreaLoaded(pos, 1)) {
            return; // NeoForge：别为了查邻居光照把未加载的区块拉进来
        }
        if (level.getMaxLocalRawBrightness(pos.above()) >= 9 && random.nextInt(7) == 0) {
            this.advanceTree(level, pos, state, random);
        }
    }

    /** 两段式：stage 0 先翻到 1，stage 1 才长树。 */
    public void advanceTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (state.getValue(STAGE) == 0) {
            level.setBlock(pos, state.cycle(STAGE), 4);
            return;
        }
        // ★ 照原版 SaplingBlock#advanceTree：长树之前先把树苗那一格换成流体状态
        //   （旱地上就是空气）。不换的话主干最底下那一格被树苗占着，
        //   TrunkPlacer#placeLog 的 isFree（只认空气与叶子）过不了，
        //   树干底部缺一格、叶子挂在缺口上。
        BlockState cleared = level.getFluidState(pos).createLegacyBlock();
        level.setBlock(pos, cleared, 4);
        if (!MapleTreeGrower.growMaple(level, pos, random)) {
            // 长不下（头顶被挡 / 空间不够）：树苗原地留任，玩家无损耗
            level.setBlock(pos, state, 4);
        }
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return (double) level.random.nextFloat() < 0.45;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        this.advanceTree(level, pos, state, random);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE);
    }
}
