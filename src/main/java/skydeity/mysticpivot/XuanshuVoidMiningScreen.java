package skydeity.mysticpivot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 玄枢虚空采矿机的界面：AE 亮色风 + 两个结构按钮（第一百八十六批）。
 *
 * <p>上半：3×4 产出栏（第一百九十三批加一列）+ 竖排进度条 + 右侧两个按钮
 * （一键搭建 / 一键收回）与激活状态；**产物栏左边隔 14 像素还有竖排三格锭槽**
 * （第二百一十二批：空槽铺锭描边，悬停给档位说明，槽下写「效率 +N」）；
 * 下半：玩家背包 27+9 格。面板、槽位、进度全部走 {@link XuanshuGui} 的 AE 画笔，
 * 按钮是同色系的压印小块（上左高光 / 下右灰影 / 近黑描边），与三台老机器的侧配按钮同族。</p>
 *
 * <p>按钮点击走 {@code clickMenuButton} 通道，服务端真正动世界；
 * 客户端只负责画与发意图。</p>
 */
public class XuanshuVoidMiningScreen extends AbstractContainerScreen<XuanshuVoidMiningMenu> {

    /** 进度条（竖排，产出栏右侧）：x / y / 宽 / 高。 */
    private static final int BAR_X = 126, BAR_Y = 22, BAR_W = 8, BAR_H = 54;

    /**
     * 锭槽里的「锭描边」底图（第二百一十二批）。
     *
     * <p>16×16，取原版铁锭的 alpha 剪影：<b>只描一圈轮廓 + 极淡的填充</b>，
     * 让空槽自己说明「这里插锭」，又不会像真放了东西。
     * 由 {@code tools/gen_void_mining_gui.py} 生成。</p>
     */
    private static final ResourceLocation INGOT_GHOST =
            ResourceLocation.fromNamespaceAndPath(MysticPivot.MODID, "textures/gui/ingot_ghost.png");

    /** 按钮面：常态 / 悬停。 */
    private static final int BTN_FACE = 0xFFD3D8EA;
    private static final int BTN_FACE_HOVER = 0xFFE9EEFB;

    /** 激活状态点：亮青 / 灰。 */
    private static final int DOT_ON = 0xFF2FA396;
    private static final int DOT_OFF = 0xFFA7AEC6;

    public XuanshuVoidMiningScreen(XuanshuVoidMiningMenu menu, Inventory inv, Component title) {
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
        this.renderUpgradeTooltip(graphics, mouseX, mouseY);
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

        // 锭槽：空槽里画一枚锭的描边（第二百一十二批）
        drawUpgradeGhosts(graphics, x, y);
        // 三格合计效率，写在锭槽正下方（字小，只报一个数）
        XuanshuGui.centerText(graphics, this.font,
                Component.translatable("skydeitymysticpivot.gui.void_mining.efficiency",
                        this.menu.getEfficiency()),
                x + XuanshuVoidMiningMenu.UP_X + 8,
                y + XuanshuVoidMiningMenu.UP_Y + 3 * XuanshuVoidMiningMenu.UP_STEP + 3,
                this.menu.getEfficiency() > 0 ? XuanshuGui.TEXT_ACCENT : XuanshuGui.TEXT_SOFT);

        // 产出进度（竖排青金条）
        progressBar(graphics, x + BAR_X, y + BAR_Y, this.menu.getProgressRatio());

        // 两个结构按钮
        drawButton(graphics, mouseX, mouseY, XuanshuVoidMiningMenu.BUTTON_BUILD,
                "skydeitymysticpivot.gui.build", XuanshuGui.TEXT_ACCENT);
        drawButton(graphics, mouseX, mouseY, XuanshuVoidMiningMenu.BUTTON_DISMANTLE,
                "skydeitymysticpivot.gui.dismantle", XuanshuGui.TEXT_SOFT);

        // 激活状态：一枚小方点 + 两个字
        boolean formed = this.menu.isFormed();
        int dotY = y + buttonY(XuanshuVoidMiningMenu.BUTTON_DISMANTLE)
                + XuanshuVoidMiningMenu.BTN_H + 6;
        graphics.fill(x + XuanshuVoidMiningMenu.BTN_X, dotY, x + XuanshuVoidMiningMenu.BTN_X + 5,
                dotY + 5, formed ? DOT_ON : DOT_OFF);
        XuanshuGui.centerText(graphics, this.font,
                Component.translatable(formed
                        ? "skydeitymysticpivot.gui.active"
                        : "skydeitymysticpivot.gui.inactive"),
                x + XuanshuVoidMiningMenu.BTN_X + 22,
                dotY - 1,
                formed ? XuanshuGui.TEXT_ACCENT : XuanshuGui.TEXT_SOFT);
    }

    // ------------------------------------------------------------------
    // 锭槽
    // ------------------------------------------------------------------

    /**
     * 三格锭槽的底图：<b>只画空槽</b>，且画在物品之前 —— 装了锭的槽由物品自己说话。
     *
     * <p>描边图是 16×16 的半透明 PNG（原版铁锭剪影），直接铺在槽底上，
     * 与浅色 AE 面板不打架；不额外上色，免得三格各带一块色斑。</p>
     */
    private void drawUpgradeGhosts(GuiGraphics graphics, int x, int y) {
        for (int i = 0; i < XuanshuVoidMiningBlockEntity.UPGRADE_SLOTS; i++) {
            Slot slot = this.menu.slots.get(XuanshuVoidMiningBlockEntity.UPGRADE_BASE + i);
            if (slot.hasItem()) {
                continue;
            }
            graphics.blit(INGOT_GHOST, x + slot.x, y + slot.y, 16, 16,
                    0.0F, 0.0F, 16, 16, 16, 16);
        }
    }

    /**
     * 悬停在锭槽上时的说明：收哪三种锭、各加几级、不消耗。
     *
     * <p>只在悬停锭槽时弹，别的时候不占屏幕。数字用「+3 / +2 / +1」直接写死在这里，
     * 与 {@link XuanshuVoidMiningBlockEntity#tierOf} 一一对应。</p>
     */
    private void renderUpgradeTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        Slot hovered = this.hoveredSlot;
        if (hovered == null || hovered.index < XuanshuVoidMiningBlockEntity.UPGRADE_BASE
                || hovered.index >= XuanshuVoidMiningBlockEntity.SLOT_COUNT) {
            return;
        }
        if (hovered.hasItem()) {
            return;   // 装了锭就让它自己的物品提示说话（名字 + 稀有度）
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("skydeitymysticpivot.gui.void_mining.upgrade_title")
                .withStyle(style -> style.withColor(XuanshuGui.TEXT_ACCENT)));
        lines.add(Component.translatable("skydeitymysticpivot.gui.void_mining.upgrade_hint")
                .withStyle(style -> style.withColor(0x6E7794)));
        lines.add(Component.translatable("skydeitymysticpivot.gui.void_mining.upgrade_tiers")
                .withStyle(style -> style.withColor(0x6E7794)));
        graphics.renderTooltip(this.font, lines, java.util.Optional.empty(), mouseX, mouseY);
    }

    // ------------------------------------------------------------------
    // 按钮
    // ------------------------------------------------------------------

    /**
     * 竖排产出进度条：<b>先灌青、后画框</b>。
     *
     * <p>★ 第一百九十三批修「最底下没有封闭」：原来是先画一圈近黑描边、再拿青条去盖，
     * 满进度时青条正好压到框的内沿，底边那一行被吃掉，看上去像底下漏了一道缝。
     * 现在把灌色全部画在框<b>下面</b>，四边最后落笔 —— 无论进度多少，框都是完整的一圈。</p>
     */
    private void progressBar(GuiGraphics graphics, int x, int y, float ratio) {
        // 空槽：与进度箭同一支凹槽色，青条灌进来才有「从底往上走」的对比
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
     * 悬停提示：只剩<b>一键搭建</b>那枚有（它要报一长串材料）。
     *
     * <p>★ 第一百九十三批：<b>一键收回那枚的框删掉了</b>。用户口径：「ui 一键收回移动上去
     * 显示的框删去」—— 收回是一次性动作，没有要交代的数字，弹个框只是碍眼。</p>
     */
    private void renderButtonTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (!overButton(mouseX, mouseY, XuanshuVoidMiningMenu.BUTTON_BUILD)) {
            return;
        }
        List<Component> lines = new ArrayList<>();
        // 第一百九十二批：形制已经编译进代码（VoidAltarBlueprint），只剩一种口径。
        // 先说一句「材料是按那座塔算的」，否则看到一长串玻璃 / 羊毛会以为是别的东西。
        lines.add(Component.translatable("skydeitymysticpivot.gui.build.custom"));
        for (Map.Entry<Item, Integer> entry : VoidMiningStructure.materials().entrySet()) {
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
            if (overButton(mouseX, mouseY, XuanshuVoidMiningMenu.BUTTON_BUILD)) {
                sendButton(XuanshuVoidMiningMenu.BUTTON_BUILD);
                return true;
            }
            if (overButton(mouseX, mouseY, XuanshuVoidMiningMenu.BUTTON_DISMANTLE)) {
                sendButton(XuanshuVoidMiningMenu.BUTTON_DISMANTLE);
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
