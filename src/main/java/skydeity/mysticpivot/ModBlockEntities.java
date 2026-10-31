package skydeity.mysticpivot;

import dev.tc4port.thaumcraft.api.ThaumcraftApiHelper;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.aspect.VisChannel;
import dev.tc4port.thaumcraft.api.aspect.VisPaymentResult;
import dev.tc4port.thaumcraft.api.node.AuraNodeState;
import dev.tc4port.thaumcraft.api.node.NodeModifierDefinition;
import dev.tc4port.thaumcraft.api.node.NodeModifierId;
import dev.tc4port.thaumcraft.api.node.AuraNodeView;
import dev.tc4port.thaumcraft.api.node.NodeApi;
import dev.tc4port.thaumcraft.api.node.NodeVis;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import skydeity.mysticpivot.compat.NodeStateBridge;
import skydeity.mysticpivot.mixin.InfusionMatrixBlockEntityAccessor;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 方块实体注册。
 *
 * <h2>玄枢水晶簇的安全场</h2>
 * <p>玄枢水晶簇（旧名玄枢魔晶，第六十五批从零重做）的核心效果：只要有一台放在注魔矩阵的
 * 扫描域内（水平 ±12 格、竖直 -10~+5 格，与本体注魔矩阵自身的稳定器扫描范围一致），
 * 矩阵的不稳定度就每 5 tick 被清零一次——本体的不稳定事件（弹射物品、电击、伤害等）
 * 只在 {@code instability > 0} 时掷骰，归零即绝不触发，注魔稳如泰山。</p>
 *
 * <p>实现走 {@link InfusionMatrixBlockEntityAccessor}（本项目唯一进入本体内部的窄口，
 * 理由见该类注释）；读取和写入都只在值非零时发生，稳态下每 5 tick 只做一轮只读扫描。</p>
 *
 */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BE_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MysticPivot.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<XuanshuCrystalBlockEntity>> XUANSHU_CRYSTAL =
            BE_TYPES.register("xuanshu_crystal",
                    () -> BlockEntityType.Builder.of(XuanshuCrystalBlockEntity::new,
                            ModBlocks.XUANSHU_CRYSTAL_CLUSTER.get()).build(null));

    /** 玄枢充能器：立在节点正下方，通红石把节点充能成充能灵气节点（单方块替代本体三段装配）。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<XuanshuChargerBlockEntity>> XUANSHU_CHARGER =
            BE_TYPES.register("xuanshu_charger",
                    () -> BlockEntityType.Builder.of(XuanshuChargerBlockEntity::new,
                            ModBlocks.XUANSHU_CHARGER.get()).build(null));

    /** 玄枢注灵香炉台：台座 + 台上香炉，华槽 / 锭槽两格；持法杖右键正上方立着的缸中节点即运转。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<XuanshuNodeModifierBlockEntity>> XUANSHU_NODE_MODIFIER =
            BE_TYPES.register("xuanshu_node_modifier",
                    () -> BlockEntityType.Builder.of(XuanshuNodeModifierBlockEntity::new,
                            ModBlocks.XUANSHU_NODE_MODIFIER.get()).build(null));

    /** 玄枢奥术台：受附物品一格，持法杖右键吸 520 魔力、附魔全 +1。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<XuanshuArcaneTableBlockEntity>> XUANSHU_ARCANE_TABLE =
            BE_TYPES.register("xuanshu_arcane_table",
                    () -> BlockEntityType.Builder.of(XuanshuArcaneTableBlockEntity::new,
                            ModBlocks.XUANSHU_ARCANE_TABLE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<XuanshuInfernalFurnaceBlockEntity>> XUANSHU_INFERNAL_FURNACE =
            BE_TYPES.register("xuanshu_infernal_furnace",
                    () -> BlockEntityType.Builder.of(XuanshuInfernalFurnaceBlockEntity::new,
                            ModBlocks.XUANSHU_INFERNAL_FURNACE.get()).build(null));

    /** 玄枢复刻台：左格放模板、右格原样复刻（连同全部数据组件）。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<XuanshuReplicatorBlockEntity>> XUANSHU_REPLICATOR =
            BE_TYPES.register("xuanshu_replicator",
                    () -> BlockEntityType.Builder.of(XuanshuReplicatorBlockEntity::new,
                            ModBlocks.XUANSHU_REPLICATOR.get()).build(null));

    /** 玄枢台座：存储台面物品并承担卦象转化的周期推进。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<XuanshuPedestalBlockEntity>> XUANSHU_PEDESTAL =
            BE_TYPES.register("xuanshu_pedestal",
                    () -> BlockEntityType.Builder.of(XuanshuPedestalBlockEntity::new,
                            ModBlocks.XUANSHU_PEDESTAL.get()).build(null));

    /**
     * 南北斗枢玄星盘：存中间两格上供着的物品，同时挂 {@code StarDialRenderer}（金线 / 六星 / 物品）。
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StarDialBlockEntity>> STAR_DIAL =
            BE_TYPES.register("star_dial",
                    () -> BlockEntityType.Builder.of(StarDialBlockEntity::new,
                            ModBlocks.STAR_DIAL.get()).build(null));

    /** 玄枢坩埚：抽取周围原质管里的要素、按配方炼成产物。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<XuanshuCrucibleBlockEntity>> XUANSHU_CRUCIBLE =
            BE_TYPES.register("xuanshu_crucible",
                    () -> BlockEntityType.Builder.of(XuanshuCrucibleBlockEntity::new,
                            ModBlocks.XUANSHU_CRUCIBLE.get()).build(null));

    /** 玄枢炼金炉：把物品烧成源质，再推给周围 5×5×3 里的原质管／罐。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<XuanshuAlchemyFurnaceBlockEntity>> XUANSHU_ALCHEMY_FURNACE =
            BE_TYPES.register("xuanshu_alchemy_furnace",
                    () -> BlockEntityType.Builder.of(XuanshuAlchemyFurnaceBlockEntity::new,
                            ModBlocks.XUANSHU_ALCHEMY_FURNACE.get()).build(null));

    /** 玄枢虚空水晶塔：一格机器 + 背后一座塔，每 5 秒从 {@code #c:ores} 随机产出一组矿。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<XuanshuVoidMiningBlockEntity>> XUANSHU_VOID_MINING =
            BE_TYPES.register("xuanshu_void_mining",
                    () -> BlockEntityType.Builder.of(XuanshuVoidMiningBlockEntity::new,
                            ModBlocks.XUANSHU_VOID_MINING.get()).build(null));

    /** 玄枢传送门：不管传送（还没做），只管背后那座 1099 格的广场齐不齐 + 开界面。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<XuanshuPortalBlockEntity>> XUANSHU_PORTAL =
            BE_TYPES.register("xuanshu_portal",
                    () -> BlockEntityType.Builder.of(XuanshuPortalBlockEntity::new,
                            ModBlocks.XUANSHU_PORTAL.get()).build(null));

    /** 玄枢源质罐：一只罐子按要素分别计数（每种 2048），可被管子灌、可被注魔与基座抽。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<XuanshuEssentiaJarBlockEntity>> XUANSHU_ESSENTIA_JAR =
            BE_TYPES.register("xuanshu_essentia_jar",
                    () -> BlockEntityType.Builder.of(XuanshuEssentiaJarBlockEntity::new,
                            ModBlocks.XUANSHU_ESSENTIA_JAR.get()).build(null));

    /** 玄枢魔晶方块实体：不存数据，只承担安全场的周期扫描。 */
    public static class XuanshuCrystalBlockEntity extends BlockEntity {

        /** 扫描周期（tick）。 */
        private static final int SCAN_INTERVAL = 5;

        public XuanshuCrystalBlockEntity(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
            super(ModBlockEntities.XUANSHU_CRYSTAL.get(), pos, state);
        }

        @SuppressWarnings("unused")
        public static void serverTick(Level level, BlockPos pos, BlockEntity blockEntity) {
            if (level.getGameTime() % SCAN_INTERVAL != 0) {
                return;
            }
            Iterable<BlockPos> area = BlockPos.betweenClosed(
                    pos.offset(-12, -10, -12), pos.offset(12, 5, 12));
            for (BlockPos p : area) {
                BlockEntity be = level.getBlockEntity(p);
                if (be instanceof InfusionMatrixBlockEntityAccessor accessor
                        && accessor.getInstability() > 0) {
                    accessor.setInstability(0);
                }
            }
        }
    }


    /**
     * 玄枢充能器方块实体：把正上方的灵气节点<b>充能成充能灵气节点</b>的状态机。
     *
     * <h2>与本体三段装配的对照</h2>
     * 本体（{@code NodeTransducerBlockEntity}）要「稳定器 + 节点 + 换能器」三块、红石通着，
     * 每 tick 从节点抽 1 点随机要素，涨满 1000 tick（50 秒）才把节点换成充能节点；
     * 断红石后计数跌回 50 以下就退回普通节点。本类把这三块压成充能器一块，
     * 并全面强化（用户要求「比原版的效果更强」）：
     * <ul>
     *   <li><b>快</b>：{@value #CHARGE_TICKS} tick（10 秒）充能，本体的 1/5 时间；</li>
     *   <li><b>省</b>：每 {@value #DRAIN_INTERVAL} tick 才抽 1 点（全程至多 40 点），本体每 tick 抽；</li>
     *   <li><b>强</b>：充能完成后节点每门要素上限<b>随时间继续上涨</b>（每秒 +{@value #GROW_STEP}，
     *       {@link #growEnergizedNode}），每 tick 产出 = {@code floor(sqrt(上限 × 要素系数))}
     *       （本体 {@code calculateEnergizedBase}），一路涨到 {@value #POOL_TARGET}/t 封顶。</li>
     * </ul>
     *
     * <h2>流转（只有 {@link #count} 一个状态量）</h2>
     * <pre>
     *   红石通 + 上方是普通节点 ──▶ 计数 0→200，期间每 5 tick 抽 1 点要素
     *                │ 满 200：把节点换成充能灵气节点，计数钉在 200
     *                ▼
     *   红石通 + 上方是充能节点 ──▶ 计数保持 200，每秒给每门要素上限 +{@value #GROW_STEP}
     *                │ 断红石：计数逐 tick 回落
     *                ▼
     *   计数归零 ──▶ 换回普通灵气节点，要素上限还原成充能前的原值
     * </pre>
     * ★ 走哪条分支只看<b>正上方现在是什么方块</b>，不靠任何「当前处于哪个状态」的标记 —
     * 第六十九批把原先那套只写不读的 {@code status}/{@code STATUS_*} 字段整个删掉了。
     *
     * <h2>两条窄口（本项目进入本体内部的既有约定）</h2>
     * <ul>
     *   <li>换方块之后新方块实体的状态是空的，且 {@code NodeApi#replaceLoadedState} 明文
     *       <b>不接受充能节点</b>（只认普通节点与节点罐）⇒ 写回走
     *       {@link skydeity.mysticpivot.compat.NodeStateBridge}（直接调本体 public 的
     *       {@code setNodeState}；接口混入走不通，见该类注释与 §12.69.6）；</li>
     *   <li>充能节点每次结构复核都会问「正下方是不是本体稳定器、上方是不是本体换能器」，
     *       答不上就炸 ⇒ {@code AuraNodeEnergizedMachineMixin} 把「正下方是玄枢充能器」
     *       也判为合法。机器被挖走时该判据自然失效，节点照本体规矩爆炸。</li>
     * </ul>
     *
     * <p>掉电还原时要把节点换回普通灵气节点——这一步同样没有 API（{@code replaceLoadedState}
     * 只写状态不换方块），换方块走注册表按名取块（{@code thaumcraft:aura_node} /
     * {@code thaumcraft:energized_aura_node}，附属不 import 本体实现包），与本类
     * {@link #isThaumcraftBlock} 比注册名的路子一致。</p>
     */
    public static class XuanshuChargerBlockEntity extends BlockEntity {

        /** 充能所需 tick：200 = 10 秒（本体换能器 1000 tick = 50 秒）。 */
        public static final int CHARGE_TICKS = 200;

        /** 抽要素间隔：每 5 tick 从节点随机抽 1 点（本体 1 tick 抽 1 点）。 */
        public static final int DRAIN_INTERVAL = 5;

        /**
         * 充能完成后要素上限的增长节奏：每 {@value #GROW_INTERVAL} tick（1 秒）每门 +{@value #GROW_STEP}，
         * 直到每 tick 产出达到 {@value #POOL_TARGET}/t 封顶（用户第六十六批：「要素的数量随着时间增多，
         * 最多 64/t」）。产出 = {@code floor(sqrt(上限 × 系数))}（本体 calculateEnergizedBase），
         * 系数取节点自己的修饰符；NodeVis 只限制要素**种类数** ≤256，单门数值无硬顶，
         * 故 64/t 可达（目标上限 ≈ 4096 / 系数）。
         *
         * <p>★ 第六十九批把 {@code GROW_STEP} 由 256 提到 {@value #GROW_STEP}：用户反馈
         * 「增加到 64/t 好像充能没这么快」。算式是死的——目标上限 = 64²/系数 = 4096（系数 1.0），
         * 每门从节点原值（通常几十到几百）爬到 4096，按 +256/秒要**十六秒上下**；
         * 加上前面十秒的充能，从通红石到满 64/t 接近半分钟。改成 +{@value #GROW_STEP}/秒 后
         * 约 **四秒** 到顶（系数 0.5 的「消逝」节点目标上限 8192，也只要八秒）。</p>
         */
        public static final int GROW_INTERVAL = 20;
        public static final int GROW_STEP = 1024;
        public static final int POOL_TARGET = 64;
        public static final int BASE_HARD_CAP = 8192;

        // ---- 状态 ----
        // 只有一个整数：充能进度。★ 第六十九批删掉了一套 status/STATUS_* 字段 ——
        // 那套「闲置/充能中/已充能」标记只会被写、从来没被读过（真正决定走哪条分支的
        // 是「上方现在到底是什么方块」），连带 KEY_STATUS 的存盘与读档校验一起删了。
        private static final String KEY_COUNT = "Count";
        private static final String KEY_ORIGINAL_BASE = "OriginalBase";

        private int count = 0;

        /**
         * 充能前节点每门要素的上限（掉电还原用）。
         *
         * <p>充能后上限会一路往 64/t 的目标涨，不记住原值就还原不回去。随方块实体存盘；
         * 充能器被挖了重放导致丢失时，掉电只能保持现状（记不住原值），属可接受的边界情形。</p>
         */
        @Nullable
        private Map<AspectId, Integer> originalBase;

        public XuanshuChargerBlockEntity(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
            super(ModBlockEntities.XUANSHU_CHARGER.get(), pos, state);
        }

        @SuppressWarnings("unused")
        public static void serverTick(Level level, BlockPos pos, BlockEntity blockEntity) {
            if (!(level instanceof ServerLevel server) || !(blockEntity instanceof XuanshuChargerBlockEntity self)) {
                return;
            }
            // 与本体节点稳定器同判据：周边区块没加载 = 本轮跳过（别把远处节点读出来）。
            if (!server.hasChunksAt(pos.offset(-2, 0, -2), pos.offset(2, 0, 2))) {
                return;
            }
            self.tick(server, pos);
        }

        /** 每 tick 推进状态机（实例方法：要动 {@link #count} / {@link #originalBase}）。 */
        private void tick(ServerLevel server, BlockPos pos) {
            boolean powered = server.hasNeighborSignal(pos);
            BlockPos nodePos = pos.above();
            BlockState nodeBlock = server.getBlockState(nodePos);
            boolean ordinary = isThaumcraftBlock(nodeBlock, "aura_node");
            boolean energized = isThaumcraftBlock(nodeBlock, "energized_aura_node");

            if (ordinary) {
                // ---- 普通灵气节点：红石通着就往满里充，断了就回落 ----
                if (powered) {
                    if (this.count < CHARGE_TICKS) {
                        this.count++;
                        if (this.count % DRAIN_INTERVAL == 0) {
                            drainOneAspect(server, nodePos);
                        }
                    }
                    if (this.count >= CHARGE_TICKS) {
                        energize(server, nodePos);
                    }
                } else if (this.count > 0) {
                    this.count--;
                }
            } else if (energized) {
                // ---- 充能灵气节点：持续红石保持，断红石计数归零即退回 ----
                if (powered) {
                    this.count = CHARGE_TICKS;
                    if (server.getGameTime() % GROW_INTERVAL == 0) {
                        growEnergizedNode(server, nodePos);   // 要素上限随时间上涨，至 64/t 封顶
                    }
                } else if (this.count > 0) {
                    this.count--;
                    if (this.count == 0) {
                        deEnergize(server, nodePos);
                    }
                }
            } else {
                // ---- 上方没有节点：收摊 ----
                if (this.count > 0) {
                    this.count--;
                }
                this.originalBase = null;
            }

            syncActive(server, pos, powered
                    && (ordinary && this.count > 0 || energized));
        }

        /** 从节点随机抽 1 点要素（CAS 写回，绝不覆盖节点自己正在跑的充能 / 抽取）。 */
        private static void drainOneAspect(ServerLevel server, BlockPos nodePos) {
            if (!(server.getBlockEntity(nodePos) instanceof AuraNodeView view)) {
                return;
            }
            AuraNodeState current = view.nodeState();
            List<AspectId> present = current.currentVis().amounts().entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .map(Map.Entry::getKey)
                    .toList();
            if (present.isEmpty()) {
                return;                     // 节点已经被抽干：不阻挠充能，只是没得抽
            }
            AspectId aspect = present.get(server.random.nextInt(present.size()));
            LinkedHashMap<AspectId, Integer> next = new LinkedHashMap<>(current.currentVis().amounts());
            next.merge(aspect, -1, Integer::sum);
            NodeApi.replaceLoadedState(server, nodePos, current,
                    current.withCurrentVis(new NodeVis(next)), VisAction.EXECUTE);
        }

        /**
         * 充能：把正上方的普通灵气节点换成<b>充能灵气节点</b>（要素上限原样不动，翻倍那是旧版；
         * 第六十六批起改为充能完成后<b>随时间慢慢涨</b>，见 {@link #growEnergizedNode}）。
         *
         * <p>步骤照本体 {@code energizeNode}：记住原上限 → 换方块 → 新实体是空的，
         * 经 {@link skydeity.mysticpivot.compat.NodeStateBridge} 写回完整状态
         * （instanceId / type / modifier / baseVis 原样保留，currentVis 清零——
         * 充能节点不存要素，它的产出是每 tick 重新算的池子）。</p>
         */
        private void energize(ServerLevel server, BlockPos nodePos) {
            if (!(server.getBlockEntity(nodePos) instanceof AuraNodeView view)) {
                return;
            }
            AuraNodeState old = view.nodeState();
            Block energized = BuiltInRegistries.BLOCK
                    .getOptional(ResourceLocation.fromNamespaceAndPath("thaumcraft", "energized_aura_node"))
                    .orElse(null);
            if (energized == null) {
                return;                     // 本体没注册这个方块（版本不符）：不贸然动手
            }
            this.originalBase = new LinkedHashMap<>(old.baseVis().amounts());
            server.setBlock(nodePos, energized.defaultBlockState(), Block.UPDATE_ALL);
            NodeStateBridge.writeNodeState(server, nodePos,
                    old.withVis(old.baseVis(), new NodeVis(zeroed(old.baseVis().amounts()))));
            this.count = CHARGE_TICKS;
            setChanged();
            // 成型音：一记深钟（第六十七批起不再冒粒子，只留声音）
            server.playSound(null, nodePos, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.BLOCKS, 0.9F, 0.7F);
        }

        /**
         * 充能完成后要素上限<b>随时间上涨</b>：每门每 {@value #GROW_INTERVAL} tick（1 秒）
         * +{@value #GROW_STEP}，直到每 tick 产出达到
         * {@value #POOL_TARGET}/t 封顶（用户第六十六批：「要素的数量随着时间增多，最多 64/t」）。
         *
         * <p>两条实现要点：</p>
         * <ul>
         *   <li>{@code replaceLoadedState} <b>不接受充能节点</b>（只认普通节点与节点罐），
         *       所以这里走 {@link NodeStateBridge} 直接 {@code setNodeState}；
         *       本体下一 tick 发现 {@code EnergizedSignature} 变了会重算产出池，
         *       写进去的上限当场生效并同步给客户端；</li>
         *   <li>产出 = {@code floor(sqrt(上限 × 系数))}，系数取节点自己的修饰符
         *       （{@code NodeApi.registry()}，公开 API）；目标上限 = {@code ceil(64² / 系数)}。
         *       {@code NodeVis} 只限制要素**种类数** ≤256，单门数值无硬顶，故 64/t 可达。</li>
         * </ul>
         */
        private void growEnergizedNode(ServerLevel server, BlockPos nodePos) {
            if (!(server.getBlockEntity(nodePos) instanceof AuraNodeView view)) {
                return;
            }
            AuraNodeState state = view.nodeState();
            float mult = energizedMultiplier(state.modifier());
            int targetBase = targetBaseFor(mult);
            LinkedHashMap<AspectId, Integer> grown = new LinkedHashMap<>();
            boolean changed = false;
            for (Map.Entry<AspectId, Integer> entry : state.baseVis().amounts().entrySet()) {
                int base = entry.getValue();
                if (poolOf(base, mult) < POOL_TARGET && base < targetBase) {
                    int next = Math.min(targetBase, base + GROW_STEP);
                    grown.put(entry.getKey(), next);
                    if (next != base) {
                        changed = true;
                    }
                } else {
                    grown.put(entry.getKey(), base);
                }
            }
            if (!changed) {
                return;                     // 每门都到 64/t 了，无需再写
            }
            NodeStateBridge.writeNodeState(server, nodePos,
                    state.withVis(new NodeVis(grown), state.currentVis()));
        }

        /** 节点修饰符的充能产出系数（公开 API 取；取不到按 1.0 算）。 */
        private static float energizedMultiplier(NodeModifierId modifier) {
            return NodeApi.registry().get(modifier)
                    .map(NodeModifierDefinition::energizedOutputMultiplier)
                    .orElse(1.0F);
        }

        /** 本节点每 tick 产出（本体 calculateEnergizedBase 同式）。 */
        private static int poolOf(int base, float mult) {
            return (int) Math.floor(Math.sqrt((double) base * mult));
        }

        /** 要让每 tick 产出达到 {@value #POOL_TARGET}，每门要素上限至少要给到多少。 */
        private static int targetBaseFor(float mult) {
            return Math.min(BASE_HARD_CAP, (int) Math.ceil((double) POOL_TARGET * POOL_TARGET / mult));
        }

        /** 掉电：把充能灵气节点换回普通灵气节点，要素上限还原成充能前的原值。 */
        private void deEnergize(ServerLevel server, BlockPos nodePos) {
            Block ordinary = BuiltInRegistries.BLOCK
                    .getOptional(ResourceLocation.fromNamespaceAndPath("thaumcraft", "aura_node"))
                    .orElse(null);
            AuraNodeState old = server.getBlockEntity(nodePos) instanceof AuraNodeView view
                    ? view.nodeState()
                    : AuraNodeState.EMPTY;
            if (ordinary == null || old == AuraNodeState.EMPTY) {
                this.count = 0;
                return;
            }
            Map<AspectId, Integer> base = this.originalBase != null ? this.originalBase : old.baseVis().amounts();
            LinkedHashMap<AspectId, Integer> restored = new LinkedHashMap<>(base);
            server.setBlock(nodePos, ordinary.defaultBlockState(), Block.UPDATE_ALL);
            NodeStateBridge.writeNodeState(server, nodePos,
                    old.withVis(new NodeVis(restored), new NodeVis(zeroed(restored))));
            this.count = 0;
            this.originalBase = null;
            setChanged();
            server.playSound(null, nodePos, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.BLOCKS, 0.5F, 1.4F);
        }

        /** ACTIVE 只在变化时落笔：无脑每 tick 写会把邻居更新和光照重算放大成卡顿。 */
        private static void syncActive(ServerLevel server, BlockPos pos, boolean active) {
            BlockState state = server.getBlockState(pos);
            if (state.getBlock() == ModBlocks.XUANSHU_CHARGER.get()
                    && state.hasProperty(ModBlocks.XuanshuChargerBlock.ACTIVE)
                    && state.getValue(ModBlocks.XuanshuChargerBlock.ACTIVE) != active) {
                server.setBlock(pos, state.setValue(ModBlocks.XuanshuChargerBlock.ACTIVE, active),
                        Block.UPDATE_ALL);
            }
        }

        @Override
        protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.saveAdditional(tag, registries);
            tag.putInt(KEY_COUNT, this.count);
            if (this.originalBase != null && !this.originalBase.isEmpty()) {
                CompoundTag base = new CompoundTag();
                for (Map.Entry<AspectId, Integer> entry : this.originalBase.entrySet()) {
                    base.putInt(entry.getKey().serialized(), entry.getValue());
                }
                tag.put(KEY_ORIGINAL_BASE, base);
            }
        }

        @Override
        protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
            super.loadAdditional(tag, registries);
            this.count = Math.min(tag.getInt(KEY_COUNT), CHARGE_TICKS);
            this.originalBase = null;
            if (tag.contains(KEY_ORIGINAL_BASE)) {
                CompoundTag base = tag.getCompound(KEY_ORIGINAL_BASE);
                LinkedHashMap<AspectId, Integer> map = new LinkedHashMap<>();
                for (String key : base.getAllKeys()) {
                    map.put(AspectId.parse(key), base.getInt(key));
                }
                this.originalBase = map;
            }
            // 状态不需要读档修复：tick 每帧都按「上方实际是什么方块」自行推导走哪条分支，
            // 存档里那个整数只是充能进度。第六十九批删掉 status 之后这里也就空了。
        }
    }

    /** 正上方是本体 {@code thaumcraft:aura_node} 或 {@code energized_aura_node} 吗（按注册名判，不 import 实现包）。 */
    private static boolean isThaumcraftBlock(BlockState state, String path) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return "thaumcraft".equals(id.getNamespace()) && path.equals(id.getPath());
    }

    /** 同键清零（充能节点不存要素，照本体规矩）。 */
    private static Map<AspectId, Integer> zeroed(Map<AspectId, Integer> amounts) {
        LinkedHashMap<AspectId, Integer> result = new LinkedHashMap<>();
        amounts.keySet().forEach(aspect -> result.put(aspect, 0));
        return result;
    }

    /**
     * 玄枢注灵香炉台方块实体（第一百六十三批由「玄枢注灵台」改名；第八十八批新增、
     * 第八十九批改<b>方块</b>版）：<b>香炉</b>台 + 两格暂存（华槽 / 锭槽）——运转逻辑全在 {@link #applyWithWand}。
     *
     * <p>「正上方立着缸中节点」是运转的前提：玩家把 {@code thaumcraft:node_jar}
     * 摆在方块上面，GUI（{@link XuanshuNodeModifierMenu}）两槽填好，再<b>持法杖右键
     * 方块本体</b>（不入背包、不弹界面，直接干活）。罐里没有节点 / 上方没罐 /
     * 华槽没天域之华 / 锭槽没玄枢锭时只在服务端弹一句提示，不动材料。</p>
     *
     * <h2>第九十七批：两槽改为「天域之华 + 玄枢锭」</h2>
     * <p>用户原话：「里面两个槽位改为天域之华 和 玄枢锭，修改上方缸中节点的要素」。
     * 华槽是<b>要素来源</b>、第二槽由「六件转化物」换成<b>玄枢锭</b>作运转费，每次一枚；
     * <b>改节点类型的老路取消</b>（那六件物品不再被识别）。</p>
     *
     * <h2>第九十八批：整台改<b>纯方块</b>，效果改成「不读天华内容」</h2>
     * <p>用户原话：「灵气节点转化改为方块吧 不要模型了 删掉。然后里面是放任意天华和玄枢锭，
     * 上方的节点随机加一要素」。于是：</p>
     * <ul>
     *   <li><b>模型整套退役</b>：{@code models/node_modifier.obj} / {@code .mtl} 删除，
     *       方块模型改成本 mod 三台机器同款的 {@code cube_bottom_top} 立方体
     *       （贴图 {@code xuanshu_node_modifier[_top|_bottom].png}）；</li>
     *   <li><b>华槽只认「是不是天华」，不读内容</b>——任意一瓶天域之华都行。
     *       天域之华<b>不是安瓿</b>（它是封好的成品，没有「空瓶」这种中间状态），
     *       所以既不读瓶里封了哪几门、也不判剩余量，运转一次整件吃掉一件。</li>
     * </ul>
     *
     * <h2>第九十九批 → 第一百六十三批：涨哪一门 = 「先补基础，再三段抽」</h2>
     * <p>第九十九批用户原话：「效果改为添加的先是 6 种基础，然后再添加其他随机的一种 一直抽奖就行」；
     * 第一百六十三批改成三段式（用户原话见 {@code XuanshuNodeModifierMenu#nextAspect} 的注释）：
     * ① 六门基础缺哪门补哪门；② 六门齐了而每门不足 {@value XuanshuNodeModifierMenu#BASIC_CAP}：
     * 八成加在六门基础上、两成添一门新要素；③ 六门各满 {@value XuanshuNodeModifierMenu#BASIC_CAP}：
     * 两成添新要素、八成加在节点现有要素上。
     * 每次抽中一门 +{@value #ASPECT_AMOUNT}。</p>
     *
     * <h2>两槽的存放</h2>
     * <p>两槽是 BE 的 {@code ItemStackHandler}：关界面只是断开客户端连接，内容原地留着、
     * 下次打开还在；法杖右键运转那一刻才消耗；挖 / 炸 / 活塞推方块时由
     * {@link #dropContents()} 散出来。</p>
     */
    public static class XuanshuNodeModifierBlockEntity extends BlockEntity
            implements net.minecraft.world.MenuProvider, StarReader {

        /** 头顶引导线的颜色：金（南斗五星各供玄枢锭时亮）。 */
        private static final int GUIDE_GOLD = 0xFFC22E;

        @Override
        public int guideColor() {
            return GUIDE_GOLD;
        }

        /**
         * 银线只在「南斗五星各供<strong>玄枢锭</strong>」成立时亮（第一百五十二批，与奥术台同款）。
         *
         * <p>见 {@code XuanshuArcaneTableBlockEntity#guideActive} 的注释：判定要与门禁
         * {@link StarRite#nandouFiveIngot} 一致，并按十 tick 缓存（渲染每帧都会问）。</p>
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

        /** 华槽（左）：天域之华，决定注入哪几门要素。 */
        public static final int SLOT_BLOOM = 0;

        /** 锭槽（右）：玄枢锭，运转费每次一枚。 */
        public static final int SLOT_INGOT = 1;

        /** 一次运转消耗的玄枢锭枚数。 */
        public static final int INGOT_COST = 1;

        /** 运转费物料：玄枢锭。 */
        public static final net.minecraft.world.item.Item INGOT_ITEM =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                                "skydeitymysticpivot", "xuanshu_ingot"));

        /**
         * 持法杖激活同时要付的要素：**六基元各 {@value #VIS_PER_CHANNEL} 整 Vis**。
         *
         * <p>第一百批用户：「这个法杖激活也需要 要素 各129」——与玄枢奥术台同一档价码、
         * 同一套写法（{@code consumeExactVisFromWand} 收的是**厘**，所以送进去的是
         * {@link #VIS_PER_CHANNEL_CENTIVIS}）。各档法杖容量（四圣兽 918、四象 1949、
         * 玄枢 2060、寰宇 99 999）都付得起这笔六元各 129 的价。</p>
         */
        public static final int VIS_PER_CHANNEL = 129;

        /** 同一价码的**厘**写法（API 收厘）。 */
        public static final int VIS_PER_CHANNEL_CENTIVIS = VIS_PER_CHANNEL * 100;

        /** 六基元各 {@link #VIS_PER_CHANNEL_CENTIVIS} 厘的价码表。 */
        private static final java.util.Map<VisChannel, Integer> VIS_COST = visCost();

        private static java.util.Map<VisChannel, Integer> visCost() {
            java.util.LinkedHashMap<VisChannel, Integer> map = new java.util.LinkedHashMap<>();
            for (VisChannel channel : VisChannel.values()) {
                map.put(channel, VIS_PER_CHANNEL_CENTIVIS);
            }
            return java.util.Collections.unmodifiableMap(map);
        }

        /** 总槽数。 */
        public static final int SLOT_COUNT = 2;

        private final ItemStackHandler items = new ItemStackHandler(SLOT_COUNT) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
            }
        };

        public XuanshuNodeModifierBlockEntity(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
            super(ModBlockEntities.XUANSHU_NODE_MODIFIER.get(), pos, state);
        }

        /** 容器句柄（Menu 与运转共用）。 */
        public IItemHandler getItems() {
            return this.items;
        }

        /** 界面槽位直接改这里（服务端写入 / 客户端同步都由菜单机制负责）。 */
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
         * 持法杖右键：为正上方的缸中节点运转（一笔 = 一瓶天华 + 一枚玄枢锭 + 六元各 {@value #VIS_PER_CHANNEL} 点）。
         *
         * <p>判定顺序（任何一条不满足就整笔不动、只弹提示）：</p>
         * <ol>
         *   <li>正上方没有缸中节点 → 「台上方并无缸中节点」；</li>
         *   <li>华槽不是天域之华 → 提示缺华（**任意**一瓶天华都行，不看里面封着什么，
         *       也不管与上一瓶同不同类 —— 第二百一十四批用户「只要是天域之华，
         *       不需要检测是否相同」）；</li>
         *   <li>锭槽没有玄枢锭 → 提示缺锭；</li>
         *   <li>法杖六元不足（{@link #VIS_COST} 用 {@link VisAction#SIMULATE} <b>先试算</b>，
         *       不扣）→ 提示「法杖要素不足」；</li>
         *   <li>都齐了：按 {@code XuanshuNodeModifierMenu#nextAspect} 抽一门
         *       （第一百六十三批三段式：<b>先补六门基础；六门没满时八成加基础、两成添新要素；
         *       六门各满后八成加现有要素、两成添新要素</b>），给它
         *       base / current 各 +{@value #ASPECT_AMOUNT}，写回走
         *       {@link NodeApi#replaceLoadedState} 的 CAS（读当前 → 算新的 → 原状态当
         *       expected 交回）：对不上就是本体正忙着，<b>不扣要素、不吃材料</b>，下一击重试；</li>
         *   <li>CAS 成功后<b>才</b>真扣要素（{@link VisAction#EXECUTE}）并吃掉一瓶天华 + 一枚锭。</li>
         * </ol>
         *
         * <p>★ 这个「先 SIMULATE 试算 → 写节点 → 再 EXECUTE 真扣」的顺序是刻意的：
         * 保证<b>绝不出现「扣了东西但节点没变」</b>（用户第九十批那条「对不上不消耗材料」的底线），
         * 而同 tick 内 SIMULATE 过了 EXECUTE 必然成功，不会白给。</p>
         */
        public void applyWithWand(net.minecraft.world.level.Level level, Player player, ItemStack wand) {
            if (!(level instanceof ServerLevel server) || this.worldPosition.above() == null) {
                return;
            }
            // ★ 第一百五十二批：前置——南斗五星齐明，且五星位上各供一枚玄枢锭
            if (!StarRite.nandouFiveIngot(level, this.worldPosition)) {
                say(player, "message.skydeitymysticpivot.star_gate_nandou");
                return;
            }
            BlockPos nodePos = this.worldPosition.above();
            if (!(server.getBlockEntity(nodePos) instanceof AuraNodeView view)) {
                if (player != null && !level.isClientSide()) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.skydeitymysticpivot.node_modifier.no_node"), true);
                }
                return;
            }

            // ---- 华槽：**任意**天域之华（不看内容、不看与上一瓶是否同类）----
            ItemStack bloomStack = this.items.getStackInSlot(SLOT_BLOOM);
            if (!XuanshuNodeModifierMenu.isWispEssence(bloomStack)) {
                say(player, "message.skydeitymysticpivot.node_modifier.no_bloom");
                return;
            }

            // ---- 锭槽：玄枢锭 → 运转费 ----
            ItemStack ingot = this.items.getStackInSlot(SLOT_INGOT);
            if (ingot.getCount() < INGOT_COST || !ingot.is(INGOT_ITEM)) {
                say(player, "message.skydeitymysticpivot.node_modifier.no_ingot");
                return;
            }

            // ---- 法杖六元：先试算（不扣），不够就到此为止 ----
            VisPaymentResult canPay = ThaumcraftApiHelper.consumeExactVisFromWand(
                    wand, player, VIS_COST, VisAction.SIMULATE);
            if (canPay != VisPaymentResult.AVAILABLE) {
                say(player, "message.skydeitymysticpivot.no_vis");
                return;
            }

            // ---- 效果：先补六门基础、六门齐了从其他要素里一直抽奖（见 nextAspect）----
            AuraNodeState old = view.nodeState();
            AspectId picked = XuanshuNodeModifierMenu.nextAspect(old, server.getRandom());
            AuraNodeState next = XuanshuNodeModifierMenu.addAspects(old,
                    java.util.Map.of(picked, 1), ASPECT_AMOUNT);
            if (!NodeApi.replaceLoadedState(server, nodePos, old, next, VisAction.EXECUTE).successful()) {
                return;   // 本体正忙着：一分不扣，下一击重试
            }

            // ---- CAS 成功，才真扣：要素 → 天华 → 锭 ----
            ThaumcraftApiHelper.consumeExactVisFromWand(wand, player, VIS_COST, VisAction.EXECUTE);
            this.items.extractItem(SLOT_BLOOM, 1, false);
            this.items.extractItem(SLOT_INGOT, INGOT_COST, false);
            this.setChanged();
            server.playSound(null, nodePos, SoundEvents.BEACON_ACTIVATE,
                    SoundSource.BLOCKS, 0.6F, 1.4F);
            server.sendParticles(ParticleTypes.END_ROD,
                    nodePos.getX() + 0.5, nodePos.getY() - 0.3, nodePos.getZ() + 0.5,
                    24, 0.3, 0.2, 0.3, 0.02D);
        }

        private static void say(Player player, String key) {
            if (player != null) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(key), true);
            }
        }

        /**
         * 方块被挖 / 炸 / 活塞推时把两格暂存<b>散出来</b>（内容不会自动掉落，
         * 原版只会掉方块自己；创造模式同样生效——见炎炉同款注释）。
         */
        public void dropContents() {
            Containers.dropContents(this.level, this.worldPosition, dynamicDrop());
        }

        /** 把两槽内容倒进一只临时容器里供 {@link Containers#dropContents} 使用。 */
        private net.minecraft.world.Container dynamicDrop() {
            net.minecraft.world.SimpleContainer out =
                    new net.minecraft.world.SimpleContainer(SLOT_COUNT);
            for (int i = 0; i < SLOT_COUNT; i++) {
                out.setItem(i, this.items.getStackInSlot(i));
            }
            return out;
        }

        // ------------------------------------------------------------------
        // MenuProvider：右键（空手 / 持非法杖）打开「司南 · 节点改易」
        // ------------------------------------------------------------------

        @Override
        public Component getDisplayName() {
            return Component.translatable("block.skydeitymysticpivot.xuanshu_node_modifier");
        }

        @Override
        @Nullable
        public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
            return new XuanshuNodeModifierMenu(id, inv, this);
        }

        /** 一次运转给那门随机要素加多少。 */
        public static final int ASPECT_AMOUNT = XuanshuNodeModifierMenu.ASPECT_AMOUNT;
    }

    private ModBlockEntities() {
    }
}
