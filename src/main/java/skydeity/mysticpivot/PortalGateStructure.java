package skydeity.mysticpivot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 玄枢传送门的多方块：<b>那座折跃门广场</b>（23×25 · 高 18 · 1099 格）。
 *
 * <p>形制在 {@link PortalGateBlueprint} —— 由 {@code chuansongmen.schem} 编译成的分层字符画。
 * 这里只管「怎么摆」：局部坐标往前对齐（{@link #FRONT_SHIFT}）、整体沉一格（{@link #Y_SHIFT}），
 * 再按朝向绕 Y 转（局部 −Z 对准方块正面，建筑自然长到背后）。四个朝向各缓存一份。</p>
 *
 * <p>判定与施工都在 {@link StructureDeployer}，与水晶塔共用同一套。</p>
 */
public final class PortalGateStructure {

    /** 提示文案前缀。 */
    public static final String MESSAGES = "message.skydeitymysticpivot.portal";

    /** 局部 z 补正：建筑最前排落在方块自己那一列（深度 0，方块像压在广场前沿的钥石）。 */
    public static final int FRONT_SHIFT = -PortalGateBlueprint.minZ();

    /** 整体比方块低一格（用户口径「往下一格」），方块与广场不抢同一格。 */
    public static final int Y_SHIFT = -1;

    /** 四个朝向各一份摆好的形制。 */
    private static final Map<Direction, List<StructureDeployer.Part>> LAYOUTS =
            new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            LAYOUTS.put(facing, layout(facing));
        }
    }

    private PortalGateStructure() {
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /** 第 {@code facing} 朝向下的一整套偏移（只读，顺序 = 从下往上）。 */
    public static List<StructureDeployer.Part> parts(Direction facing) {
        return LAYOUTS.get(normalize(facing));
    }

    /** 结构件总数（1099）：界面上的「完成度」分母。 */
    public static int total() {
        return PortalGateBlueprint.parts().size();
    }

    /** 搭一座要哪些材料。 */
    public static Map<Item, Integer> materials() {
        return PortalGateBlueprint.materials();
    }

    /** 结构是否齐全（任缺一格即 false）。 */
    public static boolean isComplete(Level level, BlockPos gate, Direction facing) {
        return StructureDeployer.isComplete(level, gate, parts(facing));
    }

    /** 已经立起来几格（界面进度条分子）。 */
    public static int filled(Level level, BlockPos gate, Direction facing) {
        return StructureDeployer.filled(level, gate, parts(facing));
    }

    // ------------------------------------------------------------------
    // 一键搭建 / 收回
    // ------------------------------------------------------------------

    public static boolean build(ServerLevel level, BlockPos gate, Direction facing, Player player) {
        return StructureDeployer.build(level, gate, parts(facing), MESSAGES, player);
    }

    public static int dismantle(ServerLevel level, BlockPos gate, Direction facing, Player player) {
        return StructureDeployer.dismantle(level, gate, parts(facing), MESSAGES, player);
    }

    // ------------------------------------------------------------------
    // 摆放
    // ------------------------------------------------------------------

    private static List<StructureDeployer.Part> layout(Direction facing) {
        List<StructureDeployer.Part> local = PortalGateBlueprint.parts();
        List<StructureDeployer.Part> out = new ArrayList<>(local.size());
        for (StructureDeployer.Part part : local) {
            out.add(new StructureDeployer.Part(rotate(part.offset(), facing), part.state()));
        }
        return Collections.unmodifiableList(out);
    }

    /**
     * 局部坐标 → 相对方块的偏移：先补 {@link #FRONT_SHIFT} 推到方块背后、补 {@link #Y_SHIFT}
     * 沉一格，再绕 Y 转。四个朝向就是「局部 −Z 对准 {@code facing}」那组旋转：
     * 北 {@code (x, z)}、东 {@code (−z, x)}、南 {@code (−x, −z)}、西 {@code (z, −x)}。
     */
    private static BlockPos rotate(BlockPos local, Direction facing) {
        int x = local.getX();
        int y = local.getY() + Y_SHIFT;
        int z = local.getZ() + FRONT_SHIFT;
        return switch (facing) {
            case EAST -> new BlockPos(-z, y, x);
            case SOUTH -> new BlockPos(-x, y, -z);
            case WEST -> new BlockPos(z, y, -x);
            default -> new BlockPos(x, y, z);
        };
    }

    /** 只认水平四向；拿到竖直方向（不该发生）就按北处理，不抛异常。 */
    private static Direction normalize(Direction facing) {
        return facing == null || facing.getAxis().isVertical() ? Direction.NORTH : facing;
    }
}
