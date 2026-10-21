package skydeity.mysticpivot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.tc4port.thaumcraft.api.client.ThaumcraftClientRenderApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * 玄枢台座渲染，三件事：
 *
 * <ol>
 *   <li><b>气脉丝带</b>：成坛之后，四隅的四象石砖各有一缕顺滑的光带搭上台座顶心
 *       （{@link #renderConnections}）——照本体奥术充能基座那条 wispy 丝带的画法，
 *       每段两条交叉四边形、中段亮两端淡、缓缓摆动，颜色取本地卦气的象色。
 *       砖被挖掉则对应那一条随之消失；</li>
 *   <li><b>台面物品</b>：悬浮、缓转、轻微上下浮动（与本体奥术基座「物品浮于座上」的观感一致）；</li>
 *   <li><b>卦象之名</b>：台座所在处凝着哪几卦的气（{@link Fengshui#qiAll}），就把那几个字
 *       各以象色悬在方块<b>上方约第三层</b>（{@code y+2.25}）——石立何处、气便为何，一眼可辨。
 *       字面恒朝向镜头（billboard），并用「穿透可见」的字型，隔着方块也读得到。
 *       <b>只在台座已开坛（四方四象石砖立齐、以法杖点过）时才显</b>：卦象是坛成之后
 *       才起的东西，普通一座台座头上不长字。</li>
 * </ol>
 *
 * <p>卦气在客户端也能算（群系与高度图客户端都有），所以这里直接读，不必走网络同步。</p>
 */
public class XuanshuPedestalRenderer implements BlockEntityRenderer<XuanshuPedestalBlockEntity> {

    /** 名字相对方块的水平中心。 */
    private static final double CENTER = 0.5D;
    /**
     * 名字的基线高度（相对方块底面）。
     *
     * <p>方块占 {@code y .. y+1}，所以「方块上方第三层」= {@code 2.25}。文字是从基线
     * <b>向上</b>长出来的（高约 {@code 9px × LABEL_SCALE ≈ 0.50} 格），基线取 2.25 时
     * 字面落在 {@code y+2.25} 上下——比上一版的 2.00 又抬了一档，离台面物品更远。</p>
     */
    private static final double LABEL_Y = 2.25D;
    /**
     * 字面缩放。
     *
     * <p>原版 nameTag 用 0.025；卦象单字从 0.085 一路收到 <b>0.055</b>（仍是名片的 2.2 倍），
     * 悬在高处小而清晰，不再抢台座本身的戏。</p>
     */
    private static final float LABEL_SCALE = 0.055F;
    /** 同时显多卦时的字距（像素）。 */
    private static final float LABEL_GAP = 2.0F;

    /**
     * 台面物品的放大倍数。
     *
     * <p>{@link ItemDisplayContext#GROUND} 自带的 display 变换只有 0.25 倍（约等于掉落物的尺寸），
     * 这里再乘一倍多，台上的东西才像个「供着的物事」而不是一粒渣。</p>
     */
    private static final float ITEM_SCALE = 0.8F;
    /** 台面物品的悬浮基准高度（台座顶面在 1.0）。 */
    private static final double ITEM_Y = 1.20D;

    /**
     * 气脉丝带的贴图——本体奥术充能基座（{@code WandRechargePedestal}）同款：
     * 一条横向的絮状流纹（512×128 的噪声带，亮暗相间），沿带子长度方向滚动时
     * 看着就是「絮在流」。
     */
    private static final ResourceLocation WISPY =
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/wispy.png");

    /**
     * 丝带半宽。
     *
     * <p>每一段画<b>两条交叉的四边形</b>（一柄横、一柄竖，各偏出半个带宽），
     * 从任何角度看都有一柄正对镜头——单条扁平带子侧看会缩成一条线，两条交叉才始终有体积。</p>
     */
    private static final float RIBBON_WIDTH = 0.15F;

    /** 每格距离切几段（段越多越顺滑，也越贵）。 */
    private static final float RIBBON_QUALITY = 4.0F;

    /**
     * UV 缓滚速度（tile / tick）。
     *
     * <p>本体丝带滚得急（约 0.67 tile/秒，那是「要素正被抽走」的赶法）；
     * 这条是坛成之后常驻的气脉，<b>0.16 tile/秒</b>的慢漂才像「一汪活水」。</p>
     */
    private static final float RIBBON_SCROLL = 0.008F;

    /**
     * UV 回绕周期（tile）——<b>必须是整数</b>。
     *
     * <p>缓滚量无限增长会吃掉 float 精度，所以要回绕；而回绕若落在半张贴图处，
     * 整条带子会「跳」一下。取<b>整数张</b>贴图：回绕前后采样到的纹素完全相同，
     * 这一跳便不存在。64 张 ≈ 400 秒才绕一圈，本就没人看得见。</p>
     */
    private static final float RIBBON_UV_WRAP = 64.0F;

    /**
     * 波动幅度（格）。
     *
     * <p>本体丝带是「从基座连到持杖的玩家」那种两三格以上的长带，幅度给到 0.5；
     * 台座到四隅砖只有一格多，0.5 的幅度会抖成一条蚯蚓——收到 <b>0.10</b>，
     * 留一点「絮状物在飘」的活气即可。</p>
     */
    private static final double RIBBON_WOBBLE = 0.10D;

    /**
     * UV 沿带子长度的压缩系数。
     *
     * <p>贴图里约两张「絮团」才铺满一格；本体长带原样铺开是「一串絮」，
     * 而这条带子总共一格多，压到 0.45 倍只露半张流纹——读起来才是<b>一缕</b>，
     * 与「连接更顺滑」的初衷一致。</p>
     */
    private static final float RIBBON_UV_SCALE = 0.45F;

    /** 丝带起点高度：四象石砖的顶面（砖顶在 1.0，抬两分不穿进砖里）。 */
    private static final double RIBBON_FROM_Y = 1.02D;
    /** 丝带终点高度：台座顶心（台面物品浮在 1.2，絮带就搭在它脚下）。 */
    private static final double RIBBON_TO_Y = 1.12D;

    /**
     * 台口的<b>金色丝带环</b>：<b>绕 X 轴斜置 26° 的椭圆</b>，长轴 1.10 格、短轴 0.82 格。
     *
     * <p>第 83 批替换原「附魔符文粒子环」——形状沿旧环：短长轴之比固定 0.82 / 1.10 ≈ 0.745，
     * 长轴 1.10 时两端探到相邻两格的上空，「环从盘在柱头变成笼住台口」；画法则复用四隅絮带
     * 那一套（同一张贴图、同一种交叉四边形、同一档 UV 缓滚）。不同的只是颜色
     * （<b>鎏金</b>）与「闭合」的采样方式。</p>
     */
    private static final double RING_A = 1.10D;
    private static final double RING_B = 0.82D;
    /** 环面倾角：绕 X 轴 26°（斜着盘在台座顶上）。 */
    private static final double RING_TILT = Math.toRadians(26.0D);
    /** 环心高度（台座顶面在 1.0）。 */
    private static final double RING_Y = 1.06D;
    /**
     * 环上的采样段数。
     *
     * <p>闭合环不能用「两端渐隐」——段与段首尾相接，整圈必须一次采样、逐段铺过去。
     * 24 段把周长（约 4.3 格）切到每段 0.18 格，比四隅絮带略密：环上没有端点遮挡接缝，
     * 密一点才看不出折线。</p>
     */
    private static final int RING_SEGMENTS = 24;
    /** 环色：鎏金（与乾卦的象色同源——「金色丝线」）。 */
    private static final int RING_COLOR = 0xE0B84A;
    /** 环上每点的透明度（闭合环无端点，全程一个值；流动感交给 UV 缓滚）。 */
    private static final float RING_ALPHA = 0.80F;
    /**
     * 环上铺几张 UV。
     *
     * <p>★ <b>必须是整数张</b>：环的 u 沿弧长从 0 涨到「总张数」，首尾相接处如果落在
     * 半张贴图上，接缝处的絮纹会错位一下（与 {@link #RIBBON_UV_WRAP} 同一个道理）。
     * 取 2 张：周长约 4.3 格，密度折合约 0.46 tile/格——与四隅絮带的
     * {@link #RIBBON_UV_SCALE} 同档。</p>
     */
    private static final float RING_UV_TILES = 2.0F;

    public XuanshuPedestalRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(XuanshuPedestalBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        long t = (level.getGameTime() % 720720L);
        float ticks = t + partialTick;

        renderConnections(be, level, ticks, poseStack, buffer, packedLight);
        renderRing(be, ticks, poseStack, buffer, packedLight);
        renderStoredItem(be, level, ticks, poseStack, buffer);
        renderTrigramName(be, level, poseStack, buffer);
    }

    /** 台面上的物品：浮起 + 缓转 + 上下轻浮。 */
    private void renderStoredItem(XuanshuPedestalBlockEntity be, Level level, float ticks,
                                  PoseStack poseStack, MultiBufferSource buffer) {
        ItemStack stack = be.getItems().getStackInSlot(XuanshuPedestalBlockEntity.SLOT);
        if (stack.isEmpty()) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(CENTER, ITEM_Y + Math.sin(ticks * 0.06D) * 0.03D, CENTER);
        poseStack.mulPose(Axis.YP.rotationDegrees(ticks * 1.1F));
        poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.GROUND,
                LevelRenderer.getLightColor(level, be.getBlockPos()),
                OverlayTexture.NO_OVERLAY, poseStack, buffer, level, 0);
        poseStack.popPose();
    }

    /**
     * 四象石砖 ↔ 台座的<b>气脉丝带</b>：成坛之后，四隅各有一缕顺滑的光带从砖顶搭上台座顶心。
     *
     * <h2>★ 为什么是「丝带」而不是粒子</h2>
     * <p>早年那版用附魔符文粒子在结构四周撒了一圈，看着是一堆散点——「连接」二字无从谈起。
     * 丝带是一条<b>连续的、会流动的</b>几何带子：每一段两条交叉四边形拼成一截，
     * 中段最亮、两端渐隐，整条还带一点缓慢的正弦摆动——这才是「连」。</p>
     *
     * <h2>★ 画法照本体奥术充能基座</h2>
     * <p>整条技法抄自本体 {@code WandRechargePedestalRenderer} 的 wispy 丝带：
     * {@link ThaumcraftClientRenderApi#legacyLinearAdditiveBillboard}（线性叠加、全亮、
     * 不写深度的 billboard 类型）+ {@code wispy.png} 流纹贴图 + 每段两柄交叉四边形 +
     * 两端 alpha 衰减 + UV 缓滚。所不同的只是幅度与 UV 密度按这条短带子重新收过
     * （见 {@link #RIBBON_WOBBLE}、{@link #RIBBON_UV_SCALE}）。</p>
     *
     * <p>颜色取<b>本地卦气的象色</b>（{@link Fengshui#color}）——与台座头顶悬着的卦象单字
     * 同一套配色：立在哪里，气是什么颜色，这一缕絮带就是什么颜色。一处不凝卦气时
     * （{@link Fengshui.Trigram#NONE}）落回白色。</p>
     *
     * <p>只画<b>确实立着四象石砖</b>的那几隅：砖被挖掉，对应那一条随之消失，
     * 不必等服务端把那座台座熄坛。</p>
     */
    private void renderConnections(XuanshuPedestalBlockEntity be, Level level, float ticks,
                                   PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // ★ 未成坛不起絮带：与卦气环、卦象单字同一条门禁。
        if (!be.isActive()) {
            return;
        }
        BlockPos pos = be.getBlockPos();
        int color = Fengshui.color(Fengshui.qiAt(level, pos));
        int red = color >> 16 & 255;
        int green = color >> 8 & 255;
        int blue = color & 255;
        VertexConsumer vertices = buffer.getBuffer(
                ThaumcraftClientRenderApi.legacyLinearAdditiveBillboard(WISPY));
        PoseStack.Pose pose = poseStack.last();
        for (int[] corner : XuanshuPedestalBlockEntity.CORNERS) {
            BlockPos brick = pos.offset(corner[0], 0, corner[1]);
            if (!level.getBlockState(brick).is(ModBlocks.SIXIANG_STONE_BRICK.get())) {
                continue;
            }
            renderOneRibbon(vertices, pose, pos, brick, ticks, red, green, blue, packedLight);
        }
    }

    /**
     * 台口的<b>金色丝带环</b>：一道闭合的鎏金丝线绕着台座顶转圈。
     *
     * <h2>★ 为什么是丝带而不是粒子</h2>
     * <p>原本这里漂着一圈附魔符文粒子。粒子是一颗一颗的点，一圈点读的是「散」，
     * 而「丝线」要的是「连」——换成与四隅絮带同源的画法后，一条连续的、会流动的
     * 带子把整圈铺满，UV 沿环滚动，「金线绕坛」才立得住。</p>
     *
     * <h2>★ 与四隅絮带的三处不同</h2>
     * <ul>
     *   <li><b>闭合</b>：整圈一次采样 {@value #RING_SEGMENTS} 个点、首尾相接，
     *       不做两端的渐隐（渐隐是给「砖 → 台座」的短带化进两端用的，环没有两端）；</li>
     *   <li><b>不动</b>：不加正弦摆动——环是这座坛的「定」，摆动的活气由四隅絮带负责；</li>
     *   <li><b>鎏金</b>：不用本地卦气的象色，固定鎏金——这一道是「坛」的结构光，
     *       与「卦气」是两层意思。</li>
     * </ul>
     *
     * <p>门禁与卦象单字、四隅絮带同一条：<b>未成坛不画</b>。</p>
     */
    private void renderRing(XuanshuPedestalBlockEntity be, float ticks, PoseStack poseStack,
                            MultiBufferSource buffer, int packedLight) {
        if (!be.isActive()) {
            return;
        }
        VertexConsumer vertices = buffer.getBuffer(
                ThaumcraftClientRenderApi.legacyLinearAdditiveBillboard(WISPY));
        PoseStack.Pose pose = poseStack.last();
        float textureOffset = (ticks * RIBBON_SCROLL) % RIBBON_UV_WRAP;

        // 先采一整圈的点（含回到起点的闭合点），弧长累计进 arcs[]
        double sinTilt = Math.sin(RING_TILT);
        double cosTilt = Math.cos(RING_TILT);
        double[] xs = new double[RING_SEGMENTS + 1];
        double[] ys = new double[RING_SEGMENTS + 1];
        double[] zs = new double[RING_SEGMENTS + 1];
        double[] arcs = new double[RING_SEGMENTS + 1];
        double arc = 0.0D;
        for (int index = 0; index <= RING_SEGMENTS; index++) {
            double angle = index * (Math.PI * 2.0D / RING_SEGMENTS);
            // 先在水平面上取椭圆，再整体绕 X 轴倾 26°：y 由短轴分量给出高度、z 被压短
            double ex = Math.cos(angle) * RING_A;
            double ez = Math.sin(angle) * RING_B;
            xs[index] = CENTER + ex;
            ys[index] = RING_Y + ez * sinTilt;
            zs[index] = CENTER + ez * cosTilt;
            if (index > 0) {
                double stepX = xs[index] - xs[index - 1];
                double stepY = ys[index] - ys[index - 1];
                double stepZ = zs[index] - zs[index - 1];
                arc += Math.sqrt(stepX * stepX + stepY * stepY + stepZ * stepZ);
            }
            arcs[index] = arc;
        }
        double totalArc = Math.max(1.0E-6D, arc);
        int red = RING_COLOR >> 16 & 255;
        int green = RING_COLOR >> 8 & 255;
        int blue = RING_COLOR & 255;
        for (int index = 1; index <= RING_SEGMENTS; index++) {
            float u0 = (float) (arcs[index - 1] / totalArc) * RING_UV_TILES + textureOffset;
            float u1 = (float) (arcs[index] / totalArc) * RING_UV_TILES + textureOffset;
            RibbonPoint first = new RibbonPoint(xs[index - 1], ys[index - 1], zs[index - 1],
                    u0, RING_ALPHA);
            RibbonPoint second = new RibbonPoint(xs[index], ys[index], zs[index],
                    u1, RING_ALPHA);
            // 与四隅絮带同样的两柄交叉：一柄横（宽沿 Y）、一柄竖（宽沿 X）
            ribbonQuad(vertices, pose, first, second, 0.0D, RIBBON_WIDTH, 0.0D,
                    red, green, blue, packedLight);
            ribbonQuad(vertices, pose, first, second, RIBBON_WIDTH, 0.0D, 0.0D,
                    red, green, blue, packedLight);
        }
    }

    /** 画一条「某隅四象石砖 → 台座顶心」的丝带。 */
    private static void renderOneRibbon(VertexConsumer vertices, PoseStack.Pose pose,
                                        BlockPos pos, BlockPos brick, float ticks,
                                        int red, int green, int blue, int packedLight) {
        // 坐标都是相对方块实体原点的（BER 拿到的 poseStack 已平移至方块角）
        double fromX = brick.getX() - pos.getX() + 0.5D;   // 砖顶心
        double fromY = RIBBON_FROM_Y;
        double fromZ = brick.getZ() - pos.getZ() + 0.5D;
        double toX = 0.5D;                                  // 台座顶心
        double toY = RIBBON_TO_Y;
        double toZ = 0.5D;
        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        int length = Math.max(2, Math.round(distance * RIBBON_QUALITY));
        // 相位种子取砖的世界坐标：四隅各不相同，四条带子才不会同步摆动
        double seedX = brick.getX() % 16.0D;
        double seedY = brick.getY() % 16.0D;
        double seedZ = brick.getZ() % 16.0D;
        // 摆动的时间项：与本体丝带相当的滚动速度（本体的 time = nanoTime/30ms，折成游戏 tick）
        double timePhase = ticks / 133.0D;
        // UV 缓滚：整数张贴图一周期的回绕，回绕处采样不变（见 RIBBON_UV_WRAP）
        float textureOffset = (ticks * RIBBON_SCROLL) % RIBBON_UV_WRAP;
        RibbonPoint previous = point(0, length, distance, fromX, fromY, fromZ, dx, dy, dz,
                seedX, seedY, seedZ, timePhase, textureOffset);
        for (int index = 1; index <= length; index++) {
            RibbonPoint next = point(index, length, distance, fromX, fromY, fromZ, dx, dy, dz,
                    seedX, seedY, seedZ, timePhase, textureOffset);
            // 每段两柄交叉：一柄横（宽沿 Y）、一柄竖（宽沿 X）
            ribbonQuad(vertices, pose, previous, next, 0.0D, RIBBON_WIDTH, 0.0D,
                    red, green, blue, packedLight);
            ribbonQuad(vertices, pose, previous, next, RIBBON_WIDTH, 0.0D, 0.0D,
                    red, green, blue, packedLight);
            previous = next;
        }
    }

    /**
     * 丝带上第 {@code index} 个点。
     *
     * <p>位置 = 起点 + 方向×进度，再叠三个轴向上各错开频率的正弦波（幅度受 alpha 节制，
     * 两端为零、中段最大）；{@code u} 沿带子长度铺开并缓滚，{@code v} 由调用方在带宽两端取 0/1。</p>
     */
    private static RibbonPoint point(int index, int length, float distance,
                                     double fromX, double fromY, double fromZ,
                                     double dx, double dy, double dz,
                                     double seedX, double seedY, double seedZ,
                                     double timePhase, float textureOffset) {
        float fraction = index / (float) length;
        // 三角形 alpha：两端透明、中段最亮——带子就「化」进两头的方块里
        float alpha = 1.0F - Math.abs(index - length / 2.0F) / (length / 2.0F);
        double distancePhase = distance * (1.0F - fraction) * 2.5D;
        double waveX = Math.sin((seedZ + distancePhase - timePhase) / 4.0D) * RIBBON_WOBBLE * alpha;
        double waveY = Math.sin((seedX + distancePhase - timePhase) / 3.0D) * RIBBON_WOBBLE * alpha;
        double waveZ = Math.sin((seedY + distancePhase - timePhase) / 2.0D) * RIBBON_WOBBLE * alpha;
        double x = fromX + (dx + waveX) * fraction;
        double y = fromY + (dy + waveY) * fraction;
        double z = fromZ + (dz + waveZ) * fraction;
        float u = (1.0F - fraction) * distance * RIBBON_UV_SCALE + textureOffset;
        return new RibbonPoint(x, y, z, u, Mth.clamp(alpha, 0.0F, 1.0F));
    }

    /** 一段丝带 = 一个四边形：前后两点各向两侧偏出半个带宽。 */
    private static void ribbonQuad(VertexConsumer vertices, PoseStack.Pose pose,
                                   RibbonPoint first, RibbonPoint second,
                                   double wx, double wy, double wz,
                                   int red, int green, int blue, int packedLight) {
        ribbonVertex(vertices, pose, first.x - wx, first.y - wy, first.z - wz,
                first.u, 1.0F, red, green, blue, first.alpha, packedLight);
        ribbonVertex(vertices, pose, first.x + wx, first.y + wy, first.z + wz,
                first.u, 0.0F, red, green, blue, first.alpha, packedLight);
        ribbonVertex(vertices, pose, second.x + wx, second.y + wy, second.z + wz,
                second.u, 0.0F, red, green, blue, second.alpha, packedLight);
        ribbonVertex(vertices, pose, second.x - wx, second.y - wy, second.z - wz,
                second.u, 1.0F, red, green, blue, second.alpha, packedLight);
    }

    /** 丝带的一个顶点：位置、颜色（透明度随该点的 alpha）、UV、法线朝上。 */
    private static void ribbonVertex(VertexConsumer vertices, PoseStack.Pose pose,
                                     double x, double y, double z, float u, float v,
                                     int red, int green, int blue, float alpha, int packedLight) {
        vertices.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(red, green, blue, Mth.clamp((int) (alpha * 255.0F), 0, 255))
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(packedLight)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    /** 丝带上的一个点：世界（相对方块）坐标、沿长度的 UV、该点的透明度。 */
    private record RibbonPoint(double x, double y, double z, float u, float alpha) {
    }

    /**
     * 台座上方浮现的卦象单字：<b>居中静悬在方块之上约第三层（{@code y+2.25}）</b>，
     * 恒朝向镜头（billboard），所在处同时凝着几卦就并排显几个字（各自用自己那一卦的象色）。
     *
     * <h2>★ 为什么必须照原版名片的姿势来画</h2>
     * <p>文本的渲染类型带<b>背面剔除</b>（{@code RenderType.CompositeState} 的
     * {@code cullState} 默认就是 {@code CULL}，文本这几个类型都没覆盖它）。而
     * {@code scale} 一旦让行列式为负，字面四边形的绕序就翻过来 —— 从背面看，整块字
     * <b>被整个剔掉，一个字都看不见</b>（表现就是「有粒子环、就是没有字」）。</p>
     * <p>所以这里逐字照抄 {@code EntityRenderer#renderNameTag} 的姿势：
     * {@code scale(+s, -s, +s)}（<b>X 取正</b>，负号只给 Y，用来把字正过来），
     * 并且同样画<b>两遍</b> —— 先 {@link Font.DisplayMode#SEE_THROUGH} 配一层半透黑底
     * （隔着方块也看得见），再 {@link Font.DisplayMode#NORMAL} 画一遍实色。</p>
     */
    private void renderTrigramName(XuanshuPedestalBlockEntity be, Level level,
                                   PoseStack poseStack, MultiBufferSource buffer) {
        // ★ 卦象只在「坛已开」时显：四象未立、法杖未点，这座台座头上不凝字。
        //   （与 XuanshuPedestalBlockEntity#clientTick 的卦气环是同一条门禁。）
        if (!be.isActive()) {
            return;
        }
        List<Fengshui.Trigram> trigrams = Fengshui.qiAll(level, be.getBlockPos());
        if (trigrams.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;

        // 逐卦造字（粗体走原版自己那条路：Font 见到 Style#isBold 会多描一遍）
        List<Component> labels = new ArrayList<>(trigrams.size());
        List<Integer> colors = new ArrayList<>(trigrams.size());
        float total = 0.0F;
        for (Fengshui.Trigram trigram : trigrams) {
            labels.add(Fengshui.displayName(trigram).copy().withStyle(Style.EMPTY.withBold(true)));
            colors.add(Fengshui.color(trigram));
            total += font.width(labels.get(labels.size() - 1));
        }
        total += LABEL_GAP * (labels.size() - 1);

        poseStack.pushPose();
        poseStack.translate(CENTER, LABEL_Y, CENTER);
        // 字面恒朝镜头（billboard）——玩家绕到哪一侧，字都正对着他
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        // ★ X 必须为正、只把 Y 取负（与原版名片一致），否则整块字被背面剔除掉
        poseStack.scale(LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);
        Matrix4f matrix = poseStack.last().pose();
        float x = -total / 2.0F;
        for (int i = 0; i < labels.size(); i++) {
            Component label = labels.get(i);
            int color = colors.get(i);
            // 第一遍：透墙可见的淡色 + 半透黑底（夜里、隔着方块也读得清）
            font.drawInBatch(label, x, 0.0F, (color & 0x00FFFFFF) | 0x20000000, false, matrix, buffer,
                    Font.DisplayMode.SEE_THROUGH, 0x66000000, 0xF000F0);
            // 第二遍：实色（正常深度测试，被挡住的部分只剩上面那层淡影）
            font.drawInBatch(label, x, 0.0F, color, false, matrix, buffer,
                    Font.DisplayMode.NORMAL, 0, 0xF000F0);
            x += font.width(label) + LABEL_GAP;
        }
        poseStack.popPose();
    }
}
