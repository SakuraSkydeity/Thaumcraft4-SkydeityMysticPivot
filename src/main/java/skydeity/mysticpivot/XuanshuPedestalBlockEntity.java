package skydeity.mysticpivot;

import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectDefinition;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaApi;
import dev.tc4port.thaumcraft.api.essentia.EssentiaSearch;
import dev.tc4port.thaumcraft.api.essentia.EssentiaSourceRef;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 玄枢台座：皇宫鎏金橘黄的单格台座（底座四级收分 + 石英柱身 + 台面板，形制仿本体奥术基座），可置物其上。
 *
 * <p>卦象转化：台座持续感应所在处的卦气（{@link Fengshui#qiAt}）——
 * 当地处「乾」之地气（开阔天空、头顶无遮挡）时，台面上的黑曜石会缓缓化为哭泣的黑曜石。
 * 可转化的物事集中在 {@link #TRANSFORMATIONS} 一张表里，JEI / EMI 也从这张表取。</p>
 *
 * <p><b>吸纳要素</b>：成坛之后，台座会像<b>注魔矩阵</b>那样从<b>基座附近 5×5</b>
 * 的「要素源」（原质罐、储液池、镜像……）里直接抽取<b>本地卦气对应的那一支</b>要素，
 * 攒在台座里（{@link #bufferedAmount}）——不必先接管子、也不是非端点不可，
 * 罐子摆在旁边就会自己吸。吸取时一条要素丝线从源一路牵进台座
 * （{@link XuanshuPedestalEffects}，与注魔矩阵同款）；每周期只吸
 * {@value #DRAW_PER_BATCH} 份，把八份拉成两秒上下的连续丝线——「看得见在被吸」。</p>
 *
 * <p>卦石就是这么来的：台面放石砖（<b>一次只纳一件</b>，见 {@link #items} 的槽上限）、
 * 立于对应卦象的地气、罐子供着对应要素，攒够 {@value #ESSENTIA_PER_CRAFT} 份，
 * 那件石砖便化为该卦之石（与旧坩埚配方同价：一件石砖 + 本卦要素 8 份）。</p>
 *
 * <p>卦气呈现：台座上方悬着所在处的卦象单字（{@link XuanshuPedestalRenderer}），
 * 底座四隅各有一缕絮带搭上台座顶心，台口外圈盘着一道<b>金色丝带环</b>
 * （同见渲染器）——立在哪里、凝的哪一卦，一眼可辨。</p>
 *
 * <p><b>客户端同步</b>（曾经漏掉、导致「放上去看不见、看着像拿不下来」）：台面上的物品由
 * {@link XuanshuPedestalRenderer} 在<b>客户端那台方块实体</b>上绘制，所以任何改动都必须下发——
 * 只调 {@code setChanged()} 只保证存档，客户端手里的 {@code items} 依旧是空的。
 * 这里三件事配齐：{@code getUpdateTag}（区块下发时带全量数据）、{@code getUpdatePacket}
 * （单点更新）、内容一变就 {@code sendBlockUpdated}（实时推送）。
 * 至于吸纳进来的要素，纯服务端数据（客户端不画它），只存档、不下发。</p>
 */
public class XuanshuPedestalBlockEntity extends BlockEntity {

    /** 单格台面。 */
    public static final int SLOT = 0;

    /** 转化所需总时长（tick），每 10 tick 推进一次，共 3 秒。 */
    public static final int TRANSFORM_TICKS = 60;

    /**
     * 卦石转化<b>每件</b>要耗的要素份数。
     *
     * <p>与旧坩埚配方同价（{@code thaumcraft:crucible}：石砖 + 本卦要素 8 份 → 本卦之石）——
     * 换的是合成途径，不是价钱。</p>
     */
    public static final int ESSENTIA_PER_CRAFT = 8;

    /**
     * 每个推进周期（10 tick）最多从罐子里抽多少份。
     *
     * <p>★ 第 84 批由 16 调到 2：一口气抽完只在屏幕上闪一下、玩家根本看不见；
     * 每周期 2 份 → 一件卦石的 8 份要吸 4 个周期（约 2 秒），丝线便成一条
     * <b>看得见的连续粒子流</b>，观感与注魔矩阵抽要素一致。</p>
     */
    private static final int DRAW_PER_BATCH = 2;

    /**
     * 旧存档兼容的抽吸上限：台面若还压着旧口径的「整堆」（见 {@link #items} 的槽上限），
     * 走这条快车道——不然一整组 64 件按每周期 2 份要吸上两分钟。
     */
    private static final int DRAW_PER_BATCH_BULK = 16;

    /**
     * 抽要素的搜索范围：以台座为中心 ±{@value #SEARCH_RANGE}（水平 5×5、上下两格之内）。
     *
     * <p>★ 第 83 批改为与<b>注魔矩阵</b>同款——直接向范围内的「要素源」（原质罐、
     * 储液池、镜像……）抽取，不再要求先接上管道、也不再是「端点专属」。</p>
     */
    private static final int SEARCH_RANGE = 2;

    /** 吸取丝线的续描周期（tick）：吸着的时候每这么多 tick 把批号 +1 推一次。 */
    private static final int TRAIL_INTERVAL = 5;

    /** 吸取丝线的余晖（tick）：停吸超过这么久就停续并清表，客户端那条尾巴自然收掉。 */
    private static final int TRAIL_LINGER = 10;

    /** 吸取丝线最多记几条源。 */
    private static final int MAX_TRAILS = 8;

    /**
     * 一条「卦象转化」。
     *
     * @param input        台面上要摆的东西（只认物品；第 84 批起台面一次只纳一件，
     *                     旧存档里可能残留旧口径的整堆）
     * @param output       转化后的产物
     * @param qi           必须凝着的卦气
     * @param essentiaCost <b>每件</b>要耗的要素份数（0 = 不要要素，光靠卦气）
     */
    public record Transformation(ItemStack input, ItemStack output, Fengshui.Trigram qi, int essentiaCost) {

        /** 台面上这堆东西是不是本条要的物事。 */
        public boolean accepts(ItemStack stack) {
            return !stack.isEmpty() && stack.is(this.input.getItem());
        }

        /**
         * 把台面上的东西换成产物。
         *
         * <p>★ <b>件数照搬</b>——早先这里写死 {@code new ItemStack(Items.CRYING_OBSIDIAN)}，
         * 于是放一组 64 个黑曜石也只拿回 1 个哭泣的黑曜石，其余的凭空没了。
         * 台面只纳一件之后件数恒为 1；照搬是为了兜住旧存档里可能残留的整堆。</p>
         */
        public ItemStack convert(ItemStack stack) {
            return new ItemStack(this.output.getItem(), stack.getCount());
        }

        /**
         * 本条转化要的要素——就是<b>本卦那一支</b>（{@code skydeitymysticpivot:<卦名>}）。
         *
         * <p>cost 为 0 的转化（黑曜石 → 哭泣的黑曜石）不该问这个。</p>
         */
        public AspectId essentia() {
            return ModAspectSources.aspectId(Fengshui.id(this.qi));
        }
    }

    /**
     * 台座可做的全部转化——<b>唯一的一处定义</b>，运行时与配方查看器（JEI / EMI）共用同一张表。
     *
     * <p>乾为天，天光彻照则黑曜石泣；八方石砖各承其卦——<b>立于对应卦象的地气、罐中供着
     * 对应要素</b>，台面石砖便尽数化为该卦之石。</p>
     */
    public static final List<Transformation> TRANSFORMATIONS = List.of(
            new Transformation(new ItemStack(Items.OBSIDIAN), new ItemStack(Items.CRYING_OBSIDIAN),
                    Fengshui.Trigram.QIAN, 0),
            new Transformation(new ItemStack(Items.STONE_BRICKS), new ItemStack(ModItems.BAGUA_STONE_QIAN.get()),
                    Fengshui.Trigram.QIAN, ESSENTIA_PER_CRAFT),
            new Transformation(new ItemStack(Items.STONE_BRICKS), new ItemStack(ModItems.BAGUA_STONE_DUI.get()),
                    Fengshui.Trigram.DUI, ESSENTIA_PER_CRAFT),
            new Transformation(new ItemStack(Items.STONE_BRICKS), new ItemStack(ModItems.BAGUA_STONE_LI.get()),
                    Fengshui.Trigram.LI, ESSENTIA_PER_CRAFT),
            new Transformation(new ItemStack(Items.STONE_BRICKS), new ItemStack(ModItems.BAGUA_STONE_ZHEN.get()),
                    Fengshui.Trigram.ZHEN, ESSENTIA_PER_CRAFT),
            new Transformation(new ItemStack(Items.STONE_BRICKS), new ItemStack(ModItems.BAGUA_STONE_XUN.get()),
                    Fengshui.Trigram.XUN, ESSENTIA_PER_CRAFT),
            new Transformation(new ItemStack(Items.STONE_BRICKS), new ItemStack(ModItems.BAGUA_STONE_KAN.get()),
                    Fengshui.Trigram.KAN, ESSENTIA_PER_CRAFT),
            new Transformation(new ItemStack(Items.STONE_BRICKS), new ItemStack(ModItems.BAGUA_STONE_GEN.get()),
                    Fengshui.Trigram.GEN, ESSENTIA_PER_CRAFT),
            new Transformation(new ItemStack(Items.STONE_BRICKS), new ItemStack(ModItems.BAGUA_STONE_KUN.get()),
                    Fengshui.Trigram.KUN, ESSENTIA_PER_CRAFT));

    /**
     * 台面上这堆东西、在当下这片卦气里，该走哪一条转化；无则回 {@code null}。
     *
     * <p>卦气只取优先级最高的那一支（{@link Fengshui#qiAt}）——台座立在开阔的森林里，
     * 得到的是巽而不是乾，也就化不出哭泣的黑曜石。</p>
     */
    public static Transformation findTransformation(ItemStack stack, Fengshui.Trigram qi) {
        for (Transformation transformation : TRANSFORMATIONS) {
            if (transformation.qi() == qi && transformation.accepts(stack)) {
                return transformation;
            }
        }
        return null;
    }


    /**
     * 单格台面；台面一有变动就存档并下发。
     *
     * <p>★ 第 84 批起<b>每格只纳一件</b>（{@code getSlotLimit → 1}）：放置时
     * {@code insertItem} 自动只收一件、余数留在玩家手里，以新换旧同此。
     * 于是「整堆石砖一起变卦石」的旧口径不复存在——一件一件来，
     * 每一件的吸纳过程都看得见。</p>
     */
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

    private int progress;

    /**
     * 台座是否已经「开坛」——脚下的 3×3 四象石砖底座齐备、以法杖点过之后为真。
     *
     * <p>未开坛的台座<b>不纳物</b>（见 {@code XuanshuPedestalBlock#useItemOn}），
     * 所以这是决定「这座台座能不能用」的开关，也是底座九砖亮不亮的依据。
     * 它<b>要存档、要下发</b>：客户端渲染器据此多画一圈粒子。</p>
     */
    private boolean active;

    /**
     * 从罐子里吸纳进来、攒在台座里的要素——<b>只可能是本地卦气对应的那一支</b>。
     *
     * <p>收坛（底座缺一块、或拆台座）即散：吸纳是「坛」的能耐，坛散了气也就散了。
     * 卦气变了（地形被改造、台座迁到别处）同样留不住，一并清空。</p>
     */
    private AspectId bufferedAspect;

    /** 攒着的要素份数（与 {@link #bufferedAspect} 配套，无要素时为 0）。 */
    private int bufferedAmount;

    /**
     * 吸取丝线的批号：服务端「刚吸到过要素」时每 {@value #TRAIL_INTERVAL} tick +1 并推一次
     * 方块数据；客户端（{@link XuanshuPedestalEffects}）见了批号变化就把「源→台座」
     * 那条吸取丝线的尾巴续满。
     */
    private int trailNonce;

    /**
     * 上次真的吸到要素的时刻（game tick）；没吸过时 -1。
     *
     * <p>轨迹续期只看它——停吸超过 {@link #TRAIL_LINGER} 就停续并清表。</p>
     */
    private long lastDrawTick = -1;

    /** 最近吸到过要素的源（画吸取丝线用；客户端读，随方块数据下发）。 */
    private final List<BlockPos> trailSources = new ArrayList<>(MAX_TRAILS);

    /** 与 {@link #trailSources} 配对的要素色。 */
    private final List<Integer> trailColors = new ArrayList<>(MAX_TRAILS);

    /** 自查周期（tick）：已开坛的台座每 20 tick 确认一次底座九砖还在不在。 */
    private static final int AUDIT_INTERVAL = 20;

    /**
     * 底座 3×3 相对台座的偏移——台座站在**中心砖**上，整层在**下一层**。
     *
     * <p>第二百零二批起开坛结构从「四角立砖」改成「3×3 砖底座 + 台座上置」：
     * 九块砖全在台座下面一层，中心那块正顶着台座的柱础。四象镇四方四隅的
     * 意象由 {@link #CORNERS}（底座的对角四块）继续承担——渲染器的四象絮带
     * 仍然只连那四块。</p>
     */
    public static final int[][] BASE = {
            {-1, -1}, {0, -1}, {1, -1},
            {-1, 0}, {0, 0}, {1, 0},
            {-1, 1}, {0, 1}, {1, 1},
    };

    /**
     * 底座 3×3 的<b>四个对角</b>（不含十字四邻）——四象絮带连的就是这四块。
     *
     * <p>取对角而非正十字：正十字是台座的正面与通道，摆在那里会挡住玩家走到座前。</p>
     */
    public static final int[][] CORNERS = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};

    public XuanshuPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.XUANSHU_PEDESTAL.get(), pos, state);
    }

    public ItemStackHandler getItems() {
        return this.items;
    }

    public boolean isActive() {
        return this.active;
    }

    /**
     * 底座 3×3 是不是九块都立着四象石砖（都在台座<b>下一层</b>）。
     *
     * <p>只认<b>方块种类</b>、不看它此刻 {@code active} 是真是假——正在点亮的途中
     * 也会走到这里，若把状态也算进来就会自己把自己判死。</p>
     */
    public static boolean baseIntact(Level level, BlockPos pos) {
        for (int[] cell : BASE) {
            if (!level.getBlockState(pos.offset(cell[0], -1, cell[1]))
                    .is(ModBlocks.SIXIANG_STONE_BRICK.get())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 把底座九砖一并点亮 / 灭掉（只有服务端会真的写方块）。
     *
     * <p>第二百零二批起激活态是旋转对称的鎏金楼台，不再需要写 {@code CORNER} 朝向——
     * 只翻 {@code active} 一个状态。</p>
     *
     * <p><b>第二百一十一批加 {@code CENTER}</b>：整座 3×3 楼台是一件跨格的 obj，
     * 只由<b>正中那一格</b>渲染（其余八格是空模型）。这里顺手把「我在正中」写进方块状态——
     * 正中的判定就是 {@link #BASE} 的 {@code (0,0)} 那一格，不必另外算。</p>
     */
    private static void lightBricks(Level level, BlockPos pos, boolean lit) {
        for (int[] cell : BASE) {
            BlockPos p = pos.offset(cell[0], -1, cell[1]);
            BlockState state = level.getBlockState(p);
            if (!state.is(ModBlocks.SIXIANG_STONE_BRICK.get())) {
                continue;
            }
            boolean center = cell[0] == 0 && cell[1] == 0;
            BlockState want = state
                    .setValue(ModBlocks.SixiangStoneBrickBlock.ACTIVE, lit)
                    .setValue(ModBlocks.SixiangStoneBrickBlock.CENTER, lit && center);
            if (want != state) {
                level.setBlock(p, want, Block.UPDATE_ALL);
            }
        }
    }

    /**
     * 开坛：底座 3×3 齐备则点亮九砖并把台座转为「已启」。
     *
     * @return 底座不齐（或本来就已经开着）时给 {@code false}
     */
    public boolean tryActivate(Level level, BlockPos pos) {
        if (level.isClientSide || !baseIntact(level, pos)) {
            return false;
        }
        setActive(level, pos, true);
        return true;
    }

    /** 收坛：台座熄火、底座九砖暗回去（重复调用无副作用）。 */
    public void deactivate(Level level, BlockPos pos) {
        setActive(level, pos, false);
    }

    /**
     * 置「已启 / 已熄」，一并处理存档、下发与四砖的明暗。
     *
     * <p>★ 顺手把底座九砖也按同一状态写一遍：只改台座自己会让两边对不上
     * （台座已熄、砖还亮着，玩家看着就像「明明亮着却说没启」）。</p>
     */
    public void setActive(Level level, BlockPos pos, boolean value) {
        if (this.active == value) {
            // 状态没变也要保证砖是对的（例如读档后砖的状态丢了）
            if (!level.isClientSide) {
                lightBricks(level, pos, value);
            }
            return;
        }
        this.active = value;
        this.setChanged();
        this.syncToClient();
        lightBricks(level, pos, value);
    }

    /** 台面一有变动就推给客户端；服务端专用，客户端调用为空操作。 */
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

    /**
     * 客户端每 tick：把「吸取丝线」的尾巴续上（{@link XuanshuPedestalEffects}）。
     *
     * <p>★ 第 83 批：原「附魔符文粒子环」（沿斜置椭圆撒的一圈零重力符文）已删，
     * 基座周围的环改为渲染器画的<b>金色丝带环</b>（{@link XuanshuPedestalRenderer}），
     * 与四隅絮带同一套画法。这里只剩吸取丝线的续期（服务端每 {@value #TRAIL_INTERVAL} tick
     * 把 {@link #trailNonce} +1 推下来，见了就把尾巴续满）。</p>
     */
    public static void clientTick(Level level, BlockPos pos, XuanshuPedestalBlockEntity be) {
        XuanshuPedestalEffects.clientTick(level, pos, be);
    }

    /**
     * 每 10 tick 推进一次卦象转化；另外每 20 tick 自查一次底座九砖还在不在。
     *
     * <h2>★ 这一趟的次序</h2>
     * <ol>
     *   <li><b>续丝</b>（每 tick，排在最前）：吸取丝线的续期 / 清表——★ 第 84 批
     *       从末尾挪来；原先它排在多个 {@code return} 之后，正在吸要素的那些周期
     *       （缓冲区未满、提前返回）根本走不到，批号推不出去、客户端一条丝线
     *       也画不出来——「像没在吸」的根源就在这；</li>
     *   <li><b>熄坛即散</b>：未成坛不测卦气、不转化，攒下的要素也一并散去（进度抹掉，
     *       免得开坛瞬间「凭空推进」）；</li>
     *   <li><b>对不上卦气就清零</b>：台面没东西、或这东西在当下这片卦气里没有对应转化，
     *       进度归零，<b>也不去抽要素</b>——闲着也是空吸玩家的罐子，不干这事；</li>
     *   <li><b>吸纳</b>：本条转化要要素时，从基座附近 5×5 的「要素源」里抽本地卦气那一支，
     *       直到够这一件（每周期最多 {@value #DRAW_PER_BATCH} 份——慢慢吸，丝线才
     *       看得清，见 {@link XuanshuPedestalEffects}）；</li>
     *   <li><b>推进</b>：要素攒够才走 3 秒的转化，成时扣要素、换产物。</li>
     * </ol>
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  XuanshuPedestalBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        // 吸取丝线的续期：★ 第 84 批从函数末尾挪到这里——原来它排在多个 `return`
        // 之后，正在吸要素的那几个周期（缓冲区未满、提前返回）根本走不到，批号
        // 推不出去、客户端一条丝线也画不出来（「看着像没在吸」的根源）。
        // 现在独立于下方的转化流程，每 tick 都续，与坩埚的写法对齐。
        // （停续条件只看 lastDrawTick，别用 trailSources.isEmpty()——那张表只增不减。）
        long now = level.getGameTime();
        long sinceDraw = be.lastDrawTick < 0 ? Long.MAX_VALUE : now - be.lastDrawTick;
        if (sinceDraw > TRAIL_LINGER) {
            if (!be.trailSources.isEmpty()) {
                be.trailSources.clear();
                be.trailColors.clear();
                be.syncToClient();
            }
        } else if (now % TRAIL_INTERVAL == 0) {
            be.bumpTrail();
        }

        if (level.getGameTime() % AUDIT_INTERVAL == 0) {
            if (be.active && !baseIntact(level, pos)) {
                // 底座缺了一块：熄火（砖那边 onRemove 也会立刻招呼一次，这里是兜底）
                be.setActive(level, pos, false);
            } else {
                // 把底座九砖抹平成台座的样子——读档、区块先后加载、砖被替换过，
                // 都可能让两边对不上，十拿九稳的做法是定期往回对齐一次。
                lightBricks(level, pos, be.active);
            }
        }
        if (!be.active) {
            // ★ 未成坛：不测卦气、不转化（进度也一并抹掉，免得开坛瞬间「凭空推进」）；
            //   吸纳来的要素是坛的能耐，坛散了气也就散了。
            be.progress = 0;
            be.clearEssentia();
            return;
        }
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        Fengshui.Trigram qi = Fengshui.qiAt(level, pos);
        // 一处不凝卦气，或卦气变了（地形被改造）：手里这份要素再也用不上，散去
        AspectId want = qi == Fengshui.Trigram.NONE
                ? null
                : ModAspectSources.aspectId(Fengshui.id(qi));
        if (want == null || !want.equals(be.bufferedAspect)) {
            be.clearEssentia();
        }

        ItemStack stack = be.items.getStackInSlot(SLOT);
        Transformation transformation = findTransformation(stack, qi);
        if (transformation == null) {
            be.progress = 0;
            return;
        }
        int cost = transformation.essentiaCost() * stack.getCount();
        if (cost > 0) {
            // 吸纳：抽够这一件要的份数（每个推进周期限量）。
            // ★ 第 84 批：槽上限 1 → 台面只有一件，每周期只吸 {@value #DRAW_PER_BATCH}
            //   份，把八份拉成两秒上下的连续丝线（看得见的过程）；旧存档残留的
            //   整堆走快车道，免得吸上两分钟。
            int batch = stack.getCount() > 1 ? DRAW_PER_BATCH_BULK : DRAW_PER_BATCH;
            int room = cost - be.bufferedAmount;
            if (room > 0) {
                int got = be.drawEssentia(want, Math.min(room, batch));
                if (got > 0) {
                    be.bufferedAspect = want;
                    be.bufferedAmount += got;
                    be.setChanged();
                }
            }
            if (be.bufferedAmount < cost) {
                // 要素还没攒够：等着（进度清零——「攒要素」不算在转化里）
                be.progress = 0;
                return;
            }
        }
        be.progress += 10;
        if (be.progress % 20 == 0) {
            ((ServerLevel) level).sendParticles(ParticleTypes.PORTAL,
                    pos.getX() + 0.5D, pos.getY() + 1.2D, pos.getZ() + 0.5D,
                    6, 0.2D, 0.1D, 0.2D, 0.5D);
        }
        if (be.progress >= TRANSFORM_TICKS) {
            be.progress = 0;
            if (cost > 0) {
                be.bufferedAmount -= cost;
                if (be.bufferedAmount <= 0) {
                    be.clearEssentia();
                }
                be.setChanged();
            }
            // setStackInSlot 会走 onContentsChanged，同步随之下发。
            // ★ 件数照搬（台面只纳一件、通常恒为 1；照搬兜住旧存档里可能残留的整堆）。
            be.items.setStackInSlot(SLOT, transformation.convert(stack));
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.BLOCKS, 0.8F, 0.7F);
        }
        be.setChanged();
    }

    /** 散去攒着的要素（收坛、换卦气、或刚好用干）。 */
    private void clearEssentia() {
        if (this.bufferedAspect != null || this.bufferedAmount != 0) {
            this.bufferedAspect = null;
            this.bufferedAmount = 0;
            this.setChanged();
        }
    }

    /**
     * 读档时解析要素 ID——{@link AspectId#parse} 遇脏数据会抛，这里兜住回 {@code null}。
     *
     * <p>正常情况下这个字段只由 {@link #saveAdditional} 自己写，抛不起来；但手改过的存档、
     * 或者将来某版改了拼写，都不该让整个区块加载跟着崩。</p>
     */
    @Nullable
    private static AspectId parseAspect(String serialized) {
        try {
            return AspectId.parse(serialized);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * 从基座附近 5×5 内抽 {@code aspect} 最多 {@code want} 份，返回实际抽到的份数。
     *
     * <p>★ <b>与注魔矩阵同款</b>（{@code InfusionMatrixBlockEntity#drainOneEssentia}）：
     * 不接管道、不认端点，直接 {@link EssentiaApi#extract} 向范围内的「要素源」
     * （原质罐、储液池、镜像……）抽取——罐子摆在旁边就会自己吸。
     * 先按「一次付清」问一遍（省事），付不满再逐份抽（罐里剩一两份也能吸干净）。
     * 每抽到一次就 {@link #recordTrail 记一条源}，客户端据此画「源→台座」的丝线。</p>
     */
    private int drawEssentia(AspectId aspect, int want) {
        Optional<EssentiaSourceRef> bulk = EssentiaApi.extract(this.level, this.worldPosition,
                aspect, want, EssentiaSearch.nearby(SEARCH_RANGE), EssentiaTransferMode.EXECUTE);
        if (bulk.isPresent()) {
            this.recordTrail(bulk.orElseThrow().position(), aspect);
            return want;
        }
        int drawn = 0;
        while (drawn < want) {
            Optional<EssentiaSourceRef> one = EssentiaApi.extract(this.level, this.worldPosition,
                    aspect, 1, EssentiaSearch.nearby(SEARCH_RANGE), EssentiaTransferMode.EXECUTE);
            if (one.isEmpty()) {
                break;
            }
            this.recordTrail(one.orElseThrow().position(), aspect);
            drawn++;
        }
        return drawn;
    }

    /**
     * 记一条吸取丝线的源（同一格只留一条，色号取该要素的色），并记下「刚吸到东西」的时刻。
     *
     * <p>与坩埚的 {@code recordTrail} 同一套：源表只增不减，何时停看 {@link #lastDrawTick}；
     * 换要素色不影响位置，只把那一格的色号改掉。</p>
     */
    private void recordTrail(BlockPos source, AspectId aspect) {
        if (this.level != null) {
            this.lastDrawTick = this.level.getGameTime();
        }
        BlockPos key = source.immutable();
        AspectDefinition definition = AspectApi.get(aspect);
        int color = definition == null ? 0xFFFFFF : definition.color();
        for (int i = 0; i < this.trailSources.size(); i++) {
            if (this.trailSources.get(i).equals(key)) {
                this.trailColors.set(i, color);
                return;
            }
        }
        while (this.trailSources.size() >= MAX_TRAILS) {
            this.trailSources.remove(0);
            this.trailColors.remove(0);
        }
        this.trailSources.add(key);
        this.trailColors.add(color);
    }

    /** 吸取丝线批号 +1（连同源表一起推给客户端）。 */
    private void bumpTrail() {
        this.trailNonce++;
        this.setChanged();
        this.syncToClient();
    }

    /** 吸取丝线批号（客户端 tick 见面即续期）。 */
    public int trailNonce() {
        return this.trailNonce;
    }

    public int trailCount() {
        return this.trailSources.size();
    }

    public BlockPos trailSource(int index) {
        return this.trailSources.get(index);
    }

    public int trailColor(int index) {
        return this.trailColors.get(index);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", this.items.serializeNBT(registries));
        tag.putInt("progress", this.progress);
        tag.putBoolean("active", this.active);
        if (this.bufferedAspect != null) {
            tag.putString("essentia_aspect", this.bufferedAspect.serialized());
            tag.putInt("essentia_amount", this.bufferedAmount);
        }
        // 吸取丝线：批号 + 源表 + 色表（与坩埚同款——顺带存档，重载后动画不突然断）
        tag.putInt("TrailNonce", this.trailNonce);
        long[] packed = new long[this.trailSources.size()];
        for (int i = 0; i < packed.length; i++) {
            packed[i] = this.trailSources.get(i).asLong();
        }
        tag.putLongArray("TrailSources", packed);
        int[] colors = new int[this.trailColors.size()];
        for (int i = 0; i < colors.length; i++) {
            colors[i] = this.trailColors.get(i);
        }
        tag.putIntArray("TrailColors", colors);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items.deserializeNBT(registries, tag.getCompound("items"));
        this.progress = tag.getInt("progress");
        this.active = tag.getBoolean("active");
        // 攒着的要素：只有「成坛 + 卦气没变」才留得住，serverTick 每个周期都会拿当下的
        // 卦气对一遍，对不上就散——所以这里照原样读进来即可，不必在这里判。
        if (tag.contains("essentia_aspect")) {
            this.bufferedAspect = parseAspect(tag.getString("essentia_aspect"));
            this.bufferedAmount = this.bufferedAspect == null ? 0 : tag.getInt("essentia_amount");
        }
        this.trailNonce = tag.getInt("TrailNonce");
        long[] packed = tag.getLongArray("TrailSources");
        int[] colors = tag.getIntArray("TrailColors");
        for (int i = 0; i < packed.length && i < colors.length; i++) {
            this.trailSources.add(BlockPos.of(packed[i]));
            this.trailColors.add(colors[i]);
        }
        // ★ 不在这里写方块：loadAdditional 是在区块反序列化途中调的，
        //   那会儿 setBlock 会把邻块更新插进一段还没搭好的区块里。底座的对齐交给
        //   serverTick 的定期自查（最多 1 秒，肉眼无感）。
    }
}
