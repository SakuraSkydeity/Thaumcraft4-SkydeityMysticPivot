#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""赤羽焚天瞬核心（focus_teleport）贴图重绘（第九十一批首绘，第九十七批重画「居中 + 闪烁」）。

用户原话（第九十七批）：「focus_teleport 的贴图居中，然后带一点点闪烁的像素感觉」。

两张一起重画（16×16 硬边）：
  * {@code focus_teleport.png}（layer1，物品 / GUI 顶层）：**圆盘星辰核**——
    外圈金环、内夜空紫盘、四臂星芒、四角小星、中心亮星；
  * {@code focus_teleport_orn.png}（layer0 + focus 档案的 ornament_texture，
    贴到法杖帽上的 focus 立方体上）：**圆盘章印**（圆轮廓 + 中心亮星 + 四向圆头星光），
    任何角度贴上去都不会出现竖条观感。

<h2>第九十七批改了两处</h2>
<ol>
  <li><b>居中</b>：上一版几何是绕「整数格 8」画的（{@code int(cx)} 起步、星芒 {@code 8±t}），
      于是不透明像素的包围盒落在 x1~13 / y1~13，<b>中心是 7.0 而不是 7.5</b>
      ——在 16×16 图标里就是整体偏左上半个像素，贴到法杖帽上最看得出来。
      本版一律用<b>连续坐标 8.0</b> 作圆心（像素中心 x+0.5 关于 8.0 镜像 ⇒ 像素下标包围盒
      中心恰为 7.5），四臂星芒也改成**成对**落点（7−t 与 8+t），画完再自检包围盒中心。</li>
  <li><b>一点点闪烁的像素感</b>：静态贴图做「闪」的观感——盘里撒若干
      **亮粒 + 相邻暗晕** 的小星（不对称分布），而不是均匀噪点。</li>
</ol>
"""
import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEM_TEX = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/item")
PREVIEW = os.path.join(ROOT, "tools/preview")

SIZE = 16

# ★ 圆心用连续坐标 8.0：像素中心 (x+0.5) 关于 8.0 镜像 ⇒ 像素下标包围盒中心恰为 7.5。
C = 8.0

# 星辰夜空调色板（硬边）
P = {
    ".": (0, 0, 0, 0),
    "p": (58, 40, 96, 255),      # 夜空紫
    "P": (96, 66, 150, 255),     # 亮紫（受光）
    "g": (140, 108, 46, 255),    # 金 · 暗
    "G": (238, 200, 96, 255),    # 金 · 中
    "h": (255, 236, 160, 255),   # 金 · 亮
    "w": (245, 248, 255, 255),   # 星白
    "b": (150, 190, 255, 255),   # 星蓝
}


def blank():
    return [["." for _ in range(SIZE)] for _ in range(SIZE)]


def put(g, x, y, c):
    if 0 <= x < SIZE and 0 <= y < SIZE:
        g[y][x] = c


def put_pair(g, x, y, c):
    """关于 7.5 的镜像成对落点：一次画 (x, y) 与 (15-x, y)。"""
    put(g, x, y, c)
    put(g, SIZE - 1 - x, y, c)


def render(g):
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = im.load()
    for y in range(SIZE):
        for x in range(SIZE):
            px[x, y] = P[g[y][x]]
    return im


def disc(g, r, c):
    for y in range(SIZE):
        for x in range(SIZE):
            if (x + 0.5 - C) ** 2 + (y + 0.5 - C) ** 2 <= r * r:
                g[y][x] = c


def ring(g, r, c, w=1.0):
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot(x + 0.5 - C, y + 0.5 - C)
            if abs(d - r) < w:
                g[y][x] = c


def cross_rays(g, arm, c_arm, c_tip):
    """四臂星芒：横臂占第 7/8 两行、竖臂占第 7/8 两列，**成对**向外（→ 居中）。"""
    for t in range(1, arm + 1):
        for row in (7, 8):
            put_pair(g, 7 - t, row, c_arm)       # 左臂 7−t ↔ 右臂 8+t
        for col in (7, 8):
            put(g, col, 7 - t, c_arm)
            put(g, col, 8 + t, c_arm)
    t = arm + 1                                  # 尖端加亮：再往外一格
    for row in (7, 8):
        put_pair(g, 7 - t, row, c_tip)
    for col in (7, 8):
        put(g, col, 7 - t, c_tip)
        put(g, col, 8 + t, c_tip)

# ---- 闪烁像素：亮粒 + 相邻暗晕（不对称分布，做出「一点点闪」）----
FLASH = [
    (4, 6, "w"), (5, 6, "g"),
    (11, 9, "w"), (10, 9, "g"),
    (6, 11, "w"), (6, 10, "g"),
    (9, 4, "b"), (9, 3, "g"),
    (3, 9, "b"),
    (12, 6, "w"),
]


def centre_report(name, im):
    px = im.load()
    xs, ys = [], []
    for y in range(SIZE):
        for x in range(SIZE):
            if px[x, y][3] > 0:
                xs.append(x)
                ys.append(y)
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    cx, cy = (x0 + x1) / 2.0, (y0 + y1) / 2.0
    ok = (cx, cy) == (7.5, 7.5)
    print("%-24s bbox x%d~%d y%d~%d  中心 (%.1f, %.1f)  %s"
          % (name, x0, x1, y0, y1, cx, cy, "OK" if ok else "!! 未居中"))
    return ok


def main():
    os.makedirs(PREVIEW, exist_ok=True)
    ok = True

    # ---- focus_teleport：圆盘星辰核（盘占满 13px，居中）----
    g = blank()
    disc(g, 6.6, "p")                       # 夜空盘
    disc(g, 6.6, "P")
    ring(g, 6.2, "h", 0.9)                  # 外金环（亮）
    ring(g, 5.2, "g", 0.9)                  # 内金环（暗）
    # 盘内上半受光
    for y in range(SIZE):
        for x in range(SIZE):
            if y + 0.5 < C - 0.5 and (x + 0.5 - C) ** 2 + (y + 0.5 - C) ** 2 <= 5.0 ** 2 \
                    and g[y][x] in ("p", "P"):
                g[y][x] = "P"
    cross_rays(g, 3, "G", "w")              # 四臂星芒（中金臂、白尖；臂长 3 不压满盘）
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):
        g[y][x] = "w"                       # 中心亮星 2×2
    # 四角小星（盘内 45° 方向的成对四粒，蓝）
    for x, y in ((5, 5), (10, 5), (5, 10), (10, 10)):
        if g[y][x] in ("p", "P"):
            g[y][x] = "b"
    # ★ 闪烁像素
    for (x, y, c) in FLASH:
        put(g, x, y, c)
    im = render(g)
    im.save(os.path.join(ITEM_TEX, "focus_teleport.png"))
    ok &= centre_report("focus_teleport.png", im)

    # ---- focus_teleport_orn：圆盘章印（cap 上不卡模的关键：圆轮廓）----
    g2 = blank()
    disc(g2, 6.0, "G")                      # 金盘底
    disc(g2, 4.8, "p")                      # 内夜空
    disc(g2, 3.4, "P")
    ring(g2, 5.4, "h", 1.0)                 # 盘缘亮金
    # 四向探出的圆头星光（成对落点 → 居中）
    for t in (5, 6):
        for row in (7, 8):
            put_pair(g2, 7 - t, row, "h")
        for col in (7, 8):
            put(g2, col, 7 - t, "h")
            put(g2, col, 8 + t, "h")
    for row in (7, 8):
        put_pair(g2, 2, row, "w")
    put(g2, 7, 2, "w")
    put(g2, 8, 2, "w")
    put(g2, 7, 13, "w")
    put(g2, 8, 13, "w")
    # 中心亮星
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8),
                 (6, 7), (9, 8), (7, 6), (8, 9)):
        g2[y][x] = "w"
    # ★ 闪烁像素（四粒对称，做出「一点点闪」）
    for x, y in ((5, 5), (10, 10)):
        g2[y][x] = "w"
    for x, y in ((10, 5), (5, 10)):
        g2[y][x] = "b"
    im2 = render(g2)
    im2.save(os.path.join(ITEM_TEX, "focus_teleport_orn.png"))
    ok &= centre_report("focus_teleport_orn.png", im2)

    # 双联预览
    cell = SIZE * 20
    canvas = Image.new("RGBA", (cell * 2 + 30, cell + 20), (30, 26, 44, 255))
    canvas.paste(im.resize((cell, cell), Image.NEAREST), (10, 10))
    canvas.paste(im2.resize((cell, cell), Image.NEAREST), (20 + cell, 10))
    canvas.save(os.path.join(PREVIEW, "focus_teleport.png"))
    print("preview -> tools/preview/focus_teleport.png")
    if not ok:
        raise SystemExit("居中的自检没过，别部署")


if __name__ == "__main__":
    main()
