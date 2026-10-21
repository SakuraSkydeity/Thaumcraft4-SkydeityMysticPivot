package skydeity.mysticpivot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 玄枢翎凰焚天镐（注册 id 仍是 {@code xuanshu_pickaxe}）—— 第七十四批扩成「三用神器」，
 * 第七十五批把「锄」换成「铲」。
 *
 * 用户原话：
 * <pre>
 * 6.给玄枢翎凰焚天镐添加即使斧头也是锄 添加效果 shift右键切换模式
 *   单个方块挖掘 或者挖掘隧道 就是面朝南挖掘就是往前挖16格 自带时运16
 * （本批）镐子不要锄头 只要斧头铲子和镐子的效果即可。切换只显示隧道/单方块
 * </pre>
 *
 * <h2>一、既镐、既斧、既铲（三合一）</h2>
 * <p>不碰 {@code DataComponents.TOOL}（那需要注册表里现成的 {@code HolderSet}，且
 * {@code DiggerItem} 的构造会把父类塞进去的单标签规则覆盖掉），改为覆写两个查询方法：
 * {@link #isCorrectToolForDrops} 把<b>镐 / 斧 / 铲</b>三个方块标签一律判为「使得对」，
 * {@link #getDestroySpeed} 对这三类方块直接给 {@code Tier} 的掘速（40.0）。</p>
 *
 * <p>三样都只到「挖得动、掉得快」为止 —— 它不会替玩家剥皮、不会踩出小径，也不会翻出耕地：
 * 那些是斧、铲、锄物品各自的专属逻辑，本镐走的是自己的 {@code Tier}。
 * 研究书里那一页写的是同一句话。</p>
 *
 * <h2>二、两种掘法（潜行右键切换）</h2>
 * <ul>
 *   <li><b>单方块</b>（默认）：寻常挖法，挖哪个掉哪个；</li>
 *   <li><b>隧道</b>：挖掉一个方块之后，镐锋不停，沿着玩家<b>面朝的水平方向</b>
 *       再凿 {@link #TUNNEL_LENGTH} 格 —— 面朝南就是往南挖出一条十六格长的洞。</li>
 * </ul>
 *
 * <p>模式记在 {@code DataComponents.CUSTOM_DATA} 里（就是一个布尔），跟着物品走，
 * 换手、进箱子都不会丢。切换时给玩家一条动作栏提示，**只报模式名**
 * （{@code ...tunnel_dig.on} = 「隧道」/ {@code .off} = 「单方块」）。</p>
 *
 * <h3>隧道为什么要防重入</h3>
 * <p>批量开挖用的是 {@link ServerPlayerGameMode#destroyBlock(BlockPos)} —— 这是「玩家挖方块」的
 * 正规入口，掉落、耐久、统计、成就一项不落（也因此<b>时运照常生效</b>）。但它内部会回调
 * {@code ItemStack#mineBlock}，也就是回到本方法自己身上。若不加闸，第一镐就会无限延伸。
 * 处理办法是一个静态闸门 {@link #diggingTunnel}：批量期间把它立起来，期间所有回调直接返回。</p>
 *
 * <p>开挖途中遇到<b>箱子 / 机器这类方块实体</b>，以及<b>基岩、屏障这类挖不动的方块</b>，
 * 一律停下（连带把整条隧道截断），免得一镐下去把家拆了；空气与流体则直接穿过去继续凿。</p>
 *
 * <h2>三、自带时运 XVI（第一百零二批改成「真的是一件附魔物品」）</h2>
 * <p>附魔在 1.21 是<b>数据包注册项</b>，注册物品那一刻拿不到 {@code Holder}，
 * 所以不能在 {@code Item.Properties} 里写死。上一版的做法是在 {@code inventoryTick} 里
 * 每 tick 检查一遍、缺则补——用户明确否了：「不要像现在这样 检测没附魔就上时运，
 * 我希望就是正常附魔物品」。</p>
 *
 * <p>现在改在 <b>{@link #onCraftedPostProcess(ItemStack, Level)}</b> 里补：
 * 这是「成品被造出来的那一刻」的统一出口——合成台走 {@code ItemStack#onCraftedBy}
 * 默认会调它，本体注魔基质（{@code InfusionMatrixBlockEntity}）与奥术工作台也都调
 * {@code onCraftedBy}，本 mod 的注魔升级（{@code bagua_pickaxe → _2}）同样覆盖到。
 * 于是<b>东西一到手就已经带 16 级时运</b>，之后就是一件普普通通的附魔物品：
 * 能被砂轮磨掉、能被铁砧加深、工具提示里正常显示，不再有每 tick 的补写。</p>
 *
 * <p>创造物品栏里那一枚由 {@link ModItems#heavenPickaxe} 现造（页签回调带
 * {@code HolderLookup.Provider}，能拿到附魔 holder）。</p>
 *
 * <p>⚠ 附带效果：带附魔组件的物品会有附魔光效 —— 一把神器发光，倒是合衬。</p>
 */
public class XuanshuHeavenPickaxeItem extends PickaxeItem {

    /** 自带的时运等级（用户点名的 16）。 */
    public static final int FORTUNE_LEVEL = 16;

    /** 隧道掘的长度：从被挖掉的那一格起，朝面向再凿 16 格。 */
    public static final int TUNNEL_LENGTH = 16;

    /** 自定义数据里记「当前是不是隧道掘」的那把钥匙。 */
    private static final String TUNNEL_KEY = "TunnelDig";

    /**
     * 重入闸门：批量开挖期间立起来，期间任何 {@code mineBlock} 回调都不再拉新的隧道。
     *
     * <p>破坏方块只发生在服务端主线程，一个静态布尔足够；写成 {@code ThreadLocal} 只是
     * 为了万一有附属在别的线程碰它。</p>
     */
    private static final ThreadLocal<Boolean> DIGGING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    public XuanshuHeavenPickaxeItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    // ------------------------------------------------------------------
    // 模式：单方掘 / 隧道掘
    // ------------------------------------------------------------------

    /** 这柄镐现在是不是「隧道掘」。 */
    public static boolean isTunnelDig(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getBoolean(TUNNEL_KEY);
    }

    private static void setTunnelDig(ItemStack stack, boolean on) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(TUNNEL_KEY, on));
    }

    /**
     * 潜行右键：两种掘法之间来回切。不潜行的右键<b>不接管</b>（返回 PASS），
     * 该给方块、该给法杖的还是它们。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        if (!level.isClientSide) {
            boolean on = !isTunnelDig(stack);
            setTunnelDig(stack, on);
            player.displayClientMessage(Component.translatable(on
                    ? "message.skydeitymysticpivot.tunnel_dig.on"
                    : "message.skydeitymysticpivot.tunnel_dig.off"), true);
        }
        return InteractionResultHolder.success(stack);
    }

    // ------------------------------------------------------------------
    // 既镐、既斧、既铲
    // ------------------------------------------------------------------

    /**
     * 镐 / 斧 / 铲三类方块标签 —— 这三类一律算「本镐使得对」。
     *
     * <p>★ 第七十五批把「锄」换成了「铲」：用户原话「镐子不要锄头 只要斧头铲子和镐子的效果即可」。
     * 于是上一批那套「右键翻耕地」的 {@code useOn}（借 {@code HoeItem.changeIntoState}）整段删掉，
     * 标签改认 {@link BlockTags#MINEABLE_WITH_SHOVEL}（土、沙、砂砾、雪、粘土这些）。</p>
     */
    private static boolean isThreeWayBlock(BlockState state) {
        return state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                || state.is(BlockTags.MINEABLE_WITH_AXE)
                || state.is(BlockTags.MINEABLE_WITH_SHOVEL);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return isThreeWayBlock(state) || super.isCorrectToolForDrops(stack, state);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return isThreeWayBlock(state) ? getTier().getSpeed() : super.getDestroySpeed(stack, state);
    }

    // ------------------------------------------------------------------
    // 隧道掘
    // ------------------------------------------------------------------

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entity) {
        boolean result = super.mineBlock(stack, level, state, pos, entity);
        if (level instanceof ServerLevel serverLevel
                && entity instanceof ServerPlayer player
                && !DIGGING.get()
                && isTunnelDig(stack)) {
            digTunnel(serverLevel, player, pos);
        }
        return result;
    }

    /**
     * 从 {@code origin} 起，朝玩家面朝的水平方向凿 {@link #TUNNEL_LENGTH} 格。
     *
     * <p>每一格都走 {@link ServerPlayerGameMode#destroyBlock(BlockPos)}：掉落、耐久、统计
     * 与本体一致，且用的是玩家主手那柄镐 ⇒ <b>时运 XVI 照常结算</b>。</p>
     */
    private static void digTunnel(ServerLevel level, ServerPlayer player, BlockPos origin) {
        Direction facing = player.getDirection();          // 只取水平四向：面朝南就是南
        DIGGING.set(Boolean.TRUE);
        try {
            BlockPos cursor = origin;
            for (int i = 0; i < TUNNEL_LENGTH; i++) {
                cursor = cursor.relative(facing);
                BlockState target = level.getBlockState(cursor);
                if (target.isAir() || !target.getFluidState().isEmpty()) {
                    continue;                              // 空档与流体：穿过去继续凿
                }
                if (target.getDestroySpeed(level, cursor) < 0) {
                    break;                                 // 基岩、屏障：停下
                }
                if (target.hasBlockEntity()) {
                    break;                                 // 箱子、机器：停下，别替玩家拆家
                }
                player.gameMode.destroyBlock(cursor);
            }
        } finally {
            DIGGING.set(Boolean.FALSE);
        }
    }

    // ------------------------------------------------------------------
    // 自带时运 XVI
    // ------------------------------------------------------------------

    /**
     * 成品落地那一刻补上 16 级时运——合成台 / 奥术工作台 / 本体注魔基质都走这个出口
     * （{@code ItemStack#onCraftedBy} 默认会转调 {@link #onCraftedPostProcess}）。
     *
     * <p>只在「还没有 16 级时运」时写一次；已经有（砂轮磨过就没了，铁砧加深更多级则保留更高的）
     * 就不动。这里<b>没有</b>每 tick 的补写——它就是一件普通附魔物品。</p>
     */
    @Override
    public void onCraftedPostProcess(ItemStack stack, Level level) {
        super.onCraftedPostProcess(stack, level);
        applyFortune(level, stack);
    }

    /**
     * 把 16 级时运写进这件物品（不到 16 才写）。
     *
     * <p>创造物品栏那条路要从 {@code CreativeModeTab.ItemDisplayParameters#holders()} 拿到注册表，
     * 所以这个方法是 {@code public static} 的，供 {@link ModItems#heavenPickaxe} 复用。</p>
     */
    public static void applyFortune(Level level, ItemStack stack) {
        applyFortune(level.registryAccess(), stack);
    }

    /** 同上，但直接吃 {@code HolderLookup.Provider}（创造页签没有 Level）。 */
    public static void applyFortune(net.minecraft.core.HolderLookup.Provider registries, ItemStack stack) {
        Holder<Enchantment> fortune = registries.lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.FORTUNE);
        ItemEnchantments current = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (current.getLevel(fortune) >= FORTUNE_LEVEL) {
            return;
        }
        EnchantmentHelper.updateEnchantments(stack, mutable -> mutable.set(fortune, FORTUNE_LEVEL));
    }
}
