package skydeity.mysticpivot;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * 玄枢三器共用的界面画笔。
 *
 * <p>面板与槽位沿用 AE2 1.21.1 压印器的骨架（{@link #panel} / {@link #slot}，
 * 四个色：{@link #PANEL_BODY} / {@link #PANEL_HI} / {@link #PANEL_LO} / {@link #PANEL_EDGE}），
 * 三台机器全用这几个色。</p>
 *
 * <h2>面板（{@link #panel}）</h2>
 * <p>一层平色 + 上左 1 像素冷白 + 下右 1 像素灰影 + 最外 1 像素近黑，没有任何渐变。</p>
 *
 * <h2>槽位（{@link #slot}）</h2>
 * <p>「冷白细框 + 比面板更暗的底 + 顶上一道更深的影」——槽比面板暗，一眼浅浅凹进去。</p>
 *
 * <h2>进度（{@link #progressArrow}）</h2>
 * <p><b>改版重点</b>：上一版照抄压印器那支「平涂灰箭头 + 左侧灌蓝」，
 * 三台机器因此整体读起来就是 AE2 换了个壳。这一版把箭头重做成<b>自家青金主题</b>的符文箭：
 * <ul>
 *   <li><b>形状</b>：箭杆两端各收 1 像素做圆头；箭尾带一道<b>内凹缺口</b>（真箭羽的轮廓，
 *       正中最深）——不再是直挺挺的梯形；</li>
 *   <li><b>底槽</b>：深板岩凹槽，顶上一道内影，底下 1 像素投影把箭头从面板上抬起来；</li>
 *   <li><b>填充</b>：自上而下三段<b>青绿渐变</b>（亮青 → 青 → 深青），
 *       前沿 1 像素<b>白青灵光</b>标示走到哪儿，顶沿 1 像素高光收边。</li>
 * </ul>
 * 几何占位仍是 {@code w × h}（三台机器都传 26 × 14），三屏的排版常量一个都不用动。</p>
 */
public final class XuanshuGui {

    // ---- AE2 面板调色板（appliedenergistics2 19.2.18 / inscriber.png 实测） ----

    /** 面板体（浅灰蓝）。 */
    public static final int PANEL_BODY = 0xFFE4E7F2;
    /** 面板内上 / 左高光（冷白）。 */
    public static final int PANEL_HI = 0xFFFFFFFF;
    /** 面板内下 / 右灰影。 */
    public static final int PANEL_LO = 0xFFA7AEC6;
    /** 最外一圈近黑描边。 */
    public static final int PANEL_EDGE = 0xFF454C68;

    // ---- AE2 槽位：亮框 + 深底 ----

    /** 槽底（比面板暗一档）。 */
    public static final int SLOT_FILL = 0xFFC8CCDE;
    /** 槽内顶部那道更深的影。 */
    public static final int SLOT_TOP = 0xFFDADFED;

    // ---- 符文进度箭（自家青金主题，取代 AE2 那支平涂灰箭头） ----

    /** 箭的描边（近黑板岩）。 */
    public static final int ARROW_EDGE = 0xFF3B3B4F;
    /** 未走到的那一段：比面板暗、比槽底更冷的凹槽。 */
    public static final int ARROW_CHANNEL = 0xFFA6ABBE;
    /** 凹槽内顶部那道影。 */
    public static final int ARROW_CHANNEL_TOP = 0xFF8B90A6;
    /** 实心箭头自己的灰（比槽底再深一档，让箭尖一眼立得住）。 */
    public static final int ARROW_HEAD = 0xFF8E93A8;
    /** 箭体外 1 像素投影（把箭从面板上抬起来）。 */
    public static final int ARROW_SHADOW = 0xFF9BA0B0;

    /** 填充三段：顶亮青 / 腰青 / 底深青。 */
    public static final int ARROW_FILL_HI = 0xFF6FDCCE;
    public static final int ARROW_FILL_MID = 0xFF2FA396;
    public static final int ARROW_FILL_LO = 0xFF15605C;
    /** 顶沿高光 + 前沿灵光（白青）。 */
    public static final int ARROW_GLINT = 0xFFDFFBF5;

    /** 箭头尾杆占高度的比例。 */
    private static final double ARROW_TAIL_RATIO = 0.46;
    /** 箭尾内凹缺口的深度（像素，正中最深）。 */
    private static final int ARROW_NOTCH = 2;

    // ---- 面板上的三种字色（第 150 批：不再「一团黑」，金色一律换 lore 蓝） ----

    /**
     * 说明文字：槽位小字、节点存量这类正文。
     *
     * <p>旧值 {@code 0xFF4A4E66} / {@code 0xFF23253A} 在浅面板上几乎就是黑，
     * 中文小字一压上去显得又黑又厚；这里抬到灰蓝 {@code 0xFF6E7794}，
     * 笔画立刻轻一档，远看的层次也更像 AE2 自己那套界面。</p>
     */
    public static final int TEXT_SOFT = 0xFF6E7794;

    /**
     * 强调文字：标题旁的提示、附魔条数、节点类型这类「要一眼看到」的字。
     *
     * <p>旧值是土金 {@code 0xFF7A5A12}；用户要求换成物品 lore 的那种蓝
     * （{@code ModItems#starLore} 的 {@code 0x8FB0FF}）。原色在浅面板上对比度只有 1.7:1，
     * 读不清，所以同色相压深一档取 {@code 0xFF4A6FE0}：还是那支蓝，但落在浅底上看得清。</p>
     */
    public static final int TEXT_ACCENT = 0xFF4A6FE0;

    /** 强调文字里更「淡」的一档（信息框正文，比 {@link #TEXT_ACCENT} 再沉一点）。 */
    public static final int TEXT_ACCENT_SOFT = 0xFF5E7FE0;

    private XuanshuGui() {
    }

    /**
     * 一块 AE2 风的面板：近黑描边 + 浅灰蓝面 + 上左高光 + 下右灰影。
     *
     * <p>描边画在 {@code (x-1, y-1)} 到 {@code (x+w+1, y+h+1)}，与改版前的原版风面板占位一致，
     * 所以三个界面的排版常量一个都不用动。</p>
     */
    public static void panel(GuiGraphics graphics, int x, int y, int w, int h) {
        // 最外 1 像素近黑描边
        graphics.fill(x - 1, y - 1, x + w + 1, y, PANEL_EDGE);
        graphics.fill(x - 1, y + h, x + w + 1, y + h + 1, PANEL_EDGE);
        graphics.fill(x - 1, y, x, y + h, PANEL_EDGE);
        graphics.fill(x + w, y, x + w + 1, y + h, PANEL_EDGE);
        // 面板体
        graphics.fill(x, y, x + w, y + h, PANEL_BODY);
        // 内侧上 / 左高光
        graphics.fill(x, y, x + w - 1, y + 1, PANEL_HI);
        graphics.fill(x, y, x + 1, y + h - 1, PANEL_HI);
        // 内侧下 / 右灰影
        graphics.fill(x + 1, y + h - 1, x + w, y + h, PANEL_LO);
        graphics.fill(x + w - 1, y + 1, x + w, y + h, PANEL_LO);
    }

    /**
     * 一格 18×18 的 AE2 槽位（传入槽左上角，即 {@code slot.x - 1}）。
     *
     * <p><b>外层冷白细框</b>，里面是<b>比面板更暗</b>的灰蓝底，顶上再压一道更深的影。</p>
     */
    public static void slot(GuiGraphics graphics, int sx, int sy) {
        // 冷白亮框（整格先铺满，再用槽底把内部盖掉，剩下就是 1 像素的框）
        graphics.fill(sx, sy, sx + 18, sy + 18, PANEL_HI);
        // 槽底（比面板暗）
        graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, SLOT_FILL);
        // 顶部一道更深的影（内凹感）
        graphics.fill(sx + 1, sy + 1, sx + 17, sy + 2, SLOT_TOP);
    }

    /**
     * 居中画一行<b>不带投影</b>的字（第一百五十三批）。
     *
     * <p>用户：「注灵台和奥术台字体还是有点厚重的感觉」。第 150 批只把字色抬浅了，但那层
     * <b>1 像素投影</b>还在——{@code drawCenteredString} 默认 {@code dropShadow = true}，
     * 中文小字叠上投影就发糊、发厚。这里统一走 {@code drawString(..., false)}，
     * 字色不变、只去掉投影，笔画立刻干净。</p>
     *
     * @param cx 文字块的水平中线
     */
    public static void centerText(GuiGraphics graphics, Font font, Component text, int cx, int y, int color) {
        graphics.drawString(font, text, cx - font.width(text) / 2, y, color, false);
    }

    /**
     * 画一支朝右的<b>符文进度箭</b>（三台机器中间那条制作进度）。
     *
     * @param ratio 进度，{@code [0,1]}；箭杆先满、最后才点亮箭头尖
     */
    public static void progressArrow(GuiGraphics graphics, int x, int y, int w, int h, float ratio) {
        if (w < 6 || h < 4) {
            return;
        }
        int headW = headWidth(w, h);
        int tailW = Math.max(1, w - headW);
        int tailTop = shaftTop(h);
        int tailH = shaftHeight(h);

        // ① 投影：整支下移 1 像素压一层淡影，把箭从面板上抬起来
        outline(graphics, x, y + 1, w, h, ARROW_SHADOW);
        // ② 近黑描边（整支形状）
        outline(graphics, x, y, w, h, ARROW_EDGE);
        // ③ 槽底：箭杆是一道凹槽，箭尖压成实心灰 —— 一眼分得出「轨道」与「箭头」
        int shaftY0 = y + tailTop;
        int shaftY1 = shaftY0 + tailH;
        graphics.fill(x + 1, shaftY0 + 1, x + tailW, shaftY1 - 1, ARROW_CHANNEL);
        graphics.fill(x + 1, shaftY0 + 1, x + tailW, shaftY0 + 2, ARROW_CHANNEL_TOP);
        headShape(graphics, x, y, x + tailW, headW, h, ARROW_HEAD, 0, h, INNER);

        // ④ 左侧按进度灌青芯：底(深青) → 腰(青) → 顶(亮青) 三段渐变
        int span = w - 2;
        int fill = Mth.clamp(Mth.ceil(Mth.clamp(ratio, 0.0F, 1.0F) * span), 0, span);
        if (fill <= 0) {
            return;
        }
        // 内芯的三个分界行（相对 y），沿用「顶 28% 亮青 / 腰 62% 青 / 余下深青」的原比例
        int hiRow = 1 + (int) Math.ceil((h - 2) * 0.28);
        int midRow = 1 + (int) Math.ceil((h - 2) * 0.62);
        graphics.enableScissor(x + 1, y + 1, x + 1 + fill, y + h - 1);
        graphics.fill(x + 1, shaftY0 + 1, x + tailW, shaftY1 - 1, ARROW_FILL_LO);
        headShape(graphics, x, y, x + tailW, headW, h, ARROW_FILL_LO, 1, h - 1, INNER);
        fillAbove(graphics, x + 1, shaftY0 + 1, x + tailW, shaftY1 - 1, ARROW_FILL_MID, y + midRow);
        headShape(graphics, x, y, x + tailW, headW, h, ARROW_FILL_MID, 1, midRow, INNER);
        fillAbove(graphics, x + 1, shaftY0 + 1, x + tailW, shaftY1 - 1, ARROW_FILL_HI, y + hiRow);
        headShape(graphics, x, y, x + tailW, headW, h, ARROW_FILL_HI, 1, hiRow, INNER);
        // 顶沿 1 像素高光：把青芯的「上棱」勾出来
        graphics.fill(x + 1, shaftY0 + 1, x + tailW, shaftY0 + 2, ARROW_GLINT);
        graphics.disableScissor();
        // ⑤ 前沿 1 像素白青灵光：走到哪儿一眼看得见。
        //   ★ 只画「箭形真正覆盖到的那一列」——原版直接竖着拉一条线，
        //     进度满时那列已经落到箭头斜边之外，会戳出 1~2 像素（本批修的就是这个）。
        int glintX = x + fill - 1;
        if (glintX <= x + tailW - 1) {
            graphics.fill(glintX, shaftY0 + 1, glintX + 1, shaftY1 - 1, ARROW_GLINT);
        }
        headGlint(graphics, y, x + tailW, headW, h, glintX, ARROW_GLINT);
    }

    /** 前沿灵光：只在箭尖三角形内部、且正好落在 {@code col} 这一列的若干行上落笔。 */
    private static void headGlint(GuiGraphics graphics, int y, int headLeft, int headW,
                                  int hRef, int col, int color) {
        double half = Math.max(0.5, (hRef - 1) / 2.0);
        for (int row = 1; row < hRef - 1; row++) {
            double t = 1.0 - Math.abs(row - (hRef - 1) / 2.0) / half;
            if (t <= 0.0) {
                continue;
            }
            int back = headLeft + (int) Math.round(ARROW_NOTCH * t) + INNER;
            int front = headLeft + (int) Math.round(headW * t) - INNER;
            if (col >= back && col < front) {
                graphics.fill(col, y + row, col + 1, y + row + 1, color);
            }
        }
    }

    /** 描边用：不内缩。 */
    private static final int EDGE = 0;
    /** 内芯（槽底 / 青芯）用：左右各内缩 1 像素，保证永远不越出描边。 */
    private static final int INNER = 1;

    /** 箭头宽（≈ 全高），箭杆长 = 总宽 − 箭头宽。 */
    private static int headWidth(int w, int h) {
        return Math.min(h, Math.max(2, w - 2));
    }

    /** 箭杆在箭形里的起始行。 */
    private static int shaftTop(int h) {
        return (h - shaftHeight(h)) / 2;
    }

    /** 箭杆高（占全高四成半）。 */
    private static int shaftHeight(int h) {
        return Math.max(2, (int) Math.round(h * ARROW_TAIL_RATIO));
    }

    /** 整支箭的外形（描边 / 投影用）：圆头箭杆 + 三角箭头。 */
    private static void outline(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        int headW = headWidth(w, h);
        int tailW = Math.max(1, w - headW);
        int tailTop = shaftTop(h);
        int tailH = shaftHeight(h);
        // 箭杆：首末行各内缩 1 像素 ⇒ 圆头
        graphics.fill(x + 1, y + tailTop, x + tailW, y + tailTop + 1, color);
        graphics.fill(x, y + tailTop + 1, x + tailW, y + tailTop + tailH - 1, color);
        graphics.fill(x + 1, y + tailTop + tailH - 1, x + tailW, y + tailTop + tailH, color);
        // 箭头
        headShape(graphics, x, y, x + tailW, headW, h, color, 0, h, EDGE);
    }

    /**
     * 三角箭头：正中那几行顶到箭尖，后缘按 {@code t} 收出一道内凹缺口（真箭羽的轮廓）。
     *
     * <p>{@code headLeft} 是箭头底边所在的 x；{@code hRef} 是三角形的高度参照
     * （内芯与描边共用同一个参照，两者才严丝合缝）；{@code rowFrom/rowTo} 只落这几行。</p>
     *
     * <p>★★ {@code inset} 是「左右各向内收几像素」：<b>描边传 0，内芯一律传 1</b>。
     * 12.63/12.66 两批的青色溢出就是因为内芯自己另算了一套 (headLeft+1, headW−2) 的
     * 三角形 —— 逐行 {@code Math.round} 的取整与描边对不上，越到三角下缘偏得越多，
     * 青芯会从描边外面戳出来 1~2 像素。现在内芯与描边共用同一组几何，只靠 {@code inset}
     * 内缩，取整误差被 1 像素的边吃掉，绝不会越界。</p>
     */
    private static void headShape(GuiGraphics graphics, int x, int y, int headLeft, int headW,
                                  int hRef, int color, int rowFrom, int rowTo, int inset) {
        if (headW < 2) {
            return;
        }
        double half = Math.max(0.5, (hRef - 1) / 2.0);
        for (int row = Math.max(0, rowFrom); row < Math.min(hRef, rowTo); row++) {
            double t = 1.0 - Math.abs(row - (hRef - 1) / 2.0) / half;
            if (t <= 0.0) {
                continue;
            }
            int back = headLeft + (int) Math.round(ARROW_NOTCH * t) + inset;
            int front = headLeft + (int) Math.round(headW * t) - inset;
            if (back < front) {
                graphics.fill(back, y + row, front, y + row + 1, color);
            }
        }
    }

    /** 把一段矩形里「低于 {@code upperY}」的那部分换个颜色（用来叠竖直渐变）。 */
    private static void fillAbove(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color, int upperY) {
        int low = Math.max(y0, Math.min(y1, upperY));
        if (y0 < low) {
            graphics.fill(x0, y0, x1, low, color);
        }
    }

    /** 1 像素空心矩形。 */
    public static void ring(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y + 1, x + 1, y + h - 1, color);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }
}
