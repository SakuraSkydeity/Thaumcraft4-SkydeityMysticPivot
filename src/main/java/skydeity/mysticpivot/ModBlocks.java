package skydeity.mysticpivot;

import com.mojang.serialization.MapCodec;
import dev.tc4port.thaumcraft.api.wand.WandCarrierItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import dev.tc4port.thaumcraft.api.wand.WandApi;
import net.minecraft.util.StringRepresentable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import javax.annotation.Nullable;

/**
 * 本模组的方块。
 *
 * <p>玄枢水晶簇（{@code xuanshu_crystal_cluster}，旧名「玄枢魔晶」，第六十五批从零重做）：
 * 仿本体「复相魔晶」的注魔稳定水晶。世界模型是 {@code models/crystal_cluster.obj}——
 * <b>十四枝六棱晶柱、大小分五档</b>（巨 / 高 / 中 / 小 / 芽），长锥尖收顶、无岩座，
 * 整丛各枝**向中心收拢**（位置 ×0.74、柱基 ×0.92、外倾 0.30→0.22）。
 * 贴图 <b>16×16</b>（第七十批由 64×64 降回 16×16 并**去掉竖向渐变**）：
 * 每一行就是一套调色板，行内 6 个棱面各占 2 px（首列亮棱线 + 末列本色），
 * <b>每枝晶柱各取一行</b>，于是同一丛里紫晶 / 蓝紫 / 品红 / 青蓝 / 粉紫等色并存。
 * 方块本体加入了 {@code thaumcraft:infusion_stabilizers} 方块标签
 * （见 {@code data/thaumcraft/tags/block/infusion_stabilizers.json}），因此本体注魔矩阵
 * 会把它认作稳定器——与蜡烛、头颅一样参与对称计算：成对、对称地摆放在注魔矩阵四周时，
 * 每一对都会把不稳定度往下压。</p>
 */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(MysticPivot.MODID);

    public static final DeferredBlock<Block> XUANSHU_CRYSTAL_CLUSTER =
            BLOCKS.registerBlock("xuanshu_crystal_cluster",
                    XuanshuCrystalBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .noCollission()
                            .instabreak()
                            .sound(SoundType.AMETHYST)
                            .pushReaction(PushReaction.DESTROY));

    /**
     * 玄枢炎炉：本体「炼狱熔炉」的单方块版本。有燃料才烧、2 秒 1 件；
     * 六个面各自可设「关闭 / 自动输入 / 自动输出」（空手右键开界面，在「传输配置」面板里点按钮切换），
     * 出厂默认 顶面 = 自动输入、底面 = 自动输出。
     *
     * <p>{@code requiresCorrectToolForDrops()}：<b>必须用镐子挖</b>（配套方块标签
     * {@code minecraft:mineable/pickaxe} 一起加），徒手砸下去什么都不掉；
     * 移除时炉内的物品与燃料会散出来（见 {@code XuanshuInfernalFurnaceBlock#onRemove}，创造模式同样生效）。</p>
     */
    public static final DeferredBlock<XuanshuInfernalFurnaceBlock> XUANSHU_INFERNAL_FURNACE =
            BLOCKS.registerBlock("xuanshu_infernal_furnace",
                    XuanshuInfernalFurnaceBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.NETHER)
                            .strength(3.5F, 12.0F)
                            .sound(SoundType.NETHER_BRICKS)
                            .requiresCorrectToolForDrops()
                            .lightLevel(state -> 7));

    /**
     * 玄枢复刻台：左格放模板、右格原样复刻（连同全部数据组件，一模一样），
     * 1 秒一批、满料一批 64 件。空手右键开界面；挖掉时两格内容散出来
     * （见 {@code XuanshuReplicatorBlock#onRemove}，创造模式同样生效）。
     *
     * <p>★ 第 143 批起不再是整块立方体：模型改成「十二像素高台面 + 台上悬星」，
     * 所以 {@code noOcclusion()} + 自报十二像素形状——台座踩过的光照坑
     * （非整块方块若自认实心，所在格存不住光照）这里直接绕开。</p>
     */
    public static final DeferredBlock<XuanshuReplicatorBlock> XUANSHU_REPLICATOR =
            BLOCKS.registerBlock("xuanshu_replicator",
                    XuanshuReplicatorBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5F, 12.0F)
                            .sound(SoundType.DEEPSLATE)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()
                            .lightLevel(state -> 5));

    /**
     * 晶簇方块：{@code models/crystal_cluster.obj}——**十四枝六棱晶柱、大小分五档**
     * （巨 / 高 / 中 / 小 / 芽），长锥尖收顶；**没有岩座**（第六十八批按用户要求去掉），
     * 晶体直接长在地面上。
     *
     * <p>两处「看着像平面」的老病根已在第六十七批修掉：① 逐面绕序混着（棱锥尖六个面全朝里），
     * 而 {@code render_type: minecraft:cutout} **开着背面剔除**，绕序反的面干脆不画；
     * ② 贴图只给得出两个明暗档，六棱柱的相邻棱面几乎同色。</p>
     *
     * <p>★ 第七十批按用户要求（「贴图 16×16、不要渐变、正常一点」）重做贴图：
     * 扒了原版紫水晶那几张图逐像素对照过 —— 原版用的是**同一套六档平色、完全没有竖向渐变**，
     * 靠「棱面各取一档 + 边缘一条更亮的棱线」读晶体感。本版照此：
     * 贴图 <b>16×16</b>，<b>每一行 = 一套调色板</b>（去渐变后行方向不再承载信息），
     * 行内 6 个棱面各占 2 px（首列亮棱线、末列本色），明度档取 **[L4 L3 L2 L1 L2 L3]**
     * 的一圈对称明暗；16 行 ⇒ 16 套配色，<b>每枝晶柱各取一行</b>。
     * 整丛同时**向中心收拢**（见类注释），不再「分这么开」。</p>
     *
     * <p>碰撞为无，选取框略内缩；带方块实体——每 5 tick 把扫描域内注魔矩阵的不稳定度清零，
     * 即「放一台，注魔安全拉满」。</p>
     */
    public static class XuanshuCrystalBlock extends Block implements EntityBlock {

        private static final VoxelShape SHAPE = Block.box(1.0, 0.0, 1.0, 15.0, 15.0, 15.0);

        public XuanshuCrystalBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new ModBlockEntities.XuanshuCrystalBlockEntity(pos, state);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (level.isClientSide || type != ModBlockEntities.XUANSHU_CRYSTAL.get()) {
                return null;
            }
            return (BlockEntityTicker<T>) (BlockEntityTicker<ModBlockEntities.XuanshuCrystalBlockEntity>)
                    (lvl, p, st, be) -> ModBlockEntities.XuanshuCrystalBlockEntity.serverTick(lvl, p, be);
        }
    }


    /**
     * 玄枢充能器（{@code xuanshu_charger}）：立在<b>灵气节点正下方</b>，通以红石，
     * 十秒之内把节点充能成<b>充能灵气节点</b>——本体要「节点稳定器 + 节点 + 节点换能器」三段
     * 装配加红石、充五十秒、还要从节点抽走约一千点要素才成的事，这一台一个人干完：
     * <ul>
     *   <li>快：{@value ModBlockEntities.XuanshuChargerBlockEntity#CHARGE_TICKS} tick（十秒）充能，
     *       本体换能器是 1000 tick（五十秒）；</li>
     *   <li>省：每 {@value ModBlockEntities.XuanshuChargerBlockEntity#DRAIN_INTERVAL} tick 才从节点
     *       随机抽 1 点要素（全程至多四十点），本体是每 tick 抽 1 点；</li>
     *   <li>强：充能完成后节点<b>每门要素的上限随时间继续上涨</b>（每秒 +1024），
     *       每 tick 产出按 {@code sqrt(上限 × 系数)} 算，一路涨到
     *       {@value ModBlockEntities.XuanshuChargerBlockEntity#POOL_TARGET}/t 封顶
     *       （系数取节点自己的修饰符，走 {@code NodeApi.registry()}）——这是「玄枢」相对本体的增量。</li>
     * </ul>
     *
     * <p>模型是一尊<b>鎏金黑金的星辰鼎</b>（第六十七批重做、第六十八批去横纹、第六十九批加图案）：
     * 三足、八棱腹、一对立耳，腹里一蓬星辰晶焰。
     * ★ 第六十八批改了两处观感：① 贴图不再竖着排一串金箍（那样进游戏是一圈圈横纹）；
     * ② 鼎身的「星辰」不再画在贴图上——每个棱面只摊到极窄的一列，
     * 贴图落一个亮点会被拉成一条横贯棱面的亮带；改成 {@code add_spark} 的
     * **三维小星钉**（正八面体），钉在腹壁上。同时**去掉了全部粒子效果**，只留音效。
     * ★ 第六十九批「鼎内外加一点图案」，全部走**三维浮雕**（贴图里画花会被棱面拉成横带）：
     * 外面 8 条**扉棱**（沿八棱棱缝、从腹底立到口沿的鎏金竖脊）+ 每面一枚鎏金星辰钉；
     * 里面把碗内壁拆成**暗铜下段 + 口内一圈鎏金弦纹带**，每面再钉一枚内壁星钉。
     * 贴图同时升到 32×32（腹 / 鼎心 / 内壁三段各占一条 UV 区），
     * 模型 JSON 的 {@code flip_v} 由 true 改回 **false**（原先会让整张贴图在游戏里上下颠倒）。
     * 灵气节点浮在鼎口正上一格，读作「鼎托着节点」。故碰撞箱改用 {@link #SHAPE}（15×14×15，不再占满整格）。
     * ★ 第七十批：整鼎按用户要求<b>放大 1.10</b>（`MODEL_SCALE`，落盘前统一变换顶点），
     * 碰撞箱同步由 14×13×14 放到 15×14×15。
     * 断掉红石，节点会在十余秒内缓缓退回普通灵气节点，
     * 涨过的要素上限随之还原（不留下永久增益）。充能中若把充能器挖走，节点照本体规矩爆炸
     * （{@code AuraNodeEnergizedMachineMixin} 让本体结构检查认这座底座；底座没了，检查自然失效）。</p>
     *
     * <p>必须用镐子采掘（{@code requiresCorrectToolForDrops} + {@code minecraft:mineable/pickaxe} 标签）。</p>
     */
    public static final DeferredBlock<XuanshuChargerBlock> XUANSHU_CHARGER =
            BLOCKS.registerBlock("xuanshu_charger",
                    XuanshuChargerBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .strength(3.5F, 12.0F)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()
                            .lightLevel(state -> state.getValue(XuanshuChargerBlock.ACTIVE) ? 8 : 0));

    /**
     * 玄枢注灵台（{@code xuanshu_node_modifier}，第八十八批新增、第八十九批改<b>方块</b>）：
     * 一尊<b>方块</b>（第九十八批：用户「灵气节点转化改为方块吧 不要模型了 删掉」——
     * OBJ 整套退役，改成本 mod 三台机器同款的 {@code cube_bottom_top} 立方体，
     * 贴图 {@code textures/block/xuanshu_node_modifier[_top|_bottom].png}）。
     *
     * <p>用法：把本台放下，<b>缸中节点摆在它正上方</b>；右键开界面，
     * 把<b>任意天域之华</b>放进华槽、<b>玄枢锭</b>放进锭槽；然后<b>手持法杖右键本台</b>
     * 即为罐中节点运转——<b>随机一门要素 +{@value ModBlockEntities.XuanshuNodeModifierBlockEntity#ASPECT_AMOUNT}</b>，
     * 玄枢锭作运转费、每次一枚，那瓶天华（任意）也扣一瓶。
     * 全部改易逻辑在
     * {@link ModBlockEntities.XuanshuNodeModifierBlockEntity#applyWithWand}。</p>
     *
     * <p>★ 第九十七批起<b>不再改节点类型</b>：原先靠六件转化物识别的那条路按用户要求取消。</p>
     *
     * <p>必须用镐子采掘（{@code requiresCorrectToolForDrops} + {@code minecraft:mineable/pickaxe}
     * 标签；破坏时两格暂存由方块实体 {@code onRemove} 散出）。</p>
     */
    public static final DeferredBlock<Block> XUANSHU_NODE_MODIFIER =
            BLOCKS.registerBlock("xuanshu_node_modifier",
                    XuanshuNodeModifierBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_GRAY)
                            .strength(3.5F, 12.0F)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()
                            .noOcclusion());

    /**
     * 玄枢充能器方块：整块立方体，与三台机器同档硬度。
     *
     * <p>{@link #ACTIVE} 是「正在干活」（红石通着、且上方确有节点）：只为换贴图与灯光——
     * 激活态那张是<b>逐帧动画</b>（{@code xuanshu_charger_active.png} + {@code .mcmeta}），
     * 晶核一圈圈转，和本体充能器「干活时有动静」一个意思；真正的充能进度在方块实体里。</p>
     *
     * <p>职责分工：
     * <ul>
     *   <li>状态机（充能 / 转化 / 掉电还原）在
     *       {@link ModBlockEntities.XuanshuChargerBlockEntity}；</li>
     *   <li>「充能器 = 合法充能装配」由 {@code AuraNodeEnergizedMachineMixin} 补；</li>
     *   <li>写节点状态（换方块后新实体是空的）走 {@code compat.NodeStateBridge} 窄口
     *       （直接调本体 public 的 {@code setNodeState}，不用 Mixin）；</li>
     *   <li>本类只负责邻居变动时把变更<b>透传给正上方的灵气节点</b>（同本体
     *       {@code NodeStabilizerBlock#neighborChanged} 的做法），让本体的
     *       {@code checkStructure} 被触发——红石源摆在节点斜对角时，原版邻居传播到不了节点。</li>
     * </ul>
     */
    public static class XuanshuChargerBlock extends BaseEntityBlock {

        /** 是否正在干活（红石通着且上方有节点）：决定贴图与灯光。 */
        public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

        /**
         * 鼎形碰撞 / 选中箱：三足 + 腹 + 外翻口沿 + 双耳约占 15×14×15。
         * 第七十批整鼎放大 1.10 之后，模型外沿为 x / z 0.033~0.967、
         * 双耳顶 y≈0.799、鼎心尖 y≈0.853，故碰撞箱也跟着放大一档
         * （原 1.0~15.0 / 高 13）；仍不是整格方块——玩家能走近鼎腹、也能站到耳高以下的腹顶上。
         */
        private static final VoxelShape SHAPE = Block.box(0.5, 0.0, 0.5, 15.5, 14.0, 15.5);

        public XuanshuChargerBlock(Properties properties) {
            super(properties);
            registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(ACTIVE);
        }

        @Override
        protected MapCodec<? extends BaseEntityBlock> codec() {
            return simpleCodec(XuanshuChargerBlock::new);
        }

        @Override
        protected RenderShape getRenderShape(BlockState state) {
            return RenderShape.MODEL;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new ModBlockEntities.XuanshuChargerBlockEntity(pos, state);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (level.isClientSide || type != ModBlockEntities.XUANSHU_CHARGER.get()) {
                return null;
            }
            return (BlockEntityTicker<T>) (BlockEntityTicker<ModBlockEntities.XuanshuChargerBlockEntity>)
                    (lvl, p, st, be) -> ModBlockEntities.XuanshuChargerBlockEntity.serverTick(lvl, p, be);
        }

        /**
         * 与本体 {@code NodeStabilizerBlock#neighborChanged} 等价：把这次变更透传给正上方的灵气节点，
         * 让本体自己的 {@code AuraNodeBlock#checkStructure} 被触发（充能节点装配是否还完整，
         * 只有本体自己说了算）。转交收在窄口 {@link skydeity.mysticpivot.compat.NodeStructureBridge}
         * 里，本类不 import 本体实现包。
         */
        @Override
        public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                    BlockPos neighborPos, boolean movedByPiston) {
            super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
            skydeity.mysticpivot.compat.NodeStructureBridge.checkStructure(level, pos.above());
        }
    }

    /**
     * 玄枢注灵台（原名「玄枢灵气节点修改器」，{@code xuanshu_node_modifier}）方块：
     * 右键（空手 / 持非法杖）开界面；<b>持法杖右键 = 运转</b>。
     *
     * <p>「持法杖右键」之所以单独一路：TC4 的语义一直是「法杖 = 施术媒介」，
     * 用 {@code WandApi.isBuiltinWand} 判定（本体全部等级的法杖都算，
     * 与 {@code WandAutoRefill} 同一口径），不写死 {@code thaumcraft:wand}。</p>
     *
     * <p>★ 第一百批：形制改仿原版附魔台——只有 12/16 高（{@link #SHAPE}），
     * 台面上再坐一尊小香炉（模型里 y=12 以上那段）。碰撞箱随之收到 12，
     * 于是玩家能踩上台面、炉身可穿（与附魔台上那本书同理）。</p>
     */
    public static class XuanshuNodeModifierBlock extends BaseEntityBlock {

        /**
         * 碰撞 / 选中箱：底板 + 四角柱 + 顶环（第一百四十四批改不完整方块）——
         * 中间与四侧完全镂空，缸中节点从顶环正上方读作「坐进环里」。
         * ★ 非整块方块必须配 {@code noOcclusion()}（注册处已加），否则所在格存不住光照
         * （台座踩过的坑，见 {@code XuanshuPedestalBlock} 注释）。
         */
        private static final VoxelShape SHAPE = Shapes.or(
                Block.box(0.0, 0.0, 0.0, 16.0, 2.0, 16.0),
                Block.box(0.0, 2.0, 0.0, 3.0, 10.0, 3.0),
                Block.box(13.0, 2.0, 0.0, 16.0, 10.0, 3.0),
                Block.box(0.0, 2.0, 13.0, 3.0, 10.0, 16.0),
                Block.box(13.0, 2.0, 13.0, 16.0, 10.0, 16.0),
                Block.box(0.0, 10.0, 0.0, 16.0, 12.0, 3.0),
                Block.box(0.0, 10.0, 13.0, 16.0, 12.0, 16.0),
                Block.box(0.0, 10.0, 3.0, 3.0, 12.0, 13.0),
                Block.box(13.0, 10.0, 3.0, 16.0, 12.0, 13.0));

        public XuanshuNodeModifierBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                      CollisionContext context) {
            return SHAPE;
        }

        @Override
        protected MapCodec<? extends BaseEntityBlock> codec() {
            return simpleCodec(XuanshuNodeModifierBlock::new);
        }

        @Override
        protected RenderShape getRenderShape(BlockState state) {
            return RenderShape.MODEL;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new ModBlockEntities.XuanshuNodeModifierBlockEntity(pos, state);
        }

        /** 空手右键：开界面。 */
        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                   Player player, BlockHitResult hit) {
            if (!level.isClientSide
                    && level.getBlockEntity(pos) instanceof ModBlockEntities.XuanshuNodeModifierBlockEntity be) {
                player.openMenu(be);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        /**
         * 方块被挖 / 炸 / 活塞推时两格暂存散出来（创造模式同样生效），
         * 照三台机器的同款写法（见 {@code XuanshuInfernalFurnaceBlock#onRemove} 注释）。
         */
        @Override
        protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                                boolean isMoving) {
            if (!state.is(newState.getBlock())
                    && level.getBlockEntity(pos) instanceof ModBlockEntities.XuanshuNodeModifierBlockEntity be) {
                be.dropContents();
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }

        /**
         * 持物右键：**法杖** = 为正上方的缸中节点运转；别的东西 = 开界面
         * （台上放着罐子，手上再拿点什么都不用先收起来）。
         *
         * <p>签名是 1.21.1 BlockBehaviour 的 {@code useItemOn(ItemStack, BlockState, Level,
         * BlockPos, Player, InteractionHand, BlockHitResult)}，返回
         * {@link ItemInteractionResult}（不是 {@code UseOnContext} 那套）。</p>
         */
        @Override
        protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                                  BlockPos pos, Player player, InteractionHand hand,
                                                  BlockHitResult hit) {
            if (level.getBlockEntity(pos) instanceof ModBlockEntities.XuanshuNodeModifierBlockEntity be) {
                if (WandApi.isBuiltinWand(stack)) {
                    if (!level.isClientSide()) {
                        // 第一百批：法杖右键要收「六元各 129 点」，所以把杖 stack 一并交下去
                        be.applyWithWand(level, player, stack);
                    }
                    return ItemInteractionResult.SUCCESS;
                }
                if (!level.isClientSide()) {
                    player.openMenu(be);
                }
                return ItemInteractionResult.SUCCESS;
            }
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
    }

    /**
     * 玄枢奥术台方块（第八十九批新增）：外貌照原版附魔台（贴图与模型<b>直接复制</b>进
     * 本 mod，见 {@code models/block/xuanshu_arcane_table.json} 与
     * {@code textures/block/xuanshu_arcane_table_*.png}）。
     *
     * <p>模型第九十七批改过：**中间那根五棱晶柱删掉**，台顶悬一枚<b>漂浮的四芒星</b>
     * （三张零厚度星面 + {@code render_type: cutout}，见 {@code gen_arcane_table_star.py}）。</p>
     *
     * <p>右键开界面放入要升附魔的物品与玄枢锭；<b>持法杖右键本台</b>运转——
     * 消耗玄枢锭 1 枚 + 法杖六种要素各 {@link XuanshuArcaneTableBlockEntity#VIS_PER_CHANNEL} 点（整 Vis），
     * 使物品所有附魔 +1（单条封顶 {@link XuanshuArcaneTableBlockEntity#MAX_ENCHANT_LEVEL}）。
     * 逻辑见 {@link XuanshuArcaneTableBlockEntity#applyWithWand}。</p>
     */
    public static final DeferredBlock<Block> XUANSHU_ARCANE_TABLE =
            BLOCKS.registerBlock("xuanshu_arcane_table",
                    XuanshuArcaneTableBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_PURPLE)
                            .strength(3.5F, 12.0F)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()
                            .noOcclusion());

    /**
     * 玄枢奥术台：右键开界面；持法杖右键 = 运转（520 魔力换一级附魔）。
     * ★ 第一百四十四批：台顶的辰星由 {@code XuanshuArcaneTableRenderer} 自转渲染，
     *   模型只剩附魔台样的台座；碰撞收到台座 12 像素 + {@code noOcclusion()}。
     */
    public static class XuanshuArcaneTableBlock extends BaseEntityBlock {

        /** 碰撞 / 选中箱：附魔台样的台座（0~12 像素），旋转星不参与碰撞。 */
        private static final VoxelShape SHAPE = Block.box(0.0, 0.0, 0.0, 16.0, 12.0, 16.0);

        public XuanshuArcaneTableBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                      CollisionContext context) {
            return SHAPE;
        }

        @Override
        protected MapCodec<? extends BaseEntityBlock> codec() {
            return simpleCodec(XuanshuArcaneTableBlock::new);
        }

        @Override
        protected RenderShape getRenderShape(BlockState state) {
            return RenderShape.MODEL;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new XuanshuArcaneTableBlockEntity(pos, state);
        }

        /** 空手右键：开界面。 */
        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                   Player player, BlockHitResult hit) {
            if (!level.isClientSide
                    && level.getBlockEntity(pos) instanceof XuanshuArcaneTableBlockEntity be) {
                player.openMenu(be);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        /** 持物右键：法杖 = 运转；别的东西 = 开界面。 */
        @Override
        protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                                  BlockPos pos, Player player, InteractionHand hand,
                                                  BlockHitResult hit) {
            if (level.getBlockEntity(pos) instanceof XuanshuArcaneTableBlockEntity be) {
                if (WandApi.isBuiltinWand(stack)) {
                    if (!level.isClientSide()) {
                        be.applyWithWand(level, player, stack);
                    }
                    return ItemInteractionResult.SUCCESS;
                }
                if (!level.isClientSide()) {
                    player.openMenu(be);
                }
                return ItemInteractionResult.SUCCESS;
            }
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }

        /** 方块消失时散出槽内物品（创造模式同样生效）。 */
        @Override
        protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                                boolean isMoving) {
            if (!state.is(newState.getBlock())
                    && level.getBlockEntity(pos) instanceof XuanshuArcaneTableBlockEntity be) {
                be.dropContents();
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    /** 玄枢炎炉方块：整块立方体，空手右键开界面（侧配在「传输配置」面板里点按钮完成）。 */
    public static class XuanshuInfernalFurnaceBlock extends BaseEntityBlock {

        public XuanshuInfernalFurnaceBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected MapCodec<? extends BaseEntityBlock> codec() {
            return simpleCodec(XuanshuInfernalFurnaceBlock::new);
        }

        @Override
        protected RenderShape getRenderShape(BlockState state) {
            return RenderShape.MODEL;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new XuanshuInfernalFurnaceBlockEntity(pos, state);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (level.isClientSide || type != ModBlockEntities.XUANSHU_INFERNAL_FURNACE.get()) {
                return null;
            }
            return (BlockEntityTicker<T>) (BlockEntityTicker<XuanshuInfernalFurnaceBlockEntity>)
                    (lvl, p, st, be) -> XuanshuInfernalFurnaceBlockEntity.serverTick(lvl, p, st, be);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                   Player player, BlockHitResult hit) {
            // 空手右键打开 GUI（侧配在界面里点按钮完成，不再潜行点面）
            if (!level.isClientSide
                    && level.getBlockEntity(pos) instanceof XuanshuInfernalFurnaceBlockEntity furnace) {
                player.openMenu(furnace);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        /**
         * 炉子被移除时，<b>把肚子里的东西散出来</b>。
         *
         * <p>★ 为什么必须显式写：方块实体的内容不会自动掉落——原版那条「掉落」只走掉落表
         * （只掉方块自己），{@code Container} 那套「破坏时倒空」是方块实体自己实现的。</p>
         *
         * <p>★ 为什么走 {@code onRemove} 而不是 {@code playerDestroy}：后者只在
         * <b>存活模式 + 正确工具</b>破坏时才被调用（见 {@code ServerPlayerGameMode#destroyBlock}），
         * 于是<b>创造模式下挖掉，里面的东西会凭空消失</b>。
         * {@code onRemove} 不挑模式，而且爆炸、活塞推、替换方块等一切消失途径也一并覆盖。
         * {@code !state.is(newState.getBlock())} 用来挡掉「同种方块原地替换」这种误触发。</p>
         */
        @Override
        protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
            if (!state.is(newState.getBlock())
                    && level.getBlockEntity(pos) instanceof XuanshuInfernalFurnaceBlockEntity furnace) {
                furnace.dropContents();
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    /**
     * 玄枢复刻台方块：十二像素高的台面（不再是整块立方体），台上悬一枚青金辰星；
     * 空手右键开界面。移除时的掉落语义与炎炉完全同款（走 {@code onRemove}
     * 覆盖创造 / 爆炸 / 活塞全途径）。
     */
    public static class XuanshuReplicatorBlock extends BaseEntityBlock {

        /** 碰撞 / 选取形状：台面 0~12 像素，上面 4 像素留给悬星（不参与碰撞）。 */
        private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 12.0D, 16.0D);

        public XuanshuReplicatorBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected MapCodec<? extends BaseEntityBlock> codec() {
            return simpleCodec(XuanshuReplicatorBlock::new);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                      CollisionContext context) {
            return SHAPE;
        }

        @Override
        protected RenderShape getRenderShape(BlockState state) {
            return RenderShape.MODEL;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new XuanshuReplicatorBlockEntity(pos, state);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (level.isClientSide || type != ModBlockEntities.XUANSHU_REPLICATOR.get()) {
                return null;
            }
            return (BlockEntityTicker<T>) (BlockEntityTicker<XuanshuReplicatorBlockEntity>)
                    (lvl, p, st, be) -> XuanshuReplicatorBlockEntity.serverTick(lvl, p, st, be);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                   Player player, BlockHitResult hit) {
            if (!level.isClientSide
                    && level.getBlockEntity(pos) instanceof XuanshuReplicatorBlockEntity replicator) {
                player.openMenu(replicator);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        @Override
        protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
            if (!state.is(newState.getBlock())
                    && level.getBlockEntity(pos) instanceof XuanshuReplicatorBlockEntity replicator) {
                replicator.dropContents();
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    /**
     * 玄枢台座：皇宫鎏金橘黄的单格台座（形制仿本体奥术基座），可置物其上；
     * 立于乾之地气（开阔天空）时，台面上的黑曜石缓缓化为哭泣的黑曜石。
     * 台座上方悬着所在处的卦象单字，四周浮一圈斜置椭圆的附魔粒子（见 {@code XuanshuPedestalBlockEntity}）。
     *
     * <p><b>必须 {@code noOcclusion()} 且自报形状</b>：台座是「底座 + 柱 + 顶板」的空心造型，
     * 若只按默认走，{@code getShape} 是整块立方体、{@code canOcclude} 又是真，
     * 光照系统便当它是实心方块——<b>它所在的那一格存不住光照</b>，
     * 于是渲染台面物品时取到的 {@code getLightColor(pos)} 是 0，物品全黑
     * （方块本体没事，因为它按面取邻格的光）。两个都补上：形状照模型三段拼，
     * 并声明不遮挡光照。</p>
     *
     * <p><b>必须用镐子采掘</b>（{@code requiresCorrectToolForDrops} + 镐子方块标签），
     * 徒手砸下去什么都不掉；挖掉时<b>台面上摆着的那件东西会散出来</b>
     * （见 {@code onRemove}，创造性破坏同样生效）。</p>
     */
    public static final DeferredBlock<XuanshuPedestalBlock> XUANSHU_PEDESTAL =
            BLOCKS.registerBlock("xuanshu_pedestal",
                    XuanshuPedestalBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.0F, 10.0F)
                            .sound(SoundType.DEEPSLATE)
                            .requiresCorrectToolForDrops()
                            .noOcclusion());

    /**
     * 玄枢坩埚：本体坩埚的单方块改良版——<b>不要热源、不要水、不会积攒咒流</b>。
     *
     * <p>肚子里放一件触媒，它就会按配方去要要素：<b>自己是一个要素端点</b>
     * （{@code EssentiaTransport}，吸力 128，与本体炼金自动机同值），贴着它的原质管会主动把要素送来；
     * 同时它还从紧邻端点和 5×5×5 内的原质罐 / 储液池里自己取。凑齐后熬 3 秒成器。</p>
     * <p>配方认<b>全部</b>坩埚配方：本模组自己的 {@code xuanshu_crucible}，
     * 以及本体的 {@code thaumcraft:crucible}（本体自带的、别的附属写的、数据包与 KubeJS 生成的）。</p>
     * <p>六个面各有 关闭 / 自动输入 / 自动输出 / 输入输出 四档（空手右键开界面，在「传输配置」里点按钮切换）
     * ——★ 那四档只管<b>物品</b>；要素不走这套。</p>
     * <p>必须用镐子采掘（{@code requiresCorrectToolForDrops} + 镐子方块标签），
     * 挖掉时锅里的触媒与产物会散出来。</p>
     */
    public static final DeferredBlock<XuanshuCrucibleBlock> XUANSHU_CRUCIBLE =
            BLOCKS.registerBlock("xuanshu_crucible",
                    XuanshuCrucibleBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(3.5F, 12.0F)
                            .sound(SoundType.COPPER)
                            .requiresCorrectToolForDrops()
                            .lightLevel(state -> 4)
                            .noOcclusion());

    /**
     * 玄枢炼金炉：把<b>物品烧成源质</b>，再把源质推给周围 5×5×3 里的原质管／罐。
     *
     * <p>与玄枢坩埚正好相反——坩埚「吸要素、做物品」，这台「烧物品、出要素」。
     * 炉膛里 3 个独立烧炼位，可同时烧三种东西；一炉最多 64 件、1 秒一炉。
     * 每推出一份要素就画一条「从炉心奔向那根管」的粒子流。</p>
     *
     * <p>六个面只有两档：关闭 / 自动输入（这台机器没有物品产出，产物是源质）。
     * 自动输入只收烧得出要素的东西。</p>
     *
     * <p>必须用镐子采掘（{@code requiresCorrectToolForDrops} + 镐子方块标签），
     * 挖掉时炉内的料会散出来。</p>
     */
    public static final DeferredBlock<XuanshuAlchemyFurnaceBlock> XUANSHU_ALCHEMY_FURNACE =
            BLOCKS.registerBlock("xuanshu_alchemy_furnace",
                    XuanshuAlchemyFurnaceBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.NETHER)
                            .strength(3.5F, 12.0F)
                            .sound(SoundType.NETHER_BRICKS)
                            .requiresCorrectToolForDrops()
                            .lightLevel(state -> 6));

    /**
     * 玄枢坩埚方块：机械智能坩埚造型的单方块（厚壁方炉 + 机械束口 + 正面透镜）。
     *
     * <p>碰撞形状是<b>真的空心</b>（底板 + 四壁 + 四边束口，逐盒与模型对齐），
     * 且 {@code noOcclusion()}：这样锅体内壁不会把光照挡在外头，
     * 也才能从上方把触媒丢进去、隔着锅沿看见锅底。</p>
     *
     * <p>空手右键开界面；方块实体负责抽要素、熬、出货。</p>
     */
    public static class XuanshuCrucibleBlock extends BaseEntityBlock {

        /**
         * 底板 + 四壁（与 {@code models/block/xuanshu_crucible.json} 的前 5 个 element 逐盒对应）。
         *
         * <p>第 6 个 element 是正面那块外凸的透镜（{@code z = -0.45 ~ -0.05}，露在方块格外），
         * 纯装饰，不参与碰撞——挡在锅口那一小块没有体积意义，写进来反而会在贴着墙走时卡人。</p>
         */
        private static final VoxelShape SHAPE = Shapes.or(
                Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.5D, 16.0D),
                Block.box(0.0D, 2.5D, 0.0D, 16.0D, 16.0D, 3.0D),
                Block.box(0.0D, 2.5D, 13.0D, 16.0D, 16.0D, 16.0D),
                Block.box(0.0D, 2.5D, 3.0D, 3.0D, 16.0D, 13.0D),
                Block.box(13.0D, 2.5D, 3.0D, 16.0D, 16.0D, 13.0D));

        public XuanshuCrucibleBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        protected MapCodec<? extends BaseEntityBlock> codec() {
            return simpleCodec(XuanshuCrucibleBlock::new);
        }

        @Override
        protected RenderShape getRenderShape(BlockState state) {
            return RenderShape.MODEL;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new XuanshuCrucibleBlockEntity(pos, state);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (type != ModBlockEntities.XUANSHU_CRUCIBLE.get()) {
                return null;
            }
            if (level.isClientSide) {
                // 客户端 tick 只负责抽要素轨迹与锅口翻泡
                return (BlockEntityTicker<T>) (BlockEntityTicker<XuanshuCrucibleBlockEntity>)
                        (lvl, p, st, be) -> XuanshuCrucibleBlockEntity.clientTick(lvl, p, be);
            }
            return (BlockEntityTicker<T>) (BlockEntityTicker<XuanshuCrucibleBlockEntity>)
                    (lvl, p, st, be) -> XuanshuCrucibleBlockEntity.serverTick(lvl, p, st, be);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                   Player player, BlockHitResult hit) {
            if (!level.isClientSide
                    && level.getBlockEntity(pos) instanceof XuanshuCrucibleBlockEntity crucible) {
                player.openMenu(crucible);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        /**
         * 手持触媒右键锅沿 = 把料丢进锅里（TC 坩埚的老规矩）。
         *
         * <p>只认触媒：手里不是触媒就交回默认逻辑，方块照常能贴上去放，
         * 不至于让这口锅把一整片墙的右键都吃掉。</p>
         */
        @Override
        protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                  Player player, InteractionHand hand, BlockHitResult hit) {
            if (stack.isEmpty()
                    || !(level.getBlockEntity(pos) instanceof XuanshuCrucibleBlockEntity crucible)
                    || !crucible.isCatalyst(stack)) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (level.isClientSide) {
                // 两边都要「这一下有反应」，免得客户端先一步去放方块
                return ItemInteractionResult.SUCCESS;
            }
            int moved = crucible.insertByHand(stack, stack.getCount());
            if (moved <= 0) {
                return ItemInteractionResult.FAIL;
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(moved);
            }
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4F,
                    1.4F + level.random.nextFloat() * 0.2F);
            return ItemInteractionResult.CONSUME;
        }

        /** 挖掉时把锅里的触媒与产物散出来（★ 走 {@code onRemove}，创造模式也会掉，理由同炎炉那一处）。 */
        @Override
        protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
            if (!state.is(newState.getBlock())
                    && level.getBlockEntity(pos) instanceof XuanshuCrucibleBlockEntity crucible) {
                crucible.dropContents();
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    /**
     * 玄枢炼金炉方块：与炎炉同一套机械造型（整块立方体，顶面朝上）。
     *
     * <p>空手右键开界面（侧配在「传输配置」面板里点按钮切换）；
     * 手持<b>烧得出要素的东西</b>右键炉沿 = 直接投料。</p>
     */
    public static class XuanshuAlchemyFurnaceBlock extends BaseEntityBlock {

        public XuanshuAlchemyFurnaceBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected MapCodec<? extends BaseEntityBlock> codec() {
            return simpleCodec(XuanshuAlchemyFurnaceBlock::new);
        }

        @Override
        protected RenderShape getRenderShape(BlockState state) {
            return RenderShape.MODEL;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new XuanshuAlchemyFurnaceBlockEntity(pos, state);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (type != ModBlockEntities.XUANSHU_ALCHEMY_FURNACE.get()) {
                return null;
            }
            if (level.isClientSide) {
                // 客户端 tick 只负责画「要素奔向管子」的粒子流
                return (BlockEntityTicker<T>) (BlockEntityTicker<XuanshuAlchemyFurnaceBlockEntity>)
                        (lvl, p, st, be) -> XuanshuAlchemyFurnaceBlockEntity.clientTick(lvl, p, be);
            }
            return (BlockEntityTicker<T>) (BlockEntityTicker<XuanshuAlchemyFurnaceBlockEntity>)
                    (lvl, p, st, be) -> XuanshuAlchemyFurnaceBlockEntity.serverTick(lvl, p, st, be);
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                   Player player, BlockHitResult hit) {
            if (!level.isClientSide
                    && level.getBlockEntity(pos) instanceof XuanshuAlchemyFurnaceBlockEntity furnace) {
                player.openMenu(furnace);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        /**
         * 手持料右键炉沿 = 直接投进去。
         *
         * <p>只认「烧得出要素的东西」与「能当薪的燃料」（前者进三膛、后者进炉下的燃料槽）：
         * 手里别的物品就交回默认逻辑，不至于让这台炉子把一整片墙的右键都吃掉。</p>
         */
        @Override
        protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                  Player player, InteractionHand hand, BlockHitResult hit) {
            if (stack.isEmpty()
                    || !(level.getBlockEntity(pos) instanceof XuanshuAlchemyFurnaceBlockEntity furnace)
                    || !furnace.accepts(stack)) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (level.isClientSide) {
                // 两边都要「这一下有反应」，免得客户端先一步去放方块
                return ItemInteractionResult.SUCCESS;
            }
            int moved = furnace.insertByHand(stack, stack.getCount());
            if (moved <= 0) {
                return ItemInteractionResult.FAIL;
            }
            if (!player.getAbilities().instabuild) {
                stack.shrink(moved);
            }
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.4F,
                    1.4F + level.random.nextFloat() * 0.2F);
            return ItemInteractionResult.CONSUME;
        }

        /** 挖掉时把炉里的料散出来（★ 走 {@code onRemove}，创造模式也会掉，理由同炎炉那一处）。 */
        @Override
        protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
            if (!state.is(newState.getBlock())
                    && level.getBlockEntity(pos) instanceof XuanshuAlchemyFurnaceBlockEntity furnace) {
                furnace.dropContents();
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    /**
     * 八枚卦位石：各应一卦，立于注魔祭坛中央台座周围 5×5 之内方能感应（见 Fengshui 与坎门禁 Mixin），
     * 也是<b>八方净界阵</b>外八格的构件（见 {@link XuanshuPurifyingStoneBlock}）。
     *
     * <p>与三器、台座一样<b>必须用镐子挖</b>（{@code requiresCorrectToolForDrops} + 镐子方块标签），
     * 徒手砸下去什么都不掉。石头自己没有内容物，故无需 {@code onRemove} 掉落处理。</p>
     *
     * <p>{@link BaguaStoneBlock#ACTIVE 点亮状态}由净界石一方写入：阵立起时外八格
     * 一齐从「整块卦位石」换形为<b>青金配色的祭坛构件</b>（石台 + 四角柱 + 浮空金环 + 能量晶），
     * 并发 6 级光，阵散即熄——见 {@code XuanshuPurifyingStoneBlock#syncRing}。</p>
     */
    public static final DeferredBlock<Block> BAGUA_STONE_QIAN = baguaStone("bagua_stone_qian");
    public static final DeferredBlock<Block> BAGUA_STONE_DUI = baguaStone("bagua_stone_dui");
    public static final DeferredBlock<Block> BAGUA_STONE_LI = baguaStone("bagua_stone_li");
    public static final DeferredBlock<Block> BAGUA_STONE_ZHEN = baguaStone("bagua_stone_zhen");
    public static final DeferredBlock<Block> BAGUA_STONE_XUN = baguaStone("bagua_stone_xun");
    public static final DeferredBlock<Block> BAGUA_STONE_KAN = baguaStone("bagua_stone_kan");
    public static final DeferredBlock<Block> BAGUA_STONE_GEN = baguaStone("bagua_stone_gen");
    public static final DeferredBlock<Block> BAGUA_STONE_KUN = baguaStone("bagua_stone_kun");

    private static DeferredBlock<Block> baguaStone(String name) {
        return BLOCKS.registerBlock(name, BaguaStoneBlock::new,
                BlockBehaviour.Properties.of()
                        .mapColor(MapColor.METAL)
                        .strength(2.0F, 8.0F)
                        .sound(SoundType.DEEPSLATE_BRICKS)
                        .requiresCorrectToolForDrops()
                        .lightLevel(state -> state.getValue(BaguaStoneBlock.ACTIVE) ? 6 : 0));
    }

    /**
     * 卦位石方块：{@code active} 记的是「这块石在不在一座<b>正跑着的</b>八方净界阵上」。
     *
     * <p>石头自己是<b>不会亮的</b>——没有交互、没有计划刻，亮不亮全听净界石的：
     * 玩家拿法杖右键阵心开阵时，净界石把外八格一齐点亮；收阵、阵散、阵心被挖，
     * 再一齐熄掉。所以 {@link #ACTIVE} 只有一个写入口
     * （{@code XuanshuPurifyingStoneBlock#syncRing}），与四象石砖的 {@code active}
     * 「由台座写、不由玩家点」同一路数。</p>
     *
     * <p>点亮即换形：整块卦位石化作<b>青金配色的坛基</b>——满宽基座石台，九块连成一片坛面，
     * 并发 6 级光。八方共用同一模型同一套色（与阵心一致）；尖晶只有阵心那一根，
     * 九块一起变化，隔着老远就能看出「阵立起来了」。见
     * {@code models/block/array_station_active.json}。</p>
     */
    public static class BaguaStoneBlock extends Block {
        /** 是否正处于一座生效的八方净界阵中（由净界石写入，玩家改不了）。 */
        public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

        /**
         * 激活态的碰撞形状：只认基座台（模型 0~4 那段）。
         *
         * <p>激活即换形：整块立方体变成一座满宽石台（见
         * {@code models/block/array_station_active.json}，八方共用同一模型、同一套青金配色）。
         * 模型矮下去了，碰撞若还是整块，玩家就会踩在台面上头的空气中——所以碰撞跟着模型收成台面。</p>
         */
        private static final VoxelShape ACTIVE_COLLISION =
                Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D);

        public BaguaStoneBlock(Properties properties) {
            super(properties);
            registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(ACTIVE);
        }

        @Override
        protected MapCodec<? extends Block> codec() {
            return simpleCodec(BaguaStoneBlock::new);
        }

        /**
         * 碰撞随 {@link #ACTIVE} 收放：未启是整块石头；激活后只剩基座台。
         *
         * <p>选取形状（{@code getShape}）故意保持整块立方体——法杖右键开关阵要能点在浮空环上，
         * 选框画满一格也与本体注魔矩阵一个路数（模型悬浮、选框整格）。</p>
         */
        @Override
        protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                               CollisionContext context) {
            return state.getValue(ACTIVE) ? ACTIVE_COLLISION : Shapes.block();
        }

        /**
         * 遮挡形状同样跟着模型走：激活后只有基座台是实的。
         *
         * <p>不写这一条的话，默认遮挡形状是整块立方体，紧贴阵法砌的墙会把朝向阵法的面全剔掉——
         * 从墙那边能直接看穿阵法上层的空当。收成台面后，台面照常剔面、照常挡光，
         * 浮空构件部分则当作不存在（与模型一致）。</p>
         */
        @Override
        protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
            return state.getValue(ACTIVE) ? ACTIVE_COLLISION : Shapes.block();
        }

        /**
         * 被挖走时<b>立刻</b>招呼阵心收阵，不等那颗 2 秒的计划刻。
         *
         * <p>卦位石占的是 3×3 的某个非中心格，阵心只可能在它的<b>八个邻格</b>里
         * （边格靠四正交、角格靠四对角），所以八个方向都要问。找到正在跑的阵心就替玩家收阵：
         * 状态写「关」、外八格一齐熄、补一条动作栏提示——挖一块就全暗，反馈是即时的。</p>
         */
        @Override
        protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
            if (!state.is(newState.getBlock()) && !level.isClientSide && state.getValue(ACTIVE)) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos p = pos.offset(dx, 0, dz);
                        if (level.getBlockState(p).getBlock() instanceof XuanshuPurifyingStoneBlock) {
                            XuanshuPurifyingStoneBlock.deactivate((ServerLevel) level, p);
                        }
                    }
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    /**
     * 四象石砖：玄枢台座「开坛」所需的多方块构件。
     *
     * <p>台座本体只是一个座子；要在上面摆东西，得先在<b>台座四角（3×3 的四个对角）</b>
     * 各砌一块四象石砖，再<b>手持法杖右键台座</b>开坛。开坛之后四块砖一齐换形：
     * 由青灰平砖变作<b>金色的斜面阶梯</b>——内侧（贴台座那两边）的退台量<b>逐级照抄台座自己的
     * 退台量</b>（1 / 2 / 4 像素），外侧则一边退一边往下落，四块砖的顶面于是都停在 y6，
     * 正好和台座的肩台同高，连成一条横线（见 {@code models/block/sixiang_stone_brick_active.json}）。
     * 四块砖各自朝哪边升，由
     * {@link SixiangStoneBrickBlock#CORNER} 记着，台座开坛时一并写下去。</p>
     *
     * <p>未启的平砖四面各刻一道印：青龙（东·木）、朱雀（南·火）、白虎（西·金）、玄武（北·水）。
     * 开坛后整块砖染成玄枢金，印文隐去——所以「这块砖在不在坛上」远远就能认出来。</p>
     *
     * <p>与三器、台座、卦位石一样<b>必须用镐子挖</b>。挖掉四块中的任意一块，台座即刻熄火、
     * 其余三块一并落回平砖（见 {@link XuanshuPedestalBlockEntity#deactivate}）。</p>
     */
    public static final DeferredBlock<SixiangStoneBrickBlock> SIXIANG_STONE_BRICK =
            BLOCKS.registerBlock("sixiang_stone_brick",
                    SixiangStoneBrickBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.METAL)
                            .strength(2.0F, 8.0F)
                            .sound(SoundType.DEEPSLATE_BRICKS)
                            .requiresCorrectToolForDrops()
                            .lightLevel(state -> state.getValue(SixiangStoneBrickBlock.ACTIVE) ? 9 : 0));

    /**
     * 四象石砖方块：{@code active} 布尔状态（未启 / 已启）决定模型与贴图换不换，
     * {@link Corner corner} 决定「已启」那套阶梯朝向哪一边。
     *
     * <p><b>为什么要朝向</b>：开坛后四砖各自朝台座那一侧退台、往外的方向落坡，
     * 内侧那几级退台量是<b>照着台座自己的退台量抄的</b>（1 / 2 / 4 像素），所以两块石头贴缝
     * 那条竖棱上不会错台、四块砖的顶面也和台座肩台齐平（见
     * {@code models/block/sixiang_stone_brick_active.json}）。四块砖分散在 3×3 的四个对角上，
     * 各自要「贴台座的那一角」正对台座，所以模型必须能转——方块模型只认 BlockState，
     * 于是把朝向做成状态属性，由台座在开坛时连 {@code active} 一起写下去
     * （见 {@code XuanshuPedestalBlockEntity#lightBricks}）。</p>
     *
     * <p>「已启」与「朝向」都是<b>台座写上去的</b>，不是玩家自己点砖点出来的——
     * 所以它也不该、也不会被玩家手动改：这块砖自己没有 {@code useItemOn}，
     * 右键只会走到「放方块 / 用手里那件东西」的默认逻辑上去。</p>
     */
    public static class SixiangStoneBrickBlock extends Block {

        /** 是否已被台座点亮（换成「四象拱卫」造型、泛金光并发光）。 */
        public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

        /**
         * 「台座在我这一侧的哪个对角」——也就是这块砖的台阶要朝哪边退。
         *
         * <p>取值即方位名：{@code nw} 西北、{@code ne} 东北、{@code se} 东南、{@code sw} 西南。
         * 模型本体是照 <b>{@code nw}</b> 造的（贴台座那一角在 x=0、z=0——那两边的退台退得最狠），
         * 其余三个方向靠 blockstate 里的 {@code y} 旋转 90° 得到；MC 的 {@code y} 旋转是
         * <b>俯视顺时针</b>（+X → +Z），所以四个方位恰好彼此相差 90°。</p>
         */
        public static final EnumProperty<Corner> CORNER =
                EnumProperty.create("corner", Corner.class);

        /** 阶梯抬起的那一角朝向（相对方块自身）。 */
        public enum Corner implements StringRepresentable {
            NW("nw"), NE("ne"), SE("se"), SW("sw");

            private final String name;

            Corner(String name) {
                this.name = name;
            }

            @Override
            public String getSerializedName() {
                return this.name;
            }
        }

        public SixiangStoneBrickBlock(Properties properties) {
            super(properties);
            registerDefaultState(this.stateDefinition.any()
                    .setValue(ACTIVE, false)
                    .setValue(CORNER, Corner.NW));
        }

        /**
         * 由「石砖相对台座的水平偏移」求朝向：台座在哪一侧的对角，退台与坡就朝哪边。
         *
         * <p>偏移 {@code (-1,-1)} 意味着台座在此砖的 <b>东南</b>（+X+Z）方向，
         * 于是东南那一角退台（贴台座）、西北那一头落坡（朝外）。</p>
         */
        public static Corner cornerFacing(int dx, int dz) {
            int tx = -dx;
            int tz = -dz;
            if (tx > 0) {
                return tz > 0 ? Corner.SE : Corner.NE;
            }
            return tz > 0 ? Corner.SW : Corner.NW;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(ACTIVE, CORNER);
        }

        @Override
        protected MapCodec<? extends Block> codec() {
            return simpleCodec(SixiangStoneBrickBlock::new);
        }

        /**
         * 砖被挖掉时<b>立刻</b>招呼对角线那台台座熄火。
         *
         * <p>台座与四砖的关系是「斜对角」——砖在 3×3 的角上，台座在那 3×3 的心里，
         * 所以从砖出发要找的正是四个对角方向。台座自己每 20 tick 也会自查一遍，
         * 这里只是把那最多一秒的延迟抹掉。</p>
         */
        @Override
        protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
            if (!state.is(newState.getBlock()) && !level.isClientSide) {
                for (int dx = -1; dx <= 1; dx += 2) {
                    for (int dz = -1; dz <= 1; dz += 2) {
                        BlockPos p = pos.offset(dx, 0, dz);
                        if (level.getBlockEntity(p) instanceof XuanshuPedestalBlockEntity pedestal) {
                            pedestal.deactivate(level, p);
                        }
                    }
                }
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    /** 物品注册侧按 id 取对应卦位石方块。 */
    /**
     * 玄枢沟灵核心（第一百零二批新增）：一块**白玉鎏金**的祭炼核心，无 BE、无状态、整格立方体。
     *
     * <p>它是本 mod 两件「台」的注魔之基——<b>玄枢奥术台</b>与<b>玄枢注灵台</b>的注魔配方
     * 都以它为中心（{@code central}），见 {@code recipe/xuanshu_arcane_table.json} 与
     * {@code recipe/xuanshu_node_modifier.json}；它自身则由玄枢锭等注魔而成
     * （{@code recipe/xuanshu_spirit_core.json}）。</p>
     *
     * <p>贴图走 `tools/gen_spirit_core.py`（玉底 + 金云纹 + 宝珠芯的「仙家」路数），
     * 模型是 {@code cube_bottom_top}。轻微自发光（{@code lightLevel 6}）——
     * 一颗「心」总该有点光。</p>
     */
    public static final DeferredBlock<Block> XUANSHU_SPIRIT_CORE =
            BLOCKS.registerBlock("xuanshu_spirit_core", Block::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_LIGHT_GREEN)
                            .strength(3.0F, 10.0F)
                            .sound(SoundType.AMETHYST)
                            .requiresCorrectToolForDrops()
                            .lightLevel(state -> 6));

    /**
     * 南北斗枢玄星盘（第一百零六批新增、第一百零七批合并为**唯一方块**）：
     * 一方**青玉盘身、墨玉盘面**的星盘（面刻南北斗，侧作回纹）。
     *
     * <p>盘面上半天为北斗七星、下半天为南斗六星，星点之间暗金细线相连，
     * 中心一枚青玉斗姆珠；侧面青铜回纹，底面墨石；自带微光（亮度 7）。
     * 风格：TC4 奥术手稿 + 中国玄幻青铜罗盘，暗金 / 墨玉 / 青玉 / 朱砂，低饱和。</p>
     *
     * <p>它同时是多方块「星斗十二座」的**唯一构件**：十二方星盘按三列四行摆好，
     * 手持法杖右键任意一方即成型，各方的 {@code role} 换成对应造型（十一座 + 中台），
     * 见 {@link StarDialBlock}。成型后中台亮度 13、其余 7，相邻两格之间会流金色光点。</p>
     */
    public static final DeferredBlock<StarDialBlock> STAR_DIAL =
            BLOCKS.registerBlock("star_dial", StarDialBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.COLOR_BROWN)
                            .strength(3.5F, 8.0F)
                            .sound(SoundType.METAL)
                            .requiresCorrectToolForDrops()
                            .lightLevel(state -> state.getValue(StarDialBlock.ROLE).formed() ? 12 : 7));

    public static DeferredBlock<Block> stoneForItem(String name) {
        return switch (name) {
            case "bagua_stone_qian" -> BAGUA_STONE_QIAN;
            case "bagua_stone_dui" -> BAGUA_STONE_DUI;
            case "bagua_stone_li" -> BAGUA_STONE_LI;
            case "bagua_stone_zhen" -> BAGUA_STONE_ZHEN;
            case "bagua_stone_xun" -> BAGUA_STONE_XUN;
            case "bagua_stone_kan" -> BAGUA_STONE_KAN;
            case "bagua_stone_gen" -> BAGUA_STONE_GEN;
            case "bagua_stone_kun" -> BAGUA_STONE_KUN;
            default -> throw new IllegalArgumentException("unknown bagua stone: " + name);
        };
    }

    /**
     * 卦 → 卦位石方块；{@link Fengshui.Trigram#NONE}（没凝卦气）给 {@code null}。
     *
     * <p>与 {@link #trigramOf(Block)} 一起构成「卦 ↔ 方块」的<b>唯一</b>映射，
     * 方块世界的八卦检测（{@link Fengshui#countTrigramStones}）只认这两个方法，
     * 以后加卦位石也只需改这两处。</p>
     *
     * <p>★ 刻意<b>不建静态 Map</b>：地图要在类初始化时读 {@code BAGUA_STONE_*.get()}，
     * 而 {@code DeferredBlock#get()} 在登记完成前是空的，静态表会把顺序问题引进来。
     * 写成 switch 就只在调用那一刻才取值，天然安全。</p>
     */
    public static Block stoneOf(Fengshui.Trigram trigram) {
        return switch (trigram) {
            case QIAN -> BAGUA_STONE_QIAN.get();
            case DUI -> BAGUA_STONE_DUI.get();
            case LI -> BAGUA_STONE_LI.get();
            case ZHEN -> BAGUA_STONE_ZHEN.get();
            case XUN -> BAGUA_STONE_XUN.get();
            case KAN -> BAGUA_STONE_KAN.get();
            case GEN -> BAGUA_STONE_GEN.get();
            case KUN -> BAGUA_STONE_KUN.get();
            default -> null;
        };
    }

    /** 方块 → 卦；不是卦位石就给 {@link Fengshui.Trigram#NONE}。见 {@link #stoneOf}。 */
    public static Fengshui.Trigram trigramOf(Block block) {
        if (block == BAGUA_STONE_QIAN.get()) {
            return Fengshui.Trigram.QIAN;
        }
        if (block == BAGUA_STONE_DUI.get()) {
            return Fengshui.Trigram.DUI;
        }
        if (block == BAGUA_STONE_LI.get()) {
            return Fengshui.Trigram.LI;
        }
        if (block == BAGUA_STONE_ZHEN.get()) {
            return Fengshui.Trigram.ZHEN;
        }
        if (block == BAGUA_STONE_XUN.get()) {
            return Fengshui.Trigram.XUN;
        }
        if (block == BAGUA_STONE_KAN.get()) {
            return Fengshui.Trigram.KAN;
        }
        if (block == BAGUA_STONE_GEN.get()) {
            return Fengshui.Trigram.GEN;
        }
        if (block == BAGUA_STONE_KUN.get()) {
            return Fengshui.Trigram.KUN;
        }
        return Fengshui.Trigram.NONE;
    }

    /**
     * 玄枢台座方块：底座 + 柱 + 顶板三段造型（克隆本体奥术基座元素），
     * 右键放物 / 取物（台面有物时再右键即取回，手中另持一物则以新换旧；
     * <b>一次只纳一件</b>——整组放上去也只收一个，余数留在手里），
     * 物品由方块实体存储、由渲染器摆到台面上。
     *
     * <p><b>光摆一座台座是不能用的</b>：须先在它的四角（3×3 的四个对角）各砌一块
     * {@link #SIXIANG_STONE_BRICK 四象石砖}，再手持法杖右键台座「开坛」；
     * 未开坛的台座不纳物（见 {@link #useItemOn}）。开坛后四砖换形并发光，
     * 潜行 + 法杖右键可收坛。砖被挖掉、或台座被拆，都会立刻熄火。</p>
     */
    public static class XuanshuPedestalBlock extends BaseEntityBlock {

        /**
         * 柱础（四级收分）+ 石英柱身 + 柱头 + 台面板，逐盒与模型对齐。
         * <p>形状非整块立方体是<b>必须</b>的：台面物品要能取到光照（见类注释）。</p>
         */
        private static final VoxelShape SHAPE = Shapes.or(
                Block.box(0.0D, 0.0D, 0.0D, 16.0D, 2.0D, 16.0D),
                Block.box(1.0D, 2.0D, 1.0D, 15.0D, 3.5D, 15.0D),
                Block.box(2.0D, 3.5D, 2.0D, 14.0D, 5.0D, 14.0D),
                Block.box(4.0D, 5.0D, 4.0D, 12.0D, 6.0D, 12.0D),
                Block.box(5.0D, 6.0D, 5.0D, 11.0D, 13.0D, 11.0D),
                Block.box(4.0D, 12.5D, 4.0D, 12.0D, 13.5D, 12.0D),
                Block.box(2.0D, 13.5D, 2.0D, 14.0D, 16.0D, 14.0D));

        public XuanshuPedestalBlock(Properties properties) {
            super(properties);
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        protected MapCodec<? extends BaseEntityBlock> codec() {
            return simpleCodec(XuanshuPedestalBlock::new);
        }

        @Override
        protected RenderShape getRenderShape(BlockState state) {
            return RenderShape.MODEL;
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new XuanshuPedestalBlockEntity(pos, state);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
            if (type != ModBlockEntities.XUANSHU_PEDESTAL.get()) {
                return null;
            }
            if (level.isClientSide) {
                // 客户端 tick 只负责「吸取丝线」的续期（见 XuanshuPedestalEffects）
                return (BlockEntityTicker<T>) (BlockEntityTicker<XuanshuPedestalBlockEntity>)
                        (lvl, p, st, be) -> XuanshuPedestalBlockEntity.clientTick(lvl, p, be);
            }
            return (BlockEntityTicker<T>) (BlockEntityTicker<XuanshuPedestalBlockEntity>)
                    (lvl, p, st, be) -> XuanshuPedestalBlockEntity.serverTick(lvl, p, st, be);
        }

        /**
         * 右键分三层，按「手里拿的是什么」递减：
         *
         * <ol>
         *   <li><b>手持法杖</b> → 开坛 / 收坛（潜行右键 = 收坛）。
         *       开坛须四隅立齐四象石砖，否则点一下只有一团烟。
         *       ★ 判据用 {@code instanceof WandCarrierItem}（本体 API 里的法杖基类，
         *       {@code WandItem} 与 {@code CustomWandItem} 都继承它），
         *       <b>不调 {@code WandApi.view}</b>——后者要读堆叠上的法杖数据，
         *       客户端拿到的未必齐全，而这里两边都要得出一致的结论。</li>
         *   <li><b>未开坛</b> → 不纳物。手里拿着东西就提示一句，然后把这次交互吃掉
         *       （不吃掉的话，手里的方块会「穿过」台座被放到旁边去，反倒更莫名其妙）。
         *       <b>但台面上原就有东西时例外</b>：照旧允许取回，免得熄火之后东西取不下来
         *       ——这正是当年 {@code FAIL} 埋下的那个坑，别再踩第二次。</li>
         *   <li><b>已开坛</b> → 走原来的放置 / 取回逻辑。</li>
         * </ol>
         *
         * <p><b>取回必须在这里做完，不能指望 {@link #useWithoutItem}。</b> 原版
         * {@code ServerPlayerGameMode.useItemOn} 只在 {@code useItemOn} 返回
         * {@code PASS_TO_DEFAULT_BLOCK_INTERACTION} 时才会转调 {@code useWithoutItem}；
         * 从前台面有物时这里直接返回 {@code FAIL}，于是连空手右键也永远走不到
         * {@code useWithoutItem}——物品一旦放上去就再也拿不下来。</p>
         */
        @Override
        protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                                  BlockPos pos, Player player, InteractionHand hand,
                                                  BlockHitResult hit) {
            if (!(level.getBlockEntity(pos) instanceof XuanshuPedestalBlockEntity pedestal)) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            // 一、手持法杖：开坛 / 收坛
            if (stack.getItem() instanceof WandCarrierItem) {
                if (!level.isClientSide) {
                    if (pedestal.isActive() && player.isShiftKeyDown()) {
                        pedestal.deactivate(level, pos);
                        player.displayClientMessage(
                                Component.translatable("block.skydeitymysticpivot.xuanshu_pedestal.dormant"),
                                true);
                    } else if (pedestal.tryActivate(level, pos)) {
                        player.displayClientMessage(
                                Component.translatable("block.skydeitymysticpivot.xuanshu_pedestal.awakened"),
                                true);
                    } else {
                        player.displayClientMessage(
                                Component.translatable("block.skydeitymysticpivot.xuanshu_pedestal.no_corners"),
                                true);
                        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 1.2F);
                    }
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            boolean empty = pedestal.getItems().getStackInSlot(XuanshuPedestalBlockEntity.SLOT).isEmpty();
            // 二、未开坛且台面本来就空着：不纳物
            if (!pedestal.isActive() && empty) {
                if (!level.isClientSide && !stack.isEmpty()) {
                    player.displayClientMessage(
                            Component.translatable("block.skydeitymysticpivot.xuanshu_pedestal.not_awake"), true);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            if (empty) {
                if (stack.isEmpty()) {
                    // 台面空、手里也空：没有可做之事
                    return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                }
                if (!level.isClientSide) {
                    // ★ 槽上限为 1（见 BE 的 items）：即便手里是一整组，也只放上去一件，
                    //   余数原样留手——「一次只纳一件」就落在这里。
                    ItemStack leftover = pedestal.getItems().insertItem(
                            XuanshuPedestalBlockEntity.SLOT, stack.copy(), false);
                    stack.setCount(leftover.getCount());
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            if (!level.isClientSide) {
                ItemStack taken = pedestal.getItems().extractItem(
                        XuanshuPedestalBlockEntity.SLOT, 64, false);
                if (!taken.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(taken);
                }
                if (!stack.isEmpty()) {
                    // 以新换旧同样只放上去一件（槽上限 1，余数留手）
                    ItemStack leftover = pedestal.getItems().insertItem(
                            XuanshuPedestalBlockEntity.SLOT, stack.copy(), false);
                    stack.setCount(leftover.getCount());
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        /**
         * 空手右键的兜底路径：台面确有物品时取回。
         *
         * <p>正常情况下走不到这里（{@link #useItemOn} 已在台面有物时自己把事情办完并返回了
         * 消费型结果），留着只为防将来有人改动上面那段而再次出现「拿不下来」。</p>
         */
        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                   Player player, BlockHitResult hit) {
            if (level.getBlockEntity(pos) instanceof XuanshuPedestalBlockEntity pedestal) {
                if (level.isClientSide) {
                    return InteractionResult.PASS;
                }
                ItemStack taken = pedestal.getItems().extractItem(
                        XuanshuPedestalBlockEntity.SLOT, 64, false);
                if (!taken.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(taken);
                    return InteractionResult.SUCCESS;
                }
            }
            return InteractionResult.PASS;
        }

        @Override
        protected void onRemove(BlockState state, Level level, BlockPos pos,
                                BlockState newState, boolean moved) {
            if (!state.is(newState.getBlock())
                    && level.getBlockEntity(pos) instanceof XuanshuPedestalBlockEntity pedestal) {
                ItemStack stack = pedestal.getItems().getStackInSlot(XuanshuPedestalBlockEntity.SLOT);
                if (!stack.isEmpty()) {
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                }
                // 座子都拆了，四砖自然该暗回去（不然再放一座新台座，四砖会「凭空已是点亮状」，
                // 而新台座自己其实是熄的 —— 两边对不上）。
                if (!level.isClientSide) {
                    pedestal.setActive(level, pos, false);
                }
            }
            super.onRemove(state, level, pos, newState, moved);
        }
    }

    /**
     * 玄枢净界石：立在哪儿，就把那一片「洗」回来。
     *
     * <p>每 {@value Purification#SWEEP_INTERVAL} tick 扫一遍，四路一起清：
     * 腐化方块（本体 {@code thaumcraft:taint} 标签里那八种）、腐化群系、
     * 附近的咒流之垢（Flux Goo / Gas），以及范围内的玩家的<b>全部扭曲</b>
     * （永久 / 粘性 / 临时）与九种腐化负面效果。详见 {@link Purification}。</p>
     *
     * <p><b>范围分两档</b>：单独立着只管<b>自己所在的那一个区块</b>；围着它砌成 3×3
     * 的「八方净界阵」——中心净界石，八方八格各一块卦位石、八种卦各不相同——范围扩成
     * <b>以本区块为中心的 3×3 共 9 个区块</b>。见
     * {@link Purification#RING_RADIUS} / {@link Purification#CHUNK_RADIUS}。</p>
     *
     * <p>用<b>计划刻</b>自续而不是方块实体：这台东西没有库存、没有界面，
     * 唯一要记的是「阵成没成」，而那个可以直接问脚下那八格（{@code active} 只是把它落成状态、
     * 顺带让客户端知道该亮多少）。计划刻是随区块存盘的，所以退出重进之后照旧在跑。</p>
     *
     * <p>必须用镐子采掘（{@code requiresCorrectToolForDrops} + 镐子方块标签）。</p>
     */
    public static final DeferredBlock<XuanshuPurifyingStoneBlock> XUANSHU_PURIFYING_STONE =
            BLOCKS.registerBlock("xuanshu_purifying_stone",
                    XuanshuPurifyingStoneBlock::new,
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.DIAMOND)
                            .strength(3.5F, 12.0F)
                            .sound(SoundType.AMETHYST)
                            .requiresCorrectToolForDrops()
                            .lightLevel(state -> state.getValue(XuanshuPurifyingStoneBlock.ACTIVE) ? 15 : 8));

    /**
     * 玄枢净界石方块：无 BE，靠计划刻每 2 秒扫一遍，顺手审计八方阵。
     *
     * <p>{@code active} 是<b>阵开没开</b>（玩家拿法杖右键点出来的），不是「有没有在干活」——
     * 开阵时更亮（8 → 15），一眼能看出这块地界管的是一个区块还是九个。写入口有三条，
     * 各管一件事：<b>开 / 关</b> = 法杖右键（{@link #useItemOn}）；<b>只写「关」</b> =
     * 计划刻 {@link #audit}（阵散了自动熄火，只判 {@code active && !arrayComplete}）；
     * <b>阵心被挖</b> = {@link #onRemove}。它不从存档读回，每轮都是算出来的，
     * 所以不存在「存档说成阵、脚下其实缺两格」的对不上。</p>
     *
     * <p>开阵不止阵心自己变：{@link #syncRing} 把外八格卦位石一齐点亮——九块石同时换形
     * （阵心化作「石台 + 神圣晶柱」的祭坛核心，晶柱高出方块一格半；外八格化作同配色的
     * 坛前构件：石台 + 悬浮尖晶），各发各的光，3×3 整座阵是一个整体。</p>
     *
     * <p>★ 第七十批：<b>未激活态也不再是一块立方体</b>（用户：「净界石也改为和古代有关的建模
     * 不要一个方块」）——改成一座<b>古代石坛</b>：台阶式基座 + 四角立柱 + 刻了符文的石鼓
     * （上下各一道细金箍）+ 双层顶冠 + 鎏金顶珠，顶面取「净化之井」的符文圆印。
     * 与激活态那座「石台 + 晶柱」同一套青石 + 鎏金语言，所以开阵前后是**同一件器物的两个状态**。</p>
     *
     * <p>★ 第七十二批：石坛**加细**（用户：「净界石在细节一点 多点细节 相信你」）——
     * element 由 12 件增到 **26 件**：台基改**三层须弥座**；四角望柱加**柱头（斗）**；
     * 石鼓上下各加一道**叠涩线脚**；鼓的四个面各嵌一块**浅浮雕壁龛**
     * （咬进鼓身 0.20、凸出 0.14，两块实体不共面 ⇒ 不 z-fighting）；
     * 顶冠之上加**檐口**与**宝顶座**，冠由两收改**三收**。
     * 贴图同时改成「清楚的横竖刻线 + 两道竖符 + 鼓面两幅卦」的读法
     * （原来的高频撒点进游戏是一团噪点）。见 {@code models/block/xuanshu_purifying_stone.json}
     * 与 {@code tools/gen_purifying_stone.py}。</p>
     */
    public static class XuanshuPurifyingStoneBlock extends Block {

        /** 八方净界阵成没成（八格凑齐八种卦位石）。 */
        public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

        /**
         * 成阵态的碰撞形状：只认基座台（0~4），台上悬浮的能量晶不挡人。
         *
         * <p>★ 第七十五批还原。第七十四批「净界石改回单方块」时，这段形状连同成阵模型一起被
         * 删掉了；用户看着实机点名「净界成阵不要改，变回原来的」—— 成阵模型（石台 + 三收冠 +
         * 悬浮能量晶）与这段形状**一并还原**：模型见
         * {@code models/block/xuanshu_purifying_stone_active.json}，晶柱顶端出格到 y24，
         * 那是整座阵的视觉焦点，不挡路也不挡光。</p>
         */
        private static final VoxelShape ACTIVE_COLLISION =
                Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D);

        // 未激活态（第七十四批改成纯单方块）走 Block 默认的 Shapes.block()：整块可站、可挡，
        // 不需要覆写。所以下面两个方法只在成阵态换形状。

        public XuanshuPurifyingStoneBlock(Properties properties) {
            super(properties);
            // 显式登记默认态：阵默认不激活，只能由玩家手持法杖右键立起来（useItemOn）。
            // 不写这一句的话默认态退化成 stateDefinition.any()（按属性声明序取首个值），
            // 行为恰好也是 false，但那是隐式的——布尔属性哪天调整声明顺序就会悄悄翻车。
            registerDefaultState(this.stateDefinition.any().setValue(ACTIVE, false));
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(ACTIVE);
        }

        /**
         * 碰撞 / 遮挡形状随 {@link #ACTIVE} 换：成阵态只认基座台（{@link #ACTIVE_COLLISION}），
         * 未激活态是单方块（整块）。选取形状保持整块（法杖右键点在环上也要能开关阵）。
         */
        @Override
        protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                               CollisionContext context) {
            return state.getValue(ACTIVE) ? ACTIVE_COLLISION : Shapes.block();
        }

        @Override
        protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
            return state.getValue(ACTIVE) ? ACTIVE_COLLISION : Shapes.block();
        }

        /**
         * 阵心被挖走：外八格一齐熄火，别把一圈点亮的石头留在世上没人管。
         *
         * <p>只在外八格确实亮着时动手（{@code ACTIVE} 为真才可能亮着）。
         * 不发提示——阵心都没了，「阵收了」说给谁听；石头自己暗下去就是提示。</p>
         */
        @Override
        protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
            if (!state.is(newState.getBlock()) && !level.isClientSide && state.getValue(ACTIVE)) {
                syncRing((ServerLevel) level, pos, false);
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }

        @Override
        protected void onPlace(BlockState state, Level level, BlockPos pos,
                               BlockState oldState, boolean moved) {
            super.onPlace(state, level, pos, oldState, moved);
            if (level instanceof ServerLevel server && !oldState.is(this)) {
                // 先把这一片扫干净，别让玩家等满一个周期：排一颗 1 tick 后的刻，
                // 扫描（以及在扫描前的那次阵势审计）都在计划刻里做，
                // 免得在 onPlace 里改自己的状态（重入一次，白添麻烦）。
                server.scheduleTick(pos, this, 1);
            }
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                                @Nullable LivingEntity placer, ItemStack stack) {
            super.setPlacedBy(level, pos, state, placer, stack);
            if (level instanceof ServerLevel server && placer instanceof Player player) {
                Purification.onPlaced(server, pos, player);
            }
        }

        /**
         * 法杖右键：开阵 / 收阵。
         *
         * <p>「阵摆齐了」和「阵开起来了」是两件事：外八格凑齐八种卦位石只说明<b>摆好了</b>，
         * 还得拿法杖点一下中心那块才算立起来。这么分是因为净界石一开阵就要每两秒扫过九个区块，
         * 该由玩家决定它什么时候开始转——而不是放下去就自己动起来。</p>
         *
         * <p>判据用 {@code instanceof WandCarrierItem}（本体 API 里的法杖基类，
         * {@code WandItem} 与 {@code CustomWandItem} 都继承它），与玄枢台座「开坛」同一口径；
         * <b>不调 {@code WandApi.view}</b>——后者要读堆叠上的法杖数据，两边未必拿到一致结论。</p>
         */
        @Override
        protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                                  BlockPos pos, Player player, InteractionHand hand,
                                                  BlockHitResult hit) {
            if (!(stack.getItem() instanceof WandCarrierItem)) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (level instanceof ServerLevel server) {
                if (state.getValue(ACTIVE)) {
                    server.setBlock(pos, state.setValue(ACTIVE, false), Block.UPDATE_ALL);
                    syncRing(server, pos, false);
                    Purification.announceArrayChange(server, pos, false);
                } else if (Purification.arrayComplete(server, pos)) {
                    server.setBlock(pos, state.setValue(ACTIVE, true), Block.UPDATE_ALL);
                    syncRing(server, pos, true);
                    Purification.announceArrayChange(server, pos, true);
                } else {
                    player.displayClientMessage(Component.translatable(
                            "message.skydeitymysticpivot.purifying_stone.array_incomplete"), true);
                    server.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH,
                            SoundSource.BLOCKS, 0.5F, 1.2F);
                }
                // ★ setBlock 会把方块刻清掉：补一颗，否则这一台就永远停摆了。
                server.scheduleTick(pos, this, Purification.SWEEP_INTERVAL);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        @Override
        protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
            audit(level, pos, state);
            if (Purification.sweep(level, pos)) {
                // 只有真的清掉了东西才冒光——「它在干活」这件事得看得见
                Purification.ambient(level, pos, random);
            }
            level.scheduleTick(pos, this, Purification.SWEEP_INTERVAL);
        }

        /**
         * 审计八方阵：阵<b>散了</b>就熄火，阵还立着就把外八格对齐一遍。
         *
         * <p>开阵不在这里——开阵是玩家拿法杖右键点出来的（见 {@link #useItemOn}）。
         * 这条计划刻只负责两件事：① 阵还立着的时候别让人不小心挖掉一块卦位石，
         * 机器却还在按九个区块扫；② 把外八格的状态「自愈」回一致——点亮那一下是
         * 右键时写下去的，万一哪一回被错过（区块边界、加载顺序），两秒之内必被拉回，
         * 不会出现「阵心亮着、外圈全黑」这种对不上。</p>
         */
        private static void audit(ServerLevel level, BlockPos pos, BlockState state) {
            if (!state.getValue(ACTIVE)) {
                return;
            }
            if (!Purification.arrayComplete(level, pos)) {
                level.setBlock(pos, state.setValue(ACTIVE, false), Block.UPDATE_ALL);
                syncRing(level, pos, false);
                Purification.announceArrayChange(level, pos, false);
                return;
            }
            syncRing(level, pos, true);
        }

        /**
         * 收阵：状态写「关」、外八格熄火、补一条提示。
         *
         * <p>给卦位石被挖时用（{@code BaguaStoneBlock#onRemove}）：那一瞬间阵就散了，
         * 玩家该立刻看到全暗，而不是等最多两秒的计划刻。★ 写状态之后必须补一颗
         * {@code scheduleTick}——{@code setBlock} 会把方块刻清掉，不补这一台就永远停摆了
         * （与 {@link #useItemOn} 里同一处注意）。</p>
         */
        public static void deactivate(ServerLevel level, BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof XuanshuPurifyingStoneBlock) || !state.getValue(ACTIVE)) {
                return;
            }
            level.setBlock(pos, state.setValue(ACTIVE, false), Block.UPDATE_ALL);
            syncRing(level, pos, false);
            Purification.announceArrayChange(level, pos, false);
            level.scheduleTick(pos, state.getBlock(), Purification.SWEEP_INTERVAL);
        }

        /**
         * 把外八格卦位石的 {@code active} 对齐到 {@code active}——只写不一样的。
         *
         * <p>这是卦位石 {@code active} 的<b>唯一写入口</b>：开阵、收阵、阵散、阵心被挖，
         * 四条路全汇到这里。写之前先读一遍状态、相同就不落笔：计划刻每 2 秒会调一次
         * （{@link #audit} 的自愈），九块一起无脑写会把邻居更新和光照重算放大成
         * 每 2 秒一次的卡顿。</p>
         *
         * <p>点亮那一下给每块石撒一圈灵光：八块依次亮起，比「唰地全亮」更像<b>阵</b>在合拢。</p>
         */
        private static void syncRing(ServerLevel level, BlockPos center, boolean active) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;                       // 中心是净界石自己
                    }
                    BlockPos p = center.offset(dx, 0, dz);
                    BlockState state = level.getBlockState(p);
                    if (!(state.getBlock() instanceof BaguaStoneBlock)
                            || state.getValue(BaguaStoneBlock.ACTIVE) == active) {
                        continue;
                    }
                    level.setBlock(p, state.setValue(BaguaStoneBlock.ACTIVE, active), Block.UPDATE_ALL);
                    if (active) {
                        level.sendParticles(ParticleTypes.END_ROD,
                                p.getX() + 0.5D, p.getY() + 1.0D, p.getZ() + 0.5D,
                                10, 0.4D, 0.3D, 0.4D, 0.02D);
                    }
                }
            }
        }
    }

    private ModBlocks() {
    }
}
