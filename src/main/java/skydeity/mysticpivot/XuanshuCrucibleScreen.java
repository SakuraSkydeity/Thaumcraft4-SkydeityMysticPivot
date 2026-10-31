package skydeity.mysticpivot;

import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectDefinition;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * 玄枢坩埚的界面。
 *
 * <h2>主面板（整幅自绘）</h2>
 * <p>没有单独的贴图：底板、格框、进度条、要素行都用 {@link GuiGraphics#fill} 画，
 * 配色走 AE2 1.21.1 那套（浅灰蓝面 + 冷白高光 + 灰影，见 {@link XuanshuGui}）。</p>
 *
 * <p>版面上<b>竖排三行</b>，一行就是一个工作面（玄枢坩埚可以三种配方同烧）：</p>
 * <ul>
 *   <li>每行：左格 = 触媒，中段一条蓝色进度条，再往右＝该行的产物格，
 *       <b>产物格右边</b>才是要素行 —— <b>三条进度条各走各的</b>；</li>
 *   <li>每行的<b>要素需求</b>就摆在同行产物格的右侧：把这一炉每样要素的图标单行排开
 *       （最多 5 枚，步进 16），右下角写<b>还差几份</b>，够份的压一层绿罩（图标即本体要素图，
 *       用的是本体的 {@code thaumcraft:textures/aspects/*.png}）。
 *       ★ <b>必须在产物格右边</b>：摆在产物格上会把槽里的东西整个盖住（第 35 批修的）。
 *       这些读的是<b>服务端同步下来的快照</b>——要素是机器吸进来的，只有服务端说了算。</li>
 * </ul>
 * <p>★ <b>玩家背包的 27+9 格必须自己逐格画格框</b>：面板既然是自绘的，原版那张底图
 * （格框就画在底图上）就不会出现，漏画的表现是「打开物品栏一片空白」。</p>
 * <p>整块面板上没有任何说明文字、也不挂悬停提示。</p>
 *
 * <h2>「传输配置」面板（与玄枢炎炉同款）</h2>
 * <p>左缘一枚按键样的页签展开；板上有「精确输入」开关、3×3 十字的六个面按钮
 * （底色即模式，格内画出该面外侧挨着的方块），底部两行色例。
 * 这一套只管<b>物品</b>的进出；要素按 5×5×5 自己找上门，与它无关。</p>
 */
public class XuanshuCrucibleScreen extends AbstractContainerScreen<XuanshuCrucibleMenu> {

    /** 要素图标的底纹（本体自带的一圈要素框）。 */
    private static final ResourceLocation ASPECT_BACK =
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/aspects/_back.png");

    /** 左缘展开页签（相对面板左上角）。 */
    private static final int TAB_W = 14, TAB_H = 16, TAB_Y = 62;

    /** 配置板尺寸与内部布局（与炎炉同一套紧凑尺寸）。 */
    private static final int WIN_W = 84, WIN_H = 130;
    private static final int CELL = 20, GAP = 3;
    private static final int GRID_W = CELL * 3 + GAP * 2;
    private static final int GRID_X = (WIN_W - GRID_W) / 2;
    private static final int GRID_Y = 35;
    private static final int BTN_X = 6, BTN_Y = 17, BTN_W = WIN_W - 12, BTN_H = 14;
    private static final int LEGEND_Y = 105, LEGEND_ROW = 11;

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

    /** 主面板骨架（AE2 调色板，画法见 {@link XuanshuGui#panel}）。 */
    private static final int COL_PANEL = XuanshuGui.PANEL_BODY;
    private static final int COL_HI = XuanshuGui.PANEL_HI;
    private static final int COL_LO = XuanshuGui.PANEL_LO;
    // 槽位与进度条的画法移到 XuanshuGui（三台机器共用），这里不再留本地色值。
    /** 页签 / 配置板的描边：AE2 近黑。 */
    private static final int COL_TAB_FRAME = XuanshuGui.PANEL_EDGE;

    private static final int COL_TITLE = 0xFF3F3F3F;
    private static final int COL_SWITCH_BG = XuanshuGui.SLOT_FILL;
    private static final int COL_SWITCH_ON = 0xFF1E6B2E;
    private static final int COL_SWITCH_OFF = 0xFF6E6E6E;
    private static final int COL_CELL_EDGE = XuanshuGui.PANEL_EDGE;
    private static final int COL_OFF = 0xFF8B8B8B, COL_OFF_DK = 0xFF6E6E6E;
    private static final int COL_INPUT = 0xFF3B6FD4, COL_INPUT_DK = 0xFF27498F;
    private static final int COL_OUTPUT = 0xFFE07A1F, COL_OUTPUT_DK = 0xFF9A4E10;
    private static final int COL_EJECT = 0xFF915DCD, COL_EJECT_DK = 0xFF5B2E96;

    /**
     * 进度箭头（AE2 压印器那支粗箭头）的坐标（相对面板；三行各一支，<b>横在行内</b>）。
     *
     * <p>进料槽占 20~38，箭头从 44 起、宽 26 到 70，右侧区从 74 起接要素行。
     * 行高 22（{@code LANE_STEP}），箭头 14 高、居中在行内。</p>
     */
    private static final int BAR_DX = 24, BAR_DY = 4, BAR_W = 26, BAR_H = 14;

    /**
     * 要素区（相对面板；每个工作面一块）。
     *
     * <p>竖排之后一行就是整块 176 宽。要素行<b>摆在出料槽的右边</b>
     * （见 {@link XuanshuCrucibleMenu#ASPECT_X}）：从 94 起、图标 16、步进 16 ⇒
     * 5 枚正好到 94 + 4×16 + 16 = 174，贴着右缘留出那条暗边。</p>
     *
     * <p>★ 步子从 17 收到 16、上限从 6 收到 5，都是为「挪到出料槽右侧」腾地方：
     * 右边只剩 80 像素，塞不下第 6 枚；而全部坩埚配方里最多也就 5 样要素。</p>
     */
    private static final int ASPECT_STEP = 16, ASPECT_MAX = 5;

    /** 「已备齐」的绿罩：图标上叠一层，一眼看得出这样要素够了。 */
    private static final int COL_READY = 0x6600E060;

    private boolean configOpen;

    public XuanshuCrucibleScreen(XuanshuCrucibleMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        // 竖排三行（20~82）之后直接接背包（标题 88）
        this.imageHeight = 184;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
        this.inventoryLabelY = 88;
    }

    // ------------------------------------------------------------------
    // 绘制
    // ------------------------------------------------------------------

    /**
     * ★ <b>必须自己把悬停提示画出来</b>：1.21 的 {@code AbstractContainerScreen#render}
     * 已经<b>不再自动调用</b> {@code renderTooltip}（原版熔炉那类界面都在自己的
     * {@code render} 里显式调一次）。漏了这一步的表现就是「鼠标移到物品上什么都不显示」，
     * 而槽位本身、点击、拖拽全都正常——因为那些都不经过 {@code renderTooltip}。
     */
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos, y = this.topPos;
        drawPanel(graphics, x, y, this.imageWidth, this.imageHeight);

        // ★ 面板是自绘的，原版那张底图（含每一格的格框）不会出现——
        //   所以玩家背包的 27+9 格必须自己逐格画出来，否则「打开物品栏是一片空白」。
        //   本机的六个格子也在 menu.slots 里（坐标就落在三组 INPUT/OUTPUT 上），一并画到。
        for (Slot slot : this.menu.slots) {
            drawSlot(graphics, x + slot.x - 1, y + slot.y - 1);
        }

        for (int lane = 0; lane < XuanshuCrucibleBlockEntity.LANES; lane++) {
            drawProgress(graphics, x, y, lane);
            drawAspects(graphics, x, y, lane);
        }

        drawTab(graphics, x, y);
        if (configOpen) {
            drawConfigWindow(graphics, configX(), configY());
        }
    }

    // ★ 第七十七批清理：这里原本有一个 {@code renderLabels} 覆写，方法体只有一行
    //   {@code super.renderLabels(graphics, mouseX, mouseY);} —— 与不覆写完全等价，纯属残留。
    //   标题与「物品栏」两行文字照常由父类默认绘制（{@code init()} 里设的
    //   {@code titleLabelX} / {@code inventoryLabelY} 就是在给它们定位）。

    /** 一块 AE2 风的面板（画法在 {@link XuanshuGui#panel}）。 */
    private static void drawPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        XuanshuGui.panel(graphics, x, y, w, h);
    }

    /** 一格 AE2 内凹槽位（画法在 {@link XuanshuGui#slot}）。 */
    private static void drawSlot(GuiGraphics graphics, int sx, int sy) {
        XuanshuGui.slot(graphics, sx, sy);
    }

    /** 第 {@code lane} 行中间那支 AE2 箭头进度：炼满一格出一炉（画法见 {@link XuanshuGui#progressArrow}）。 */
    private void drawProgress(GuiGraphics graphics, int x, int y, int lane) {
        int bx = x + XuanshuCrucibleMenu.INPUT_X + BAR_DX;
        int by = y + XuanshuCrucibleMenu.laneY(lane) + BAR_DY;
        XuanshuGui.progressArrow(graphics, bx, by, BAR_W, BAR_H, this.menu.getProgressRatio(lane));
    }

    /**
     * 第 {@code lane} 组的要素需求：这一炉每样要素一张图，右下角写<b>还差几份</b>。
     *
     * <p>读的是客户端那份方块实体同步下来的快照（服务端每 tick 算、变了才推）——
     * 要素是机器「吸」进来的，只有服务端说了算；界面上这几行于是恒与锅里一致。
     * 已经够份的那几样压一层绿罩、不再写数字。</p>
     */
    private void drawAspects(GuiGraphics graphics, int x, int y, int lane) {
        XuanshuCrucibleBlockEntity crucible = clientCrucible();
        if (crucible == null) {
            return;
        }
        Map<AspectId, Integer> needed = crucible.neededAspects(lane);
        if (needed.isEmpty()) {
            return;
        }
        int ax = x + XuanshuCrucibleMenu.ASPECT_X;
        int iy = y + XuanshuCrucibleMenu.laneY(lane) + 1;
        int shown = Math.min(needed.size(), ASPECT_MAX);
        int index = 0;
        for (Map.Entry<AspectId, Integer> entry : needed.entrySet()) {
            if (index >= shown) {
                break;
            }
            int ix = ax + index * ASPECT_STEP;
            graphics.blit(ASPECT_BACK, ix, iy, 16, 16, 0.0F, 0.0F, 16, 16, 16, 16);
            AspectDefinition definition = AspectApi.get(entry.getKey());
            if (definition != null) {
                graphics.blit(definition.texture(), ix, iy, 16, 16, 0.0F, 0.0F, 16, 16, 16, 16);
            }
            int missing = Math.max(0, entry.getValue() - crucible.storedOf(lane, entry.getKey()));
            if (missing > 0) {
                String amount = Integer.toString(missing);
                graphics.drawString(this.font, amount, ix + 16 - this.font.width(amount),
                        iy + 10, 0xFFFFFFFF, true);
            } else {
                graphics.fill(ix, iy, ix + 16, iy + 16, COL_READY);
            }
            index++;
        }
    }

    /** 客户端那份玄枢坩埚（位置由数据槽下发）；查不到就给 {@code null}。 */
    @Nullable
    private XuanshuCrucibleBlockEntity clientCrucible() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return null;
        }
        BlockPos pos = this.menu.getBlockPos();
        if (pos == null) {
            return null;
        }
        return this.minecraft.level.getBlockEntity(pos) instanceof XuanshuCrucibleBlockEntity crucible
                ? crucible
                : null;
    }

    /**
     * 左缘的展开页签：<b>外层一圈色框 + 内面浅灰 + 上左高光 / 下右暗边</b>，
     * 四角各缺一像素做圆角——一枚可按的按键。
     */
    private void drawTab(GuiGraphics graphics, int x, int y) {
        int tx = x - TAB_W - 4, ty = y + TAB_Y;
        roundedFill(graphics, tx - 1, ty - 1, tx + TAB_W + 1, ty + TAB_H + 1, COL_TAB_FRAME);
        roundedFill(graphics, tx, ty, tx + TAB_W, ty + TAB_H, COL_PANEL);
        graphics.fill(tx + 1, ty, tx + TAB_W - 1, ty + 1, COL_HI);
        graphics.fill(tx, ty + 1, tx + 1, ty + TAB_H - 1, COL_HI);
        graphics.fill(tx + 1, ty + TAB_H - 1, tx + TAB_W - 1, ty + TAB_H, COL_LO);
        graphics.fill(tx + TAB_W - 1, ty + 1, tx + TAB_W, ty + TAB_H - 1, COL_LO);
        // 第二百一十五批：页签上的「传输配置」图标（三条滑杆）补回来
        XuanshuGui.tabIcon(graphics, tx, ty, COL_TITLE);
    }

    /** 圆角 1 像素的实心矩形：四角各缺一格。 */
    private static void roundedFill(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        graphics.fill(x0 + 1, y0, x1 - 1, y1, color);
        graphics.fill(x0, y0 + 1, x1, y1 - 1, color);
    }

    private int configX() {
        int left = this.leftPos - WIN_W - 22;
        return left >= 2 ? left : this.leftPos + this.imageWidth + 22;
    }

    private int configY() {
        return this.topPos + 12;
    }

    private void drawConfigWindow(GuiGraphics graphics, int wx, int wy) {
        roundedFill(graphics, wx - 1, wy - 1, wx + WIN_W + 1, wy + WIN_H + 1, COL_TAB_FRAME);
        roundedFill(graphics, wx, wy, wx + WIN_W, wy + WIN_H, COL_PANEL);
        graphics.fill(wx + 1, wy, wx + WIN_W - 1, wy + 1, COL_HI);
        graphics.fill(wx, wy + 1, wx + 1, wy + WIN_H - 1, COL_HI);
        graphics.fill(wx + 1, wy + WIN_H - 1, wx + WIN_W - 1, wy + WIN_H, COL_LO);
        graphics.fill(wx + WIN_W - 1, wy + 1, wx + WIN_W, wy + WIN_H - 1, COL_LO);

        Component title = XuanshuCrucibleMenu.configTitle();
        graphics.drawString(this.font, title, wx + (WIN_W - this.font.width(title)) / 2, wy + 5,
                COL_TITLE, false);

        graphics.fill(wx + BTN_X, wy + BTN_Y, wx + BTN_X + BTN_W, wy + BTN_Y + BTN_H, COL_SWITCH_BG);
        XuanshuGui.ring(graphics, wx + BTN_X, wy + BTN_Y, BTN_W, BTN_H, COL_TAB_FRAME);
        Component strict = strictLabel();
        graphics.drawString(this.font, strict, wx + BTN_X + (BTN_W - this.font.width(strict)) / 2,
                wy + BTN_Y + 3, this.menu.hasStrictInput() ? COL_SWITCH_ON : COL_SWITCH_OFF, false);

        for (int face = 0; face < 6; face++) {
            drawSideButton(graphics, wx, wy, face);
        }

        int ly = wy + LEGEND_Y;
        drawLegend(graphics, wx + 7, ly, COL_INPUT, "skydeitymysticpivot.gui.input");
        drawLegend(graphics, wx + 45, ly, COL_OUTPUT, "skydeitymysticpivot.gui.output");
        drawLegend(graphics, wx + 7, ly + LEGEND_ROW, COL_EJECT, "skydeitymysticpivot.gui.eject");
    }

    private void drawLegend(GuiGraphics graphics, int lx, int ly, int color, String key) {
        graphics.fill(lx, ly + 1, lx + 6, ly + 7, color);
        graphics.drawString(this.font, Component.translatable(key), lx + 8, ly, COL_TITLE, false);
    }

    /** 一个面按钮：粗黑描边 + 模式底色 + 底部更深的一条；格内画出该面外侧挨着的方块。 */
    private void drawSideButton(GuiGraphics graphics, int wx, int wy, int face) {
        int bx = wx + GRID_X + CELL_POS[face][1] * (CELL + GAP);
        int by = wy + GRID_Y + CELL_POS[face][0] * (CELL + GAP);
        int mode = this.menu.getSideMode(face);
        graphics.fill(bx, by, bx + CELL, by + CELL, COL_CELL_EDGE);
        graphics.fill(bx + 1, by + 1, bx + CELL - 1, by + CELL - 1, modeColor(mode));
        graphics.fill(bx + 1, by + CELL - 4, bx + CELL - 1, by + CELL - 1, modeDark(mode));

        ItemStack neighbour = neighbourStack(face);
        if (!neighbour.isEmpty()) {
            graphics.pose().pushPose();
            graphics.pose().translate(bx + 1.0F, by + 1.0F, 1.0F);
            graphics.pose().scale(1.125F, 1.125F, 1.0F);
            graphics.renderItem(neighbour, 0, 0);
            graphics.pose().popPose();
        }
    }

    private ItemStack neighbourStack(int face) {
        Block block = BuiltInRegistries.BLOCK.byId(this.menu.getNeighbourBlockId(face));
        return block == null ? ItemStack.EMPTY : new ItemStack(block);
    }

    private Component strictLabel() {
        return Component.translatable("skydeitymysticpivot.gui.strict_input",
                Component.translatable(this.menu.hasStrictInput()
                        ? "skydeitymysticpivot.gui.on"
                        : "skydeitymysticpivot.gui.off"));
    }

    private static int modeColor(int mode) {
        return mode == XuanshuCrucibleBlockEntity.MODE_INPUT ? COL_INPUT
                : mode == XuanshuCrucibleBlockEntity.MODE_OUTPUT ? COL_OUTPUT
                : mode == XuanshuCrucibleBlockEntity.MODE_IO ? COL_EJECT
                : COL_OFF;
    }

    private static int modeDark(int mode) {
        return mode == XuanshuCrucibleBlockEntity.MODE_INPUT ? COL_INPUT_DK
                : mode == XuanshuCrucibleBlockEntity.MODE_OUTPUT ? COL_OUTPUT_DK
                : mode == XuanshuCrucibleBlockEntity.MODE_IO ? COL_EJECT_DK
                : COL_OFF_DK;
    }

    // ------------------------------------------------------------------
    // 交互
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (overTab(mouseX, mouseY)) {
                configOpen = !configOpen;
                clickSound();
                return true;
            }
            if (configOpen) {
                if (overStrictButton(mouseX, mouseY)) {
                    sendButton(XuanshuCrucibleMenu.BUTTON_STRICT_INPUT);
                    clickSound();
                    return true;
                }
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

    private boolean overStrictButton(double mouseX, double mouseY) {
        int wx = configX(), wy = configY();
        return mouseX >= wx + BTN_X && mouseX < wx + BTN_X + BTN_W
                && mouseY >= wy + BTN_Y && mouseY < wy + BTN_Y + BTN_H;
    }

    private boolean overCell(int face, double mouseX, double mouseY) {
        int wx = configX(), wy = configY();
        int x = wx + GRID_X + CELL_POS[face][1] * (CELL + GAP);
        int y = wy + GRID_Y + CELL_POS[face][0] * (CELL + GAP);
        return mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
    }
}
