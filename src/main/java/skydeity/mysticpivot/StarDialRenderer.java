package skydeity.mysticpivot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.tc4port.thaumcraft.api.client.ThaumcraftClientRenderApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
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
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * 星斗阵的渲染：<b>浮空的那一组星</b>（连同它的星座金线）+ <b>十二格上的供物与星名</b>。
 *
 * <h2>哪一组浮空</h2>
 * <p>盘面上永远画着一组（{@link StarDialBlock.Chart} 决定是七星还是六星，画在贴图里），
 * <b>另一组就由这里浮在盘面上方</b>——用户：「六星和七星占满整个 3×4，然后另一个就浮在空中」。
 * 于是 {@code chart=seven} 时浮的是六星（冷银），{@code chart=six} 时浮的是七星（暖金）。</p>
 *
 * <h2>金线 = 星座连线（第一百一十五批）</h2>
 * <p>原来那道「格与格之间」的鎏金丝带（按上下左右四邻拉线）已删：用户要的是
 * 「金线改为七星和六星的连线顺序」。现在丝带<b>沿 {@link #SIX_LINKS} / {@link #SEVEN_LINKS}
 * 的次序，把浮空的星一颗一颗串起来</b>——北斗是「勺口闭合成环，再从第四星挑出柄」，
 * 南斗是「勺口闭合成环，再从第二星拖出两星」，与盘面上画的连线同序，
 * 只是这一组悬在空中，所以线也悬在空中。</p>
 * <p>画法仍是玄枢台座那条 {@code wispy} 絮带：每段两条交叉四边形、UV 沿带缓滚、两端不衰减。</p>
 *
 * <h2>十二格的供物</h2>
 * <p>第一百一十七批起十二格都能供物（一格一件），物品浮在格心上方缓转、头顶报星名。两处踩过的坑都挡住了——
 * <b>不发黑</b>（光照取 {@link LevelRenderer#getLightColor}）、
 * <b>不错位</b>（先挪到格心再交给 {@link ItemDisplayContext#GROUND}，它自带居中与缩放）。</p>
 */
public class StarDialRenderer implements BlockEntityRenderer<StarDialBlockEntity> {

    /**
     * 丝带贴图——与玄枢台座、本体奥术充能基座同一条 {@code wispy.png}：
     * 一条横向的絮状流纹，沿长度方向滚动时看着就是「絮在流」。
     */
    private static final ResourceLocation WISPY =
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/wispy.png");

    /** 鎏金（与台座那道金环同色）。 */
    private static final int GOLD = 0xE0B84A;

    /** 浮空星点贴图（亮芯 + 四向芒 + 柔晕，32×32，圆外全透明）。 */
    private static final ResourceLocation GLOW =
            ResourceLocation.fromNamespaceAndPath(MysticPivot.MODID, "textures/block/star_glow.png");

    // ------------------------------------------------------------------
    // 两组星座（96×128 的整阵坐标，铺满 3×4）
    // ------------------------------------------------------------------

    /**
     * 两张星图的星位（整阵 96×128 坐标）——与 {@code tools/gen_star_dial.py} 的
     * {@code SEVEN_STARS} / {@code SIX_STARS} <b>必须一致</b>。
     *
     * <p>第一百二十一批按用户要求「盘面改回原来的」，星图是<b>跨格</b>排的：
     * 北斗从上到下斜着穿过整个阵，所以星不一定落在它自己那一格里。</p>
     */
    private static final float[][] SEVEN_STARS = {
            {16.0F, 102.0F}, {22.0F, 70.0F}, {50.0F, 78.0F}, {56.0F, 108.0F},
            {74.0F, 94.0F}, {86.0F, 62.0F}, {84.0F, 24.0F},
    };

    /** 北斗连线：勺口 0-1-2-3 环，柄 2-4-5-6。 */
    private static final int[][] SEVEN_LINKS = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {2, 4}, {4, 5}, {5, 6}};

    /** 南斗六星。 */
    private static final float[][] SIX_STARS = {
            {16.0F, 58.0F}, {44.0F, 20.0F}, {58.0F, 52.0F},
            {26.0F, 80.0F}, {76.0F, 62.0F}, {88.0F, 108.0F},
    };

    /** 南斗连线：勺口 0-1-2-3 环，柄 2-4-5。 */
    private static final int[][] SIX_LINKS = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {2, 4}, {4, 5}};

    /** 整阵星图的像素尺寸（96×128 = 3 列 × 4 行，每格 32px）。 */
    private static final float CHART_W = 96.0F;
    private static final float CHART_H = 128.0F;

    /** 阵占的格数：三列四行。 */
    private static final float BOARD_W = 3.0F;
    private static final float BOARD_D = 4.0F;

    /** 台阶顶面高度。 */
    private static final double PLATE_TOP = 0.5D;

    /**
     * 悬空高度：星点从台面（0.5）再往上 {@code 1.25} 格，也就是 <b>y = 1.75</b>。
     *
     * <p>第一百一十九批用户：「金线再高很多」——原来 0.25 只浮起小半格，看着像贴在盘面上；
     * 现在整层（星点 + 金线）一起抬到 1.75，是悬在半空的一张星图。</p>
     */
    private static final double STAR_LIFT = 1.25D;

    /** 金线跟着星点同高（1.75）：一条线穿过星心，才是「连着的星座」。 */
    private static final float STRAND_Y = 1.75F;

    /** 星点半径（格）。0.10 → 直径 0.2 格，比上一版更大更清楚。 */
    private static final float STAR_RADIUS = 0.10F;

    /**
     * 浮空星点的颜色（第一百二十四批换色）。
     *
     * <p>用户：「物品放在对应星上，特效的星改个颜色」——供物本来就在星位上，原来的暖金 / 冷银
     * 跟盘面上的星、跟金锭都糊在一起。现在浮空那层统一走<b>冷月光蓝</b>，
     * 一眼能分出「贴图上的星」与「浮在空中的星」。北斗略亮、南斗略深，仍留一点区别。</p>
     */
    private static final int SEVEN_RED = 176;
    private static final int SEVEN_GREEN = 228;
    private static final int SEVEN_BLUE = 255;
    private static final int SIX_RED = 146;
    private static final int SIX_GREEN = 202;
    private static final int SIX_BLUE = 255;

    // ------------------------------------------------------------------
    // 金线（星座连线）
    // ------------------------------------------------------------------

    /** 丝带半宽：悬空的金线要比贴地那道更细一点（0.06 → 全宽 0.12 格），远看是一根发光的金丝。 */
    private static final float WIDTH = 0.06F;

    /** 每段切几段（一格长的线切 8 段，摆动就够顺）。 */
    private static final int SEGMENTS = 8;

    /** 摆动幅度（格）：悬空的金线可以比贴地时活一点。 */
    private static final double WOBBLE = 0.02D;

    /** 整条带的透明度：两端不衰减，星与星之间才连得起来。 */
    private static final float ALPHA = 0.72F;

    /** UV 沿带子的密度与缓滚速度（tile / tick）。 */
    private static final float UV_SCALE = 0.55F;
    private static final float UV_SCROLL = 0.006F;

    /** UV 回绕周期：必须是整数张，回绕处采样不变（与台座同一条经验）。 */
    private static final float UV_WRAP = 64.0F;

    // ------------------------------------------------------------------
    // 中间两格的物品
    // ------------------------------------------------------------------

    /** 物品的悬浮高度（台阶顶面 0.5，浮到 0.62 才看得出「悬」）。 */
    private static final double ITEM_Y = 0.62D;

    /** 物品放大倍数（{@link ItemDisplayContext#GROUND} 自带 0.25 倍，再乘 0.8 与台座同款）。 */
    private static final float ITEM_SCALE = 0.8F;

    /** 星名文字的大小与高度：比名片大一点、浮在供物之上，抬头就看得见。 */
    private static final float LABEL_SCALE = 0.016F;
    private static final double LABEL_Y = 0.98D;

    // ------------------------------------------------------------------
    // 阵外的两圈（第一百一十八批）
    // ------------------------------------------------------------------

    /** 7×8 最外圈的符文圈（本体 {@code mark.png}，128×128，圆心几乎全透明）。 */
    private static final ResourceLocation MARK =
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/mark.png");

    /** 符文圈的横 / 纵半径：7×8 的外沿是 7 格宽、8 格深，所以是个略扁的椭圆。 */
    private static final float RING_RX = 3.35F;
    private static final float RING_RZ = 3.85F;

    /**
     * 竖光已不在方块实体渲染器里画（第一百四十四批）：星盘区段退出视锥时，
     * 这里的整段渲染会一起被剔掉，用户看到的正是「机器的光头往上抬就消失」。
     * 引导线改走 TC 的关卡后世界渲染（{@link StarBeamEffects}），本渲染器
     * 只负责在盘面可见时把阵心报到缓存里。
     */
    private static final double RING_Y = 0.515D;

    public StarDialRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(StarDialBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof StarDialBlock) || !state.getValue(StarDialBlock.ROLE).formed()) {
            return;
        }
        StarDialBlock.Role role = state.getValue(StarDialBlock.ROLE);
        float ticks = (level.getGameTime() % 720720L) + partialTick;

        // 供物浮在格心；只有「当前这一斗」的格子才报星名（另一斗放不上去，也不显示）
        boolean sevenPlate = state.getValue(StarDialBlock.CHART) == StarDialBlock.Chart.SEVEN;
        renderStoredItem(be, level, ticks, poseStack, buffer);
        StarDialBlock.Seat seat = StarDialBlock.seatAt(state.getValue(StarDialBlock.CHART),
                role.row(), role.col());
        if (seat != null) {
            renderStarName(be, seat.star(),
                    chartX(seat.px()) - (role.col() - 1),
                    chartZ(seat.py()) - (role.row() - 1), poseStack, buffer);
        }

        // 阵外的两圈（符文圈 / 工作方块光柱）也只由阵心那一格画
        if (role == StarDialBlock.Role.R1C1) {
            renderArray(level, be.getBlockPos(), poseStack, buffer, ticks);
            // ★ 第一百一十九批：浮空层画的就是<b>盘面这一斗</b>——「星盘是什么，上面只显示什么」。
            //   另一斗（含它的星与金线）完全不画。
            boolean plateIsSeven = state.getValue(StarDialBlock.CHART) == StarDialBlock.Chart.SEVEN;
            renderFloating(poseStack, buffer, ticks,
                    plateIsSeven ? SEVEN_STARS : SIX_STARS,
                    plateIsSeven ? SEVEN_LINKS : SIX_LINKS,
                    plateIsSeven ? SEVEN_RED : SIX_RED,
                    plateIsSeven ? SEVEN_GREEN : SIX_GREEN,
                    plateIsSeven ? SEVEN_BLUE : SIX_BLUE);
        }
    }

    /**
     * 阵外的符文圈（7×8 外沿全是星盘时铺一圈本体 {@code mark.png} 的符文环）。
     *
     * <p>★ 工作方块头顶的引导线已迁往 {@link StarBeamEffects}（关卡后世界渲染，
     * 不受区段剔除）；这里只在阵心可见时把阵心报到它的缓存。</p>
     */
    private static void renderArray(Level level, BlockPos anchor,
                                    PoseStack poseStack, MultiBufferSource buffer, float ticks) {
        // 阵心可见：报到引导线的缓存（那边每帧自行校验存亡与远近）
        StarBeamEffects.touch(level, anchor);

        if (outerRingComplete(level, anchor)) {
            poseStack.pushPose();
            poseStack.translate(0.5D, RING_Y, 1.0D);
            poseStack.mulPose(Axis.YP.rotationDegrees(ticks * 0.12F));
            poseStack.scale(RING_RX, 1.0F, RING_RZ);
            VertexConsumer mark = buffer.getBuffer(ThaumcraftClientRenderApi.legacyLinearAdditiveBillboard(MARK));
            glowQuad(mark, poseStack.last(),
                    -1.0F, 0.0F, -1.0F, -1.0F, 0.0F, 1.0F,
                    1.0F, 0.0F, 1.0F, 1.0F, 0.0F, -1.0F,
                    0.0F, 1.0F, 1.0F, 0.0F,
                    232, 186, 92, 205);
            poseStack.popPose();
        }
    }

    /** 最外那圈（7×8 去掉 5×6）是不是全是星盘。 */
    private static boolean outerRingComplete(Level level, BlockPos anchor) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 4; dz++) {
                if (!isOuterRing(dx, dz)) {
                    continue;
                }
                if (!(level.getBlockState(anchor.offset(dx, 0, dz)).getBlock() instanceof StarDialBlock)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 最外一圈（7×8 的外沿、5×6 之外）的判定。 */
    private static boolean isOuterRing(int dx, int dz) {
        boolean outer = dx >= -3 && dx <= 3 && dz >= -3 && dz <= 4;
        boolean inner = dx >= -2 && dx <= 2 && dz >= -2 && dz <= 3;
        return outer && !inner;
    }

    /** 一片带透明度的发光四边形（uv 从左上到右下）。 */
    private static void glowQuad(VertexConsumer vertices, PoseStack.Pose pose,
                                 float x0, float y0, float z0, float x1, float y1, float z1,
                                 float x2, float y2, float z2, float x3, float y3, float z3,
                                 float u0, float v0, float u1, float v1,
                                 int red, int green, int blue, int alpha) {
        glowVertex(vertices, pose, x0, y0, z0, u0, v0, red, green, blue, alpha);
        glowVertex(vertices, pose, x1, y1, z1, u1, v0, red, green, blue, alpha);
        glowVertex(vertices, pose, x2, y2, z2, u1, v1, red, green, blue, alpha);
        glowVertex(vertices, pose, x3, y3, z3, u0, v1, red, green, blue, alpha);
    }

    private static void glowVertex(VertexConsumer vertices, PoseStack.Pose pose,
                                   float x, float y, float z, float u, float v,
                                   int red, int green, int blue, int alpha) {
        vertices.addVertex(pose, x, y, z)
                .setColor(red, green, blue, Mth.clamp(alpha, 0, 255))
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    /**
     * 浮空的一组：先按连线次序拉金线，再把星压在线端。
     *
     * <p>坐标换算：星图 96×128 的 {@code (px, py)} → 相对阵心的格坐标
     * {@code (-1 + px/96*3, -1 + py/128*4)}。阵心那一格的方块实体就在阵心，
     * 所以本方法里直接当原点用。</p>
     */
    private static void renderFloating(PoseStack poseStack, MultiBufferSource buffer, float ticks,
                                       float[][] stars, int[][] links,
                                       int red, int green, int blue) {
        float lift = (float) (PLATE_TOP + STAR_LIFT);
        PoseStack.Pose pose = poseStack.last();
        VertexConsumer ribbon = buffer.getBuffer(ThaumcraftClientRenderApi.legacyLinearAdditiveBillboard(WISPY));
        for (int[] link : links) {
            float[] a = stars[link[0]];
            float[] b = stars[link[1]];
            strand(ribbon, pose, ticks,
                    chartX(a[0]), STRAND_Y, chartZ(a[1]),
                    chartX(b[0]), STRAND_Y, chartZ(b[1]));
        }

        VertexConsumer glow = buffer.getBuffer(ThaumcraftClientRenderApi.legacyLinearAdditiveBillboard(GLOW));
        for (int i = 0; i < stars.length; i++) {
            float x = chartX(stars[i][0]);
            float z = chartZ(stars[i][1]);
            float y = lift + Mth.sin(ticks * 0.045F + i * 1.7F) * 0.018F;
            float radius = STAR_RADIUS * (1.0F + Mth.sin(ticks * 0.07F + i * 2.1F) * 0.1F);
            star(glow, pose, x, y, z, radius, red, green, blue);
        }
    }

    /** 星图 x（0~96）→ 相对阵心的格坐标（-1~+2）。 */
    private static float chartX(float px) {
        return -1.0F + px / CHART_W * BOARD_W;
    }

    /** 星图 y（0~128）→ 相对阵心的格坐标（-1~+3）。 */
    private static float chartZ(float py) {
        return -1.0F + py / CHART_H * BOARD_D;
    }

    /** 一枚星点：平躺一片 + 两片立面，任何角度都不会缩成一条线。 */
    private static void star(VertexConsumer vertices, PoseStack.Pose pose,
                             float x, float y, float z, float r, int red, int green, int blue) {
        quad(vertices, pose, x - r, y - r, z, x + r, y - r, z, x + r, y + r, z, x - r, y + r, z,
                red, green, blue);
        quad(vertices, pose, x - r, y, z - r, x + r, y, z - r, x + r, y, z + r, x - r, y, z + r,
                red, green, blue);
        quad(vertices, pose, x, y - r, z - r, x, y - r, z + r, x, y + r, z + r, x, y + r, z - r,
                red, green, blue);
    }

    /** 一片贴满整张星点贴图的四边形（四个角按顺序给）。 */
    private static void quad(VertexConsumer vertices, PoseStack.Pose pose,
                             float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3,
                             int red, int green, int blue) {
        vertex(vertices, pose, x0, y0, z0, 0.0F, 1.0F, red, green, blue);
        vertex(vertices, pose, x1, y1, z1, 1.0F, 1.0F, red, green, blue);
        vertex(vertices, pose, x2, y2, z2, 1.0F, 0.0F, red, green, blue);
        vertex(vertices, pose, x3, y3, z3, 0.0F, 0.0F, red, green, blue);
    }

    private static void vertex(VertexConsumer vertices, PoseStack.Pose pose,
                               float x, float y, float z, float u, float v,
                               int red, int green, int blue) {
        vertices.addVertex(pose, x, y, z)
                .setColor(red, green, blue, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    /**
     * 供在星位上的东西：浮起 + 缓转 + 上下轻浮。
     *
     * <p>与玄枢台座的 {@code renderStoredItem} 同一套写法（那里已经踩平了「发黑 / 错位」）。</p>
     */
    private static void renderStoredItem(StarDialBlockEntity be, Level level, float ticks,
                                         PoseStack poseStack, MultiBufferSource buffer) {
        ItemStack stack = be.getItem();
        if (stack.isEmpty()) {
            return;
        }
        BlockState state = be.getBlockState();
        if (!(state.getBlock() instanceof StarDialBlock)
                || !state.getValue(StarDialBlock.ROLE).formed()) {
            return;
        }
        // ★ 第一百二十三批：星位由图案定（StarDialBlock.Seat），供物直接摆在那个坐标上；
        //   星位是「相对阵心」的盘面坐标，还要减掉本格相对阵心的偏移 (col-1, row-1)。
        //   不是星位的格子（翻面留下的残留）退回格心，方便空手取回。
        StarDialBlock.Role role = state.getValue(StarDialBlock.ROLE);
        StarDialBlock.Seat seat = StarDialBlock.seatAt(state.getValue(StarDialBlock.CHART),
                role.row(), role.col());
        float seatX = 0.5F;
        float seatZ = 0.5F;
        if (seat != null) {
            seatX = chartX(seat.px()) - (role.col() - 1);
            seatZ = chartZ(seat.py()) - (role.row() - 1);
        }
        poseStack.pushPose();
        poseStack.translate(seatX, ITEM_Y + Mth.sin(ticks * 0.05F) * 0.02D, seatZ);
        poseStack.mulPose(Axis.YP.rotationDegrees(ticks * 1.1F));
        poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.GROUND,
                LevelRenderer.getLightColor(level, be.getBlockPos()),
                OverlayTexture.NO_OVERLAY, poseStack, buffer, level, 0);
        poseStack.popPose();
    }

    /**
     * 星名：这一格供着东西时，头顶浮一个「天枢」「天府」。
     *
     * <p>写法照搬玄枢台座的卦名（{@code XuanshuPedestalRenderer#renderTrigramName}）：
     * 字面恒朝镜头（billboard），先画一遍透墙的淡影、再画一遍实色——隔着方块也读得清。
     * 北斗用暖金、南斗用冷银，与盘面上的星色一致。</p>
     */
    private static void renderStarName(StarDialBlockEntity be, Star star, float x, float z,
                                       PoseStack poseStack, MultiBufferSource buffer) {
        if (star == null || be.getItem().isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        Component name = star.displayName().copy().withStyle(Style.EMPTY.withBold(true));
        int color = star.color();

        poseStack.pushPose();
        poseStack.translate(x, LABEL_Y, z);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        // ★ X 为正、只把 Y 取负（与原版名片一致），否则整块字会被背面剔除
        poseStack.scale(LABEL_SCALE, -LABEL_SCALE, LABEL_SCALE);
        Matrix4f matrix = poseStack.last().pose();
        float textX = -font.width(name) / 2.0F;
        font.drawInBatch(name, textX, 0.0F, (color & 0x00FFFFFF) | 0x20000000, false, matrix, buffer,
                Font.DisplayMode.SEE_THROUGH, 0x66000000, 0xF000F0);
        font.drawInBatch(name, textX, 0.0F, color, false, matrix, buffer,
                Font.DisplayMode.NORMAL, 0, 0xF000F0);
        poseStack.popPose();
    }

    /** 画一条从 {@code (x0,y0,z0)} 到 {@code (x1,y1,z1)} 的丝带。 */
    private static void strand(VertexConsumer vertices, PoseStack.Pose pose, float ticks,
                               float x0, float y0, float z0, float x1, float y1, float z1) {
        int red = GOLD >> 16 & 255;
        int green = GOLD >> 8 & 255;
        int blue = GOLD & 255;
        float offset = (ticks * UV_SCROLL) % UV_WRAP;
        double timePhase = ticks / 133.0D;

        RibbonPoint previous = point(0, x0, y0, z0, x1, y1, z1, timePhase, offset);
        for (int i = 1; i <= SEGMENTS; i++) {
            RibbonPoint next = point(i, x0, y0, z0, x1, y1, z1, timePhase, offset);
            // 两柄交叉：一柄宽沿 Y、一柄宽沿 X
            ribbonQuad(vertices, pose, previous, next, 0.0D, WIDTH, 0.0D, red, green, blue);
            ribbonQuad(vertices, pose, previous, next, WIDTH, 0.0D, 0.0D, red, green, blue);
            previous = next;
        }
    }

    /** 第 {@code index} 个点：两点之间直线插值 + 一点正弦摆动。 */
    private static RibbonPoint point(int index, float x0, float y0, float z0,
                                     float x1, float y1, float z1,
                                     double timePhase, float offset) {
        float f = index / (float) SEGMENTS;
        double wobble = Math.sin(index * 0.9D + timePhase * 2.0D) * WOBBLE * Math.sin(Math.PI * f);
        float x = x0 + (x1 - x0) * f;
        float y = (float) (y0 + (y1 - y0) * f + wobble);
        float z = z0 + (z1 - z0) * f;
        return new RibbonPoint(x, y, z, f * UV_SCALE + offset);
    }

    /** 一段 = 一个四边形：前后两点各向两侧偏出半个带宽。 */
    private static void ribbonQuad(VertexConsumer vertices, PoseStack.Pose pose,
                                   RibbonPoint first, RibbonPoint second,
                                   double wx, double wy, double wz,
                                   int red, int green, int blue) {
        ribbonVertex(vertices, pose, first.x() - wx, first.y() - wy, first.z() - wz,
                first.u(), 1.0F, red, green, blue);
        ribbonVertex(vertices, pose, first.x() + wx, first.y() + wy, first.z() + wz,
                first.u(), 0.0F, red, green, blue);
        ribbonVertex(vertices, pose, second.x() + wx, second.y() + wy, second.z() + wz,
                second.u(), 0.0F, red, green, blue);
        ribbonVertex(vertices, pose, second.x() - wx, second.y() - wy, second.z() - wz,
                second.u(), 1.0F, red, green, blue);
    }

    /** 一个顶点：位置、鎏金、UV、法线朝上。 */
    private static void ribbonVertex(VertexConsumer vertices, PoseStack.Pose pose,
                                     double x, double y, double z, float u, float v,
                                     int red, int green, int blue) {
        vertices.addVertex(pose, (float) x, (float) y, (float) z)
                .setColor(red, green, blue, Mth.clamp((int) (ALPHA * 255.0F), 0, 255))
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    /** 丝带上的一个点：相对方块原点的坐标与沿长度的 UV。 */
    private record RibbonPoint(float x, float y, float z, float u) {
    }
}
