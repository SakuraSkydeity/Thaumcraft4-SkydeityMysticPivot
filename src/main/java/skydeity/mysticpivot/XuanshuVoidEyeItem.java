package skydeity.mysticpivot;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.aspect.VisChannel;
import dev.tc4port.thaumcraft.api.aspect.VisCost;
import dev.tc4port.thaumcraft.api.ability.PlayerAbility;
import dev.tc4port.thaumcraft.api.item.PlayerAbilityGear;
import dev.tc4port.thaumcraft.api.wand.WandApi;
import dev.tc4port.thaumcraft.api.wand.WandView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/**
 * 玄枢洞虚灵目（第七十八批新增，第八十批改项链栏）：一枚<b>Curios 饰品</b>，戴进
 * <b>项链栏</b>（{@code ring} 栏位（第一百零二批由项链栏改为戒指栏））后，效果对齐本体的
 * <b>揭示之护目镜</b>——只是不需要占用头盔格。
 *
 * <h2>五项能力各自的落点</h2>
 * <ul>
 *   <li><b>揭示灵气节点 + 容器要素 HUD</b>：实现本体 API 的 {@link PlayerAbilityGear}，
 *       在 {@link #grantsPlayerAbility} 里对 {@link PlayerAbility#AURA_REVEALING} 与
 *       {@link PlayerAbility#GOGGLES_OVERLAY} 都返回 {@code true}（本物按「像揭示之护目镜
 *       一样」的口径两项都开）。本体 {@code PlayerAbilitySources} 每 tick 扫
 *       「四件盔甲 + 全部 Curios 饰品」，逐个问
 *       {@code EquipmentAbilities.grantsPlayerAbility(stack, wearer, ability)}，命中就把
 *       玩家的能力属性点亮；渲染端只读属性，不认物品 —— 所以戒指戴在 Curios 栏里也能生效。
 *       <b>第二百零八批换本体 20753</b>：原来那两个接口
 *       （{@code AuraRevealingGear} / {@code GogglesOverlayGear}）在 20753 的
 *       revealing-abilities 重构里删了，统一成这一个 + 属性通道。</li>
 *   <li><b>护甲 +200%</b>（第八十三批，此前是护甲 +20 → 最大生命 +100%）：覆写
 *       {@link ICurioItem#getAttributeModifiers}，向
 *       {@link Attributes#ARMOR} 挂一枚 {@code ADD_MULTIPLIED_TOTAL} 的修正
 *       （{@code +2.0} 即最终护甲 = 当前护甲 ×3）。
 *       ★ 必须用 TOTAL 而不是 BASE：玩家的护甲值全部来自装备的
 *       {@code ADD_VALUE} 修正、实体基础值是 0，BASE 通道乘的是 0；
 *       TOTAL 则对「当前护甲总额」生效，与文字描述「护甲 +200%」逐字对齐。
 *       · 第一百零四批追加<b>生命 +200%</b>：同一方法里再向 {@link Attributes#MAX_HEALTH}
 *       挂一枚 {@code ADD_MULTIPLIED_TOTAL}（{@code +2.0}），20 点基础生命 → 60 点。
 *   <li><b>法杖缓充</b>（第一百零四批）：覆写 {@link ICurioItem#curioTick}，
 *       每 40 tick 遍历一次背包，给每一柄 {@code WandApi} 认得出的法杖、每条通道补
 *       <b>容量的 1%</b>（至少 1 Vis）。只走服务端。
 *       Curios 9.5.1 的属性管线（{@code CurioStacksHandler} 的
 *       modifier 缓存 → {@code CuriosEventHandler.tick} 里的 lambda →
 *       {@code AttributeMap}）最终会问到
 *       {@code ItemizedCurioCapability#getAttributeModifiers}，它在本物品没挂
 *       {@code CurioAttributeModifiers} 数据组件时<b>回退</b>到这个接口方法，
 *       所以覆写它即可生效。</li>
 *   <li><b>削峰</b>（第一百五十七批）：单次受伤不超过自身最大生命的
 *       {@value #DAMAGE_CAP_RATIO} 倍。不写在这里 —— 需要拦「即将到来的伤害」，
 *       落点是 {@link ModEvents#onLivingIncomingDamage}，本类只提供
 *       {@link #isWornBy} 这个「戴没戴」的判定。</li>
 * </ul>
 *
 * <h2>为什么走项链栏</h2>
 * 本体 TC4 的 {@code data/thaumcraft/curios/entities/player.json} 已经给玩家
 * 开好了 {@code necklace / ring / belt} 三槽，{@code ring} 槽的 validator
 * 是 {@code curios:tag}——物品能否入槽由 <b>{@code curios:ring} 物品标签</b>
 * 决定（符文护身符等本体饰品也是这么挂进去的）。本mod只需在
 * {@code data/curios/tags/item/ring.json} 里列上本物品（replace:false，
 * 与本体那份自动合并），不必再动任何 Java 侧槽位声明。
 *
 * <h2>为什么不走 CurioAttributeModifiers 数据组件</h2>
 * 那个组件类型由 Curios 自己的 {@code DeferredRegister.createDataComponents("curios")}
 * 提供（{@code CuriosRegistry.CURIO_ATTRIBUTE_MODIFIERS}），其 {@code .get()}
 * 要等数据组件注册事件走完才能用；而本mod的物品注册发生在模组构造期，
 * 在 {@code Item.Properties} 里提前 {@code .get()} 会直接抛异常。接口回退
 * 则在游戏内属性重算时才被调用，时序上零风险，且只依赖 {@code curios.api} 公开包。
 *
 * <h2>为什么直接 implements ICurioItem 而不另写行为类</h2>
 * 本体 TC4 的 {@code CuriosCompat} 给二十多件符文饰品共用一个
 * {@code ThaumcraftCurioBehavior} 单例；本mod只有这一件饰品，没有任何
 * equip/unequip/tick 副作用，{@code ICurioItem} 的默认方法（含
 * {@code hasCurioCapability} 恒真）已经够用，让物品自己当行为体最省。
 *
 * <h2>前置依赖</h2>
 * Curios 是本体 TC4 的硬依赖（{@code [9.5.1,)}，见本体 mods.toml），
 * 本mod又硬依赖 TC4，故运行时 Curios 必然在场；编译期依赖由
 * {@code deps/curios-neoforge-9.5.1+1.21.1.jar}（compileOnly）提供。
 */
public class XuanshuVoidEyeItem extends Item implements ICurioItem, PlayerAbilityGear {

    /** 护甲修正的稳定 id（同一件饰品在戒指栏里只可能有一份，用固定 id 即可）。 */
    public static final ResourceLocation ARMOR_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(MysticPivot.MODID, "void_eye_armor");

    /** 生命修正的稳定 id（第一百零四批新增）。 */
    public static final ResourceLocation HEALTH_MODIFIER_ID =
            ResourceLocation.fromNamespaceAndPath(MysticPivot.MODID, "void_eye_health");

    /** 护甲乘算加值：2.0 = +200%（戴上后护甲总额 ×3）。 */
    public static final double ARMOR_BONUS = 2.0;

    /** 生命乘算加值：2.0 = +200%（20 点基础 → 60 点，即 +20 颗心）。 */
    public static final double HEALTH_BONUS = 2.0;

    /** 缓充周期（tick）：40 = 两秒一跳。 */
    private static final int RECHARGE_INTERVAL = 40;

    /** 每一跳给每一条通道补多少：容量的 1%（不足 1 Vis 按 1 Vis 算）。 */
    private static final double RECHARGE_RATIO = 0.01;

    /**
     * 单次受伤的上限：穿戴者<b>一次最多吃下自身最大生命 10% 的伤害</b>（第一百五十七批新增）。
     *
     * <p>落点是 {@code LivingIncomingDamageEvent}（{@link ModEvents#onLivingIncomingDamage}），
     * 在护甲、附魔、抗性之前就把数额压下去——所以它是一道「削峰」而不是「减伤」：
     * 小刀小剑完全不受影响，一发 40 点的重击会被削到 6 点（60 血 × 10%）。</p>
     */
    public static final float DAMAGE_CAP_RATIO = 0.10F;

    public XuanshuVoidEyeItem(Item.Properties properties) {
        super(properties);
    }

    /**
     * 这名玩家此刻是否把这枚戒指戴在饰品栏里。
     *
     * <p>用 Curios 的 {@code isEquipped(Item)} 而不是自己翻背包：它只认<b>已装备</b>的槽位，
     * 放背包里不算，与「戴着才有效」的口径一致。Curios 不在场时返回 {@code false}（不生效），
     * 不会抛异常。</p>
     */
    public static boolean isWornBy(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .map(handler -> handler.isEquipped(ModItems.XUANSHU_VOID_EYE.get()))
                .orElse(false);
    }

    /**
     * 两项能力恒开：戴上就看得见灵气节点、瞄准机器 / 容器时浮出要素标签
     * （与揭示之护目镜同效）。本体 {@code PlayerAbilitySources} 每 tick 扫一遍
     * 「四件盔甲 + 全部 Curios 饰品」，问到这里就由它把玩家的能力属性点亮。
     */
    @Override
    public boolean grantsPlayerAbility(ItemStack stack, Player wearer, PlayerAbility ability) {
        return ability == PlayerAbility.AURA_REVEALING || ability == PlayerAbility.GOGGLES_OVERLAY;
    }

    /**
     * 护甲 +200%。Curios 传进来的 {@code identifier} 是「这个栏位这一次」的
     * 修正 id（{@code CuriosApi.getSlotId(slotContext)}，按槽位生成），原样用它，
     * 万一将来有别的数据包给玩家添了更多项链格，两件灵目也不会互相覆盖。
     */
    @Override
    public Multimap<Holder<Attribute>, AttributeModifier> getAttributeModifiers(
            SlotContext slotContext, ResourceLocation identifier, ItemStack stack) {
        Multimap<Holder<Attribute>, AttributeModifier> modifiers = HashMultimap.create();
        modifiers.put(Attributes.ARMOR, new AttributeModifier(
                identifier != null ? identifier : ARMOR_MODIFIER_ID,
                ARMOR_BONUS,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        // 第一百零四批：生命 +200%。同样是乘在「当前最大生命」上的 TOTAL 通道——
        // 玩家的基础生命是 20，这里 ×3 得 60（+20 颗心），与「+200%」逐字对齐。
        ResourceLocation healthId = identifier != null
                ? ResourceLocation.fromNamespaceAndPath(identifier.getNamespace(), identifier.getPath() + "_health")
                : HEALTH_MODIFIER_ID;
        modifiers.put(Attributes.MAX_HEALTH, new AttributeModifier(
                healthId, HEALTH_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        return modifiers;
    }

    /**
     * 戴着它的时候，每 40 tick 给<b>背包里每一柄法杖</b>补一次魔力。
     *
     * <p>口径是「缓慢」：每跳每条通道只补<b>容量的 1%</b>（不足 1 Vis 按 1 Vis 算），
     * 一柄 918 的朱雀杖从空到满要约 200 秒。补的是「当前背包」——主背包 36 格，
     * 装备栏与背包里的都算（{@code Inventory#getContainerSize} 覆盖到）。
     * 只动 {@link WandApi#view} 认得出的法杖，别的物品一概不碰；本体的
     * {@code recharge} 被动充能照旧，两者叠加不冲突（都走
     * {@code WandApi.insert}，各自封顶到容量）。</p>
     *
     * <p>★ 只在服务端跑：{@code curioTick} 两端都会调，客户端跑一遍毫无意义，
     * 还会白白遍历背包。</p>
     */
    @Override
    public void curioTick(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        if (player.tickCount % RECHARGE_INTERVAL != 0) {
            return;
        }
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            recharge(player, inventory.getItem(slot));
        }
    }

    /** 给一柄法杖补一跳（各通道按容量 1%，封顶）。 */
    private static void recharge(ServerPlayer player, ItemStack candidate) {
        if (candidate.isEmpty()) {
            return;
        }
        WandView view = WandApi.view(candidate).orElse(null);
        if (view == null) {
            return;
        }
        for (var entry : view.capacityCentivis().entrySet()) {
            VisChannel channel = entry.getKey();
            int capacity = entry.getValue();
            int current = view.visCentivis().getOrDefault(channel, 0);
            int missing = capacity - current;
            if (missing <= 0) {
                continue;
            }
            int step = Math.max(100, (int) Math.round(capacity * RECHARGE_RATIO));
            WandApi.insert(player, candidate, VisCost.ofCentivis(channel, Math.min(step, missing)),
                    VisAction.EXECUTE);
        }
    }
}
