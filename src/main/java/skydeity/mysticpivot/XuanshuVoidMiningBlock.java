package skydeity.mysticpivot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 玄枢虚空采矿方块（第一百七十四批新增；第一百八十六批推倒重来；第一百九十二批定形制）。
 *
 * <h2>第一百九十二批：形制收进代码，光效改成一柱一雾</h2>
 * <ul>
 *   <li><b>形制</b>：多方块从「运行期读 {@code structure.schem}」改成编译进
 *       {@link VoidAltarBlueprint} 的常量（255 格 / 8×8 / 高 13 格 / 9 种方块）。
 *       用户口径：「纯代码不但不更占，反而更省一点，那就改成纯代码」；</li>
 *   <li><b>光效</b>：塔心一根长竖直光柱、四角四根细竖光、塔内一团环形雾
 *       （{@link XuanshuVoidMiningRenderer}）。用户口径：「是集结部队这种长一点竖直的，
 *       下面稍微高一点的位子一个雾状的圆」；</li>
 *   <li>仍然<b>不撒任何粒子</b>（第一百九十一批定的：不要粒子，要画出来的特效）。</li>
 * </ul>
 *
 * <p>其余照旧：</p>
 * <ul>
 *   <li>多一个 {@link #FACING}（放置时正对玩家），塔立在它的<b>背面</b>；</li>
 *   <li>结构一齐就自动激活（{@link #FORMED}），每 5 秒产出一组矿；缺一格就熄灭停转；</li>
 *   <li>结构不用手动摆：空手右键本方块开界面，界面里两个按钮：<b>一键搭建</b> /
 *       <b>一键收回</b>（材料走背包，见 {@link VoidMiningStructure#build}）。</li>
 * </ul>
 *
 * <p>塔的尺寸、材料与判定全在 {@link VoidMiningStructure}，本类只管
 * 「激活状态怎么变」与「右键开界面」。</p>
 */
public class XuanshuVoidMiningBlock extends Block implements EntityBlock {

    /** 星枢光坛是否齐全（= 机器是否在运转）。 */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    /** 正面朝哪；光坛长在背面（见 {@link VoidMiningStructure}）。 */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public XuanshuVoidMiningBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FORMED, Boolean.FALSE)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED, FACING);
    }

    /** 放置时正面朝向玩家：于是「背面」就是玩家看过去的那一侧，结构往远处立。 */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new XuanshuVoidMiningBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (type != ModBlockEntities.XUANSHU_VOID_MINING.get()) {
            return null;
        }
        // 客户端不再需要 tick：第一百九十一批把三种粒子全删了，光效改由
        // XuanshuVoidMiningRenderer 每帧画（它自己读 level.getGameTime() 做动画）。
        if (level.isClientSide) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<XuanshuVoidMiningBlockEntity>)
                (lvl, p, st, be) -> XuanshuVoidMiningBlockEntity.serverTick(lvl, p, st, be);
    }

    /**
     * 空手右键：开机器界面。
     *
     * <p>第一百八十六批起<b>不分成型与否</b>——没成型时界面正是「一键搭建」的入口，
     * 拦着不让开反而没地方按。</p>
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide && level instanceof ServerLevel server
                && server.getBlockEntity(pos) instanceof XuanshuVoidMiningBlockEntity machine) {
            player.openMenu(machine);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * 结构自检：把 {@link #FORMED} 刷成「光坛齐不齐」，并在<b>状态翻转</b>那一刻放音效。
     *
     * <p>由方块实体每秒调一次。只在真的变了才 {@code setBlock}，不然每秒一次方块更新
     * 会把周围的红石与光照刷得吱吱响。</p>
     */
    public static boolean refreshFormed(ServerLevel server, BlockPos pos) {
        BlockState state = server.getBlockState(pos);
        if (!(state.getBlock() instanceof XuanshuVoidMiningBlock)) {
            return false;
        }
        boolean formed = VoidMiningStructure.isComplete(server, pos);
        if (state.getValue(FORMED) != formed) {
            server.setBlock(pos, state.setValue(FORMED, formed), Block.UPDATE_ALL);
            if (formed) {
                server.playSound(null, pos, SoundEvents.BEACON_ACTIVATE,
                        SoundSource.BLOCKS, 0.7F, 1.5F);
                broadcast(server, pos, "message.skydeitymysticpivot.void_mining.formed");
            } else {
                server.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE,
                        SoundSource.BLOCKS, 0.6F, 1.2F);
                broadcast(server, pos, "message.skydeitymysticpivot.void_mining.stalled");
            }
        }
        return formed;
    }

    // ★ 第一百九十一批把原来的 ringBurst（激活时沿弧炸 END_ROD 星火）整个删掉了：
    //   用户要「不要粒子，就是画出来的特效」。光效现在全在 XuanshuVoidMiningRenderer
    //   里每帧画，与「激活」这个事件无关（FORMED 为真就一直亮）。这里只留音效与提示。

    /** 只给 12 格内的玩家发一条动作栏提示（结构翻转不是天天发生，不用广播全世界）。 */
    private static void broadcast(ServerLevel server, BlockPos pos, String key) {
        Component message = Component.translatable(key);
        for (Player nearby : server.players()) {
            if (nearby.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 144.0) {
                nearby.displayClientMessage(message, true);
            }
        }
    }

    /** 挖掉机器：产出照常散出来（光坛是玩家自己的建筑，不跟着塌）。 */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && level instanceof ServerLevel
                && !(newState.getBlock() instanceof XuanshuVoidMiningBlock)
                && level.getBlockEntity(pos) instanceof XuanshuVoidMiningBlockEntity machine) {
            machine.dropContents();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
