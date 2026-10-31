#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""乾元罡气核心（focus_guardian）贴在法杖上的装饰贴图重绘（第一百六十五批）。

用户原话（第一百六十五批）：「给focus_guardian在法杖上的模型添加两个圆环
我想要的是类似木星环一样横着的 两个 倾斜不同角度 不要想现在这样贴图」。

上一版（第一百六十三批）画的是**同心双圆环**（外圈 14px + 内圈 10px）——
用户要的不是这个：要**木星环那样横放着的两条扁环，各自倾斜不同角度**。
本版按木星环重画（16×16 硬边）：

  * 环一：rx=7.4、ry=3.0 的**扁椭圆环**，倾斜 **-16°**，亮金（HI）；
  * 环二：rx=6.4、ry=2.4 的**扁椭圆环**，倾斜 **+22°**，金（GOLD）；
  * 两环都从**核心后面**过（核心最后画，压住环的内弧）——木星环正是
    「环从行星背后穿过、只露外侧两段」的观感；
  * 核心仍是乾元罡气的棕曜核 + 2×2 亮金「心火」（配色取自
    {@code focus_guardian.png} 既有像素，不引入新色）。

居中自检为什么必然过得去：扁环无论怎么转，都是**关于连续圆心 (8.0, 8.0)
中心对称**的图形；而像素 (x, y) 关于 (7.5, 7.5) 的镜像 (15-x, 15-y)，
其像素中心 (15.5-x, 15.5-y) 恰是 (x+0.5, y+0.5) 关于 8.0 的镜像——
所以不透明像素的包围盒中心必是 (7.5, 7.5)，与倾斜角无关。
"""
import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEM_TEX = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/item")
PREVIEW = os.path.join(ROOT, "tools/preview")

SIZE = 16
C = 8.0     # 连续圆心：像素中心 (x+0.5) 关于 8.0 镜像 ⇒ 包围盒中心恰为 7.5

# 乾元罡气调色板（全部来自 focus_guardian.png 既有像素）
HI = (255, 200, 96, 255)      # 亮金
GOLD = (240, 176, 80, 255)    # 金
BROWN = (122, 78, 20, 255)    # 棕曜核心
BROWN_DK = (102, 68, 21, 255)  # 核心暗心


def tilted_ring(px, rx, ry, theta_deg, color, half_width=0.55):
    """画一条倾斜的扁椭圆环，环宽按**像素**算（半宽 half_width 像素）。

    <p>把像素坐标逆旋转 θ 后在椭圆局部坐标里算 {@code k = hypot(u/rx, v/ry)}
    （k=1 是椭圆本身），再用 {@code |∇k|} 把 {@code |k-1|} 折算成<b>到曲线的
    像素距离</b>：{@code dist ≈ |k-1| / |∇k|}。环带取 {@code dist ≤ half_width}，
    长轴、短轴的环宽就是同一个像素数。</p>

    <p>这一版踩过的三个坑，都记在这儿：<br>
    ① 按「k 的带宽」判环（第一百六十五批）：扁环短轴处同一带宽折算成竖直方向
    不足 1px，环断成一串虚线弧；长轴处又厚到 3px，用户嫌粗。<br>
    ② 只画 1px 的「同心缩小两圈」：长轴端两圈的间距比短轴端大得多，环时粗时细，
    还容易在短轴处并成一坨。<br>
    ③ 把扁轴压到 1.1~1.2px 做「近乎侧视」：环成了贴着球皮的两片小翅膀，
    根本看不出是环。<br>
    所以：形状沿用第一百六十五批（用户只嫌粗、没嫌形状），只把环宽按像素压到 1.1px。</p>
    """
    th = math.radians(theta_deg)
    ct, st = math.cos(th), math.sin(th)
    for y in range(SIZE):
        for x in range(SIZE):
            dx, dy = x + 0.5 - C, y + 0.5 - C
            u = dx * ct + dy * st
            v = -dx * st + dy * ct
            k = math.hypot(u / rx, v / ry)
            if k <= 1.0e-6:
                continue
            grad = math.hypot(u / (rx * rx), v / (ry * ry)) / k
            if grad <= 1.0e-6:
                continue
            if abs(k - 1.0) / grad <= half_width:
                px[x, y] = color


def disc(px, r, color):
    for y in range(SIZE):
        for x in range(SIZE):
            if math.hypot(x + 0.5 - C, y + 0.5 - C) <= r:
                px[x, y] = color


def main():
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = im.load()

    # 两条木星环：一亮金、一金，倾斜角一负一正，都横着压扁
    # ★ 用户第一百六十六批「现在的太粗了 细一点」：环从「带」改成**1px 细线**
    #   （沿椭圆参数角采样，见 tilted_ring 的注释）。
    # ★ 环宽按像素给（half_width=0.55 ⇒ 约 1.1px 细线）：长轴短轴一样细，
    #   不再出现「长轴 3px、短轴 0.7px」的粗带子（用户：「现在的太粗了 细一点」）。
    # ★ 扁轴放大到 3.8 / 3.0（核心 r 2.6）：环的上下弧要明显浮在球的轮廓之外，
    #   才看得出是「绕着球的环」。上一版 ry 只有 2.5/2.2、比核心还小，
    #   弧线全被球吃掉、只剩两侧几粒方块，读起来不像环。
    tilted_ring(px, rx=7.4, ry=3.8, theta_deg=-14.0, color=HI, half_width=0.55)
    tilted_ring(px, rx=6.0, ry=3.0, theta_deg=26.0, color=GOLD, half_width=0.55)

    # 核心最后画：环从球背后穿过，两侧只露出探出的那两段
    disc(px, 2.6, BROWN)
    disc(px, 1.5, BROWN_DK)
    for x, y in ((7, 7), (8, 7), (7, 8), (8, 8)):
        px[x, y] = HI                    # 心火 2×2

    out = os.path.join(ITEM_TEX, "focus_guardian_orn.png")
    im.save(out)
    print("written", os.path.relpath(out, ROOT))

    # 自检：包围盒必须居中（贴到法杖帽上才不歪）
    xs, ys = [], []
    for y in range(SIZE):
        for x in range(SIZE):
            if px[x, y][3] > 0:
                xs.append(x)
                ys.append(y)
    cx, cy = (min(xs) + max(xs)) / 2.0, (min(ys) + max(ys)) / 2.0
    print("focus_guardian_orn.png   bbox x%d~%d y%d~%d  中心 (%.1f, %.1f)  %s"
          % (min(xs), max(xs), min(ys), max(ys), cx, cy,
             "OK" if (cx, cy) == (7.5, 7.5) else "!! 未居中"))
    if (cx, cy) != (7.5, 7.5):
        raise SystemExit("居中的自检没过，别部署")

    os.makedirs(PREVIEW, exist_ok=True)
    cell = SIZE * 20
    canvas = Image.new("RGBA", (cell + 20, cell + 20), (30, 26, 44, 255))
    canvas.paste(im.resize((cell, cell), Image.NEAREST), (10, 10))
    canvas.save(os.path.join(PREVIEW, "focus_guardian_orn.png"))
    print("preview -> tools/preview/focus_guardian_orn.png")


if __name__ == "__main__":
    main()
