package skydeity.mysticpivot;

import javax.annotation.Nullable;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * 玄枢奥术台（第八十九批新增）的容器菜单：中央一格<b>受附物品</b> + 玩家背包。
 *
 * <p>界面不带按钮——运转是<b>手持法杖右键方块</b>（见
 * {@link XuanshuArcaneTableBlockEntity#applyWithWand}：法杖六元各 77 点，
 * 令原有附魔各 +1，书槽有附魔书时把书上所有附魔一并加上）。这里只把东西放进去、拿回来。</p>
 *
 * <p>情报同步：{@link #DATA_KINDS}（附魔条数）/ {@link #DATA_MAXLV}（最高附魔等级），
 * 由 {@link ContainerData} 每 tick 现读。</p>
 *
 * <p>★ 关界面<b>不退还、不清空</b>：两槽是 BE 的 {@code ItemStackHandler}，关界面只是断开
 * 客户端连接，内容原地留在 BE 里、下次打开还在（用户第九十三/九十四批反馈「物品放上去
 * 不能存着，关闭 ui 就到物品栏了」）；挖 / 炸 / 活塞推方块时才由
 * {@code XuanshuArcaneTableBlockEntity#dropContents} 散出来。
 * 因此本类<b>不覆写 {@code removed()}</b>——第九十九批把那两个「只调 super」的空壳覆写删了
 * （留着等于没写，只是噪音）。</p>
 */
public class XuanshuArcaneTableMenu extends AbstractContainerMenu {

    /** 物品槽（要加附魔的物品）。 */
    public static final int SLOT_ITEM = 0;

    /** 书槽（可选）：放一本附魔书，运转时把书上所有附魔并进物品。 */
    public static final int SLOT_BOOK = 1;

    private static final int SLOT_COUNT = 2;

    /** 数据槽下标。 */
    public static final int DATA_KINDS = 0;
    public static final int DATA_MAXLV = 1;
    public static final int DATA_COUNT = 2;

    /** 界面坐标（Screen 同款）：第九十七批起<b>两格左右并排</b>（原先是上下两行，提示字被挤到背包上）。 */
    public static final int TARGET_X = 44, TARGET_Y = 44;
    public static final int BOOK_X = 116, BOOK_Y = 44;

    @Nullable
    private final XuanshuArcaneTableBlockEntity blockEntity;
    private final ItemStackHandler handler;
    private final ContainerData data;

    /** 客户端工厂（网络通道）。 */
    public XuanshuArcaneTableMenu(int id, Inventory inv) {
        this(id, inv, null);
    }

    /** 服务端：由方块实体的 {@code createMenu} 创建。 */
    public XuanshuArcaneTableMenu(int id, Inventory inv, @Nullable XuanshuArcaneTableBlockEntity be) {
        super(ModMenus.XUANSHU_ARCANE_TABLE.get(), id);
        this.blockEntity = be;
        this.handler = be != null ? be.rawItems() : new ItemStackHandler(SLOT_COUNT);
        this.data = be != null ? targetData(be) : new net.minecraft.world.inventory.SimpleContainerData(DATA_COUNT);
        this.addDataSlots(this.data);

        // 两格都不设放置门槛（第九十六批：用户反馈「两个都放不了」——旧的 mayPlace
        // 过滤在客户端就把点击拦了，玩家拿错物料连放都放不进；现在什么都让进，
        // 运转时再按内容识别：物品槽吃受附之物、书槽只认附魔书，放错的原样留着不用）。
        this.addSlot(new SlotItemHandler(this.handler, SLOT_ITEM, TARGET_X + 1, TARGET_Y + 1));
        this.addSlot(new SlotItemHandler(this.handler, SLOT_BOOK, BOOK_X + 1, BOOK_Y + 1));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, 9 + row * 9 + col, 8 + col * 18, 112 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, 8 + col * 18, 170));
        }
    }

    /** 情报两个数据槽：现读物品槽里的附魔（客户端由菜单同步机制推送）。 */
    private static ContainerData targetData(XuanshuArcaneTableBlockEntity be) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                ItemStack stack = be.getItems().getStackInSlot(SLOT_ITEM);
                var ench = stack.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
                if (ench == null || ench.isEmpty()) {
                    return 0;
                }
                int max = 0;
                for (var holder : ench.keySet()) {
                    max = Math.max(max, ench.getLevel(holder));
                }
                return index == DATA_KINDS ? ench.size() : max;
            }

            @Override
            public void set(int index, int value) {
                // 只读数据槽
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    /** 槽内物品的附魔条数（Screen 用）。 */
    public int targetEnchantKinds() {
        return this.data.get(DATA_KINDS);
    }

    /** 槽内物品的最高附魔等级（Screen 用）。 */
    public int targetMaxLevel() {
        return this.data.get(DATA_MAXLV);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < SLOT_COUNT) {
            if (!this.moveItemStackTo(stack, SLOT_COUNT, SLOT_COUNT + 36, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 背包 → 附魔书优先落书槽，其余先试物品槽、再试书槽
            boolean toBook = stack.is(net.minecraft.world.item.Items.ENCHANTED_BOOK)
                    && this.moveItemStackTo(stack, SLOT_BOOK, SLOT_BOOK + 1, false);
            if (!toBook
                    && !this.moveItemStackTo(stack, SLOT_ITEM, SLOT_ITEM + 1, false)
                    && !this.moveItemStackTo(stack, SLOT_BOOK, SLOT_BOOK + 1, false)) {
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
