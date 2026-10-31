package skydeity.mysticpivot;

import dev.tc4port.thaumcraft.api.aspect.AspectAmounts;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaMultiStoreView;
import dev.tc4port.thaumcraft.api.essentia.EssentiaSource;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransport;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 玄枢源质罐的方块实体（第一百九十三批新增）：<b>一只装得下所有要素的罐子</b>。
 *
 * <h2>它和本体的原质罐差在哪</h2>
 * <p>本体的防护罐（{@code thaumcraft:warded_jar}）一罐只认一种要素，满了就顶住；
 * 这只玄枢罐<b>按要素分别计数</b>，火、水、秩序可以同住一罐，
 * 每种各自最多 {@link #CAPACITY} 份（2048）。</p>
 *
 * <h2>为什么「种类」也有上限（{@link #MAX_KINDS}）</h2>
 * <p>用户口径：「你根据不会爆 component 来设计一个上限」。罐子被挖起来时要带着内容变成
 * 一个<b>物品</b>，内容原样写进物品的组件里；组件是要<b>整包同步到客户端</b>的，
 * 要素表没有上限的话，遇到「所有要素各塞一点」的罐子，一个物品动辄几十 KB，
 * 界面一悬停就要往客户端推一遍。32 种 × 2048 份 = 一张四百来字节的表，
 * 同步、存档、鼠标悬停都没有任何压力；32 种覆盖四原质、六元质与绝大多数复合要素，
 * 真要再加就把这个常数调大 —— 组件体积是随种类线性涨的，不会突然炸。</p>
 *
 * <h2>内容怎么活过「挖起来」这一下（第一百九十四批改法）</h2>
 * <p>见 {@link ModDataComponents#JAR_ESSENTIA}：内容是一个真正的物品组件，在
 * {@link #collectImplicitComponents} 里写出去，战利品表的 {@code copy_components}
 * 从方块实体上复制回掉落的物品；放回去时 {@link #applyImplicitComponents} 读回来。
 * 这条链和本体防护罐是同一套，创造模式拆、爆炸拆、别的模组拆，走的都是同一张表。</p>
 *
 * <h2>为什么要实现三个接口</h2>
 * <ul>
 *   <li>{@link EssentiaTransport}：管子 / 炼金炉 / 任何「往罐里灌」的东西都靠它。
 *       四个侧面与上下都收（本体原质罐只认顶面，那是旧版魔像的老规矩；这只罐四面都通，
 *       摆在炼金炉旁边哪个方向都能灌）；</li>
 *   <li>{@link EssentiaSource}：<b>注魔矩阵与玄枢基座都从这里抽</b>。两者都走
 *       {@code EssentiaApi.extract(…, SEARCH)}，而 {@code extract} 只认这个能力 ——
 *       实现了它，罐子摆在注魔坛边或基座 5×5 里就会被自动吸，连带那束
 *       「源 → 台」的丝线动画也照常出来（动画由本体与基座各自按抽到的源坐标画）；</li>
 *   <li>{@link EssentiaMultiStoreView}：给本体的要素谐振器（护目镜那一路）读内容用 ——
 *       它 {@code instanceof EssentiaMultiStoreView} 才肯显示「罐里有什么」。</li>
 * </ul>
 *
 * <h2>第一百九十六批：跟着本体 API 20751 自动接上 Jade</h2>
 * <p>20751 之前 {@link EssentiaMultiStoreView} 的父接口是 {@code AspectContainerView}，
 * 只能「读内容」，说不了「一罐能装多少」；20751 起父接口换成新的
 * {@code EssentiaStorageView}（四个方法全是 default：{@code essentiaContents(face)} /
 * {@code essentiaCapacity()} / {@code sharedEssentiaCapacity()} / {@code essentiaFilter()}），
 * 专门给「检查」用。而本体在 {@code TCCapabilities} 里给<b>所有</b>
 * {@code BlockEntityType} 登记了 {@code EssentiaCapabilities.STORAGE} 能力 ——
 * 凡是 {@code instanceof EssentiaStorageView} 的方块实体就直接暴露出去。</p>
 *
 * <p>于是本类实现这个接口之后，<b>本体的 Jade 存储段自己就挂上来了</b>
 * （{@code EssentiaStorageJadeProvider} 注册在 {@code Block.class} 上，对任何方块生效）：
 * 先写一行「要素」，再把每种要素画成 <b>数量 / 上限</b> 的比例条。所以我们原先那套
 * 自己写的 {@code compat/jade} 插件（一百九十四批）整包删掉了 —— 同一份信息画两遍
 * 只是重复，而本体的版本还多给了「占多少百分比」。这里只需要补两条「上限是多少」。</p>
 *
 * <p>能力要在 {@code ModEvents#onRegisterCapabilities} 里挂上（{@code TRANSPORT} /
 * {@code SOURCE}），本体才不会去找自己的注册表；{@code STORAGE} 那条是本体统一登记的，
 * 不用我们管。</p>
 */
public class XuanshuEssentiaJarBlockEntity extends BlockEntity
        implements EssentiaTransport, EssentiaSource, EssentiaMultiStoreView {

    /**
     * 每种要素各自的上限（份）：<b>2048</b>。
     *
     * <p>第一百九十三批 2048 → 第一百九十五批 1024 → <b>第二百零七批回到 2048</b>
     * （用户：「玄枢源质罐子要素上限也改为 2048」，与同一批的注灵台同档）。
     * 32 种 × 2048 = 一罐最多 65536 份，而本体一个原质罐是 250 份 —— 这只罐子仍然是
     * 「一只顶一大排」，单个要素存得多，但种类上限不变，组件体积只随种类涨，不会因为这一改炸开。</p>
     */
    public static final int CAPACITY = 2048;

    /**
     * 一罐最多存几种要素（第一百九十四批：16 → <b>32</b>）。
     *
     * <p>见类注释：这条是给<b>物品组件</b>留的余地，不是玩法上的限制。
     * 32 种覆盖四原质、六元质与绝大多数复合要素，日常够用；
     * 32 种 × 2048 份的表仍然只有几百字节，同步、存档、Jade 一悬停全无压力。</p>
     */
    public static final int MAX_KINDS = 32;

    /**
     * 吸力：与本体原质罐同档（32）。
     *
     * <p>不调高是刻意的 —— 同档意味着「这只罐和普通罐一样抢」，不会把周围管子的要素
     * 全吸到自己这儿来，玩家想优先灌它就把管子接它上头。</p>
     */
    public static final int SUCTION = 32;

    /** 内容表：要素 → 份数（只存 &gt;0 的；用 LinkedHashMap 让显示顺序稳定）。 */
    private final Map<AspectId, Integer> contents = new LinkedHashMap<>();

    public XuanshuEssentiaJarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.XUANSHU_ESSENTIA_JAR.get(), pos, state);
    }

    // ------------------------------------------------------------------
    // 查询（渲染器、界面提示、谐振器都读这几条）
    // ------------------------------------------------------------------

    /** 总共多少份（所有要素加起来）。 */
    public int totalAmount() {
        int total = 0;
        for (int amount : contents.values()) {
            total += amount;
        }
        return total;
    }

    /** 最多那种要素的份数（渲染液面用）。 */
    public int fullestAmount() {
        int max = 0;
        for (int amount : contents.values()) {
            max = Math.max(max, amount);
        }
        return max;
    }

    /**
     * 液面高度比例 {@code [0,1]}（渲染器画鎏金液体用）。
     *
     * <p>取「<b>最多的那种</b> / {@link #CAPACITY}」：一种要素装满 2048 时液面到顶。
     * 第一百九十五批把总量换成最多那种 —— 罐子是按要素分别计数的，液面该跟着
     * 「最满的那一格」走；按总量算的话，32 种各倒一点也会显示满罐，看着像假的。</p>
     */
    public float fillRatio() {
        return Math.min(1.0F, (float) fullestAmount() / CAPACITY);
    }

    /** 占得最多的那种要素（没有则 null），渲染器与提示都用它。 */
    @Nullable
    public AspectId dominant() {
        AspectId best = null;
        int most = 0;
        for (Map.Entry<AspectId, Integer> entry : contents.entrySet()) {
            if (entry.getValue() > most) {
                most = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    /** 某要素现在有多少份。 */
    public int amount(AspectId aspect) {
        return contents.getOrDefault(aspect, 0);
    }

    /** 还能不能再收一种新的（种类没满，或这一种本来就在罐里）。 */
    private boolean acceptsKind(@Nullable AspectId aspect) {
        if (aspect == null || !AspectApi.contains(aspect)) {
            return false;
        }
        return contents.containsKey(aspect) || contents.size() < MAX_KINDS;
    }

    /** {@code aspect} 还能再收多少份（不含种类判定之外的其它条件）。 */
    private int roomFor(AspectId aspect) {
        if (!acceptsKind(aspect)) {
            return 0;
        }
        return Math.max(0, CAPACITY - amount(aspect));
    }

    /** 还有没有一点余地（吸力为 0 就是「别往我这儿推了」）。 */
    private boolean hasRoom() {
        if (contents.size() < MAX_KINDS) {
            return true;
        }
        for (int amount : contents.values()) {
            if (amount < CAPACITY) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 收 / 放
    // ------------------------------------------------------------------

    /**
     * 收下最多 {@code requested} 份，返回<b>拒收</b>的份数。
     *
     * <p>与本体原质罐同一套语义（{@code addToContainer}）：收得下多少算多少，
     * 收不下的退回去。{@code execute=false} 时只算账不动手。</p>
     */
    public int insert(AspectId aspect, int requested, boolean execute) {
        if (requested <= 0) {
            return requested;
        }
        int room = roomFor(aspect);
        int accepted = Math.min(requested, room);
        if (accepted > 0 && execute) {
            contents.merge(aspect, accepted, Integer::sum);
            changedAndSync();
        }
        return requested - accepted;
    }

    /**
     * 取走最多 {@code requested} 份，返回<b>实际取走</b>的份数。
     *
     * <p>{@code execute=false} 时只算账不动手。</p>
     */
    public int extract(AspectId aspect, int requested, boolean execute) {
        int have = amount(aspect);
        if (requested <= 0 || have <= 0) {
            return 0;
        }
        int taken = Math.min(requested, have);
        if (execute) {
            int left = have - taken;
            if (left <= 0) {
                contents.remove(aspect);
            } else {
                contents.put(aspect, left);
            }
            changedAndSync();
        }
        return taken;
    }

    /** 一次倒空（潜行空手右键）。 */
    public void clearContents() {
        if (contents.isEmpty()) {
            return;
        }
        contents.clear();
        changedAndSync();
    }

    /** 比较器档位：总量 / 总容量（32 种各满）。 */
    public int comparatorOutput() {
        int full = CAPACITY * MAX_KINDS;
        int total = totalAmount();
        if (total <= 0) {
            return 0;
        }
        return 1 + (int) ((long) Math.min(total, full) * 14L / full);
    }

    // ------------------------------------------------------------------
    // EssentiaTransport
    // ------------------------------------------------------------------

    @Override
    public boolean isConnectable(Direction face) {
        return true;
    }

    @Override
    public boolean canInputFrom(Direction face) {
        return true;
    }

    @Override
    public boolean canOutputTo(Direction face) {
        return true;
    }

    /**
     * 想要什么要素：<b>null = 什么都收</b>。
     *
     * <p>本体原质罐在这里报自己那一种（于是别的要素进不来）。这只罐是多要素的，
     * 报 null 才和「空的普通罐」一样对任何要素都有吸力；种类满了之后靠
     * {@link #hasRoom} 把吸力降到 0，管子自然就不推了。</p>
     */
    @Nullable
    @Override
    public AspectId suctionType(Direction face) {
        return null;
    }

    @Override
    public int suctionAmount(Direction face) {
        return hasRoom() ? SUCTION : 0;
    }

    @Override
    public int takeEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        return canOutputTo(face) ? extract(aspect, amount, mode.executes()) : 0;
    }

    @Override
    public int addEssentia(AspectId aspect, int amount, Direction face, EssentiaTransferMode mode) {
        if (!canInputFrom(face)) {
            return 0;
        }
        return amount - insert(aspect, amount, mode.executes());
    }

    /** 现在是哪种要素（给显示 / 管子读）：取占得最多的那种。 */
    @Nullable
    @Override
    public AspectId essentiaType(Direction face) {
        return dominant();
    }

    @Override
    public int essentiaAmount(Direction face) {
        return fullestAmount();
    }

    @Override
    public int minimumSuction() {
        return SUCTION;
    }

    /**
     * 任意取一种「现在抽得出来」的要素。
     *
     * <p>管子按「随便来一种」抽的时候先问这个；返回占得最多的那种即可 ——
     * 罐里只要还有东西，就一定有一个非空的答案。</p>
     */
    @Nullable
    @Override
    public AspectId extractableAspect(Direction face) {
        return dominant();
    }

    /**
     * 某一种要素现在能抽走多少。
     *
     * <p>★ <b>多要素容器必须覆写这一条</b>：默认实现只会拿 {@link #essentiaType} 那一种
     * 来比对，罐里另外那十几种会被一律报成 0 —— 管子就永远抽不走它们。
     * 这里直接按要素查表。</p>
     */
    @Override
    public int availableEssentia(AspectId aspect, Direction face) {
        return amount(aspect);
    }

    @Override
    public boolean renderExtendedTube() {
        return true;
    }

    // ------------------------------------------------------------------
    // EssentiaSource（注魔矩阵 / 玄枢基座从这里抽）
    // ------------------------------------------------------------------

    /**
     * 远程抽取：<b>一次付清</b>语义 —— 要么给满 {@code requested} 份，要么一份不给。
     *
     * <p>本体 {@code EssentiaApi} 会校验返回值只允许 0 或原样返回，中间值会被判成
     * 「非法结果」直接丢掉。注魔矩阵与玄枢基座都按「一份一份地问」在抽，
     * 所以这里付得满就付、付不满就报 0，让它们换下一个源。</p>
     */
    @Override
    public int extractEssentia(AspectId aspect, int requested, EssentiaTransferMode mode) {
        if (requested <= 0 || amount(aspect) < requested) {
            return 0;
        }
        return mode.executes() ? extract(aspect, requested, true) : requested;
    }

    // ------------------------------------------------------------------
    // EssentiaMultiStoreView（护目镜 / 谐振器读内容）
    // ------------------------------------------------------------------

    @Override
    public AspectAmounts visibleAspects() {
        if (contents.isEmpty()) {
            return AspectAmounts.EMPTY;
        }
        return new AspectAmounts(new LinkedHashMap<>(contents));
    }

    /**
     * 每种要素各能装多少份 —— 直接报 {@link #CAPACITY}（2048）。
     *
     * <p>{@code EssentiaStorageView} 的约定是「0 = 不知道 / 无上限」，所以这里必须报真数，
     * 本体的 Jade 才会把每种要素画成「数量 / 2048」而不是只报裸数量。</p>
     *
     * <p>返回值是<b>每种</b>的上限，不是整罐总量：配 {@link #sharedEssentiaCapacity()}
     * 返回 {@code false} 一起看 —— 罐子是按要素分别计数的，32 种各占 2048，不共享那一格。</p>
     */
    @Override
    public int essentiaCapacity() {
        return CAPACITY;
    }

    /**
     * 所有要素是不是共用同一份容量 —— {@code false}：每个要素各有一格 2048。
     *
     * <p>这一条决定本体 Jade 的排版：{@code false} 走「每种要素各画 数量/上限」，
     * {@code true} 才会合并成一行「总量 / 总容量」。我们显然是前者。</p>
     */
    @Override
    public boolean sharedEssentiaCapacity() {
        return false;
    }

    // ------------------------------------------------------------------
    // 物品组件：内容随罐子一起被挖走
    // ------------------------------------------------------------------

    /**
     * 把内容写成物品组件（空罐返回 {@code null}，<b>不挂组件</b>）。
     *
     * <p>空罐一定要返回 null：组件不同的物品不能互相堆叠，空罐要是也挂一个空表，
     * 它就和刚合成出来的罐子不是同一种堆，玩家会发现「两只空罐叠不起来」。
     * 本体防护罐也是这么处理的（{@code amount == 0 && filter == null} 时不写组件）。</p>
     */
    @Nullable
    private CompoundTag componentTag() {
        if (contents.isEmpty()) {
            return null;
        }
        CompoundTag tag = new CompoundTag();
        contents.forEach((aspect, amount) -> {
            if (amount > 0) {
                tag.putInt(aspect.serialized(), amount);
            }
        });
        return tag.isEmpty() ? null : tag;
    }

    /**
     * 从物品组件里读回内容（{@code null} = 物品上没这个组件，<b>一个字节都别动</b>）。
     *
     * <p>★ 「没组件就不动」这一条是必须的：放置时原版会先把
     * {@code BLOCK_ENTITY_DATA}（{@link #loadAdditional} 读的那份）灌进来，
     * 随后 {@code setPlacedBy} 又调一次本方法。要是这里无条件 {@code clear()}，
     * 走旧数据那条路的罐子就被清空了。</p>
     */
    private void loadComponentTag(@Nullable CompoundTag tag) {
        if (tag == null) {
            return;
        }
        contents.clear();
        for (String key : tag.getAllKeys()) {
            AspectId aspect = parseAspect(key);
            int amount = Math.min(CAPACITY, tag.getInt(key));
            if (aspect != null && amount > 0 && contents.size() < MAX_KINDS) {
                contents.merge(aspect, amount, Integer::sum);
            }
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        loadComponentTag(input.get(ModDataComponents.JAR_ESSENTIA.get()));
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder output) {
        super.collectImplicitComponents(output);
        CompoundTag tag = componentTag();
        if (tag != null) {
            output.set(ModDataComponents.JAR_ESSENTIA.get(), tag);
        }
    }

    // ------------------------------------------------------------------
    // 同步与存档
    // ------------------------------------------------------------------

    private void changedAndSync() {
        this.setChanged();
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

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        contents.forEach((aspect, amount) -> {
            if (amount <= 0) {
                return;
            }
            CompoundTag entry = new CompoundTag();
            entry.putString("Aspect", aspect.serialized());
            entry.putInt("Amount", amount);
            list.add(entry);
        });
        tag.put("Essentia", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        contents.clear();
        ListTag list = tag.getList("Essentia", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && contents.size() < MAX_KINDS; i++) {
            CompoundTag entry = list.getCompound(i);
            AspectId aspect = parseAspect(entry.getString("Aspect"));
            int amount = Math.min(CAPACITY, entry.getInt("Amount"));
            if (aspect != null && amount > 0) {
                contents.merge(aspect, amount, Integer::sum);
            }
        }
    }

    /**
     * 读档时解析要素 id：{@link AspectId#parse} 遇脏数据会抛，
     * 手改存档或换了本体版本都不该让整个区块加载跟着崩。
     */
    @Nullable
    private static AspectId parseAspect(String serialized) {
        try {
            return AspectId.parse(serialized);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
