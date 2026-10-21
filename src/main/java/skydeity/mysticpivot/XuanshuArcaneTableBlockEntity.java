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
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * 玄枢奥术台方块实体（第八十九批新增）：一格<b>受附物品</b>暂存 +
 * {@link #applyWithWand} 的「一级附魔 = 1 枚玄枢锭 + 法杖六元各 129 点」。
 *
 * <h2>运转（用户原话）</h2>
 * <p>第九十六批：「一个是物品要增加附魔的，一个是染灵，放玄枢锭，附魔一次消耗一个」；
 * 第九十七批加码：「升级附魔同时消耗玄枢锭和法杖 6 种要素各 129 点」。
 * 于是把要升附魔的物品放进左槽、<b>玄枢锭</b>放进右槽，<b>手持任意法杖右键方块</b>——
 * 一次运转同时扣<b>两样</b>：玄枢锭 ×{@value #DYE_COST}，以及法杖
 * <b>风 / 地 / 火 / 水 / 序 / 灭 六种要素各 {@value #VIS_PER_CHANNEL} 点（整 Vis）</b>
 * （送进 API 时换成 {@value #VIS_PER_CHANNEL_CENTIVIS} 厘；走
 * {@code ThaumcraftApiHelper.consumeExactVisFromWand}，六路**逐路足额**校验，
 * 哪一路不够就整笔不扣），成功后槽内物品的<b>每一项附魔等级 +1</b>（单条最高
 * {@value #MAX_ENCHANT_LEVEL}）。</p>
 *
 * <ul>
 *   <li>台上无物品：提示「台上并无物品」；</li>
 *   <li>物品没有附魔：提示「此物并无附魔」；</li>
 *   <li>全部附魔已封顶：提示「诸附魔已至绝顶」（<b>封顶不烧锭、不抽魔</b>）；</li>
 *   <li>染灵槽没有玄枢锭：提示缺锭（**先查锭、再查魔**，免得白扣魔力）；</li>
 *   <li>法杖六元有缺口：提示六元不足，锭与魔力都不动。</li>
 * </ul>
 *
 * <p>法杖判定走 {@code WandApi.isBuiltinWand}（本体各级法杖都算，
 * 与 {@code WandAutoRefill}、节点修改器同口径）。</p>
 */
public final class XuanshuArcaneTableBlockEntity extends BlockEntity implements MenuProvider, StarReader {

    /** 物品槽（要加附魔的物品）。 */
    public static final int SLOT_ITEM = 0;

    /** 染灵槽（放玄枢锭，运转一次消耗一枚）。 */
    public static final int SLOT_DYE = 1;

    /** 运转一次消耗的染灵：玄枢锭 ×{@value #DYE_COST}。 */
    public static final int DYE_COST = 1;

    /**
     * 运转一次、每一种要素的消耗，单位是**整 Vis**（也就是法杖面板上直接显示的那个数）。
     *
     * <p>★ 第九十八批修正：上一批按**厘**（centivis）传了 129，于是法杖面板只掉
     * <b>1.29</b>——因为 {@code WandItem.formatVis} 把厘数 ÷100 显示（`129 % 100 != 0`
     * 时才显示两位小数），玩家看到的是「1.29」而不是「129」，用户当场就发现
     * 「消耗的不是 129 点」。本版按整 Vis 记（{@value #VIS_PER_CHANNEL} Vis），
     * 送进 API 时再 ×100 换成厘（{@link #VIS_PER_CHANNEL_CENTIVIS}）。
     * 玄枢四杖（朱雀 918 / 四象 1949 / 鎏金 2035 / 寰宇 99999）都付得起。</p>
     */
    public static final int VIS_PER_CHANNEL = 129;

    /** 同一价码的**厘**（centivis）写法——API 收的就是这个单位。 */
    public static final int VIS_PER_CHANNEL_CENTIVIS = VIS_PER_CHANNEL * 100;

    /** 染灵物料：玄枢锭。 */
    public static final net.minecraft.world.item.Item DYE_ITEM = net.minecraft.core.registries.BuiltInRegistries.ITEM
            .get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("skydeitymysticpivot", "xuanshu_ingot"));

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

    /**
     * 持法杖右键运转（第九十七批）：物品槽附魔全 +1（封顶 {@value #MAX_ENCHANT_LEVEL}），
     * 代价<b>两样一起扣</b>——染灵槽 1 枚玄枢锭，加上法杖六种要素各
     * {@value #VIS_PER_CHANNEL} 点（整 Vis；{@code consumeExactVisFromWand} 收厘，内部 ×100），
     * 六路逐路足额。
     *
     * <p>判定顺序故意是「先能升 → 再查锭 → 最后扣魔」：<b>封顶不烧锭、缺锭不抽魔</b>，
     * 任何一条不满足都整笔不动、只弹一句提示。</p>
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
        ItemEnchantments ench = target.get(DataComponents.ENCHANTMENTS);
        if (ench == null || ench.isEmpty()) {
            say(player, "message.skydeitymysticpivot.arcane_table.no_enchant");
            return;
        }

        // 先全查一遍是否都已封顶（封顶就既不烧锭也不抽魔）
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ench);
        boolean changed = false;
        for (Holder<Enchantment> holder : ench.keySet()) {
            int lv = ench.getLevel(holder);
            if (lv < MAX_ENCHANT_LEVEL) {
                mutable.set(holder, Math.min(MAX_ENCHANT_LEVEL, lv + 1));
                changed = true;
            }
        }
        if (!changed) {
            say(player, "message.skydeitymysticpivot.arcane_table.maxed");
            return;
        }

        // 再查染灵：玄枢锭不够就到此为止（先查锭、后扣魔，免得白扣魔力）
        ItemStack dye = this.items.getStackInSlot(SLOT_DYE);
        if (dye.getCount() < DYE_COST || !dye.is(DYE_ITEM)) {
            say(player, "message.skydeitymysticpivot.arcane_table.no_dye");
            return;
        }

        // 最后扣魔：六种要素各 VIS_PER_CHANNEL 整 Vis，逐路足额；哪一路不够就整笔不扣
        VisPaymentResult paid = ThaumcraftApiHelper.consumeExactVisFromWand(
                wand, player, VIS_COST, VisAction.EXECUTE);
        if (!paid.consumed()) {
            say(player, "message.skydeitymysticpivot.no_vis");
            return;
        }

        // 结算：写附魔 → 扣锭 → 反馈
        target.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        this.items.setStackInSlot(SLOT_ITEM, target);
        this.items.extractItem(SLOT_DYE, DYE_COST, false);
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
        out.setItem(1, this.items.getStackInSlot(SLOT_DYE));
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
