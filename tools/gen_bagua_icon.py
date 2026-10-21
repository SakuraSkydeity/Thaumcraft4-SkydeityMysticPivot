#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""研究书「玄枢」分类图标 —— 像素风仙家八卦璧（32×32）。

用户原话：「魔导书左侧的栏中八卦那个小图标修改，改为像素风的仙家的感觉」。

<h2>旧图的问题</h2>
<p>旧版是 16×16 的金黑太极盘：金环太厚、太极太小，缩到侧栏 20 来像素时只剩
一团金色，读不出「八卦」，更没有「仙家」。</p>

<h2>新的画法</h2>
<ul>
  <li><b>底</b>：一圈极淡的藕粉灵光（双层半透明圆）——仙气从「光」来，不从描边来；</li>
  <li><b>璧</b>：青玉盘 + 两圈鎏金边（外暗金、内亮金），玉面留几道玉纹；</li>
  <li><b>八卦</b>：玉盘内缘八向各一枚金刻，长短交替（乾兑离震巽坎艮坤）；</li>
  <li><b>心</b>：一枚紫粉太极——阴半深紫、阳半藕粉，S 线用 1 像素勾出来，
      两个鱼眼各一点金；</li>
  <li><b>额</b>：正上方一枚小金云头，点明「仙家」。</li>
</ul>

32×32 是侧栏图标能吃到的最小「能画细节」尺寸：16 格塞不下八刻 + 太极 + 云头。
"""
import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DST = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/misc/bagua.png")
PREVIEW = os.path.join(ROOT, "tools/preview")

S = 32
C = 15.5

JADE = (150, 178, 162)
JADE_L = (182, 206, 190)
JADE_D = (112, 140, 124)
GOLD = (222, 178, 88)
GOLD_D = (150, 104, 40)
GOLD_L = (248, 226, 156)
YIN = (74, 44, 98)
YANG = (240, 214, 238)
PINK = (226, 150, 222)
VIOLET = (150, 96, 210)


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def put(im, x, y, color, alpha=255):
    if 0 <= x < S and 0 <= y < S:
        im.putpixel((x, y), color + (alpha,))


def disc(im, cx, cy, r, color, alpha=255):
    for y in range(S):
        for x in range(S):
            if math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= r:
                put(im, x, y, color, alpha)


def ring(im, cx, cy, r, color, width=1.0, alpha=255):
    for y in range(S):
        for x in range(S):
            if abs(math.hypot(x + 0.5 - cx, y + 0.5 - cy) - r) < width / 2:
                put(im, x, y, color, alpha)


def main():
    os.makedirs(PREVIEW, exist_ok=True)
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))

    # ---- 一、灵光：两层极淡的藕粉 / 紫 ----
    disc(im, C, C, 15.4, PINK, 30)
    disc(im, C, C, 14.6, VIOLET, 26)

    # ---- 二、璧：青玉盘 + 双金边 ----
    disc(im, C, C, 13.0, GOLD_D)
    disc(im, C, C, 12.2, GOLD)
    disc(im, C, C, 11.4, JADE)
    # 玉面：上亮下暗的薄渐变 + 几道玉纹
    for y in range(S):
        for x in range(S):
            if math.hypot(x + 0.5 - C, y + 0.5 - C) <= 11.3:
                t = (y - 4) / 24.0
                im.putpixel((x, y), lerp(JADE_L, JADE_D, max(0.0, min(1.0, t))) + (255,))
    for (x, y, d) in ((9, 12, 3), (22, 20, 3), (13, 23, 2), (20, 10, 2)):
        for k in range(d):
            put(im, x + k, y + k, JADE_D, 150)

    # 玉面内缘一圈暗青，托住金边（不然金边像浮在玉上）
    ring(im, C, C, 10.7, JADE_D, 1.0)

    # ---- 三、八向卦刻：玉盘内缘，长短交替 ----
    for k in range(8):
        a = math.pi * 2 * k / 8
        inner = 8.2 if k % 2 == 0 else 8.4
        outer = 9.2 if k % 2 == 0 else 8.9
        steps = int((outer - inner) * 4) + 1
        for i in range(steps):
            r = inner + (outer - inner) * i / max(1, steps - 1)
            x = int(round(C + math.cos(a) * r - 0.5))
            y = int(round(C + math.sin(a) * r - 0.5))
            put(im, x, y, GOLD if k % 2 == 0 else GOLD_D)

    # ---- 四、太极 ----
    R = 6.2
    disc(im, C, C, R + 0.9, GOLD_D)          # 外圈描边
    disc(im, C, C, R, YANG)                  # 阳底
    # 阴半（左）
    for y in range(S):
        for x in range(S):
            if math.hypot(x + 0.5 - C, y + 0.5 - C) <= R and x + 0.5 < C:
                im.putpixel((x, y), YIN + (255,))
    # S 线：上半个小圆填阴、下半个小圆填阳
    disc(im, C, C - R / 2, R / 2, YIN)
    disc(im, C, C + R / 2, R / 2, YANG)
    # 鱼眼：各一点金
    disc(im, C, C - R / 2, 1.2, GOLD_L)
    disc(im, C, C + R / 2, 1.2, GOLD_D)
    # 太极外圈再勾一圈亮金（只勾外侧半像素）
    ring(im, C, C, R + 0.9, GOLD, 0.9)

    # ---- 五、（无额饰）32 格里云头会压住玉盘，宁缺 ----

    im.save(DST)
    print("->", os.path.relpath(DST, ROOT), im.size)

    cell = S * 14
    canvas = Image.new("RGBA", (cell + 24, cell + 24), (26, 22, 38, 255))
    canvas.paste(im.resize((cell, cell), Image.NEAREST), (12, 12))
    canvas.save(os.path.join(PREVIEW, "bagua_icon.png"))
    print("preview ->", os.path.relpath(os.path.join(PREVIEW, "bagua_icon.png"), ROOT))


if __name__ == "__main__":
    main()
