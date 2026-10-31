package skydeity.mysticpivot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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

import javax.annotation.Nullable;

/**
 * 玄枢传送门的菜单（第一百九十八批）：<b>与玄枢虚空水晶塔一模一样的版式</b>。
 *
 * <p>用户口径：「<b>给传送门添加和矿机一样的 ui</b>」。所以这里<b>一个版式常量都不新写</b> ——
 * 槽位、背包、按钮的坐标全部直接引用 {@link XuanshuVoidMiningMenu} 的那一套
 * （{@code OUT_X} / {@code INV_X} / {@code BTN_X} …），哪天矿机的界面挪了位置，这台跟着挪，
 * 两边的界面不会各走各的。</p>
 *
 * <p>按钮走原版 {@code clickMenuButton} 通道（{@code ServerboundContainerButtonClickPacket}）：
 * 客户端只发意图，服务端在 {@link #clickMenuButton} 里动世界。</p>
 *
 * <p>数据槽：0 = 已立起格数，1 = 总格数（1099），2 = 广场齐不齐（1/0）。
 * 进度条量的是<b>工程量</b>（已立起 / 1099）—— 这一批先把建筑搞定，传送还没接。</p>
 */
public class XuanshuPortalMenu extends AbstractContainerMenu {

    /** 按钮 id：一键搭建。 */
    public static final int BUTTON_BUILD = 0;
    /** 按钮 id：一键收回。 */
    public static final int BUTTON_DISMANTLE = 1;

    private static final int INV_SLOT_START = XuanshuPortalBlockEntity.SLOT_COUNT;
    private static final int INV_SLOT_END = INV_SLOT_START + 36;

    public static final int DATA_COUNT = 3;
    public static final int FILLED_DATA = 0;
    public static final int TOTAL_DATA = 1;
    public static final int FORMED_DATA = 2;

    @Nullable
    private final XuanshuPortalBlockEntity blockEntity;
    private final ContainerData data;

    /** 客户端工厂入口。 */
    public XuanshuPortalMenu(int id, Inventory inv) {
        this(id, inv, null, new SimpleContainerData(DATA_COUNT));
    }

    /** 服务端：由方块实体创建。 */
    public XuanshuPortalMenu(int id, Inventory inv, XuanshuPortalBlockEntity be) {
        this(id, inv, be, new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case FILLED_DATA -> be.getFilled();
                    case TOTAL_DATA -> be.getTotal();
                    case FORMED_DATA -> be.isFormed() ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // 只读数据槽，无可写
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        });
    }

    private XuanshuPortalMenu(int id, Inventory inv,
                              @Nullable XuanshuPortalBlockEntity be, ContainerData data) {
        super(ModMenus.XUANSHU_PORTAL.get(), id);
        this.blockEntity = be;
        this.data = data;
        this.addDataSlots(this.data);

        IItemHandler handler = be != null
                ? be.getItems()
                : new ItemStackHandler(XuanshuPortalBlockEntity.SLOT_COUNT);
        for (int row = 0; row < XuanshuVoidMiningMenu.OUT_ROWS; row++) {
            for (int col = 0; col < XuanshuVoidMiningMenu.OUT_COLS; col++) {
                this.addSlot(new SlotItemHandler(handler, row * XuanshuVoidMiningMenu.OUT_COLS + col,
                        XuanshuVoidMiningMenu.OUT_X + col * XuanshuVoidMiningMenu.OUT_STEP + 1,
                        XuanshuVoidMiningMenu.OUT_Y + row * XuanshuVoidMiningMenu.OUT_STEP + 1));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, 9 + row * 9 + col,
                        XuanshuVoidMiningMenu.INV_X + col * 18,
                        XuanshuVoidMiningMenu.INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, XuanshuVoidMiningMenu.INV_X + col * 18,
                    XuanshuVoidMiningMenu.HOTBAR_Y));
        }
    }

    // ------------------------------------------------------------------
    // 界面读取
    // ------------------------------------------------------------------

    /** 进度（0~1）：已立起格数 / 总格数。 */
    public float getFillRatio() {
        int total = this.data.get(TOTAL_DATA);
        return total <= 0 ? 0.0F : Math.min(1.0F, (float) this.data.get(FILLED_DATA) / total);
    }

    /** 广场齐不齐（界面上的「已激活 / 未激活」）。 */
    public boolean isFormed() {
        return this.data.get(FORMED_DATA) != 0;
    }

    /** 这门朝哪（结构往它背面长）。 */
    private Direction facing() {
        if (this.blockEntity == null) {
            return Direction.NORTH;
        }
        return this.blockEntity.getBlockState().getValue(XuanshuPortalBlock.FACING);
    }

    // ------------------------------------------------------------------
    // 结构按钮
    // ------------------------------------------------------------------

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (this.blockEntity == null || this.blockEntity.getLevel() == null) {
            return false;
        }
        if (!(this.blockEntity.getLevel() instanceof ServerLevel server)) {
            return false;
        }
        BlockPos pos = this.blockEntity.getBlockPos();
        Direction facing = facing();
        boolean handled = switch (id) {
            case BUTTON_BUILD -> PortalGateStructure.build(server, pos, facing, player);
            case BUTTON_DISMANTLE -> PortalGateStructure.dismantle(server, pos, facing, player) > 0;
            default -> false;
        };
        if (handled) {
            // 立刻重算一次激活态，不用等下一秒的自检
            this.blockEntity.refreshNow();
        }
        return handled;
    }

    // ------------------------------------------------------------------
    // 背包交互
    // ------------------------------------------------------------------

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
        } else if (!this.moveItemStackTo(stack, 0, XuanshuPortalBlockEntity.SLOT_COUNT, false)) {
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
