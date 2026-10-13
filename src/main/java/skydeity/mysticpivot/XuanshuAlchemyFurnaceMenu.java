package skydeity.mysticpivot;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * 玄枢炼金炉的容器菜单：<b>三个进料槽</b>（每组一条进度条）+ 玩家背包。
 *
 * <p>这台机器没有物品产出槽——它把物品烧成源质，源质走管子出去（见方块实体）。</p>
 *
 * <p>「传输配置」只有两档（关闭 / 自动输入），同样走 {@link ContainerData} 自动同步，
 * 点按钮走 {@code clickMenuButton}。</p>
 */
public class XuanshuAlchemyFurnaceMenu extends AbstractContainerMenu {

    /** 玩家背包槽位的起始下标。 */
    private static final int INV_SLOT_START = XuanshuAlchemyFurnaceBlockEntity.SLOT_COUNT;
    private static final int INV_SLOT_END = INV_SLOT_START + 36;

    /**
     * 数据槽布局：
     * <ul>
     *   <li>0~5 = 六面模式（0 关闭 / 1 自动输入）；</li>
     *   <li>6~8 = 三个烧炼位的进度（tick）；</li>
     *   <li>9 = 一炉总时长；</li>
     *   <li>10~15 = 六面外侧的方块；</li>
     *   <li>16~17 = 本方块坐标（{@link BlockPos#asLong()} 的高低两半）；</li>
     *   <li>18 = 剩余燃烧；19 = 本轮燃烧总时长。</li>
     * </ul>
     */
    public static final int DATA_COUNT = 20;

    /** 数据槽里「三个烧炼位进度」的起始下标。 */
    public static final int PROGRESS_DATA_START = 6;

    /** 数据槽里「一炉总时长」的下标。 */
    public static final int TOTAL_DATA = 9;

    /** 数据槽里「第 0 个面（DOWN）外侧方块」的下标。 */
    public static final int NEIGHBOUR_DATA_START = 10;

    /** 数据槽里「本方块坐标」的起始下标（高低两半）。 */
    public static final int POS_DATA_START = 16;

    /** 数据槽里「剩余燃烧 / 本轮燃烧总时长」的下标。 */
    public static final int LIT_REMAINING_DATA = 18;
    public static final int LIT_TOTAL_DATA = 19;

    /**
     * 三个烧炼位的界面坐标（自绘面板，见 {@code XuanshuAlchemyFurnaceScreen}）。
     *
     * <p><b>竖排三行</b>（自上而下 lane 0 → 2），与另外两台机器同一套几何：
     * 行距 22、三行落在 y = 20 / 42 / 64。每行是「进料槽 → 蓝色进度条 → 要素行」。</p>
     */
    public static final int LANE_Y0 = 20, LANE_STEP = 22;

    /** 进料槽的 x（三行对齐）。 */
    public static final int INPUT_X = 20;

    /** 要素行的起点 x（每行一块，最多 6 枚、步进 17）。 */
    public static final int ASPECT_X = 74;

    /** 燃料槽的界面坐标（三行之下居中；火苗画在它左侧）。 */
    public static final int FUEL_X = 79, FUEL_Y = 88;

    /** 第 {@code lane} 行槽位的 y（槽高 18）。 */
    public static int laneY(int lane) {
        return LANE_Y0 + lane * LANE_STEP;
    }

    @Nullable
    private final XuanshuAlchemyFurnaceBlockEntity blockEntity;
    private final ContainerData data;

    /** 客户端工厂入口。 */
    public XuanshuAlchemyFurnaceMenu(int id, Inventory inv) {
        this(id, inv, null, new SimpleContainerData(DATA_COUNT));
    }

    /** 服务端：由方块实体创建。 */
    public XuanshuAlchemyFurnaceMenu(int id, Inventory inv, XuanshuAlchemyFurnaceBlockEntity be) {
        this(id, inv, be, new ContainerData() {
            @Override
            public int get(int index) {
                if (index < 6) {
                    return be.sideMode(Direction.values()[index]);
                }
                if (index < PROGRESS_DATA_START + XuanshuAlchemyFurnaceBlockEntity.LANES) {
                    return be.getProgress(index - PROGRESS_DATA_START);
                }
                return switch (index) {
                    case TOTAL_DATA -> XuanshuAlchemyFurnaceBlockEntity.PROCESS_TICKS;
                    case POS_DATA_START -> (int) (be.getBlockPos().asLong() >>> 32);
                    case POS_DATA_START + 1 -> (int) be.getBlockPos().asLong();
                    case LIT_REMAINING_DATA -> be.getLitRemaining();
                    case LIT_TOTAL_DATA -> be.getLitTotal();
                    default -> be.neighbourBlockId(Direction.values()[index - NEIGHBOUR_DATA_START]);
                };
            }

            @Override
            public void set(int index, int value) {
                if (index < 6) {
                    be.setSideMode(Direction.values()[index], value);
                }
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        });
    }

    private XuanshuAlchemyFurnaceMenu(int id, Inventory inv,
                                      @Nullable XuanshuAlchemyFurnaceBlockEntity be, ContainerData data) {
        super(ModMenus.XUANSHU_ALCHEMY_FURNACE.get(), id);
        this.blockEntity = be;
        this.data = data;
        this.addDataSlots(this.data);

        IItemHandler handler = be != null
                ? be.getItems()
                : new ItemStackHandler(XuanshuAlchemyFurnaceBlockEntity.SLOT_COUNT);
        for (int lane = 0; lane < XuanshuAlchemyFurnaceBlockEntity.LANES; lane++) {
            this.addSlot(new SlotItemHandler(handler, lane, INPUT_X + 1, laneY(lane) + 1));
        }
        this.addSlot(new SlotItemHandler(handler, XuanshuAlchemyFurnaceBlockEntity.SLOT_FUEL,
                FUEL_X + 1, FUEL_Y + 1));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, 9 + row * 9 + col, 8 + col * 18, 122 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, 8 + col * 18, 180));
        }
    }

    // ------------------------------------------------------------------
    // 界面读取
    // ------------------------------------------------------------------

    /** 某面的当前模式（0 关闭 / 1 自动输入）。 */
    public int getSideMode(int face) {
        return this.data.get(face);
    }

    /** 某一面外侧紧挨着的方块在登记表里的 id（0 = 空气）。 */
    public int getNeighbourBlockId(int face) {
        return this.data.get(NEIGHBOUR_DATA_START + face);
    }

    /** 第 {@code lane} 个烧炼位的进度（tick）。 */
    public int getCookProgress(int lane) {
        return this.data.get(PROGRESS_DATA_START + lane);
    }

    /** 一炉总时长（tick）。 */
    public int getCookTotal() {
        return this.data.get(TOTAL_DATA);
    }

    /** 第 {@code lane} 个烧炼位的进度比例（0~1）。 */
    public float getProgressRatio(int lane) {
        int total = this.getCookTotal();
        return total <= 0 ? 0.0F : Math.min(1.0F, (float) this.getCookProgress(lane) / total);
    }

    /** 当前是否在燃烧。 */
    public boolean isLit() {
        return this.getLitRemaining() > 0;
    }

    /** 剩余燃烧（tick）。 */
    public int getLitRemaining() {
        return this.data.get(LIT_REMAINING_DATA);
    }

    /** 本轮燃烧总时长（tick）。 */
    public int getLitTotal() {
        return this.data.get(LIT_TOTAL_DATA);
    }

    /** 燃烧剩余比例（0~1），画火苗用。 */
    public float getLitProgress() {
        int total = this.getLitTotal();
        return total <= 0 ? 0.0F : Math.min(1.0F, (float) this.getLitRemaining() / total);
    }

    /**
     * 本方块在客户端的位置（由数据槽下发）。
     *
     * <p>界面据此找到客户端那份方块实体，读它同步下来的「这一炉出哪些要素」。</p>
     */
    @Nullable
    public BlockPos getBlockPos() {
        long packed = ((long) this.data.get(POS_DATA_START) << 32)
                | (this.data.get(POS_DATA_START + 1) & 0xFFFFFFFFL);
        return packed == 0L ? null : BlockPos.of(packed);
    }

    /**
     * 侧配面板标题。
     *
     * <p>★ 这台机器<b>没有输出面可配</b>：烧出来的要素一律自动送往周围 5×5×3 里的管与罐
     * （见方块实体里的 {@code inputFaceFor}），所以面板只管进料，标题也照实写清楚，
     * 免得玩家去找「输出模式」。</p>
     */
    public static Component configTitle() {
        return Component.translatable("skydeitymysticpivot.gui.config_output_auto");
    }

    // ------------------------------------------------------------------
    // 交互
    // ------------------------------------------------------------------

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (this.blockEntity == null) {
            return false;
        }
        if (id >= 0 && id < 6) {
            this.blockEntity.cycleSide(Direction.values()[id]);
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < INV_SLOT_START) {
            if (!this.moveItemStackTo(stack, INV_SLOT_START, INV_SLOT_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!this.moveItemStackTo(stack, 0, XuanshuAlchemyFurnaceBlockEntity.LANES, false)
                && !this.moveItemStackTo(stack, XuanshuAlchemyFurnaceBlockEntity.SLOT_FUEL,
                        XuanshuAlchemyFurnaceBlockEntity.SLOT_FUEL + 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return this.blockEntity == null || !this.blockEntity.isRemoved();
    }
}
