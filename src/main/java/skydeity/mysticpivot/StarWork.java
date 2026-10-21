package skydeity.mysticpivot;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

/**
 * 星象的读取入口（第一百一十七批）——<b>「工作方块」要接的口就在这一个类</b>。
 *
 * <h2>谁读、怎么读</h2>
 * <p>阵是 3×4（十二个星位），外面还得有一圈才能摆工作方块。外圈就是 3×4 的
 * <b>5×6 外沿</b>（长宽各加 1，共 18 格）——见 {@link #isRing}。
 * 工作方块（玄枢坩埚，以后的另外两台）放在外圈任意一格，就能这样拿到星象：</p>
 *
 * <pre>{@code
 * BlockPos origin = StarWork.boardOrigin(level, this.worldPosition);
 * Constellation constellation = origin == null ? Constellation.EMPTY : StarWork.at(level, origin);
 * }</pre>
 * <p>现成的一步到位封装见 {@link StarRite#doubled}（斗口四星供玄枢锭 → 翻倍）。</p>
 *
 * <p>读法是「反查」：从工作方块向四周找那座阵的阵心（{@code role=r1c1} 的那一格），
 * 再逐格看十二格里有没有东西——<b>有物品 = 那颗星亮着</b>。</p>
 *
 * <h2>为什么不做成事件</h2>
 * <p>阵与工作方块是两个独立的方块，没有主从关系：玩家先摆阵、后放锅，或者反过来，
 * 都可能。与其在二十来处方块里互相通知，不如让<b>需要星象的那一方</b>按自己的节奏问
 * （推荐每 20 tick 问一次，别每 tick 都扫）。这里只是纯函数，没有缓存，问多少次都不会错。</p>
 */
public final class StarWork {

    private StarWork() {
    }

    /**
     * 外圈坐标（相对阵心）：3×4 的阵占 {@code x∈[-1,1], z∈[-1,2]}，
     * 外圈就是 {@code 5×6} 去掉这十二格、剩下的十八格。
     */
    public static boolean isRing(int dx, int dz) {
        boolean inBoard = dx >= -1 && dx <= 1 && dz >= -1 && dz <= 2;
        return dx >= -2 && dx <= 2 && dz >= -2 && dz <= 3 && !inBoard;
    }

    /**
     * 从工作方块反查它挨着的那座阵的阵心；不在任何阵的外圈里就返回 {@code null}。
     *
     * <p>先只认「阵心那一格」（一条方块查询就能排除绝大多数候选），
     * 再确认工作方块确实落在它的外圈上。</p>
     */
    public static BlockPos boardOrigin(Level level, BlockPos workPos) {
        if (level == null) {
            return null;
        }
        for (int dz = -3; dz <= 3; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                BlockPos candidate = workPos.offset(dx, 0, dz);
                BlockState state = level.getBlockState(candidate);
                if (!(state.getBlock() instanceof StarDialBlock)
                        || state.getValue(StarDialBlock.ROLE) != StarDialBlock.Role.R1C1) {
                    continue;
                }
                if (isRing(workPos.getX() - candidate.getX(), workPos.getZ() - candidate.getZ())) {
                    return candidate;
                }
            }
        }
        return null;
    }

    /**
     * 读一座阵当下的星象：十二格里有物品的，那颗星就算亮着。
     *
     * <p>摇光不占格，由 {@link Constellation} 在北斗六星全亮时自动点亮。</p>
     */
    public static Constellation at(Level level, BlockPos origin) {
        if (level == null) {
            return Constellation.EMPTY;
        }
        BlockState anchor = level.getBlockState(origin);
        if (!(anchor.getBlock() instanceof StarDialBlock)) {
            return Constellation.EMPTY;
        }
        // ★ 第一百二十三批：星位由「图案落在哪一格」决定，不再按行切北斗 / 南斗。
        StarDialBlock.Chart chart = anchor.getValue(StarDialBlock.CHART);
        EnumSet<Star> lit = EnumSet.noneOf(Star.class);
        for (StarDialBlock.Seat seat : StarDialBlock.seats(chart)) {
            if (level.getBlockEntity(StarDialBlock.cellAt(origin, seat.row(), seat.col()))
                    instanceof StarDialBlockEntity dial && !dial.getItem().isEmpty()) {
                lit.add(seat.star());
            }
        }
        return lit.isEmpty() ? Constellation.EMPTY : new Constellation(lit);
    }
}
