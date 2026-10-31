package skydeity.mysticpivot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;

/**
 * 玄枢源质罐里的<b>鎏金液体</b>（第一百九十三批新增；第一百九十四批改成斜向渐变）。
 *
 * <h2>用户口径</h2>
 * <blockquote>「中间的液体颜色就改为鎏金色渐变，不用根据不同的要素改颜色 简单一点」<br>
 * 「内部颜色改为鎏金色渐变，<b>斜着渐变</b>」</blockquote>
 *
 * <p>于是这里<b>不读要素色</b>：不管罐里是火还是秩序，液面都是同一支鎏金 ——
 * 底下沉一点（暗琥珀），中间提亮（赤金），液面最亮（近白的淡金）。</p>
 *
 * <h2>「斜着」是怎么做的</h2>
 * <p>第一版是<b>按层</b>取色：同一层四个面的颜色一样，只能竖着渐变，读起来像一块分层
 * 的塑料。这一版改成<b>逐顶点</b>取色：每个顶点的取样位置由两条轴合成 ——</p>
 * <pre>
 *   t = 0.55 · 高度比例 + 0.45 · 对角线比例      （对角线 = x、z 都在同一侧时才最大）
 * </pre>
 * <p>于是颜色沿「西北下角 → 东南上角」那根斜轴推进：贴图上看到的是一条斜跨整个罐子的
 * 金带，而不是一道横杠，同一层里四个面的颜色也各不相同 —— 这才是「渐变」的样子。
 * 权重 0.55 / 0.45 让斜度明显但不过分（全给平面就成了绕圈的色轮，全给高度又退回竖渐变）。</p>
 *
 * <h2>怎么画</h2>
 * <p>照本体防护罐那套几何：罐内空腔 {@code 0.25 … 0.75}，液体从 {@code y=0.0625}
 * 往上长，最高到 {@code 0.6875}（罐颈下面）。竖直方向切成 {@link #BANDS} 段，
 * 每段四个面各四个顶点，<b>每个顶点自己算一次颜色</b>，所以渐变是连续的。</p>
 *
 * <p>渲染类型用 {@code RenderType.solid()}（不是半透明）：液体是<b>不透明</b>的，
 * 先写进主目标并带上深度，罐身那层半透明玻璃随后叠上去才不会和世界里的半透明面
 * 抢排序 —— 本体的防护罐也是这么做的。液体顶点另外加了一道很慢的正弦微光，
 * 沿高度爬，读起来像金的流。</p>
 *
 * <p>★ 液面高度取 {@code 最满那种 / 2048}（见 {@link XuanshuEssentiaJarBlockEntity#fillRatio}），
 * 不在渲染器里改：多要素共罐时按「罐子有多满」看，比按种类分账直观。</p>
 */
public class XuanshuEssentiaJarRenderer implements BlockEntityRenderer<XuanshuEssentiaJarBlockEntity> {

    /** 液体贴图：一张近乎纯白的噪声，颜色全靠顶点色上。 */
    private static final ResourceLocation GLOW =
            ResourceLocation.fromNamespaceAndPath(MysticPivot.MODID, "block/essentia_glow");

    /** 罐内空腔（格）：与本体防护罐的液体盒子一致。 */
    private static final float MIN = 0.25F;
    private static final float MAX = 0.75F;

    /** 液体底面（贴罐底）。 */
    private static final float BOTTOM = 0.0625F;

    /** 满罐时液面高度：0.0625 + 0.625 = 0.6875，正好在罐颈下沿。 */
    private static final float SPAN = 0.625F;

    /** 竖直方向切几段：段越多，贴着玻璃的明暗过渡越顺，10 段肉眼看不出台阶。 */
    private static final int BANDS = 10;

    /** 鎏金三档：底（暗琥珀）/ 腰（赤金）/ 液面（淡金）。 */
    private static final float[] DEEP = {0.42F, 0.24F, 0.04F};
    private static final float[] MID = {0.94F, 0.70F, 0.17F};
    private static final float[] LIGHT = {1.00F, 0.95F, 0.70F};

    /** 中段（赤金）在渐变里的位置：0.55 之前是暗琥珀 → 赤金，之后到液面是赤金 → 淡金。 */
    private static final float MID_STOP = 0.55F;

    /** 斜向渐变里「高度」占多少（其余给水平对角线，两者相加 = 1）。 */
    private static final float DIAG_VERTICAL = 0.55F;

    /** 斜向渐变里「水平对角线」占多少。 */
    private static final float DIAG_PLANE = 1.0F - DIAG_VERTICAL;

    public XuanshuEssentiaJarRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(XuanshuEssentiaJarBlockEntity be, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        float ratio = be.fillRatio();
        if (ratio <= 0.0F) {
            return;
        }
        Level level = be.getLevel();
        if (level == null) {
            return;
        }
        float top = BOTTOM + ratio * SPAN;
        // 时间取模，跑一整天也不会因为浮点太大而抖
        float time = (level.getGameTime() % 12000L) + partialTick;

        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(GLOW);
        // 罐里的液体自发光一点：本体防护罐同样把地板抬到 200，不然暗处一口黑罐
        int light = Math.max(200, packedLight);
        VertexConsumer vc = buffer.getBuffer(RenderType.solid());
        PoseStack.Pose pose = poseStack.last();

        for (int band = 0; band < BANDS; band++) {
            float u0 = (float) band / BANDS;
            float u1 = (float) (band + 1) / BANDS;
            float y0 = Mth.lerp(u0, BOTTOM, top);
            float y1 = Mth.lerp(u1, BOTTOM, top);
            // 沿高度慢慢爬的一道微光：±7%，不改变色相，只让金「流动」起来
            float shimmer = 1.0F + 0.07F * Mth.sin(time * 0.05F - u0 * 5.5F);
            sides(vc, pose, y0, y1, top, shimmer, sprite, light);
        }
        // 液面：最亮的一层，微微呼吸（像金的表面在反射光）
        float breath = 0.94F + 0.06F * Mth.sin(time * 0.04F);
        surface(vc, pose, top, breath, sprite, light);
    }

    // ------------------------------------------------------------------
    // 颜色
    // ------------------------------------------------------------------

    /**
     * 斜向渐变的取样位置 {@code t ∈ [0,1]}。
     *
     * @param x 绝对坐标（罐内 {@code MIN … MAX}）
     * @param y 绝对高度（罐内 {@code BOTTOM … top}）
     * @param z 绝对坐标（罐内 {@code MIN … MAX}）
     * @param top 当前液面高度
     */
    private static float diag(float x, float y, float z, float top) {
        float height = (y - BOTTOM) / Math.max(1.0E-4F, top - BOTTOM);
        // (MIN, MIN) 角为 0，(MAX, MAX) 角为 1 —— 一根横跨罐底的斜轴
        float plane = ((x - MIN) + (z - MIN)) / (2.0F * (MAX - MIN));
        return Mth.clamp(DIAG_VERTICAL * height + DIAG_PLANE * plane, 0.0F, 1.0F);
    }

    /** 鎏金渐变在 {@code t} 处的颜色：暗琥珀 → 赤金 → 淡金。 */
    private static float[] goldAt(float t) {
        return t < MID_STOP
                ? mix(DEEP, MID, t / MID_STOP)
                : mix(MID, LIGHT, (t - MID_STOP) / (1.0F - MID_STOP));
    }

    /** 两色线性插值。 */
    private static float[] mix(float[] from, float[] to, float t) {
        return new float[]{
                Mth.lerp(t, from[0], to[0]),
                Mth.lerp(t, from[1], to[1]),
                Mth.lerp(t, from[2], to[2]),
        };
    }

    // ------------------------------------------------------------------
    // 四个侧面
    // ------------------------------------------------------------------

    /** 一段液体的四个侧面（法向朝外的四边形，顺序与本体罐一致，剔除才不会被翻掉）。 */
    private static void sides(VertexConsumer vc, PoseStack.Pose pose, float y0, float y1,
                              float top, float shimmer,
                              TextureAtlasSprite sprite, int light) {
        // 北（z = MIN，法向 -Z）：左下 (MIN, MIN) → 右上 (MAX, MIN)
        face(vc, pose, MIN, MIN, MAX, MIN, y0, y1, top, shimmer, sprite, light, 0, 0, -1);
        // 南（z = MAX，法向 +Z）：左下 (MAX, MAX) → 右上 (MIN, MAX)
        face(vc, pose, MAX, MAX, MIN, MAX, y0, y1, top, shimmer, sprite, light, 0, 0, 1);
        // 西（x = MIN，法向 -X）：左下 (MIN, MAX) → 右上 (MIN, MIN)
        face(vc, pose, MIN, MAX, MIN, MIN, y0, y1, top, shimmer, sprite, light, -1, 0, 0);
        // 东（x = MAX，法向 +X）：左下 (MAX, MIN) → 右上 (MAX, MAX)
        face(vc, pose, MAX, MIN, MAX, MAX, y0, y1, top, shimmer, sprite, light, 1, 0, 0);
    }

    /**
     * 一个侧面：竖直的两条边分别过 {@code (blx, blz)} 与 {@code (trx, trz)}，
     * 从 {@code y0} 到 {@code y1}。
     *
     * <p>顶点顺序沿用本体罐的「左下 → 左上 → 右上 → 右下」（从法向正面看是逆时针），
     * 反了会被面剔除直接剔掉。四个顶点<b>各取一次颜色</b>，斜向渐变才连得上。</p>
     */
    private static void face(VertexConsumer vc, PoseStack.Pose pose,
                             float blx, float blz, float trx, float trz,
                             float y0, float y1, float top, float shimmer,
                             TextureAtlasSprite sprite, int light,
                             float nx, float ny, float nz) {
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        vertex(vc, pose, blx, y0, blz, goldAt(diag(blx, y0, blz, top)), shimmer, u0, v1, light, nx, ny, nz);
        vertex(vc, pose, blx, y1, blz, goldAt(diag(blx, y1, blz, top)), shimmer, u0, v0, light, nx, ny, nz);
        vertex(vc, pose, trx, y1, trz, goldAt(diag(trx, y1, trz, top)), shimmer, u1, v0, light, nx, ny, nz);
        vertex(vc, pose, trx, y0, trz, goldAt(diag(trx, y0, trz, top)), shimmer, u1, v1, light, nx, ny, nz);
    }

    /** 液面那片水平面（法向 +Y）：斜向渐变照旧，四个角各取一次色。 */
    private static void surface(VertexConsumer vc, PoseStack.Pose pose, float y, float breath,
                                TextureAtlasSprite sprite, int light) {
        float u0 = sprite.getU0();
        float u1 = sprite.getU1();
        float v0 = sprite.getV0();
        float v1 = sprite.getV1();
        vertex(vc, pose, MIN, y, MAX, goldAt(diag(MIN, y, MAX, y)), breath, u0, v1, light, 0, 1, 0);
        vertex(vc, pose, MAX, y, MAX, goldAt(diag(MAX, y, MAX, y)), breath, u1, v1, light, 0, 1, 0);
        vertex(vc, pose, MAX, y, MIN, goldAt(diag(MAX, y, MIN, y)), breath, u1, v0, light, 0, 1, 0);
        vertex(vc, pose, MIN, y, MIN, goldAt(diag(MIN, y, MIN, y)), breath, u0, v0, light, 0, 1, 0);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose, float x, float y, float z,
                               float[] color, float scale, float u, float v, int light,
                               float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z)
                .setColor(Mth.clamp(color[0] * scale, 0.0F, 1.0F),
                        Mth.clamp(color[1] * scale, 0.0F, 1.0F),
                        Mth.clamp(color[2] * scale, 0.0F, 1.0F), 1.0F)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
