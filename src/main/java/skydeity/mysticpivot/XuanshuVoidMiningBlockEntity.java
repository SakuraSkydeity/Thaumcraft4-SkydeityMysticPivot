package skydeity.mysticpivot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 玄枢虚空采矿机的方块实体（第一百七十四批；第一百八十六批去掉「锚点 / 核心」两格模型）。
 *
 * <h2>一百八十六批的变化</h2>
 * <p>旧的 {@code corePos} / 「只有锚点干活」整套删掉：机器现在<b>只有一格</b>，
 * 它自己就是锚点。激活与否不再看法杖，而看周围那座
 * {@link VoidMiningStructure 星枢光坛}齐不齐（{@link XuanshuVoidMiningBlock#FORMED}）。</p>
 *
 * <h2>产出：所有矿，通用标签</h2>
 * <p>产出清单从 <b>{@code #c:ores}</b>（NeoForge conventional item tag，含
 * {@code #forge:ores} 兼容）收集——凡是注册进这个标签的物品（所有模组的矿石、
 * 含 {@code minecraft:ancient_debris} 即下界合金原料）都能被产出，
 * <b>不单独适配任何模组</b>。清单在服务端首次使用时构建并缓存（静态），
 * 换世界 / 重启后自动重建。</p>
 *
 * <h2>节奏（第一百九十九批：拉到与三台工作方块同拍）</h2>
 * <p>光坛齐（{@code formed=true}）时，<b>每 tick 出一批</b>（{@link #CYCLE_TICKS} = 1）——
 * 炎炉 / 坩埚 / 炼金炉的 {@code PROCESS_TICKS} 全是 1，这台塔既然叫「最快」，就不该比它们慢。</p>
 *
 * <p>「快」全压在<b>一批里装多少</b>上，而不是「多跑几遍」：一批一次点
 * {@link #TYPES_PER_CYCLE} <b>种不同</b>的矿（不重复），每种 {@link #MIN_PER_TYPE}~{@link #MAX_PER_TYPE} 个，
 * 所以「不同类型也一起变快」。栏满则暂停（不丢失、不覆盖）。</p>
 *
 * <p>★ 第二百一十五批：<b>先补货、再添新</b>（用户「水晶塔产矿效率再快一点，
 * 放上 3 个玄枢锭要 1 秒满」）。旧写法每 tick 都从随机起点取 13 种<b>新</b>矿，
 * 种类一直摊开，12 格里每格只攒到三五件，锭槽插满也等不到「满」——
 * 现在每个 tick 先给栏里<b>已有</b>的每一格按档位补一份（3 枚玄枢锭 = 每格 +4），
 * 12 格 × 4 = 48 件/刻 ⇒ 从空栏到 12 格各 64 只要 <b>17 tick（0.85 秒）</b>；
 * 空栏第一刻仍然先铺 12 种，让玩家一眼看出这座塔在挖什么。</p>
 *
 * <h2>TPS：一 tick 的活是常数</h2>
 * <ul>
 *   <li><b>不再每产一次就发一个方块更新包</b>：产物直接进栏，客户端靠菜单的槽位同步看；
 *       方块实体包按 {@link #SYNC_INTERVAL} tick 节流、且只在真的动过之后发一次；</li>
 *   <li><b>不再每产一次就响一声</b>：{@link #SOUND_INTERVAL} tick 最多一声；</li>
 *   <li>矿物清单 <b>静态缓存</b>（只建一次），结构自检仍是 <b>一秒一次</b>，
 *       自动输出空闲时<b>一次能力查询都不做</b>；</li>
 *   <li>一批的代价 = 12 次「补货」+ 至多 12 次「添新」，与批量大小无关 —— 想更快就调
 *       {@link #MAX_PER_TYPE} 的系数，不会多一个 tick 任务。</li>
 * </ul>
 *
 * <p><b>未激活不产出</b>——这是光坛存在的意义。</p>
 *
 * <h2>第二百一十二批：输出对齐炎炉 + 三格锭槽</h2>
 * <ul>
 *   <li><b>输出</b>：用户「水晶塔输出产物的效率跟玄枢熔炉一样，要最快，指的是输出而不是生成矿物」。
 *       炎炉的自动输出走六个面（默认下面就是输出面），这台塔原来只推东西南北。
 *       现在同样是<b>六面、每 tick、整摞</b>地推，推法与 {@code XuanshuInfernalFurnaceBlockEntity#autoOutput}
 *       逐行对齐；停转（光坛塌了）时也照样把栏里剩的推出去。</li>
 *   <li><b>锭槽</b>：产物栏左边竖排三格，各收一枚<b>四神兽之锭（+1）/ 未烬的玄枢锭（+2）/
 *       玄枢锭（+3）</b>（档位次序第二百一十三批按用户口径重排），不消耗，合计 0~9 级。
 *       效率只加在<b>产出批量</b>上：种类 4 + 等级、每种 1 ~ 2 + 等级/4，满级一批 13 种 × 1~4 个。</li>
 * </ul>
 */
public class XuanshuVoidMiningBlockEntity extends BlockEntity implements MenuProvider {

    /**
     * 产出周期（tick）。
     *
     * <p>第一百九十九批从 {@code 100}（5 秒）改成 {@code 1}：与三台工作方块同拍
     * （{@code XuanshuInfernalFurnaceBlockEntity.SMELT_INTERVAL} = 1、
     * {@code XuanshuCrucibleBlockEntity.PROCESS_TICKS} = 1、
     * {@code XuanshuAlchemyFurnaceBlockEntity.PROCESS_TICKS} = 1）。</p>
     */
    public static final int CYCLE_TICKS = 1;

    /**
     * 一批里点<b>几种不同</b>的矿（空着锭槽时的底数）。
     *
     * <p>不是「同一种多给几个」—— 用户口径「不同类型输出也变快」：
     * 一批就铺开 4 种，既不重复，也让输出栏一眼看得出这座塔在挖什么。
     * 第二百一十二批起，锭槽里的锭<b>再加种类</b>（见 {@link #typesPerCycle()}）。</p>
     */
    public static final int TYPES_PER_CYCLE = 4;

    /** 每种几个的下限 / 上限（空着锭槽时的底数）。 */
    public static final int MIN_PER_TYPE = 1;
    public static final int MAX_PER_TYPE = 2;

    /**
     * 锭槽拉满时，一批最多点几种（4 + 3 格 × 最高 3 级 = 13）。
     *
     * <p>封这个上限是为了 TPS：一批的代价就是「种类数」次合并进栏，
     * 13 次/刻仍是常数级，再多就没有意义了（产出栏一共只有 12 格）。</p>
     */
    public static final int MAX_TYPES_PER_CYCLE = 13;

    /**
     * 界面竖条一圈的 tick。
     *
     * <p>周期缩到 1 tick 之后，「这一炉还剩多少」已经没有意义了，竖条改成
     * <b>运转指示</b>：一秒扫一圈，机器在转它就一直在动。</p>
     */
    public static final int SWEEP_TICKS = 20;

    /** 产出栏格数：3 行 × 4 列（第一百九十三批从 3×3 加了一列）。 */
    public static final int OUT_SLOTS = 12;

    /**
     * 锭槽格数（第二百一十二批新增）：产物栏左边竖着三格。
     *
     * <p>用户口径「给产物栏左边隔远一点添加竖着的 3 个格子，里面画上锭的描边……
     * 可以添加四神兽锭或者玄枢锭 / 未尽的玄枢锭，添加矿机的产矿效率」。
     * 三格各收一枚锭，<b>不消耗</b>（AE 升级卡那种思路：装上就一直生效），
     * 按锭的档位加效率（越贵越高：四神兽 +1 / 未尽的玄枢锭 +2 / 玄枢锭 +3），
     * 见 {@link #tierOf(ItemStack)}。</p>
     */
    public static final int UPGRADE_SLOTS = 3;

    /** 锭槽在物品栏里的起始下标（0~11 是产出栏，12~14 是锭槽）。 */
    public static final int UPGRADE_BASE = OUT_SLOTS;

    /** 整台机器的槽位总数（产出栏 + 锭槽）= 背包槽的起始下标。 */
    public static final int SLOT_COUNT = OUT_SLOTS + UPGRADE_SLOTS;

    /**
     * 自动输出的节拍（tick）。
     *
     * <p>第一百九十九批从 10 收到 1：一批 4~8 个、每 tick 一批，十 tick 才推一次的话
     * 12 格会在两秒内塞满然后停转。<b>空闲时一分钱不花</b>（见 {@link #autoOutput()} 的短路）。</p>
     */
    public static final int AUTO_IO_TICKS = 1;

    /** 结构自检的节拍（tick）：一秒一次（这一步是整台机器最贵的一次，不必更快）。 */
    public static final int STRUCTURE_CHECK_TICKS = 20;

    /** 方块实体包的节流（tick）：产物变动攒到这个节拍才发一次。 */
    public static final int SYNC_INTERVAL = 10;

    /** 产出音效的节流（tick）：两秒最多一声，不跟着 tick 走。 */
    public static final int SOUND_INTERVAL = 40;

    /** 进度（tick）。 */
    private int progress;

    /** 攒着没发给客户端的变动（tick 计数，到 {@link #SYNC_INTERVAL} 清一次）。 */
    private int syncCooldown;

    /** 上一秒自检时光坛齐不齐（客户端也要读，用于界面上的状态字）。 */
    private boolean formed;

    private final ItemStackHandler items = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            // 锭槽里的锭一变，效率就变，界面上的「效率 +N」要立刻跟上
            if (slot >= UPGRADE_BASE) {
                syncToClient();
            }
        }

        /** 锭槽只放一枚：它是「装上去的升级件」，不是往里倒的燃料。 */
        @Override
        public int getSlotLimit(int slot) {
            return slot >= UPGRADE_BASE ? 1 : super.getSlotLimit(slot);
        }

        /**
         * 产出栏对<b>本机自己的产出</b>永远可写（{@link #produce()} 走的就是这个口），
         * 对外的那份能力（{@link #getExternalItems}）另有一层「只许塞空格」的拦截；
         * 锭槽只收三种锭。
         */
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot < UPGRADE_BASE || tierOf(stack) > 0;
        }
    };

    // ------------------------------------------------------------------
    // 锭槽：三枚锭换产矿效率（第二百一十二批）
    // ------------------------------------------------------------------

    /**
     * 一枚锭值几级效率。
     *
     * <p>第二百一十三批按用户口径重排：「四神兽之锭效率最低，未尽第二，玄枢效率最高」。
     * 四神兽之锭是四象锭那条线的成品（相对最容易量产），玄枢锭才是这条科技树的顶点，
     * 所以档位是 <b>四神兽 +1 / 未尽的玄枢锭 +2 / 玄枢锭 +3</b>。
     * <b>三格全插玄枢锭 = 9 级</b>，一批就从 4 种 × 1~2 个涨到 13 种 × 1~4 个。</p>
     *
     * <p>锭<b>不消耗</b>：插上去就是一块「升级件」，拆下来还你。想让玩家付出代价的话，
     * 在这里加一句「用一次扣一点耐久」之类的都很容易，但那是另一套设计，本批没做。</p>
     */
    public static int tierOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        Item item = stack.getItem();
        if (item == ModItems.XUANSHU_INGOT.get()) {
            return 3;
        }
        if (item == ModItems.XUANSHU_INGOT_UNFIRED.get()) {
            return 2;
        }
        if (item == ModItems.AZURE_DRAGON_INGOT.get()
                || item == ModItems.VERMILION_BIRD_INGOT.get()
                || item == ModItems.WHITE_TIGER_INGOT.get()
                || item == ModItems.BLACK_TORTOISE_INGOT.get()) {
            return 1;
        }
        return 0;
    }

    /** 锭槽能不能收这一件（菜单的 {@code mayPlace} 与外部能力都问这一句）。 */
    public static boolean isUpgrade(ItemStack stack) {
        return tierOf(stack) > 0;
    }

    /** 三格锭槽加起来几级效率（0 ~ 9）。 */
    public int efficiency() {
        int sum = 0;
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            sum += tierOf(items.getStackInSlot(UPGRADE_BASE + i));
        }
        return sum;
    }

    /**
     * 这一批点几种矿：4 + 效率等级（上限 {@link #MAX_TYPES_PER_CYCLE}）。
     *
     * <p>★ 第二百一十五批：这里只是「上一批最多铺几种新种类」，实际还会被
     * {@code 产出栏格数}（{@link #OUT_SLOTS} = 12）截一刀 —— 多出来的种类没有空格可站。</p>
     */
    public int typesPerCycle() {
        return Math.min(TYPES_PER_CYCLE + efficiency(), MAX_TYPES_PER_CYCLE);
    }

    /** 每种几个的上限：2 + 效率/4（每四级涨一个，封顶 4）。 */
    public int maxPerType() {
        return MAX_PER_TYPE + efficiency() / 4;
    }

    // ------------------------------------------------------------------
    // 矿物清单（#c:ores）
    // ------------------------------------------------------------------

    private static final TagKey<Item> ORES_TAG =
            TagKey.create(net.minecraft.core.registries.Registries.ITEM,
                    ResourceLocation.fromNamespaceAndPath("c", "ores"));

    /** 缓存的矿物清单（服务端；null = 还没建）。 */
    @Nullable
    private static volatile List<Item> oresCache;

    /** 从 {@code #c:ores} 收集所有矿物物品（缓存）。 */
    private static List<Item> ores() {
        List<Item> cache = oresCache;
        if (cache == null) {
            synchronized (XuanshuVoidMiningBlockEntity.class) {
                cache = oresCache;
                if (cache == null) {
                    List<Item> built = new ArrayList<>();
                    BuiltInRegistries.ITEM.getTag(ORES_TAG).ifPresent(holders ->
                            holders.forEach(holder -> built.add(holder.value())));
                    cache = built;
                    oresCache = built;
                }
            }
        }
        return cache;
    }

    public XuanshuVoidMiningBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.XUANSHU_VOID_MINING.get(), pos, state);
    }

    // ------------------------------------------------------------------
    // 状态读写
    // ------------------------------------------------------------------

    /** 光坛齐不齐（服务端每秒刷一次，客户端从数据包同步）。 */
    public boolean isFormed() {
        return formed;
    }

    /**
     * 立刻重算一次激活态（界面按完「搭建 / 收回」后调）。
     *
     * <p>不这么做的话，按完按钮要等下一秒的自检才点亮，玩家会以为没生效。</p>
     */
    public void refreshFormedNow() {
        if (!(this.level instanceof ServerLevel server)) {
            return;
        }
        boolean now = XuanshuVoidMiningBlock.refreshFormed(server, this.worldPosition);
        if (now != this.formed) {
            this.formed = now;
        }
        this.setChanged();
        this.syncToClient();
    }

    public ItemStackHandler getItems() {
        return items;
    }

    /**
     * 界面竖条的当前值（0 ~ {@link #SWEEP_TICKS}）。
     *
     * <p>周期 1 tick 之后「这一炉还剩多少」没有意义了，竖条改成<b>运转指示</b>：一秒扫一圈。
     * 纯读 {@code gameTime}，不写字段、不参与存档。<b>没激活就恒为 0</b>（条空着）——
     * 否则塔塌了条还在扫，看着像还在出矿。</p>
     */
    public int getSweep() {
        if (!formed || this.level == null) {
            return 0;
        }
        return (int) Math.floorMod(this.level.getGameTime(), SWEEP_TICKS);
    }

    // ------------------------------------------------------------------
    // 每 tick
    // ------------------------------------------------------------------

    @SuppressWarnings("unused")
    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  XuanshuVoidMiningBlockEntity be) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        // 一秒一次结构自检：齐了就点亮，缺格就停转
        if (level.getGameTime() % STRUCTURE_CHECK_TICKS == 0L) {
            boolean formed = XuanshuVoidMiningBlock.refreshFormed(server, pos);
            if (formed != be.formed) {
                be.formed = formed;
                be.setChanged();
                be.syncToClient();
            }
        }
        if (!be.formed) {
            // 停转期间也应该把栏里剩的推出去：结构塌了不等于产物就地锁死
            if (level.getGameTime() % AUTO_IO_TICKS == 0L) {
                be.autoOutput();
            }
            if (be.progress != 0) {
                be.progress = 0;   // 停转期间不攒进度：光坛塌了重立不该立刻吐一组
                be.setChanged();
            }
            be.flushSync();
            return;
        }
        // 自动化：往机器四周的容器里推产物（光坛的柱子都在 4 格外，四面一格是空的）
        if (level.getGameTime() % AUTO_IO_TICKS == 0L) {
            be.autoOutput();
        }
        // 加速：倍率只看本机这一格有没有被烛龙衔时催着
        int rate = TemporalCore.rateAt(level, pos);
        int steps = rate > 0 ? rate : 1;
        be.progress += steps;
        int guard = 0;
        while (be.progress >= CYCLE_TICKS && guard++ < 64) {
            be.progress -= CYCLE_TICKS;
            if (!be.produce()) {
                be.progress = CYCLE_TICKS;   // 满仓：停在「下一轮就出」，等有格子再走
                break;
            }
        }
        be.setChanged();
        // 方块实体包节流：产物每秒动 20 次，包只发两次
        be.flushSync();
    }

    /** 攒着的变动到节拍了就发一次（{@link #SYNC_INTERVAL} tick 至多一包）。 */
    private void flushSync() {
        if (syncCooldown <= 0) {
            return;
        }
        if (++syncCooldown >= SYNC_INTERVAL) {
            syncCooldown = 0;
            syncToClient();
        }
    }

    /** 记一笔「客户端需要重读」，真正发包由 {@link #flushSync()} 攒批发。 */
    private void markClientDirty() {
        if (syncCooldown == 0) {
            syncCooldown = 1;   // 从 1 起算，下一次 flushSync 才开始数
        }
    }

    /**
     * 产出一批矿：<b>先给栏里已有的矿补货</b>，再把没出现过的种类铺进空格。
     *
     * <p>直接<b>合并进产出栏</b>（先叠到同种已占的格子上，再找空格）——
     * 所以 12 格实际能压 12 × 64 件，不必每 tick 都找空格。
     * 每次产出发包、发声都<b>不在这里做</b>：包走 {@link #flushSync()} 节流，
     * 声音按 {@link #SOUND_INTERVAL} 节流，一 tick 的成本就是十几次合并。</p>
     *
     * <p>第二百一十二批：种类与每种个数都改问 {@link #typesPerCycle()} / {@link #maxPerType()}，
     * 两处都随锭槽里的锭上浮。</p>
     *
     * <p>★ 第二百一十五批：<b>补货优先</b>。旧写法每 tick 从随机起点取一批<b>新</b>种类，
     * 同一格要等很久才会被轮到第二次，于是 12 格永远是一层薄薄的「每种十来个」，
     * 锭槽插满也看不出「满」。现在每 tick 先按档位把<b>已有的每一格</b>各补一份
     * （三枚玄枢锭 = 每格 +4，12 格共 48 件/刻 ⇒ 64 件只要 16 刻），
     * 剩下的名额才拿去铺新种类（最多铺满 12 格）。</p>
     *
     * @return 至少塞进去一件；{@code false} = 产出栏真的满了（这一轮作废，不丢进度）
     */
    private boolean produce() {
        List<Item> ores = ores();
        if (ores.isEmpty() || !(level instanceof ServerLevel server)) {
            return false;
        }
        RandomSource random = server.getRandom();
        int perStack = maxPerType();
        int placed = 0;

        // ① 补货：栏里已经有的矿，每格按当前档位再补一份 —— 「1 秒满」靠的是这一步
        Set<Item> present = new HashSet<>();
        for (int slot = 0; slot < OUT_SLOTS; slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (stack.isEmpty() || !ores.contains(stack.getItem())) {
                continue;
            }
            present.add(stack.getItem());
            if (stack.getCount() >= stack.getMaxStackSize()) {
                continue;
            }
            ItemStack left = ItemHandlerHelper.insertItemStacked(items,
                    new ItemStack(stack.getItem(), perStack), false);
            placed += perStack - left.getCount();
        }

        // ② 添新：空格里补上还没出现过的种类（上限 = 产出栏格数，多出来的种也没地方站）
        int size = ores.size();
        int types = Math.min(typesPerCycle(), Math.min(size, OUT_SLOTS));
        int start = random.nextInt(size);
        int added = 0;
        for (int i = 0; i < size && added < types; i++) {
            // 从随机起点<b>顺序</b>取，所以这一批铺开的几样一定互不相同
            Item ore = ores.get((start + i) % size);
            if (present.contains(ore) || !hasEmptyOutSlot()) {
                continue;
            }
            int count = MIN_PER_TYPE + random.nextInt(perStack - MIN_PER_TYPE + 1);
            ItemStack left = ItemHandlerHelper.insertItemStacked(items, new ItemStack(ore, count), false);
            int moved = count - left.getCount();
            if (moved > 0) {
                present.add(ore);
                placed += moved;
                added++;
            }
        }
        if (placed <= 0) {
            return false;
        }
        setChanged();
        markClientDirty();
        if (server.getGameTime() % SOUND_INTERVAL == 0L) {
            server.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.BLOCKS, 0.35F, 1.5F);
        }
        return true;
    }

    /** 产出栏还有空格吗（添新种类只看这个：12 格塞满就只剩补货）。 */
    private boolean hasEmptyOutSlot() {
        for (int slot = 0; slot < OUT_SLOTS; slot++) {
            if (items.getStackInSlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 自动化：机器四面的容器
    // ------------------------------------------------------------------

    /**
     * 把产出栏推给机器<b>四周六个面</b>的容器。
     *
     * <p>第一百八十七批起光坛整个退到机器<b>背后</b>，最近的一圈也在第 3 格上，
     * 机器紧挨着的四面永远是空的 —— 箱子、漏斗、管道就摆在那里。和坩埚 / 炼金炉那套
     * 一样是「机器自己推」，不要求对方是管道；推不进去的留在栏里，不丢。</p>
     *
     * <p>第一百九十九批改成<b>每 tick 推一轮</b>（不推就跟不上每 tick 一批的产出），
     * 同时补了一条短路：<b>栏里一件东西都没有就直接返回</b> ——
     * 没接容器、或者接满了，这台塔在空闲时连一次能力查询都不做。</p>
     *
     * <p>★ 第二百一十二批：用户「水晶塔输出产物的效率跟玄枢熔炉一样，要最快，
     * 指的是输出而不是生成矿物」。炎炉的自动输出走 {@code Direction.values()}，
     * 默认把<b>下面</b>也算输出面；这台塔原来只推<b>东西南北</b>，正下方（和头顶）
     * 摆的漏斗、管道一律收不到东西 —— 玩家眼里就是「产物堵在塔里不动」。
     * 现在同样走六个面、每 tick 一轮、整摞推，推法与炎炉逐行对齐。</p>
     */
    private void autoOutput() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (isEmpty()) {
            return;
        }
        boolean moved = false;
        for (Direction dir : Direction.values()) {
            BlockPos target = worldPosition.relative(dir);
            IItemHandler neighbor = server.getCapability(Capabilities.ItemHandler.BLOCK,
                    target, dir.getOpposite());
            if (neighbor == null) {
                continue;
            }
            for (int slot = 0; slot < OUT_SLOTS; slot++) {
                ItemStack out = items.getStackInSlot(slot);
                if (out.isEmpty()) {
                    continue;
                }
                ItemStack refused = ItemHandlerHelper.insertItemStacked(neighbor, out.copy(), true);
                int accepted = out.getCount() - refused.getCount();
                if (accepted <= 0) {
                    continue;
                }
                ItemStack extracted = items.extractItem(slot, accepted, false);
                ItemStack leftover = ItemHandlerHelper.insertItemStacked(neighbor, extracted, false);
                if (!leftover.isEmpty()) {
                    items.insertItem(slot, leftover, false);
                }
                moved = true;
            }
        }
        if (moved) {
            setChanged();
            markClientDirty();
        }
    }

    /** 产出栏是不是空的（十二次引用比较，比六次能力查询便宜得多）。 */
    private boolean isEmpty() {
        for (int slot = 0; slot < OUT_SLOTS; slot++) {
            if (!items.getStackInSlot(slot).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 对外暴露的物品能力（管道 / 漏斗直接接在机器六个面上）。
     *
     * <p>和旧版一样只许塞进空格：产出栏被外面塞进来的东西合并掉，
     * 这一炉的矿石就没地方放了。</p>
     *
     * <p>★ 第二百一十二批：对外只暴露<b>产出栏那 12 格</b>（{@link #OUT_SLOTS}）。
     * 锭槽是玩家手动插的升级件，不能被管道当成「机器的一个格子」随便抽走。</p>
     */
    public IItemHandler getExternalItems(@Nullable Direction side) {
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return OUT_SLOTS;
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return items.getStackInSlot(slot);
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                if (!items.getStackInSlot(slot).isEmpty()) {
                    return stack;
                }
                return items.insertItem(slot, stack, simulate);
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return items.extractItem(slot, amount, simulate);
            }

            @Override
            public int getSlotLimit(int slot) {
                return items.getSlotLimit(slot);
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return items.isItemValid(slot, stack);
            }
        };
    }

    /** 挖掉机器时把产出散出来。 */
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
    // 同步与存档
    // ------------------------------------------------------------------

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

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", items.serializeNBT(registries));
        tag.putInt("Progress", progress);
        tag.putBoolean("Formed", formed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Items")) {
            items.deserializeNBT(registries, tag.getCompound("Items"));
        }
        progress = tag.getInt("Progress");
        formed = tag.getBoolean("Formed");
    }

    // ------------------------------------------------------------------
    // MenuProvider
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.skydeitymysticpivot.xuanshu_void_mining");
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new XuanshuVoidMiningMenu(id, inv, this);
    }
}
