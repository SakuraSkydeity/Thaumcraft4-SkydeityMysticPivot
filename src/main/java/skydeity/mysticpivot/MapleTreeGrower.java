package skydeity.mysticpivot;

import java.util.OptionalInt;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FancyFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.trunkplacers.FancyTrunkPlacer;

/**
 * 枫树的生长器（第一百六十七批）。
 *
 * <h2>为什么在 Java 里现构 ConfiguredFeature，而不是走数据包注册表</h2>
 * <p>原版「大型橡树」= {@code minecraft:fancy_oak}，形制是三件套：
 * {@link FancyTrunkPlacer}（高主干 + 四向侧枝，侧枝的 {@code AXIS} 由
 * {@code makeLimb} 按枝条朝向自动摆正）+ {@link FancyFoliagePlacer}（枝头椭圆冠）
 * + {@link TwoLayersFeatureSize}（冠径包络）。用户要的「类似原版大型的橡树」
 * 就是这套参数，只把木/叶换成本 mod 的枫木与枫叶。</p>
 *
 * <p>{@code TreeGrower}（原版树苗持有的那个类）只能持 {@code ResourceKey}，
 * 逼着把 configured feature 注册进动态注册表——为了三棵树引一套世界生成注册表，
 * 还要操心快照同步与 datapack 覆盖，不值。所以这里<b>每次生长现构一个
 * {@link ConfiguredFeature} 直接 {@code place}</b>：{@code TreeFeature.place}
 * 只依赖配置对象本身，不查注册表；世界生成那条路则照常用数据包 JSON
 * （{@code data/skydeitymysticpivot/worldgen/...}，同一套参数）。</p>
 *
 * <h2>参数与原版 fancy_oak 逐项对齐（树干固定最大高度）</h2>
 * <p>原版 fancy_oak 的树干高度 = {@code base_height(3) + rand(0..height_rand_a(11))
 * + rand(0..height_rand_b(0))}，即 3~14 格随机。用户要「只保留最大的那款」，所以把
 * {@code base_height} 提到 14、两个随机项归零，树干固定 14 格（整树含冠约 18 格，
 * 正是「大型珍异橡树」的最大形态）。其余参数仍与原版逐项同参。</p>
 * <pre>
 * 原版 fancy_oak（TreeFeatures.createFancyOak）          枫树（固定最大）
 *   FancyTrunkPlacer(3, 11, 0)                          ← 改 FancyTrunkPlacer(14, 0, 0)
 *   FancyFoliagePlacer(2, 4, 4)                         ← 同参
 *   TwoLayersFeatureSize(0, 0, 0, OptionalInt.of(4))    ← 同参
 *   ignoreVines()                                       ← 同参
 * </pre>
 *
 * <p>树叶状态用 {@link ModBlocks#MAPLE_LEAVES} 的默认态（{@code distance=7,
 * persistent=false, waterlogged=false}）——与原版橡叶完全同规：放下去时先按
 * 「未计算距离」落位，随后随机 tick 由 {@code LeavesBlock#updateDistance} 按
 * 周围原木重算，离木超过 7 格且未标记持久的叶子照原版规矩枯萎。</p>
 *
 * @see MapleSaplingBlock
 */
public final class MapleTreeGrower {

    private MapleTreeGrower() {
    }

    /**
     * 在 {@code pos} 处长出一棵枫树（大型橡树形制）。
     *
     * @param level  服务端世界
     * @param pos    树苗所在格（木桩从这一格长出）
     * @param random 随机源（同一粒种子下树形确定，与玩家 Bone Meal 的随机性解耦）
     * @return 是否放得下（空间不够时原样返回 false，由调用方把树苗放回去）
     */
    public static boolean growMaple(ServerLevel level, BlockPos pos, RandomSource random) {
        TreeConfiguration config = new TreeConfiguration.TreeConfigurationBuilder(
                BlockStateProvider.simple(ModBlocks.MAPLE_LOG.get()),
                new FancyTrunkPlacer(14, 0, 0),
                BlockStateProvider.simple(ModBlocks.MAPLE_LEAVES.get()),
                new FancyFoliagePlacer(ConstantInt.of(2), ConstantInt.of(4), 4),
                new TwoLayersFeatureSize(0, 0, 0, OptionalInt.of(4)))
                .ignoreVines()
                .build();
        ConfiguredFeature<TreeConfiguration, ?> feature = new ConfiguredFeature<>(Feature.TREE, config);
        return feature.place(level, level.getChunkSource().getGenerator(), random, pos);
    }
}
