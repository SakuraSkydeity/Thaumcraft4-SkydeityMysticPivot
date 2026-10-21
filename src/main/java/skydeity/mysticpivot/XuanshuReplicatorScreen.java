package skydeity.mysticpivot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * 玄枢复刻台的界面。
 *
 * <h2>主面板</h2>
 * <p>骨架与玄枢炎炉同一套（{@link XuanshuGui}，AE2 1.21.1 浅灰蓝亮色）：
 * 底板、格框、进度条全部 {@link GuiGraphics#fill} 自绘。布局极简——</p>
 * <ul>
 *   <li><b>一行两槽</b>：左 = 模板槽、右 = 产物槽，中间横一支<b>与炎炉同款的符文进度箭</b>
 *       （{@link XuanshuGui#progressArrow}，同宽 26 × 高 14，青绿渐变灌芯）；</li>
 *   <li>下方就是玩家背包（27 + 9 格）。</li>
 * </ul>
 *
 * <h2>「传输配置」面板（第一百四十四批，与炎炉同款）</h2>
 * <p>左缘页签展开 / 收起；3×3 十字的六个面按钮，底色即模式
 * （灰 = 关闭、蓝 = 自动输入、橙 = 自动输出、紫 = 输入输出），格内画出该面外侧
 * 挨着的方块；底部「输入 / 输出 / 输入输出」三格色例。点击走菜单的
 * {@code clickMenuButton} 通道，服务端真正改状态。</p>
 *
 * <p>★ 面板是自绘的，玩家背包 27+9 格和机器两格都必须逐格画框。</p>
 */
public class XuanshuReplicatorScreen extends AbstractContainerScreen<XuanshuReplicatorMenu> {

    /** 左缘展开页签（相对面板左上角）。 */
    private static final int TAB_W = 14, TAB_H = 16, TAB_Y = 40;

    /** 配置板尺寸与内部布局（紧凑版，无精确输入开关）。 */
    private static final int WIN_W = 84, WIN_H = 112;
    private static final int CELL = 20, GAP = 3;
    private static final int GRID_W = CELL * 3 + GAP * 2;
    private static final int GRID_X = (WIN_W - GRID_W) / 2;
    private static final int GRID_Y = 24;
    /** 图例：两行（输入 / 输出 一行，输入输出 一行）。 */
    private static final int LEGEND_Y = 92, LEGEND_ROW = 11;

    /** 六个面在 3×3 十字里的位置（行, 列）；中心格（1,1）代表北面。 */
    private static final int[][] CELL_POS = new int[6][];
    static {
        CELL_POS[Direction.UP.ordinal()] = new int[]{0, 1};
        CELL_POS[Direction.WEST.ordinal()] = new int[]{1, 0};
        CELL_POS[Direction.NORTH.ordinal()] = new int[]{1, 1};
        CELL_POS[Direction.EAST.ordinal()] = new int[]{1, 2};
        CELL_POS[Direction.DOWN.ordinal()] = new int[]{2, 0};
        CELL_POS[Direction.SOUTH.ordinal()] = new int[]{2, 1};
    }

    /** 进度箭头（与玄枢炎炉同款的符文箭）。 */
    private static final int BAR_DX = 24, BAR_DY = 2, BAR_W = 26, BAR_H = 14;

    /** 主面板 / 配置板配色（全部取 {@link XuanshuGui} 的 AE 调色板）。 */
    private static final int COL_PANEL = XuanshuGui.PANEL_BODY;
    private static final int COL_HI = XuanshuGui.PANEL_HI;
    private static final int COL_LO = XuanshuGui.PANEL_LO;
    private static final int COL_EDGE = XuanshuGui.PANEL_EDGE;
    private static final int COL_TITLE = 0xFF3F3F3F;
    private static final int COL_OFF = 0xFF8B8B8B, COL_OFF_DK = 0xFF6E6E6E;
    private static final int COL_INPUT = 0xFF3B6FD4, COL_INPUT_DK = 0xFF27498F;
    private static final int COL_OUTPUT = 0xFFE07A1F, COL_OUTPUT_DK = 0xFF9A4E10;
    private static final int COL_EJECT = 0xFF915DCD, COL_EJECT_DK = 0xFF5B2E96;

    private boolean configOpen;

    public XuanshuReplicatorScreen(XuanshuReplicatorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        // 机器一行（34~52）+ 背包标签（72）+ 背包三行（84~138）+ 热栏（142~160）
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
    }

    // ------------------------------------------------------------------
    // 绘制
    // ------------------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos, y = this.topPos;
        XuanshuGui.panel(graphics, x, y, this.imageWidth, this.imageHeight);

        // ★ 面板是自绘的：机器两格 + 玩家背包 27+9 格全部逐格画框
        for (Slot slot : this.menu.slots) {
            XuanshuGui.slot(graphics, x + slot.x - 1, y + slot.y - 1);
        }

        // 进度箭头：与玄枢炎炉同一支符文箭（青绿渐变 + 白青灵光前沿）
        int bx = x + XuanshuReplicatorMenu.INPUT_X + BAR_DX;
        int by = y + XuanshuReplicatorMenu.ROW_Y + BAR_DY;
        XuanshuGui.progressArrow(graphics, bx, by, BAR_W, BAR_H, this.menu.getCopyProgress());

        drawTab(graphics, x, y);
        if (configOpen) {
            drawConfigWindow(graphics, configX(), configY());
        }
    }

    /** 左缘页签：外框 + 内面 + 高光暗边，四角缺一像素的圆角按键样（与炎炉同款）。 */
    private void drawTab(GuiGraphics graphics, int x, int y) {
        int tx = x - TAB_W - 4, ty = y + TAB_Y;
        roundedFill(graphics, tx - 1, ty - 1, tx + TAB_W + 1, ty + TAB_H + 1, COL_EDGE);
        roundedFill(graphics, tx, ty, tx + TAB_W, ty + TAB_H, COL_PANEL);
        graphics.fill(tx + 1, ty, tx + TAB_W - 1, ty + 1, COL_HI);
        graphics.fill(tx, ty + 1, tx + 1, ty + TAB_H - 1, COL_HI);
        graphics.fill(tx + 1, ty + TAB_H - 1, tx + TAB_W - 1, ty + TAB_H, COL_LO);
        graphics.fill(tx + TAB_W - 1, ty + 1, tx + TAB_W, ty + TAB_H - 1, COL_LO);
        // 极简图标：三条滑杆 + 三粒错开滑钮（「传输配置」的通用意象，深灰单色）
        graphics.fill(tx + 3, ty + 4, tx + 12, ty + 5, COL_TITLE);
        graphics.fill(tx + 3, ty + 8, tx + 12, ty + 9, COL_TITLE);
        graphics.fill(tx + 3, ty + 12, tx + 12, ty + 13, COL_TITLE);
        graphics.fill(tx + 5, ty + 3, tx + 7, ty + 6, COL_TITLE);
        graphics.fill(tx + 9, ty + 7, tx + 11, ty + 10, COL_TITLE);
        graphics.fill(tx + 4, ty + 11, tx + 6, ty + 14, COL_TITLE);
    }

    /** 圆角 1 像素的实心矩形：四角各缺一格。 */
    private static void roundedFill(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        graphics.fill(x0 + 1, y0, x1 - 1, y1, color);
        graphics.fill(x0, y0 + 1, x1, y1 - 1, color);
    }

    /** 配置板左上角（默认贴主面板左侧；屏幕太窄时挪到右侧）。 */
    private int configX() {
        int left = this.leftPos - WIN_W - 22;
        return left >= 2 ? left : this.leftPos + this.imageWidth + 22;
    }

    private int configY() {
        return this.topPos + 12;
    }

    private void drawConfigWindow(GuiGraphics graphics, int wx, int wy) {
        // 底板：外近黑描边 + 内浅灰蓝面 + 上左高光 / 下右暗边
        roundedFill(graphics, wx - 1, wy - 1, wx + WIN_W + 1, wy + WIN_H + 1, COL_EDGE);
        roundedFill(graphics, wx, wy, wx + WIN_W, wy + WIN_H, COL_PANEL);
        graphics.fill(wx + 1, wy, wx + WIN_W - 1, wy + 1, COL_HI);
        graphics.fill(wx, wy + 1, wx + 1, wy + WIN_H - 1, COL_HI);
        graphics.fill(wx + 1, wy + WIN_H - 1, wx + WIN_W - 1, wy + WIN_H, COL_LO);
        graphics.fill(wx + WIN_W - 1, wy + 1, wx + WIN_W, wy + WIN_H - 1, COL_LO);

        // 标题
        Component title = Component.translatable("skydeitymysticpivot.gui.config");
        graphics.drawString(this.font, title, wx + (WIN_W - this.font.width(title)) / 2, wy + 5,
                COL_TITLE, false);

        // 六个面按钮
        for (int face = 0; face < 6; face++) {
            drawSideButton(graphics, wx, wy, face);
        }

        // 底部色例
        int ly = wy + LEGEND_Y;
        drawLegend(graphics, wx + 7, ly, COL_INPUT, "skydeitymysticpivot.gui.input");
        drawLegend(graphics, wx + 45, ly, COL_OUTPUT, "skydeitymysticpivot.gui.output");
        drawLegend(graphics, wx + 7, ly + LEGEND_ROW, COL_EJECT, "skydeitymysticpivot.gui.eject");
    }

    /** 一格色例：色块 + 说明文字。 */
    private void drawLegend(GuiGraphics graphics, int lx, int ly, int color, String key) {
        graphics.fill(lx, ly + 1, lx + 6, ly + 7, color);
        graphics.drawString(this.font, Component.translatable(key), lx + 8, ly, COL_TITLE, false);
    }

    /** 一个面按钮：粗描边 + 模式底色 + 底部深色阴影 + 该面外侧的方块图标。 */
    private void drawSideButton(GuiGraphics graphics, int wx, int wy, int face) {
        int bx = wx + GRID_X + CELL_POS[face][1] * (CELL + GAP);
        int by = wy + GRID_Y + CELL_POS[face][0] * (CELL + GAP);
        int mode = this.menu.getSideMode(face);
        graphics.fill(bx, by, bx + CELL, by + CELL, COL_EDGE);
        graphics.fill(bx + 1, by + 1, bx + CELL - 1, by + CELL - 1, modeColor(mode));
        graphics.fill(bx + 1, by + CELL - 4, bx + CELL - 1, by + CELL - 1, modeDark(mode));

        Block block = BuiltInRegistries.BLOCK.byId(this.menu.getNeighbourBlockId(face));
        if (block != null) {
            ItemStack neighbour = new ItemStack(block);
            if (!neighbour.isEmpty()) {
                graphics.pose().pushPose();
                graphics.pose().translate(bx + 1.0F, by + 1.0F, 1.0F);
                graphics.pose().scale(1.125F, 1.125F, 1.0F);
                graphics.renderItem(neighbour, 0, 0);
                graphics.pose().popPose();
            }
        }
    }

    private static int modeColor(int mode) {
        return mode == XuanshuReplicatorBlockEntity.MODE_INPUT ? COL_INPUT
                : mode == XuanshuReplicatorBlockEntity.MODE_OUTPUT ? COL_OUTPUT
                : mode == XuanshuReplicatorBlockEntity.MODE_IO ? COL_EJECT
                : COL_OFF;
    }

    private static int modeDark(int mode) {
        return mode == XuanshuReplicatorBlockEntity.MODE_INPUT ? COL_INPUT_DK
                : mode == XuanshuReplicatorBlockEntity.MODE_OUTPUT ? COL_OUTPUT_DK
                : mode == XuanshuReplicatorBlockEntity.MODE_IO ? COL_EJECT_DK
                : COL_OFF_DK;
    }

    // ------------------------------------------------------------------
    // 交互
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // ★ 1.21 必须显式补一次 renderTooltip，否则鼠标移到物品上什么都不显示
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (overTab(mouseX, mouseY)) {
                configOpen = !configOpen;
                clickSound();
                return true;
            }
            if (configOpen) {
                for (int face = 0; face < 6; face++) {
                    if (overCell(face, mouseX, mouseY)) {
                        sendButton(face);
                        clickSound();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    /** 只发意图，真正的状态由服务端改（与炎炉 / Mekanism 同款）。 */
    private void sendButton(int id) {
        if (this.minecraft != null && this.minecraft.gameMode != null) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    private void clickSound() {
        if (this.minecraft != null && this.minecraft.getSoundManager() != null) {
            this.minecraft.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance
                            .forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F));
        }
    }

    private boolean overTab(double mouseX, double mouseY) {
        int x = this.leftPos - TAB_W - 4, y = this.topPos + TAB_Y;
        return mouseX >= x - 1 && mouseX < x + TAB_W + 1 && mouseY >= y - 1 && mouseY < y + TAB_H + 1;
    }

    private boolean overCell(int face, double mouseX, double mouseY) {
        int wx = configX(), wy = configY();
        int x = wx + GRID_X + CELL_POS[face][1] * (CELL + GAP);
        int y = wy + GRID_Y + CELL_POS[face][0] * (CELL + GAP);
        return mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
    }
}
