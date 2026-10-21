#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""台顶辰星贴图（第一百五十批重绘）：32×32 的柔光四芒星。

用户原话：「奥术台和复刻台上面的十字星是贴图吗 还是纯代码 改大一点 好看一点」。

答案：**既是贴图也是代码**——星形本身是这张 16×16 的白字贴图，
三片交叉的零厚度四边形由 `XuanshuArcaneTableRenderer` / `XuanshuReplicatorRenderer`
在台顶画出来，颜色走顶点色（奥术台鎏金、复刻台紫）。

第一百四十八批那版是「四条长细光刺 + 一个点」，16×16 逐像素画出来，
放大到 0.48 格之后边缘全是硬台阶，所以显小、显糙。这一版：

  · 分辨率 16 → **32**（每像素只有原来一半大，臂的收窄是平滑的）；
  · 四条主臂按「高斯横向衰减 × 沿臂幂次衰减」连续算，没有逐像素的锯齿；
  · 加一层**圆形软光晕**（很淡，α≈0.28），星看着有光，不是四个三角片；
  · 加四条**对角短芒**（0.42 强度），俯仰角度看也有星味；
  · 中心留一枚更亮的芯（α 拉满），夜里透出来是一点白热。

贴图是纯白 + alpha：颜色交给顶点色，一台金黄一台紫，不必各出一张。
"""
import os
import math

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BLOCK_TEX = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/block")
PREVIEW = os.path.join(ROOT, "tools/preview")

SIZE = 32
CENTER = (SIZE - 1) / 2.0     # 15.5：星心落在四个像素的交点上，几何居中

# 主臂：横向半宽与长度
ARM_WIDTH = 1.15
ARM_LEN = 16.0
# 对角短芒：更细更短更淡
DIAG_WIDTH = 0.72
DIAG_LEN = 10.5
DIAG_WEIGHT = 0.42
# 中心芯：小范围拉满
CORE_SIGMA = 1.9
# 软光晕：大范围、很低
HALO_SIGMA = 6.4
HALO_WEIGHT = 0.30


def star_alpha(x, y):
    dx = x - CENTER
    dy = y - CENTER
    r = math.hypot(dx, dy)

    # 四条主臂（横竖）：取「沿臂 / 垂直臂」两分量
    along = max(abs(dx), abs(dy))
    perp = min(abs(dx), abs(dy))
    arm = math.exp(-(perp / ARM_WIDTH) ** 2) * max(0.0, 1.0 - along / ARM_LEN) ** 1.5

    # 四条对角短芒：把坐标转 45° 再算一次
    u = (dx + dy) / math.sqrt(2.0)
    v = (dx - dy) / math.sqrt(2.0)
    d_along = max(abs(u), abs(v))
    d_perp = min(abs(u), abs(v))
    diag = math.exp(-(d_perp / DIAG_WIDTH) ** 2) * max(0.0, 1.0 - d_along / DIAG_LEN) ** 1.6
    diag *= DIAG_WEIGHT

    core = math.exp(-(r / CORE_SIGMA) ** 2) * 1.05
    halo = math.exp(-(r / HALO_SIGMA) ** 2) * HALO_WEIGHT

    # 加法叠（与游戏里的加法混合同义：哪里都有贡献，越叠越亮）
    v_sum = arm + diag + core + halo
    return max(0.0, min(1.0, v_sum))


def build():
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = im.load()
    for y in range(SIZE):
        for x in range(SIZE):
            a = star_alpha(x, y)
            px[x, y] = (255, 255, 255, int(round(a * 255)))
    return im


def main():
    os.makedirs(PREVIEW, exist_ok=True)
    im = build()
    for name in ("xuanshu_arcane_star", "xuanshu_replicator_star"):
        dst = os.path.join(BLOCK_TEX, name + ".png")
        im.save(dst)
        print("->", os.path.relpath(dst, ROOT))

    # 自检：不透明像素包围盒应关于 15.5 居中，且四臂延伸到边
    xs, ys = [], []
    for y in range(SIZE):
        for x in range(SIZE):
            if im.getpixel((x, y))[3] > 8:
                xs.append(x)
                ys.append(y)
    print("bbox x %d~%d (中心 %.1f)  y %d~%d (中心 %.1f)"
          % (min(xs), max(xs), (min(xs) + max(xs)) / 2.0,
             min(ys), max(ys), (min(ys) + max(ys)) / 2.0))

    # 预览：深底 + 放大 12 倍，另附一张「按顶点色染金 / 染紫」的对照
    scale = 12
    cell = SIZE * scale
    canvas = Image.new("RGBA", (cell * 3 + 40, cell + 20), (26, 22, 34, 255))
    canvas.alpha_composite(im.resize((cell, cell), Image.LANCZOS), (10, 10))
    for i, tint in enumerate(((255, 205, 55), (180, 85, 255))):
        layer = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
        lp = layer.load()
        for y in range(SIZE):
            for x in range(SIZE):
                a = im.getpixel((x, y))[3]
                lp[x, y] = (tint[0], tint[1], tint[2], a)
        canvas.alpha_composite(layer.resize((cell, cell), Image.LANCZOS),
                               (20 + cell + i * (cell + 10), 10))
    out = os.path.join(PREVIEW, "table_star.png")
    canvas.save(out)
    print("preview ->", os.path.relpath(out, ROOT))


if __name__ == "__main__":
    main()
