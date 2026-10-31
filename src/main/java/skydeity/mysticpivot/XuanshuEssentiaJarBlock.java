package skydeity.mysticpivot;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * 玄枢源质罐（第一百九十三批新增）：<b>一只装得下所有要素的罐子</b>。
 *
 * <h2>外观</h2>
 * <p>模型与贴图<b>直接用本体的防护罐</b>（{@code thaumcraft:block/warded_jar}，
 * 十格宽、十二格高的玻璃罐 + 罐口），所以方块状态只有一个变体、没有朝向 ——
 * 用户只要「模型和贴图全用 tc4 的源质罐子」，罐身上那张要素标签这一版不要，
 * 罐里的液体一律鎏金（见 {@link XuanshuEssentiaJarRenderer}）。</p>
 *
 * <h2>玩法</h2>
 * <ul>
 *   <li>四原质 / 复合要素<b>各存各的</b>，一种最多 2048 份（
 *       {@link XuanshuEssentiaJarBlockEntity#CAPACITY}），一罐最多 32 种；</li>
 *   <li>四个侧面与上下都通要素：炼金炉、原质管、任何「往罐里灌」的东西都能用；</li>
 *   <li>注魔矩阵与玄枢基座<b>会自动从它抽</b>（它实现了 {@code EssentiaSource}），
 *       抽的时候那束「源 → 台」的丝线动画照旧；</li>
 *   <li>潜行 + 空手右键：倒空（和本体原质罐一个手感）；</li>
 *   <li>挖掉时罐子<b>带着里面的要素一起掉</b>（物品组件 {@code jar_essentia} + 战利品表
 *       {@code copy_components}），放回去原样还在；创造模式拆下来也带着。</li>
 * </ul>
 */
public class XuanshuEssentiaJarBlock extends BaseEntityBlock {

    /** 方块编解码器（{@link BaseEntityBlock} 要求子类自己给一个；本方块没有状态，直接套构造器）。 */
    public static final MapCodec<XuanshuEssentiaJarBlock> CODEC =
            simpleCodec(XuanshuEssentiaJarBlock::new);

    /** 罐身轮廓：与本体防护罐同一套尺寸（3~13 宽、0~12 高）。 */
    private static final VoxelShape OUTLINE = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 12.0D, 13.0D);

    public XuanshuEssentiaJarBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new XuanshuEssentiaJarBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE;
    }

    /** 碰撞仍是整格：与本体防护罐一致，走上去不会掉进罐口。 */
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                        CollisionContext context) {
        return Shapes.block();
    }

    /** 罐身是非不透明方块（玻璃），自阴影关掉才不会一块一块发暗。 */
    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof XuanshuEssentiaJarBlockEntity jar
                ? jar.comparatorOutput() : 0;
    }

    /**
     * 潜行 + 空手右键：倒空。
     *
     * <p>与本体原质罐同一个手感（{@code useWithoutItem} + {@code isShiftKeyDown}）。
     * 不潜行就什么都不做 —— 空手右键普通罐也是这个反应，玩家不会误触。</p>
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!player.isShiftKeyDown()
                || !(level.getBlockEntity(pos) instanceof XuanshuEssentiaJarBlockEntity jar)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            jar.clearContents();
            level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.7F, 0.9F);
            player.displayClientMessage(
                    Component.translatable("message.skydeitymysticpivot.essentia_jar.cleared"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ------------------------------------------------------------------
    // 内容随物品走
    // ------------------------------------------------------------------

    /**
     * 创造模式拆罐子：补一次「正常掉落」。
     *
     * <p>★ 这一条是第一百九十四批补的，也是「挖掉之后罐子是空的」的病根之一：
     * 服务端 {@code ServerPlayerGameMode#destroyBlock} 在创造模式下走的是
     * {@code if (isCreative()) { removeBlock(...); return true; }}，
     * <b>整条 {@code playerDestroy} 都不会被调用</b>，所以自己写 {@code playerDestroy}
     * 的方块，在创造模式里一颗掉落都没有。想「和本体防护罐一样」就得在这里补一刀 ——
     * 本体 {@code WardedJarBlock} 正是这么干的。</p>
     *
     * <p>内容不在这里写：掉什么、带不带内容，全交给战利品表
     * （{@code copy_components} 从方块实体复制要素表，见 {@code loot_table/blocks/xuanshu_essentia_jar.json}）。</p>
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (player.isCreative() && level instanceof ServerLevel server) {
            Block.dropResources(state, server, pos, level.getBlockEntity(pos), player,
                    player.getMainHandItem());
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** 中键取方块：同样带着内容（{@code saveToItem} 会把要素组件一起收走）。 */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level,
                                       BlockPos pos, Player player) {
        ItemStack stack = new ItemStack(this);
        if (level.getBlockEntity(pos) instanceof XuanshuEssentiaJarBlockEntity jar) {
            jar.saveToItem(stack, player.level().registryAccess());
        }
        return stack;
    }

    /**
     * 放下时把物品上的要素组件读回来。
     *
     * <p>两条路都留着：新路是物品组件（{@code skydeitymysticpivot:jar_essentia}），
     * 旧路是原版放置时自动灌进来的 {@code BLOCK_ENTITY_DATA}。方块实体里
     * {@code applyImplicitComponents} 只在自己那个组件<b>存在</b>时才动内容，
     * 所以先灌后读、先读后灌都不会互相清空。</p>
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof XuanshuEssentiaJarBlockEntity jar) {
            jar.applyComponentsFromItemStack(stack);
        }
    }
}
