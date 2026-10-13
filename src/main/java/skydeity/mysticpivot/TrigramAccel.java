package skydeity.mysticpivot;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * 卦位石对注魔坛的<b>催炉</b>：坛脚那一圈立得越多，合成跑得越快。
 *
 * <h2>怎么数</h2>
 * <p>中心取<b>祭坛中央台座正下方那一格</b>（本体 {@code centralPedestal()} 是
 * {@code worldPosition.below(2)}，再下一格即是坛脚），范围是<b>9×9 单层</b>——
 * 也就是以那一格为中心、半径 4 的水平一层。落在这个范围里的<b>任意</b>卦位石都算数，
 * 不挑卦象、不挑朝向：这一条是「多立多快」的体力活，不是门禁那种「必须凑齐八卦」的谜题。</p>
 *
 * <p>数石头这件事直接复用 {@link Fengshui#countTrigramStones}——<b>不再写第二套范围口径</b>。
 * 门禁那边数的是「符不符合要求」，这边数的是「一共有多少块」，同一套 {@code radius × height}
 * 语义，将来改范围只需改两边的调用参数，不会出现「门禁说 5×5、催炉说 9×9」这种自相矛盾。</p>
 *
 * <h2>倍率</h2>
 * <p>{@code 倍率 = 1 + min(块数, 16)}：一块也不立就是原速（1×），立满 16 块是 17×。
 * 上限卡在 16 是为了让「多立有用、但别无限堆」——坛脚能摆的格子远多于 16，
 * 不封顶的话玩家会拿石头把整片地铺满。</p>
 */
public final class TrigramAccel {

    /** 水平半径：4 = 9×9 单层。 */
    public static final int RADIUS = 4;

    /** 竖直层数：只算坛脚那一层。 */
    public static final int HEIGHT = 1;

    /** 计入倍率的石头上限；再往上堆不再提速。 */
    public static final int MAX_STONES = 16;

    private TrigramAccel() {
    }

    /** 数一数坛脚 9×9 单层里立了多少块卦位石（不挑卦象）。 */
    public static int stonesAt(ServerLevel level, BlockPos foot) {
        return Fengshui.countTrigramStones(level, foot, null, RADIUS, HEIGHT);
    }

    /**
     * 这一炉的加速倍率，恒 ≥ 1。
     *
     * <p>{@code 1} 表示不加速（坛脚没立石头），{@code 17} 是满配。调用方拿到之后
     * 把本体的节拍 {@code countDelay} 按它缩小即可——<b>不要</b>去改 {@code count} 或
     * 合成进度：那些是本体自己的状态机，动它们等于把一炉合成拆散。</p>
     */
    public static int multiplierAt(ServerLevel level, BlockPos foot) {
        return 1 + Math.min(stonesAt(level, foot), MAX_STONES);
    }
}
