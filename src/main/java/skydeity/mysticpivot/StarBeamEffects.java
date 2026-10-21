package skydeity.mysticpivot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.tc4port.thaumcraft.api.client.ThaumcraftClientRenderApi;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作方块头顶的星象引导线（第一百四十四批重做；第一百五十批按用户要求改回这一版）。
 *
 * <p>★ 第一百四十九批曾把它换成本体 {@code renderBeam} 的三片自转光束，用户看过之后
 * 要「特效改回之前的」——所以这里回到第一百四十八批的<b>加法广告牌</b>：
 * 单片恒朝镜头的竖直四边形 + {@code beam.png}，外圈淡晕裹一根浓芯，
 * 底 1.02 / 顶 5.5、半宽 0.62。几何全在本类里，一个顶点都不借本体的光束。</p>
 *
 * <h2>为什么要从方块实体渲染器里搬出来</h2>
 * <p>1.21.1 里方块实体只有<b>它所在的 16³ 区段仍在视锥里</b>才会被渲染。
 * 引导线从机器头顶拉到 y+5.5，渲染它的却永远是<b>地面那一格星盘</b>的区段——
 * 玩家一抬头，星盘的区段退出视锥，整段渲染（包括高高在上的光柱）一起消失。
 * 用户：「机器的光头往上抬就是消失」——病根就是区段剔除，画得再高也没用。</p>
 *
 * <h2>新画法：走本体 TC 的「关卡后世界渲染」口子</h2>
 * <p>TC 公开 API
 * {@link ThaumcraftClientRenderApi#beginAfterLevelWorldRender(Matrix4f, Matrix4f, boolean)}
 * 正是为这种「不受区段剔除的后期世界特效」准备的（本体自己的焦点光束、灵气节点汲魔光
 * 都走这一路）：挂 {@link RenderLevelStageEvent} 的 {@code AFTER_LEVEL} 阶段，
 * 在 TC 的渲染状态作用域里提交顶点、当场冲刷。这一阶段<b>不看区段</b>，
 * 光柱永不消失；{@code depthTested=true} 保留场景深度，隔着地形依旧被挡住。</p>
 *
 * <h2>光柱本体：恒朝镜头的单片广告牌</h2>
 * <p>旧版是两片十字交叉立面——水平看是光柱，视角一抬两片都成了刀刃，线就细没了。
 * 新版每根线只画<b>一片永远正对镜头的竖直广告牌</b>（绕 Y 轴转向镜头，保持竖直）：
 * 任何水平角都是整幅宽，配合 {@code beam.png} 的竖向衰减，顶端照旧收成烟。</p>
 *
 * <h2>第一百四十八批：换加法发光（additive）</h2>
 * <p>引导线原先走 {@code legacyLinearTranslucentBillboard}（半透明深度混合），
 * 第一百四十八批换成 {@code legacyLinearAdditiveBillboard}——与台上辰星、台座符纹同一路
 * 加法叠加：光柱<b>自发光</b>、越叠越亮，夜里像从星位透出的光，不再是「贴在场景上的灰膜」。
 * 加法下两层（外圈淡晕 + 内芯浓光）会在正中自然叠出一根更亮的光芯。</p>
 *
 * <h2>阵心的发现与校验</h2>
 * <p>这一阶段没有方块实体上下文，阵心坐标由星盘渲染器在盘面可见时「报到」缓存里
 * （{@link #touch}）；每帧渲染前逐一校验——阵还在（R1C1、已成型）才画，
 * 阵被拆了当场从缓存里除名。离镜头太远的（超出引导线视距）跳过不画。</p>
 */
@EventBusSubscriber(modid = MysticPivot.MODID, value = Dist.CLIENT)
public final class StarBeamEffects {

    /** 光柱贴图（本体 {@code beam.png}，32×64）：底亮顶渐隐，与旧版同一条。 */
    private static final ResourceLocation BEAM =
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/beam.png");

    /** 光柱起止高度与外圈半宽（与第一百四十二批定稿一致）。 */
    private static final float BEAM_BOTTOM = 1.02F;
    private static final float BEAM_TOP = 5.5F;
    private static final float BEAM_HALF = 0.62F;

    /**
     * 内芯半宽与两层各自的浓度：一圈外晕裹一根浓芯。
     *
     * <p>★ 第一百五十三批：用户「特效白色的太淡了，换个金色，然后颜色浓度全部都高一点」。
     * 上一版是 0.34 / 0.66，加法叠加下远处几乎糊成一层灰白；这里抬到 0.52 / 1.0，
     * 内芯直接吃满 alpha（{@code 255}），外晕也跟着厚一档，四色才「立得住」。</p>
     */
    private static final float CORE_HALF = 0.28F;
    private static final float OUTER_ALPHA = 0.52F;
    private static final float CORE_ALPHA = 1.0F;

    /** 引导线视距（平方）：星盘渲染器的默认视距是 64，这里跟它对齐。 */
    private static final double VIEW_RADIUS_SQR = 64.0D * 64.0D;

    /** 缓存清理半径（平方）：比视距远一倍的阵心直接除名，缓存不无界长。 */
    private static final double PRUNE_RADIUS_SQR = 128.0D * 128.0D;

    /** 已见过的阵心（key = pos.asLong()）。渲染器每帧报到，这里每帧校验。 */
    private static final Map<Long, BlockPos> ANCHORS = new HashMap<>();

    private StarBeamEffects() {
    }

    /** 星盘渲染器在阵心可见时调用：把阵心记进缓存（只进不出，校验在后）。 */
    public static void touch(Level level, BlockPos anchor) {
        ANCHORS.put(anchor.asLong(), anchor.immutable());
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        if (level == null || ANCHORS.isEmpty()) {
            return;
        }
        Camera camera = event.getCamera();
        Vec3 camPos = camera.getPosition();

        // 1) 收集：校验缓存里的阵心，够近的才收集它头顶的引导线
        List<Beam> beams = new ArrayList<>();
        List<Long> dead = null;
        for (Map.Entry<Long, BlockPos> entry : ANCHORS.entrySet()) {
            BlockPos anchor = entry.getValue();
            double distSqr = anchor.getCenter().distanceToSqr(camPos);
            if (distSqr > PRUNE_RADIUS_SQR) {
                if (dead == null) {
                    dead = new ArrayList<>();
                }
                dead.add(entry.getKey());
                continue;
            }
            if (distSqr > VIEW_RADIUS_SQR) {
                continue;
            }
            if (!collectAnchor(level, anchor, beams)) {
                if (dead == null) {
                    dead = new ArrayList<>();
                }
                dead.add(entry.getKey());
            }
        }
        if (dead != null) {
            for (Long key : dead) {
                ANCHORS.remove(key);
            }
        }
        if (beams.isEmpty()) {
            return;
        }

        // 2) 画：TC 的关卡后世界渲染作用域（保留深度测试），提交完当场冲刷这一种类型
        float ticks = (level.getGameTime() % 720720L) + event.getPartialTick().getGameTimeDeltaPartialTick(false);
        // 呼吸底值：上一版 180±30，抬到 214±38（176~252），整体更实
        float alphaBase = 214.0F + Mth.sin(ticks * 0.06F) * 38.0F;
        // 恒朝镜头的「右」向量：把镜头水平视线转 90°，光柱就不会随视角缩成一条线
        org.joml.Vector3f look = camera.getLookVector();
        float lx = look.x();
        float lz = look.z();
        float len = Mth.sqrt(lx * lx + lz * lz);
        if (len < 1.0E-4F) {
            lx = 0.0F;
            lz = 1.0F;
            len = 1.0F;
        }
        float rightX = lz / len * BEAM_HALF;
        float rightZ = -lx / len * BEAM_HALF;
        float coreRightX = rightX * (CORE_HALF / BEAM_HALF);
        float coreRightZ = rightZ * (CORE_HALF / BEAM_HALF);

        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        var renderType = ThaumcraftClientRenderApi.legacyLinearAdditiveBillboard(BEAM);
        try (var ignored = ThaumcraftClientRenderApi.beginAfterLevelWorldRender(
                event.getModelViewMatrix(), event.getProjectionMatrix(), true)) {
            VertexConsumer vertices = buffers.getBuffer(renderType);
            PoseStack poseStack = event.getPoseStack();
            for (Beam beam : beams) {
                poseStack.pushPose();
                poseStack.translate(
                        beam.x() + 0.5D - camPos.x,
                        beam.y() - camPos.y,
                        beam.z() + 0.5D - camPos.z);
                billboard(vertices, poseStack.last(), rightX, rightZ,
                        beam.red(), beam.green(), beam.blue(), (int) (alphaBase * OUTER_ALPHA));
                billboard(vertices, poseStack.last(), coreRightX, coreRightZ,
                        beam.red(), beam.green(), beam.blue(), (int) (alphaBase * CORE_ALPHA));
                poseStack.popPose();
            }
            buffers.endBatch(renderType);
        }
    }

    /** 一座阵心：还立着（R1C1 且成型）就收集外圈里亮着的引导线；拆了返回 false 除名。 */
    private static boolean collectAnchor(Level level, BlockPos anchor, List<Beam> beams) {
        BlockEntity be = level.getBlockEntity(anchor);
        if (!(be instanceof StarDialBlockEntity)) {
            return false;
        }
        BlockState state = level.getBlockState(anchor);
        if (!(state.getBlock() instanceof StarDialBlock)
                || state.getValue(StarDialBlock.ROLE) != StarDialBlock.Role.R1C1
                || !state.getValue(StarDialBlock.ROLE).formed()) {
            return false;
        }
        Constellation constellation = StarWork.at(level, anchor);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 3; dz++) {
                if (!StarWork.isRing(dx, dz)) {
                    continue;
                }
                BlockPos cell = anchor.offset(dx, 0, dz);
                if (level.getBlockEntity(cell) instanceof StarReader reader
                        && reader.guideActive(constellation)) {
                    int color = reader.guideColor();
                    beams.add(new Beam(cell.getX(), cell.getY(), cell.getZ(),
                            color >> 16 & 255, color >> 8 & 255, color & 255));
                }
            }
        }
        return true;
    }

    /**
     * 一根广告牌光柱：单片竖直四边形，底亮顶渐隐（顶端 alpha 归零，收口像烟散掉）。
     *
     * @param rightX 光柱半宽沿 X 的分量（恒朝镜头的水平向量，渲染前算好全帧共用）
     * @param rightZ 光柱半宽沿 Z 的分量
     */
    private static void billboard(VertexConsumer vertices, PoseStack.Pose pose,
                                  float rightX, float rightZ,
                                  int red, int green, int blue, int alpha) {
        int bottom = Mth.clamp(alpha, 0, 255);
        vertex(vertices, pose, -rightX, BEAM_BOTTOM, -rightZ, 0.0F, 1.0F, red, green, blue, bottom);
        vertex(vertices, pose, rightX, BEAM_BOTTOM, rightZ, 1.0F, 1.0F, red, green, blue, bottom);
        vertex(vertices, pose, rightX, BEAM_TOP, rightZ, 1.0F, 0.0F, red, green, blue, 0);
        vertex(vertices, pose, -rightX, BEAM_TOP, -rightZ, 0.0F, 0.0F, red, green, blue, 0);
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose,
                               float x, float y, float z, float u, float v,
                               int red, int green, int blue, int alpha) {
        vertices.addVertex(pose, x, y, z)
                .setColor(red, green, blue, Mth.clamp(alpha, 0, 255))
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    /** 一根待画的光柱：方块世界坐标 + 引导线颜色。 */
    private record Beam(float x, float y, float z, int red, int green, int blue) {
    }
}
