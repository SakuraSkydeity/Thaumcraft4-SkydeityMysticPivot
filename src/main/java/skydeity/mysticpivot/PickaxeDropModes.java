package skydeity.mysticpivot;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.Locale;

/**
 * 两柄镐（八卦开山辟地镐 / 玄枢翎凰焚天镐）共用的<b>「右键掉落模式」</b>——
 * 第一百六十批新增，用户原话：
 * <pre>
 * 给玄枢镐和八卦镐添加3个右键模式 即正常 精准采集 自动熔炼
 * （这个用最少的代码最不报错的方式 能熔炼的都要）
 * </pre>
 *
 * <h2>一、三个模式</h2>
 * <ul>
 *   <li><b>正常</b>（{@link Mode#NORMAL}）：原版掉落，本类完全不插手；</li>
 *   <li><b>精准采集</b>（{@link Mode#SILK}）：一镐下去掉<b>方块本身</b>——
 *       石头掉石头、矿掉矿石、草方块掉草方块（等同无附魔的时运逆用）；</li>
 *   <li><b>自动熔炼</b>（{@link Mode#SMELT}）：按<b>原版熔炉配方表</b>
 *       （{@code minecraft:smelting}）把每样掉落物烧成产物——矿石掉锭、
 *       沙子掉玻璃、生肉掉熟肉，凡熔炉烧得的都烧，「能熔炼的都要」。</li>
 * </ul>
 *
 * <h2>二、存在哪儿</h2>
 * <p>模式记在物品的 {@code DataComponents.CUSTOM_DATA} 里（一个整数 0/1/2），
 * <b>跟着物品走</b>：换手、进箱子、重启服务器都不丢；两柄镐各记各的。
 * 写法与 {@link XuanshuHeavenPickaxeItem} 的「隧道 / 单方块」布尔同源。</p>
 *
 * <h2>三、切换与生效</h2>
 * <ul>
 *   <li>切换：手持镐<b>普通右键</b>（焚天镐的「潜行右键 = 隧道掘」照旧，两者不冲突），
 *       切完给一条动作栏提示（{@code message...drop_mode.*}），只报模式名；</li>
 *   <li>生效：{@link PickaxeDropEvents} 挂 {@code BlockDropsEvent}，只在
 *       非默认模式下改掉落——默认模式连事件都不看，零开销、零行为变化。</li>
 * </ul>
 *
 * <h2>四、为什么这样最不易错</h2>
 * <ul>
 *   <li>不碰 {@code DataComponents.TOOL}、不换 {@code Tier}、不加 mixin；</li>
 *   <li>掉落改写走 NeoForge 官方的 {@code BlockDropsEvent}（掉落物已生成、
 *       正要撒出来的那一刻），拿到的就是最终 {@code ItemEntity} 列表，
 *       改完即终稿——不去抢「谁掉落」的流程，自然也不会和时运、精准采集附魔、
 *       其他模组的同事件处理器打架（各自改各自的，都过同一份列表）；</li>
 *   <li>熔炼查询复用项目里 {@code XuanshuInfernalFurnaceBlockEntity} 验证过的写法：
 *       {@code getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level)}，
 *       查原版配方表，不自带任何配方数据。</li>
 * </ul>
 */
public final class PickaxeDropModes {

    /** 自定义数据里记「当前掉落模式」的那把钥匙。 */
    private static final String KEY = "DropMode";

    /** 三个模式，顺序即右键循环的顺序。 */
    public enum Mode {
        NORMAL,
        SILK,
        SMELT;

        /** 下一个（循环）。 */
        public Mode next() {
            Mode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private PickaxeDropModes() {
    }

    /** 这柄镐当前的掉落模式；没记过（或记了个越界的数）一律按正常。 */
    public static Mode get(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Mode.NORMAL;
        }
        int ordinal = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag().getInt(KEY);
        Mode[] values = Mode.values();
        return ordinal >= 0 && ordinal < values.length ? values[ordinal] : Mode.NORMAL;
    }

    /** 记下新模式，返回它。 */
    public static Mode set(ItemStack stack, Mode mode) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(KEY, mode.ordinal()));
        return mode;
    }

    /** 切到下一个模式（循环），只改数据、不吭声。 */
    public static Mode cycle(ItemStack stack) {
        return set(stack, get(stack).next());
    }

    /**
     * 右键切换 + 动作栏提示，两柄镐的 {@code use()} 共用。
     *
     * <p>调用方保证在服务端（{@code !level.isClientSide}）才调——
     * 客户端改 NBT 会被服务端覆盖，纯属浪费。</p>
     */
    public static Mode toggleWithFeedback(ItemStack stack, Player player) {
        Mode mode = cycle(stack);
        player.displayClientMessage(
                Component.translatable("message.skydeitymysticpivot.drop_mode."
                        + mode.name().toLowerCase(Locale.ROOT)),
                true);
        return mode;
    }
}
