package skydeity.mysticpivot;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.annotation.Nullable;

import dev.tc4port.thaumcraft.api.ThaumcraftApiHelper;
import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.aspect.VisChannel;
import dev.tc4port.thaumcraft.api.aspect.VisPaymentResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 玄枢奥术台方块实体（第八十九批新增）：一格<b>受附物品</b> + 一格<b>可选附魔书</b>，
 * {@link #applyWithWand} 一次运转 = 法杖六元各 {@value #VIS_PER_CHANNEL} 点。
 *
 * <h2>运转（用户口径的沿革）</h2>
 * <p>第九十六批：「一个是物品要增加附魔的，一个是染灵，放玄枢锭，附魔一次消耗一个」；
 * 第九十七批加码：「升级附魔同时消耗玄枢锭和法杖 6 种要素各 129 点」。
 * <b>第二百一十四批整组改口径</b>：</p>
 * <pre>
 * 奥术台将原来玄枢锭一格栏位，改为可以放附魔书（可选）放了就是给物品添加这个附魔书上所有
 * 然后附魔书消失，画一个和附魔书一样的轮廓，稍微淡一点……无论附魔能不能发挥作用直接加上。
 * 升级一次消耗 77 点法杖 6 种要素
 * </pre>
 * <p>于是：<b>染灵用的玄枢锭整条去掉</b>（槽位改收附魔书、且是<b>可选</b>的），
 * 代价只剩法杖六元各 {@value #VIS_PER_CHANNEL} 点（{@value #VIS_PER_CHANNEL_CENTIVIS} 厘，
 * 走 {@code ThaumcraftApiHelper.consumeExactVisFromWand}，六路逐路足额，哪一路不够整笔不扣）。
 * 一次运转做两件事：</p>
 * <ol>
 *   <li>物品<b>原有的每一项附魔 +1</b>（单条封顶 {@value #MAX_ENCHANT_LEVEL}）——与旧版一致；</li>
 *   <li>书槽里若放了<b>附魔书</b>，把书上<b>所有</b>附魔<b>原样并入</b>物品
 *       （同一条取两者较高等级），<b>不做任何「能不能生效」的判定</b>——锋利刻在镐上也照刻；
 *       并入成功后那本书<b>消耗一本</b>（书是「可选」的，不放就只做第 1 件事）。</li>
 * </ol>
 *
 * <p><b>为什么能「直接加上」</b>：附魔存在 {@link DataComponents#ENCHANTMENTS} 这个数据组件里，
 * 是一张 {@code 附魔 → 等级} 的表；原版铁砧 / 附魔台会先过一遍
 * {@code Enchantment.canEnchant} 与互斥表，这里<b>不走那两道</b>，直接往表里写 ——
 * 于是「锋利 V 的能量剑」「时运与精准采集同一条」都成立。这是用户明确要的效果，不是漏判。</p>
 *
 * <ul>
 *   <li>台上无物品：提示「台上并无物品」；</li>
 *   <li>既没有附魔、书槽也没有附魔书：提示「无从添加」（<b>不扣魔</b>）；</li>
 *   <li>原有附魔全封顶、书也带不进新东西：提示「诸附魔已至绝顶」（同样不扣魔）；</li>
 *   <li>法杖六元有缺口：提示六元不足，什么都不动。</li>
 * </ul>
 *
 * <p>法杖判定走 {@code WandApi.isBuiltinWand}（本体各级法杖都算，与节点修改器同口径）。</p>
 */
public final class XuanshuArcaneTableBlockEntity extends BlockEntity implements MenuProvider, StarReader {

    /** 物品槽（要加附魔的物品）。 */
    public static final int SLOT_ITEM = 0;

    /**
     * 书槽（第二百一十四批由「玄枢锭槽」改来）：放一本<b>附魔书</b>，可选。
     *
     * <p>放了就把书上所有附魔并进物品、书消耗一本；不放也能运转（只做「原有附魔各 +1」）。
     * 放别的东西不报错、也不吃，按空槽处理。</p>
     */
    public static final int SLOT_BOOK = 1;

    /**
     * 运转一次、每一种要素的消耗，单位是**整 Vis**（法杖面板上直接显示的那个数）。
     *
     * <p>★ 第九十八批的教训：这个数按**厘**（centivis）传过一次 129，法杖面板只掉 1.29
     * （{@code WandItem.formatVis} 把厘 ÷100 显示），用户当场就问「消耗的不是 129 点」。
     * 所以这里一律记整 Vis，送进 API 时才 ×100（{@link #VIS_PER_CHANNEL_CENTIVIS}）。</p>
     *
     * <p>第二百一十四批由 129 降到 <b>77</b>（用户口径「升级一次消耗 77 点法杖 6 种要素」），
     * 与玄枢耀光配方同一档数。</p>
     */
    public static final int VIS_PER_CHANNEL = 77;

    /** 同一价码的**厘**（centivis）写法——API 收的就是这个单位。 */
    public static final int VIS_PER_CHANNEL_CENTIVIS = VIS_PER_CHANNEL * 100;

    /** 单条附魔的等级上限。 */
    public static final int MAX_ENCHANT_LEVEL = 128;

    /**
     * 一次运转的价码：六种要素各 {@link #VIS_PER_CHANNEL} 整 Vis（= 各
     * {@link #VIS_PER_CHANNEL_CENTIVIS} 厘，API 收厘）。
     *
     * <p>顺序取 {@code VisChannel} 的六基元，与法杖魔力池一一对应。</p>
     */
    private static final Map<VisChannel, Integer> VIS_COST = visCost();

    private static Map<VisChannel, Integer> visCost() {
        LinkedHashMap<VisChannel, Integer> map = new LinkedHashMap<>();
        map.put(VisChannel.AER, VIS_PER_CHANNEL_CENTIVIS);
        map.put(VisChannel.TERRA, VIS_PER_CHANNEL_CENTIVIS);
        map.put(VisChannel.IGNIS, VIS_PER_CHANNEL_CENTIVIS);
        map.put(VisChannel.AQUA, VIS_PER_CHANNEL_CENTIVIS);
        map.put(VisChannel.ORDO, VIS_PER_CHANNEL_CENTIVIS);
        map.put(VisChannel.PERDITIO, VIS_PER_CHANNEL_CENTIVIS);
        return java.util.Collections.unmodifiableMap(map);
    }


    private final ItemStackHandler items = new ItemStackHandler(2) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    /** 头顶引导线的颜色：金（南斗五星各供玄枢锭时亮）。 */
    private static final int GUIDE_GOLD = 0xFFC22E;

    @Override
    public int guideColor() {
        return GUIDE_GOLD;
    }

    /**
     * 银线只在「南斗五星各供<strong>玄枢锭</strong>」成立时亮（第一百五十二批改口径）。
     *
     * <p>第一百五十一批这里判的是玄枢碎片，与复刻台撞了物；用户第一百五十二批改口径：
     * 「奥术台、注灵台用玄枢锭」。判定与门禁同源（{@link StarRite#nandouFiveIngot}），
     * 并按 {@value #GUIDE_GATE_TICKS} tick 缓存——{@code guideActive} 是渲染每帧都会问的，
     * 别每帧扫十二格星位。</p>
     *
     * <p>★ 第二百一十四批台内的玄枢锭槽没了，<b>台外的星位门禁不动</b>——那是南斗五星各自供锭，
     * 与台内材料无关。</p>
     */
    @Override
    public boolean guideActive(Constellation constellation) {
        if (this.level == null) {
            return false;
        }
        long now = this.level.getGameTime();
        if (this.guideGateTick == Long.MIN_VALUE || now - this.guideGateTick >= GUIDE_GATE_TICKS) {
            this.guideGateTick = now;
            this.guideGateCache = StarRite.nandouFiveIngot(this.level, this.worldPosition);
        }
        return this.guideGateCache;
    }

    /** 引导线判定的缓存（见 {@link #guideActive}）。 */
    private boolean guideGateCache;
    private long guideGateTick = Long.MIN_VALUE;
    private static final int GUIDE_GATE_TICKS = 10;

    public XuanshuArcaneTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.XUANSHU_ARCANE_TABLE.get(), pos, state);
    }

    /** 容器句柄（Menu 与掉落共用）。 */
    public IItemHandler getItems() {
        return this.items;
    }

    /** 界面槽位直接改这里。 */
    public ItemStackHandler rawItems() {
        return this.items;
    }

    @Override
    protected void saveAdditional(net.minecraft.nbt.CompoundTag tag,
                                  net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Items", this.items.serializeNBT(registries));
    }

    @Override
    protected void loadAdditional(net.minecraft.nbt.CompoundTag tag,
                                  net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Items")) {
            this.items.deserializeNBT(registries, tag.getCompound("Items"));
        }
    }

    /** 书槽里那本附魔书的附魔表；不是附魔书 / 是空书 → 空表。 */
    private static ItemEnchantments bookEnchantments(ItemStack book) {
        if (!book.is(Items.ENCHANTED_BOOK)) {
            return ItemEnchantments.EMPTY;
        }
        ItemEnchantments ench = book.get(DataComponents.STORED_ENCHANTMENTS);
        return ench == null ? ItemEnchantments.EMPTY : ench;
    }

    /**
     * 持法杖右键运转（第九十七批，第二百一十四批改价改料）：原有附魔各 +1，
     * 书槽有附魔书就把书上所有附魔并进来（书消耗一本），代价是法杖六元各
     * {@value #VIS_PER_CHANNEL} 点（整 Vis；{@code consumeExactVisFromWand} 收厘，内部 ×100），
     * 六路逐路足额。
     *
     * <p>判定顺序故意是「先算得出东西可加 → 再扣魔」：<b>没东西可加、六元不足都不动任何材料</b>，
     * 只弹一句提示。</p>
     */
    public void applyWithWand(net.minecraft.world.level.Level level, Player player, ItemStack wand) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        // ★ 第一百五十二批：前置——南斗五星齐明，且五星位上各供一枚玄枢锭
        if (!StarRite.nandouFiveIngot(level, this.worldPosition)) {
            say(player, "message.skydeitymysticpivot.star_gate_nandou");
            return;
        }
        ItemStack target = this.items.getStackInSlot(SLOT_ITEM);
        if (target.isEmpty()) {
            say(player, "message.skydeitymysticpivot.arcane_table.empty");
            return;
        }

        ItemEnchantments current = target.get(DataComponents.ENCHANTMENTS);
        if (current == null) {
            current = ItemEnchantments.EMPTY;
        }
        ItemStack book = this.items.getStackInSlot(SLOT_BOOK);
        ItemEnchantments fromBook = bookEnchantments(book);

        // 先把「原有附魔各 +1」算出来
        ItemEnchantments.Mutable merged = new ItemEnchantments.Mutable(current);
        boolean changed = false;
        for (Holder<Enchantment> holder : current.keySet()) {
            int lv = current.getLevel(holder);
            if (lv < MAX_ENCHANT_LEVEL) {
                merged.set(holder, Math.min(MAX_ENCHANT_LEVEL, lv + 1));
                changed = true;
            }
        }
        // 再把书上的附魔原样并入：同一条取较高等级；不管这东西能不能带、也不管互斥
        for (Holder<Enchantment> holder : fromBook.keySet()) {
            int bookLevel = Math.min(MAX_ENCHANT_LEVEL, fromBook.getLevel(holder));
            int have = merged.getLevel(holder);
            if (bookLevel > have) {
                merged.set(holder, bookLevel);
                changed = true;
            }
        }
        if (!changed) {
            // 没有附魔可升、书上也没带新东西：分两种提示，免得玩家不知道是缺书还是封顶
            say(player, fromBook.isEmpty()
                    ? "message.skydeitymysticpivot.arcane_table.no_enchant"
                    : "message.skydeitymysticpivot.arcane_table.maxed");
            return;
        }

        // 扣魔：六种要素各 VIS_PER_CHANNEL 整 Vis，逐路足额；哪一路不够就整笔不扣
        VisPaymentResult paid = ThaumcraftApiHelper.consumeExactVisFromWand(
                wand, player, VIS_COST, VisAction.EXECUTE);
        if (!paid.consumed()) {
            say(player, "message.skydeitymysticpivot.no_vis");
            return;
        }

        // 结算：写附魔 → 吃掉一本书（有的话）→ 反馈
        target.set(DataComponents.ENCHANTMENTS, merged.toImmutable());
        this.items.setStackInSlot(SLOT_ITEM, target);
        if (!fromBook.isEmpty()) {
            this.items.extractItem(SLOT_BOOK, 1, false);
        }
        this.setChanged();
        server.playSound(null, this.worldPosition, SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.BLOCKS, 0.8F, 1.2F);
        server.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.1, this.worldPosition.getZ() + 0.5,
                40, 0.5, 0.3, 0.5, 0.02D);
    }

    private static void say(Player player, String key) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(key), true);
        }
    }

    /** 方块消失（挖/炸/活塞）时把两格物品散出来（创造模式同样生效）。 */
    public void dropContents() {
        Containers.dropContents(this.level, this.worldPosition, drop());
    }

    private net.minecraft.world.Container drop() {
        SimpleContainer out = new SimpleContainer(2);
        out.setItem(0, this.items.getStackInSlot(SLOT_ITEM));
        out.setItem(1, this.items.getStackInSlot(SLOT_BOOK));
        return out;
    }

    // ------------------------------------------------------------------
    // MenuProvider：右键（空手 / 持非法杖）打开「玄枢奥术台」
    // ------------------------------------------------------------------

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.skydeitymysticpivot.xuanshu_arcane_table");
    }

    @Override
    @Nullable
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return new XuanshuArcaneTableMenu(id, inv, this);
    }
}
