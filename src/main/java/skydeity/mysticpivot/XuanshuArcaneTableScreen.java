package skydeity.mysticpivot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

import static skydeity.mysticpivot.XuanshuGui.*;

/**
 * 玄枢奥术台的界面——骨架与三台机器同款（{@link XuanshuGui#panel} 面板 +
 * 遍历 {@code menu.slots} 的 {@link XuanshuGui#slot} 格框 + 标题 {@link #init()} 居中），
 * 第九十七批把<b>两格改成左右并排</b>、并把面板高度补齐。
 *
 * <h2>本批两处改动（用户原话：「里面的ui改为左右 上下会超出框」）</h2>
 * <ul>
 *   <li><b>左右</b>：物品槽 / 书槽由「上下两行」改成<b>同一行左右相对</b>
 *       （{@link XuanshuArcaneTableMenu#TARGET_X} 44 / {@link XuanshuArcaneTableMenu#BOOK_X} 116，
 *       中间画一个「+」），提示小字各贴在自己槽下，不再占纵向空间；</li>
 *   <li><b>上下会超出框</b>：面板 {@code imageHeight} 一直是 166，而玩家背包三行与快捷栏
 *       落在 112 / 130 / 148 / 170 —— <b>快捷栏整条都在面板外面</b>（§12.94.3 那句
 *       「物品栏也不完整」就是这个）。本批提到 <b>190</b>，188 底边落在面板内。</li>
 * </ul>
 *
 * <p>内容：顶部「受附情报」框（附魔条数 · 最高级 / 运转消耗）；中部两槽左右相对；
 * 其下一行居中「持法杖右键本台运转」提示。</p>
 */
public class XuanshuArcaneTableScreen extends AbstractContainerScreen<XuanshuArcaneTableMenu> {

    private static final int INFO_X = 8, INFO_Y = 18, INFO_W = 160, INFO_H = 14;

    /** 面板总高：背包 112 / 130 / 148 + 快捷栏 170，加 18 高与 2 边距 = 190。 */
    private static final int PANEL_H = 190;

    /**
     * 书槽里的「附魔书描边」底图（第二百一十四批）。
     *
     * <p>16×16，取 `disenchant_book.png` 的 alpha 剪影（= 原版附魔书的形状）：
     * 只描一圈轮廓 + 极淡的填充，空槽自己说明「这里放书」，又不会像真放了一本。
     * 由 {@code tools/gen_arcane_table_gui.py} 生成；锭那枚同步改淡到同一浓度。</p>
     */
    private static final ResourceLocation BOOK_GHOST =
            ResourceLocation.fromNamespaceAndPath(MysticPivot.MODID, "textures/gui/book_ghost.png");

    public XuanshuArcaneTableScreen(XuanshuArcaneTableMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = PANEL_H;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
        this.inventoryLabelY = 100;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos, y = this.topPos;
        panel(g, x, y, this.imageWidth, this.imageHeight);
        for (Slot slot : this.menu.slots) {
            slot(g, x + slot.x - 1, y + slot.y - 1);
        }
        drawInfo(g, x + INFO_X, y + INFO_Y);
        drawBookGhost(g, x, y);

        // 两槽之间的「+」：一眼看出「物品 + 附魔书（可选）→ 附魔更上一层」
        centerText(g, this.font, Component.literal("+"),
                x + this.imageWidth / 2, y + XuanshuArcaneTableMenu.TARGET_Y + 5, TEXT_ACCENT);

        // 两槽小字各贴自己槽下（左右并排，不再叠在背包上）
        int hintY = y + XuanshuArcaneTableMenu.TARGET_Y + 20;
        centerText(g, this.font,
                Component.translatable("gui.skydeitymysticpivot.arcane_table.hint_item"),
                x + XuanshuArcaneTableMenu.TARGET_X + 9, hintY, TEXT_SOFT);
        centerText(g, this.font,
                Component.translatable("gui.skydeitymysticpivot.arcane_table.hint_book"),
                x + XuanshuArcaneTableMenu.BOOK_X + 9, hintY, TEXT_SOFT);
        centerText(g, this.font,
                Component.translatable("gui.skydeitymysticpivot.arcane_table.hint"),
                x + this.imageWidth / 2, hintY + 16, TEXT_ACCENT);
    }

    /**
     * 书槽空着时，在槽底画一本**很淡的附魔书轮廓**（第二百一十四批）。
     *
     * <p>贴图是 16×16、与槽内格同尺寸，直接对齐槽位内框（槽框本身比格子大 1 像素，
     * 所以这里从 {@code slot.x}/{@code slot.y} 起画，与物品图标落点一致）。
     * 有书时由原版渲染叠加，不再画 —— 免得影子透到真书下面。</p>
     */
    private void drawBookGhost(GuiGraphics g, int left, int top) {
        Slot bookSlot = this.menu.slots.get(XuanshuArcaneTableMenu.SLOT_BOOK);
        if (bookSlot.hasItem()) {
            return;
        }
        g.blit(BOOK_GHOST, left + bookSlot.x, top + bookSlot.y, 16, 16,
                0.0F, 0.0F, 16, 16, 16, 16);
    }

    /**
     * 情报框：共享四色（外描边 / 内填 / 上高光 / 下暗影），**只有一行**。
     *
     * <p>★ 第九十八批：用户「把运转消耗那一行字全删了」——消耗那行（玄枢锭 + 六元各 N）
     * 整行去掉，框高跟着 22 → 14，只留「附魔 N 条 · 最高 N 级」。</p>
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

        Component ench = Component.translatable("message.skydeitymysticpivot.arcane_table.ench",
                        this.menu.targetEnchantKinds(), this.menu.targetMaxLevel())
                .withStyle(s -> s.withColor(TEXT_ACCENT));
        g.drawString(this.font, ench, x + 8, y + 3, TEXT_ACCENT_SOFT, false);
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
