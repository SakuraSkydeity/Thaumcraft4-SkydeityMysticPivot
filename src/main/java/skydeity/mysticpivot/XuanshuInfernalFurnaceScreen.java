package skydeity.mysticpivot;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

/**
 * 玄枢炎炉的界面。
 *
 * <h2>主面板（整幅自绘）</h2>
 * <p>骨架与玄枢坩埚同一套：底板、格框、进度条都用 {@link GuiGraphics#fill} 画，
 * 配色走 AE2 1.21.1 那套（浅灰蓝面 + 冷白高光 + 灰影，见 {@link XuanshuGui}）。内容是：</p>
 * <ul>
 *   <li><b>竖排三行</b>熔炼位，每行左格 = 原料、右格 = 产物、中间一条蓝色进度条
 *       ——<b>三条进度条各走各的</b>（一份燃料点起来，三个格子同时烧）；</li>
 *   <li>三行之下居中一只<b>外置燃料槽</b>，火苗贴在它左侧（用的还是原版图集精灵
 *       {@code container/furnace/lit_progress}，自下而上点亮）。</li>
 * </ul>
 * <p>★ <b>玩家背包的 27+9 格必须自己逐格画格框</b>：面板既然是自绘的，原版那张底图
 * （格框就画在底图上）就不会出现，漏画的表现是「打开物品栏一片空白」。</p>
 *
 * <h2>「传输配置」面板（AE2 式）</h2>
 * <ul>
 *   <li>主面板左缘一个小页签（<b>带外框的按键样</b>），点击展开 / 收起配置板；</li>
 *   <li>配置板<b>四角圆润</b>、整体紧凑；顶部居中标题「传输配置」；</li>
 *   <li>标题下方是 AE2 内凹面、绿 / 灰字标的「精确输入（开 / 关）」开关；</li>
 *   <li>3×3 十字的六个面按钮（上=顶、下=底、左=西、右=东、左下=南、中心=北）；
 *       <b>底色即模式</b>：灰 = 关闭、蓝 = 自动输入、橙 = 自动输出、紫 = 输入输出；
 *       <b>每格还画出该面外侧挨着的那个方块</b>，一眼看得出哪一面朝哪儿；</li>
 *   <li>底部「输入 / 输出 / 输入输出」三格色例。</li>
 * </ul>
 * <p>悬停提示一律去掉：面板上的信息已经全写在格子与色例里了。</p>
 */
public class XuanshuInfernalFurnaceScreen extends AbstractContainerScreen<XuanshuInfernalFurnaceMenu> {

    /** 原版「火苗」图集精灵（自下而上点亮，用来标燃料在烧）。 */
    private static final ResourceLocation LIT_SPRITE =
            ResourceLocation.withDefaultNamespace("container/furnace/lit_progress");

    /** 左缘展开页签（相对面板左上角）。 */
    private static final int TAB_W = 14, TAB_H = 16, TAB_Y = 62;

    /** 配置板尺寸与内部布局（整体收小后的紧凑版）。 */
    private static final int WIN_W = 84, WIN_H = 130;
    private static final int CELL = 20, GAP = 3;
    private static final int GRID_W = CELL * 3 + GAP * 2;
    private static final int GRID_X = (WIN_W - GRID_W) / 2;
    private static final int GRID_Y = 35;
    private static final int BTN_X = 6, BTN_Y = 17, BTN_W = WIN_W - 12, BTN_H = 14;
    /** 图例：两行（输入 / 输出 一行，输入输出 一行）。 */
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
     * 进度箭头（AE2 压印器那支粗箭头；相对面板，三行各一支，横在行内）。
     *
     * <p>进料槽占 20~38，箭头从 44 起、宽 26 到 70，出料槽在 74。
     * 行高 22（{@code LANE_STEP}），箭头 14 高、居中在行内。</p>
     */
    private static final int BAR_DX = 24, BAR_DY = 4, BAR_W = 26, BAR_H = 14;
    /**
     * 火苗在<b>燃料槽左侧</b>（竖排之后槽的上方已排满三行，没地方横搁火苗了）。
     *
     * <p>燃料槽 88~106、中心 97；火苗 14 高搁在 90~104，两者中线对齐。</p>
     */
    private static final int FLAME_W = 14, FLAME_H = 14;
    private static final int FLAME_X = XuanshuInfernalFurnaceMenu.FUEL_X - 20;
    private static final int FLAME_Y = XuanshuInfernalFurnaceMenu.FUEL_Y + 2;

    private boolean configOpen;

    public XuanshuInfernalFurnaceScreen(XuanshuInfernalFurnaceMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        // 竖排三行（20~82）+ 燃料槽一行（88~106）+ 背包
        this.imageHeight = 206;
    }

    @Override
    protected void init() {
        super.init();
        // 与原版 AbstractFurnaceScreen.init() 同款：标题居中
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
        this.inventoryLabelY = 110;
    }

    // ------------------------------------------------------------------
    // 绘制
    // ------------------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos, y = this.topPos;
        drawPanel(graphics, x, y, this.imageWidth, this.imageHeight);

        // ★ 面板是自绘的，原版那张底图（含每一格的格框）不会出现——
        //   所以玩家背包的 27+9 格必须自己逐格画出来，否则「打开物品栏是一片空白」。
        //   本机的七个格子也在 menu.slots 里（坐标就落在三组 INPUT/OUTPUT 与燃料槽上）。
        for (Slot slot : this.menu.slots) {
            drawSlot(graphics, x + slot.x - 1, y + slot.y - 1);
        }

        // 火苗：原版图集精灵，自下而上点亮
        if (this.menu.isLit()) {
            int lit = Mth.ceil(this.menu.getLitProgress() * (FLAME_H - 1)) + 1;
            graphics.blitSprite(LIT_SPRITE, FLAME_W, FLAME_H, 0, FLAME_H - lit,
                    x + FLAME_X, y + FLAME_Y + FLAME_H - lit, FLAME_W, lit);
        }

        for (int lane = 0; lane < XuanshuInfernalFurnaceBlockEntity.LANES; lane++) {
            drawProgress(graphics, x, y, lane);
        }

        drawTab(graphics, x, y);
        if (configOpen) {
            drawConfigWindow(graphics, configX(), configY());
        }
    }

    /** 一块 AE2 风的面板（画法在 {@link XuanshuGui#panel}）。 */
    private static void drawPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        XuanshuGui.panel(graphics, x, y, w, h);
    }

    /** 一格 AE2 内凹槽位（画法在 {@link XuanshuGui#slot}）。 */
    private static void drawSlot(GuiGraphics graphics, int sx, int sy) {
        XuanshuGui.slot(graphics, sx, sy);
    }

    /** 第 {@code lane} 行中间那支 AE2 箭头进度（画法见 {@link XuanshuGui#progressArrow}）。 */
    private void drawProgress(GuiGraphics graphics, int x, int y, int lane) {
        int bx = x + XuanshuInfernalFurnaceMenu.INPUT_X + BAR_DX;
        int by = y + XuanshuInfernalFurnaceMenu.laneY(lane) + BAR_DY;
        XuanshuGui.progressArrow(graphics, bx, by, BAR_W, BAR_H, this.menu.getBurnProgress(lane));
    }

    /**
     * 左缘的展开页签：<b>外层一圈色框 + 内面浅灰 + 上左高光 / 下右暗边</b>，
     * 四角各缺一像素做出圆角——看起来就是一枚可按的按键。
     */
    private void drawTab(GuiGraphics graphics, int x, int y) {
        int tx = x - TAB_W - 4, ty = y + TAB_Y;
        // 色框（比内面各外扩 1 像素，圆角）
        roundedFill(graphics, tx - 1, ty - 1, tx + TAB_W + 1, ty + TAB_H + 1, COL_TAB_FRAME);
        // 内面
        roundedFill(graphics, tx, ty, tx + TAB_W, ty + TAB_H, COL_PANEL);
        // 上 / 左高光
        graphics.fill(tx + 1, ty, tx + TAB_W - 1, ty + 1, COL_HI);
        graphics.fill(tx, ty + 1, tx + 1, ty + TAB_H - 1, COL_HI);
        // 下 / 右暗边
        graphics.fill(tx + 1, ty + TAB_H - 1, tx + TAB_W - 1, ty + TAB_H, COL_LO);
        graphics.fill(tx + TAB_W - 1, ty + 1, tx + TAB_W, ty + TAB_H - 1, COL_LO);
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
        // 底板：外近黑描边（圆角）+ 内浅灰蓝面，上左高光、下右灰影（AE2 面板的立体感）
        roundedFill(graphics, wx - 1, wy - 1, wx + WIN_W + 1, wy + WIN_H + 1, COL_TAB_FRAME);
        roundedFill(graphics, wx, wy, wx + WIN_W, wy + WIN_H, COL_PANEL);
        graphics.fill(wx + 1, wy, wx + WIN_W - 1, wy + 1, COL_HI);
        graphics.fill(wx, wy + 1, wx + 1, wy + WIN_H - 1, COL_HI);
        graphics.fill(wx + 1, wy + WIN_H - 1, wx + WIN_W - 1, wy + WIN_H, COL_LO);
        graphics.fill(wx + WIN_W - 1, wy + 1, wx + WIN_W, wy + WIN_H - 1, COL_LO);

        // 标题
        Component title = XuanshuInfernalFurnaceMenu.configTitle();
        graphics.drawString(this.font, title, wx + (WIN_W - this.font.width(title)) / 2, wy + 5,
                COL_TITLE, false);

        // 精确输入开关（AE2 内凹面，绿 / 灰字标开与关）
        graphics.fill(wx + BTN_X, wy + BTN_Y, wx + BTN_X + BTN_W, wy + BTN_Y + BTN_H, COL_SWITCH_BG);
        XuanshuGui.ring(graphics, wx + BTN_X, wy + BTN_Y, BTN_W, BTN_H, COL_TAB_FRAME);
        Component strict = strictLabel();
        graphics.drawString(this.font, strict, wx + BTN_X + (BTN_W - this.font.width(strict)) / 2,
                wy + BTN_Y + 3, this.menu.hasStrictInput() ? COL_SWITCH_ON : COL_SWITCH_OFF, false);

        // 六个面按钮
        for (int face = 0; face < 6; face++) {
            drawSideButton(graphics, wx, wy, face);
        }

        // 底部色例：第一行 输入 / 输出，第二行 输入输出
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

    /**
     * 一个面按钮：粗黑描边 + 模式底色 + 底部一条更深的同色阴影；
     * 格内再画出<b>该面外侧挨着的那个方块</b>（空着就不画），一眼看得出这一面朝哪儿。
     */
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

    /** 某个面外侧挨着的方块（做成物品堆叠；空气或取不到就是空的）。 */
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
        return mode == XuanshuInfernalFurnaceBlockEntity.MODE_INPUT ? COL_INPUT
                : mode == XuanshuInfernalFurnaceBlockEntity.MODE_OUTPUT ? COL_OUTPUT
                : mode == XuanshuInfernalFurnaceBlockEntity.MODE_IO ? COL_EJECT
                : COL_OFF;
    }

    private static int modeDark(int mode) {
        return mode == XuanshuInfernalFurnaceBlockEntity.MODE_INPUT ? COL_INPUT_DK
                : mode == XuanshuInfernalFurnaceBlockEntity.MODE_OUTPUT ? COL_OUTPUT_DK
                : mode == XuanshuInfernalFurnaceBlockEntity.MODE_IO ? COL_EJECT_DK
                : COL_OFF_DK;
    }

    // ------------------------------------------------------------------
    // 交互
    // ------------------------------------------------------------------

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // ★ 1.21 的 AbstractContainerScreen#render 不再自动调 renderTooltip，
        //   必须在这里显式补一次，否则「鼠标移到物品上什么都不显示」。
        //   （本机面板上的六面配置仍不挂悬停提示，信息都画在格子里了。）
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
                if (overStrictButton(mouseX, mouseY)) {
                    sendButton(XuanshuInfernalFurnaceMenu.BUTTON_STRICT_INPUT);
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

    /** 只发意图，真正的状态由服务端改（与 Mekanism 同款）。 */
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
