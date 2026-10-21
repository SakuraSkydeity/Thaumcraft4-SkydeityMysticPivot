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
 * 玄枢复刻台台顶的辰星（第一百四十五批）：原先钉在模型里的三片交叉平面
 * （十字纹理、cutout 硬边）搬到这里，改成与奥术台同款的<b>自转发光星</b>——
 * 贴图换成 32×32 柔光四芒星（白热芯 + 对角短芒 + 淡光晕），顶点色上紫色
 * （与复刻台双晶、走线纹同一族），加法混合夜里也透亮。
 *
 * <p>高度悬在 12 像素台面上方，半宽 0.34 格（与奥术台同尺寸），
 * 带浮沉与呼吸缩放；转向与奥术台<b>相反</b>（-1.2°/tick），
 * 两台摆在一起时一顺一逆更有生气。</p>
 *
 * <p>★ 第一百五十三批：用户「玄枢复刻台特效改为蓝绿色，那种科技的颜色」——
 * 星色由紫改<b>蓝绿</b>（{@code #18E8D6}），与头顶引导线的蓝绿同一色，
 * 台上那颗星与头顶那根线这才是一套。</p>
 */
public class XuanshuReplicatorRenderer implements BlockEntityRenderer<XuanshuReplicatorBlockEntity> {

    /** 辰星贴图（16×16 透明底十字星芒，纯白由顶点色上色）。 */
    private static final ResourceLocation STAR =
            ResourceLocation.fromNamespaceAndPath(MysticPivot.MODID, "textures/block/xuanshu_replicator_star.png");

    /** 星面中心高度（格）：悬在台面上方，星放大了，中心跟着抬高。 */
    private static final float STAR_Y = 1.12F;

    /** 星的半宽（格）：直径 0.68 格（第 150 批与奥术台同步放大）。 */
    private static final float STAR_HALF = 0.34F;

    /** 呼吸缩放幅度：±6%。 */
    private static final float BREATH_AMPLITUDE = 0.06F;

    /** 呼吸角速度（弧度 / tick）。 */
    private static final float BREATH_SPEED = 0.045F;

    /** 自转速度（度 / tick）：与奥术台同速、反向。 */
    private static final float SPIN_DEGREES_PER_TICK = -1.2F;

    /** 星色（高饱和蓝绿，第一百五十三批由紫改蓝绿，与引导线蓝绿同族）。 */
    private static final int TEAL_RED = 24;
    private static final int TEAL_GREEN = 232;
    private static final int TEAL_BLUE = 214;

    public XuanshuReplicatorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(XuanshuReplicatorBlockEntity be, float partialTick, PoseStack poseStack,
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
                .setColor(TEAL_RED, TEAL_GREEN, TEAL_BLUE, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
