package skydeity.mysticpivot;

import dev.tc4port.thaumcraft.api.ThaumcraftApiHelper;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaApi;
import dev.tc4port.thaumcraft.api.essentia.EssentiaSearch;
import dev.tc4port.thaumcraft.api.essentia.EssentiaSourceRef;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 玄枢坩埚：单方块的「炼金坩埚」，本体 {@code thaumcraft:crucible} 的单方块改良版。
 *
 * <h2>三个并行加工位（「三炉同烧」）</h2>
 * <p>肚子里有 <b>3 组</b>独立的工作面：每组一进一出，各认各的触媒、各吸各的要素、
 * 各走各的进度条——<b>三种完全不同的配方可以同时炼</b>，界面上就是三条进度条。</p>
 *
 * <p>一炉最多一次消化 {@value #MAX_BATCH} 件触媒（「上限 64 个」）：输入槽里堆了几件，
 * 要素就按同样的倍数吸，一次出同样多的成品。一炉耗时 {@value #PROCESS_TICKS} tick
 * ——<b>即 1 tick 一炉</b>（第 47 批由 20 压到 1），
 * 比第一版的 3 秒快了三倍。</p>
 *
 * <h2>与原版坩埚的三处不同</h2>
 * <ol>
 *   <li><b>不要热源、不要水</b>：没有流体、没有液面、没有火；
 *       只要肚子里有一件<b>触媒</b>，要素又凑得齐，就地开炼。</li>
 *   <li><b>不会积攒咒流</b>：本体坩埚把「多丢进去的要素」泡在汤里，丢多了就成了咒流；
 *       这里<b>按配方的份数精确吸入</b>——需要 3 份就只吸 3 份，
 *       连「多倒」的入口都不存在，自然也就没有咒流。</li>
 *   <li><b>要素自己找上门</b>：见下。</li>
 * </ol>
 *
 * <h2>要素从哪来（★ 与本体炼金自动机同口径）</h2>
 * <p>第一版走的是 {@link EssentiaApi#findSource}，结果一口要素都吸不到——
 * ★ 因为<b>原质管实现的是 {@link EssentiaTransport}（传输端点），不是 {@link dev.tc4port.thaumcraft.api.essentia.EssentiaSource}（可远程抽取的源）</b>：
 * 只有原质罐、储液池、镜像这类「源」才实现后者。所以与管打交道必须换一套做法，
 * 照本体的 {@code ThaumatoriumBlockEntity}：</p>
 * <ol>
 *   <li><b>对外宣告吸力</b>：本方块自己实现 {@link EssentiaTransport}，
 *       为「当前缺的那样要素」宣告 {@code suction = }{@value #SUCTION}（与本体同值）。
 *       原质管每 tick 会读取邻居的吸力并<b>跟着抬高自己的吸力</b>，
 *       于是整张管网的要素会朝这口锅流过来——
 *       这是 TC4 的规矩：机<em>吸</em>，管<em>送</em>，谁也不硬塞。</li>
 *   <li><b>从紧邻的管里直接取</b>：每 {@value #PULL_INTERVAL} tick 扫一遍六个面，
 *       对面是能朝我们输出的要素端点就 {@link EssentiaApi#take}（缺多少取多少）。</li>
 *   <li><b>顺带扫 5×5×5 里的「源」</b>：每 {@value #REGION_INTERVAL} tick 在边长 5 的立方体内
 *       用 {@link EssentiaApi#findSource} 找原质罐 / 储液池 / 镜像这类
 *       {@code EssentiaSource}，一次付清地补上缺的那几份。</li>
 * </ol>
 * <p>两条路都与「传输配置」<b>无关</b>：物品的六个面怎么设，要素照样按上面三条走。</p>
 *
 * <h2>配方</h2>
 * <p>见 {@link XuanshuCrucibleRecipe#find}：先认本模组自己的 {@code xuanshu_crucible}，
 * 认不到再让<b>本体按 {@code thaumcraft:crucible} 去匹配</b>（要素从
 * {@link XuanshuCrucibleRecipes} 的索引里按配方 id 补），
 * 所以本体自带的、别的附属写的坩埚配方<b>都能做</b>。
 * 也<b>不看研究进度</b>：本体的坩埚要你先解锁研究，这台只认料齐不齐。</p>
 *
 * <h2>六面自动化（与玄枢炎炉同一口径）</h2>
 * <p>六个面各自可设 关闭 / 自动输入 / 自动输出 / 输入输出；
 * 自动输入<b>只收有坩埚配方的触媒</b>（见 {@link #acceptsExternal}），其余一概拒收。
 * 这一套只管<b>物品</b>，与上面的要素吸入互不相干。</p>
 */
public class XuanshuCrucibleBlockEntity extends BlockEntity implements MenuProvider, EssentiaTransport, StarReader {

    /** 并行加工位数：三炉同烧。 */
    public static final int LANES = 3;

    /** 输入槽下标 = 0,1,2；输出槽下标 = 3,4,5。 */
    public static final int SLOT_INPUT_BASE = 0;
    public static final int SLOT_OUTPUT_BASE = 3;
    public static final int SLOT_COUNT = LANES * 2;

    /** 第 {@code lane} 个工作面的输入槽下标。 */
    public static int inputSlot(int lane) {
        return SLOT_INPUT_BASE + lane;
    }

    /** 第 {@code lane} 个工作面的输出槽下标。 */
    public static int outputSlot(int lane) {
        return SLOT_OUTPUT_BASE + lane;
    }

    /** 一炉最多一次消化的触媒件数：「上限 64 个」。 */
    public static final int MAX_BATCH = 64;

    /** 炼一炉的时长（tick）：★ 1 tick——一炉 64 件、每 tick 一炉，即本机最大速度（第 47 批由 20 改到此值）。 */
    public static final int PROCESS_TICKS = 1;

    /** 抽运动画的刷新间隔（tick）：每 5 tick 重发一次，客户端那边维持十多 tick，于是连着看是不断流的。 */
    public static final int TRAIL_INTERVAL = 5;

    /**
     * 抽运动画的「余晖」（tick）：<b>最后一次真正吸到要素之后</b>，轨迹再续这么久就收摊。
     *
     * <p>★ <b>这里踩过的坑（第 35 批）</b>：原来的续期条件是
     * 「轨迹源非空 || 还有工作面在忙」——而 {@code trailSources}
     * 是<b>只增不减</b>的（只有换存档才清），于是哪怕锅里早就不吸了，
     * 这个条件<b>恒为真</b>：服务端每 5 tick 永远把批号 +1，客户端于是永远把 15 tick
     * 的轨迹表续满 —— 表现就是<b>一条吸收动画挂在那儿没完没了</b>。</p>
     *
     * <p>正确的判据是「<b>上次吸到东西到现在过了多久</b>」：{@link #lastAbsorbTick} 记时刻，
     * 超过 {@value #TRAIL_LINGER} 就停止续期并清表。配上客户端那 10 tick 的尾巴，
     * 从最后一滴要素吸进来到动画彻底消失，正好是 <b>1 秒</b>。</p>
     */
    private static final int TRAIL_LINGER = 10;

    /** 自动输入 / 输出的搬运周期（tick）：★ 1——每 tick 一轮，跟上 1 tick 一炉的炉速。 */
    public static final int TRANSFER_INTERVAL = 1;

    /** 从紧邻的管里取要素的周期（tick）：★ 1——吸料与炉速同步。 */
    private static final int PULL_INTERVAL = 1;

    /** 扫 5×5×5 里的「源」（原质罐 / 储液池 / 镜像）的周期（tick）。 */
    private static final int REGION_INTERVAL = 5;

    /**
     * 对外宣告的吸力值：与本体的炼金自动机（{@code ThaumatoriumBlockEntity.SUCTION}）同值。
     *
     * <p>取一样的值是有意的——两台机器挨在同一张管网上时，谁也不会单方面把要素全抢走。</p>
     */
    private static final int SUCTION = 128;

    /**
     * 要素搜索半径：range = 2 ⇒ 以本方块为中心的 <b>5×5×5</b> 立方体。
     *
     * <p>走 {@link EssentiaSearch#nearby(int)}（无方向版）：遍历顺序沿用本体 TC4 的
     * aa/bb/cc 确定性次序，并且<b>绝不加载未加载的区块</b>。</p>
     */
    public static final int SEARCH_RANGE = 2;

    /**
     * 一轮里同一样要素最多取几份。
     *
     * <p>★ 这里<b>不再细水长流</b>：一炉最多吃 {@value #MAX_BATCH} 件触媒，
     * 要素需求同样会被放大到几十上百份，一次只取 8 份会让进度条空等半天。
     * 取 {@value #MAX_BATCH} 就是为了让「上限 64」这件事真的跑得起来。</p>
     */
    private static final int MAX_PULL_PER_CYCLE = MAX_BATCH;

    /** 面模式（与炎炉同编号，界面色例可以共用）。 */
    public static final int MODE_OFF = 0;
    public static final int MODE_INPUT = 1;
    public static final int MODE_OUTPUT = 2;
    public static final int MODE_IO = 3;
    public static final int MODE_COUNT = 4;

    /** 一次抽运最多画几条轨迹（配方里的要素通常也就三五种）。 */
    private static final int MAX_TRAILS = 8;

    /**
     * 一个工作面（一"炉"）的全部状态。
     *
     * <p>三个工作面彼此完全独立：配方不同、吸的要素不同、进度不同、产物不同。</p>
     */
    private static final class Lane {

        /** 这一炉已经吸进来的要素（id → 份数）。装不下多余的：只按配方缺的份数吸。 */
        final Map<AspectId, Integer> stored = new LinkedHashMap<>();

        /** 这一炉需要的要素（= 配方要素 × 本次批量），同步给界面画那一行图标。 */
        final Map<AspectId, Integer> needed = new LinkedHashMap<>();

        /** 本炉进度（tick）。 */
        int progress;

        /** 是否正在熬。 */
        boolean working;

        /** 本炉锁定的批量（开炉那一刻从输入槽件数算出来，中途不再变）。 */
        int batch;

        /** 本炉用的配方（熬的那 1 秒里触媒还在锅里，这里留一份免得中途被挪走就认不出来）。 */
        XuanshuCrucibleRecipe recipe;

        /** 本炉的产物（已按批量乘好）。 */
        ItemStack pending = ItemStack.EMPTY;

        void reset() {
            working = false;
            progress = 0;
            batch = 0;
            recipe = null;
            pending = ItemStack.EMPTY;
        }
    }

    /** 玩家与机器逻辑共用的内部物品栏（无进出限制）。 */
    private ItemStackHandler items = new ItemStackHandler(SLOT_COUNT);

    /** 三个并行工作面。 */
    private final Lane[] lanes = {new Lane(), new Lane(), new Lane()};

    /** 六个面的模式，下标 = {@link Direction#ordinal()}。 */
    private final byte[] sideModes = new byte[6];

    /** 精确输入（Mekanism 的 strict input）：开启后自动输入只拉触媒，产物槽永不被拉。 */
    private boolean strictInput;

    /**
     * 对外宣告的吸力要素（{@code null} = 不要要素）。
     *
     * <p>★ 取的是「<b>还缺的第一样</b>」，与本体炼金自动机同一个做法：
     * 一次只要一样，管网把这样送齐了再换下一样。若宣告的类型与管里跑的类型对不上，
     * 管会判定「在排气」而把要素白白喷掉——所以只有真要的时候才宣告，而且类型必须具体。</p>
     *
     * <p>★ 算的是<b>同步下来的快照</b>（{@code needed} / {@code stored}），
     * 因此两边都给得出同一个答案——管子渲染那些客户端查询也照常有值。</p>
     */
    @Nullable
    private AspectId liveSuction() {
        for (Lane lane : lanes) {
            if (lane.working) {
                continue;
            }
            for (Map.Entry<AspectId, Integer> entry : lane.needed.entrySet()) {
                if (lane.stored.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    /**
     * 抽运轨迹的「批号」。
     *
     * <p>服务端<b>在「刚吸到过要素」时</b>每 {@link #TRAIL_INTERVAL} tick 把它 +1 并推一次
     * 方块实体数据，客户端见到批号变了就把轨迹表续满 —— 这正是本体
     * {@code EssentiaSourceEffectManager} 的「源表 + 重复包续期」那套做法，
     * 效果就是一条从源奔向坩埚的粒子流，不断流、也不堆积。</p>
     *
     * <p>停吸 {@link #TRAIL_LINGER} tick 之后就不再 +1（并把源表清掉），
     * 于是那串粒子在 1 秒内自然收尾、不会赖着不走。</p>
     */
    private int trailNonce;

    /**
     * 最近一次<b>真正吸到要素</b>的游戏时刻；{@code -1} = 这一炉还没吸过。
     *
     * <p>轨迹续期只看它（见 {@link #TRAIL_LINGER}）——不再看「源表空不空」，
     * 因为源表是只增不减的，拿它当条件会让动画永远停不下来。</p>
     */
    private long lastAbsorbTick = -1;

    /** 本炉次抽过要素的源（画轨迹用）。 */
    /**
     * 北斗七座是否各供一枚玄枢碎片（第一百三十三批立门、<b>第一百五十一批加严</b>）：
     * 未烬的玄枢锭要这个条件才炼得成。
     *
     * <p>原来只查「北斗全曜」——七颗星亮着就算，星位上摆圆石也行；用户改口径后改成核供物，
     * 见 {@link StarRite#beidouShard}。服务端每 {@value #STAR_CACHE_TICKS} tick 重算一次；
     * 头顶那条绿线不读这个字段，客户端自己算星象。</p>
     */
    private boolean starBeidouShard;
    private long starCacheTick = Long.MIN_VALUE;

    private static final int STAR_CACHE_TICKS = 20;

    /** 头顶引导线的颜色：翠绿（北斗七星全亮时亮）。 */
    private static final int GUIDE_GREEN = 0x1FD45E;

    @Override
    public int guideColor() {
        return GUIDE_GREEN;
    }

    /**
     * 绿线只在「北斗七座各供玄枢碎片」成立时亮（第一百五十一批改口径）。
     *
     * <p>原来是 {@code constellation.beidouComplete()}——七颗星亮着就算，与加严后的门禁
     * （{@link StarRite#beidouShard}）对不上。这里改成同一套判定，十 tick 一算。</p>
     */
    @Override
    public boolean guideActive(Constellation constellation) {
        if (this.level == null) {
            return false;
        }
        long now = this.level.getGameTime();
        if (this.guideGateTick == Long.MIN_VALUE || now - this.guideGateTick >= GUIDE_GATE_TICKS) {
            this.guideGateTick = now;
            this.guideGateCache = StarRite.beidouShard(this.level, this.worldPosition);
        }
        return this.guideGateCache;
    }

    /** 引导线判定的缓存（见 {@link #guideActive}）。 */
    private boolean guideGateCache;
    private long guideGateTick = Long.MIN_VALUE;
    private static final int GUIDE_GATE_TICKS = 10;

    private final List<BlockPos> trailSources = new ArrayList<>(MAX_TRAILS);

    /** 对应的要素色（画轨迹用）。 */
    private final List<Integer> trailColors = new ArrayList<>(MAX_TRAILS);

    public XuanshuCrucibleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.XUANSHU_CRUCIBLE.get(), pos, state);
        // 出厂默认：顶面自动输入（料从上头丢进去）、底面自动输出（成品从底下掏出来）
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

    /** 供能力系统按面暴露给相邻自动化。 */
    public IItemHandler getExternalItems(@Nullable Direction face) {
        return new SideHandler(face == null ? MODE_OFF : sideModes[face.ordinal()]);
    }

    public static boolean isIntakeMode(int mode) {
        return mode == MODE_INPUT || mode == MODE_IO;
    }

    public static boolean isOutputMode(int mode) {
        return mode == MODE_OUTPUT || mode == MODE_IO;
    }

    /** 这个槽是不是三个输入槽之一。 */
    private static boolean isInputSlot(int slot) {
        return slot >= SLOT_INPUT_BASE && slot < SLOT_INPUT_BASE + LANES;
    }

    /** 这个槽是不是三个输出槽之一。 */
    private static boolean isOutputSlot(int slot) {
        return slot >= SLOT_OUTPUT_BASE && slot < SLOT_OUTPUT_BASE + LANES;
    }

    /**
     * 这一格里愿不愿意收外面送来的这一堆——<b>必查配方</b>：
     * 锅里熬不出东西的（不是任何坩埚配方的触媒）一律拒收。
     */
    private boolean acceptsExternal(int slot, ItemStack stack) {
        return isInputSlot(slot) && isCatalyst(stack);
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

    public boolean hasStrictInput() {
        return strictInput;
    }

    public void setStrictInput(boolean value) {
        if (strictInput != value) {
            strictInput = value;
            setChanged();
        }
    }

    public boolean toggleStrictInput() {
        setStrictInput(!strictInput);
        return strictInput;
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
    // 要素端点（实现 EssentiaTransport：原质管才认得出我们、才肯把要素送来）
    // ------------------------------------------------------------------

    /** 六个面都当要素端口——要素不归「传输配置」管。 */
    @Override
    public boolean isConnectable(Direction face) {
        return true;
    }

    @Override
    public boolean canInputFrom(Direction face) {
        return true;
    }

    /** 我们从不往外吐要素。 */
    @Override
    public boolean canOutputTo(Direction face) {
        return false;
    }

    @Nullable
    @Override
    public AspectId suctionType(Direction face) {
        return liveSuction();
    }

    @Override
    public int suctionAmount(Direction face) {
        return liveSuction() == null ? 0 : SUCTION;
    }

    @Override
    public int takeEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        return 0;
    }

    /**
     * 别的机器（或附属）往我们这里塞要素时的入口。
     *
     * <p>只收<b>某个工作面当前缺的那样</b>、而且只收到够用为止——多给的一律原样退回，
     * 这就是「不会积攒咒流」在代码里的落地。</p>
     */
    @Override
    public int addEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        if (!canInputFrom(face) || aspect == null || amount <= 0) {
            return 0;
        }
        int accepted = acceptableAmount(aspect, amount);
        if (accepted <= 0) {
            return 0;
        }
        if (mode.executes()) {
            fillLanes(aspect, accepted);
            recordTrail(this.worldPosition.relative(face), aspect);
            syncToClient();
        }
        return accepted;
    }

    @Nullable
    @Override
    public AspectId essentiaType(Direction face) {
        return null;
    }

    @Override
    public int essentiaAmount(Direction face) {
        return 0;
    }

    @Override
    public int minimumSuction() {
        return 0;
    }

    @Override
    public boolean renderExtendedTube() {
        return false;
    }

    // ------------------------------------------------------------------
    // GUI 读取
    // ------------------------------------------------------------------

    /** 第 {@code lane} 个工作面的进度（tick）。 */
    public int getProgress(int lane) {
        return lanes[lane].progress;
    }

    /** 第 {@code lane} 个工作面需要的要素（界面画那一行图标用；读的是同步下来的快照）。 */
    public Map<AspectId, Integer> neededAspects(int lane) {
        return Collections.unmodifiableMap(lanes[lane].needed);
    }

    /** 第 {@code lane} 个工作面里某样要素已经吸到多少（界面用来把「够了」的那几样标亮）。 */
    public int storedOf(int lane, AspectId aspect) {
        return lanes[lane].stored.getOrDefault(aspect, 0);
    }

    // ------------------------------------------------------------------
    // 每 tick
    // ------------------------------------------------------------------

    @SuppressWarnings("unused")
    public static void serverTick(Level level, BlockPos pos, BlockState state, XuanshuCrucibleBlockEntity be) {
        long time = level.getGameTime();
        if (time % TRANSFER_INTERVAL == 0) {
            be.autoInput();
            be.autoOutput();
        }
        be.tickCrucible(time);
    }

    /** 客户端每 tick：把「正在吸要素」表现成要素轨迹 + 坩埚口的翻泡。 */
    @SuppressWarnings("unused")
    public static void clientTick(Level level, BlockPos pos, XuanshuCrucibleBlockEntity be) {
        XuanshuCrucibleEffects.clientTick(level, pos, be);
    }

    private void tickCrucible(long time) {
        if (level == null || level.isClientSide) {
            return;
        }
        if (this.starCacheTick == Long.MIN_VALUE || time - this.starCacheTick >= STAR_CACHE_TICKS) {
            this.starCacheTick = time;
            this.starBeidouShard = StarRite.beidouShard(level, this.worldPosition);
        }
        for (int lane = 0; lane < LANES; lane++) {
            tickLane(lane, time);
        }

        // 轨迹续期：只在「不久前真的吸到过要素」时才续。
        // ★ 别用 trailSources.isEmpty() 当条件——那张表只增不减，会让动画永远停不下来（见 TRAIL_LINGER）。
        long sinceAbsorb = lastAbsorbTick < 0 ? Long.MAX_VALUE : time - lastAbsorbTick;
        if (sinceAbsorb > TRAIL_LINGER) {
            // 停吸超过余晖时间：把源表清掉并下发，客户端那一串粒子到此为止
            if (!trailSources.isEmpty()) {
                trailSources.clear();
                trailColors.clear();
                syncToClient();
            }
        } else if (time % TRAIL_INTERVAL == 0) {
            bumpTrail();
        }
    }

    /**
     * 推进第 {@code lane} 个工作面：认配方 → 定上限 → 吸要素 → <b>按手里有多少开炉</b> → 熬 → 出货。
     */
    private void tickLane(int lane, long time) {
        Lane state = lanes[lane];
        ItemStack input = items.getStackInSlot(inputSlot(lane));

        if (state.working) {
            // 锅里那件触媒被玩家提前挪走了：止损，这炉作废（吸进来的要素仍留在锅里）
            if (state.recipe == null || !state.recipe.catalystMatches(input)) {
                abortLane(state);
                return;
            }
            state.progress++;
            if (state.progress >= PROCESS_TICKS) {
                finishLane(lane, state);
            }
            setChanged();
            return;
        }

        // ★ 第 82 批：完成态（进度停在满格等取货）——成品被取走就把进度归零；
        //   成品还压在输出槽里就保持满格，箭头一直亮着。而「能连做下一炉」的路径
        //   不受影响：下面要素够 + 输出有空间会走 startLane，把进度翻篇从头再涨。
        if (state.progress >= PROCESS_TICKS
                && items.getStackInSlot(outputSlot(lane)).isEmpty()) {
            state.progress = 0;
            syncToClient();
        }

        XuanshuCrucibleRecipe recipe = XuanshuCrucibleRecipe.find(level, input);
        // ★ 第一百三十三批立门、第一百五十一批加严：未烬的玄枢锭要北斗七座各供玄枢碎片
        //   才炼得成，其余配方不受影响
        if (recipe != null && input.is(ModItems.XUANSHU_INGOT_UNFIRED.get()) && !this.starBeidouShard) {
            recipe = null;
        }
        int cap = planBatch(lane, recipe, input);
        refreshNeeded(state, recipe, cap);
        if (recipe == null || cap <= 0) {
            return;
        }
        // 先吸：宣告吸力（{@link #liveSuction()} 自己会算）、从紧邻的管里与 5×5×5 的「源」里取
        if (time % PULL_INTERVAL == 0) {
            pullLane(state);
        }
        if (time % REGION_INTERVAL == 0) {
            pullRegionLane(state);
        }
        // ★ 再按「此刻手里有多少要素」定这一炉做几件 —— 凑不满整炉也开工（见 readyBatch）
        int ready = readyBatch(state, recipe, cap);
        if (ready >= 1) {
            startLane(lane, state, recipe, ready);
        }
    }

    /**
     * 本炉打算一次消化几件触媒。
     *
     * <p>三个上限取最小：输入槽现有的件数、{@value #MAX_BATCH}（「上限 64 个」）、
     * 以及<b>产物槽还能装下几份</b>（否则一炉做出来的东西没处放）。</p>
     */
    private int planBatch(int lane, @Nullable XuanshuCrucibleRecipe recipe, ItemStack input) {
        if (recipe == null || input.isEmpty()) {
            return 0;
        }
        ItemStack result = recipe.getResultItem(level.registryAccess());
        if (result.isEmpty()) {
            return 0;
        }
        int per = Math.max(1, result.getCount());
        int batch = Math.min(input.getCount(), MAX_BATCH);
        batch = Math.min(batch, Math.max(0, outputSpace(lane, result) / per));
        return batch;
    }

    /** 输出槽还能再装几件这种东西。 */
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

    /** 把「这张配方要什么、要多少」记成一份快照；变了就推给客户端（界面那一行随之一变）。 */
    private void refreshNeeded(Lane lane, @Nullable XuanshuCrucibleRecipe recipe, int batch) {
        Map<AspectId, Integer> fresh;
        if (recipe == null || batch <= 0) {
            fresh = Map.of();
        } else {
            Map<AspectId, Integer> scaled = new LinkedHashMap<>();
            recipe.aspects().forEach((aspect, amount) -> scaled.put(aspect, amount * batch));
            fresh = scaled;
        }
        if (fresh.equals(lane.needed)) {
            return;
        }
        lane.needed.clear();
        lane.needed.putAll(fresh);
        syncToClient();
    }

    /**
     * 眼下手里这些要素最多还能开几件。
     *
     * <p>★ <b>第 36 批的口径：不再要求「要素先凑齐整炉」</b>。原来那条 {@code allStored} 要求
     * {@code needed}（= 配方要素 × <b>整批</b>件数）全齐才开炉，于是「放 64 件、每件要 2 份、
     * 边上只有 64 份」这种情形会<b>吸进去却永远不干活</b>（表现就是用户说的「它会吸收但不工作」）。
     * 现在改成：每样要素各自按「手里存量 ÷ <b>单件</b>需求」取整数，全部取最小值，再被上限
     * （输入件数 / {@value #MAX_BATCH} / 产物槽余量）卡住 —— <b>吸到多少就做多少</b>，
     * 不会再卡在「差一点点」。</p>
     *
     * <p>例：铁粒配方每件要 `metallum 2`，锅里有 64 份、槽里 64 件 ⇒ 这一炉做
     * {@code 64 / 2 = 32} 件（而不是干等着另外 64 份）。</p>
     */
    private int readyBatch(Lane lane, XuanshuCrucibleRecipe recipe, int cap) {
        int ready = cap;
        for (Map.Entry<AspectId, Integer> entry : recipe.aspects().entrySet()) {
            int per = Math.max(1, entry.getValue());
            ready = Math.min(ready, lane.stored.getOrDefault(entry.getKey(), 0) / per);
        }
        return ready;
    }

    /**
     * 从紧邻的六个面里取要素。
     *
     * <p>★ 关键在于「对面是<b>要素端点</b>」而不是「要素源」：原质管走的就是这条
     * （{@link ThaumcraftApiHelper#getConnectableTransport}）。</p>
     */
    private void pullLane(Lane lane) {
        boolean moved = false;
        for (Map.Entry<AspectId, Integer> entry : lane.needed.entrySet()) {
            AspectId aspect = entry.getKey();
            int missing = entry.getValue() - lane.stored.getOrDefault(aspect, 0);
            int guard = 0;
            while (missing > 0 && guard++ < 4) {
                boolean progressed = false;
                for (Direction dir : Direction.values()) {
                    EssentiaTransport source =
                            ThaumcraftApiHelper.getConnectableTransport(level, worldPosition, dir);
                    if (source == null) {
                        continue;
                    }
                    Direction sourceFace = dir.getOpposite();
                    if (!source.canOutputTo(sourceFace)
                            || source.availableEssentia(aspect, sourceFace) <= 0) {
                        continue;
                    }
                    // 对面自己也在使劲吸（吸力不低于我们）就别抢；也不去抢「门槛比我们高」的源
                    if (source.suctionAmount(sourceFace) >= SUCTION || SUCTION < source.minimumSuction()) {
                        continue;
                    }
                    int want = Math.min(missing, MAX_PULL_PER_CYCLE);
                    int taken = EssentiaApi.take(level, source, aspect, want, sourceFace,
                            EssentiaTransferMode.EXECUTE);
                    if (taken <= 0) {
                        continue;
                    }
                    lane.stored.merge(aspect, taken, Integer::sum);
                    recordTrail(this.worldPosition.relative(dir), aspect);
                    missing -= taken;
                    progressed = true;
                    moved = true;
                    break;
                }
                if (!progressed) {
                    break;
                }
            }
        }
        if (moved) {
            setChanged();
        }
    }

    /**
     * 扫 5×5×5 里的「可远程抽取的源」：原质罐、储液池、镜像。
     *
     * <p>这类实现的是 {@link dev.tc4port.thaumcraft.api.essentia.EssentiaSource}，
     * 只用一次 {@link EssentiaApi#findSource} 就能确认「能不能一次付清」，
     * 付不起就先降成 1 份再问一次——罐里剩一两份的情形也能吸干净。</p>
     */
    private void pullRegionLane(Lane lane) {
        boolean moved = false;
        for (Map.Entry<AspectId, Integer> entry : lane.needed.entrySet()) {
            AspectId aspect = entry.getKey();
            int missing = entry.getValue() - lane.stored.getOrDefault(aspect, 0);
            if (missing <= 0) {
                continue;
            }
            int want = Math.min(missing, MAX_PULL_PER_CYCLE);
            EssentiaSourceRef reference = null;
            int amount = 0;
            int[] asks = want > 1 ? new int[]{want, 1} : new int[]{1};
            for (int ask : asks) {
                Optional<EssentiaSourceRef> found = EssentiaApi.findSource(level, worldPosition, aspect,
                        ask, EssentiaSearch.nearby(SEARCH_RANGE));
                if (found.isPresent()) {
                    reference = found.orElseThrow();
                    amount = ask;
                    break;
                }
            }
            if (reference == null) {
                continue;
            }
            int taken = EssentiaApi.extract(level, reference, aspect, amount, EssentiaTransferMode.EXECUTE);
            if (taken <= 0) {
                continue;
            }
            lane.stored.merge(aspect, taken, Integer::sum);
            recordTrail(reference.position(), aspect);
            moved = true;
        }
        if (moved) {
            setChanged();
        }
    }

    /** 要素已凑齐，进「熬」这一段。 */
    private void startLane(int lane, Lane state, XuanshuCrucibleRecipe recipe, int batch) {
        ItemStack result = recipe.getResultItem(level.registryAccess());
        if (result.isEmpty()) {
            return;
        }
        state.recipe = recipe;
        state.pending = result.copyWithCount(result.getCount() * batch);
        state.batch = batch;
        state.working = true;
        state.progress = 0;
        bumpTrail();
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            server.playSound(null, worldPosition, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 0.7F, 0.8F);
        }
        syncToClient();
    }

    /** 走满 {@link #PROCESS_TICKS}：扣料、扣要素、出货。 */
    private void finishLane(int lane, Lane state) {
        ItemStack result = state.pending;
        if (result.isEmpty() || state.recipe == null) {
            abortLane(state);
            return;
        }
        int batch = Math.max(1, state.batch);

        // 先把这一炉用的要素从锅里扣掉
        state.recipe.aspects().forEach((aspect, amount) -> {
            int used = amount * batch;
            state.stored.computeIfPresent(aspect, (key, present) -> present > used ? present - used : null);
        });

        // 再扣触媒（玩家中途拿走了一部分的话，按剩下的扣）
        ItemStack input = items.getStackInSlot(inputSlot(lane));
        int take = Math.min(batch, input.getCount());
        if (take > 0) {
            items.extractItem(inputSlot(lane), take, false);
        }

        ItemStack out = items.getStackInSlot(outputSlot(lane));
        int total = result.getCount();
        if (out.isEmpty()) {
            items.setStackInSlot(outputSlot(lane), result.copy());
        } else if (ItemStack.isSameItemSameComponents(out, result)
                && out.getCount() + total <= Math.min(out.getMaxStackSize(), result.getMaxStackSize())) {
            items.setStackInSlot(outputSlot(lane), out.copyWithCount(out.getCount() + total));
        } else {
            // 产物槽满了：绝不凭空吞掉，掉在锅边上
            Block.popResource(level, worldPosition, result.copy());
        }
        // ★ 第 82 批：不调 state.reset()——进度条要<b>留在满格</b>（PROCESS_TICKS）上，
        //   表示「这一炉做完了，成品在输出槽等你取」，绿箭头不消失（对齐炼金炉的排要素阶段）。
        //   只清本炉的「账」：working / batch / recipe / pending。
        //   成品被取走（输出槽空）后由 tickLane 把进度归零；能连做下一炉时 startLane 也会归零。
        state.working = false;
        state.batch = 0;
        state.recipe = null;
        state.pending = ItemStack.EMPTY;
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            server.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.1F);
        }
        syncToClient();
    }

    /** 中止本炉（触媒被挪走 / 产物查不到）：吸进来的要素留在锅里下次接着用。 */
    private void abortLane(Lane state) {
        state.reset();
        syncToClient();
    }

    // ------------------------------------------------------------------
    // 要素的账
    // ------------------------------------------------------------------

    /** 这样要素总共还能收多少（三个工作面缺口的合计，只按配方缺的份数收）。 */
    private int acceptableAmount(AspectId aspect, int amount) {
        int room = 0;
        for (Lane lane : lanes) {
            if (lane.working) {
                continue;
            }
            room += Math.max(0, lane.needed.getOrDefault(aspect, 0) - lane.stored.getOrDefault(aspect, 0));
            if (room >= amount) {
                return amount;
            }
        }
        return room;
    }

    /** 把刚收下的要素按工作面的缺口分下去（先到先得，每个工作面只收到够用为止）。 */
    private void fillLanes(AspectId aspect, int amount) {
        int left = amount;
        for (Lane lane : lanes) {
            if (left <= 0) {
                break;
            }
            if (lane.working) {
                continue;
            }
            int room = Math.max(0, lane.needed.getOrDefault(aspect, 0) - lane.stored.getOrDefault(aspect, 0));
            if (room <= 0) {
                continue;
            }
            int put = Math.min(room, left);
            lane.stored.merge(aspect, put, Integer::sum);
            left -= put;
        }
        setChanged();
    }

    /** 记一条抽运轨迹（同一格只留一条，色号取该要素的色），并记下「刚吸到东西」的时刻。 */
    private void recordTrail(BlockPos source, AspectId aspect) {
        if (level != null) {
            lastAbsorbTick = level.getGameTime();
        }
        BlockPos key = source.immutable();
        int color = XuanshuCrucibleRecipe.colorOf(aspect);
        for (int i = 0; i < trailSources.size(); i++) {
            if (trailSources.get(i).equals(key)) {
                trailColors.set(i, color);
                return;
            }
        }
        while (trailSources.size() >= MAX_TRAILS) {
            trailSources.remove(0);
            trailColors.remove(0);
        }
        trailSources.add(key);
        trailColors.add(color);
    }

    private void bumpTrail() {
        trailNonce++;
        setChanged();
        syncToClient();
    }

    // ------------------------------------------------------------------
    // 自动化（只管物品）
    // ------------------------------------------------------------------

    /** 从所有「自动输入 / 输入输出」面拉触媒——必查配方。 */
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
                if (!isCatalyst(stack)) {
                    continue;
                }
                ItemStack refused = insertCatalyst(stack.copy(), true);
                int accepted = stack.getCount() - refused.getCount();
                if (accepted <= 0) {
                    continue;
                }
                ItemStack pulled = neighbor.extractItem(slot, accepted, false);
                if (pulled.isEmpty()) {
                    break;
                }
                ItemStack leftover = insertCatalyst(pulled.copy(), false);
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

    /** 把产物推给所有「自动输出 / 输入输出」面（三个输出槽逐个推）。 */
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

    /** 把一堆触媒依次塞进三个输入槽，返回塞不下的余量。 */
    private ItemStack insertCatalyst(ItemStack stack, boolean simulate) {
        ItemStack remainder = stack;
        for (int lane = 0; lane < LANES; lane++) {
            remainder = items.insertItem(inputSlot(lane), remainder, simulate);
            if (remainder.isEmpty()) {
                break;
            }
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

    /** 是不是任何坩埚配方的触媒（自动输入与按面暴露都靠它把废物挡在外面）。 */
    public boolean isCatalyst(ItemStack stack) {
        return XuanshuCrucibleRecipe.find(level, stack) != null;
    }

    /**
     * 玩家手持东西右键锅沿时的入口（和 TC 坩埚「把料丢进去」一个意思）。
     *
     * <p>不收非触媒——省得把锅当成一个普通箱子塞满杂物。</p>
     *
     * @return 实际塞进锅里的个数，0 表示没收
     */
    public int insertByHand(ItemStack held, int amount) {
        if (amount <= 0 || !isCatalyst(held)) {
            return 0;
        }
        ItemStack remainder = insertCatalyst(held.copyWithCount(amount), false);
        int moved = amount - remainder.getCount();
        if (moved > 0) {
            setChanged();
            syncToClient();
        }
        return moved;
    }

    /** 被挖掉时把锅里的东西（触媒与产物）原样散出来。 */
    public void dropContents() {
        if (this.level == null || this.level.isClientSide) {
            return;
        }
        for (int slot = 0; slot < items.getSlots(); slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Block.popResource(this.level, this.worldPosition, stack.copy());
            }
        }
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

    /** 轨迹批号（客户端 tick 见面即续期）。 */
    public int trailNonce() {
        return trailNonce;
    }

    public int trailCount() {
        return trailSources.size();
    }

    public BlockPos trailSource(int index) {
        return trailSources.get(index);
    }

    public int trailColor(int index) {
        return trailColors.get(index);
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
        tag.putInt("TrailNonce", trailNonce);
        for (int lane = 0; lane < LANES; lane++) {
            Lane state = lanes[lane];
            tag.putInt("Progress" + lane, state.progress);
            tag.putBoolean("Working" + lane, state.working);
            tag.putInt("Batch" + lane, state.batch);
            tag.put("Pending" + lane, state.pending.saveOptional(registries));
            tag.put("Stored" + lane, writeAspects(state.stored));
            tag.put("Needed" + lane, writeAspects(state.needed));
        }
        // 轨迹源与颜色：只在下发时有用，但一并存下来可以让存档重载后动画不至于突然断掉
        long[] packed = new long[trailSources.size()];
        for (int i = 0; i < packed.length; i++) {
            packed[i] = trailSources.get(i).asLong();
        }
        tag.putLongArray("TrailSources", packed);
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
        strictInput = tag.getBoolean("StrictInput");
        trailNonce = tag.getInt("TrailNonce");
        for (int lane = 0; lane < LANES; lane++) {
            Lane state = lanes[lane];
            state.progress = tag.getInt("Progress" + lane);
            state.working = tag.getBoolean("Working" + lane);
            state.batch = tag.getInt("Batch" + lane);
            state.pending = ItemStack.parseOptional(registries, tag.getCompound("Pending" + lane));
            readAspects(tag.getCompound("Stored" + lane), state.stored);
            readAspects(tag.getCompound("Needed" + lane), state.needed);
            // 配方在客户端也查得到（配方表是两边都有的），重载后照样能算出熬的是哪一张
            if (state.working && level != null) {
                state.recipe = XuanshuCrucibleRecipe.find(level, items.getStackInSlot(inputSlot(lane)));
            }
        }
        trailSources.clear();
        trailColors.clear();
        long[] packed = tag.getLongArray("TrailSources");
        int[] colors = tag.getIntArray("TrailColors");
        for (int i = 0; i < packed.length && trailSources.size() < MAX_TRAILS; i++) {
            trailSources.add(BlockPos.of(packed[i]));
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
        return Component.translatable("block.skydeitymysticpivot.xuanshu_crucible");
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new XuanshuCrucibleMenu(id, inv, this);
    }
}
