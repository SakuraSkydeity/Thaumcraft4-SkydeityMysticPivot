package skydeity.mysticpivot;

import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.aspect.VisPaymentResult;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionContext;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionResult;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 「法杖核心:传送」的行为（{@code focus_teleport}，装到任意杖柄上使用）。
 *
 * <ul>
 *   <li><b>潜行右键</b>：把当前脚下坐标记进这柄杖（位置 + 维度都记，跟杖走，换手也带着）。</li>
 *   <li><b>右键</b>：从杖里抽出六种原始要素各 {@value #VIS_PER_CHANNEL} 点，
 *       把玩家送到记下的那一点。</li>
 * </ul>
 *
 * <p>记点<b>不耗魔</b>、传送才耗魔：档案里的 {@code base_cost} 只是给界面显示用的价码，
 * 真正扣费交给 {@link FocusActionContext#pay}——只有在「传送」这条路上才调用它。
 * 本体要求同一个动作在 {@code SIMULATE} 与 {@code EXECUTE} 两趟里给出同一结果，
 * 因此落点安全、维度存在性与够不够魔都在 {@code SIMULATE} 里先算一遍；
 * 两趟之间不隔 tick，算得的结果必然一致。</p>
 *
 * <p>落点安全：记录的是当时的站立坐标；若那块地方后来被填成了石头，
 * 会向上找最多 {@value #SAFE_SEARCH_UP} 格找一个落脚点，找不到就不动（宁可不去，也不埋人）。</p>
 */
public final class TeleportCore {

    /** 每种原始要素各扣多少点。 */
    public static final int VIS_PER_CHANNEL = 10;

    /** 落点被堵时最多往上找几格。 */
    private static final int SAFE_SEARCH_UP = 4;

    private static final String TAG_SET = "SkydeityTeleportSet";
    private static final String TAG_X = "SkydeityTeleportX";
    private static final String TAG_Y = "SkydeityTeleportY";
    private static final String TAG_Z = "SkydeityTeleportZ";
    private static final String TAG_DIM = "SkydeityTeleportDim";

    private TeleportCore() {
    }

    /**
     * 核心的右键动作（{@link ModFocuses#register()} 注册进本体）。
     *
     * <p>{@code action} 会随本体在两趟里分别传 {@code SIMULATE} 与 {@code EXECUTE}；
     * 两趟必须走同一条分支、返回同一个结果，这里靠「先判潜行、再判记点/传送」保证。</p>
     */
    public static FocusActionResult cast(FocusActionContext context, VisAction action) {
        ServerPlayer player = context.player();
        ItemStack wand = context.wand();

        // 潜行 = 记点，不耗魔。
        if (player.isShiftKeyDown()) {
            if (action == VisAction.EXECUTE) {
                record(player, wand);
            }
            return FocusActionResult.SUCCESS;
        }

        CompoundTag tag = readTag(wand);
        if (tag == null || !tag.getBoolean(TAG_SET)) {
            if (action == VisAction.SIMULATE) {
                message(player, Component.translatable(
                        "message.skydeitymysticpivot.teleport.none").withStyle(ChatFormatting.GRAY));
                failSound(player);
            }
            return FocusActionResult.FAILED;
        }

        ResourceLocation dimId = ResourceLocation.tryParse(tag.getString(TAG_DIM));
        ServerLevel target = resolveLevel(player, dimId);
        Vec3 landing = target == null ? null
                : findLanding(target, tag.getDouble(TAG_X), tag.getDouble(TAG_Y), tag.getDouble(TAG_Z));
        if (landing == null) {
            if (action == VisAction.SIMULATE) {
                message(player, Component.translatable(target == null
                        ? "message.skydeitymysticpivot.teleport.dim_missing"
                        : "message.skydeitymysticpivot.teleport.blocked").withStyle(ChatFormatting.RED));
                failSound(player);
            }
            return FocusActionResult.FAILED;
        }

        // 价码由档案的 base_cost 给出（六基元各 10 点），实际扣费在这里发生。
        VisPaymentResult paid = context.pay(context.configuredCost(), action);
        if (action == VisAction.SIMULATE) {
            if (paid != VisPaymentResult.AVAILABLE) {
                message(player, Component.translatable(
                        "message.skydeitymysticpivot.teleport.no_vis", VIS_PER_CHANNEL)
                        .withStyle(ChatFormatting.RED));
                failSound(player);
                return FocusActionResult.FAILED;
            }
            return FocusActionResult.SUCCESS;
        }
        if (paid != VisPaymentResult.CONSUMED) {
            return FocusActionResult.FAILED;
        }

        sourceFx(player);
        player.teleportTo(target, landing.x, landing.y, landing.z, player.getYRot(), player.getXRot());
        targetFx(target, landing);
        return FocusActionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ 记点

    private static void record(ServerPlayer player, ItemStack wand) {
        ServerLevel level = player.serverLevel();
        Vec3 pos = player.position();
        ResourceLocation dim = level.dimension().location();
        CustomData.update(DataComponents.CUSTOM_DATA, wand, tag -> {
            tag.putBoolean(TAG_SET, true);
            tag.putDouble(TAG_X, pos.x);
            tag.putDouble(TAG_Y, pos.y);
            tag.putDouble(TAG_Z, pos.z);
            tag.putString(TAG_DIM, dim.toString());
        });
        message(player, Component.translatable(
                "message.skydeitymysticpivot.teleport.recorded",
                fmt(pos), Component.translatable(dimKey(dim)).withStyle(ChatFormatting.AQUA)));
        level.playSound(null, player.blockPosition(), SoundEvents.LODESTONE_COMPASS_LOCK,
                SoundSource.PLAYERS, 0.7F, 1.4F);
        level.sendParticles(ParticleTypes.PORTAL,
                pos.x, pos.y + 1.0D, pos.z, 24, 0.4D, 0.6D, 0.4D, 0.08D);
    }

    // ------------------------------------------------------------------ 工具

    private static CompoundTag readTag(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? null : data.copyTag();
    }

    private static ServerLevel resolveLevel(ServerPlayer player, ResourceLocation dimId) {
        if (dimId == null) {
            return null;
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, dimId);
        return player.server.getLevel(key);
    }

    /**
     * 找一个落脚点：优先原坐标，被堵就往上找（最多 {@value #SAFE_SEARCH_UP} 格）。
     * 判定「站得住」= 脚位、头位与脚下都不是固体（方块本身要可碰撞才算堵）。
     */
    private static Vec3 findLanding(ServerLevel level, double x, double y, double z) {
        BlockPos base = BlockPos.containing(x, y, z);
        for (int up = 0; up <= SAFE_SEARCH_UP; up++) {
            BlockPos feet = base.above(up);
            if (!blocked(level, feet) && !blocked(level, feet.above())
                    && !blocked(level, feet.below())) {
                return new Vec3(x, feet.getY(), z);
            }
        }
        return null;
    }

    private static boolean blocked(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    private static void sourceFx(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.9F, 1.0F);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL,
                player.getX(), player.getY() + 1.0D, player.getZ(), 30, 0.3D, 0.6D, 0.3D, 0.05D);
    }

    private static void targetFx(ServerLevel level, Vec3 at) {
        level.playSound(null, BlockPos.containing(at), SoundEvents.ENDERMAN_TELEPORT,
                SoundSource.PLAYERS, 0.9F, 1.2F);
        level.sendParticles(ParticleTypes.PORTAL,
                at.x, at.y + 1.0D, at.z, 40, 0.4D, 0.7D, 0.4D, 0.12D);
    }

    private static void message(ServerPlayer player, Component text) {
        player.displayClientMessage(text, true);
    }

    private static void failSound(ServerPlayer player) {
        player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.6F, 0.7F);
    }

    private static String fmt(Vec3 pos) {
        return String.format("%d, %d, %d", (int) Math.floor(pos.x), (int) Math.floor(pos.y),
                (int) Math.floor(pos.z));
    }

    private static String dimKey(ResourceLocation dim) {
        return "dimension." + dim.getNamespace() + "." + dim.getPath().replace('/', '.');
    }
}