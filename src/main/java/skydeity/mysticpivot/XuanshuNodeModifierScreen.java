package skydeity.mysticpivot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import static skydeity.mysticpivot.XuanshuGui.*;

/**
 * 玄枢注灵香炉台的界面——骨架与三台机器同款：
 * {@link XuanshuGui#panel} 画面板、{@link XuanshuGui#slot} 遍历 {@code menu.slots}
 * 画全部格框（玩家背包 27+9 也覆盖），标题在 {@link #init()} 里居中。
 *
 * <p>内容：顶部「节点情报」框（类型中文单字 + 要素种类 · 现存量/容量）；
 * 中部<b>华槽</b>（左）与<b>锭槽</b>（右）相对；两槽下小字 + 居中「持法杖右键运转」提示。</p>
 *
 * <p>★ 第九十七批两处：① 右槽由「化槽：转化之物」改为「锭槽：玄枢锭」；
 * ② 面板高 166 → {@value #PANEL_H}（背包三行 + 快捷栏落在 112 / 130 / 148 / 170，
 * 166 的底边会把快捷栏整条留在面板外——与奥术台同一个毛病，一并补上）。</p>
 */
public class XuanshuNodeModifierScreen extends AbstractContainerScreen<XuanshuNodeModifierMenu> {

    /** 情报框尺寸与位置（相对面板左上角）。 */
    private static final int INFO_X = 8, INFO_Y = 18, INFO_W = 160, INFO_H = 22;

    /** 面板总高：背包 148 + 18 与快捷栏 170 + 18，加边距 = 190。 */
    private static final int PANEL_H = 190;

    public XuanshuNodeModifierScreen(XuanshuNodeModifierMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = PANEL_H;
    }

    @Override
    protected void init() {
        super.init();
        // 与炎炉 / 坩埚同款：标题居中、「物品栏」贴背包三行上一行
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
        this.inventoryLabelY = 100;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos, y = this.topPos;
        // 面板（共享画法）
        panel(g, x, y, this.imageWidth, this.imageHeight);
        // 全部槽位（含玩家背包）逐格画——遍历 menu.slots，与炎炉一模一样
        for (Slot slot : this.menu.slots) {
            slot(g, x + slot.x - 1, y + slot.y - 1);
        }
        // 节点情报框
        drawInfo(g, x + INFO_X, y + INFO_Y);
        // 小字提示
        int hintY = y + XuanshuNodeModifierMenu.BLOOM_Y + 24;
        centerText(g, this.font,
                Component.translatable("gui.skydeitymysticpivot.node_modifier.hint_bloom"),
                x + XuanshuNodeModifierMenu.BLOOM_X + 9, hintY, TEXT_SOFT);
        centerText(g, this.font,
                Component.translatable("gui.skydeitymysticpivot.node_modifier.hint_trans"),
                x + XuanshuNodeModifierMenu.TRANS_X + 9, hintY, TEXT_SOFT);
        centerText(g, this.font,
                Component.translatable("gui.skydeitymysticpivot.node_modifier.hint_wand"),
                x + this.imageWidth / 2, hintY + 12, TEXT_ACCENT);
    }

    /**
     * 情报框：外描边 + 内填 + 上高光下暗影（共享四色），两行文字**居中**。
     *
     * <p>★ 第一百零二批：用户「中间节点类型要素种类这两行字在方框的中间 居中」——
     * 原先两行都左对齐（{@code drawString} 从 {@code x+8} 起笔），现在走框的水平中线
     * {@code x + INFO_W/2}。</p>
     *
     * <p>★ 第一百五十三批：用户「方框里面居中 上下不是居中」——只做了水平居中，
     * 两行压在 {@code y+5 / y+13}（块高 16、框高 22），上留 5 下留 1，看着往下坠。
     * 改成 {@code y+3 / y+11}，上下各留 3，并且一并去掉文字投影（见
     * {@link XuanshuGui#centerText}）。</p>
     */
    private void drawInfo(GuiGraphics g, int x, int y) {
        g.fill(x + 1, y, x + INFO_W - 1, y + INFO_H, PANEL_BODY);
        g.fill(x, y + 1, x + 1, y + INFO_H - 1, PANEL_HI);
        g.fill(x + 1, y, x + INFO_W - 1, y + 1, PANEL_HI);
        g.fill(x + INFO_W - 1, y + 1, x + INFO_W, y + INFO_H - 1, PANEL_LO);
        g.fill(x + 1, y + INFO_H - 1, x + INFO_W - 1, y + INFO_H, PANEL_LO);
        g.fill(x, y, x + INFO_W, y + 1, PANEL_EDGE);
        g.fill(x, y + INFO_H - 1, x + INFO_W, y + INFO_H, PANEL_EDGE);
        g.fill(x, y, x + 1, y + INFO_H, PANEL_EDGE);
        g.fill(x + INFO_W - 1, y, x + INFO_W, y + INFO_H, PANEL_EDGE);

        // 两行各 8 像素、行距 8（共 16），框高 22 —— 上边留 3、下边留 3 才是真居中
        int mid = x + INFO_W / 2;
        Component type = Component.translatable("message.skydeitymysticpivot.node_modifier.type")
                .append(" ")
                .append(Component.translatable(XuanshuNodeModifierMenu.typeLangKey(this.menu.nodeTypeOrdinal()))
                        .withStyle(s -> s.withColor(TEXT_ACCENT)));
        centerText(g, this.font, type, mid, y + 3, TEXT_ACCENT_SOFT);
        Component stats = Component.translatable("message.skydeitymysticpivot.node_modifier.stats",
                this.menu.nodeAspectKinds(), this.menu.nodeCurrentSum(), this.menu.nodeBaseSum());
        centerText(g, this.font, stats, mid, y + 11, TEXT_SOFT);
    }


    /**
     * 标题与「物品栏」也走**不带投影**的字（第一百五十三批）。
     *
     * <p>{@code AbstractContainerScreen#renderLabels} 里这两行是 {@code drawString(..., true)}，
     * 面板上唯一还剩的两处投影就在这里；用户嫌「字体厚重」，把这两行一起清掉，
     * 整个界面的字面才统一干净。</p>
     */
    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFF3C4157, false);
        g.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY,
                0xFF3C4157, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        this.renderTooltip(g, mouseX, mouseY);
    }
}
