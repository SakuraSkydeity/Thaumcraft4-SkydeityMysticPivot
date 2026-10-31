#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
玄枢虚空采矿方块（xuanshu_void_mining_block）· 未成型态贴图（第一百八十五批重画）。

<h2>为什么这一批要改</h2>

用户原话：「采矿方块贴图还是太丑了 上面不要圆盘 修改机械风格」。

第 184 批的顶面是一枚**圆盘**（玉盘 + 同心环 + 八向金钉）——圆盘是「仙家供奉」的
语言，不是机械的语言。这一批顶面整个推倒重来，三张 16×16 全部按**方形机械面板**画：

  * **顶面**：鎏金**方框**（外亮内暗四档）+ 拉丝银面板 + 居中**下沉方窗**（阵纹灵窗）
    + 四角**十字螺钉**——一方块就是一块机器护板，没有一个圆；
  * **侧面**：拉丝面板 + 上下鎏金压边 + 两道机械分板缝 + 阵纹灵窗 + 四角十字螺钉
    （第 184 批的圆铆钉换成十字螺钉，更机械）；
  * **底面**：暗银底 + 一圈金环 + 四颗金钉（底面有交代，从下方也不穿帮）。

阵纹灵窗 = 凹陷窗框（上左暗 / 下右亮）+ 暖白灵气渐变 + 鎏金阵纹边 + 中央回纹阵眼
（顶面窗大，四角再各引一道 L 形回路）——**机械的壳、修仙的心**。

抖动归零（工程口径）：拉丝与纹样全部**固定分档**，不靠随机噪声。

方块是整格立方体，`cube_bottom_top`，未成型没有独立状态；成型后由
`xuanshu_void_mining.json` 的 variants 换成空模型（祭坛从核心格长出，穿过本格）。
"""
import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BLOCK_TEX = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot",
                         "textures/block")

S = 16

# 与 gen_astrolabe_obj.py 同一套色（改一处要一起改，否则台座与机器两个色系）
SW_HI = (247, 250, 254)
SW = (223, 229, 238)
SW_D = (186, 194, 207)
SW_DD = (140, 149, 164)
SW_INK = (96, 104, 118)

GOLD = (226, 182, 94)
GOLD_HI = (252, 228, 154)
GOLD_D = (176, 131, 52)
GOLD_DD = (120, 86, 30)

GLOW = (255, 253, 244)      # 灵窗底 · 暖白（鎏金白的「白」）
GLOW_D = (236, 226, 198)    # 灵窗底 · 边缘
CORE = (255, 255, 252)      # 阵眼芯


def canvas():
    return Image.new("RGBA", (S, S))


def fill(im, color):
    """平铺底色（抖动归零：素底不加噪）。"""
    for y in range(S):
        for x in range(S):
            im.putpixel((x, y), color + (255,))


def rect(im, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if 0 <= x < S and 0 <= y < S:
                im.putpixel((x, y), c + (255,))


def disc(im, cx, cy, r, c):
    for y in range(S):
        for x in range(S):
            if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                im.putpixel((x, y), c + (255,))


def ring(im, cx, cy, r, c, w=0.9):
    for y in range(S):
        for x in range(S):
            if abs(math.hypot(x + 0.5 - cx, y + 0.5 - cy) - r) < w:
                im.putpixel((x, y), c + (255,))


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def brush(im, y0, y1):
    """竖向拉丝：每 4 纹素一道亮、一道暗（固定分档，读作金属拉丝板）。"""
    for x in range(S):
        if x % 4 == 1:
            rect(im, x, y0, x, y1, SW_HI)
        elif x % 4 == 3:
            rect(im, x, y0, x, y1, SW_D)


def trim(im):
    """上下鎏金压边三档 + 顶/底缘的倒角。"""
    rect(im, 0, 0, 15, 0, SW_HI)
    rect(im, 1, 1, 14, 1, GOLD_HI)
    rect(im, 1, 2, 14, 2, GOLD)
    rect(im, 1, 3, 14, 3, GOLD_D)
    rect(im, 1, 13, 14, 13, GOLD_D)
    rect(im, 1, 14, 14, 14, GOLD_DD)
    rect(im, 0, 15, 15, 15, SW_INK)


def screw(im, cx, cy):
    """十字螺钉：一横一竖 + 亮芯（机械感，替掉第 184 批的圆铆钉）。"""
    for (dx, dy) in ((0, 0), (-1, 0), (1, 0), (0, -1), (0, 1)):
        im.putpixel((cx + dx, cy + dy), GOLD_D + (255,))
    im.putpixel((cx, cy), GOLD_HI + (255,))


def glow_window(im, x0, y0, x1, y1, traces=False):
    """阵纹灵窗（方窗）：凹陷框 + 灵气渐变 + 鎏金阵纹边 + 中央回纹阵眼。

    `traces=True` 时（窗内 ≥6×6）四角再各引一道 L 形回路，从阵纹边拐向阵眼。
    """
    # 窗框：上左暗、下右亮 = 凹陷
    rect(im, x0, y0, x1, y0, SW_DD)
    rect(im, x0, y0, x0, y1, SW_DD)
    rect(im, x0, y1, x1, y1, SW_HI)
    rect(im, x1, y0, x1, y1, SW_HI)
    # 灵气底：中心亮、四缘暗（径向渐变，只用固定分档）
    cx, cy = (x0 + x1) / 2.0, (y0 + y1) / 2.0
    for y in range(y0 + 1, y1):
        for x in range(x0 + 1, x1):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            im.putpixel((x, y), mix(GLOW, GLOW_D, min(1.0, d / 5.2)) + (255,))
    # 阵纹边：窗内沿一圈金线
    rect(im, x0 + 1, y0 + 1, x1 - 1, y0 + 1, GOLD_D)
    rect(im, x0 + 1, y1 - 1, x1 - 1, y1 - 1, GOLD_D)
    rect(im, x0 + 1, y0 + 1, x0 + 1, y1 - 1, GOLD_D)
    rect(im, x1 - 1, y0 + 1, x1 - 1, y1 - 1, GOLD_D)
    # 四角 L 形回路（只有大窗才画，小窗留白）
    if traces:
        for (bx, by, dx, dy) in ((x0 + 1, y0 + 1, 1, 1), (x1 - 1, y0 + 1, -1, 1),
                                 (x0 + 1, y1 - 1, 1, -1), (x1 - 1, y1 - 1, -1, -1)):
            rect(im, bx, by, bx + dx, by, GOLD)
            rect(im, bx, by, bx, by + dy, GOLD)
    # 中央回纹阵眼：3×3 金环 + 亮芯
    ex, ey = int(round(cx)), int(round(cy))
    for (dx, dy) in ((-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (0, 1), (1, 1)):
        im.putpixel((ex + dx, ey + dy), GOLD + (255,))
    im.putpixel((ex, ey), CORE + (255,))


def side():
    """拉丝面板 + 阵纹灵窗 + 鎏金压边与十字螺钉。"""
    im = canvas()
    fill(im, SW)
    trim(im)
    brush(im, 4, 12)
    # 机械分板缝：面板上下各一道
    rect(im, 1, 4, 14, 4, SW_DD)
    rect(im, 1, 12, 14, 12, SW_DD)
    glow_window(im, 4, 5, 11, 10)
    # 四角十字螺钉（窗框左右各一对）
    for (cx, cy) in ((2, 6), (13, 6), (2, 10), (13, 10)):
        screw(im, cx, cy)
    return im


def top():
    """方形机械面板（不要圆盘）：鎏金方框 + 拉丝面板 + 阵纹灵窗 + 四角十字螺钉。"""
    im = canvas()
    fill(im, SW)
    brush(im, 2, 13)
    # 鎏金方框：外沿亮、框身金、内缘暗、外角暗（四档，读作包边）
    rect(im, 0, 0, 15, 0, GOLD_HI)
    rect(im, 0, 0, 0, 15, GOLD_HI)
    rect(im, 1, 1, 14, 1, GOLD)
    rect(im, 1, 1, 1, 14, GOLD)
    rect(im, 1, 14, 14, 14, GOLD_D)
    rect(im, 14, 1, 14, 14, GOLD_D)
    rect(im, 0, 15, 15, 15, GOLD_DD)
    rect(im, 15, 0, 15, 15, GOLD_DD)
    # 阵纹灵窗（居中下沉方窗，窗大，四角引回路）
    glow_window(im, 4, 4, 11, 11, traces=True)
    # 四角十字螺钉
    for (cx, cy) in ((3, 3), (12, 3), (3, 12), (12, 12)):
        screw(im, cx, cy)
    return im


def bottom():
    """暗银底 + 一圈金环 + 四颗金钉。"""
    im = canvas()
    fill(im, (88, 92, 104))
    rect(im, 0, 0, 15, 0, (66, 70, 80))
    rect(im, 0, 15, 15, 15, (66, 70, 80))
    rect(im, 0, 0, 0, 15, (66, 70, 80))
    rect(im, 15, 0, 15, 15, (66, 70, 80))
    ring(im, 8.0, 8.0, 5.8, (168, 140, 84), 0.9)
    ring(im, 8.0, 8.0, 6.4, (110, 92, 58), 0.5)
    for i in range(4):
        a = math.radians(45 + i * 90)
        disc(im, 8 + 5.0 * math.cos(a), 8 + 5.0 * math.sin(a), 0.9, GOLD_D)
    return im


def save(im, name):
    p = os.path.join(BLOCK_TEX, name)
    im.save(p)
    print("  %-38s 16x16" % name)


if __name__ == "__main__":
    save(side(), "xuanshu_void_mining_side.png")
    save(top(), "xuanshu_void_mining_top.png")
    save(bottom(), "xuanshu_void_mining_bottom.png")
    print("done")
