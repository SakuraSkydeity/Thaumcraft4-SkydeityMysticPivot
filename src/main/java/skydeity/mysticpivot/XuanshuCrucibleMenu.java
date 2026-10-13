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
 * 玄枢坩埚的容器菜单：<b>三组</b>工作面（每组一进一出，共 6 格）+ 玩家背包。
 *
 * <p>三个工作面各自带一条进度条，数据槽里也同样分三段下发（见 {@link #PROGRESS_DATA_START}）。
 * 「传输配置」那套与玄枢炎炉完全同款：六面模式、精确输入开关、以及
 * <b>每一面外侧挨着的方块</b>（界面按钮上直接画出它来）都走 {@link ContainerData}
 * 自动同步；点按钮走 {@code clickMenuButton} 通道，客户端只发意图、服务端改状态。</p>
 */
public class XuanshuCrucibleMenu extends AbstractContainerMenu {

    /** 玩家背包槽位的起始下标。 */
    private static final int INV_SLOT_START = XuanshuCrucibleBlockEntity.SLOT_COUNT;
    private static final int INV_SLOT_END = INV_SLOT_START + 36;

    /**
     * 数据槽布局：
     * <ul>
     *   <li>0~5 = 六面模式；</li>
     *   <li>6~8 = 三个工作面的进度（tick）；</li>
     *   <li>9 = 一炉总时长；</li>
     *   <li>10 = 精确输入；</li>
     *   <li>11~16 = 六面外侧的方块；</li>
     *   <li>17~18 = 本方块坐标（{@link BlockPos#asLong()} 的高低两半）。</li>
     * </ul>
     *
     * <p>坐标那两槽是给界面用的：界面画「需要哪些要素」时要读<b>客户端那份方块实体</b>
     * 的同步快照（要素吸入由服务端说了算），所以得先知道自己在哪一格。</p>
     */
    public static final int DATA_COUNT = 19;

    /** 数据槽里「三个工作面进度」的起始下标。 */
    public static final int PROGRESS_DATA_START = 6;

    /** 数据槽里「一炉总时长」的下标。 */
    public static final int TOTAL_DATA = 9;

    /** 数据槽里「精确输入」的下标。 */
    public static final int STRICT_DATA = 10;

    /** 数据槽里「第 0 个面（DOWN）外侧方块」的下标。 */
    public static final int NEIGHBOUR_DATA_START = 11;

    /** 数据槽里「本方块坐标」的起始下标（高低两半）。 */
    public static final int POS_DATA_START = 17;

    /** 「精确输入」按钮的 id。 */
    public static final int BUTTON_STRICT_INPUT = 6;

    /**
     * 三个工作面的界面坐标（自绘面板，见 {@code XuanshuCrucibleScreen}）。
     *
     * <p><b>竖排三行</b>（自上而下 lane 0 → 2）：行距 22（槽本身 18、行间留 4），
     * 于是三行落在 y = 20 / 42 / 64。每行是「进料槽 → 蓝色进度条 → 要素行」——
     * 竖排之后每行的横向空间很宽，要素那一行能<b>一次摆下 6 枚</b>（步进 17）。</p>
     */
    public static final int LANE_Y0 = 20, LANE_STEP = 22;

    /** 进料槽的 x（三行对齐，所以只有一个值）。 */
    public static final int INPUT_X = 20;

    /** 出料槽的 x。 */
    public static final int OUTPUT_X = 74;

    /**
     * 要素行的起点 x（每行一块，最多 {@code 5} 枚、步进 16）。
     *
     * <p>★ <b>它必须落在出料槽右边（第 35 批修的）</b>：出料槽的格框画在
     * {@code 74~92}，所以要素行从 {@code 94} 起 —— 94、110、126、142、158，
     * 最后一枚贴到 {@code 174}，正好把面板右缘那条暗边留出来。</p>
     *
     * <p>原先这里是 {@code 74}（<b>与出料槽同一起点</b>），于是那一排要素图标
     * 整个压在产物格上，玩家压根看不见槽里的东西。要素最多 5 样
     * （玄枢坩埚全部配方里最多的一条就是 5 样，见 {@code xuanshu_crucible/**}），
     * 所以 5 枚够用。</p>
     */
    public static final int ASPECT_X = 94;

    /** 第 {@code lane} 行槽位的 y（槽高 18）。 */
    public static int laneY(int lane) {
        return LANE_Y0 + lane * LANE_STEP;
    }

    @Nullable
    private final XuanshuCrucibleBlockEntity blockEntity;
    private final ContainerData data;

    /** 客户端工厂入口。 */
    public XuanshuCrucibleMenu(int id, Inventory inv) {
        this(id, inv, null, new SimpleContainerData(DATA_COUNT));
    }

    /** 服务端：由方块实体创建。 */
    public XuanshuCrucibleMenu(int id, Inventory inv, XuanshuCrucibleBlockEntity be) {
        this(id, inv, be, new ContainerData() {
            @Override
            public int get(int index) {
                if (index < 6) {
                    return be.sideMode(Direction.values()[index]);
                }
                if (index < PROGRESS_DATA_START + XuanshuCrucibleBlockEntity.LANES) {
                    return be.getProgress(index - PROGRESS_DATA_START);
                }
                return switch (index) {
                    case TOTAL_DATA -> XuanshuCrucibleBlockEntity.PROCESS_TICKS;
                    case STRICT_DATA -> be.hasStrictInput() ? 1 : 0;
                    case POS_DATA_START -> (int) (be.getBlockPos().asLong() >>> 32);
                    case POS_DATA_START + 1 -> (int) be.getBlockPos().asLong();
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

    private XuanshuCrucibleMenu(int id, Inventory inv,
                                @Nullable XuanshuCrucibleBlockEntity be, ContainerData data) {
        super(ModMenus.XUANSHU_CRUCIBLE.get(), id);
        this.blockEntity = be;
        this.data = data;
        this.addDataSlots(this.data);

        IItemHandler handler = be != null
                ? be.getItems()
                : new ItemStackHandler(XuanshuCrucibleBlockEntity.SLOT_COUNT);
        for (int lane = 0; lane < XuanshuCrucibleBlockEntity.LANES; lane++) {
            this.addSlot(new SlotItemHandler(handler, XuanshuCrucibleBlockEntity.inputSlot(lane),
                    INPUT_X + 1, laneY(lane) + 1));
            this.addSlot(new SlotItemHandler(handler, XuanshuCrucibleBlockEntity.outputSlot(lane),
                    OUTPUT_X + 1, laneY(lane) + 1) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, 9 + row * 9 + col, 8 + col * 18, 100 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, 8 + col * 18, 158));
        }
    }

    // ------------------------------------------------------------------
    // 界面读取
    // ------------------------------------------------------------------

    /** 某面的当前模式（0 关闭 / 1 自动输入 / 2 自动输出 / 3 输入输出）。 */
    public int getSideMode(int face) {
        return this.data.get(face);
    }

    /** 某一面外侧紧挨着的方块在登记表里的 id（0 = 空气）。 */
    public int getNeighbourBlockId(int face) {
        return this.data.get(NEIGHBOUR_DATA_START + face);
    }

    /** 第 {@code lane} 个工作面的进度（tick）。 */
    public int getCookProgress(int lane) {
        return this.data.get(PROGRESS_DATA_START + lane);
    }

    /** 一炉总时长（tick）。 */
    public int getCookTotal() {
        return this.data.get(TOTAL_DATA);
    }

    /** 第 {@code lane} 个工作面的进度比例（0~1）。 */
    public float getProgressRatio(int lane) {
        int total = this.getCookTotal();
        return total <= 0 ? 0.0F : Math.min(1.0F, (float) this.getCookProgress(lane) / total);
    }

    /** 是否开启精确输入。 */
    public boolean hasStrictInput() {
        return this.data.get(STRICT_DATA) != 0;
    }

    /**
     * 本方块在客户端的位置（由数据槽下发）。
     *
     * <p>界面据此找到客户端那份方块实体，读它同步下来的「需要哪些要素 / 已经吸到多少」。
     * 数据槽还没送到时两边都是 0，这里回 {@code null}，界面就按「还没数据」处理。</p>
     */
    @Nullable
    public BlockPos getBlockPos() {
        long packed = ((long) this.data.get(POS_DATA_START) << 32)
                | (this.data.get(POS_DATA_START + 1) & 0xFFFFFFFFL);
        return packed == 0L ? null : BlockPos.of(packed);
    }

    /** 侧配面板标题。 */
    public static Component configTitle() {
        return Component.translatable("skydeitymysticpivot.gui.config");
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
        if (id == BUTTON_STRICT_INPUT) {
            this.blockEntity.toggleStrictInput();
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
        } else if (!this.moveItemStackTo(stack, XuanshuCrucibleBlockEntity.SLOT_INPUT_BASE,
                XuanshuCrucibleBlockEntity.SLOT_INPUT_BASE + XuanshuCrucibleBlockEntity.LANES, false)) {
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
