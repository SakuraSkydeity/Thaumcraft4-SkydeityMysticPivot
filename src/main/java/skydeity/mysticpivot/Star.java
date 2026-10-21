package skydeity.mysticpivot;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * 南北斗的十三颗星（第一百一十七批）。
 *
 * <h2>十二个星位 + 一颗隐星</h2>
 * <p>阵是 3×4 = 12 格，而上两行 6 格、下两行 6 格正好对半分：
 * <b>北两行 = 北斗六星</b>（天枢、天璇、天玑、天权、玉衡、开阳），
 * <b>南两行 = 南斗六星</b>（天府、天梁、天机、天同、天相、七杀）。</p>
 * <p>北斗还有第七星<b>摇光</b>，十二格放不下——它是<b>隐星</b>：
 * 不动手放东西，北斗六星全亮时「斗成柄现」，由 {@link Constellation} 自动点亮。</p>
 *
 * <h2>命名冲突</h2>
 * <p>北斗的「天玑」和南斗的「天机」拼音都是 {@code tianji}，所以 id 一律带
 * {@code beidou_} / {@code nandou_} 前缀，别图省事。</p>
 */
public enum Star implements StringRepresentable {

    BEIDOU_TIANSHU("beidou_tianshu", true, true),
    BEIDOU_TIANXUAN("beidou_tianxuan", true, true),
    BEIDOU_TIANJI("beidou_tianji", true, true),
    BEIDOU_TIANQUAN("beidou_tianquan", true, true),
    BEIDOU_YUHENG("beidou_yuheng", true, true),
    BEIDOU_KAIYANG("beidou_kaiyang", true, true),
    /** 北斗第七星（斗柄尖）：<b>没有星位</b>，北斗六星全亮时自动现。 */
    BEIDOU_YAOGUANG("beidou_yaoguang", true, false),

    NANDOU_TIANFU("nandou_tianfu", false, true),
    NANDOU_TIANLIANG("nandou_tianliang", false, true),
    NANDOU_TIANJI("nandou_tianji", false, true),
    NANDOU_TIANTONG("nandou_tiantong", false, true),
    NANDOU_TIANXIANG("nandou_tianxiang", false, true),
    NANDOU_QISHA("nandou_qisha", false, true);

    /** 北斗斗口四星（勺口）：全亮即「斗口合」。 */
    public static final Star[] BEIDOU_MOUTH = {
            BEIDOU_TIANSHU, BEIDOU_TIANXUAN, BEIDOU_TIANJI, BEIDOU_TIANQUAN,
    };

    /** 南斗斗口四星：全亮即「南斗口合」。 */
    public static final Star[] NANDOU_MOUTH = {
            NANDOU_TIANFU, NANDOU_TIANLIANG, NANDOU_TIANJI, NANDOU_TIANTONG,
    };

    /** 北斗暖金 / 南斗冷银。 */
    private static final int WARM = 0xE8C87C;
    private static final int COLD = 0xB4CBE4;

    private final String id;
    private final boolean beidou;
    private final boolean seated;

    Star(String id, boolean beidou, boolean seated) {
        this.id = id;
        this.beidou = beidou;
        this.seated = seated;
    }

    @Override
    public String getSerializedName() {
        return this.id;
    }

    public String id() {
        return this.id;
    }

    /** 北斗（阳、暖金）还是南斗（阴、冷银）。 */
    public boolean beidou() {
        return this.beidou;
    }

    /** 有没有自己的星位（只有摇光没有）。 */
    public boolean seated() {
        return this.seated;
    }

    /** 星名：「天枢」「天府」…… */
    public Component displayName() {
        return Component.translatable("star.skydeitymysticpivot." + this.id);
    }

    /** 文字颜色：北斗暖金、南斗冷银。 */
    public int color() {
        return this.beidou ? WARM : COLD;
    }
}
