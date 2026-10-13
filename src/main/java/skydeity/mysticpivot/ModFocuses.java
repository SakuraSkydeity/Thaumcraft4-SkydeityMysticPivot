package skydeity.mysticpivot;

import com.mojang.serialization.Codec;
import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionApi;
import dev.tc4port.thaumcraft.api.focus.FocusId;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionSpec;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionTarget;
import dev.tc4port.thaumcraft.api.focus.action.FocusActivation;
import dev.tc4port.thaumcraft.api.focus.action.FocusCastAnimation;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionResult;
import dev.tc4port.thaumcraft.api.presentation.ActionPresentationAudience;
import dev.tc4port.thaumcraft.api.research.ResearchRequirement;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
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
 * <h2>一、寰宇（{@code focus_cosmos}）</h2>
 * <p>右键方块：消耗六基础元素各 1 点，把目标方块<b>直接变成掉落物</b>——
 * 以带精准采集的下界合金镐为工具上下文取掉落表，无视方块硬度与挖掘等级。</p>
 *
 * <h2>二、玄枢守护（{@code focus_guardian}）</h2>
 * <p>右键：消耗六基础元素各 200 点，获得一颗绕玩家旋转的守护之心；
 * 死亡时拦截一次致死事件（免死，复用 SakuraFall 的 {@code LivingDeathEvent} 取消路线），
 * 随后心消失。守护之心没有时间限制，且可叠加。</p>
 *
 * <h2>三、传送（{@code focus_teleport}）</h2>
 * <p>潜行右键记点、右键耗六基础元素各 10 点回到记下的那一点（行为在
 * {@link TeleportCore}）。</p>
 *
 * <p>三核心的魔耗档案由数据包提供（六卦各 128 的合成见 recipe），动作在此注册。
 * 守护之心挂在玩家 {@link AttachmentType} 上，随存档持久化。</p>
 */
@EventBusSubscriber(modid = MysticPivot.MODID)
public final class ModFocuses {

    /** 寰宇核心的行为 id（同时是数据包档案文件 skydeitymysticpivot:cosmos 的资源 id）。 */
    public static final FocusId COSMOS_ID = FocusId.of(rl("cosmos"));

    /** 玄枢守护核心的行为 id（对应数据包档案 skydeitymysticpivot:guardian）。 */
    public static final FocusId GUARDIAN_ID = FocusId.of(rl("guardian"));

    /** 传送核心的行为 id（对应数据包档案 skydeitymysticpivot:teleport）。 */
    public static final FocusId TELEPORT_ID = FocusId.of(rl("teleport"));

    /** 守护之心数量（可叠加；免死一次消耗一颗）。 */
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MysticPivot.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Integer>> GUARD_HEARTS =
            ATTACHMENTS.register("guard_hearts",
                    () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).build());

    private ModFocuses() {
    }

    /** mod 构造期注册两个核心的右键动作。 */
    public static void register() {
        FocusActionApi.register(new FocusActionSpec(
                        rl("focus/cosmos"), new FocusActionTarget.Focus(COSMOS_ID),
                        Set.of(FocusActivation.USE_BLOCK), ResearchRequirement.none(),
                        16, 0, FocusCastAnimation.INSTANT, ActionPresentationAudience.CONFIRMED_ALL),
                ModFocuses::cosmosCast);
        FocusActionApi.register(new FocusActionSpec(
                        rl("focus/guardian"), new FocusActionTarget.Focus(GUARDIAN_ID),
                        Set.of(FocusActivation.USE_AIR, FocusActivation.USE_BLOCK), ResearchRequirement.none(),
                        8, 0, FocusCastAnimation.INSTANT, ActionPresentationAudience.CONFIRMED_ALL),
                ModFocuses::guardianCast);
        FocusActionApi.register(new FocusActionSpec(
                        rl("focus/teleport"), new FocusActionTarget.Focus(TELEPORT_ID),
                        Set.of(FocusActivation.USE_AIR, FocusActivation.USE_BLOCK), ResearchRequirement.none(),
                        8, 0, FocusCastAnimation.INSTANT, ActionPresentationAudience.CONFIRMED_ALL),
                TeleportCore::cast);
    }

    // ------------------------------------------------------------------
    // 寰宇：目标方块直接化为掉落物
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
        if (action == VisAction.EXECUTE) {
            breakIntoDrops(context.player(), pos);
        }
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
    // 玄枢守护：获得免死之心
    // ------------------------------------------------------------------

    private static FocusActionResult guardianCast(dev.tc4port.thaumcraft.api.focus.action.FocusActionContext context,
                                                  VisAction action) {
        if (action == VisAction.EXECUTE) {
            ServerPlayer player = context.player();
            player.setData(GUARD_HEARTS, player.getData(GUARD_HEARTS) + 1);
            player.serverLevel().playSound(null, player.blockPosition(),
                    SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0F, 1.3F);
        }
        return FocusActionResult.SUCCESS;
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
