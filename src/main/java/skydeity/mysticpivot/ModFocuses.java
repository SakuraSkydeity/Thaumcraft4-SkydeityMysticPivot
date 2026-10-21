package skydeity.mysticpivot;

import com.mojang.serialization.Codec;
import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.aspect.VisPaymentResult;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionApi;
import dev.tc4port.thaumcraft.api.focus.FocusId;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionSpec;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionTarget;
import dev.tc4port.thaumcraft.api.focus.action.FocusCastMode;
import dev.tc4port.thaumcraft.api.focus.action.FocusInput;
import dev.tc4port.thaumcraft.api.focus.action.FocusTargetKind;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionResult;
import dev.tc4port.thaumcraft.api.presentation.ActionPresentationAudience;
import dev.tc4port.thaumcraft.api.research.ResearchRequirement;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * 三个自制法杖核心（仿本体「九狱」核心的注册方式，均为 {@link XuanshuFocusItem}）：
 *
 * <h2>一、镇狱碎青峰（{@code focus_cosmos}）</h2>
 * <p>右键方块：消耗六基础元素各 <b>10</b> 点，把目标方块<b>直接变成掉落物</b>——
 * 以带精准采集的下界合金镐为工具上下文取掉落表，无视方块硬度与挖掘等级。</p>
 *
 * <h2>二、乾元罡气（{@code focus_guardian}）</h2>
 * <p>右键：消耗六基础元素各 <b>200</b> 点，获得一颗绕玩家旋转的守护之心；
 * 死亡时拦截一次致死事件（免死，复用 SakuraFall 的 {@code LivingDeathEvent} 取消路线），
 * 随后心消失。守护之心没有时间限制，且可叠加。</p>
 *
 * <h2>三、赤羽焚天瞬（{@code focus_teleport}）</h2>
 * <p>潜行右键记点、右键耗六基础元素各 <b>10</b> 点回到记下的那一点（行为在
 * {@link TeleportCore}）。</p>
 *
 * <p>三核心的价码都由数据包提供（{@code focus_catalog/*.json} 的 {@code base_cost}，
 * 单位是**厘 Vis**，100 厘 = 1 Vis；六卦各 128 的合成见 recipe），动作在此注册。
 * 三条路都<b>必须显式调 {@code context.pay}</b> 才会真的扣魔。
 * 守护之心挂在玩家 {@link AttachmentType} 上，随存档持久化。</p>
 */
@EventBusSubscriber(modid = MysticPivot.MODID)
public final class ModFocuses {

    /** 镇狱碎青峰核心的行为 id（同时是数据包档案文件 skydeitymysticpivot:cosmos 的资源 id）。 */
    public static final FocusId COSMOS_ID = FocusId.of(rl("cosmos"));

    /** 乾元罡气核心的行为 id（对应数据包档案 skydeitymysticpivot:guardian）。 */
    public static final FocusId GUARDIAN_ID = FocusId.of(rl("guardian"));

    /** 赤羽焚天瞬核心的行为 id（对应数据包档案 skydeitymysticpivot:teleport）。 */
    public static final FocusId TELEPORT_ID = FocusId.of(rl("teleport"));

    /*
     * ★ 三核心的价码**只存在数据包里**（focus_catalog/*.json 的 base_cost，单位厘 Vis：
     *   档里 1000 = 10 整 Vis），`FocusUpgradeCatalog` 读进来时走 `VisCost.ofCentivis`。
     *   第九十九批之前这里还留着 COSMOS_VIS_PER_CHANNEL / GUARDIAN_VIS_PER_CHANNEL 两个常量，
     *   第一百批「要素不足」提示统一成一句不带数字的短句后，那两个常量就彻底没人用了 ——
     *   已删（改价只改 json，别在两处对表）。
     *
     * 现价（整 Vis / 每通道）：镇狱碎青峰 10、乾元罡气 200、赤羽焚天瞬 10。
     * 实际扣的还会乘杖帽耗魔系数（鎏金 0.6 / 八卦 0.8 / 朱雀 0.9 / 寰宇 0.1）：
     * `PaymentPlan.addDiscounted` 走 `WandVis.modifiedCost(..., CASTING)`，
     * 这是本体法核的一贯规矩，不是本 mod 另加的。
     *
     * 第六十九批的教训也留在这儿：档里的价码**只是显示用**，真正扣费取决于处理函数
     * 有没有调 `context.pay`——镇狱碎青峰与乾元罡气两条路原来压根没调，
     * `PaymentPlan` 的 calls 列表是空的、扣费恒为 0（等于白嫖）；现在三条路都调了。
     */

    /** 守护之心数量（可叠加；免死一次消耗一颗）。 */
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MysticPivot.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> GUARD_HEARTS =
            ATTACHMENTS.register("guard_hearts",
                    () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).build());

    private ModFocuses() {
    }

    /**
     * mod 构造期注册三个核心的右键动作。
     *
     * <p>★ 第一百二十七批（本体 20744 focus 重构）：老的
     * {@code FocusActionSpec(id, target, Set<FocusActivation>, research, range, priority, FocusCastAnimation, audience)}
     * 没了，换成
     * {@code FocusActionSpec(id, target, FocusInput, Set<FocusTargetKind>, FocusCastMode, research, range, priority, audience)}。
     * 对应关系：老 {@code USE_AIR/USE_BLOCK} → 新 {@code FocusInput.SECONDARY}（就是右键）
     * + {@code FocusTargetKind.AIR/BLOCK}；老 {@code FocusCastAnimation.INSTANT} → {@code FocusCastMode.INSTANT}。</p>
     */
    public static void register() {
        FocusActionApi.register(new FocusActionSpec(
                        rl("focus/cosmos"), new FocusActionTarget.Focus(COSMOS_ID),
                        FocusInput.SECONDARY, Set.of(FocusTargetKind.BLOCK), FocusCastMode.INSTANT,
                        ResearchRequirement.none(), 16, 0, ActionPresentationAudience.CONFIRMED_ALL),
                ModFocuses::cosmosCast);
        FocusActionApi.register(new FocusActionSpec(
                        rl("focus/guardian"), new FocusActionTarget.Focus(GUARDIAN_ID),
                        FocusInput.SECONDARY, Set.of(FocusTargetKind.AIR, FocusTargetKind.BLOCK),
                        FocusCastMode.INSTANT, ResearchRequirement.none(), 8, 0,
                        ActionPresentationAudience.CONFIRMED_ALL),
                ModFocuses::guardianCast);
        FocusActionApi.register(new FocusActionSpec(
                        rl("focus/teleport"), new FocusActionTarget.Focus(TELEPORT_ID),
                        FocusInput.SECONDARY, Set.of(FocusTargetKind.AIR, FocusTargetKind.BLOCK),
                        FocusCastMode.INSTANT, ResearchRequirement.none(), 8, 0,
                        ActionPresentationAudience.CONFIRMED_ALL),
                TeleportCore::cast);
    }

    // ------------------------------------------------------------------
    // 镇狱碎青峰：目标方块直接化为掉落物
    // ------------------------------------------------------------------

    private static FocusActionResult cosmosCast(dev.tc4port.thaumcraft.api.focus.action.FocusActionContext context,
                                                VisAction action) {
        if (!(context.authoritativeHit() instanceof BlockHitResult hit)) {
            return FocusActionResult.FAILED;
        }
        BlockPos pos = hit.getBlockPos();
        if (context.player().serverLevel().getBlockState(pos).isAir()) {
            return FocusActionResult.FAILED;
        }
        // ★ 第六十九批：这里原先**没有**付魔 —— 本体的付费是「计划式」的：
        // 处理函数必须在 SIMULATE 里调 pay() 把价码记进计划，引擎才知道要扣多少
        // （PaymentPlan.commitDebit 是照 calls 列表累计的）。不调 pay = 计划为空 = 白嫖。
        // 现在的写法与 TeleportCore 完全一致：两趟都调同一个价码，两趟结果必然一致。
        VisPaymentResult paid = context.pay(context.configuredCost(), action);
        if (action == VisAction.SIMULATE) {
            if (paid != VisPaymentResult.AVAILABLE) {
                notifyNoVis(context.player());
                return FocusActionResult.FAILED;
            }
            return FocusActionResult.SUCCESS;
        }
        if (paid != VisPaymentResult.CONSUMED) {
            return FocusActionResult.FAILED;
        }
        breakIntoDrops(context.player(), pos);
        return FocusActionResult.SUCCESS;
    }

    /**
     * 方块本体优先化为掉落物：不管该方块正常挖掘掉不掉东西（基岩、叶子等也照掉），
     * 直接弹出方块自己的物品；方块没有物品形态（流体、传送门等）才退回掉落表（精准采集上下文）。
     */
    private static void breakIntoDrops(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return;
        }
        level.removeBlock(pos, false);
        level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, pos, Block.getId(state));
        Item blockItem = state.getBlock().asItem();
        if (blockItem != Items.AIR) {
            Block.popResource(level, pos, new ItemStack(blockItem));
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(pos);
        ItemStack silkTool = new ItemStack(Items.NETHERITE_PICKAXE);
        var enchantments = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        silkTool.enchant(enchantments.wrapAsHolder(enchantments.getOrThrow(Enchantments.SILK_TOUCH)), 1);
        var drops = state.getDrops(new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL, silkTool)
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, blockEntity));
        drops.forEach(drop -> Block.popResource(level, pos, drop));
    }

    // ------------------------------------------------------------------
    // 乾元罡气：获得免死之心
    // ------------------------------------------------------------------

    private static FocusActionResult guardianCast(dev.tc4port.thaumcraft.api.focus.action.FocusActionContext context,
                                                  VisAction action) {
        // ★ 第六十九批：同样补上付魔（理由见 cosmosCast 的注释）。
        VisPaymentResult paid = context.pay(context.configuredCost(), action);
        if (action == VisAction.SIMULATE) {
            if (paid != VisPaymentResult.AVAILABLE) {
                notifyNoVis(context.player());
                return FocusActionResult.FAILED;
            }
            return FocusActionResult.SUCCESS;
        }
        if (paid != VisPaymentResult.CONSUMED) {
            return FocusActionResult.FAILED;
        }
        ServerPlayer player = context.player();
        player.setData(GUARD_HEARTS, player.getData(GUARD_HEARTS) + 1);
        player.serverLevel().playSound(null, player.blockPosition(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.3F);
        return FocusActionResult.SUCCESS;
    }

    /**
     * 要素不够时给玩家的提示（**四路法核 + 两台玄枢机器共用一个短句**）。
     *
     * <p>★ 第一百批：用户「法杖要素不足的提示改为：法杖要素不足」——
     * 原先每个核心各一条「要素不足：某某要六种要素各 N 点」，现在统一成
     * {@code message.skydeitymysticpivot.no_vis} 一个键，四个旧键（三个核心 + 奥术台）全删。
     * 具体价码请看法核物品的 lore（那儿写着「各 N 点」），提示本身不再报数。</p>
     */
    static void notifyNoVis(ServerPlayer player) {
        player.displayClientMessage(Component.translatable(
                "message.skydeitymysticpivot.no_vis").withStyle(ChatFormatting.RED), true);
        player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.6F, 0.7F);
    }

    /** 免死：拦截一次死亡，消耗一颗心并回满血（SakuraFall 同款 {@code LivingDeathEvent} 路线）。 */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        int hearts = player.getData(GUARD_HEARTS);
        if (hearts <= 0) {
            return;
        }
        player.setData(GUARD_HEARTS, hearts - 1);
        event.setCanceled(true);
        player.setHealth(player.getMaxHealth());
        player.clearFire();
        level.playSound(null, player.blockPosition(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 0.8F);
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MysticPivot.MODID, path);
    }
}
