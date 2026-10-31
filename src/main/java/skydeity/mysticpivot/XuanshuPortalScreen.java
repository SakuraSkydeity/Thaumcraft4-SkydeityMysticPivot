package skydeity.mysticpivot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 玄枢传送门的界面（第一百九十八批）：<b>与玄枢虚空水晶塔同一副壳子</b>。
 *
 * <p>用户口径：「<b>给传送门添加和矿机一样的 ui（只添加 ui 不设计功能，先把建筑搞定）</b>」。
 * 于是版式全照搬 —— 196×188 的 AE 亮色面板、3×4 栏、竖排进度条、右侧两枚按钮、
 * 按钮下的激活点，连坐标都直接读 {@link XuanshuVoidMiningMenu} 那一套常量，
 * 不是抄一份数字（抄一遍迟早会分叉）。</p>
 *
 * <p>唯一的差别是<b>进度条量什么</b>：矿机量产出周期，这里量<b>工程量</b> ——
 * 已立起的格数 / 1099。按完「一键搭建」看着条往上走，就是这一批要做的事。
 * 传送还没接，所以栏里不放产出、也没有「已激活」之外的机器状态。</p>
 */
public class XuanshuPortalScreen extends AbstractContainerScreen<XuanshuPortalMenu> {

    /** 进度条（竖排，栏右侧）：x / y / 宽 / 高。与矿机同位置。 */
    private static final int BAR_X = 126, BAR_Y = 22, BAR_W = 8, BAR_H = 54;

    /** 按钮面：常态 / 悬停。 */
    private static final int BTN_FACE = 0xFFD3D8EA;
    private static final int BTN_FACE_HOVER = 0xFFE9EEFB;

    /** 激活状态点：亮青 / 灰。 */
    private static final int DOT_ON = 0xFF2FA396;
    private static final int DOT_OFF = 0xFFA7AEC6;

    public XuanshuPortalScreen(XuanshuPortalMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 196;
        this.imageHeight = 188;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
        this.inventoryLabelX = XuanshuVoidMiningMenu.INV_X;
        this.inventoryLabelY = 92;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderButtonTooltips(graphics, mouseX, mouseY);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos, y = this.topPos;
        XuanshuGui.panel(graphics, x, y, this.imageWidth, this.imageHeight);

        // 玩家背包 27+9 格逐格画框（自绘面板必须自己画）
        for (Slot slot : this.menu.slots) {
            XuanshuGui.slot(graphics, x + slot.x - 1, y + slot.y - 1);
        }

        // 工程量（竖排青金条）
        progressBar(graphics, x + BAR_X, y + BAR_Y, this.menu.getFillRatio());

        // 两个结构按钮
        drawButton(graphics, mouseX, mouseY, XuanshuPortalMenu.BUTTON_BUILD,
                "skydeitymysticpivot.gui.build", XuanshuGui.TEXT_ACCENT);
        drawButton(graphics, mouseX, mouseY, XuanshuPortalMenu.BUTTON_DISMANTLE,
                "skydeitymysticpivot.gui.dismantle", XuanshuGui.TEXT_SOFT);

        // 激活状态：一枚小方点 + 两个字
        boolean formed = this.menu.isFormed();
        int dotY = y + buttonY(XuanshuPortalMenu.BUTTON_DISMANTLE)
                + XuanshuVoidMiningMenu.BTN_H + 6;
        graphics.fill(x + XuanshuVoidMiningMenu.BTN_X, dotY,
                x + XuanshuVoidMiningMenu.BTN_X + 5, dotY + 5, formed ? DOT_ON : DOT_OFF);
        XuanshuGui.centerText(graphics, this.font,
                Component.translatable(formed
                        ? "skydeitymysticpivot.gui.active"
                        : "skydeitymysticpivot.gui.inactive"),
                x + XuanshuVoidMiningMenu.BTN_X + 22,
                dotY - 1,
                formed ? XuanshuGui.TEXT_ACCENT : XuanshuGui.TEXT_SOFT);
    }

    // ------------------------------------------------------------------
    // 进度条与按钮
    // ------------------------------------------------------------------

    /**
     * 竖排进度条：<b>先灌青、后画框</b>（同矿机，第 193 批定的画法）。
     *
     * <p>灌色全部画在框<b>下面</b>，四边最后落笔 —— 无论进度多少，框都是完整的一圈，
     * 满进度时底边也不会被青条吃掉。</p>
     */
    private void progressBar(GuiGraphics graphics, int x, int y, float ratio) {
        graphics.fill(x + 1, y + 1, x + BAR_W - 1, y + BAR_H - 1, XuanshuGui.ARROW_CHANNEL);
        int inner = BAR_H - 2;
        int fill = Mth.clamp((int) Math.ceil(Mth.clamp(ratio, 0.0F, 1.0F) * inner), 0, inner);
        if (fill > 0) {
            int top = y + BAR_H - 1 - fill;
            graphics.fill(x + 1, top, x + BAR_W - 1, y + BAR_H - 1, XuanshuGui.ARROW_FILL_MID);
            // 前沿 1 像素亮青：走到哪儿一眼看得出
            graphics.fill(x + 1, top, x + BAR_W - 1, top + 1, XuanshuGui.ARROW_FILL_HI);
        }
        XuanshuGui.ring(graphics, x, y, BAR_W, BAR_H, XuanshuGui.PANEL_EDGE);
    }

    /** 第 {@code id} 个按钮在面板内的 y。 */
    private static int buttonY(int id) {
        return XuanshuVoidMiningMenu.BTN_Y
                + id * (XuanshuVoidMiningMenu.BTN_H + XuanshuVoidMiningMenu.BTN_GAP);
    }

    private void drawButton(GuiGraphics graphics, int mouseX, int mouseY, int id,
                            String labelKey, int textColor) {
        int x = this.leftPos + XuanshuVoidMiningMenu.BTN_X;
        int y = this.topPos + buttonY(id);
        int w = XuanshuVoidMiningMenu.BTN_W;
        int h = XuanshuVoidMiningMenu.BTN_H;
        boolean hover = overButton(mouseX, mouseY, id);

        graphics.fill(x, y, x + w, y + h, XuanshuGui.PANEL_EDGE);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1,
                hover ? BTN_FACE_HOVER : BTN_FACE);
        if (!hover) {
            graphics.fill(x + 1, y + 1, x + w - 1, y + 2, XuanshuGui.PANEL_HI);
            graphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, XuanshuGui.PANEL_LO);
        } else {
            graphics.fill(x + 1, y + 1, x + w - 1, y + 2, XuanshuGui.ARROW_FILL_HI);
        }
        XuanshuGui.centerText(graphics, this.font, Component.translatable(labelKey),
                x + w / 2, y + (h - 8) / 2, textColor);
    }

    private boolean overButton(double mouseX, double mouseY, int id) {
        int x = this.leftPos + XuanshuVoidMiningMenu.BTN_X;
        int y = this.topPos + buttonY(id);
        return mouseX >= x && mouseX < x + XuanshuVoidMiningMenu.BTN_W
                && mouseY >= y && mouseY < y + XuanshuVoidMiningMenu.BTN_H;
    }

    /**
     * 悬停提示：只有<b>一键搭建</b>那枚有（它要报一长串材料）；一键收回是一次性动作，不弹框。
     */
    private void renderButtonTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!overButton(mouseX, mouseY, XuanshuPortalMenu.BUTTON_BUILD)) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("skydeitymysticpivot.gui.build.portal"));
        for (Map.Entry<Item, Integer> entry : PortalGateStructure.materials().entrySet()) {
            lines.add(Component.literal(" ")
                    .append(Component.translatable(entry.getKey().getDescriptionId()))
                    .append(" ×" + entry.getValue())
                    .withStyle(style -> style.withColor(0x6E7794)));
        }
        graphics.renderTooltip(this.font, lines, java.util.Optional.empty(), mouseX, mouseY);
    }

    // ------------------------------------------------------------------
    // 交互
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (overButton(mouseX, mouseY, XuanshuPortalMenu.BUTTON_BUILD)) {
                sendButton(XuanshuPortalMenu.BUTTON_BUILD);
                return true;
            }
            if (overButton(mouseX, mouseY, XuanshuPortalMenu.BUTTON_DISMANTLE)) {
                sendButton(XuanshuPortalMenu.BUTTON_DISMANTLE);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void sendButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
            this.minecraft.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance
                            .forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
        }
    }
}
