package skydeity.mysticpivot;

import dev.tc4port.thaumcraft.api.aspect.AspectAmounts;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.aspect.AspectQueryApi;
import dev.tc4port.thaumcraft.api.essentia.EssentiaApi;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 玄枢炼金炉：<b>把物品烧成源质，再送进四周的原质管</b>。
 *
 * <p>与玄枢坩埚正好相反——坩埚「吸要素、做物品」，这台「烧物品、出要素」。</p>
 *
 * <h2>三个并行加工位</h2>
 * <p>炉膛里 <b>3 个</b>独立的烧炼位：各放各的料、各走各的进度条，可以同时烧三种东西；
 * 一炉最多一次消化 {@value #MAX_BATCH} 件（「上限 64 个」），耗时 {@value #PROCESS_TICKS} tick
 * ——<b>即 1 tick 一炉</b>（第 47 批把 {@code PROCESS_TICKS} 由 20 压到 1，跑满「一炉 64 件、
 * 每 tick 一炉」的最大速度）。</p>
 *
 * <h2>炉下须有薪火</h2>
 * <p>与炎炉同源：燃料槽在炉腹下方，一份燃料点着后<b>三膛共用同一段火</b>
 * （{@link #litRemaining} 是机器级的）。<b>没火则一格也烧不动</b>——料放进去只会停在原地，
 * 直到有人添了薪、火起，才继续熬。</p>
 *
 * <h2>要素从哪来、又到哪去</h2>
 * <ol>
 *   <li><b>来源 = 物品自身的要素</b>：走本体公开的 {@link AspectQueryApi#item(net.minecraft.world.item.ItemStack)}
 *       （即数据包里的 {@code object_aspects}，与本体炼金炉同一条路）。
 *       有多少要素就烧出多少，再按本炉件数<b>线性放大</b>（64 件铁锭就是 64 份铁之要素）。
 *       ★ <b>别用 {@code AspectApi#getItemAspects}</b>——那读的是「物品自己存着的要素」，
 *       原版物品全都为空表，一炉也烧不出来（见 {@link #aspectsOf}）。</li>
 *   <li><b>去向 = 5×5×3 范围内的要素端点</b>：以本方块为中心，水平 ±{@value #RANGE_XZ}、
 *       竖直 ±{@value #RANGE_Y}（= 5×5×3），逐个找实现 {@link EssentiaTransport} 的邻居
 *       （原质管、原质罐、储液池……），用 {@link EssentiaApi#add} 把要素<b>推</b>进去。
 *       管收到后会照 TC4 的规矩自己送走。</li>
 * </ol>
 * <p><b>输出的方向不用配</b>：这台机器没有「输出面」这一说，要素一律自动送往范围内的端点。
 * 至于「从端点的哪一面塞进去」，见 {@link #inputFaceFor}——那一面由<b>端点自己</b>说了算，
 * 不是由我们相对它的方位决定。</p>
 * <p>目标列表每 {@value #REFRESH_TARGETS} tick 重扫一遍（机器会被拆、管会被接），
 * 每 {@value #TRANSFER_INTERVAL} tick 推一轮，一轮最多 {@value #PUSH_BUDGET} 份。
 * 推不出去的要素<b>留在炉里</b>等着，绝不凭空消失。</p>
 *
 * <h2>动画</h2>
 * <p>每成功推出一份要素就记一条「从炉心奔向那根管」的轨迹
 * （{@link #trailNonce} 是批号，客户端见批号变了就把轨迹续到 15 tick），
 * 客户端用本体的 {@code ThaumcraftClientEffectsApi#spawnEssentiaTrail} 画出来
 * ——与注魔矩阵那条「要素流」是同一条粒子。</p>
 *
 * <h2>六面自动化</h2>
 * <p>这台机器只有<b>进料</b>没有物品产出（产物是源质，走管子出去），
 * 所以每个面只有两档：{@link #MODE_OFF 关闭} / {@link #MODE_INPUT 自动输入}。
 * 自动输入<b>只收烧得出要素的东西与能当薪的燃料</b>：前者进三膛，后者进炉下的燃料槽。</p>
 */
public class XuanshuAlchemyFurnaceBlockEntity extends BlockEntity implements MenuProvider {

    /** 并行加工位数。 */
    public static final int LANES = 3;

    /** 槽位：0,1,2 = 三个进料槽，3 = 炉下的燃料槽（没有物品产出槽，要素走管子出去）。 */
    public static final int SLOT_FUEL = LANES;
    public static final int SLOT_COUNT = LANES + 1;

    /** 一炉最多一次消化的件数：「上限 64 个」。 */
    public static final int MAX_BATCH = 64;

    /** 烧一炉的时长（tick）：★ 1 tick——一炉 64 件、每 tick 一炉，即本机最大速度（第 47 批由 20 改到此值）。 */
    public static final int PROCESS_TICKS = 1;

    /** 向外推送要素的周期（tick）：★ 1——与炉速同步，要素出炉即走，不再被推送节奏拖住。 */
    public static final int TRANSFER_INTERVAL = 1;

    /** 一轮里最多推出几份要素。 */
    private static final int PUSH_BUDGET = 64;

    /** 推送范围：水平 ±2、竖直 ±1 ⇒ 以本方块为中心的 <b>5×5×3</b>。 */
    public static final int RANGE_XZ = 2, RANGE_Y = 1;

    /** 目标列表的重扫周期（tick）。 */
    private static final int REFRESH_TARGETS = 20;

    /** 一次最多画几条轨迹。 */
    private static final int MAX_TRAILS = 8;

    /** 面模式：这台机器只有两档。 */
    public static final int MODE_OFF = 0;
    public static final int MODE_INPUT = 1;
    public static final int MODE_COUNT = 2;

    /** 一个烧炼位的状态。 */
    private static final class Lane {

        /** 这一炉将产出的要素总量（= 物品要素 × 本炉件数）。 */
        final Map<AspectId, Integer> planned = new LinkedHashMap<>();

        /** 还没送出去的要素（熬完那一刻填上，边送边减）。 */
        final Map<AspectId, Integer> output = new LinkedHashMap<>();

        /** 本炉进度（tick）。 */
        int progress;

        /** 是否正在烧 / 正在排要素。 */
        boolean working;

        /** 本炉锁定的件数。 */
        int batch;

        void reset() {
            planned.clear();
            output.clear();
            progress = 0;
            working = false;
            batch = 0;
        }
    }

    /**
     * 玩家与机器逻辑共用的内部物品栏（无进出限制）。
     */
    private ItemStackHandler items = new ItemStackHandler(SLOT_COUNT);

    /** 三个并行烧炼位。 */
    private final Lane[] lanes = {new Lane(), new Lane(), new Lane()};

    /** 六个面的模式，下标 = {@link Direction#ordinal()}。 */
    private final byte[] sideModes = new byte[6];

    /** 炉火余烬（tick）：一份燃料点着后三膛共用这一段（照玄枢炎炉）。 */
    private int litRemaining;

    /** 本次燃料总共能烧多久（tick），只用来画火苗的进度。 */
    private int litTotal;

    /** 5×5×3 里可推送的端点：位置 → 该用哪一面塞进去。 */
    private final Map<BlockPos, Direction> targets = new LinkedHashMap<>();

    /** 下次重扫目标的游戏时刻。 */
    private long nextTargetScan;

    // ---- 动画（与玄枢坩埚同一套「源表 + 重复包续期 + 事件时刻余晖」） ----

    /**
     * 传出动画的「余晖」（tick）：<b>最后一次真正把要素送出去之后</b>，轨迹再续这么久就收摊。
     *
     * <p>★ <b>这里踩过的坑（第 35 批在坩埚上、本批在炼金炉上）</b>：原来的续期条件是
     * 「{@code !trailTargets.isEmpty() &amp;&amp; time % TRANSFER_INTERVAL == 0}」——而
     * {@code trailTargets} 是<b>只增不减</b>的（只有读档才清），于是<b>送过一次之后就恒为真</b>：
     * 服务端每 5 tick 永远把批号 +1，客户端于是永远把轨迹表续满 —— 表现就是
     * <b>一条「往外送要素」的动画挂在那儿没完没了</b>。</p>
     *
     * <p>正确的判据是「<b>上次真送出东西到现在过了多久</b>」：{@link #lastPushTick} 记时刻，
     * 超过 {@value #TRAIL_LINGER} 就停止续期并清表。配上客户端那 10 tick 的尾巴，
     * 从最后一份要素送出去到动画彻底消失，正好是 <b>1 秒</b>。</p>
     */
    private static final int TRAIL_LINGER = 10;

    private int trailNonce;

    /**
     * 最近一次<b>真正送出要素</b>的游戏时刻；{@code -1} = 还没送过。
     *
     * <p>轨迹续期只看它（见 {@link #TRAIL_LINGER}）——不再看「目标表空不空」，
     * 因为那张表是只增不减的，拿它当条件会让动画永远停不下来。</p>
     */
    private long lastPushTick = -1;

    private final List<BlockPos> trailTargets = new ArrayList<>(MAX_TRAILS);
    private final List<Integer> trailColors = new ArrayList<>(MAX_TRAILS);

    public XuanshuAlchemyFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.XUANSHU_ALCHEMY_FURNACE.get(), pos, state);
        // 出厂默认：顶面自动输入（料从上头丢进去）
        sideModes[Direction.UP.ordinal()] = MODE_INPUT;
    }

    // ------------------------------------------------------------------
    // 物品栏
    // ------------------------------------------------------------------

    /** 供菜单（玩家交互）使用：无进出限制。 */
    public IItemHandler getItems() {
        return items;
    }

    /** 供能力系统按面暴露给相邻自动化。 */
    public IItemHandler getExternalItems(@Nullable Direction face) {
        return new SideHandler(face == null ? MODE_OFF : sideModes[face.ordinal()]);
    }

    public static boolean isIntakeMode(int mode) {
        return mode == MODE_INPUT;
    }

    /**
     * 这一格愿不愿意收外面送来的这一堆。
     *
     * <p>进料槽<b>必查要素</b>（烧不出任何要素的东西如纯石子一律拒收，省得把炉子当垃圾桶）；
     * 燃料槽只认能烧的。</p>
     */
    private boolean acceptsExternal(int slot, ItemStack stack) {
        if (slot == SLOT_FUEL) {
            return isFuel(stack);
        }
        return slot >= 0 && slot < LANES && isAlchemical(stack);
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
            if (!isIntakeMode(mode) || !acceptsExternal(slot, stack)) {
                return stack;
            }
            return items.insertItem(slot, stack, simulate);
        }

        /** 炉子不往外给物品：产物是源质。 */
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return items.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return isIntakeMode(mode) && acceptsExternal(slot, stack) && items.isItemValid(slot, stack);
        }
    }

    // ------------------------------------------------------------------
    // 侧配状态
    // ------------------------------------------------------------------

    public void cycleSide(@Nullable Direction face) {
        if (face == null) {
            return;
        }
        sideModes[face.ordinal()] = (byte) ((sideModes[face.ordinal()] + 1) % MODE_COUNT);
        setChanged();
    }

    public void setSideMode(Direction face, int mode) {
        sideModes[face.ordinal()] = (byte) Math.floorMod(mode, MODE_COUNT);
        setChanged();
    }

    public int sideMode(Direction face) {
        return sideModes[face.ordinal()];
    }

    /** 某一面外侧紧挨着的方块在登记表里的 id（空气 / 没世界 → 0）；界面按钮上画它。 */
    public int neighbourBlockId(Direction face) {
        if (this.level == null) {
            return 0;
        }
        BlockState state = this.level.getBlockState(this.worldPosition.relative(face));
        return state.isAir() ? 0 : BuiltInRegistries.BLOCK.getId(state.getBlock());
    }

    // ------------------------------------------------------------------
    // GUI 读取
    // ------------------------------------------------------------------

    /** 第 {@code lane} 个烧炼位的进度（tick）。 */
    public int getProgress(int lane) {
        return lanes[lane].progress;
    }

    /** 炉火还在不在（界面画火苗用）。 */
    public boolean isLit() {
        return litRemaining > 0;
    }

    /** 炉火余烬（tick）。 */
    public int getLitRemaining() {
        return litRemaining;
    }

    /** 本次燃料能烧多久（tick）。 */
    public int getLitTotal() {
        return litTotal;
    }

    /** 第 {@code lane} 个烧炼位这一炉将产出的要素（界面画图标用）。 */
    public Map<AspectId, Integer> plannedAspects(int lane) {
        return Collections.unmodifiableMap(lanes[lane].planned);
    }

    /** 第 {@code lane} 个烧炼位还没送出去的要素。 */
    public Map<AspectId, Integer> pendingAspects(int lane) {
        return Collections.unmodifiableMap(lanes[lane].output);
    }

    // ------------------------------------------------------------------
    // 每 tick
    // ------------------------------------------------------------------

    @SuppressWarnings("unused")
    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  XuanshuAlchemyFurnaceBlockEntity be) {
        long time = level.getGameTime();
        if (time % TRANSFER_INTERVAL == 0) {
            be.autoInput();
        }
        be.tickFurnace(time);
    }

    /** 客户端每 tick：把「要素送进管子」表现成一条条奔出去的粒子流。 */
    @SuppressWarnings("unused")
    public static void clientTick(Level level, BlockPos pos, XuanshuAlchemyFurnaceBlockEntity be) {
        XuanshuAlchemyEffects.clientTick(level, pos, be);
    }

    private void tickFurnace(long time) {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean wasLit = litRemaining > 0;

        // 1. 燃烧倒计时（与原版熔炉一致：无论三膛是否可烧，火都在烧）
        if (litRemaining > 0) {
            litRemaining--;
        }

        // 2. 三膛里只要有料可烧，就算「有事可做」
        boolean anyWork = false;
        for (int lane = 0; lane < LANES; lane++) {
            ItemStack input = items.getStackInSlot(lane);
            if (!aspectsOf(input).isEmpty() && input.getCount() > 0) {
                anyWork = true;
                break;
            }
        }

        // 3. 未点着但「有活 + 炉下有薪」→ 点着，并吃掉一个燃料（含桶之类的容器残余）
        if (litRemaining <= 0 && anyWork) {
            ItemStack fuel = items.getStackInSlot(SLOT_FUEL);
            int burn = burnDuration(fuel);
            if (burn > 0) {
                litRemaining = burn;
                litTotal = burn;
                ItemStack leftoverItem = fuel.getCraftingRemainingItem();
                items.extractItem(SLOT_FUEL, 1, false);
                if (items.getStackInSlot(SLOT_FUEL).isEmpty() && !leftoverItem.isEmpty()) {
                    items.setStackInSlot(SLOT_FUEL, leftoverItem);
                }
            }
        }

        // 4. 推要素（与火无关：已经熬出来的东西要照送）
        for (int lane = 0; lane < LANES; lane++) {
            tickLane(lane, time, litRemaining > 0);
        }

        // 5. 轨迹续期：只在「不久前真的送出过要素」时才续。
        // ★ 别用 trailTargets.isEmpty() 当条件——那张表只增不减，会让动画永远停不下来（见 TRAIL_LINGER）。
        long sincePush = lastPushTick < 0 ? Long.MAX_VALUE : time - lastPushTick;
        if (sincePush > TRAIL_LINGER) {
            // 停送超过余晖时间：把目标表清掉并下发，客户端那一串粒子到此为止
            if (!trailTargets.isEmpty()) {
                trailTargets.clear();
                trailColors.clear();
                syncToClient();
            }
        } else if (time % TRANSFER_INTERVAL == 0) {
            bumpTrail();
        }

        if (wasLit != (litRemaining > 0)) {
            setChanged();
            syncToClient();
        }
    }

    /**
     * 推进第 {@code lane} 个烧炼位：熬 → 出货 → 排要素。
     *
     * <p>★ {@code lit} 为假时<b>一格也烧不动</b>：没开工的不开，开了工的原地停着
     * （进度既不推进、也不回退），等火再起接着熬。排要素那一段不受火影响。</p>
     */
    private void tickLane(int lane, long time, boolean lit) {
        Lane state = lanes[lane];
        ItemStack input = items.getStackInSlot(lane);

        if (state.working) {
            // 熬制阶段
            if (state.progress < PROCESS_TICKS) {
                if (!lit) {
                    return;
                }
                state.progress++;
                if (state.progress >= PROCESS_TICKS) {
                    commit(lane, state, input);
                }
                setChanged();
                return;
            }
            // 排要素阶段：每 TRANSFER_INTERVAL tick 推一轮
            if (time % TRANSFER_INTERVAL == 0 && !state.output.isEmpty()) {
                pushLane(state);
            }
            if (state.output.isEmpty()) {
                state.reset();
                syncToClient();
            }
            return;
        }

        Map<AspectId, Integer> aspects = aspectsOf(input);
        int batch = aspects.isEmpty() ? 0 : Math.min(input.getCount(), MAX_BATCH);
        if (batch <= 0) {
            if (!state.planned.isEmpty()) {
                state.planned.clear();
                syncToClient();
            }
            return;
        }
        Map<AspectId, Integer> scaled = new LinkedHashMap<>();
        aspects.forEach((aspect, amount) -> scaled.put(aspect, amount * batch));
        if (!scaled.equals(state.planned)) {
            state.planned.clear();
            state.planned.putAll(scaled);
            syncToClient();
        }
        // ★ 没火就不开炉（料就静静躺在那儿，界面照旧预告这一炉能出什么）
        if (!lit) {
            return;
        }
        start(state, batch);
    }

    /** 燃料能烧多久（tick）：与原版熔炉同源，走 NeoForge 的燃料数据。 */
    private int burnDuration(ItemStack fuel) {
        return fuel.isEmpty() ? 0 : fuel.getBurnTime(RecipeType.SMELTING);
    }

    /** 是不是能当薪烧。 */
    private boolean isFuel(ItemStack stack) {
        return !stack.isEmpty() && burnDuration(stack) > 0;
    }

    /** 开炉。 */
    private void start(Lane state, int batch) {
        state.working = true;
        state.progress = 0;
        state.batch = batch;
        if (level instanceof ServerLevel server) {
            server.playSound(null, worldPosition, SoundEvents.BLAZE_BURN, SoundSource.BLOCKS, 0.5F, 0.9F);
        }
        syncToClient();
    }

    /** 熬满：扣掉触媒，把该出的要素填进待送队列。 */
    private void commit(int lane, Lane state, ItemStack input) {
        int take = Math.min(state.batch, input.getCount());
        Map<AspectId, Integer> aspects = aspectsOf(input);
        if (take <= 0 || aspects.isEmpty()) {
            state.reset();
            syncToClient();
            return;
        }
        items.extractItem(lane, take, false);
        Map<AspectId, Integer> produced = new LinkedHashMap<>();
        aspects.forEach((aspect, amount) -> produced.put(aspect, amount * take));
        state.planned.clear();
        state.planned.putAll(produced);
        state.output.clear();
        state.output.putAll(produced);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.ENCHANT,
                    worldPosition.getX() + 0.5D, worldPosition.getY() + 1.0D, worldPosition.getZ() + 0.5D,
                    8, 0.35D, 0.2D, 0.35D, 0.6D);
            server.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.BLOCKS, 0.7F, 1.4F);
        }
        syncToClient();
    }

    /** 把待送队列里的要素推给 5×5×3 内的端点（一轮最多 {@link #PUSH_BUDGET} 份）。 */
    private void pushLane(Lane state) {
        int budget = PUSH_BUDGET;
        Iterator<Map.Entry<AspectId, Integer>> iterator = state.output.entrySet().iterator();
        while (iterator.hasNext() && budget > 0) {
            Map.Entry<AspectId, Integer> entry = iterator.next();
            int want = Math.min(entry.getValue(), budget);
            int moved = distribute(entry.getKey(), want);
            if (moved <= 0) {
                continue;
            }
            budget -= moved;
            int left = entry.getValue() - moved;
            if (left <= 0) {
                iterator.remove();
            } else {
                entry.setValue(left);
            }
            setChanged();
        }
    }

    /**
     * 把 {@code aspect} 送出去最多 {@code want} 份，返回实际送出的份数。
     *
     * <p>目标表每 {@link #REFRESH_TARGETS} tick 重扫一次（表里存的「面」由
     * {@link #inputFaceFor} 问端点自己）；推送时逐个试，收多少算多少，
     * 直到把这批送完或把表跑完。</p>
     *
     * <p>★ 这里<b>不要</b>再拿 {@code isConnectable(face)} 预筛一遍：进来的面已经问过
     * {@code canInputFrom}，而 {@code EssentiaApi.add} 内部还会再确认一次；
     * 多这一道只会把「罐只认 UP」这类端点重新挡在门外（见 {@link #inputFaceFor}）。</p>
     */
    private int distribute(AspectId aspect, int want) {
        refreshTargetsIfDue();
        int moved = 0;
        for (Map.Entry<BlockPos, Direction> entry : targets.entrySet()) {
            if (moved >= want) {
                break;
            }
            BlockPos target = entry.getKey();
            Direction face = entry.getValue();
            if (!level.isLoaded(target)) {
                continue;
            }
            if (!(level.getBlockEntity(target) instanceof EssentiaTransport transport)) {
                continue;
            }
            int got = EssentiaApi.add(level, transport, aspect, want - moved, face,
                    EssentiaTransferMode.EXECUTE);
            if (got <= 0) {
                continue;
            }
            moved += got;
            recordTrail(target, aspect);
        }
        return moved;
    }

    /** 重扫 5×5×3 里的要素端点（管 / 罐 / 储液池……）。 */
    private void refreshTargetsIfDue() {
        long time = level.getGameTime();
        if (time < nextTargetScan) {
            return;
        }
        nextTargetScan = time + REFRESH_TARGETS;
        targets.clear();
        for (int dy = -RANGE_Y; dy <= RANGE_Y; dy++) {
            for (int dx = -RANGE_XZ; dx <= RANGE_XZ; dx++) {
                for (int dz = -RANGE_XZ; dz <= RANGE_XZ; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    BlockPos target = worldPosition.offset(dx, dy, dz);
                    if (!level.isLoaded(target)) {
                        continue;
                    }
                    if (!(level.getBlockEntity(target) instanceof EssentiaTransport transport)) {
                        continue;
                    }
                    Direction face = inputFaceFor(transport, dx, dy, dz);
                    if (face == null) {
                        continue;
                    }
                    targets.put(target.immutable(), face);
                }
            }
        }
    }

    /**
     * 从端点的<b>哪一面</b>把要素塞进去——这一面由端点自己说了算。
     *
     * <p>★ <b>这里踩过的坑（第 34 批）</b>：原先只拿「端点朝向我们的那一面」
     * （{@code Direction.getNearest(-dx, -dy, -dz)}）去问 {@code isConnectable}，
     * 于是<b>侧放、底放的原质罐一个都送不进去</b>——因为罐只认 {@code canInputFrom(UP)}
     * （往罐里灌要素，走的永远是罐顶那一面，魔像喂罐也是固定传 {@code UP}），
     * 而侧放时算出来的面是东/西/南/北，直接被筛掉，罐压根进不了目标表。</p>
     *
     * <p>正确的顺序：先试「端点朝向我们的那一面」（正经连着的那一侧，管最喜欢它），
     * 不通再把六个面逐个试一遍，取第一个 {@code canInputFrom} 为真的。
     * 都说不收就返回 {@code null}（这一格不是可送的目标）。</p>
     */
    @Nullable
    private static Direction inputFaceFor(EssentiaTransport transport, int dx, int dy, int dz) {
        Direction towardMachine = Direction.getNearest(-dx, -dy, -dz);
        if (transport.canInputFrom(towardMachine)) {
            return towardMachine;
        }
        for (Direction direction : Direction.values()) {
            if (direction != towardMachine && transport.canInputFrom(direction)) {
                return direction;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 物品 → 要素
    // ------------------------------------------------------------------

    /** 这件物品烧得出要素吗（本体公开的 {@code object_aspects} 查询）。 */
    public boolean isAlchemical(ItemStack stack) {
        return !aspectsOf(stack).isEmpty();
    }

    /** 这台炉子收不收这件东西：烧得出要素、或者能当薪。 */
    public boolean accepts(ItemStack stack) {
        return isAlchemical(stack) || isFuel(stack);
    }

    /**
     * 一件物品含有的要素（<b>每件</b>的量，不含件数）。
     *
     * <p>★ <b>这里踩过的坑（第 35 批，也是「炼金炉既不产也不出」的总病根）</b>：
     * 原来走的是 {@code AspectApi.getItemAspects(stack)} —— 那个接口读的是
     * <b>物品自己存在 ItemStack 上的要素</b>（要求该 Item 实现 {@code AspectItemView} 间谍口），
     * <b>完全不查数据包里的 {@code object_aspects}</b>。铁锭、煤炭这类原版物品
     * 一个都不是 {@code AspectItemView}，于是这里<b>恒返回空表</b>：
     * 三膛「没有可烧的料」⇒ 不点火、不推进、不出料 ⇒ 既<b>不产</b>也无从<b>输出</b>。</p>
     *
     * <p>正确的接口是 {@link AspectQueryApi#item(ItemStack)}：它内部走
     * {@code ObjectAspectResolver}，即<b>数据包 {@code object_aspects} 的正解</b>
     * （工具耐久换要素、附魔换要素、法杖 craft_cost 那一整套也都在这条路上）。
     * 本体自己的炼金炉就是这么写的——{@code AlchemyFurnaceBlockEntity#inputAspects()}
     * 用的正是 {@code AspectQueryApi.item(input).amounts()}，这里与之对齐。</p>
     *
     * <p>返回的是<b>单件</b>的量：件数由调用方按本炉批量线性放大，
     * 所以别用 {@link AspectQueryApi#stack(ItemStack)}（那个已经乘过件数了）。</p>
     */
    private Map<AspectId, Integer> aspectsOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return Map.of();
        }
        AspectAmounts amounts = AspectQueryApi.item(stack);
        return amounts == null ? Map.of() : amounts.amounts();
    }

    // ------------------------------------------------------------------
    // 自动化（只管进料）
    // ------------------------------------------------------------------

    /** 从所有「自动输入」面拉料——必查要素（或能当薪）。 */
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
                if (!isAlchemical(stack) && !isFuel(stack)) {
                    continue;
                }
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
                    neighbor.insertItem(slot, leftover, false);
                }
                moved = true;
            }
        }
        if (moved) {
            setChanged();
        }
    }

    /**
     * 把送上门的料分派下去：<b>烧得出要素的</b>进三个进料槽，<b>能当薪的</b>进炉下的燃料槽；
     * 两样都不是的原样退回。返回塞不下的余量。
     */
    private ItemStack insert(ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = stack;
        if (isAlchemical(remainder)) {
            for (int lane = 0; lane < LANES && !remainder.isEmpty(); lane++) {
                remainder = items.insertItem(lane, remainder, simulate);
            }
        }
        if (!remainder.isEmpty() && isFuel(remainder)) {
            remainder = items.insertItem(SLOT_FUEL, remainder, simulate);
        }
        return remainder;
    }

    @Nullable
    private IItemHandler neighbor(Direction dir) {
        if (level == null) {
            return null;
        }
        return level.getCapability(Capabilities.ItemHandler.BLOCK,
                worldPosition.relative(dir), dir.getOpposite());
    }

    /**
     * 玩家手持东西右键炉沿时的入口。
     *
     * @return 实际塞进去的个数，0 表示没收（既烧不出要素、又不能当薪）
     */
    public int insertByHand(ItemStack held, int amount) {
        if (amount <= 0) {
            return 0;
        }
        ItemStack remainder = insert(held.copyWithCount(amount), false);
        int moved = amount - remainder.getCount();
        if (moved > 0) {
            setChanged();
            syncToClient();
        }
        return moved;
    }

    /** 被挖掉时把炉里的料散出来。 */
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
    // 动画
    // ------------------------------------------------------------------

    /** 记一条「炉心 → 目标」的轨迹（同格只留一条，色号取该要素的色），并记下「刚送出去东西」的时刻。 */
    private void recordTrail(BlockPos target, AspectId aspect) {
        if (level != null) {
            lastPushTick = level.getGameTime();
        }
        BlockPos key = target.immutable();
        int color = XuanshuCrucibleRecipe.colorOf(aspect);
        for (int i = 0; i < trailTargets.size(); i++) {
            if (trailTargets.get(i).equals(key)) {
                trailColors.set(i, color);
                return;
            }
        }
        while (trailTargets.size() >= MAX_TRAILS) {
            trailTargets.remove(0);
            trailColors.remove(0);
        }
        trailTargets.add(key);
        trailColors.add(color);
    }

    private void bumpTrail() {
        trailNonce++;
        setChanged();
        syncToClient();
    }

    public int trailNonce() {
        return trailNonce;
    }

    public int trailCount() {
        return trailTargets.size();
    }

    public BlockPos trailTarget(int index) {
        return trailTargets.get(index);
    }

    public int trailColor(int index) {
        return trailColors.get(index);
    }

    // ------------------------------------------------------------------
    // 客户端同步
    // ------------------------------------------------------------------

    /** 内容和客户端有关时推一次；服务端专用。 */
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
    // 存档
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
        tag.putByteArray("SideModes", sideModes);
        tag.putInt("TrailNonce", trailNonce);
        tag.putInt("LitRemaining", litRemaining);
        tag.putInt("LitTotal", litTotal);
        for (int lane = 0; lane < LANES; lane++) {
            Lane state = lanes[lane];
            tag.putInt("Progress" + lane, state.progress);
            tag.putBoolean("Working" + lane, state.working);
            tag.putInt("Batch" + lane, state.batch);
            tag.put("Planned" + lane, writeAspects(state.planned));
            tag.put("Output" + lane, writeAspects(state.output));
        }
        long[] packed = new long[trailTargets.size()];
        for (int i = 0; i < packed.length; i++) {
            packed[i] = trailTargets.get(i).asLong();
        }
        tag.putLongArray("TrailTargets", packed);
        int[] colors = new int[trailColors.size()];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = trailColors.get(i);
        }
        tag.putIntArray("TrailColors", colors);
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
        trailNonce = tag.getInt("TrailNonce");
        litRemaining = tag.getInt("LitRemaining");
        litTotal = tag.getInt("LitTotal");
        for (int lane = 0; lane < LANES; lane++) {
            Lane state = lanes[lane];
            state.progress = tag.getInt("Progress" + lane);
            state.working = tag.getBoolean("Working" + lane);
            state.batch = tag.getInt("Batch" + lane);
            readAspects(tag.getCompound("Planned" + lane), state.planned);
            readAspects(tag.getCompound("Output" + lane), state.output);
        }
        trailTargets.clear();
        trailColors.clear();
        long[] packed = tag.getLongArray("TrailTargets");
        int[] colors = tag.getIntArray("TrailColors");
        for (int i = 0; i < packed.length && trailTargets.size() < MAX_TRAILS; i++) {
            trailTargets.add(BlockPos.of(packed[i]));
            trailColors.add(i < colors.length ? colors[i] : 0xFFFFFF);
        }
    }

    /** 要素表 → NBT（键用紧凑名，与配方 JSON 一致）。 */
    private static CompoundTag writeAspects(Map<AspectId, Integer> aspects) {
        CompoundTag out = new CompoundTag();
        aspects.forEach((aspect, amount) -> {
            if (amount > 0) {
                out.putInt(aspect.serialized(), amount);
            }
        });
        return out;
    }

    private static void readAspects(CompoundTag source, Map<AspectId, Integer> target) {
        target.clear();
        for (String key : source.getAllKeys()) {
            if (source.getTagType(key) != Tag.TAG_INT) {
                continue;
            }
            try {
                int amount = source.getInt(key);
                if (amount > 0) {
                    target.put(AspectId.parse(key), amount);
                }
            } catch (RuntimeException ignored) {
                // 认不出的键跳过
            }
        }
    }

    // ------------------------------------------------------------------
    // MenuProvider
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.skydeitymysticpivot.xuanshu_alchemy_furnace");
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new XuanshuAlchemyFurnaceMenu(id, inv, this);
    }
}
