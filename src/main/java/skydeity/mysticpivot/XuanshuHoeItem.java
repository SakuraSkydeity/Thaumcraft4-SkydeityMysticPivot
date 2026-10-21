package skydeity.mysticpivot;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 玄枢玄冥镇海锄（注册 id 仍是 {@code xuanshu_hoe}）—— 第七十六批做成**农事神器**。
 *
 * 用户原话：
 * <pre>
 * 2.锄头改为可以自动锄9x9的耕地，shift右键在方块上面放置一格水，
 *   手持时加速9x9作物生长，直接提高生长进度，强制生长无视作物生长要求
 * </pre>
 *
 * <h2>一、一次九宫格耕地（普通右键）</h2>
 * <p>右键任意方块，以它为中心把水平 {@link #AREA_HALF}{@code ×2+1 = 9×9} 范围内的
 * <b>所有可耕地</b>一次翻成耕地 —— 草方块、泥土、砂土、土径、缠根泥土，与本体锄头
 * 认的那五种完全一致。缠根泥土照本体规矩翻成普通泥土并抖出一份垂根，其余一律翻成耕地。</p>
 *
 * <p>只翻「上方是空气或可替换方块」的那些（与本体 {@code onlyIfAirAbove} 同一把尺子），
 * 免得把墙里的土、别人造景的草皮一块铲了。一次九宫格<b>只扣 1 点耐久</b>，
 * 而不是 81 点 —— 它是神器，不是 81 把锄头。</p>
 *
 * <h2>二、潜行右键放一格水</h2>
 * <p>潜行右键任意方块，在它<b>正上方一格</b>放一个水源方块（前提是那一格可替换）。
 * 农田要水，这是给九宫格耕地收尾用的：先把地翻平，再往中间点一格水。</p>
 *
 * <h2>三、手持催熟（九宫格）</h2>
 * <p>主手<b>选中</b>它时，每 {@link #GROW_INTERVAL} tick（默认 1 秒）扫一遍玩家周围
 * {@link #AREA_HALF}{@code ×2+1} 的水平范围、脚下两层到头顶一层，把里面所有没长成的作物
 * <b>直接推一级生长</b>：</p>
 * <ul>
 *   <li>{@link CropBlock}（小麦、胡萝卜、马铃薯、甜菜、下界疣一类）直接把 age +1；</li>
 *   <li>其余任何实现 {@link BonemealableBlock} 的（南瓜/西瓜茎、甘蔗、竹、树苗……）
 *       调一次 {@code performBonemeal}，等于替它撒了一把骨粉。</li>
 * </ul>
 *
 * <p>★ 这里走的是「直接改方块状态」与「骨粉接口」，<b>完全不查光照、水分、耕地条件</b> ——
 * 也就是说地里没水、洞里没光，照样长。用户要的正是这句「强制生长无视作物生长要求」。</p>
 *
 * <p>⚠ 代价：站在自家麦田里会一直听到方块更新、看到作物一格一格往上蹿；树苗也照催，
 * 所以别举着它站苗圃里发呆。这是设计如此，不是 bug。</p>
 */
public class XuanshuHoeItem extends HoeItem {

    /** 九宫格：水平半径 4 ⇒ 9×9。翻地与催熟共用这一个数。 */
    public static final int AREA_HALF = 4;

    /** 催熟的垂直范围：玩家脚下方块往下 2 层、往上 1 层。 */
    public static final int GROW_DOWN = 2;

    public static final int GROW_UP = 1;

    /** 催熟间隔（tick）。20 = 每秒推一次生长。 */
    public static final int GROW_INTERVAL = 20;

    /**
     * 可耕地表：方块 → 翻完之后的样子，与本体锄头认的那五种一致。
     *
     * <p>★ 不查 {@code HoeItem#TILLABLES} —— 那把表在 NeoForge 里标了 {@code @Deprecated}
     * （编译会报警告），而且它存的是「谓词 + 消费者」，这里只需要一张「换什么」的表。
     * 缠根泥土翻成普通泥土，垂根在 {@link #tillArea} 里单独掉。</p>
     */
    private static final Map<Block, BlockState> TILLABLE = Map.of(
            Blocks.GRASS_BLOCK, Blocks.FARMLAND.defaultBlockState(),
            Blocks.DIRT, Blocks.FARMLAND.defaultBlockState(),
            Blocks.COARSE_DIRT, Blocks.FARMLAND.defaultBlockState(),
            Blocks.DIRT_PATH, Blocks.FARMLAND.defaultBlockState(),
            Blocks.ROOTED_DIRT, Blocks.DIRT.defaultBlockState());

    public XuanshuHoeItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    // ------------------------------------------------------------------
    // 右键：9×9 翻地 / 潜行放水
    // ------------------------------------------------------------------

    /**
     * 潜行右键 = 在点击方块上方放一格水源；不潜行右键 = 一次翻 9×9 的耕地。
     *
     * <p>两件事都不潜行共用同一次右键，所以潜行是唯一的开关 —— 与焚天镐「潜行右键切模式」
     * 是同一套手感。</p>
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        Level level = context.getLevel();

        if (player.isShiftKeyDown()) {
            return placeWater(level, context.getClickedPos());
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }
        int tilled = tillArea(serverLevel, context.getClickedPos());
        if (tilled == 0) {
            return InteractionResult.PASS;          // 一格都翻不动，就把这一下让给别人
        }
        ItemStack stack = context.getItemInHand();
        stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(context.getHand()));
        return InteractionResult.CONSUME;
    }

    /**
     * 以 {@code center} 为中心翻一片 {@code 9×9}。
     *
     * @return 真的翻了几格（0 = 这一下不该被消耗）
     */
    private static int tillArea(ServerLevel level, BlockPos center) {
        BlockPos min = center.offset(-AREA_HALF, 0, -AREA_HALF);
        BlockPos max = center.offset(AREA_HALF, 0, AREA_HALF);
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            BlockState tilled = TILLABLE.get(state.getBlock());
            if (tilled == null || !isAirAbove(level, pos)) {
                continue;
            }
            level.setBlock(pos, tilled, 3);
            if (state.is(Blocks.ROOTED_DIRT)) {
                // 本体规矩：缠根泥土翻成普通泥土，并把缠着的垂根抖出来
                Block.popResource(level, pos, new ItemStack(Items.HANGING_ROOTS));
            }
            count++;
        }
        return count;
    }

    /** 与本体 {@code onlyIfAirAbove} 同一把尺子：上方得是空的（草、雪这类可替换的也算）。 */
    private static boolean isAirAbove(ServerLevel level, BlockPos pos) {
        BlockState above = level.getBlockState(pos.above());
        return above.isAir() || above.canBeReplaced();
    }

    /** 在 {@code pos} 的正上方一格放一个水源方块。 */
    private static InteractionResult placeWater(Level level, BlockPos pos) {
        BlockPos above = pos.above();
        if (!level.getBlockState(above).canBeReplaced()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            level.setBlockAndUpdate(above, Blocks.WATER.defaultBlockState());
            level.playSound(null, above, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ------------------------------------------------------------------
    // 手持催熟
    // ------------------------------------------------------------------

    /**
     * 只有<b>主手选中</b>它时才催熟（{@code isSelected}）—— 躺在背包里、拿在副手都不算「手持」。
     *
     * <p>每 {@link #GROW_INTERVAL} tick 走一次，靠 {@code player.tickCount} 取模对表，
     * 免得再挂一个计时器。<b>催熟不改任何生长条件</b>，直接把作物状态往前推。</p>
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        super.inventoryTick(stack, level, entity, slotId, isSelected);
        if (!isSelected || level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!(entity instanceof Player player) || player.tickCount % GROW_INTERVAL != 0) {
            return;
        }
        growAround(serverLevel, player);
    }

    /**
     * 催熟玩家周围九宫格里的作物：{@link CropBlock} 直接 age +1，其余 {@link BonemealableBlock}
     * 走一次骨粉。两者都不看光照与水分。
     */
    private static void growAround(ServerLevel level, Player player) {
        RandomSource random = level.getRandom();
        BlockPos center = player.blockPosition();
        BlockPos min = center.offset(-AREA_HALF, -GROW_DOWN, -AREA_HALF);
        BlockPos max = center.offset(AREA_HALF, GROW_UP, AREA_HALF);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState state = level.getBlockState(pos);
            Block block = state.getBlock();
            if (block instanceof CropBlock crop) {
                if (!crop.isMaxAge(state)) {
                    level.setBlock(pos, crop.getStateForAge(crop.getAge(state) + 1), 2);
                }
                continue;
            }
            if (block instanceof BonemealableBlock bonemealable
                    && bonemealable.isValidBonemealTarget(level, pos, state)) {
                bonemealable.performBonemeal(level, random, pos, state);
            }
        }
    }

    /**
     * 锄头自己也认「锄类方块」—— 本体的 {@code HoeItem} 已经通过 {@code DiggerItem} 把
     * {@code MINEABLE_WITH_HOE} 那条标签挂上了，这里不用再覆写掘速与落物判定。
     */
}
