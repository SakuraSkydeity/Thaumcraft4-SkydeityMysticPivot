package skydeity.mysticpivot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * 玄枢虚空水晶塔的<b>四道深蓝圆光</b>（第一百九十七批改成圆管；第一百九十八批给根上加了光苞）。
 *
 * <h2>用户口径</h2>
 * <blockquote>「矿机的多方块蓝色的光束仔细看是十字 我不希望是这样，用光线一样的圆，然后要 4 条，
 * 再（在）四个玻璃上」<br>
 * 「光束靠近底部位子画一个我红色一样的」（第一百九十八批）</blockquote>
 *
 * <h2>「十字」是怎么来的，又怎么没了</h2>
 * <p>第一百九十五批那根柱是<b>两片零厚度四边形十字交叉</b> —— 那是「一根柱」最省的画法
 * （两根面片、八次 {@code addVertex}），但零厚度面片只有在正对时才是「一条线」：
 * 侧着看是一片扁的，45° 看过去两片交叉，就成了一个<b>十字</b>。</p>
 *
 * <p>这一版改成真正的<b>圆柱侧面</b>：绕轴 12 段四边形，每段两个竖直边 ——
 * 任何角度看截面都是个圆，不再有「十字」。半径 {@link #BEAM_RADIUS} 0.09（全宽 0.18 格），
 * 与上一版那条线同宽，只是从「两片」变成「一圈」。</p>
 *
 * <h2>四根站在四块玻璃上</h2>
 * <p>落点不再写死在中轴：改为问 {@link VoidMiningStructure#beamAnchors()} ——
 * 它从结构常量里挑出<b>塔顶同一层、绕塔心四个侧面各一块</b>的淡蓝玻璃（现在是 {@code dy = 8}
 * 那层：北、南、西、东各一块），返回每块的<b>上表面中心</b>，光就从那块玻璃的顶面长出来。
 * 一层凑不出四块时它返回空表，这里退回「中轴一根、从塔顶玻璃起」的老样子，不至于一根都不画。</p>
 *
 * <h2>第一百九十八批：根上添一枚光苞</h2>
 * <p>每根光柱的根部再罩一团<b>纺锤形的光</b>（{@link #drawSpindle}）：上下收成尖、中间鼓起，
 * 半径按 {@code sin(πt)} 沿高度走，α 比光柱淡一档（{@link #LENS_ALPHA}）。
 * 半径取 {@link #LENS_RADIUS} 0.55 —— 四个落点最近两根相距 1.41 格，再粗就会互相穿插。</p>
 *
 * <h2>渲染类型：只写颜色，不写深度</h2>
 * <p>仍然是 {@link RenderType#debugStructureQuads()}。原因见第一百九十五批那个坑（§9 #93）：
 * <b>方块实体是在半透明地形之前画的</b>，谁在这里写深度，谁就会把随后画的玻璃、水、雨
 * 「挖掉」一块 —— 看着就是「竖线周围能透视方块」。它只差 {@code debugQuads()} 一个
 * 写掩码位（{@code COLOR_WRITE}），深度<b>测试</b>照做，被山挡住照样黑掉。</p>
 *
 * <p>圆柱有正反两面，而这条渲染类型是 {@code NO_CULL}：一圈侧面里，远的半边先画、
 * 近的半边后画，颜色一样、α 一样，叠出来的仍是同一支蓝，不会出现「一根管两个亮度」。</p>
 *
 * <h2>为什么还要自己报渲染包围盒</h2>
 * <p>{@code BlockEntityRenderer} 默认只包方块自己那几格，三十几格高的光柱一出盒子就被整条
 * 剔除 —— 「抬头一闪、低头又出来」。{@link #getRenderBoundingBox} 把盒子撑到包住全部光柱。</p>
 */
public class XuanshuVoidMiningRenderer implements BlockEntityRenderer<XuanshuVoidMiningBlockEntity> {

    // ---- 颜色：全图一个蓝 ----

    /** 深蓝的 R。 */
    private static final float BEAM_R = 0.11F;

    /** 深蓝的 G。 */
    private static final float BEAM_G = 0.20F;

    /** 深蓝的 B。 */
    private static final float BEAM_B = 0.78F;

    /**
     * 不透明度。
     *
     * <p>取 0.9 而不是 1：圆管的正反两面都会画到，α = 1 时近侧碎片会把远侧整个盖掉，
     * 边缘只剩一条硬边；留一点透，管壁才有一点点「光在里头」的厚度感。
     * 但也不能更低 —— 低于 0.7 就会看见管子里透出后面的方块。</p>
     */
    private static final float BEAM_ALPHA = 0.9F;

    // ---- 位置与粗细 ----

    /** 顶端：塔高之上再冲二十几格，读作「往天上抽」。 */
    private static final double BEAM_TOP = 36.0D;

    /** 半径（格）：全宽 0.18，与上一版那条线同宽。 */
    private static final float BEAM_RADIUS = 0.09F;

    /**
     * 一圈几段。
     *
     * <p>12 段时半圆只有 6 个折点，半径 0.09 的管子直径才 0.18 格 —— 近距离仍然看得出多边形？
     * 看得出的前提是管子够粗。0.18 格在屏幕上通常只有十几像素，12 段足够了；
     * 段数再翻倍只会多一倍顶点，观感没有区别。</p>
     */
    private static final int BEAM_SIDES = 12;

    // ---- 光苞（第一百九十八批新增）----

    /**
     * 光苞：光柱根上那枚<b>纺锤形的光团</b>，也就是用户截图里红笔画的那个尖头形状。
     *
     * <h2>用户口径</h2>
     * <blockquote>「光束靠近底部位子画一个我红色一样的」</blockquote>
     * <p>截图里那个红圈是<b>上下两头收成尖、中间鼓起来</b>的一片叶子（菱形 + 微凸的侧边），
     * 光柱从中间穿过去。所以这里不再画「一根等粗的管子」，而是让半径沿高度按
     * {@code sin(πt)} 收放：底下是 0、正中间最粗、上去又收成 0 —— 侧影就是一个纺锤。</p>
     *
     * <p>尺寸要贴着四根光柱的间距定：四个落点最近的两根相距 <b>1.41</b> 格
     * （{@code (1,−1)} 与 {@code (2,0)}），所以半径必须 <b>&lt; 0.70</b> 才不会互相穿插。
     * 取 0.55（直径 1.1），留 0.31 的缝，四枚光苞各自独立、不会叠成一大坨。</p>
     */
    private static final float LENS_RADIUS = 0.55F;

    /** 光苞多高（格）：从光柱根部往上 8 格，正好罩住「刚出塔顶」那一段。 */
    private static final double LENS_HEIGHT = 8.0D;

    /**
     * 光苞的不透明度。
     *
     * <p>比光柱本体（0.9）淡：它是一团「光散出来」的感觉，太实就成了四个玻璃锥。
     * 0.55 在天空前仍看得清轮廓，贴着塔身看又不会盖住玻璃的颜色。</p>
     */
    private static final float LENS_ALPHA = 0.55F;

    /**
     * 光苞一圈几段 / 沿轴几层。
     *
     * <p>比光柱细分的多：它比光柱粗六倍，段数少了锥面会出现明显的棱；
     * 16 段 × 12 层 = 每根 768 个四边形，四根三千出头，比一棵树还便宜。</p>
     */
    private static final int LENS_SIDES = 16;

    /** 光苞沿轴几层（<b>层</b>越密，sin 曲线越顺）。 */
    private static final int LENS_RINGS = 12;

    // ---- 渲染类型 ----

    /**
     * 光柱专用的渲染类型：{@link RenderType#debugStructureQuads()}。
     *
     * <p>选它只为一个状态位 —— <b>写掩码是 {@code COLOR_WRITE}（只写颜色，不写深度）</b>。
     * 方块实体的渲染在半透明地形<b>之前</b>，谁在这里写深度，谁就会把后面的玻璃、
     * 水、雨「挖掉」一块。它同时满足另外三个要求：{@code POSITION_COLOR}（不用贴图、
     * 颜色随便给）、{@code NO_CULL}（管子的正反两面都要画）、{@code TRANSLUCENT_TRANSPARENCY}
     * （α 混合）。深度测试仍是 {@code LEQUAL}：被山、被塔挡住时老实黑掉。</p>
     */
    private static final RenderType BEAM_TYPE = RenderType.debugStructureQuads();

    public XuanshuVoidMiningRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(XuanshuVoidMiningBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (!be.isFormed()) {
            return;
        }
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof XuanshuVoidMiningBlock)) {
            return;
        }

        BlockPos pos = be.getBlockPos();
        VertexConsumer vc = buffer.getBuffer(BEAM_TYPE);
        PoseStack.Pose pose = poseStack.last();

        List<double[]> anchors = VoidMiningStructure.beamAnchors();
        if (anchors.isEmpty()) {
            // 形制换了、一层凑不出四块玻璃：退回「中轴一根，从塔顶玻璃起」
            double[] axis = VoidMiningStructure.axis(pos);
            drawBeam(vc, pose,
                    (float) (axis[0] - pos.getX()), (float) VoidMiningStructure.height(),
                    (float) (axis[1] - pos.getZ()));
            return;
        }
        for (double[] anchor : anchors) {
            drawBeam(vc, pose, (float) anchor[0], (float) anchor[1], (float) anchor[2]);
        }
    }

    /** 一根光柱的<b>全套</b>：等粗的管子 + 根上那枚光苞。 */
    private static void drawBeam(VertexConsumer vc, PoseStack.Pose pose, float cx, float baseY, float cz) {
        drawTube(vc, pose, cx, baseY, cz);
        drawSpindle(vc, pose, cx, baseY, cz);
    }

    /**
     * 一根圆管：{@code (cx, baseY, cz)} 处、从 {@code baseY} 到 {@link #BEAM_TOP} 的圆柱侧面。
     *
     * <p>绕轴切 {@link #BEAM_SIDES} 份，每份一片竖直四边形（两个三角合成一个 quad 的顶点序）。
     * 因为渲染类型是 {@code NO_CULL}，绕序不影响可见性，只要四边形别退化成线就行。</p>
     */
    private static void drawTube(VertexConsumer vc, PoseStack.Pose pose, float cx, float baseY, float cz) {
        float y0 = baseY;
        float y1 = (float) BEAM_TOP;
        float r = BEAM_RADIUS;
        for (int i = 0; i < BEAM_SIDES; i++) {
            double a0 = Math.PI * 2.0D * i / BEAM_SIDES;
            double a1 = Math.PI * 2.0D * (i + 1) / BEAM_SIDES;
            float x0 = cx + (float) (Math.cos(a0) * r);
            float z0 = cz + (float) (Math.sin(a0) * r);
            float x1 = cx + (float) (Math.cos(a1) * r);
            float z1 = cz + (float) (Math.sin(a1) * r);
            vc.addVertex(pose, x0, y0, z0).setColor(BEAM_R, BEAM_G, BEAM_B, BEAM_ALPHA);
            vc.addVertex(pose, x1, y0, z1).setColor(BEAM_R, BEAM_G, BEAM_B, BEAM_ALPHA);
            vc.addVertex(pose, x1, y1, z1).setColor(BEAM_R, BEAM_G, BEAM_B, BEAM_ALPHA);
            vc.addVertex(pose, x0, y1, z0).setColor(BEAM_R, BEAM_G, BEAM_B, BEAM_ALPHA);
        }
    }

    /**
     * 光苞：<b>纺锤形</b>的一团光，罩在光柱根上（第一百九十八批，用户截图里红笔画的那个形状）。
     *
     * <p>做法与 {@link #drawTube} 同源，只是半径不再固定：沿轴切 {@link #LENS_RINGS} 层，
     * 第 {@code i} 层的半径取 {@code R · sin(π · t)}（{@code t} 是这一层占全高的比例）。
     * {@code t = 0} 与 {@code t = 1} 处半径为 0 —— 上下各收成一个尖，正中间鼓到最粗，
     * 侧影就是一片两头尖的叶子；光柱本身从它中间穿过去，与截图那种「一条线穿过红圈」一致。</p>
     *
     * <p>两端的四边形会退化成一条线（四个顶点重合两个），{@code NO_CULL} 下不可见、也不会报错，
     * 换来的是不必为「尖头」单独写一套三角。</p>
     */
    private static void drawSpindle(VertexConsumer vc, PoseStack.Pose pose, float cx, float baseY, float cz) {
        for (int ring = 0; ring < LENS_RINGS; ring++) {
            float t0 = (float) ring / LENS_RINGS;
            float t1 = (float) (ring + 1) / LENS_RINGS;
            float r0 = (float) (LENS_RADIUS * Math.sin(Math.PI * t0));
            float r1 = (float) (LENS_RADIUS * Math.sin(Math.PI * t1));
            float y0 = baseY + (float) (LENS_HEIGHT * t0);
            float y1 = baseY + (float) (LENS_HEIGHT * t1);
            for (int i = 0; i < LENS_SIDES; i++) {
                double a0 = Math.PI * 2.0D * i / LENS_SIDES;
                double a1 = Math.PI * 2.0D * (i + 1) / LENS_SIDES;
                float c0 = (float) Math.cos(a0);
                float s0 = (float) Math.sin(a0);
                float c1 = (float) Math.cos(a1);
                float s1 = (float) Math.sin(a1);
                vc.addVertex(pose, cx + c0 * r0, y0, cz + s0 * r0)
                        .setColor(BEAM_R, BEAM_G, BEAM_B, LENS_ALPHA);
                vc.addVertex(pose, cx + c1 * r0, y0, cz + s1 * r0)
                        .setColor(BEAM_R, BEAM_G, BEAM_B, LENS_ALPHA);
                vc.addVertex(pose, cx + c1 * r1, y1, cz + s1 * r1)
                        .setColor(BEAM_R, BEAM_G, BEAM_B, LENS_ALPHA);
                vc.addVertex(pose, cx + c0 * r1, y1, cz + s0 * r1)
                        .setColor(BEAM_R, BEAM_G, BEAM_B, LENS_ALPHA);
            }
        }
    }

    /**
     * 渲染包围盒：把整根光柱包进去，抬头不再被视锥裁掉。
     *
     * <p>水平方向直接用 {@link VoidMiningStructure#bounds()}（塔的 8×8 投影，
     * {@code {minX, minZ, maxX, maxZ}}），竖直到 {@code BEAM_TOP + 2} —— 全是算出来的，
     * 没有第二个地方写塔有多宽、有多高。</p>
     */
    @Override
    public AABB getRenderBoundingBox(XuanshuVoidMiningBlockEntity be) {
        BlockPos pos = be.getBlockPos();
        int[] bounds = VoidMiningStructure.bounds();
        return new AABB(pos.getX() + bounds[0], pos.getY() - 2.0D, pos.getZ() + bounds[1],
                pos.getX() + bounds[2], pos.getY() + BEAM_TOP + 2.0D, pos.getZ() + bounds[3]);
    }

    @Override
    public int getViewDistance() {
        // 光柱冲上三十几格，站远一点也该看得见
        return 160;
    }
}
