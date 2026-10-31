package skydeity.mysticpivot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;

/**
 * 玄枢传送门的方块实体（第一百九十八批）。
 *
 * <h2>它现在只干三件事</h2>
 * <ol>
 *   <li><b>每秒自检一次</b>背后那座广场齐不齐（{@link PortalGateStructure#isComplete}），
 *       齐了就把 {@link XuanshuPortalBlock#FORMED} 点亮、放音效；</li>
 *   <li><b>数一下立起来几格</b>（{@link PortalGateStructure#filled}），界面上的竖排进度条读它 ——
 *       用户这一批要的就是「先把建筑搞定」，所以这条进度条量的是<b>工程量</b>，不是别的；</li>
 *   <li>开界面（{@link XuanshuPortalMenu}）。</li>
 * </ol>
 *
 * <h2>刻意还没做的</h2>
 * <p>没有传送、没有耗能、没有产出、没有区块加载、没有网络同步。用户口径是
 * 「<b>只添加 ui 不设计功能</b>」，12 格栏先摆在那儿能放东西（挖掉会散出来），
 * 等下一批定了传送规则再让它干活。</p>
 *
 * <h2>为什么进度不写进方块状态</h2>
 * <p>{@code filled} 一秒变一次，写进 blockstate 等于每秒一次 {@code setBlock} + 一次光照重算，
 * 上千格的广场施工时能把客户端刷得发烫。它只走菜单的 {@code ContainerData}
 * （原版 {@code broadcastChanges} 只给开着的界面发变化），
 * 方块状态里只留一个低频的 {@link XuanshuPortalBlock#FORMED}。</p>
 */
public class XuanshuPortalBlockEntity extends BlockEntity implements MenuProvider {

    /** 界面栏格数：3 行 × 4 列，与玄枢虚空水晶塔同一版式。 */
    public static final int SLOT_COUNT = 12;

    /** 结构自检的节拍（tick）：一秒一次。 */
    public static final int STRUCTURE_CHECK_TICKS = 20;

    /** 广场齐不齐（服务端每秒刷一次；界面靠数据槽读，不走方块实体同步）。 */
    private boolean formed;

    /** 已经立起来几格（缓存，界面每 tick 都会问）。 */
    private int filled;

    private final ItemStackHandler items = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public XuanshuPortalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.XUANSHU_PORTAL.get(), pos, state);
    }

    // ------------------------------------------------------------------
    // 状态读写
    // ------------------------------------------------------------------

    /** 广场齐不齐（界面上的「已激活 / 未激活」）。 */
    public boolean isFormed() {
        return formed;
    }

    /** 已经立起来几格（界面进度条分子）。 */
    public int getFilled() {
        return filled;
    }

    /** 一共要几格（界面进度条分母）：1099。 */
    public int getTotal() {
        return PortalGateStructure.total();
    }

    public ItemStackHandler getItems() {
        return items;
    }

    /**
     * 立刻重算一次（界面按完「搭建 / 收回」后调）。
     *
     * <p>不这么做的话，按完按钮要等下一秒的自检才更新，玩家会以为没生效。</p>
     */
    public void refreshNow() {
        if (!(this.level instanceof ServerLevel server)) {
            return;
        }
        recount(server);
    }

    /** 数一遍：齐不齐 + 立起来几格。 */
    private void recount(ServerLevel server) {
        BlockState state = this.getBlockState();
        if (!(state.getBlock() instanceof XuanshuPortalBlock)) {
            return;
        }
        Direction facing = state.getValue(XuanshuPortalBlock.FACING);
        boolean nowFormed = XuanshuPortalBlock.refreshFormed(server, this.worldPosition);
        int nowFilled = PortalGateStructure.filled(server, this.worldPosition, facing);
        if (nowFormed != this.formed || nowFilled != this.filled) {
            this.formed = nowFormed;
            this.filled = nowFilled;
            this.setChanged();
        }
    }

    // ------------------------------------------------------------------
    // 每 tick
    // ------------------------------------------------------------------

    @SuppressWarnings("unused")
    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  XuanshuPortalBlockEntity be) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (level.getGameTime() % STRUCTURE_CHECK_TICKS == 0L) {
            be.recount(server);
        }
    }

    /** 挖掉方块时把栏里的东西散出来。 */
    public void dropContents() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(this.level, this.worldPosition.getX(),
                        this.worldPosition.getY(), this.worldPosition.getZ(), stack.copy());
            }
        }
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
        tag.putBoolean("Formed", formed);
        tag.putInt("Filled", filled);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Items")) {
            items.deserializeNBT(registries, tag.getCompound("Items"));
        }
        formed = tag.getBoolean("Formed");
        filled = tag.getInt("Filled");
    }

    // ------------------------------------------------------------------
    // MenuProvider
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.skydeitymysticpivot.xuanshu_portal");
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new XuanshuPortalMenu(id, inv, this);
    }
}
