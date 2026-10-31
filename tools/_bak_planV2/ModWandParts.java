package skydeity.mysticpivot;

import dev.tc4port.thaumcraft.api.research.ResearchRequirement;
import dev.tc4port.thaumcraft.api.wand.WandApi;
import dev.tc4port.thaumcraft.api.wand.WandCapSpec;
import dev.tc4port.thaumcraft.api.wand.WandMaterialId;
import dev.tc4port.thaumcraft.api.wand.WandPartApi;
import dev.tc4port.thaumcraft.api.wand.WandPartAssemblyMode;
import dev.tc4port.thaumcraft.api.wand.WandRechargeSpec;
import dev.tc4port.thaumcraft.api.wand.WandRodSpec;
import dev.tc4port.thaumcraft.api.wand.WandView;
import java.util.List;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * 本模组的七把法杖（各由杖柄 + 杖端两件材质构成）。
 *
 * <h2>一、宇宙寰宇支配法杖（终局）</h2>
 * <p>杖柄 {@code infinity}「寰宇支配」容量 99 999 Vis、与其他法杖同一套被动充能口径
 * （recharge = ANY），杖端 {@code matrix}「宇宙」<b>不打折：耗魔按原价 100%</b>
 * （第二百零九批按用户口径「改为 100% 法力消耗」由 10% 提上来——它的强在储量，不在省魔）。
 * ★ 第二百一十七批：用户「容量 99999、100% 消耗，不要消耗了直接补满，就设置最简单的」——
 * 原来那个每 5 tick 把六元立刻补满的 {@code WandAutoRefill} 整个删了，只留容量与价码，
 * 充能交回本体的标准通道，不再有任何特殊逻辑。
 * 这把杖<b>没有合成途径</b>：两件部件注册为 {@link WandPartAssemblyMode#RUNTIME_ONLY}，
 * 既不参与奥术工作台装配、也不会出现在 JEI/EMI 的装配页里。只能通过本模组创造页签里
 * 现成的那把法杖取得。</p>
 *
 * <h2>二、玄枢法杖（常规）</h2>
 * <p>杖柄 {@code onyx}「玄枢银树包裹」容量 2035 Vis、可被任一要素通道被动充能（recharge = ANY），
 * 杖端 {@code gilded}「玄枢」把魔力消耗压到 60%。两件部件各自注魔造出，再放进 3×3 奥术工作台自动装配。</p>
 *
 * <h2>三、八卦四象法杖（中高阶）</h2>
 * <p>杖柄 {@code sixiang}「四象黑曜图层」容量 1 949 Vis、杖端 {@code bagua}「八卦」耗魔八成。</p>
 *
 * <h2>四、四圣兽法杖（入门档，第一百六十一批补齐四象）</h2>
 * <ul>
 *   <li><b>朱雀蝶舞宏伟包裹</b>（{@code zhuque}）：918 Vis / 八折，红金；</li>
 *   <li><b>青龙云从翠竹包裹</b>（{@code qinglong}）：918 Vis / 八折，青碧；</li>
 *   <li><b>白虎风从寒铁包裹</b>（{@code baihu}）：918 Vis / 八折，霜白；</li>
 *   <li><b>玄武负图龟甲包裹</b>（{@code xuanwu}）：918 Vis / 八折，幽蓝。</li>
 * </ul>
 * <p>四柄都是「最易得」一档：部件各自注魔炼出（中心是神兽碎片 / 神兽锭，
 * 八格带本兽配色的小件），再在奥术工作台对角摆装配。杖柄定储量、杖端定耗损。</p>
 *
 * <p>★ 第一百六十二批把四柄的<b>容量</b>拉齐（一律 918，原先 950 / 980 / 940 各差一点）；
 * 第一百六十三批再把<b>耗损</b>拉齐——四柄同价 <b>八折</b>
 * （用户原话：「刚刚四个法杖同一918 容量 80% 消耗」）。</p>
 *
 * <h2>五、注册方式（第一百六十一批：表格驱动）</h2>
 * <p>七柄杖的参数全列在 {@link #PRESETS} 一张表里，{@link #register()} 照表循环注册——
 * 再加一柄杖只需在表尾加一行，不必再复读七段二十行。名色分档也查同一张表
 * （{@link #isEntryTier}）：四圣兽为 {@link Rarity#RARE}（蓝名），其余为 {@link Rarity#EPIC}
 * （浅紫名色）。名色是物品堆叠上的 {@code minecraft:rarity} 组件，由创造页签与
 * {@link ModEvents} 一起保证，见 {@link #stampRarity(ItemStack)}。</p>
 */
public final class ModWandParts {

    // ------------------------------------------------------------------
    // 各杖的材质 id / 容量 / 耗损（物件本身按 PRESETS 表注册）
    // ------------------------------------------------------------------

    /** 杖柄材质 id，对应本体 lang 键 {@code item.Wand.<命名空间>:infinity.rod}。 */
    public static final WandMaterialId ROD = WandMaterialId.of(rl("infinity"));

    /** 杖端材质 id，对应本体 lang 键 {@code item.Wand.<命名空间>:matrix.cap}。 */
    public static final WandMaterialId CAP = WandMaterialId.of(rl("matrix"));

    /** 杖柄容量（整 Vis）。 */
    public static final int CAPACITY = 99_999;

    /** 魔力消耗倍率，1.0 即 <b>原价 100%</b>（第二百零九批按用户口径由 0.1 提上来，不再打折）。 */
    public static final float COST_MODIFIER = 1.0F;

    /** 装配系数（会决定奥术工作台的 Vis 成本）。 */
    private static final int ROD_CRAFT_COST = 100;
    private static final int CAP_CRAFT_COST = 10;

    /** 玄枢银树包裹杖柄材质 id，对应本体 lang 键 {@code item.Wand.<命名空间>:onyx.rod}。 */
    public static final WandMaterialId ONYX_ROD = WandMaterialId.of(rl("onyx"));

    /** 玄枢杖端材质 id，对应本体 lang 键 {@code item.Wand.<命名空间>:gilded.cap}。 */
    public static final WandMaterialId GILDED_CAP = WandMaterialId.of(rl("gilded"));

    /** 玄枢银树包裹杖柄容量（整 Vis）。 */
    public static final int GILDED_CAPACITY = 2_035;

    /** 玄枢杖端的魔力消耗倍率，0.6 即六折消耗。 */
    public static final float GILDED_COST_MODIFIER = 0.6F;

    // 装配 Vis/每要素 = 杖端 craftCost × 杖柄 craftCost（玄枢法杖 25 × 40 = 1000）
    private static final int ONYX_ROD_CRAFT_COST = 40;
    private static final int GILDED_CAP_CRAFT_COST = 25;

    /** 四象黑曜图层杖柄材质 id，对应本体 lang 键 {@code item.Wand.<命名空间>:sixiang.rod}。 */
    public static final WandMaterialId SIXIANG_ROD = WandMaterialId.of(rl("sixiang"));

    /** 八卦杖端材质 id，对应本体 lang 键 {@code item.Wand.<命名空间>:bagua.cap}。 */
    public static final WandMaterialId BAGUA_CAP = WandMaterialId.of(rl("bagua"));

    /** 四象杖柄容量（整 Vis）。 */
    public static final int SIXIANG_CAPACITY = 1_949;

    /** 八卦杖端的魔力消耗倍率，0.8 即八折消耗。 */
    public static final float BAGUA_COST_MODIFIER = 0.8F;

    // 装配 Vis/每要素 = 杖端 craftCost × 杖柄 craftCost（八卦四象法杖 25 × 22 = 550）
    private static final int SIXIANG_ROD_CRAFT_COST = 22;
    private static final int BAGUA_CAP_CRAFT_COST = 25;

    /** 朱雀宏伟包裹杖柄材质 id，对应本体 lang 键 {@code item.Wand.<命名空间>:zhuque.rod}。 */
    public static final WandMaterialId ZHUQUE_ROD = WandMaterialId.of(rl("zhuque"));

    /** 朱雀蝶舞杖端材质 id，对应本体 lang 键 {@code item.Wand.<命名空间>:zhuque.cap}。 */
    public static final WandMaterialId ZHUQUE_CAP = WandMaterialId.of(rl("zhuque"));

    // ---- 第一百六十三批：四圣兽共用数值档（容量一个价、耗损也是一个价）----

    /** 四圣兽杖柄容量（整 Vis）：朱雀 / 青龙 / 白虎 / 玄武四柄同价。 */
    private static final int BEAST_CAPACITY = 918;

    /** 四圣兽杖端耗损：四柄同价，魔力消耗压到 80%。 */
    private static final float BEAST_COST = 0.8F;

    /** 青龙翠竹包裹杖柄材质 id，对应本体 lang 键 {@code item.Wand.<命名空间>:qinglong.rod}。 */
    public static final WandMaterialId QINGLONG_ROD = WandMaterialId.of(rl("qinglong"));

    /** 青龙云从杖端材质 id，对应本体 lang 键 {@code item.Wand.<命名空间>:qinglong.cap}。 */
    public static final WandMaterialId QINGLONG_CAP = WandMaterialId.of(rl("qinglong"));

    /** 白虎寒铁包裹杖柄材质 id，对应本体 lang 键 {@code item.Wand.<命名空间>:baihu.rod}。 */
    public static final WandMaterialId BAIHU_ROD = WandMaterialId.of(rl("baihu"));

    /** 白虎风从杖端材质 id，对应本体 lang 键 {@code item.Wand.<命名空间>:baihu.cap}。 */
    public static final WandMaterialId BAIHU_CAP = WandMaterialId.of(rl("baihu"));

    /** 玄武龟甲包裹杖柄材质 id，对应本体 lang 键 {@code item.Wand.<命名空间>:xuanwu.rod}。 */
    public static final WandMaterialId XUANWU_ROD = WandMaterialId.of(rl("xuanwu"));

    /** 玄武负图杖端材质 id，对应本体 lang 键 {@code item.Wand.<命名空间>:xuanwu.cap}。 */
    public static final WandMaterialId XUANWU_CAP = WandMaterialId.of(rl("xuanwu"));

    // 四圣兽的装配成本（10 × 15 = 150）：入门档一个价，不搞隐形加价
    private static final int BEAST_ROD_CRAFT_COST = 15;
    private static final int BEAST_CAP_CRAFT_COST = 10;

    /** 部件在创造页签/排序中的次序基数。 */
    private static final int ORDER = 1000;

    // ------------------------------------------------------------------
    // 注册表（第一百六十一批：七柄杖一张表，register() 照表循环）
    // ------------------------------------------------------------------

    /**
     * 一柄法杖的全部注册参数。
     *
     * @param rodItem      杖柄物品 id（贴图路径由它推导：{@code textures/models/<id>.png}）
     * @param capItem      杖端物品 id
     * @param rodMaterial  杖柄材质 id（本体 lang 键 {@code item.Wand.<ns>:<material>.rod}）
     * @param capMaterial  杖端材质 id
     * @param capacity     杖柄容量（整 Vis）
     * @param costModifier 杖端魔力消耗倍率
     * @param rodCraftCost 杖柄装配系数
     * @param capCraftCost 杖端装配系数
     * @param recharge     充能口径（{@code any()} = 可被任一要素通道被动充能）
     * @param runtimeOnly  true = 只能「生」出来，不参与装配、不进配方查看器
     * @param entryTier    true = 入门档，成品名色为 RARE（蓝）
     */
    private record WandPreset(
            String rodItem, String capItem,
            WandMaterialId rodMaterial, WandMaterialId capMaterial,
            int capacity, float costModifier,
            int rodCraftCost, int capCraftCost,
            WandRechargeSpec recharge, boolean runtimeOnly, boolean entryTier) {

        ResourceLocation rodTexture() {
            return rl("textures/models/" + rodItem + ".png");
        }

        ResourceLocation capTexture() {
            return rl("textures/models/" + capItem + ".png");
        }

        WandPartAssemblyMode assembly() {
            return runtimeOnly ? WandPartAssemblyMode.RUNTIME_ONLY : WandPartAssemblyMode.NORMAL;
        }
    }

    /** 七柄杖，按创造页签/排序的先后列。 */
    private static final List<WandPreset> PRESETS = List.of(
            new WandPreset("cosmic_neutronium_rod", "crystal_studded_cap",
                    ROD, CAP, CAPACITY, COST_MODIFIER,
                    ROD_CRAFT_COST, CAP_CRAFT_COST, WandRechargeSpec.any(), true, false),
            new WandPreset("onyx_wand_rod", "gilded_wand_cap",
                    ONYX_ROD, GILDED_CAP, GILDED_CAPACITY, GILDED_COST_MODIFIER,
                    ONYX_ROD_CRAFT_COST, GILDED_CAP_CRAFT_COST, WandRechargeSpec.any(), false, false),
            new WandPreset("sixiang_wand_rod", "bagua_wand_cap",
                    SIXIANG_ROD, BAGUA_CAP, SIXIANG_CAPACITY, BAGUA_COST_MODIFIER,
                    SIXIANG_ROD_CRAFT_COST, BAGUA_CAP_CRAFT_COST, WandRechargeSpec.any(), false, false),
            new WandPreset("zhuque_wand_rod", "zhuque_wand_cap",
                    ZHUQUE_ROD, ZHUQUE_CAP, BEAST_CAPACITY, BEAST_COST,
                    BEAST_ROD_CRAFT_COST, BEAST_CAP_CRAFT_COST, WandRechargeSpec.any(), false, true),
            new WandPreset("qinglong_wand_rod", "qinglong_wand_cap",
                    QINGLONG_ROD, QINGLONG_CAP, BEAST_CAPACITY, BEAST_COST,
                    BEAST_ROD_CRAFT_COST, BEAST_CAP_CRAFT_COST, WandRechargeSpec.any(), false, true),
            new WandPreset("baihu_wand_rod", "baihu_wand_cap",
                    BAIHU_ROD, BAIHU_CAP, BEAST_CAPACITY, BEAST_COST,
                    BEAST_ROD_CRAFT_COST, BEAST_CAP_CRAFT_COST, WandRechargeSpec.any(), false, true),
            new WandPreset("xuanwu_wand_rod", "xuanwu_wand_cap",
                    XUANWU_ROD, XUANWU_CAP, BEAST_CAPACITY, BEAST_COST,
                    BEAST_ROD_CRAFT_COST, BEAST_CAP_CRAFT_COST, WandRechargeSpec.any(), false, true));

    private ModWandParts() {
    }

    public static void register() {
        int order = ORDER;
        for (WandPreset preset : PRESETS) {
            WandPartApi.registerRod(new WandRodSpec(
                    rl(preset.rodItem()), preset.rodMaterial(), preset.capacity(), preset.rodCraftCost(),
                    preset.recharge(), ResearchRequirement.none(),
                    preset.rodTexture(), preset.assembly(), false, true, false, order));
            WandPartApi.registerCap(new WandCapSpec(
                    rl(preset.capItem()), preset.capMaterial(), preset.costModifier(), Set.of(),
                    preset.costModifier(), preset.capTexture(), preset.capCraftCost(),
                    ResearchRequirement.none(), preset.assembly(), order));
            order++;
        }
    }

    // ------------------------------------------------------------------
    // 创造页签：一键造出满魔力的成品杖
    // ------------------------------------------------------------------

    /** 一键造出满魔力的宇宙寰宇支配法杖（创造模式物品栏用）。 */
    public static ItemStack charged() {
        return stamp(WandApi.createFullyCharged(ROD, CAP));
    }

    /** 一键造出满魔力的玄枢法杖（创造模式物品栏用）。 */
    public static ItemStack gildedCharged() {
        return stamp(WandApi.createFullyCharged(ONYX_ROD, GILDED_CAP));
    }

    /** 一键造出满魔力的八卦四象法杖（创造模式物品栏用）。 */
    public static ItemStack baguaCharged() {
        return stamp(WandApi.createFullyCharged(SIXIANG_ROD, BAGUA_CAP));
    }

    /** 一键造出满魔力的朱雀蝶舞宏伟包裹法杖（创造模式物品栏用）。 */
    public static ItemStack zhuqueCharged() {
        return stamp(WandApi.createFullyCharged(ZHUQUE_ROD, ZHUQUE_CAP));
    }

    /** 一键造出满魔力的青龙云从翠竹包裹法杖（创造模式物品栏用）。 */
    public static ItemStack qinglongCharged() {
        return stamp(WandApi.createFullyCharged(QINGLONG_ROD, QINGLONG_CAP));
    }

    /** 一键造出满魔力的白虎风从寒铁包裹法杖（创造模式物品栏用）。 */
    public static ItemStack baihuCharged() {
        return stamp(WandApi.createFullyCharged(BAIHU_ROD, BAIHU_CAP));
    }

    /** 一键造出满魔力的玄武负图龟甲包裹法杖（创造模式物品栏用）。 */
    public static ItemStack xuanwuCharged() {
        return stamp(WandApi.createFullyCharged(XUANWU_ROD, XUANWU_CAP));
    }

    /** 名色一次补齐（合成、创造页签、背包巡检三处共用）。 */
    public static ItemStack stamp(ItemStack stack) {
        stampRarity(stack);
        return stack;
    }

    /** 这把法杖是否由本模组的材质构成。 */
    public static boolean isOurWand(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        WandView view = WandApi.view(stack).orElse(null);
        return view != null && (isOurRod(view.rod()) || isOurCap(view.cap()));
    }

    public static boolean isOurRod(WandMaterialId material) {
        for (WandPreset preset : PRESETS) {
            if (preset.rodMaterial().equals(material)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isOurCap(WandMaterialId material) {
        for (WandPreset preset : PRESETS) {
            if (preset.capMaterial().equals(material)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 把堆叠的名色按杖分档：四圣兽（朱雀 / 青龙 / 白虎 / 玄武）为 RARE（蓝），
     * 其余为 EPIC（浅紫）。
     * 本体 {@code WandItem} 不覆写稀有度，而 1.21 的 {@code ItemStack#getRarity}
     * 直接读 {@code minecraft:rarity} 组件，所以在这里补上即可。
     *
     * @return 该堆叠是否需要（或已经）带上目标名色
     */
    public static boolean stampRarity(ItemStack stack) {
        if (!isOurWand(stack)) {
            return false;
        }
        Rarity target = Rarity.EPIC;
        WandView view = WandApi.view(stack).orElse(null);
        if (view != null && isEntryTier(view.rod(), view.cap())) {
            target = Rarity.RARE;
        }
        if (stack.get(DataComponents.RARITY) != target) {
            stack.set(DataComponents.RARITY, target);
        }
        return true;
    }

    /** 「柄 + 端」都对得上某一柄入门档杖（查 {@link #PRESETS}）。 */
    private static boolean isEntryTier(WandMaterialId rod, WandMaterialId cap) {
        for (WandPreset preset : PRESETS) {
            if (preset.entryTier()
                    && preset.rodMaterial().equals(rod)
                    && preset.capMaterial().equals(cap)) {
                return true;
            }
        }
        return false;
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MysticPivot.MODID, path);
    }
}
