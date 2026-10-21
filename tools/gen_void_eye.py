#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""玄枢洞虚灵目（xuanshu_void_eye）物品图标 —— 第八十七批重绘（原版像素画风）。

需求（用户原话）：符合我的世界原版像素画风，为一个斜着的八卦，然后有金链子。

口径落实：
  * **原版像素画风**：硬边、无柔光、无抗锯齿；明暗只走三阶（亮/中/暗）；
    轮廓有不透明深色描边（原版物品如末影之眼、附魔金苹果的通行做法）；
    饱和度明快、贴近原版金/紫配色。第八十三批那版「可爱风」的柔光描边作废。
  * **斜着的八卦**：吊坠是一面**八卦镜**（外环 + 八卦卦位 + 中心太极），
    整盘相对画面**顺时针倾斜**（候选 G 20°、候选 H 38°）——读作挂坠自然歪斜。
  * **金链子**：顶部链扣 + 双股链节（亮金/暗金交替出「节感」）连到镜盘上沿。

实现：逐像素把屏幕坐标**逆旋转**回镜盘局部坐标再判定（圆/环/太极 S 线/卦位钉），
旋转倾角即「斜着」的量。16×16 硬边。

用法：
  python tools/gen_void_eye.py            # 生成候选草稿到 tools/work/eye_draft/
  python tools/gen_void_eye.py --commit   # 用 CHOSEN 版覆盖正式贴图
"""
import math
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DST = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/item/xuanshu_void_eye.png")
DRAFT_DIR = os.path.join(ROOT, "tools/work/eye_draft")

CHOSEN = "G"

SIZE = 16
AXIS = 8.0          # 16 格的正中（x0..x15 格中心关于 8.0 对称）

# 原版风调色板：金三阶 + 紫三阶 + 深描边 + 白。硬边，无中间色。
PALETTE = {
    ".": (0, 0, 0, 0),            # 透明
    "O": (43, 27, 56, 255),       # 描边 · 深紫黑（原版式不透明描边）
    "h": (255, 226, 130, 255),    # 金 · 亮（受光/高光）
    "G": (242, 186, 66, 255),     # 金 · 中
    "g": (176, 124, 32, 255),     # 金 · 暗（背光）
    "P": (196, 138, 255, 255),    # 紫 · 亮
    "p": (140, 82, 208, 255),     # 紫 · 中
    "d": (94, 50, 148, 255),      # 紫 · 暗
    "W": (255, 255, 255, 255),    # 白
}


def blank():
    return [["." for _ in range(SIZE)] for _ in range(SIZE)]


def put(g, x, y, c):
    if 0 <= x < SIZE and 0 <= y < SIZE:
        g[y][x] = c


def render(grid):
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = im.load()
    for y in range(SIZE):
        for x in range(SIZE):
            px[x, y] = PALETTE[grid[y][x]]
    return im


def chain(g, drop_to):
    """金链：顶部链扣 + 双股链节落到 drop_to 行（亮/暗交替出节感）。"""
    put(g, 7, 0, "h")
    put(g, 8, 0, "h")
    put(g, 7, 1, "G")
    put(g, 8, 1, "G")
    # 左股：(6,1) 起向外下斜；右股镜像。末端接 drop_to。
    left = [(6, 1), (5, 2), (4, 3)]
    for i, (x, y) in enumerate(left):
        c = "h" if i % 2 == 0 else "G"
        put(g, x, y, c)
        put(g, 15 - x, y, c)
    # 尾节伸到镜盘上沿（drop_to）
    put(g, 3, drop_to, "g")
    put(g, 12, drop_to, "g")


def bagua_mirror(g, cx, cy, tilt_deg, r_out):
    """斜置八卦镜：外环（深描边 + 金环带三阶明暗 + 8 颗卦位珠）+ 内盘太极。

    逐像素把屏幕坐标逆旋转回镜盘局部坐标判定；tilt_deg 为顺时针倾角。
    盘内太极不画 S 线（16px 小盘里线条切碎鱼形；原版风忌糊，靠外环描边定型）。
    """
    ang = math.radians(tilt_deg)
    ca, sa = math.cos(ang), math.sin(ang)
    r_ring = r_out - 1.05        # 金环带外缘（其内是太极盘）
    r_bead = (r_ring + r_out) / 2 - 0.25   # 卦位珠所在半径（金环带中）
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            # 逆旋转（把屏幕坐标转回未倾斜的盘）
            ux, uy = dx * ca + dy * sa, -dx * sa + dy * ca
            r = math.hypot(ux, uy)
            if r > r_out:
                continue
            if r > r_ring:
                # 外缘 1px 深描边 + 金环带（上亮 / 中 / 下暗，按局部 uy）
                if r > r_out - 0.62:
                    g[y][x] = "O"
                elif uy < -1.6:
                    g[y][x] = "h"
                elif uy > 1.9:
                    g[y][x] = "g"
                else:
                    g[y][x] = "G"
            else:
                # ---- 太极（局部坐标，左金右紫 + 上下小圆 + 细 S 分界）----
                fish = 1.9                       # 小圆半径
                if math.hypot(ux, uy + fish) <= fish:          # 上小鱼 → 金
                    g[y][x] = "h" if uy < -fish else "G"
                elif math.hypot(ux, uy - fish) <= fish:        # 下小鱼 → 紫
                    g[y][x] = "d" if uy > fish else "p"
                elif ux <= 0:
                    g[y][x] = "G" if uy < 0 else "g"
                else:
                    g[y][x] = "p" if uy < 0 else "d"
    # 阴阳鱼眼：上鱼一点紫、下鱼一点金（跟着盘一起斜）
    for (lx, ly, c) in ((0.0, -fish_eye_dy, "d"), (0.0, fish_eye_dy, "h")):
        sx = cx + (lx * ca - ly * sa)
        sy = cy + (lx * sa + ly * ca)
        put(g, int(round(sx)), int(round(sy)), c)
    # 八卦卦位珠：金环带上 8 等分亮珠（45° 起，不对准链子方向）
    for k in range(8):
        a = math.radians(45 * k)
        ux, uy = math.cos(a) * r_bead, math.sin(a) * r_bead
        sx = cx + (ux * ca - uy * sa)
        sy = cy + (ux * sa + uy * ca)
        put(g, int(round(sx)), int(round(sy)), "h")


fish_eye_dy = 1.9   # 鱼眼距盘心（= 小圆半径），模块级常量供 bagua_mirror 用


# ----------------------------------------------------------------------
# 候选 G：金链 + 斜置八卦镜（倾角 20°，盘径中）
# ----------------------------------------------------------------------

def design_g():
    g = blank()
    chain(g, drop_to=4)
    bagua_mirror(g, cx=AXIS, cy=10.1, tilt_deg=20, r_out=4.85)
    return g


# ----------------------------------------------------------------------
# 候选 H：金链 + 斜置八卦镜（倾角 38°，盘略大、重心更低）
# ----------------------------------------------------------------------

def design_h():
    g = blank()
    chain(g, drop_to=4)
    bagua_mirror(g, cx=AXIS, cy=10.4, tilt_deg=38, r_out=5.15)
    return g


DESIGNS = {"G": design_g, "H": design_h}


# ----------------------------------------------------------------------
# 输出
# ----------------------------------------------------------------------

def main():
    commit = "--commit" in sys.argv
    os.makedirs(DRAFT_DIR, exist_ok=True)

    built = {}
    for name, fn in DESIGNS.items():
        g = fn()
        built[name] = g
        render(g).save(os.path.join(DRAFT_DIR, "draft_%s.png" % name))
        print("== design %s ==" % name)
        print("\n".join("".join(row) for row in g))

    # 对比图：旧版 + 两候选，各自 x24 放大并排
    old = Image.open(DST).convert("RGBA")
    cell = SIZE * 24
    gap = 14
    order = ("G", "H")
    canvas = Image.new("RGB", (cell * (len(order) + 1) + gap * len(order), cell + 30), (188, 190, 198))
    dr = ImageDraw.Draw(canvas)
    labels = [("old", old)] + [(n, render(built[n])) for n in order]
    for i, (tag, im) in enumerate(labels):
        x = i * (cell + gap)
        big = im.resize((cell, cell), Image.NEAREST)
        canvas.paste(big, (x, 26), big)
        dr.text((x + 8, 8), tag, fill=(40, 40, 48))
    canvas.save(os.path.join(DRAFT_DIR, "draft_compare.png"))
    print("saved", os.path.join(DRAFT_DIR, "draft_compare.png"))

    if commit:
        render(built[CHOSEN]).save(DST)
        print("COMMITTED design %s -> %s" % (CHOSEN, os.path.relpath(DST, ROOT)))


if __name__ == "__main__":
    main()
