package skydeity.mysticpivot;

/**
 * 「能读星象的工作方块」标记（第一百一十八批、第一百二十批扩展）。
 *
 * <p>摆在阵的 <b>5×6 外圈</b>（见 {@link StarWork#isRing}）里的方块，只要它的方块实体实现了
 * 这个接口，阵心就会在它身上亮一束光——告诉玩家「这一格起效了」。
 * 第 149 批起这束光是<b>本体原生光束</b>（自转 + UV 上滚的三片长条），
 * <b>从方块正中（y+0.5）起、往上拉到 y+7.5</b>，见 {@link StarBeamEffects}。</p>
 *
 * <p>颜色由 {@link #guideColor()} 决定：玄枢炎炉赤红、以后的机器各自挑色。</p>
 */
public interface StarReader {

    /** 引导线的颜色（{@code 0xRRGGBB}）。 */
    int guideColor();

    /**
     * 引导线亮不亮——<b>只在工作条件满足时才画</b>（用户：线出现的前提是符合工作条件）。
     *
     * <p>渲染器把当下这座阵的星象递进来，各类机器各判各的：</p>
     * <ul>
     *   <li>玄枢炎炉（赤）：斗口四星各供玄枢锭（翻倍成立）；</li>
     *   <li>玄枢坩埚（绿）：北斗七座各供玄枢碎片；</li>
     *   <li>玄枢复刻台（蓝绿）：南斗六星各供龙蛋；</li>
     *   <li>玄枢奥术台 / 玄枢注灵台（金）：南斗五星各供<b>玄枢锭</b>。</li>
     * </ul>
     */
    boolean guideActive(Constellation constellation);
}
