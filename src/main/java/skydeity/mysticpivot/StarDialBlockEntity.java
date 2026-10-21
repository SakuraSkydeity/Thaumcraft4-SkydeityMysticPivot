package skydeity.mysticpivot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 南北斗枢玄星盘的方块实体。
 *
 * <p>第一百一十四批之前它<b>什么都不存</b>，只是给 {@link StarDialRenderer} 一个挂载点。
 * 第一百一十七批起多了一件事：<b>这一格上供着的东西</b>——十二格都是星位、每格一件，
 * 有物品就代表那颗星亮着（{@link StarWork} 据此读星象）。</p>
 *
 * <h2>客户端同步（漏了就会「放上去看不见」）</h2>
 * <p>物品是渲染器在<b>客户端那台方块实体</b>上画的，所以内容一变就必须下发：
 * 只调 {@code setChanged()} 只保证存档，客户端手里还是空的。这里三件事配齐——
 * {@code getUpdateTag}（区块下发带全量）、{@code getUpdatePacket}（单点更新）、
 * 内容一变就 {@code sendBlockUpdated}（实时推送）。与 {@link XuanshuPedestalBlockEntity} 同一套。</p>
 */
public class StarDialBlockEntity extends BlockEntity {

    /** 唯一的一格：一件东西。 */
    public static final int SLOT = 0;

    private final ItemStackHandler items = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            syncToClient();
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };

    public StarDialBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STAR_DIAL.get(), pos, state);
    }

    /** 这一格上供着的东西（没有就是 {@link ItemStack#EMPTY}）。 */
    public ItemStack getItem() {
        return this.items.getStackInSlot(SLOT);
    }

    /** 换掉这一格上的东西（走 {@code setStackInSlot}，存档与下发一并处理）。 */
    public void setItem(ItemStack stack) {
        this.items.setStackInSlot(SLOT, stack);
    }

    /** 取走这一格上的东西并清空（破阵 / 拆方块时把物品吐给玩家）。 */
    public ItemStack takeItem() {
        ItemStack stack = this.items.getStackInSlot(SLOT);
        this.items.setStackInSlot(SLOT, ItemStack.EMPTY);
        return stack;
    }

    /** 内容一变就推给客户端；客户端调用为空操作。 */
    public void syncToClient() {
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(),
                    this.getBlockState(), Block.UPDATE_ALL);
        }
    }

    /** 区块下发 / 首次进入视野时的全量数据。 */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    /** 单点实时更新包。 */
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", this.items.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("items")) {
            this.items.deserializeNBT(registries, tag.getCompound("items"));
        }
    }
}
