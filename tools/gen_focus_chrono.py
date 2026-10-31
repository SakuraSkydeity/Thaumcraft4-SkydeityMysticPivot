#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""烛龙衔时核心（focus_chrono）的物品图标与杖端饰纹首绘（第一百六十四批）。

用户原话（第一百六十四批）：「添加一个新的法术核心……效果是右键瞄准的方块进行加速……
使用的时候添加一个特效：选取一个tc4的特效引用 然后从玩家发射到目标方块。」
命名取「烛龙衔时」——烛龙视为昼、瞑为夜，嘴里衔着昼夜本身；把它衔住的那一瞬
炼成法核，便是四倍速催动机器。

贴图走钟面主题（与特效选用的本体 destination_wisp_blue 同一套蓝金配色）：

  * 物品图标 focus_chrono.png：外圈亮金包边 + 金环 + 深蓝表盘，
    四道刻度、一长一短两根金针（短针竖直、长针指向四点半——「走快了」），
    亮白金针轴，右上一粒亮蓝火花；
  * 杖端饰纹 focus_chrono_orn.png：与 guardian 饰纹同规格的 14px 外圈，
    圈内是一条深蓝刻度环（四道 2px 亮金刻度），环心深蓝小表盘 + 两根金针——
    远看是一枚「表环」，和 guardian 的双圆环、teleport 的紫曜球都不重样。

配色沿用法杖核心家族既有色（HI/GOLD 取自 guardian，FACE 蓝取自 cosmos 的深蓝一档，
BLUE_HI 取自 teleport 的亮蓝点缀），不引入家族外的新色相。
"""
import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEM_TEX = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/item")
PREVIEW = os.path.join(ROOT, "tools/preview")

SIZE = 16
C = 8.0     # 连续圆心：像素中心 (x+0.5) 关于 8.0 镜像 ⇒ 包围盒中心恰为 7.5

# 家族调色板（全部来自既有核心贴图）
HI = (255, 200, 96, 255)       # 亮金（guardian 外圈同色）
GOLD = (240, 176, 80, 255)     # 金（guardian 内圈同色）
FACE = (38, 52, 96, 255)       # 深蓝表盘（cosmos 深蓝一档）
FACE_LT = (56, 74, 128, 255)   # 表盘提亮一档（出盘面层次）
WHITE = (245, 248, 255, 255)   # 针轴（teleport 高光同色）
BLUE_HI = (150, 190, 255, 255)  # 亮蓝火花（teleport 点缀同色）


def line(px, x0, y0, x1, y1, color):
    """在像素网格上从连续坐标 (x0,y0) 画到 (x1,y1)（Bresenham，1px 宽）。"""
    ax, ay = int(math.floor(x0)), int(math.floor(y0))
    bx, by = int(math.floor(x1)), int(math.floor(y1))
    dx, dy = abs(bx - ax), abs(by - ay)
    sx = 1 if bx >= ax else -1
    sy = 1 if by >= ay else -1
    err = dx - dy
    while True:
        if 0 <= ax < SIZE and 0 <= ay < SIZE:
            px[ax, ay] = color
        if ax == bx and ay == by:
            return
        e2 = 2 * err
        if e2 > -dy:
            err -= dy
            ax += sx
        if e2 < dx:
            err += dx
            ay += sy


def disc(px, r_out, r_in, color):
    """画一个半径 [r_in, r_out) 的实心圆环/圆盘。"""
    for y in range(SIZE):
        for x in range(SIZE):
            d = math.hypot(x + 0.5 - C, y + 0.5 - C)
            if r_in <= d < r_out:
                px[x, y] = color


def thin_ring(px, r, color, half=0.5):
    """画一个 1px 宽的细圆环：像素中心到圆心的距离落在 [r-half, r+half] 就上色。

    <p>为什么不用 `disc(r_out, r_in)` 那种内外半径：16×16 上半径 7.5 的环，
    0.7 宽的内外半径带在某些角度上<b>一个像素中心都踩不到</b>，环会碎成
    一串虚线（第一百六十六批「外环细一点」第一版就碎成了点）。</p>
    <p>按「到曲线的距离 ≤ 0.5px」取像素，等价于把曲线栅格化：
    每个像素中心只要离曲线半格以内就归它，得到的是一圈连着的 8 邻接像素。</p>
    """
    for y in range(SIZE):
        for x in range(SIZE):
            if abs(math.hypot(x + 0.5 - C, y + 0.5 - C) - r) <= half:
                px[x, y] = color


def draw_clock_face(px, face_r, hour_len, minute_len, hub2x2=True):
    """在半径 face_r 的盘面上画四道刻度 + 一长一短两根金针。"""
    # ★ 四道刻度统一落在 d≈4.5 的内圈（原上下两道在 d=5.5，会压到主体金环上糊掉）
    for tx, ty in ((7, 3), (8, 3), (12, 7), (12, 8), (7, 12), (8, 12), (3, 7), (3, 8)):
        if math.hypot(tx + 0.5 - C, ty + 0.5 - C) <= face_r:
            px[tx, ty] = HI                       # 四道刻度
    line(px, C, C, C, C - hour_len, HI)          # 短针：竖直（12 点）
    line(px, C, C, C + minute_len * 0.75, C + minute_len * 0.38, HI)  # 长针：约四点半
    if hub2x2:
        for hx, hy in ((7, 7), (8, 7), (7, 8), (8, 8)):
            px[hx, hy] = WHITE                    # 针轴


def save_with_checks(im, name):
    out = os.path.join(ITEM_TEX, name)
    im.save(out)
    print("written", os.path.relpath(out, ROOT))

    # 自检：包围盒必须居中（图标贴到物品上、饰纹贴到法杖帽上才不歪）
    px = im.load()
    xs, ys = [], []
    for y in range(SIZE):
        for x in range(SIZE):
            if px[x, y][3] > 0:
                xs.append(x)
                ys.append(y)
    cx, cy = (min(xs) + max(xs)) / 2.0, (min(ys) + max(ys)) / 2.0
    status = "OK" if (cx, cy) == (7.5, 7.5) else "!! 未居中"
    print("%s   bbox x%d~%d y%d~%d  中心 (%.1f, %.1f)  %s"
          % (name, min(xs), max(xs), min(ys), max(ys), cx, cy, status))
    if status != "OK":
        raise SystemExit("居中的自检没过，别部署")

    # 预览（20 倍放大，深色底）
    os.makedirs(PREVIEW, exist_ok=True)
    cell = SIZE * 20
    canvas = Image.new("RGBA", (cell + 20, cell + 20), (30, 26, 44, 255))
    # ★ 必须带 mask 合成：直接 paste 会把画布上的深色底一起被透明像素顶掉，
    #   白线饰纹的预览就成了一片白（第一百六十六批踩到）。
    canvas.alpha_composite(im.resize((cell, cell), Image.NEAREST), (10, 10))
    canvas.save(os.path.join(PREVIEW, name))
    print("preview -> tools/preview/%s" % name)


def main():
    # ---------------- 物品图标：钟面法核 ----------------
    icon = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = icon.load()
    # ★ 第一百六十六批（用户：「外面的黄色环细一点 然后和中间的主体钟有一点间隔 透明图层」）：
    #   外圈由 7.9..6.55（1.35 厚）收成 7.9..7.2（0.7 细环），
    #   6.3..7.2 留一圈**透明缝**，主体钟自己从 6.3 起（金环 6.3..5.55 + 表盘 ≤5.65）。
    #   各圈仍然互相咬住（不做 0.1 的缝），只在「外环 + 主体」之间留那条刻意的大缝。
    disc(px, 5.65, 0.0, FACE)     # 深蓝表盘
    thin_ring(px, 5.95, GOLD, 0.55)   # 主体钟的金环（约 1.1px）
    thin_ring(px, 7.55, HI, 0.5)      # 外圈：1px 细金环；6.5~7.0 那道是刻意的透明缝
    # ★ 第一百六十六批「中间部分小一点」：盘心亮面 4.0→3.0，指针也收短
    #   （时针 2.6→2.0、分针 3.6→2.8）——钟面整体缩一圈，深蓝盘面留白多出来。
    disc(px, 3.0, 0.0, FACE_LT)
    draw_clock_face(px, face_r=4.6, hour_len=2.2, minute_len=3.0)
    for sx, sy in ((12, 3), (11, 4), (12, 4), (13, 4), (12, 5)):
        px[sx, sy] = BLUE_HI       # 右上一粒亮蓝火花（十字星）
    save_with_checks(icon, "focus_chrono.png")

    # ---------------- 杖端饰纹：白线时刻（第一百六十六批整体重做） ----------------
    # 用户：「模型不要一个大圆 很丑 模型就一点白色线条」——杖上那一面从此**不画底盘、
    # 不画大圆盘**，只用 1px 白线勾一个钟：一圈细表环 + 时/分两根白针 + 一根蓝秒针 + 白针轴。
    # 背景全透明，贴到法杖帽的 focus 立方体上就是几道浮着的白线。
    orn = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = orn.load()
    # 1px 细表环：半径 5.6、距离带 ±0.5（圆不像扁椭圆那样有高曲率端，带宽 1px 也不会断）
    for oy in range(SIZE):
        for ox in range(SIZE):
            if abs(math.hypot(ox + 0.5 - C, oy + 0.5 - C) - 5.6) <= 0.5:
                px[ox, oy] = WHITE
    # 四道正点刻度（白，1px，落在环内 d≈4.5 处；压在环上的话等于没画）
    for tx, ty in ((7, 3), (8, 3), (12, 7), (12, 8), (7, 12), (8, 12), (3, 7), (3, 8)):
        px[tx, ty] = WHITE
    line(px, C, C, C, C - 2.4, WHITE)                           # 时针：朝上
    line(px, C, C, C + 2.6 * 0.75, C + 2.6 * 0.38, WHITE)       # 分针：四点半
    line(px, C, C, C - 2.0, C + 1.6, BLUE_HI)                   # 秒针：七点半（唯一一点蓝）
    for hx, hy in ((7, 7), (8, 7), (7, 8), (8, 8)):
        px[hx, hy] = WHITE             # 针轴
    save_with_checks(orn, "focus_chrono_orn.png")


if __name__ == "__main__":
    main()
