package skydeity.mysticpivot;

import javax.annotation.Nullable;
import net.minecraft.core.Direction;
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
 * 玄枢复刻台的容器菜单：<b>一进一出</b>（左 = 模板槽、右 = 产物槽）+ 玩家背包。
 *
 * <p>状态走 {@link ContainerData}（服务端自动同步到客户端）：</p>
 * <ul>
 *   <li>0 = 当前进度（tick）、1 = 一批总时长（{@link XuanshuReplicatorBlockEntity#COPY_INTERVAL}）；</li>
 *   <li>2~7 = 六个面的侧配模式（关 / 自动输入 / 自动输出 / 输入输出）；</li>
 *   <li>8~13 = 六个面外侧挨着的方块 id（「传输配置」面板按钮上画的那台机器）。</li>
 * </ul>
 *
 * <p>点击侧配面板走原版 {@code clickMenuButton} 通道（与炎炉同款：
 * 客户端只发意图，真实状态在服务端改）。</p>
 */
public class XuanshuReplicatorMenu extends AbstractContainerMenu {

    /** 玩家背包槽位的起始下标。 */
    private static final int INV_SLOT_START = XuanshuReplicatorBlockEntity.SLOT_COUNT;
    private static final int INV_SLOT_END = INV_SLOT_START + 36;

    /** 数据槽布局：进度两组 + 六面模式 + 六面邻居。 */
    public static final int DATA_COUNT = 14;
    public static final int PROGRESS_DATA = 0;
    public static final int TOTAL_DATA = 1;
    /** 六面模式数据槽的起点（0~5 面 = 2~7）。 */
    public static final int SIDE_DATA_START = 2;
    /** 六面邻居方块 id 数据槽的起点（0~5 面 = 8~13）。 */
    public static final int NEIGHBOUR_DATA_START = 8;

    /** 槽位的界面坐标（自绘面板，见 {@code XuanshuReplicatorScreen}）。 */
    public static final int INPUT_X = 52, OUTPUT_X = 106, ROW_Y = 34;

    @Nullable
    private final XuanshuReplicatorBlockEntity blockEntity;
    private final ContainerData data;

    /** 客户端工厂入口（无额外数据；槽位内容由菜单同步机制推送）。 */
    public XuanshuReplicatorMenu(int id, Inventory inv) {
        this(id, inv, null, new SimpleContainerData(DATA_COUNT));
    }

    /** 服务端：由方块实体创建。 */
    public XuanshuReplicatorMenu(int id, Inventory inv, XuanshuReplicatorBlockEntity be) {
        this(id, inv, be, new ContainerData() {
            @Override
            public int get(int index) {
                if (index >= SIDE_DATA_START && index < SIDE_DATA_START + 6) {
                    return be.sideMode(Direction.values()[index - SIDE_DATA_START]);
                }
                if (index >= NEIGHBOUR_DATA_START && index < NEIGHBOUR_DATA_START + 6) {
                    return be.neighbourBlockId(Direction.values()[index - NEIGHBOUR_DATA_START]);
                }
                return switch (index) {
                    case PROGRESS_DATA -> be.getProgress();
                    case TOTAL_DATA -> XuanshuReplicatorBlockEntity.COPY_INTERVAL;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // 进度只由服务端 tick 推进；模式走 clickMenuButton，不接受数据槽写入
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        });
    }

    private XuanshuReplicatorMenu(int id, Inventory inv,
                                  @Nullable XuanshuReplicatorBlockEntity be, ContainerData data) {
        super(ModMenus.XUANSHU_REPLICATOR.get(), id);
        this.blockEntity = be;
        this.data = data;
        this.addDataSlots(this.data);

        IItemHandler handler = be != null
                ? be.getItems()
                : new ItemStackHandler(XuanshuReplicatorBlockEntity.SLOT_COUNT);
        // 模板槽（左）
        this.addSlot(new SlotItemHandler(handler, XuanshuReplicatorBlockEntity.SLOT_INPUT,
                INPUT_X + 1, ROW_Y + 1));
        // 产物槽（右）：只许取，不许放
        this.addSlot(new SlotItemHandler(handler, XuanshuReplicatorBlockEntity.SLOT_OUTPUT,
                OUTPUT_X + 1, ROW_Y + 1) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, 9 + row * 9 + col, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, 8 + col * 18, 142));
        }
    }

    /** 当前复刻进度（tick）。 */
    public int getCopyProgressTick() {
        return this.data.get(PROGRESS_DATA);
    }

    /** 一批总时长（tick）。 */
    public int getCopyTotal() {
        return this.data.get(TOTAL_DATA);
    }

    /** 复刻进度比例（0~1），画箭头用。 */
    public float getCopyProgress() {
        int total = this.getCopyTotal();
        return total <= 0 ? 0.0F : Math.min(1.0F, (float) this.getCopyProgressTick() / total);
    }

    /** 客户端读取某面的当前模式（0 关闭 / 1 自动输入 / 2 自动输出 / 3 输入输出）。 */
    public int getSideMode(int face) {
        return this.data.get(SIDE_DATA_START + face);
    }

    /** 某一面外侧紧挨着的方块在登记表里的 id（0 = 空气 / 未同步）。 */
    public int getNeighbourBlockId(int face) {
        return this.data.get(NEIGHBOUR_DATA_START + face);
    }

    /**
     * 「传输配置」面板上的按钮（与炎炉同款通道）：0~5 号 = 六个面循环切换模式，
     * 客户端只发意图，服务端在这里真正改状态。
     */
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
            // 机器槽 → 背包
            if (!this.moveItemStackTo(stack, INV_SLOT_START, INV_SLOT_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 背包 → 只进模板槽（产物槽拒收）
            if (!this.moveItemStackTo(stack, XuanshuReplicatorBlockEntity.SLOT_INPUT,
                    XuanshuReplicatorBlockEntity.SLOT_INPUT + 1, false)) {
                return ItemStack.EMPTY;
            }
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
