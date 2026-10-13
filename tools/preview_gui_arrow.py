#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""玄枢三器 GUI 的离线预览器（12.63 批新增，**可复跑**）。

用途：把 `XuanshuGui` 里那套自绘画笔（面板 / 槽位 / 符文进度箭）在离线环境按 **1:1 像素**
重画出来，放大成 PNG —— 改画法时不用进游戏就能看效果。

★ 关键设计：**颜色与几何常量直接从 `XuanshuGui.java` 里解析**（正则抓
`public static final int NAME = 0xFF...` 与 `private static final double/int NAME = ...`），
不在这里另抄一份 —— 抄一份就会有一天和 Java 对不上。

绘制语义严格对齐 `GuiGraphics`：
  `graphics.fill(x0, y0, x1, y1, c)` 填的是 **半开矩形 [x0,x1) × [y0,y1)**；
  `enableScissor(x0, y0, x1, y1)` 裁到同一个半开矩形；
  `Math.round(double)` = `floor(v + 0.5)`。

用法（在项目根目录）：
    python tools/preview_gui_arrow.py [--out tools/preview/gui_progress_arrow.png] [--scale 8]
"""
import argparse
import math
import os
import re

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
JAVA = os.path.join(ROOT, "src/main/java/skydeity/mysticpivot/XuanshuGui.java")


# ------------------------------------------------------------------ 常量解析
def load_constants(path):
    src = open(path, encoding="utf-8").read()
    ints, doubles = {}, {}
    for m in re.finditer(r"static final int (\w+) = (0x[0-9A-Fa-f]+);", src):
        ints[m.group(1)] = int(m.group(2), 16)
    for m in re.finditer(r"static final (double|int) (\w+) = ([0-9.]+);", src):
        (doubles if m.group(1) == "double" else ints)[m.group(2)] = float(m.group(3)) \
            if m.group(1) == "double" else int(m.group(3))
    # private static final int A = 1, B = 2;  （同一行多个）
    for m in re.finditer(r"static final int (BAR_DX|BAR_DY|BAR_W|BAR_H)", src):
        pass
    return ints, doubles


C, D = load_constants(JAVA)


def argb(v):
    return (v >> 16) & 255, (v >> 8) & 255, v & 255, (v >> 24) & 255


# ------------------------------------------------------------------ 画布
class Canvas:
    """极小的 GuiGraphics 替身：只有 fill / scissor。"""

    def __init__(self, w, h, bg=(0, 0, 0, 0)):
        self.im = Image.new("RGBA", (w, h), bg)
        self.px = self.im.load()
        self.w, self.h = w, h
        self.clip = (0, 0, w, h)

    def enableScissor(self, x0, y0, x1, y1):
        self.clip = (x0, y0, x1, y1)

    def disableScissor(self):
        self.clip = (0, 0, self.w, self.h)

    def fill(self, x0, y0, x1, y1, color):
        cx0, cy0, cx1, cy1 = self.clip
        xa, xb = max(x0, cx0, 0), min(x1, cx1, self.w)
        ya, yb = max(y0, cy0, 0), min(y1, cy1, self.h)
        col = argb(color)
        for y in range(ya, yb):
            for x in range(xa, xb):
                self.px[x, y] = col


def rnd(v):
    """Java Math.round(double)：floor(v + 0.5)。"""
    return math.floor(v + 0.5)


# ------------------------------------------------------------------ 画笔（1:1 镜像 XuanshuGui）
def panel(g, x, y, w, h):
    g.fill(x - 1, y - 1, x + w + 1, y, C["PANEL_EDGE"])
    g.fill(x - 1, y + h, x + w + 1, y + h + 1, C["PANEL_EDGE"])
    g.fill(x - 1, y, x, y + h, C["PANEL_EDGE"])
    g.fill(x + w, y, x + w + 1, y + h, C["PANEL_EDGE"])
    g.fill(x, y, x + w, y + h, C["PANEL_BODY"])
    g.fill(x, y, x + w - 1, y + 1, C["PANEL_HI"])
    g.fill(x, y, x + 1, y + h - 1, C["PANEL_HI"])
    g.fill(x + 1, y + h - 1, x + w, y + h, C["PANEL_LO"])
    g.fill(x + w - 1, y + 1, x + w, y + h, C["PANEL_LO"])


def slot(g, sx, sy):
    g.fill(sx, sy, sx + 18, sy + 18, C["PANEL_HI"])
    g.fill(sx + 1, sy + 1, sx + 17, sy + 17, C["SLOT_FILL"])
    g.fill(sx + 1, sy + 1, sx + 17, sy + 2, C["SLOT_TOP"])


def head_width(w, h):
    return min(h, max(2, w - 2))


def shaft_height(h):
    return max(2, rnd(h * D["ARROW_TAIL_RATIO"]))


def shaft_top(h):
    return (h - shaft_height(h)) // 2


def head_shape(g, x, y, head_left, head_w, h_ref, color, row_from, row_to, inset=0):
    if head_w < 2:
        return
    half = max(0.5, (h_ref - 1) / 2.0)
    for row in range(max(0, row_from), min(h_ref, row_to)):
        t = 1.0 - abs(row - (h_ref - 1) / 2.0) / half
        if t <= 0.0:
            continue
        back = head_left + rnd(C["ARROW_NOTCH"] * t) + inset
        front = head_left + rnd(head_w * t) - inset
        if back < front:
            g.fill(back, y + row, front, y + row + 1, color)


def fill_above(g, x0, y0, x1, y1, color, upper_y):
    low = max(y0, min(y1, upper_y))
    if y0 < low:
        g.fill(x0, y0, x1, low, color)


def outline(g, x, y, w, h, color):
    hw, tw = head_width(w, h), max(1, w - head_width(w, h))
    tt, th = shaft_top(h), shaft_height(h)
    g.fill(x + 1, y + tt, x + tw, y + tt + 1, color)
    g.fill(x, y + tt + 1, x + tw, y + tt + th - 1, color)
    g.fill(x + 1, y + tt + th - 1, x + tw, y + tt + th, color)
    head_shape(g, x, y, x + tw, hw, h, color, 0, h)


def progress_arrow(g, x, y, w, h, ratio):
    if w < 6 or h < 4:
        return
    hw = head_width(w, h)
    tw = max(1, w - hw)
    tt, th = shaft_top(h), shaft_height(h)

    outline(g, x, y + 1, w, h, C["ARROW_SHADOW"])
    outline(g, x, y, w, h, C["ARROW_EDGE"])
    g.fill(x + 1, y + tt + 1, x + tw, y + tt + th - 1, C["ARROW_CHANNEL"])
    g.fill(x + 1, y + tt + 1, x + tw, y + tt + 2, C["ARROW_CHANNEL_TOP"])
    head_shape(g, x, y, x + tw, hw, h, C["ARROW_HEAD"], 0, h, 1)

    span = w - 2
    fill = min(max(int(math.ceil(min(max(ratio, 0.0), 1.0) * span)), 0), span)
    if fill <= 0:
        return
    hi_row = 1 + int(math.ceil((h - 2) * 0.28))
    mid_row = 1 + int(math.ceil((h - 2) * 0.62))
    g.enableScissor(x + 1, y + 1, x + 1 + fill, y + h - 1)
    g.fill(x + 1, y + tt + 1, x + tw, y + tt + th - 1, C["ARROW_FILL_LO"])
    head_shape(g, x, y, x + tw, hw, h, C["ARROW_FILL_LO"], 1, h - 1, 1)
    fill_above(g, x + 1, y + tt + 1, x + tw, y + tt + th - 1, C["ARROW_FILL_MID"], y + mid_row)
    head_shape(g, x, y, x + tw, hw, h, C["ARROW_FILL_MID"], 1, mid_row, 1)
    fill_above(g, x + 1, y + tt + 1, x + tw, y + tt + th - 1, C["ARROW_FILL_HI"], y + hi_row)
    head_shape(g, x, y, x + tw, hw, h, C["ARROW_FILL_HI"], 1, hi_row, 1)
    g.fill(x + 1, y + tt + 1, x + tw, y + tt + 2, C["ARROW_GLINT"])
    g.disableScissor()
    glint_x = x + fill - 1
    if glint_x <= x + tw - 1:
        g.fill(glint_x, y + tt + 1, glint_x + 1, y + tt + th - 1, C["ARROW_GLINT"])
    head_glint(g, y, x + tw, hw, h, glint_x, C["ARROW_GLINT"])


def head_glint(g, y, head_left, head_w, h_ref, col, color):
    """前沿灵光：只在箭尖三角形内部、且正好落在 col 这一列的若干行上落笔。"""
    half = max(0.5, (h_ref - 1) / 2.0)
    for row in range(1, h_ref - 1):
        t = 1.0 - abs(row - (h_ref - 1) / 2.0) / half
        if t <= 0.0:
            continue
        back = head_left + rnd(C["ARROW_NOTCH"] * t) + 1
        front = head_left + rnd(head_w * t) - 1
        if back <= col < front:
            g.fill(col, y + row, col + 1, y + row + 1, color)


# ------------------------------------------------------------------ 拼图
BAR_W, BAR_H = 26, 14          # = 三屏的 BAR_W / BAR_H
RATIOS = (0.0, 0.25, 0.5, 0.75, 1.0)
PAD, ROW_GAP, LEFT = 10, 8, 46


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(HERE, "preview/gui_progress_arrow.png"))
    ap.add_argument("--scale", type=int, default=8)
    a = ap.parse_args()

    row_h = 30
    w = LEFT + BAR_W + 24 + PAD
    h = PAD + len(RATIOS) * (row_h + ROW_GAP) - ROW_GAP + PAD
    g = Canvas(w, h, (28, 28, 34, 255))

    for i, r in enumerate(RATIOS):
        cy = PAD + i * (row_h + ROW_GAP)
        panel(g, PAD, cy, w - 2 * PAD, row_h)
        slot(g, PAD + 8, cy + 6)                       # 输入槽
        slot(g, PAD + 8 + BAR_W + 34, cy + 6)          # 输出槽
        progress_arrow(g, PAD + 8 + 26, cy + 8, BAR_W, BAR_H, r)

    big = g.im.resize((w * a.scale, h * a.scale), Image.NEAREST)
    d = ImageDraw.Draw(big)
    for i, r in enumerate(RATIOS):
        cy = (PAD + i * (row_h + ROW_GAP) + row_h // 2) * a.scale
        d.text((6, cy - 6), "%3d%%" % round(r * 100), fill=(200, 200, 210, 255))
    os.makedirs(os.path.dirname(a.out), exist_ok=True)
    big.save(a.out)
    print("saved:", a.out, big.size)
    print("arrow %dx%d | headWidth=%d tailW=%d shaftTop=%d shaftH=%d notch=%g tailRatio=%g"
          % (BAR_W, BAR_H, head_width(BAR_W, BAR_H), BAR_W - head_width(BAR_W, BAR_H),
             shaft_top(BAR_H), shaft_height(BAR_H), C["ARROW_NOTCH"], D["ARROW_TAIL_RATIO"]))


if __name__ == "__main__":
    main()
