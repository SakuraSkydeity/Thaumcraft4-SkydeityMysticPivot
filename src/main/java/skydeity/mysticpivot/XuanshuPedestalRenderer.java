package skydeity.mysticpivot;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * 玄枢台座渲染，两件事：
 *
 * <ol>
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
