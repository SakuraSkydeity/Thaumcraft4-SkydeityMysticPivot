package skydeity.mysticpivot;

import net.minecraft.core.BlockPos;
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
 * 玄枢虚空采矿机的菜单：<b>3×4</b> 产出栏（第一百九十三批从 3×3 加一列）+
 * <b>竖排 3 格锭槽</b>（第二百一十二批）+ 玩家背包 + 两个结构按钮。
 *
 * <p>按钮走原版 {@code clickMenuButton} 通道（{@code ServerboundContainerButtonClickPacket}），
 * 与炎炉 / 坩埚 / 炼金炉的侧配面板同一条路：客户端只发意图，服务端在
 * {@link #clickMenuButton} 里改世界。</p>
 *
 * <p>数据槽：0 = 运转指示（{@code gameTime % 20}），1 = 指示一圈的长度（20），
 * 2 = 光坛齐不齐（1/0），3~4 = 机器坐标（{@link BlockPos#asLong()} 两半），
 * 5 = 锭槽合计效率（0~9，第二百一十二批新增）。</p>
 *
 * <p>第一百九十九批：产出缩到「每 tick 一批」之后，0 / 1 两槽从「本炉进度 / 周期总长」
 * 改成「运转指示 / 一圈长度」—— 竖条不再是产出进度条，而是「机器在不在转」的指示灯。</p>
 */
public class XuanshuVoidMiningMenu extends AbstractContainerMenu {

    /** 按钮 id：一键搭建。 */
    public static final int BUTTON_BUILD = 0;
    /** 按钮 id：一键收回。 */
    public static final int BUTTON_DISMANTLE = 1;

    private static final int INV_SLOT_START = XuanshuVoidMiningBlockEntity.SLOT_COUNT;
    private static final int INV_SLOT_END = INV_SLOT_START + 36;

    public static final int DATA_COUNT = 6;
    public static final int PROGRESS_DATA = 0;
    public static final int TOTAL_DATA = 1;
    public static final int FORMED_DATA = 2;
    public static final int POS_DATA_START = 3;
    /** 锭槽合计效率（0~9），第二百一十二批新增，界面上的「效率 +N」读它。 */
    public static final int EFFICIENCY_DATA = 5;

    /** 产出栏（3 行 × 4 列）的界面坐标（自绘面板）。 */
    public static final int OUT_X = 44, OUT_Y = 22, OUT_STEP = 18;
    /** 产出栏几列：第一百九十三批从 3×3 改成 3×4，最左侧补了一列。 */
    public static final int OUT_COLS = 4;
    /** 产出栏几行。 */
    public static final int OUT_ROWS = 3;

    /**
     * 锭槽（竖排三格）的界面坐标：产物栏左边，隔开 14 像素。
     *
     * <p>用户口径「给产物栏左边隔远一点添加竖着的 3 个格子」——
     * 槽框画在 {@code UP_X}，产物栏第一格框在 {@code OUT_X - 1 = 43}，
     * 中间空出 43 − (13 + 16) = 14 像素，一眼分得开「升级件」与「产物」。</p>
     */
    public static final int UP_X = 13, UP_Y = 22, UP_STEP = 18;

    /** 背包区在加宽后的面板里居中（面板 196 宽，背包 162 宽）。 */
    public static final int INV_X = 18;
    public static final int INV_Y = 104;
    public static final int HOTBAR_Y = 162;

    /** 两个结构按钮的界面坐标（屏幕侧也读这两个常量画）。 */
    public static final int BTN_X = 143, BTN_Y = 24, BTN_W = 46, BTN_H = 18, BTN_GAP = 4;

    @Nullable
    private final XuanshuVoidMiningBlockEntity blockEntity;
    private final ContainerData data;

    /** 客户端工厂入口。 */
    public XuanshuVoidMiningMenu(int id, Inventory inv) {
        this(id, inv, null, new SimpleContainerData(DATA_COUNT));
    }

    /** 服务端：由方块实体创建。 */
    public XuanshuVoidMiningMenu(int id, Inventory inv, XuanshuVoidMiningBlockEntity be) {
        this(id, inv, be, new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    // 第一百九十九批：产出缩到每 tick 一批，「周期」不再有意义，
                    // 竖条改画「运转指示」（一秒扫一圈，见 getSweep()）
                    case PROGRESS_DATA -> be.getSweep();
                    case TOTAL_DATA -> XuanshuVoidMiningBlockEntity.SWEEP_TICKS;
                    case FORMED_DATA -> be.isFormed() ? 1 : 0;
                    case POS_DATA_START -> (int) (be.getBlockPos().asLong() >>> 32);
                    case POS_DATA_START + 1 -> (int) be.getBlockPos().asLong();
                    case EFFICIENCY_DATA -> be.efficiency();
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

    private XuanshuVoidMiningMenu(int id, Inventory inv,
                                  @Nullable XuanshuVoidMiningBlockEntity be, ContainerData data) {
        super(ModMenus.XUANSHU_VOID_MINING.get(), id);
        this.blockEntity = be;
        this.data = data;
        this.addDataSlots(this.data);

        IItemHandler handler = be != null
                ? be.getItems()
                : new ItemStackHandler(XuanshuVoidMiningBlockEntity.SLOT_COUNT);
        for (int row = 0; row < OUT_ROWS; row++) {
            for (int col = 0; col < OUT_COLS; col++) {
                this.addSlot(new SlotItemHandler(handler, row * OUT_COLS + col,
                        OUT_X + col * OUT_STEP + 1, OUT_Y + row * OUT_STEP + 1));
            }
        }

        // 锭槽：竖排三格，只收四神兽之锭 / 玄枢锭 / 未烬的玄枢锭，每格一枚
        for (int i = 0; i < XuanshuVoidMiningBlockEntity.UPGRADE_SLOTS; i++) {
            this.addSlot(new SlotItemHandler(handler, XuanshuVoidMiningBlockEntity.UPGRADE_BASE + i,
                    UP_X + 1, UP_Y + i * UP_STEP + 1) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return XuanshuVoidMiningBlockEntity.isUpgrade(stack);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, 9 + row * 9 + col, INV_X + col * 18, INV_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, INV_X + col * 18, HOTBAR_Y));
        }
    }

    // ------------------------------------------------------------------
    // 界面读取
    // ------------------------------------------------------------------

    public int getProgress() {
        return this.data.get(PROGRESS_DATA);
    }

    public int getTotal() {
        return this.data.get(TOTAL_DATA);
    }

    public float getProgressRatio() {
        int total = this.getTotal();
        return total <= 0 ? 0.0F : Math.min(1.0F, (float) this.getProgress() / total);
    }

    /** 光坛齐不齐（界面上的「已激活 / 未激活」）。 */
    public boolean isFormed() {
        return this.data.get(FORMED_DATA) != 0;
    }

    /** 锭槽合计效率（0~9），界面上的「效率 +N」读它。 */
    public int getEfficiency() {
        return this.data.get(EFFICIENCY_DATA);
    }

    @Nullable
    public BlockPos getBlockPos() {
        long packed = ((long) this.data.get(POS_DATA_START) << 32)
                | (this.data.get(POS_DATA_START + 1) & 0xFFFFFFFFL);
        return packed == 0L ? null : BlockPos.of(packed);
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
        boolean handled = switch (id) {
            case BUTTON_BUILD -> VoidMiningStructure.build(server, pos, player);
            case BUTTON_DISMANTLE -> VoidMiningStructure.dismantle(server, pos, player) > 0;
            default -> false;
        };
        if (handled) {
            // 立刻重算一次激活态，不用等下一秒的自检
            this.blockEntity.refreshFormedNow();
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
            // 机器里的东西（产物 / 锭）整体送回背包
            if (!this.moveItemStackTo(stack, INV_SLOT_START, INV_SLOT_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 背包 → 机器：只认锭槽那三格（产物栏不给玩家塞东西，塞进去就把矿石挤掉了）
            if (!XuanshuVoidMiningBlockEntity.isUpgrade(stack)) {
                return ItemStack.EMPTY;
            }
            if (!this.moveItemStackTo(stack, XuanshuVoidMiningBlockEntity.UPGRADE_BASE,
                    XuanshuVoidMiningBlockEntity.SLOT_COUNT, false)) {
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
