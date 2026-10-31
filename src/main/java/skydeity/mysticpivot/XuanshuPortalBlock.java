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
 * 玄枢传送门方块（第一百九十七批新增；<b>第一百九十八批从「纯装饰」变成一台机器</b>）。
 *
 * <h2>第一百九十八批：先有壳，再有内容</h2>
 * <p>用户口径：「<b>给传送门添加和矿机一样的 ui（只添加 ui 不设计功能，先把建筑搞定）</b>」。
 * 所以这一批做的只有两件事：</p>
 * <ul>
 *   <li><b>界面</b>：与玄枢虚空水晶塔同一套版式（12 格栏 + 竖排进度条 + 一键搭建 / 一键收回 +
 *       激活点），见 {@link XuanshuPortalScreen}；</li>
 *   <li><b>建筑</b>：一键搭建 / 收回是真的 —— 形制由 {@link PortalGateStructure} 给
 *       （第 204 批：23×25 八角台基 + 折跃门，1099 格 / 高 18 格，由 schem 编译成字符画），
 *       生成在方块<b>背后</b>。</li>
 * </ul>
 * <p><b>传送本身还没做</b>：不放传送门、不消耗、不产东西，12 格栏眼下只是个能放东西的格子。</p>
 *
 * <h2>朝向</h2>
 * <p>与塔一致：<b>放置时正面朝玩家</b>（{@link #FACING} = 玩家朝向的反向），
 * 整座广场就长在背面 —— 玩家站在门前，门在身后展开。</p>
 *
 * <h2>激活</h2>
 * <p>结构齐了 {@link #FORMED} 变真：亮度 7 → 12，放一次「已激活」音效与提示；
 * 拆掉任意一格就熄回去。一秒自检一次（由方块实体调 {@link #refreshFormed}）。</p>
 */
public class XuanshuPortalBlock extends Block implements EntityBlock {

    /** 背后那座广场齐不齐（= 门是否已激活）。 */
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");

    /** 正面朝哪；广场长在背面（见 {@link PortalGateStructure}）。 */
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public XuanshuPortalBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FORMED, Boolean.FALSE)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED, FACING);
    }

    /** 放置时正面朝向玩家：于是「背面」就是玩家看过去的那一侧，广场往远处铺开。 */
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
        return new XuanshuPortalBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (type != ModBlockEntities.XUANSHU_PORTAL.get()) {
            return null;
        }
        // 客户端不需要 tick：门的「光效」是贴图里画好的能量核，没有需要每帧算的东西
        if (level.isClientSide) {
            return null;
        }
        return (BlockEntityTicker<T>) (BlockEntityTicker<XuanshuPortalBlockEntity>)
                (lvl, p, st, be) -> XuanshuPortalBlockEntity.serverTick(lvl, p, st, be);
    }

    /**
     * 空手右键：开界面。
     *
     * <p>不分成型与否 —— 没成型时界面正是「一键搭建」的入口。</p>
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!level.isClientSide && level instanceof ServerLevel server
                && server.getBlockEntity(pos) instanceof XuanshuPortalBlockEntity gate) {
            player.openMenu(gate);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * 结构自检：把 {@link #FORMED} 刷成「广场齐不齐」，并在<b>状态翻转</b>那一刻放音效。
     *
     * <p>只在真的变了才 {@code setBlock}，不然每秒一次方块更新会把周围的红石与光照刷得吱吱响。</p>
     */
    public static boolean refreshFormed(ServerLevel server, BlockPos pos) {
        BlockState state = server.getBlockState(pos);
        if (!(state.getBlock() instanceof XuanshuPortalBlock)) {
            return false;
        }
        Direction facing = state.getValue(FACING);
        boolean formed = PortalGateStructure.isComplete(server, pos, facing);
        if (state.getValue(FORMED) != formed) {
            server.setBlock(pos, state.setValue(FORMED, formed), Block.UPDATE_ALL);
            if (formed) {
                server.playSound(null, pos, SoundEvents.BEACON_ACTIVATE,
                        SoundSource.BLOCKS, 0.7F, 1.5F);
                broadcast(server, pos, "message.skydeitymysticpivot.portal.formed");
            } else {
                server.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE,
                        SoundSource.BLOCKS, 0.6F, 1.2F);
                broadcast(server, pos, "message.skydeitymysticpivot.portal.stalled");
            }
        }
        return formed;
    }

    /** 只给 12 格内的玩家发一条动作栏提示（结构翻转不是天天发生，不用广播全世界）。 */
    private static void broadcast(ServerLevel server, BlockPos pos, String key) {
        Component message = Component.translatable(key);
        for (Player nearby : server.players()) {
            if (nearby.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 144.0) {
                nearby.displayClientMessage(message, true);
            }
        }
    }

    /** 挖掉方块：12 格栏里的东西照常散出来（广场是玩家自己的建筑，不跟着塌）。 */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!level.isClientSide && !(newState.getBlock() instanceof XuanshuPortalBlock)
                && level.getBlockEntity(pos) instanceof XuanshuPortalBlockEntity gate) {
            gate.dropContents();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
