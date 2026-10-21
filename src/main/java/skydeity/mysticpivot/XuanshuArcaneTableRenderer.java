package skydeity.mysticpivot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.tc4port.thaumcraft.api.client.ThaumcraftClientRenderApi;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * 玄枢奥术台的台顶辰星（第一百四十四批）：模型里那枚钉死的静态星搬到这里，
 * 改成<b>绕竖轴缓缓自转</b>的一枚——三片交叉的星面（南北 / 东西两片立面 + 一片平躺）
 * 整组一起转，任何角度都看得到它在旋。
 *
 * <p>画法与星盘浮空星同一路数（三片零厚度四边形），贴图用台顶那枚
 * {@code xuanshu_arcane_star.png}（32×32 柔光四芒星：四条主臂 + 四条对角短芒
 * + 淡光晕 + 白热芯）；走本体的 {@code legacyLinearAdditiveBillboard} 加法混合 +
 * 全亮光照，星是<b>发光</b>的，夜里也看得清。星悬在台面上方，
 * 半宽 0.34 格（直径 0.68 格，第一百五十批放大），带轻微浮沉与呼吸缩放。</p>
 */
public class XuanshuArcaneTableRenderer implements BlockEntityRenderer<XuanshuArcaneTableBlockEntity> {

    /** 辰星贴图（16×16，透明底，十字星芒）。 */
    private static final ResourceLocation STAR =
            ResourceLocation.fromNamespaceAndPath(MysticPivot.MODID, "textures/block/xuanshu_arcane_star.png");

    /** 星面中心高度（格）：悬在 12 像素（0.75）台面上方，星放大了，中心跟着抬高。 */
    private static final float STAR_Y = 1.12F;

    /** 星的半宽（格）：直径 0.68 格（第 150 批由 0.48 放大四成），台顶的主角。 */
    private static final float STAR_HALF = 0.34F;

    /** 呼吸缩放幅度：±6%，安静地一涨一缩。 */
    private static final float BREATH_AMPLITUDE = 0.06F;

    /** 呼吸角速度（弧度 / tick）。 */
    private static final float BREATH_SPEED = 0.045F;

    /** 自转速度（度 / tick）：约 3 秒一圈，安静不晃眼。 */
    private static final float SPIN_DEGREES_PER_TICK = 1.2F;

    /** 星色（高饱和鎏金，第一百四十八批提艳：黄分量拉满、蓝压到最低）。 */
    private static final int GOLD_RED = 255;
    private static final int GOLD_GREEN = 205;
    private static final int GOLD_BLUE = 55;

    public XuanshuArcaneTableRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(XuanshuArcaneTableBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        float ticks = (level.getGameTime() % 720720L) + partialTick;
        // 轻微的上下浮沉（±0.02 格）、缓转与呼吸缩放，像悬在台面上的一粒辰星
        float y = STAR_Y + Mth.sin(ticks * 0.05F) * 0.02F;
        float half = STAR_HALF * (1.0F + BREATH_AMPLITUDE * Mth.sin(ticks * BREATH_SPEED));

        poseStack.pushPose();
        // 绕台面中心自转（先挪中心、转、再挪回去，星面坐标不变）
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(ticks * SPIN_DEGREES_PER_TICK));
        poseStack.translate(-0.5D, 0.0D, -0.5D);

        PoseStack.Pose pose = poseStack.last();
        VertexConsumer vertices = buffer.getBuffer(ThaumcraftClientRenderApi.legacyLinearAdditiveBillboard(STAR));
        // 南北立面
        quad(vertices, pose,
                0.5F - half, y - half, 0.5F,
                0.5F + half, y - half, 0.5F,
                0.5F + half, y + half, 0.5F,
                0.5F - half, y + half, 0.5F);
        // 东西立面
        quad(vertices, pose,
                0.5F, y - half, 0.5F - half,
                0.5F, y - half, 0.5F + half,
                0.5F, y + half, 0.5F + half,
                0.5F, y + half, 0.5F - half);
        // 平躺一片（俯视也是一颗星）
        quad(vertices, pose,
                0.5F - half, y, 0.5F - half,
                0.5F + half, y, 0.5F - half,
                0.5F + half, y, 0.5F + half,
                0.5F - half, y, 0.5F + half);
        poseStack.popPose();
    }

    /** 一片贴满整张星图的四边形（四个角按序给，双面都画得出）。 */
    private static void quad(VertexConsumer vertices, PoseStack.Pose pose,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3) {
        vertex(vertices, pose, x0, y0, z0, 0.0F, 1.0F);
        vertex(vertices, pose, x1, y1, z1, 1.0F, 1.0F);
        vertex(vertices, pose, x2, y2, z2, 1.0F, 0.0F);
        vertex(vertices, pose, x3, y3, z3, 0.0F, 0.0F);
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose,
                               float x, float y, float z, float u, float v) {
        vertices.addVertex(pose, x, y, z)
                .setColor(GOLD_RED, GOLD_GREEN, GOLD_BLUE, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
