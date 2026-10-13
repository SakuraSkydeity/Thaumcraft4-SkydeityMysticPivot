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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import javax.annotation.Nullable;
import java.util.Map;

/**
 * 玄枢炼金炉的界面。
 *
 * <h2>主面板（整幅自绘）</h2>
 * <p>骨架与玄枢坩埚 / 炎炉同一套：底板、格框、进度条都用 {@link GuiGraphics#fill} 画，
 * 配色走 AE2 1.21.1 那套（见 {@link XuanshuGui}）。
 * 版面上<b>竖排三行</b>烧炼位，每行 = 一个进料槽 + 一条蓝色进度条 + 右半段一行<b>这一炉出的要素</b>
 * （图标即本体要素图；熬制时显示「将产出」，推出阶段显示「还剩几份没送走」）。
 * 三行之下居中一只<b>燃料槽</b>，火苗贴在它左侧——<b>没燃料一格也烧不动</b>。</p>
 * <p>★ <b>玩家背包的 27+9 格必须自己逐格画格框</b>：面板既然是自绘的，原版那张底图
 * （格框就画在底图上）就不会出现，漏画的表现是「打开物品栏一片空白」。</p>
 *
 * <h2>「传输配置」面板</h2>
 * <p>左缘一枚按键样的页签展开；板上有 3×3 十字的六个面按钮——这台机器只有<b>两档</b>：
 * 灰 = 关闭、蓝 = 自动输入（它没有物品产出，产物是源质、走管子出去）。</p>
 */
public class XuanshuAlchemyFurnaceScreen extends AbstractContainerScreen<XuanshuAlchemyFurnaceMenu> {

    /** 原版「火苗」图集精灵（自下而上点亮，用来标燃料在烧）。 */
    private static final ResourceLocation LIT_SPRITE =
            ResourceLocation.withDefaultNamespace("container/furnace/lit_progress");

    /** 要素图标的底纹（本体自带的一圈要素框）。 */
    private static final ResourceLocation ASPECT_BACK =
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/aspects/_back.png");

    /** 左缘展开页签（相对面板左上角）。 */
    private static final int TAB_W = 14, TAB_H = 16, TAB_Y = 62;

    /** 配置板尺寸与内部布局（这台机器档数少，板子比另外两台矮一截）。 */
    private static final int WIN_W = 84, WIN_H = 118;
    private static final int CELL = 20, GAP = 3;
    private static final int GRID_W = CELL * 3 + GAP * 2;
    private static final int GRID_X = (WIN_W - GRID_W) / 2;
    private static final int GRID_Y = 33;
    private static final int LEGEND_Y = 100, LEGEND_ROW = 11;

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
    private static final int COL_CELL_EDGE = XuanshuGui.PANEL_EDGE;
    private static final int COL_OFF = 0xFF8B8B8B, COL_OFF_DK = 0xFF6E6E6E;
    private static final int COL_INPUT = 0xFF3B6FD4, COL_INPUT_DK = 0xFF27498F;

    /**
     * 进度箭头（AE2 压印器那支粗箭头；相对面板，三行各一支，横在行内）。
     *
     * <p>进料槽占 20~38，箭头从 44 起、宽 26 到 70，右侧区从 74 起接要素行。
     * 行高 22（{@code LANE_STEP}），箭头 14 高、居中在行内。</p>
     */
    private static final int BAR_DX = 24, BAR_DY = 4, BAR_W = 26, BAR_H = 14;

    /**
     * 火苗在<b>燃料槽左侧</b>（竖排之后槽的上方已排满三行）。
     *
     * <p>燃料槽 88~106、中心 97；火苗 14 高搁在 90~104，两者中线对齐。</p>
     */
    private static final int FLAME_W = 14, FLAME_H = 14;
    private static final int FLAME_X = XuanshuAlchemyFurnaceMenu.FUEL_X - 20;
    private static final int FLAME_Y = XuanshuAlchemyFurnaceMenu.FUEL_Y + 2;

    /**
     * 要素区（相对面板；每个烧炼位一块）。
     *
     * <p>竖排之后一行就是整块 176 宽，所以要素<b>单行排开、最多 6 枚</b>：
     * 从 74 起、图标 16、步进 17 ⇒ 6 枚正好到 175，贴着右缘。</p>
     */
    private static final int ASPECT_STEP = 17, ASPECT_MAX = 6;

    private boolean configOpen;

    public XuanshuAlchemyFurnaceScreen(XuanshuAlchemyFurnaceMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        // 竖排三行（20~82）+ 燃料槽一行（88~106）+ 背包
        this.imageHeight = 206;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
        this.inventoryLabelY = 110;
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
        for (Slot slot : this.menu.slots) {
            drawSlot(graphics, x + slot.x - 1, y + slot.y - 1);
        }

        // 火苗：原版图集精灵，自下而上点亮（贴在燃料槽左侧）
        if (this.menu.isLit()) {
            int lit = Mth.ceil(this.menu.getLitProgress() * (FLAME_H - 1)) + 1;
            graphics.blitSprite(LIT_SPRITE, FLAME_W, FLAME_H, 0, FLAME_H - lit,
                    x + FLAME_X, y + FLAME_Y + FLAME_H - lit, FLAME_W, lit);
        }

        for (int lane = 0; lane < XuanshuAlchemyFurnaceBlockEntity.LANES; lane++) {
            drawProgress(graphics, x, y, lane);
            drawAspects(graphics, x, y, lane);
        }

        drawTab(graphics, x, y);
        if (configOpen) {
            drawConfigWindow(graphics, configX(), configY());
        }
    }

    /** 面板上的信息全在格子与色例里，不再画任何说明文字、也不挂悬停提示。 */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
    }

    /** 一块 AE2 风的面板（画法在 {@link XuanshuGui#panel}）。 */
    private static void drawPanel(GuiGraphics graphics, int x, int y, int w, int h) {
        XuanshuGui.panel(graphics, x, y, w, h);
    }

    /** 一格 AE2 内凹槽位（画法在 {@link XuanshuGui#slot}）。 */
    private static void drawSlot(GuiGraphics graphics, int sx, int sy) {
        XuanshuGui.slot(graphics, sx, sy);
    }

    /** 第 {@code lane} 行的 AE2 箭头进度（画法见 {@link XuanshuGui#progressArrow}）。 */
    private void drawProgress(GuiGraphics graphics, int x, int y, int lane) {
        int bx = x + XuanshuAlchemyFurnaceMenu.INPUT_X + BAR_DX;
        int by = y + XuanshuAlchemyFurnaceMenu.laneY(lane) + BAR_DY;
        XuanshuGui.progressArrow(graphics, bx, by, BAR_W, BAR_H, this.menu.getProgressRatio(lane));
    }

    /**
     * 第 {@code lane} 组这一炉出的要素：每样一张图，右下角写份数。
     *
     * <p>推完之后显示的是<b>还没送走</b>的份数，所以数字会一路减到没有；熬制中还只是预告。</p>
     */
    private void drawAspects(GuiGraphics graphics, int x, int y, int lane) {
        XuanshuAlchemyFurnaceBlockEntity furnace = clientFurnace();
        if (furnace == null) {
            return;
        }
        Map<AspectId, Integer> pending = furnace.pendingAspects(lane);
        Map<AspectId, Integer> shownMap = pending.isEmpty() ? furnace.plannedAspects(lane) : pending;
        if (shownMap.isEmpty()) {
            return;
        }
        int ax = x + XuanshuAlchemyFurnaceMenu.ASPECT_X;
        int iy = y + XuanshuAlchemyFurnaceMenu.laneY(lane) + 1;
        int index = 0;
        for (Map.Entry<AspectId, Integer> entry : shownMap.entrySet()) {
            if (index >= ASPECT_MAX) {
                break;
            }
            int ix = ax + index * ASPECT_STEP;
            graphics.blit(ASPECT_BACK, ix, iy, 16, 16, 0.0F, 0.0F, 16, 16, 16, 16);
            AspectDefinition definition = AspectApi.get(entry.getKey());
            if (definition != null) {
                graphics.blit(definition.texture(), ix, iy, 16, 16, 0.0F, 0.0F, 16, 16, 16, 16);
            }
            String amount = Integer.toString(entry.getValue());
            graphics.drawString(this.font, amount, ix + 16 - this.font.width(amount),
                    iy + 10, 0xFFFFFFFF, true);
            index++;
        }
    }

    /** 客户端那份玄枢炼金炉（位置由数据槽下发）；查不到就给 {@code null}。 */
    @Nullable
    private XuanshuAlchemyFurnaceBlockEntity clientFurnace() {
        if (this.minecraft == null || this.minecraft.level == null) {
            return null;
        }
        BlockPos pos = this.menu.getBlockPos();
        if (pos == null) {
            return null;
        }
        return this.minecraft.level.getBlockEntity(pos) instanceof XuanshuAlchemyFurnaceBlockEntity furnace
                ? furnace
                : null;
    }

    /** 左缘的展开页签：外层一圈色框 + 内面浅灰 + 上左高光 / 下右暗边，四角缺像素做圆角。 */
    private void drawTab(GuiGraphics graphics, int x, int y) {
        int tx = x - TAB_W - 4, ty = y + TAB_Y;
        roundedFill(graphics, tx - 1, ty - 1, tx + TAB_W + 1, ty + TAB_H + 1, COL_TAB_FRAME);
        roundedFill(graphics, tx, ty, tx + TAB_W, ty + TAB_H, COL_PANEL);
        graphics.fill(tx + 1, ty, tx + TAB_W - 1, ty + 1, COL_HI);
        graphics.fill(tx, ty + 1, tx + 1, ty + TAB_H - 1, COL_HI);
        graphics.fill(tx + 1, ty + TAB_H - 1, tx + TAB_W - 1, ty + TAB_H, COL_LO);
        graphics.fill(tx + TAB_W - 1, ty + 1, tx + TAB_W, ty + TAB_H - 1, COL_LO);
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

        Component title = XuanshuAlchemyFurnaceMenu.configTitle();
        graphics.drawString(this.font, title, wx + (WIN_W - this.font.width(title)) / 2, wy + 5,
                COL_TITLE, false);

        for (int face = 0; face < 6; face++) {
            drawSideButton(graphics, wx, wy, face);
        }

        int ly = wy + LEGEND_Y;
        drawLegend(graphics, wx + 7, ly, COL_OFF, "skydeitymysticpivot.gui.off");
        drawLegend(graphics, wx + 45, ly, COL_INPUT, "skydeitymysticpivot.gui.input");
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

    private static int modeColor(int mode) {
        return mode == XuanshuAlchemyFurnaceBlockEntity.MODE_INPUT ? COL_INPUT : COL_OFF;
    }

    private static int modeDark(int mode) {
        return mode == XuanshuAlchemyFurnaceBlockEntity.MODE_INPUT ? COL_INPUT_DK : COL_OFF_DK;
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

    private boolean overCell(int face, double mouseX, double mouseY) {
        int wx = configX(), wy = configY();
        int x = wx + GRID_X + CELL_POS[face][1] * (CELL + GAP);
        int y = wy + GRID_Y + CELL_POS[face][0] * (CELL + GAP);
        return mouseX >= x && mouseX < x + CELL && mouseY >= y && mouseY < y + CELL;
    }
}
