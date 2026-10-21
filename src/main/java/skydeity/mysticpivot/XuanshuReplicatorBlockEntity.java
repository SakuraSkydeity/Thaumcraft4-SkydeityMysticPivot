package skydeity.mysticpivot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;

/**
 * 玄枢复刻台：左格放「模板」，右格原样复刻——<b>完全一模一样的复制</b>。
 *
 * <h2>复刻规则</h2>
 * <ul>
 *   <li><b>模板不消耗</b>：左格是模板位，放进去的东西原地不动，机器照着它不断产出复制品；</li>
 *   <li><b>一批一件起、满料翻倍</b>：每 {@value #COPY_INTERVAL} tick（1 秒）复刻一批，
 *       批量 = min(模板堆叠数, 输出槽余位, {@value #MAX_BATCH})——放 1 件每秒出 1 件，
 *       放满一组每秒出 64 件（与炎炉「一炉 64 件」同款量级）；</li>
 *   <li><b>一模一样</b>：复制品用 {@link ItemStack#copy()} 整份深拷贝——附魔、耐久、
 *       自定义名字、容器内容（潜影盒 / 背包）等<b>全部数据组件</b>原样随行。</li>
 * </ul>
 *
 * <h2>★ 数据安全（为什么这里绝不出现「NBT 泄露」）</h2>
 * <p>1.20.5 起（含 1.21.1）物品数据不再是挂在 {@code ItemStack} 上的裸 NBT
 * {@code tag} 复合标签，而换成<b>数据组件</b>（{@code DataComponentMap}，
 * 不可变、写时复制）。因此：</p>
 * <ul>
 *   <li>复制品一律 {@code input.copy()} <b>新造</b>一个 {@code ItemStack} 再设数量，
 *       <b>绝不把同一个 ItemStack 实例同时塞进两个槽</b>——旧式 NBT 代码里
 *       {@code getOrCreateTag()} 共享同一份可变 CompoundTag 的那种泄露，在组件模型下
 *       只要守「不共享实例」这条线就不会发生；</li>
 *   <li>组件图本身不可变：两个栈即使共享同一份组件图（原版堆叠本就如此）也互不影响；</li>
 *   <li>输出槽合并用 {@link ItemStack#isSameItemSameComponents}：只有<b>逐组件相等</b>的
 *       复制品才并堆，绝不出现「内容不同的两份背包混进一组」；</li>
 *   <li>对外掉落 / 搬运一律再 {@code copy()} 一份，槽里的引用绝不直接交出去。</li>
 * </ul>
 *
 * <h2>自动化（第一百四十四批起与其他工作方块同一套）</h2>
 * <p>六个面各自可设四档：{@link #MODE_OFF 关闭} → {@link #MODE_INPUT 自动输入} →
 * {@link #MODE_OUTPUT 自动输出} → {@link #MODE_IO 输入输出}（GUI「传输配置」面板循环切换）：</p>
 * <ul>
 *   <li><b>自动输入面</b>：每 {@value #TRANSFER_INTERVAL} tick 从相邻容器拉取物品，只塞进模板槽；</li>
 *   <li><b>自动输出面</b>：每 {@value #TRANSFER_INTERVAL} tick 把产物槽推给相邻容器；</li>
 *   <li>能力暴露（漏斗 / 管道）同样按面裁剪：输入面只收模板槽、输出面只给产物槽。</li>
 * </ul>
 */
public class XuanshuReplicatorBlockEntity extends BlockEntity implements MenuProvider, StarReader {

    /** 模板槽（左）。 */
    public static final int SLOT_INPUT = 0;
    /** 产物槽（右）。 */
    public static final int SLOT_OUTPUT = 1;
    /** 总槽数。 */
    public static final int SLOT_COUNT = 2;

    /** 复刻周期（tick）：20 = 1 秒一批，箭头看得清地走。 */
    public static final int COPY_INTERVAL = 20;

    /** 一批最多复刻的件数（与炎炉 MAX_BATCH 同量级）。 */
    public static final int MAX_BATCH = 64;

    // ------------------------------------------------------------------
    // 侧配（与其他工作方块同一套：关 / 自动输入 / 自动输出 / 输入输出）
    // ------------------------------------------------------------------

    /** 该面关闭：不进不出。 */
    public static final int MODE_OFF = 0;
    /** 该面自动输入：从相邻容器拉模板。 */
    public static final int MODE_INPUT = 1;
    /** 该面自动输出：把产物推给相邻容器。 */
    public static final int MODE_OUTPUT = 2;
    /** 该面输入输出：两者兼可。 */
    public static final int MODE_IO = 3;
    /** 模式档数。 */
    public static final int MODE_COUNT = 4;

    /**
     * 自动输入 / 输出的间隔（tick）。
     *
     * <p>★ 比炎炉的 1 tick 放宽到 4：复刻一批要 20 tick，产物最快也是 1 秒出一次，
     * 4 tick（0.2 秒）的搬运延迟肉眼不可见，却把「每 tick 扫六个面 × 相邻槽位」的
     * 常规开销砍到四分之一——弹出不卡顿的关键就是别让搬运逻辑抢主线程。</p>
     */
    public static final int TRANSFER_INTERVAL = 4;

    /** 六个面各自的模式（下标 = {@link Direction#ordinal()}）。 */
    private final byte[] sideModes = new byte[6];

    /** 头顶引导线的颜色：**蓝绿（科技色）**——南斗六星全供龙蛋时亮。 */
    private static final int GUIDE_TEAL = 0x14E8D6;

    /** 星象前置多久重算一次（tick）。 */
    private static final int STAR_CACHE_TICKS = 20;

    /** 星象前置：南斗六星全亮、六格**各供一枚龙蛋**才复刻（第一百五十三批改供物）。 */
    private boolean nandouEggs;
    private long starCacheTick = Long.MIN_VALUE;

    /** 模板 + 产物。 */
    private final ItemStackHandler items = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    /** 当前复刻进度（tick）。 */
    private int progress;

    @Override
    public int guideColor() {
        return GUIDE_TEAL;
    }

    /** 蓝绿线只在「南斗六星全供龙蛋」成立时亮。 */
    @Override
    public boolean guideActive(Constellation constellation) {
        return this.nandouEggs;
    }

    public XuanshuReplicatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.XUANSHU_REPLICATOR.get(), pos, state);
    }

    // ------------------------------------------------------------------
    // 物品栏
    // ------------------------------------------------------------------

    /** 供菜单（玩家交互）使用：无进出限制。 */
    public IItemHandler getItems() {
        return items;
    }

    /**
     * 供能力系统按面暴露给相邻自动化（漏斗 / 管道）：输入面只收<b>模板槽</b>、
     * 输出面只给<b>产物槽</b>、输入输出面两者兼可、关闭面全拒。
     */
    public IItemHandler getExternalItems(@Nullable Direction face) {
        return new SideHandler(face == null ? MODE_OFF : sideModes[face.ordinal()]);
    }

    // ------------------------------------------------------------------
    // 侧配状态
    // ------------------------------------------------------------------

    /** 循环切换某个面的模式：关闭 → 自动输入 → 自动输出 → 输入输出。 */
    public void cycleSide(@Nullable Direction face) {
        if (face == null) {
            return;
        }
        sideModes[face.ordinal()] = (byte) ((sideModes[face.ordinal()] + 1) % MODE_COUNT);
        setChanged();
    }

    /** 直接设置某个面的模式（GUI 侧配按钮用）。 */
    public void setSideMode(Direction face, int mode) {
        sideModes[face.ordinal()] = (byte) Math.floorMod(mode, MODE_COUNT);
        setChanged();
    }

    /** 读取某个面的模式。 */
    public int sideMode(Direction face) {
        return sideModes[face.ordinal()];
    }

    /**
     * 某一面外侧紧挨着的方块在登记表里的 id（空气 / 没世界 → 0）。
     *
     * <p>走 {@link net.minecraft.world.inventory.ContainerData} 下发给客户端：
     * 「传输配置」面板的六面按钮要直接把贴着的那台机器画在按钮里。
     * 与炎炉同款写法。</p>
     */
    public int neighbourBlockId(Direction face) {
        if (this.level == null) {
            return 0;
        }
        BlockState neighbourState = this.level.getBlockState(this.worldPosition.relative(face));
        return neighbourState.isAir() ? 0
                : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getId(neighbourState.getBlock());
    }

    private static boolean isIntakeMode(int mode) {
        return mode == MODE_INPUT || mode == MODE_IO;
    }

    private static boolean isOutputMode(int mode) {
        return mode == MODE_OUTPUT || mode == MODE_IO;
    }

    /** 按面裁剪过的外部物品栏。 */
    private final class SideHandler implements IItemHandler {

        private final int mode;

        private SideHandler(int mode) {
            this.mode = mode;
        }

        @Override
        public int getSlots() {
            return SLOT_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return items.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!isIntakeMode(this.mode) || slot != SLOT_INPUT || stack.isEmpty()) {
                return stack;
            }
            return items.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!isOutputMode(this.mode) || slot != SLOT_OUTPUT) {
                return ItemStack.EMPTY;
            }
            return items.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isIntakeMode(this.mode) && slot == SLOT_INPUT && items.isItemValid(slot, stack);
        }
    }

    // ------------------------------------------------------------------
    // 自动输入 / 输出（与炎炉同一套路，两格版）
    // ------------------------------------------------------------------

    /**
     * 从所有「自动输入 / 输入输出」面拉取模板——只收能进模板槽的，其余原样退回。
     *
     * <p>★ 搬运纪律（三查两退，绝不吞物）：先<b>模拟</b>能吃下多少，再从对方抽出恰好
     * 那么多，塞进来的余量<b>原路退回</b>对方容器；任何一步对不上就中止，绝不凭空消失。</p>
     */
    private void autoInput() {
        boolean moved = false;
        for (Direction dir : Direction.values()) {
            if (!isIntakeMode(sideModes[dir.ordinal()])) {
                continue;
            }
            IItemHandler neighbor = neighbor(dir);
            if (neighbor == null) {
                continue;
            }
            for (int slot = 0; slot < neighbor.getSlots(); slot++) {
                ItemStack stack = neighbor.getStackInSlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                ItemStack refused = items.insertItem(SLOT_INPUT, stack.copy(), true);
                int accepted = stack.getCount() - refused.getCount();
                if (accepted <= 0) {
                    continue;
                }
                ItemStack pulled = neighbor.extractItem(slot, accepted, false);
                if (pulled.isEmpty()) {
                    break;
                }
                ItemStack leftover = items.insertItem(SLOT_INPUT, pulled.copy(), false);
                if (!leftover.isEmpty()) {
                    neighbor.insertItem(slot, leftover, false);
                }
                moved = true;
            }
        }
        if (moved) {
            setChanged();
        }
    }

    /** 把产物槽推给所有「自动输出 / 输入输出」面。 */
    private void autoOutput() {
        ItemStack out = items.getStackInSlot(SLOT_OUTPUT);
        if (out.isEmpty()) {
            return;
        }
        boolean moved = false;
        for (Direction dir : Direction.values()) {
            if (out.isEmpty()) {
                break;
            }
            if (!isOutputMode(sideModes[dir.ordinal()])) {
                continue;
            }
            IItemHandler neighbor = neighbor(dir);
            if (neighbor == null) {
                continue;
            }
            ItemStack refused = ItemHandlerHelper.insertItemStacked(neighbor, out.copy(), true);
            int accepted = out.getCount() - refused.getCount();
            if (accepted <= 0) {
                continue;
            }
            ItemStack extracted = items.extractItem(SLOT_OUTPUT, accepted, false);
            ItemStack leftover = ItemHandlerHelper.insertItemStacked(neighbor, extracted, false);
            if (!leftover.isEmpty()) {
                // 对方中途变卦：余量塞回产物槽，绝不丢在地上一半
                items.insertItem(SLOT_OUTPUT, leftover, false);
            }
            moved = true;
        }
        if (moved) {
            setChanged();
        }
    }

    /** 取相邻方块的物品栏（要它朝向本方的那一面）。 */
    @Nullable
    private IItemHandler neighbor(Direction dir) {
        if (level == null) {
            return null;
        }
        return level.getCapability(Capabilities.ItemHandler.BLOCK,
                worldPosition.relative(dir), dir.getOpposite());
    }

    // ------------------------------------------------------------------
    // 每 tick
    // ------------------------------------------------------------------

    @SuppressWarnings("unused")
    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  XuanshuReplicatorBlockEntity be) {
        if (level.getGameTime() % TRANSFER_INTERVAL == 0) {
            be.autoInput();
            be.autoOutput();
        }
        be.refreshStarGate();
        if (!be.nandouEggs) {
            // 星象前置未达成：不复刻，进度清零（紫色引导线熄灭）
            if (be.progress != 0) {
                be.progress = 0;
                be.setChanged();
            }
            return;
        }
        ItemStack input = be.items.getStackInSlot(SLOT_INPUT);
        int batch = input.isEmpty() ? 0 : be.planBatch(input);
        if (batch <= 0) {
            // 没活可干：进度清零（输出满 / 没模板）
            if (be.progress != 0) {
                be.progress = 0;
                be.setChanged();
            }
            return;
        }
        be.progress++;
        if (be.progress >= COPY_INTERVAL) {
            be.progress = 0;
            be.finishCopy(input, batch);
            be.emitEffects();
        }
        be.setChanged();
    }

    /** 重算星象前置（节流）；只读一个布尔，复刻逻辑直接用。 */
    private void refreshStarGate() {
        if (this.level == null) {
            return;
        }
        long now = this.level.getGameTime();
        if (this.starCacheTick != Long.MIN_VALUE && now - this.starCacheTick < STAR_CACHE_TICKS) {
            return;
        }
        this.starCacheTick = now;
        boolean next = StarRite.nandouSixEggs(this.level, this.worldPosition);
        if (next != this.nandouEggs) {
            this.nandouEggs = next;
            if (!this.level.isClientSide) {
                this.syncToClient();     // 紫线亮不亮要下发，客户端才知道
            }
        }
    }

    /**
     * 这一批打算复刻几件：三个上限取最小——模板堆叠数、{@value #MAX_BATCH}、
     * 以及<b>产物槽还能装下几件</b>。
     */
    private int planBatch(ItemStack input) {
        int limit = Math.min(items.getSlotLimit(SLOT_OUTPUT), input.getMaxStackSize());
        ItemStack out = items.getStackInSlot(SLOT_OUTPUT);
        int space;
        if (out.isEmpty()) {
            space = limit;
        } else if (ItemStack.isSameItemSameComponents(out, input)) {
            space = limit - out.getCount();
        } else {
            return 0;   // 产物槽压着别的东西：等取走
        }
        return Math.min(Math.min(input.getCount(), MAX_BATCH), Math.max(0, space));
    }

    /**
     * 一批复刻成：整份深拷贝模板组件，产出一次进产物槽。
     *
     * <p>★ {@code input.copy()} 是<b>新造</b>的 ItemStack——组件图随拷贝落地，
     * 模板槽与产物槽<b>绝不共享同一个实例</b>（见类注释「数据安全」一节）。</p>
     */
    private void finishCopy(ItemStack input, int batch) {
        ItemStack copy = input.copy();
        copy.setCount(batch);
        ItemStack out = items.getStackInSlot(SLOT_OUTPUT);
        items.setStackInSlot(SLOT_OUTPUT, out.isEmpty()
                ? copy
                : out.copyWithCount(out.getCount() + batch));
    }

    /** 复刻完成的特效：一圈传送门粒子 + 一记轻钟。 */
    private void emitEffects() {
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.PORTAL,
                    worldPosition.getX() + 0.5D, worldPosition.getY() + 1.0D, worldPosition.getZ() + 0.5D,
                    8, 0.3D, 0.2D, 0.3D, 0.05D);
            server.sendParticles(ParticleTypes.END_ROD,
                    worldPosition.getX() + 0.5D, worldPosition.getY() + 0.6D, worldPosition.getZ() + 0.5D,
                    2, 0.15D, 0.1D, 0.15D, 0.01D);
            level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.BLOCKS, 0.5F, 1.4F);
        }
    }

    // ------------------------------------------------------------------
    // GUI 读取
    // ------------------------------------------------------------------

    /** 当前复刻进度（tick，GUI 同步用）。 */
    public int getProgress() {
        return progress;
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
        tag.putInt("Progress", progress);
        tag.putBoolean("NandouEggs", this.nandouEggs);
        tag.putByteArray("SideModes", this.sideModes);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Items")) {
            items.deserializeNBT(registries, tag.getCompound("Items"));
        }
        progress = tag.getInt("Progress");
        this.nandouEggs = tag.getBoolean("NandouEggs");
        byte[] modes = tag.getByteArray("SideModes");
        for (int i = 0; i < this.sideModes.length && i < modes.length; i++) {
            this.sideModes[i] = (byte) Math.floorMod(modes[i], MODE_COUNT);
        }
    }

    /** 状态一变就推给客户端（紫色引导线的亮灭靠它）。 */
    public void syncToClient() {
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(),
                    this.getBlockState(), Block.UPDATE_ALL);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ------------------------------------------------------------------
    // 掉落与 MenuProvider
    // ------------------------------------------------------------------

    /**
     * 被挖 / 炸 / 活塞推时把两格内容<b>散出来</b>（内容不会自动掉落，
     * 原版只会掉方块自己；创造模式同样生效——见炎炉同款注释）。
     *
     * <p>★ 掉出去的每一份都再 {@code copy()} 一遍：槽里的引用绝不直接交给世界。</p>
     */
    public void dropContents() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        for (int slot = 0; slot < items.getSlots(); slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(this.level, this.worldPosition.getX(),
                        this.worldPosition.getY(), this.worldPosition.getZ(), stack.copy());
            }
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.skydeitymysticpivot.xuanshu_replicator");
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new XuanshuReplicatorMenu(id, inv, this);
    }
}
