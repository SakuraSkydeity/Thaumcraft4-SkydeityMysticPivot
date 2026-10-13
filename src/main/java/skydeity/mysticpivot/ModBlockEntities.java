package skydeity.mysticpivot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import skydeity.mysticpivot.mixin.InfusionMatrixBlockEntityAccessor;

/**
 * 方块实体注册。
 *
 * <h2>玄枢魔晶的安全场</h2>
 * <p>玄枢魔晶的核心效果：只要有一台放在注魔矩阵的扫描域内（水平 ±12 格、竖直 -10~+5 格，
 * 与本体注魔矩阵自身的稳定器扫描范围一致），矩阵的不稳定度就每 5 tick 被清零一次——
 * 本体的不稳定事件（弹射物品、电击、伤害等）只在 {@code instability > 0} 时掷骰，
 * 归零即绝不触发，注魔稳如泰山。</p>
 *
 * <p>实现走 {@link InfusionMatrixBlockEntityAccessor}（本项目唯一进入本体内部的窄口，
 * 理由见该类注释）；读取和写入都只在值非零时发生，稳态下每 5 tick 只做一轮只读扫描。</p>
 */
public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BE_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MysticPivot.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<XuanshuCrystalBlockEntity>> XUANSHU_CRYSTAL =
            BE_TYPES.register("xuanshu_crystal",
                    () -> BlockEntityType.Builder.of(XuanshuCrystalBlockEntity::new,
                            ModBlocks.XUANSHU_CRYSTAL_CLUSTER.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<XuanshuInfernalFurnaceBlockEntity>> XUANSHU_INFERNAL_FURNACE =
            BE_TYPES.register("xuanshu_infernal_furnace",
                    () -> BlockEntityType.Builder.of(XuanshuInfernalFurnaceBlockEntity::new,
                            ModBlocks.XUANSHU_INFERNAL_FURNACE.get()).build(null));

    /** 玄枢台座：存储台面物品并承担卦象转化的周期推进。 */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<XuanshuPedestalBlockEntity>> XUANSHU_PEDESTAL =
            BE_TYPES.register("xuanshu_pedestal",
                    () -> BlockEntityType.Builder.of(XuanshuPedestalBlockEntity::new,
                            ModBlocks.XUANSHU_PEDESTAL.get()).build(null));

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

    private ModBlockEntities() {
    }
}
