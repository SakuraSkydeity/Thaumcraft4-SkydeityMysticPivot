package skydeity.mysticpivot;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 玄枢虚空水晶塔的多方块：<b>那座塔</b>（8×8 · 高 13 · 255 格 · 9 种方块）。
 *
 * <p>形制在 {@link VoidAltarBlueprint} —— 由 {@code structure.schem} 编译成的分层字符画。
 * 塔不随朝向转：建筑朝哪边盖的，机器就朝哪边摆。判定与施工在 {@link StructureDeployer}，
 * 与传送门共用同一套。</p>
 */
public final class VoidMiningStructure {

    /** 提示文案前缀。 */
    public static final String MESSAGES = "message.skydeitymysticpivot.void_mining";

    /** 整套结构件（只读，顺序 = 从下往上；拆的时候反着遍历就是从顶往下）。 */
    private static final List<StructureDeployer.Part> PARTS = VoidAltarBlueprint.parts();

    /** 水平包围盒 {@code {minX, minZ, maxX, maxZ}}（相对机器，格边界口径）。 */
    private static final int[] BOUNDS = {
            VoidAltarBlueprint.minX(), VoidAltarBlueprint.minZ(),
            VoidAltarBlueprint.maxX() + 1, VoidAltarBlueprint.maxZ() + 1,
    };

    private VoidMiningStructure() {
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /** 结构件（相对机器，只读）。 */
    public static List<StructureDeployer.Part> parts() {
        return PARTS;
    }

    /** 结构总高（格）：悬浮字、光柱顶端都按它定位。 */
    public static int height() {
        return VoidAltarBlueprint.height();
    }

    /** 水平包围盒 {@code {minX, minZ, maxX, maxZ}}（格边界口径）。 */
    public static int[] bounds() {
        return BOUNDS;
    }

    /** 搭一座要哪些材料。 */
    public static Map<Item, Integer> materials() {
        return VoidAltarBlueprint.materials();
    }

    /**
     * 中轴的世界坐标（{x, z}，格边界口径）—— 竖光与悬浮字都钉在这条线上。
     *
     * <p>塔是 8 格宽，中轴落在<b>格与格之间</b>：8×8 的建筑没有「正中那一格」，只有正中那条线。</p>
     */
    public static double[] axis(BlockPos machine) {
        return new double[]{
                machine.getX() + VoidAltarBlueprint.axisX(),
                machine.getZ() + VoidAltarBlueprint.axisZ(),
        };
    }

    /**
     * 竖光的落点：<b>塔顶那一圈玻璃里、四个侧面各一块</b>的上表面中心（相对机器，{@code y} 已 +1）。
     *
     * <p>规则是「从低往高，第一个恰好有四块露顶玻璃的层」—— 不写死坐标，换形制也跟着挪；
     * 一层凑不出四块时返回空表，渲染器退回「中轴一根」。</p>
     */
    public static List<double[]> beamAnchors() {
        Set<BlockPos> filled = new HashSet<>();
        for (StructureDeployer.Part part : PARTS) {
            filled.add(part.offset());
        }
        Map<Integer, List<StructureDeployer.Part>> byLayer = new TreeMap<>();
        for (StructureDeployer.Part part : PARTS) {
            if (part.state().is(Blocks.LIGHT_BLUE_STAINED_GLASS)
                    && !filled.contains(part.offset().above())) {      // 上面不压东西，顶面才露着
                byLayer.computeIfAbsent(part.offset().getY(), key -> new ArrayList<>()).add(part);
            }
        }
        for (List<StructureDeployer.Part> layer : byLayer.values()) {
            if (layer.size() != 4) {
                continue;
            }
            List<double[]> out = new ArrayList<>(4);
            for (StructureDeployer.Part part : layer) {
                BlockPos off = part.offset();
                out.add(new double[]{off.getX() + 0.5D, off.getY() + 1.0D, off.getZ() + 0.5D});
            }
            return out;
        }
        return List.of();
    }

    // ------------------------------------------------------------------
    // 判定 / 施工（流程在 StructureDeployer）
    // ------------------------------------------------------------------

    public static boolean isComplete(Level level, BlockPos machine) {
        return StructureDeployer.isComplete(level, machine, PARTS);
    }

    /** 还差几格（界面 / 提示用）。 */
    public static int missing(Level level, BlockPos machine) {
        return PARTS.size() - StructureDeployer.filled(level, machine, PARTS);
    }

    public static boolean build(ServerLevel level, BlockPos machine, Player player) {
        return StructureDeployer.build(level, machine, PARTS, MESSAGES, player);
    }

    public static int dismantle(ServerLevel level, BlockPos machine, Player player) {
        return StructureDeployer.dismantle(level, machine, PARTS, MESSAGES, player);
    }
}
