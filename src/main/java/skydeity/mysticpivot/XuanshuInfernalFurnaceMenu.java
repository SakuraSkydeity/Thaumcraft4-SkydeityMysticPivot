package skydeity.mysticpivot;

import javax.annotation.Nullable;
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
 * 玄枢炎炉的容器菜单：<b>三组</b>熔炼位（每组一进一出，共 6 格）+ 一格外置燃料 + 玩家背包。
 *
 * <p>三个熔炼位各自带一条进度条，数据槽里分三段下发（见 {@link #PROGRESS_DATA_START}）；
 * 燃料只有一格，点起来那一刻三个格子一起烧（见方块实体 {@code tickSmelting}）。</p>
 *
 * <p>「传输配置」的六面模式与「精确输入」开关走 {@link ContainerData}（服务端自动同步到客户端）；
 * 点击面板上的按钮走本体的 {@code clickMenuButton} 通道
 * （{@code ServerboundContainerButtonClickPacket}），服务端在 {@link #clickMenuButton} 里执行
 * ——与 Mekanism 同一套交互语义（客户端只发意图，真实状态在服务端改）。</p>
 */
public class XuanshuInfernalFurnaceMenu extends AbstractContainerMenu {

    /** 玩家背包槽位的起始下标。 */
    private static final int INV_SLOT_START = XuanshuInfernalFurnaceBlockEntity.SLOT_COUNT;
    private static final int INV_SLOT_END = INV_SLOT_START + 36;

    /**
     * 数据槽布局：
     * <ul>
     *   <li>0~5 = 六面模式；</li>
     *   <li>6~8 = 三个熔炼位的进度（tick）；</li>
     *   <li>9 = 一炉总时长；</li>
     *   <li>10 = 剩余燃烧；11 = 本轮燃烧总时长；</li>
     *   <li>12 = 精确输入；</li>
     *   <li>13~18 = 六面外侧挨着的方块（登记表 id，0 即空气）。</li>
     * </ul>
     */
    public static final int DATA_COUNT = 19;

    /** 数据槽里「三个熔炼位进度」的起始下标。 */
    public static final int PROGRESS_DATA_START = 6;

    /** 数据槽里「一炉总时长」的下标。 */
    public static final int TOTAL_DATA = 9;

    /** 数据槽里「剩余燃烧 / 本轮燃烧总时长」的下标。 */
    public static final int LIT_REMAINING_DATA = 10;
    public static final int LIT_TOTAL_DATA = 11;

    /** 数据槽里「精确输入」的下标。 */
    public static final int STRICT_DATA = 12;

    /** 数据槽里「第 0 个面（DOWN）外侧方块」的下标。 */
    public static final int NEIGHBOUR_DATA_START = 13;

    /** 面板上「精确输入」按钮用的菜单按钮 id。 */
    public static final int BUTTON_STRICT_INPUT = 6;

    /**
     * 三组槽位的界面坐标（自绘面板，见 {@code XuanshuInfernalFurnaceScreen}）。
     *
     * <p><b>竖排三行</b>（自上而下 lane 0 → 2）：行距 22（槽本身 18、行间留 4），
     * 三行落在 y = 20 / 42 / 64。每行是「进料槽 → 蓝色进度条 → 出料槽」。</p>
     */
    public static final int LANE_Y0 = 20, LANE_STEP = 22;

    /** 进料槽的 x（三行对齐）。 */
    public static final int INPUT_X = 20;

    /** 出料槽的 x。 */
    public static final int OUTPUT_X = 74;

    /** 燃料槽的界面坐标（三行之下居中；火苗画在它左侧）。 */
    public static final int FUEL_X = 79, FUEL_Y = 88;

    /** 第 {@code lane} 行槽位的 y（槽高 18）。 */
    public static int laneY(int lane) {
        return LANE_Y0 + lane * LANE_STEP;
    }

    @Nullable
    private final XuanshuInfernalFurnaceBlockEntity blockEntity;
    private final ContainerData data;

    /** 客户端工厂入口（无额外数据；槽位内容与侧配状态都由菜单同步机制推送）。 */
    public XuanshuInfernalFurnaceMenu(int id, Inventory inv) {
        this(id, inv, null, new SimpleContainerData(DATA_COUNT));
    }

    /** 服务端：由方块实体创建。 */
    public XuanshuInfernalFurnaceMenu(int id, Inventory inv, XuanshuInfernalFurnaceBlockEntity be) {
        this(id, inv, be, new ContainerData() {
            @Override
            public int get(int index) {
                if (index < 6) {
                    return be.sideMode(Direction.values()[index]);
                }
                if (index < PROGRESS_DATA_START + XuanshuInfernalFurnaceBlockEntity.LANES) {
                    return be.getProgress(index - PROGRESS_DATA_START);
                }
                return switch (index) {
                    case TOTAL_DATA -> XuanshuInfernalFurnaceBlockEntity.SMELT_INTERVAL;
                    case LIT_REMAINING_DATA -> be.getLitRemaining();
                    case LIT_TOTAL_DATA -> be.getLitTotal();
                    case STRICT_DATA -> be.hasStrictInput() ? 1 : 0;
                    // 13~18：六个面外侧挨着的方块（登记表 id，0 即空气）——就是面板按钮上画的那个图标。
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

    private XuanshuInfernalFurnaceMenu(int id, Inventory inv,
                                       @Nullable XuanshuInfernalFurnaceBlockEntity be, ContainerData data) {
        super(ModMenus.XUANSHU_INFERNAL_FURNACE.get(), id);
        this.blockEntity = be;
        this.data = data;
        this.addDataSlots(this.data);

        IItemHandler handler = be != null
                ? be.getItems()
                : new ItemStackHandler(XuanshuInfernalFurnaceBlockEntity.SLOT_COUNT);
        for (int lane = 0; lane < XuanshuInfernalFurnaceBlockEntity.LANES; lane++) {
            this.addSlot(new SlotItemHandler(handler, XuanshuInfernalFurnaceBlockEntity.inputSlot(lane),
                    INPUT_X + 1, laneY(lane) + 1));
            this.addSlot(new SlotItemHandler(handler, XuanshuInfernalFurnaceBlockEntity.outputSlot(lane),
                    OUTPUT_X + 1, laneY(lane) + 1) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }
            });
        }
        this.addSlot(new SlotItemHandler(handler, XuanshuInfernalFurnaceBlockEntity.SLOT_FUEL,
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

    /** 客户端读取某面的当前模式（0 关闭 / 1 自动输入 / 2 自动输出 / 3 输入输出）。 */
    public int getSideMode(int face) {
        return this.data.get(face);
    }

    /** 第 {@code lane} 个熔炼位的进度（tick）。 */
    public int getCookProgress(int lane) {
        return this.data.get(PROGRESS_DATA_START + lane);
    }

    /** 熔炼总时长（tick）。 */
    public int getCookTotal() {
        return this.data.get(TOTAL_DATA);
    }

    /** 剩余燃烧（tick）。 */
    public int getLitRemaining() {
        return this.data.get(LIT_REMAINING_DATA);
    }

    /** 本轮燃烧总时长（tick）。 */
    public int getLitTotal() {
        return this.data.get(LIT_TOTAL_DATA);
    }

    /** 是否开启精确输入。 */
    public boolean hasStrictInput() {
        return this.data.get(STRICT_DATA) != 0;
    }

    /**
     * 某一面外侧紧挨着的方块在登记表里的 id（0 = 空气 / 未同步）。
     *
     * @param face {@code Direction#ordinal()} 的取值，与数据槽 0~5 一一对应
     */
    public int getNeighbourBlockId(int face) {
        return this.data.get(NEIGHBOUR_DATA_START + face);
    }

    // ------------------------------------------------------------------
    // 原版熔炉风格的进度读取（AbstractFurnaceMenu 的同名方法）
    // ------------------------------------------------------------------

    /** 当前是否在燃烧。 */
    public boolean isLit() {
        return this.getLitRemaining() > 0;
    }

    /** 燃烧剩余比例（0~1），画火焰用。 */
    public float getLitProgress() {
        int total = this.getLitTotal();
        return total <= 0 ? 0.0F : Math.min(1.0F, (float) this.getLitRemaining() / total);
    }

    /** 第 {@code lane} 个熔炼位的进度比例（0~1），画箭头用。 */
    public float getBurnProgress(int lane) {
        int total = this.getCookTotal();
        return total <= 0 ? 0.0F : Math.min(1.0F, (float) this.getCookProgress(lane) / total);
    }

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
            // 机器槽 → 背包
            if (!this.moveItemStackTo(stack, INV_SLOT_START, INV_SLOT_END, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 背包 → 先试三个输入槽，再试燃料槽（与原版熔炉同款两段式）
            if (!this.moveItemStackTo(stack, XuanshuInfernalFurnaceBlockEntity.SLOT_INPUT_BASE,
                    XuanshuInfernalFurnaceBlockEntity.SLOT_INPUT_BASE
                            + XuanshuInfernalFurnaceBlockEntity.LANES, false)
                    && !this.moveItemStackTo(stack, XuanshuInfernalFurnaceBlockEntity.SLOT_FUEL,
                            XuanshuInfernalFurnaceBlockEntity.SLOT_FUEL + 1, false)) {
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

    /** 侧配面板标题（屏幕用）。 */
    public static Component configTitle() {
        return Component.translatable("skydeitymysticpivot.gui.config");
    }
}
