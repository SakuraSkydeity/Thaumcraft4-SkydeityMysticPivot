package skydeity.mysticpivot;

import dev.tc4port.thaumcraft.api.Registration;
import dev.tc4port.thaumcraft.api.research.ResearchRequirement;
import dev.tc4port.thaumcraft.api.wand.WandApi;
import dev.tc4port.thaumcraft.api.wand.WandCapSpec;
import dev.tc4port.thaumcraft.api.wand.WandMaterialId;
import dev.tc4port.thaumcraft.api.wand.WandPartApi;
import dev.tc4port.thaumcraft.api.wand.WandPartAssemblyMode;
import dev.tc4port.thaumcraft.api.wand.WandRechargeSpec;
import dev.tc4port.thaumcraft.api.wand.WandRodSpec;
import dev.tc4port.thaumcraft.api.wand.WandView;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * 本模组的四把法杖（各由杖柄 + 杖端两件材质构成）。
 *
 * <h2>一、宇宙寰宇支配法杖（终局）</h2>
 * <p>杖柄 {@code infinity}「寰宇支配」容量 99 999 Vis、无需充能（recharge = NONE），
 * 杖端 {@code matrix}「宇宙」把魔力消耗压到 10%。</p>
 * <p>这把杖<b>没有合成途径</b>：两件部件注册为 {@link WandPartAssemblyMode#RUNTIME_ONLY}，
 * 既不参与奥术工作台装配、也不会出现在 JEI/EMI 的装配页里；原先那两件实体物品
 * （{@code cosmic_neutronium_rod} / {@code crystal_studded_cap}）与其配方均已删除。
 * 只能通过本模组创造页签里现成的那把法杖取得。</p>
 *
 * <h2>二、玄枢法杖（常规）</h2>
 * <p>杖柄 {@code onyx}「玄枢银树包裹」容量 5200 Vis、可被任一要素通道被动充能（recharge = ANY），
 * 杖端 {@code gilded}「玄枢」把魔力消耗压到 60%。两件部件各自注魔造出，再放进 3×3 奥术工作台自动装配。</p>
 *
 * <p><b>卦象门禁</b>：注魔<b>玄枢杖端</b>（龙蛋居中那条配方）须先在注魔祭坛所在区块内立起
 * <b>坎卦位石</b>方能引气入炉，见 {@code InfusionMatrixCraftGateMixin}。这条规则的提示写在
 * <b>两件玄枢杖材物品</b>的 lore 上（{@link ModItems#ONYX_WAND_ROD} 与
 * {@link ModItems#GILDED_WAND_CAP}），<b>不再</b>追随成品法杖——
 * 门禁只认「龙蛋居中」那一条，写在整个法杖上会连带把八卦四象杖也说得像有门禁。</p>
 *
 * <h2>三、八卦四象法杖（中高阶）</h2>
 * <p>杖柄 {@code sixiang}「四象黑曜图层」容量 1 500 Vis、可被任一要素通道被动充能，
 * 杖端 {@code bagua}「八卦」把魔力消耗压到 80%。两件部件都在注魔台打造
 * （见 {@code data/skydeitymysticpivot/recipe/}），成品名由三段拼成「八卦 + 四象 + 法杖」。</p>
 *
 * <p>部件通过本体公开的 {@link WandPartApi} 在 mod 构造期注册，因此不依赖本体的
 * 数据映射（data map）与物品标签，主模组更新时不受影响。</p>
 *
 * <h2>四、朱雀蝶舞宏伟包裹法杖（入门档）</h2>
 * <p>杖柄 {@code zhuque.rod}「朱雀宏伟包裹」容量 600 Vis、可被任一要素通道被动充能，
 * 杖端 {@code zhuque.cap}「朱雀蝶舞」耗魔九折（减免 10%）。红金配色。
 * 两件部件都在注魔台打造（见 {@code data/skydeitymysticpivot/recipe/zhuque_wand_rod.json}
 * 与 {@code zhuque_wand_cap.json}），此为全模组<b>最易得</b>的一柄杖。</p>
 *
 * <p>四把杖里朱雀杖带 {@link Rarity#RARE}（蓝名），其余三把带 {@link Rarity#EPIC}
 * （浅紫名色）。名色是物品堆叠上的 {@code minecraft:rarity} 组件，由创造页签与
 * {@link ModEvents} 一起保证，见 {@link #stampRarity(ItemStack)}。</p>
 */
public final class ModWandParts {

    // ------------------------------------------------------------------
    // 一、宇宙寰宇支配法杖（终局）：寰宇支配杖柄 + 宇宙杖端
    // ------------------------------------------------------------------

    /** 杖柄材质 id，对应本体 lang 键 {@code item.Wand.<命名空间>:infinity.rod}。 */
    public static final WandMaterialId ROD = WandMaterialId.of(rl("infinity"));

    /** 杖端材质 id，对应本体 lang 键 {@code item.Wand.<命名空间>:matrix.cap}。 */
    public static final WandMaterialId CAP = WandMaterialId.of(rl("matrix"));

    /** 杖柄容量（整 Vis）。 */
    public static final int CAPACITY = 99_999;

    /** 魔力消耗倍率，0.1 即减免至 10%。 */
    public static final float COST_MODIFIER = 0.1F;

    /** 装配系数（会决定奥术工作台的 Vis 成本）。 */
    private static final int ROD_CRAFT_COST = 100;
    private static final int CAP_CRAFT_COST = 10;

    /** 部件在创造页签/排序中的次序。 */
    private static final int ORDER = 1000;

    // ------------------------------------------------------------------
    // 二、玄枢法杖（常规）：墨玄杖柄 + 玄枢杖端
    // ------------------------------------------------------------------

    /** 玄枢银树包裹杖柄材质 id，对应本体 lang 键 {@code item.Wand.<命名空间>:onyx.rod}。 */
    public static final WandMaterialId ONYX_ROD = WandMaterialId.of(rl("onyx"));

    /** 玄枢杖端材质 id，对应本体 lang 键 {@code item.Wand.<命名空间>:gilded.cap}。 */
    public static final WandMaterialId GILDED_CAP = WandMaterialId.of(rl("gilded"));

    /** 玄枢银树包裹杖柄容量（整 Vis）。 */
    public static final int GILDED_CAPACITY = 5_200;

    /** 玄枢杖端的魔力消耗倍率，0.6 即六折消耗。 */
    public static final float GILDED_COST_MODIFIER = 0.6F;

    // 装配 Vis/每要素 = 杖端 craftCost × 杖柄 craftCost（玄枢法杖 25 × 40 = 1000）
    private static final int ONYX_ROD_CRAFT_COST = 40;
    private static final int GILDED_CAP_CRAFT_COST = 25;

    // ------------------------------------------------------------------
    // 三、八卦四象法杖（中高阶）：四象杖柄 + 八卦杖端
    // ------------------------------------------------------------------

    /** 四象黑曜图层杖柄材质 id，对应本体 lang 键 {@code item.Wand.<命名空间>:sixiang.rod}。 */
    public static final WandMaterialId SIXIANG_ROD = WandMaterialId.of(rl("sixiang"));

    /** 八卦杖端材质 id，对应本体 lang 键 {@code item.Wand.<命名空间>:bagua.cap}。 */
    public static final WandMaterialId BAGUA_CAP = WandMaterialId.of(rl("bagua"));

    /** 四象杖柄容量（整 Vis）。 */
    public static final int SIXIANG_CAPACITY = 1_500;

    /** 八卦杖端的魔力消耗倍率，0.8 即八折消耗。 */
    public static final float BAGUA_COST_MODIFIER = 0.8F;

    // 装配 Vis/每要素 = 杖端 craftCost × 杖柄 craftCost（八卦四象法杖 25 × 22 = 550）
    private static final int SIXIANG_ROD_CRAFT_COST = 22;
    private static final int BAGUA_CAP_CRAFT_COST = 25;

    // ------------------------------------------------------------------
    // 四、朱雀蝶舞宏伟包裹法杖（入门档）：朱雀宏伟包裹杖柄 + 朱雀蝶舞杖端
    // ------------------------------------------------------------------

    /** 朱雀宏伟包裹杖柄材质 id，对应本体 lang 键 {@code item.Wand.<命名空间>:zhuque.rod}。 */
    public static final WandMaterialId ZHUQUE_ROD = WandMaterialId.of(rl("zhuque"));

    /** 朱雀蝶舞杖端材质 id，对应本体 lang 键 {@code item.Wand.<命名空间>:zhuque.cap}。 */
    public static final WandMaterialId ZHUQUE_CAP = WandMaterialId.of(rl("zhuque"));

    /** 朱雀宏伟包裹杖柄容量（整 Vis）。 */
    public static final int ZHUQUE_CAPACITY = 600;

    /** 朱雀蝶舞杖端的魔力消耗倍率，0.9 即九折消耗（减免 10%）。 */
    public static final float ZHUQUE_COST_MODIFIER = 0.9F;

    // 装配 Vis/每要素 = 杖端 craftCost × 杖柄 craftCost（10 × 15 = 150）
    private static final int ZHUQUE_ROD_CRAFT_COST = 15;
    private static final int ZHUQUE_CAP_CRAFT_COST = 10;

    // ------------------------------------------------------------------

    private static Registration rodRegistration;
    private static Registration capRegistration;
    private static Registration onyxRodRegistration;
    private static Registration gildedCapRegistration;
    private static Registration sixiangRodRegistration;
    private static Registration baguaCapRegistration;
    private static Registration zhuqueRodRegistration;
    private static Registration zhuqueCapRegistration;

    private ModWandParts() {
    }

    public static void register() {
        // 终局杖：RUNTIME_ONLY —— 只能「生」出来，不能装配，也不进配方查看器。
        rodRegistration = WandPartApi.registerRod(new WandRodSpec(
                rl("cosmic_neutronium_rod"), ROD, CAPACITY, ROD_CRAFT_COST,
                WandRechargeSpec.none(), ResearchRequirement.none(),
                rl("textures/models/cosmic_neutronium_rod.png"),
                WandPartAssemblyMode.RUNTIME_ONLY, false, true, false, ORDER));
        capRegistration = WandPartApi.registerCap(new WandCapSpec(
                rl("crystal_studded_cap"), CAP, COST_MODIFIER, Set.of(), COST_MODIFIER,
                rl("textures/models/crystal_studded_cap.png"), CAP_CRAFT_COST,
                ResearchRequirement.none(), WandPartAssemblyMode.RUNTIME_ONLY, ORDER));

        // 常规杖：NORMAL —— 与本体法杖一样，两件部件各自可造，再在奥术台装配。
        onyxRodRegistration = WandPartApi.registerRod(new WandRodSpec(
                rl("onyx_wand_rod"), ONYX_ROD, GILDED_CAPACITY, ONYX_ROD_CRAFT_COST,
                WandRechargeSpec.any(), ResearchRequirement.none(),
                rl("textures/models/onyx_wand_rod.png"),
                WandPartAssemblyMode.NORMAL, false, true, false, ORDER + 1));
        gildedCapRegistration = WandPartApi.registerCap(new WandCapSpec(
                rl("gilded_wand_cap"), GILDED_CAP, GILDED_COST_MODIFIER, Set.of(),
                GILDED_COST_MODIFIER, rl("textures/models/gilded_wand_cap.png"),
                GILDED_CAP_CRAFT_COST, ResearchRequirement.none(),
                WandPartAssemblyMode.NORMAL, ORDER + 1));

        // 八卦四象杖：同样 NORMAL —— 两件部件用四象锭打造，再在奥术台装配。
        sixiangRodRegistration = WandPartApi.registerRod(new WandRodSpec(
                rl("sixiang_wand_rod"), SIXIANG_ROD, SIXIANG_CAPACITY, SIXIANG_ROD_CRAFT_COST,
                WandRechargeSpec.any(), ResearchRequirement.none(),
                rl("textures/models/sixiang_wand_rod.png"),
                WandPartAssemblyMode.NORMAL, false, true, false, ORDER + 2));
        baguaCapRegistration = WandPartApi.registerCap(new WandCapSpec(
                rl("bagua_wand_cap"), BAGUA_CAP, BAGUA_COST_MODIFIER, Set.of(),
                BAGUA_COST_MODIFIER, rl("textures/models/bagua_wand_cap.png"),
                BAGUA_CAP_CRAFT_COST, ResearchRequirement.none(),
                WandPartAssemblyMode.NORMAL, ORDER + 2));

        // 朱雀杖：入门档 NORMAL —— 红金配色，容量 600、耗魔九折。
        zhuqueRodRegistration = WandPartApi.registerRod(new WandRodSpec(
                rl("zhuque_wand_rod"), ZHUQUE_ROD, ZHUQUE_CAPACITY, ZHUQUE_ROD_CRAFT_COST,
                WandRechargeSpec.any(), ResearchRequirement.none(),
                rl("textures/models/zhuque_wand_rod.png"),
                WandPartAssemblyMode.NORMAL, false, true, false, ORDER + 3));
        zhuqueCapRegistration = WandPartApi.registerCap(new WandCapSpec(
                rl("zhuque_wand_cap"), ZHUQUE_CAP, ZHUQUE_COST_MODIFIER, Set.of(),
                ZHUQUE_COST_MODIFIER, rl("textures/models/zhuque_wand_cap.png"),
                ZHUQUE_CAP_CRAFT_COST, ResearchRequirement.none(),
                WandPartAssemblyMode.NORMAL, ORDER + 3));
    }

    /** 一键造出满魔力的宇宙寰宇支配法杖（创造模式物品栏用）。 */
    public static ItemStack charged() {
        ItemStack wand = WandApi.createFullyCharged(ROD, CAP);
        stamp(wand);
        return wand;
    }

    /** 一键造出满魔力的玄枢法杖（创造模式物品栏用）。 */
    public static ItemStack gildedCharged() {
        ItemStack wand = WandApi.createFullyCharged(ONYX_ROD, GILDED_CAP);
        stamp(wand);
        return wand;
    }

    /** 一键造出满魔力的八卦四象法杖（创造模式物品栏用）。 */
    public static ItemStack baguaCharged() {
        ItemStack wand = WandApi.createFullyCharged(SIXIANG_ROD, BAGUA_CAP);
        stamp(wand);
        return wand;
    }

    /** 一键造出满魔力的朱雀蝶舞宏伟包裹法杖（创造模式物品栏用）。 */
    public static ItemStack zhuqueCharged() {
        ItemStack wand = WandApi.createFullyCharged(ZHUQUE_ROD, ZHUQUE_CAP);
        stamp(wand);
        return wand;
    }

    /** 名色一次补齐（合成、创造页签、背包巡检三处共用）。 */
    public static void stamp(ItemStack stack) {
        stampRarity(stack);
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
        return ROD.equals(material) || ONYX_ROD.equals(material) || SIXIANG_ROD.equals(material)
                || ZHUQUE_ROD.equals(material);
    }

    public static boolean isOurCap(WandMaterialId material) {
        return CAP.equals(material) || GILDED_CAP.equals(material) || BAGUA_CAP.equals(material)
                || ZHUQUE_CAP.equals(material);
    }

    /**
     * 把堆叠的名色按杖分档：朱雀杖为 RARE（蓝），其余为 EPIC（浅紫）。
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
        if (view != null && ZHUQUE_ROD.equals(view.rod()) && ZHUQUE_CAP.equals(view.cap())) {
            target = Rarity.RARE;
        }
        if (stack.get(DataComponents.RARITY) != target) {
            stack.set(DataComponents.RARITY, target);
        }
        return true;
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MysticPivot.MODID, path);
    }
}
