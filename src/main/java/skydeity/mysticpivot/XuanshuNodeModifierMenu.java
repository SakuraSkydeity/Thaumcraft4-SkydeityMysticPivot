package skydeity.mysticpivot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.aspect.VisChannel;
import dev.tc4port.thaumcraft.api.node.AuraNodeState;
import dev.tc4port.thaumcraft.api.node.AuraNodeView;
import dev.tc4port.thaumcraft.api.node.NodeTypeId;
import dev.tc4port.thaumcraft.api.node.NodeVis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

/**
 * 玄枢注灵台（第八十九批<b>方块版</b>）的容器菜单：左**华槽** + 右**锭槽** + 玩家背包。
 *
 * <h2>界面与运转怎么分工</h2>
 * <ul>
 *   <li><b>界面</b>只负责把两格材料摆进去——真正的改易动作不在按钮上：玩家
 *       <b>持法杖右键方块</b>才运转（见
 *       {@link ModBlockEntities.XuanshuNodeModifierBlockEntity#applyWithWand}
 *       与 {@code ModBlocks.XuanshuNodeModifierBlock#useItemOn}），走 TC4 的
 *       「法杖 = 施术媒介」语义；</li>
 *   <li>运转逻辑（抽要素 / 加要素 / CAS 写回）全在方块实体里，本类只把
 *       {@link #isWispEssence}、{@link #nextAspect}、{@link #addAspects}
 *       三个静态工具借给它用（它 import 不到本包的编译单元，放这里正合适）。</li>
 * </ul>
 *
 * <h2>第九十七批：第二槽由「六件转化物」改为「玄枢锭」</h2>
 * <p>用户原话：「里面两个槽位改为天域之华 和 玄枢锭，修改上方缸中节点的要素」。
 * 于是 {@link #SLOT_INGOT} 只收<b>玄枢锭</b>当运转费（每次一枚），
 * {@code transformTarget} / {@code transformItems} 那套六件物品改类型的表
 * <b>整组删除</b>——机器从此只加要素、不改类型。</p>
 *
 * <h2>第九十八批：华槽的「内容」也不看了</h2>
 * <p>用户原话：「里面是放任意天华和玄枢锭，上方的节点随机加一要素」。
 * 于是 {@code wispAspects}（读天华里封着哪几门要素）<b>删除</b>——
 * 华槽只判「这一格是不是天域之华」（{@link #isWispEssence}），
 * 天域之华<b>不是安瓿</b>：它是一件封好的成品、没有「空瓶」这种中间状态，
 * 所以这里既不读内容也不判剩余量，运转一次整件吃掉一件。</p>
 *
 * <h2>第九十九批：加哪一门改成「先基础、后抽奖」</h2>
 * <p>用户原话：「效果改为添加的先是 6 种基础，然后再添加其他随机的一种 一直抽奖就行」。
 * 抽签逻辑集中在 {@link #nextAspect}：先按 风→地→火→水→序→灭 补齐六门基础
 * （缺哪门补哪门，不赌运气），六门齐了之后从<b>非基础要素</b>里等概率一直抽。
 * 每抽中一门涨 {@value #ASPECT_AMOUNT}。</p>
 *
 * <h2>情报同步</h2>
 * <p>{@link #DATA_TYPE}:节点类型序 / {@link #DATA_KINDS}:要素种类数 /
 * {@link #DATA_CURRENT}:要素现存量 / {@link #DATA_BASE}:要素容量——四个 {@link ContainerData}
 * 槽每 tick 从「方块正上方的缸中节点」现读现同步（类型仍照旧显示，只是不再能改）。</p>
 *
 * <p>★ 关界面<b>不退还、不清空</b>：两槽是 BE 的 {@code ItemStackHandler}，关界面只是断开
 * 客户端连接，内容原地留着（用户反馈「物品放上去不能存着，关闭 ui 就到物品栏了」）；
 * 法杖右键运转那一刻才消耗，挖 / 炸 / 活塞推方块时才由 {@code dropContents} 散出。
 * 因此本类<b>不覆写 {@code removed()}</b>——第九十九批把那两个「只调 super」的空壳覆写删了。</p>
 */
public class XuanshuNodeModifierMenu extends AbstractContainerMenu {

    /** 天域之华槽（左）。 */
    public static final int SLOT_BLOOM = 0;

    /** 玄枢锭槽（右）：运转费，每次一枚。 */
    public static final int SLOT_INGOT = 1;

    private static final int SLOT_COUNT = 2;

    /** 一次运转给抽中的那门要素加多少（base/current 同涨）。 */
    public static final int ASPECT_AMOUNT = 12;

    /** 节点类型在数据槽里的序号（与 {@link NodeTypeId} 静态常量序一致）。 */
    private static final NodeTypeId[] TYPE_ORDER = {
            NodeTypeId.NORMAL, NodeTypeId.UNSTABLE, NodeTypeId.DARK,
            NodeTypeId.TAINTED, NodeTypeId.HUNGRY, NodeTypeId.PURE,
    };

    /** 数据槽下标。 */
    public static final int DATA_TYPE = 0;
    public static final int DATA_KINDS = 1;
    public static final int DATA_CURRENT = 2;
    public static final int DATA_BASE = 3;
    public static final int DATA_COUNT = 4;

    /** 界面坐标（Screen 同款，自绘面板）。 */
    public static final int BLOOM_X = 28, BLOOM_Y = 44;
    public static final int TRANS_X = 126, TRANS_Y = 44;

    @Nullable
    private final ModBlockEntities.XuanshuNodeModifierBlockEntity blockEntity;
    private final ItemStackHandler handler;
    private final ContainerData data;

    /** 客户端工厂（网络通道）：拿不到方块实体，数据槽全零，等同步。 */
    public XuanshuNodeModifierMenu(int id, Inventory inv) {
        this(id, inv, null);
    }

    /** 服务端：由方块实体的 {@code createMenu} 创建。 */
    public XuanshuNodeModifierMenu(int id, Inventory inv,
                                   @Nullable ModBlockEntities.XuanshuNodeModifierBlockEntity be) {
        super(ModMenus.XUANSHU_NODE_MODIFIER.get(), id);
        this.blockEntity = be;
        this.handler = be != null ? be.rawItems() : new ItemStackHandler(SLOT_COUNT);
        this.data = be != null ? nodeData(be) : new SimpleContainerData(DATA_COUNT);
        this.addDataSlots(this.data);

        // ★ 第九十六批：mayPlace 不再过滤（用户反馈「两个都放不了」——过滤在客户端
        //   就把点击拦死了，玩家拿错物料连放都放不进）。现在什么都让进，运转时
        //   按内容识别：华槽只认天域之华、锭槽只扣玄枢锭，放错的原样留着不烧。
        this.addSlot(new SlotItemHandler(this.handler, SLOT_BLOOM, BLOOM_X + 1, BLOOM_Y + 1));
        this.addSlot(new SlotItemHandler(this.handler, SLOT_INGOT, TRANS_X + 1, TRANS_Y + 1));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inv, 9 + row * 9 + col, 8 + col * 18, 112 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inv, col, 8 + col * 18, 170));
        }
    }

    /** 情报四个数据槽：现读方块正上方的缸中节点（客户端由菜单同步机制推送）。 */
    private static ContainerData nodeData(ModBlockEntities.XuanshuNodeModifierBlockEntity be) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                var level = be.getLevel();
                if (level instanceof ServerLevel server
                        && server.getBlockEntity(be.getBlockPos().above()) instanceof AuraNodeView view) {
                    AuraNodeState s = view.nodeState();
                    return switch (index) {
                        case DATA_TYPE -> typeOrdinal(s.type());
                        case DATA_KINDS -> s.baseVis().amounts().size();
                        case DATA_CURRENT -> s.currentVis().amounts().values().stream()
                                .mapToInt(Integer::intValue).sum();
                        case DATA_BASE -> s.baseVis().amounts().values().stream()
                                .mapToInt(Integer::intValue).sum();
                        default -> 0;
                    };
                }
                return 0;
            }

            @Override
            public void set(int index, int value) {
                // 只读数据槽：改了也会在下一 tick 被重新读取覆盖
            }

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    // ------------------------------------------------------------------
    // 静态工具（方块实体的运转直接借这三件）
    // ------------------------------------------------------------------

    /** 本体天域之华的物品（{@code thaumcraft:wisp_essence}，WispEssenceItem）。 */
    private static Item wispEssenceItem;

    /**
     * 是不是本体的<b>天域之华</b>（{@code thaumcraft:wisp_essence}）——按注册名判，不 import 本体实现包。
     *
     * <p>★ 天域之华<b>不是安瓿</b>：它是一件封好的成品，没有「用空了还剩个空瓶」这种中间状态，
     * 所以只需判「是不是这件物品」，既不用读内容、也不用判剩余量（第九十八批删掉了读内容那套，
     * 第九十九批把这条写进注释免得下回又画蛇添足加个「空瓶」判断）。</p>
     */
    public static boolean isWispEssence(ItemStack stack) {
        if (wispEssenceItem == null) {
            wispEssenceItem = BuiltInRegistries.ITEM
                    .get(ResourceLocation.fromNamespaceAndPath("thaumcraft", "wisp_essence"));
        }
        return stack.is(wispEssenceItem);
    }

    /**
     * 抽取池：六基元（风·地·火·水·序·灭）各自的要素 id，也是「先补基础」那段的上限。
     *
     * <p>直接由 {@link VisChannel} 取 {@code aspectId()}，与法杖魔力池、节点 vis 通道同一套 id。</p>
     */
    private static final AspectId[] PRIMAL_ASPECTS;

    static {
        VisChannel[] channels = VisChannel.values();
        PRIMAL_ASPECTS = new AspectId[channels.length];
        for (int i = 0; i < channels.length; i++) {
            PRIMAL_ASPECTS[i] = channels[i].aspectId();
        }
    }

    /**
     * 这一抽加哪一门（第九十八批「随机抽一门」→ 第九十九批「先基础、后抽奖」两段式）。
     *
     * <p>用户原话：「效果改为添加的先是 6 种基础，然后再添加其他随机的一种 一直抽奖就行」。
     * 于是分两段：</p>
     *
     * <ol>
     *   <li><b>先补六基元</b>：节点的 {@code baseVis} 里还缺哪一门就补哪一门
     *       （按 风→地→火→水→序→灭 找第一个 {@code <= 0} 的），
     *       于是连着六次运转必然把六门基础补齐——不用赌；</li>
     *   <li><b>六门齐了就一直抽奖</b>：从要素注册表里的<b>非基础要素</b>
     *       （{@code AspectApi.registry().compounds()}，含本 mod 的八卦）等概率抽一门往上加；
     *       注册表里一门复合要素都没有时，退回六基元里抽。</li>
     * </ol>
     *
     * <p>NodeVis 的零值条目会保留（TC4 的侵蚀生命周期要用），所以判「缺不缺」必须看
     * {@code amount <= 0} 而不是 {@code containsKey}。</p>
     */
    public static AspectId nextAspect(AuraNodeState state, net.minecraft.util.RandomSource random) {
        Map<AspectId, Integer> base = state.baseVis().amounts();
        for (AspectId primal : PRIMAL_ASPECTS) {
            if (base.getOrDefault(primal, 0) <= 0) {
                return primal;
            }
        }
        List<AspectId> pool = lotteryPool();
        if (!pool.isEmpty()) {
            return pool.get(random.nextInt(pool.size()));
        }
        return PRIMAL_ASPECTS[random.nextInt(PRIMAL_ASPECTS.length)];
    }

    /** 抽奖池：注册表里的非基础要素（排一次序，让同一档存档里的抽取顺序稳定）。 */
    private static List<AspectId> lotteryPool() {
        dev.tc4port.thaumcraft.api.aspect.AspectRegistryView registry =
                dev.tc4port.thaumcraft.api.aspect.AspectApi.registry();
        if (registry == null) {
            return List.of();
        }
        List<AspectId> pool = new ArrayList<>(registry.compounds());
        pool.sort(null);
        return pool;
    }

    /** 给节点加/涨多门要素（一瓶天域之华的每一门）：base 与 current 同涨。 */
    public static AuraNodeState addAspects(AuraNodeState state, Map<AspectId, Integer> aspects, int each) {
        LinkedHashMap<AspectId, Integer> base = new LinkedHashMap<>(state.baseVis().amounts());
        LinkedHashMap<AspectId, Integer> current = new LinkedHashMap<>(state.currentVis().amounts());
        aspects.keySet().forEach(id -> {
            base.merge(id, each, Integer::sum);
            current.merge(id, each, Integer::sum);
        });
        return state.withVis(new NodeVis(base), new NodeVis(current));
    }

    private static int typeOrdinal(NodeTypeId type) {
        for (int i = 0; i < TYPE_ORDER.length; i++) {
            if (TYPE_ORDER[i].equals(type)) {
                return i;
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------
    // 客户端读取（Screen 用）
    // ------------------------------------------------------------------

    /** 目标节点当前类型序号（0~5；-1 = 无节点 / 客户端未就绪）。 */
    public int nodeTypeOrdinal() {
        int v = this.data.get(DATA_TYPE);
        return v >= 0 && v < TYPE_ORDER.length ? v : -1;
    }

    public int nodeAspectKinds() {
        return this.data.get(DATA_KINDS);
    }

    public int nodeCurrentSum() {
        return this.data.get(DATA_CURRENT);
    }

    public int nodeBaseSum() {
        return Math.max(this.data.get(DATA_BASE), this.data.get(DATA_CURRENT));
    }

    /** 类型序号的 lang 键（Screen 画面板用）。 */
    public static String typeLangKey(int ordinal) {
        String[] keys = {"normal", "unstable", "dark", "tainted", "hungry", "pure"};
        return ordinal >= 0 && ordinal < keys.length ? "node.type." + keys[ordinal] : "node.type.none";
    }

    // ------------------------------------------------------------------
    // 原版菜单骨架
    // ------------------------------------------------------------------

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < SLOT_COUNT) {
            // 界面槽 → 背包
            if (!this.moveItemStackTo(stack, SLOT_COUNT, SLOT_COUNT + 36, true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // 背包 → 先试华槽（天域之华），再试锭槽（玄枢锭）
            if (!this.moveItemStackTo(stack, SLOT_BLOOM, SLOT_BLOOM + 1, false)
                    && !this.moveItemStackTo(stack, SLOT_INGOT, SLOT_INGOT + 1, false)) {
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
