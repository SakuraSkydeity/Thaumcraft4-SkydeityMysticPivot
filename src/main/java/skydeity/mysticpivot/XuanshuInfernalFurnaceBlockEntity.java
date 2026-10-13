package skydeity.mysticpivot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;

/**
 * 玄枢炎炉：本体「炼狱熔炉」的单方块版本。
 *
 * <h2>三个并行加工位（「三炉同烧」）</h2>
 * <p>炉膛里有 <b>3 个</b>互相独立的熔炼位：各放各的料、各走各的进度条，
 * 一炉最多一次消化 {@value #MAX_BATCH} 件（「上限 64 个」），耗时 {@value #SMELT_INTERVAL} tick
 * ——<b>即 1 tick 一炉</b>（第 47 批由 20 压到 1，跑满「一炉 64 件、每 tick 一炉」的最大速度）。
 * <b>燃料只有一格</b>——一份燃料点起来，三个格子同时在烧（见 {@link #tickSmelting}）。</p>
 *
 * <p><b>配方表有两张</b>：先查本模组专属的 {@code skydeitymysticpivot:xuanshu_smelting}
 * （碎片、玄枢八卦之盐这类高阶烧炼只此一处可成，产出双倍），查不到再回落到原版
 * {@code minecraft:smelting}——普通人也能拿它当熔炉使。</p>
 *
 * <h2>六面自动化（Mekanism 式侧配）</h2>
 * <p>六个面各自可设四档：{@link #MODE_OFF 关闭} → {@link #MODE_INPUT 自动输入} →
 * {@link #MODE_OUTPUT 自动输出} → {@link #MODE_IO 输入输出}（GUI 侧配按钮循环切换，
 * 可随意多个面同时输入或输出）。</p>
 * <ul>
 *   <li><b>自动输入面</b>：每 {@value #TRANSFER_INTERVAL} tick 从相邻容器拉取物品，只塞进
 *       三个输入槽与燃料槽（<b>绝不塞输出槽</b>），塞不下的原样留在对方容器里；</li>
 *   <li><b>自动输出面</b>：每 {@value #TRANSFER_INTERVAL} tick 把三个输出槽推给相邻容器，
 *       对方只吃下一部分时余量退回输出槽；</li>
 *   <li><b>输入输出面</b>（紫色）：两面兼收——既从相邻容器拉取原料，也把产物推向它；</li>
 *   <li>配置随存档保存；出厂默认 顶面 = 自动输入、底面 = 自动输出。</li>
 * </ul>
 *
 * <h2>入料必查配方（铁律）</h2>
 * <p>自动输入（以及管道从输入 / 输入输出面塞进来的东西）<b>一律先查配方</b>：
 * <b>既烧不出东西、又不能当燃料的，一律拒收</b>——绝不让废物占住输入槽与燃料槽。</p>
 *
 * <h2>精确输入（Mekanism 的 strict input）</h2>
 * <p>在上面的配方校验之上再收一道：{@link #hasStrictInput()} 为真时，自动输入<b>只拉能烧炼的原料</b>，
 * 燃料必须玩家自己放（燃料槽不再接受自动送入）。关闭（出厂默认）时燃料也照拉。</p>
 */
public class XuanshuInfernalFurnaceBlockEntity extends BlockEntity implements MenuProvider {

    /** 并行加工位数：三炉同烧。 */
    public static final int LANES = 3;

    /** 输入槽下标 = 0,1,2；燃料槽 = 3；输出槽下标 = 4,5,6。 */
    public static final int SLOT_INPUT_BASE = 0;
    public static final int SLOT_FUEL = 3;
    public static final int SLOT_OUTPUT_BASE = 4;
    public static final int SLOT_COUNT = LANES * 2 + 1;

    /** 第 {@code lane} 个熔炼位的输入槽下标。 */
    public static int inputSlot(int lane) {
        return SLOT_INPUT_BASE + lane;
    }

    /** 第 {@code lane} 个熔炼位的输出槽下标。 */
    public static int outputSlot(int lane) {
        return SLOT_OUTPUT_BASE + lane;
    }

    /** 一炉最多一次消化的件数：「上限 64 个」。 */
    public static final int MAX_BATCH = 64;

    /** 熔炼周期（tick）：★ 1 tick——一炉 64 件、每 tick 一炉，即本机最大速度（第 47 批由 20 改到此值）。 */
    public static final int SMELT_INTERVAL = 1;

    /** 自动输入 / 输出的搬运周期（tick）：★ 1——每 tick 一轮，跟上 1 tick 一炉的炉速。 */
    public static final int TRANSFER_INTERVAL = 1;

    /** 面模式。 */
    public static final int MODE_OFF = 0;
    public static final int MODE_INPUT = 1;
    public static final int MODE_OUTPUT = 2;
    /** 输入输出：输入与输出兼收（拉原料的同时把产物推给对方）。 */
    public static final int MODE_IO = 3;
    /** 面模式档数（循环切换用）。 */
    public static final int MODE_COUNT = 4;

    /** 玩家与熔炼逻辑共用的内部物品栏（无进出限制）。 */
    private ItemStackHandler items = new ItemStackHandler(SLOT_COUNT);

    /** 六个面的模式，下标 = {@link Direction#ordinal()}。 */
    private final byte[] sideModes = new byte[6];

    /** 精确输入（Mekanism 的 strict input）。 */
    private boolean strictInput;

    /** 当前燃烧剩余（tick）。一份燃料点起来，三个格子一起用。 */
    private int litRemaining;
    /** 本次燃烧的总时长（tick），供进度条换算。 */
    private int litTotal;
    /** 三个熔炼位各自的进度（tick）。 */
    private final int[] progress = new int[LANES];

    public XuanshuInfernalFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.XUANSHU_INFERNAL_FURNACE.get(), pos, state);
        // 出厂默认：顶面自动输入、底面自动输出（地狱熔炉的进料/出料口）
        sideModes[Direction.UP.ordinal()] = MODE_INPUT;
        sideModes[Direction.DOWN.ordinal()] = MODE_OUTPUT;
    }

    // ------------------------------------------------------------------
    // 物品栏
    // ------------------------------------------------------------------

    /** 供菜单（玩家交互）使用：无进出限制。 */
    public IItemHandler getItems() {
        return items;
    }

    /**
     * 供能力系统按面暴露给相邻自动化：可访问的槽位由该面的模式决定
     * （关闭 = 全拒，输入 = 只可塞输入/燃料槽，输出 = 只可抽输出槽，输入输出 = 两者兼可）。
     */
    public IItemHandler getExternalItems(@Nullable Direction face) {
        return new SideHandler(face == null ? MODE_OFF : sideModes[face.ordinal()]);
    }

    /** 这个槽是不是三个输入槽之一。 */
    private static boolean isInputSlot(int slot) {
        return slot >= SLOT_INPUT_BASE && slot < SLOT_INPUT_BASE + LANES;
    }

    /** 这个槽是不是三个输出槽之一。 */
    private static boolean isOutputSlot(int slot) {
        return slot >= SLOT_OUTPUT_BASE && slot < SLOT_OUTPUT_BASE + LANES;
    }

    /** 某个面是否为可输入槽位（三个输入槽或燃料槽）。 */
    private static boolean isIntakeSlot(int slot) {
        return isInputSlot(slot) || slot == SLOT_FUEL;
    }

    /** 该模式是否允许外部往里面塞（自动输入、输入输出）。 */
    public static boolean isIntakeMode(int mode) {
        return mode == MODE_INPUT || mode == MODE_IO;
    }

    /** 该模式是否允许外部从产物槽抽出（自动输出、输入输出）。 */
    public static boolean isOutputMode(int mode) {
        return mode == MODE_OUTPUT || mode == MODE_IO;
    }

    /**
     * 该格愿不愿意接收外面送来的这一堆——<b>必查配方</b>：
     * 既烧不出东西、又不能当燃料的一律拒收；能烧的进输入槽，能当燃料的进燃料槽。
     * 精确输入开启时更严：燃料槽不再接受外部送入（燃料须玩家自己放）。
     */
    private boolean acceptsExternal(int slot, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (isInputSlot(slot)) {
            return isSmeltable(stack);
        }
        if (slot == SLOT_FUEL) {
            return !strictInput && isFuel(stack);
        }
        return false;
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
            if (!isIntakeMode(mode) || !isIntakeSlot(slot) || !acceptsExternal(slot, stack)) {
                return stack;
            }
            return items.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (!isOutputMode(mode) || !isOutputSlot(slot)) {
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
            return isIntakeMode(mode) && isIntakeSlot(slot)
                    && acceptsExternal(slot, stack) && items.isItemValid(slot, stack);
        }
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

    /** 是否开启精确输入。 */
    public boolean hasStrictInput() {
        return strictInput;
    }

    /** 设置 / 切换精确输入。 */
    public void setStrictInput(boolean value) {
        if (strictInput != value) {
            strictInput = value;
            setChanged();
        }
    }

    /** 切换精确输入，返回新值。 */
    public boolean toggleStrictInput() {
        setStrictInput(!strictInput);
        return strictInput;
    }

    /**
     * 某一面外侧紧挨着的方块在登记表里的 id（空气 / 没世界 → 0）。
     *
     * <p>走 {@link net.minecraft.world.inventory.ContainerData} 下发给客户端：
     * GUI 上那六个面按钮要像 Mekanism 一样<b>直接把贴着的那台机器画在按钮里</b>，
     * 而客户端拿不到服务端的世界快照，所以每个网络刷新周期读一次、只传一个 int。</p>
     */
    public int neighbourBlockId(Direction face) {
        if (this.level == null) {
            return 0;
        }
        BlockState state = this.level.getBlockState(this.worldPosition.relative(face));
        return state.isAir() ? 0 : BuiltInRegistries.BLOCK.getId(state.getBlock());
    }

    // ------------------------------------------------------------------
    // 客户端渲染 / GUI 读取
    // ------------------------------------------------------------------

    /** 当前是否在燃烧（客户端渲染光效可用）。 */
    public boolean isLit() {
        return litRemaining > 0;
    }

    /** 第 {@code lane} 个熔炼位的进度（tick，GUI 同步用）。 */
    public int getProgress(int lane) {
        return progress[lane];
    }

    /** 剩余燃烧（tick，GUI 同步用）。 */
    public int getLitRemaining() {
        return litRemaining;
    }

    /** 本轮燃烧总时长（tick，GUI 同步用）。 */
    public int getLitTotal() {
        return litTotal;
    }

    // ------------------------------------------------------------------
    // 每 tick
    // ------------------------------------------------------------------

    @SuppressWarnings("unused")
    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  XuanshuInfernalFurnaceBlockEntity be) {
        if (level.getGameTime() % TRANSFER_INTERVAL == 0) {
            be.autoInput();
            be.autoOutput();
        }
        be.tickSmelting();
    }

    /** 从所有「自动输入 / 输入输出」面拉取物品——必查配方，只收能烧或能当燃料的。 */
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
                // 先模拟：本方究竟能吃下多少
                ItemStack refused = insert(stack.copy(), true);
                int accepted = stack.getCount() - refused.getCount();
                if (accepted <= 0) {
                    continue;
                }
                ItemStack pulled = neighbor.extractItem(slot, accepted, false);
                if (pulled.isEmpty()) {
                    break;
                }
                ItemStack leftover = insert(pulled.copy(), false);
                if (!leftover.isEmpty()) {
                    // 理论之外的情况：原样退回对方容器，绝不凭空消失
                    neighbor.insertItem(slot, leftover, false);
                }
                moved = true;
            }
        }
        if (moved) {
            setChanged();
        }
    }

    /** 把三个输出槽推给所有「自动输出 / 输入输出」面。 */
    private void autoOutput() {
        boolean moved = false;
        for (Direction dir : Direction.values()) {
            if (!isOutputMode(sideModes[dir.ordinal()])) {
                continue;
            }
            IItemHandler neighbor = neighbor(dir);
            if (neighbor == null) {
                continue;
            }
            for (int lane = 0; lane < LANES; lane++) {
                ItemStack out = items.getStackInSlot(outputSlot(lane));
                if (out.isEmpty()) {
                    continue;
                }
                ItemStack refused = ItemHandlerHelper.insertItemStacked(neighbor, out.copy(), true);
                int accepted = out.getCount() - refused.getCount();
                if (accepted <= 0) {
                    continue;
                }
                ItemStack extracted = items.extractItem(outputSlot(lane), accepted, false);
                ItemStack leftover = ItemHandlerHelper.insertItemStacked(neighbor, extracted, false);
                if (!leftover.isEmpty()) {
                    items.insertItem(outputSlot(lane), leftover, false);
                }
                moved = true;
            }
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

    /**
     * 往自己肚子里塞东西——<b>永不碰输出槽</b>，且<b>必查配方</b>：
     * 能烧炼的进三个输入槽，能当燃料的进燃料槽；两样都不是的直接原样退回。
     * 精确输入开启时燃料槽不受自动送入。
     * 模拟（{@code simulate = true}）时不改任何状态，返回塞不下的余量。
     */
    private ItemStack insert(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = stack;
        if (isSmeltable(remainder)) {
            for (int lane = 0; lane < LANES && !remainder.isEmpty(); lane++) {
                remainder = insertInto(inputSlot(lane), remainder, simulate);
            }
        }
        if (!remainder.isEmpty() && !strictInput && isFuel(remainder)) {
            remainder = insertInto(SLOT_FUEL, remainder, simulate);
        }
        return remainder;
    }

    /** 往指定槽塞东西，返回塞不下的余量。 */
    private ItemStack insertInto(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack current = items.getStackInSlot(slot);
        int limit = Math.min(items.getSlotLimit(slot), stack.getMaxStackSize());
        int space;
        if (current.isEmpty()) {
            space = limit;
        } else if (ItemStack.isSameItemSameComponents(current, stack)) {
            space = limit - current.getCount();
        } else {
            return stack;
        }
        if (space <= 0) {
            return stack;
        }
        int moved = Math.min(space, stack.getCount());
        if (!simulate) {
            items.setStackInSlot(slot, current.isEmpty()
                    ? stack.copyWithCount(moved)
                    : current.copyWithCount(current.getCount() + moved));
        }
        return stack.copyWithCount(stack.getCount() - moved);
    }

    // ------------------------------------------------------------------
    // 熔炼（照抄原版熔炉，但三格并行、一炉最多 64 件）
    // ------------------------------------------------------------------

    /**
     * 每 tick 推进燃烧与熔炼——骨架照抄原版熔炉
     * {@code AbstractFurnaceBlockEntity.serverTick}（燃烧时长、点燃条件、进度推进/回退），
     * 两处不同：
     * <ol>
     *   <li><b>三个熔炼位并行</b>：各自一套进度，谁有料谁推进；</li>
     *   <li><b>一份燃料烧三格</b>：{@link #litRemaining} 是<b>机器级</b>的，
     *       点一次火，三个格子共用同一段燃烧时间（而不是每格各烧一份燃料）。</li>
     * </ol>
     */
    private void tickSmelting() {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean wasLit = litRemaining > 0;
        boolean dirty = false;

        // 1. 燃烧倒计时（与原版一致：无论是否可烧，火都在烧）
        if (litRemaining > 0) {
            litRemaining--;
        }

        ItemStack fuel = items.getStackInSlot(SLOT_FUEL);

        // 2. 三个格子各自算「这一炉打算烧几件」；只要有一格能烧，就算「有事可做」
        int[] plans = new int[LANES];
        ItemStack[] results = new ItemStack[LANES];
        boolean anyWork = false;
        for (int lane = 0; lane < LANES; lane++) {
            ItemStack input = items.getStackInSlot(inputSlot(lane));
            ItemStack result = input.isEmpty() ? ItemStack.EMPTY : smeltResult(input);
            results[lane] = result;
            plans[lane] = planBatch(lane, input, result);
            if (plans[lane] > 0) {
                anyWork = true;
            }
        }

        // 3. 未点燃但「有活 + 有燃料」→ 点燃并消耗 1 个燃料（含容器残余物，如桶）
        if (litRemaining <= 0 && anyWork && !fuel.isEmpty()) {
            int burn = burnDuration(fuel);
            if (burn > 0) {
                litRemaining = burn;
                litTotal = burn;
                dirty = true;
                items.extractItem(SLOT_FUEL, 1, false);
                ItemStack leftoverItem = fuel.getCraftingRemainingItem();
                if (items.getStackInSlot(SLOT_FUEL).isEmpty() && !leftoverItem.isEmpty()) {
                    items.setStackInSlot(SLOT_FUEL, leftoverItem);
                }
            }
        }

        // 4. 燃烧中且这一格可烧 → 推进进度；否则进度清零（原版同款，三个格子各算各的）
        if (litRemaining > 0) {
            for (int lane = 0; lane < LANES; lane++) {
                if (plans[lane] <= 0) {
                    progress[lane] = 0;
                    continue;
                }
                progress[lane]++;
                if (progress[lane] >= SMELT_INTERVAL) {
                    progress[lane] = 0;
                    finishLane(lane, results[lane], plans[lane]);
                    emitEffects();
                }
                dirty = true;
            }
        } else {
            for (int lane = 0; lane < LANES; lane++) {
                progress[lane] = 0;
            }
        }

        if (dirty || wasLit != (litRemaining > 0)) {
            setChanged();
        }
    }

    /**
     * 这一炉打算一次烧几件。
     *
     * <p>三个上限取最小：输入槽现有的件数、{@value #MAX_BATCH}（「上限 64 个」）、
     * 以及<b>该格的输出槽还能装下几份</b>。</p>
     */
    private int planBatch(int lane, ItemStack input, ItemStack result) {
        if (input.isEmpty() || result.isEmpty()) {
            return 0;
        }
        int per = Math.max(1, result.getCount());
        int batch = Math.min(input.getCount(), MAX_BATCH);
        return Math.min(batch, Math.max(0, outputSpace(lane, result) / per));
    }

    /** 该格输出槽还能再装几件这种东西。 */
    private int outputSpace(int lane, ItemStack result) {
        ItemStack out = items.getStackInSlot(outputSlot(lane));
        int limit = Math.min(items.getSlotLimit(outputSlot(lane)), result.getMaxStackSize());
        if (out.isEmpty()) {
            return limit;
        }
        if (ItemStack.isSameItemSameComponents(out, result)) {
            return Math.max(0, limit - out.getCount());
        }
        return 0;
    }

    /** 一炉烧成：扣掉 batch 件原料，产物一次进该格的输出槽。 */
    private void finishLane(int lane, ItemStack result, int batch) {
        if (result.isEmpty() || batch <= 0) {
            return;
        }
        items.extractItem(inputSlot(lane), batch, false);
        ItemStack produced = result.copyWithCount(result.getCount() * batch);
        ItemStack out = items.getStackInSlot(outputSlot(lane));
        if (out.isEmpty()) {
            items.setStackInSlot(outputSlot(lane), produced);
        } else {
            items.setStackInSlot(outputSlot(lane), out.copyWithCount(out.getCount() + produced.getCount()));
        }
    }

    /** 燃烧时长：与原版 {@code getBurnDuration} 同源（走 NeoForge 的燃料数据）。 */
    private int burnDuration(ItemStack fuel) {
        return fuel.isEmpty() ? 0 : fuel.getBurnTime(RecipeType.SMELTING);
    }

    /** 是不是能当燃料烧。 */
    private boolean isFuel(ItemStack stack) {
        return !stack.isEmpty() && burnDuration(stack) > 0;
    }

    /** 是不是能烧炼出东西（需要 level 才能查配方）。 */
    private boolean isSmeltable(ItemStack stack) {
        return !stack.isEmpty() && !smeltResult(stack).isEmpty();
    }

    /**
     * 查输入物品的熔炼产物；不可烧或客户端（无配方表）则返回空。
     *
     * <p><b>先查玄枢专属烧炼表</b>（{@link ModRecipes#XUANSHU_SMELTING_TYPE}：碎片、玄枢八卦之盐
     * 这类高阶烧炼只此一处可成，产出亦按双倍写死在配方里），查不到再回落到原版
     * {@code minecraft:smelting} 表——于是这台炉子既是「玄枢炎炉」，也仍是一台合格的普通熔炉。</p>
     */
    private ItemStack smeltResult(ItemStack input) {
        if (level == null || level.isClientSide || input.isEmpty()) {
            return ItemStack.EMPTY;
        }
        SingleRecipeInput recipeInput = new SingleRecipeInput(input);
        RecipeManager manager = level.getRecipeManager();

        // 一、玄枢专属烧炼表：碎片、玄枢八卦之盐这类高阶烧炼只此一处可成（产出亦按双倍写死在配方里）
        var special = manager.getRecipeFor(ModRecipes.XUANSHU_SMELTING_TYPE.get(), recipeInput, level);
        if (special.isPresent()) {
            ItemStack result = special.get().value().getResultItem(level.registryAccess());
            if (!result.isEmpty()) {
                return result;
            }
        }

        // 二、回落原版烧炼表：于是这台炉子也仍是一台合格的普通熔炉
        var vanilla = manager.getRecipeFor(RecipeType.SMELTING, recipeInput, level);
        if (vanilla.isPresent()) {
            ItemStack result = vanilla.get().value().getResultItem(level.registryAccess());
            if (!result.isEmpty()) {
                return result;
            }
        }
        return ItemStack.EMPTY;
    }

    private void emitEffects() {
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.FLAME,
                    worldPosition.getX() + 0.5D, worldPosition.getY() + 0.9D, worldPosition.getZ() + 0.5D,
                    4, 0.25D, 0.15D, 0.25D, 0.005D);
            server.sendParticles(ParticleTypes.SMOKE,
                    worldPosition.getX() + 0.5D, worldPosition.getY() + 1.0D, worldPosition.getZ() + 0.5D,
                    2, 0.2D, 0.1D, 0.2D, 0.01D);
            level.playSound(null, worldPosition, SoundEvents.FURNACE_FIRE_CRACKLE,
                    SoundSource.BLOCKS, 0.5F, 1.3F);
        }
    }

    // ------------------------------------------------------------------
    // 存档
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
        tag.putByteArray("SideModes", sideModes);
        tag.putBoolean("StrictInput", strictInput);
        tag.putInt("LitRemaining", litRemaining);
        tag.putInt("LitTotal", litTotal);
        tag.putIntArray("Progress", progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Items")) {
            items.deserializeNBT(registries, tag.getCompound("Items"));
        }
        byte[] modes = tag.getByteArray("SideModes");
        if (modes.length == 6) {
            System.arraycopy(modes, 0, sideModes, 0, 6);
        }
        strictInput = tag.getBoolean("StrictInput");
        litRemaining = tag.getInt("LitRemaining");
        litTotal = tag.getInt("LitTotal");
        int[] saved = tag.getIntArray("Progress");
        for (int lane = 0; lane < LANES; lane++) {
            progress[lane] = lane < saved.length ? saved[lane] : 0;
        }
    }

    // ------------------------------------------------------------------
    // 展示用名字
    // ------------------------------------------------------------------

    /**
     * 被挖掉时把七个格子（三输入 / 燃料 / 三产物）原样散出来。
     *
     * <p>方块实体的内容不会自动掉落——掉落表只掉方块自己，这一下必须显式写；
     * 由 {@code XuanshuInfernalFurnaceBlock#playerDestroy} 调用。</p>
     */
    public void dropContents() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        for (int slot = 0; slot < items.getSlots(); slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(this.level, this.worldPosition.getX(), this.worldPosition.getY(),
                        this.worldPosition.getZ(), stack.copy());
            }
        }
    }

    // ------------------------------------------------------------------
    // MenuProvider：右键打开 GUI
    // ------------------------------------------------------------------
    @Override
    public Component getDisplayName() {
        return Component.translatable("block.skydeitymysticpivot.xuanshu_infernal_furnace");
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new XuanshuInfernalFurnaceMenu(id, inv, this);
    }
}
