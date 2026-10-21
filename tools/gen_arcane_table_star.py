#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""玄枢奥术台「漂浮星」贴图（第九十七批）。

用户原话：「玄枢奥术台：删掉中间的柱状，改为一个漂浮的星星」。

原「星核」是一根 2×5×2 的**五棱晶柱**（第九十六批），本批整根删掉，改成台上悬着
一枚**四芒星**：三张零厚度的**星面**（两竖一横，互相正交）用 `render_type: cutout`
剔除透明像素，于是星是「星形轮廓」而不是一块画着星的板子——与本体花草同一套做法，
最稳。几何在 `models/block/xuanshu_arcane_table.json` 里手写，这里只出这张 16×16。

贴图：透明底 + 四芒星（金白渐层），四角撒几粒**闪烁像素**（亮粒 + 暗晕相邻，
静态做出「闪」的观感）。
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BLOCK_TEX = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/block")
PREVIEW = os.path.join(ROOT, "tools/preview")

SIZE = 16

P = {
    ".": (0, 0, 0, 0),           # 透空（cutout 剔掉）
    "w": (255, 252, 242, 255),   # 星白（芯）
    "h": (255, 226, 146, 255),   # 淡金
    "G": (238, 192, 92, 255),    # 金
    "g": (168, 126, 58, 255),    # 暗金（晕）
    "b": (208, 226, 255, 255),   # 星蓝白（尖端）
}

# 四芒星：上下 / 左右各一臂（臂宽 2，尖端收成 2px）+ 中央菱形芯。
# 逐行镜像（y -> 15-y）保证几何居中于 7.5/7.5。
STAR_HALF = [
    ".......ww.......",
    ".......hh.......",
    ".......hh.......",
    ".......hh.......",
    ".......hh.......",
    "......ghhg......",
    ".....ghhhhg.....",
    "..bGhhwwwwhhGb..",
]


def expand():
    grid = [list(r) for r in STAR_HALF]
    # 大写 W 只是占位，统一成星白 w
    for row in grid:
        for i, c in enumerate(row):
            if c == "W":
                row[i] = "w"
    # 下半镜像
    for r in reversed(grid):
        grid.append(list(r))
    assert len(grid) == SIZE and all(len(r) == SIZE for r in grid), "网格尺寸不对"
    return grid


# 闪烁像素：亮粒 + 相邻暗晕（错落分布，静态做出「一点点闪」的观感）
TWINKLES = [((3, 5), "w"), ((2, 5), "g"),
            ((12, 10), "w"), ((13, 10), "g"),
            ((11, 3), "b"), ((11, 2), "g"),
            ((5, 12), "w"), ((4, 12), "g")]


def main():
    os.makedirs(PREVIEW, exist_ok=True)
    grid = expand()
    for (x, y), c in TWINKLES:
        if grid[y][x] == ".":
            grid[y][x] = c

    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = im.load()
    for y in range(SIZE):
        for x in range(SIZE):
            px[x, y] = P[grid[y][x]]

    dst = os.path.join(BLOCK_TEX, "xuanshu_arcane_star.png")
    im.save(dst)
    print("->", os.path.relpath(dst, ROOT))

    # 居中自检：不透明像素的包围盒中心应落在 7.5
    xs, ys = [], []
    for y in range(SIZE):
        for x in range(SIZE):
            if px[x, y][3] > 0:
                xs.append(x)
                ys.append(y)
    print("bbox x %d~%d (中心 %.2f)  y %d~%d (中心 %.2f)  px %d"
          % (min(xs), max(xs), (min(xs) + max(xs)) / 2.0,
             min(ys), max(ys), (min(ys) + max(ys)) / 2.0, len(xs)))

    # 预览：棋盘底 + 放大 20 倍
    cell = SIZE * 20
    canvas = Image.new("RGBA", (cell + 20, cell + 20), (60, 52, 92, 255))
    for y in range(SIZE):
        for x in range(SIZE):
            if (x + y) % 2 == 0:
                for dy in range(20):
                    for dx in range(20):
                        canvas.putpixel((10 + x * 20 + dx, 10 + y * 20 + dy), (36, 30, 62, 255))
    canvas.alpha_composite(im.resize((cell, cell), Image.NEAREST), (10, 10))
    canvas.save(os.path.join(PREVIEW, "arcane_table_star.png"))
    print("preview ->", os.path.relpath(os.path.join(PREVIEW, "arcane_table_star.png"), ROOT))


if __name__ == "__main__":
    main()
