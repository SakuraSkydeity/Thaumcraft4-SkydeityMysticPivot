#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""玄枢奥术台贴图（第九十一批）：附魔台样貌 + **星辰玄幻感**。

用户原话：「玄枢奥术台给贴图增加一点星辰玄幻感觉」。

在**原版附魔台贴图的结构**上重绘（保留符文环 / 边框 / 裂纹的构图，认得出仍是附魔台）：
  * 整体压成**暗夜蓝紫**（原版羊皮纸米色 → 夜空底）；
  * 撒**星辰**：随机白 / 淡蓝小点，四角与符文环外侧更密；
  * 边框与符文描**鎏金线**（原版黑线 → 金线）；
  * 顶盖中央一粒大「星核」（十字亮星）。
16×16 硬边，三张（top / side / bottom）。
"""
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BLOCK_TEX = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/block")
PREVIEW = os.path.join(ROOT, "tools/preview")

SIZE = 16

# 星辰夜空调色板（硬边）
# ★ 第九十二批整体提亮：方块模型的面有 shade（up 1.0 / side 0.8·0.6 / down 0.5），
#   上一版夜空底（26,20,48）过暗，乘上 shade 后侧面近黑、游戏里读成「一张黑块」。
NIGHT_DEEP = (48, 36, 88)      # 夜空深紫（提亮）
NIGHT_MID = (72, 54, 124)      # 夜空中紫
NIGHT_LIT = (104, 78, 162)     # 夜空亮紫（受光）
STAR_WHITE = (245, 248, 255)
STAR_BLUE = (170, 205, 255)
GOLD = (248, 220, 128)
GOLD_DIM = (178, 140, 66)


def stars(count, seed, x0=0, y0=0, x1=SIZE, y1=SIZE):
    """撒星：大部 1px 白点，少量 2px 亮星（十字芯）。"""
    rng = random.Random(seed)
    out = {}
    for _ in range(count):
        x = rng.randint(x0, x1 - 1)
        y = rng.randint(y0, y1 - 1)
        out[(x, y)] = STAR_WHITE if rng.random() < 0.7 else STAR_BLUE
    for _ in range(max(1, count // 6)):
        x = rng.randint(x0 + 1, x1 - 2)
        y = rng.randint(y0 + 1, y1 - 2)
        for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
            out[(x + dx, y + dy)] = STAR_WHITE
    return out


def night_base(seed, lit_from_top=True):
    """夜空底：上亮下深的缓变 + 噪点。"""
    rng = random.Random(seed)
    im = Image.new("RGBA", (SIZE, SIZE))
    px = im.load()
    for y in range(SIZE):
        t = y / (SIZE - 1)
        for x in range(SIZE):
            base = tuple(int(NIGHT_LIT[i] * (1 - t) + NIGHT_DEEP[i] * t) for i in range(3))
            j = rng.randint(-6, 6)
            px[x, y] = tuple(max(0, min(255, c + j)) for c in base) + (255,)
    return im


def gen_top():
    """顶盖：符文圆环（金线）+ 环心大星核。"""
    im = night_base(101)
    px = im.load()
    cx, cy, r = 7.5, 7.5, 5.6
    for y in range(SIZE):
        for x in range(SIZE):
            d = ((x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2) ** 0.5
            if abs(d - r) < 0.9:                      # 符文环（金）
                px[x, y] = GOLD
            elif abs(d - r) < 1.9:                    # 环外晕
                px[x, y] = GOLD_DIM
    # 环心的大星核（十字 + 四斜芒）
    for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1), (2, 0), (-2, 0), (0, 2), (0, -2)):
        px[int(cx) + dx, int(cy) + dy] = STAR_WHITE
    for x, y in ((6, 6), (9, 6), (6, 9), (9, 9)):     # 四角斜芒
        px[x, y] = STAR_BLUE
    for k, v in stars(14, seed=102).items():
        px[k] = v
    # 外框金线
    for i in range(SIZE):
        px[i, 0] = GOLD
        px[i, SIZE - 1] = GOLD
        px[0, i] = GOLD
        px[SIZE - 1, i] = GOLD
    return im


def gen_side():
    """侧壁：附魔台式的横带（金线分上下带）+ 带中符文方 + 竖排星辰。"""
    im = night_base(103, lit_from_top=False)
    px = im.load()
    # 横带：顶部一条鎏金箍 + 下部符文方（附魔台 side 的构图）
    for i in range(SIZE):
        px[i, 3] = GOLD
        px[i, 4] = GOLD_DIM
        px[i, 10] = GOLD_DIM
        px[i, 11] = GOLD
    # 带中的「符文方」：中央暗底 + 金框
    for y in range(5, 10):
        for x in range(3, 13):
            px[x, y] = NIGHT_DEEP if (x + y) % 2 == 0 else NIGHT_MID
    for x in range(3, 13):
        px[x, 5] = GOLD_DIM
        px[x, 9] = GOLD_DIM
    for y in range(5, 10):
        px[3, y] = GOLD_DIM
        px[12, y] = GOLD_DIM
    # 符文方中央两枚「目」纹（附魔台 side 的方孔）
    for gx in (5, 6, 9, 10):
        for gy in (7, 8):
            px[gx, gy] = NIGHT_LIT
    for k, v in stars(10, seed=104).items():
        if v:
            px[k] = v
    # 左右竖边各一列星点
    for y in range(12, 16):
        px[1, y] = STAR_BLUE if y % 2 == 0 else STAR_WHITE
        px[14, y] = STAR_BLUE if y % 3 == 0 else STAR_WHITE
    for i in range(SIZE):
        px[i, 0] = GOLD_DIM
        px[i, SIZE - 1] = GOLD_DIM
        px[0, i] = NIGHT_DEEP
        px[SIZE - 1, i] = NIGHT_DEEP
    return im


def gen_bottom():
    """底盖：暗夜 + 稀疏星尘。"""
    im = night_base(105, lit_from_top=False)
    px = im.load()
    for k, v in stars(8, seed=106).items():
        px[k] = v
    for i in range(SIZE):
        px[i, 0] = GOLD_DIM
        px[i, SIZE - 1] = GOLD_DIM
        px[0, i] = GOLD_DIM
        px[SIZE - 1, i] = GOLD_DIM
    return im


def main():
    os.makedirs(PREVIEW, exist_ok=True)
    jobs = (("xuanshu_arcane_table_top", gen_top),
            ("xuanshu_arcane_table_side", gen_side),
            ("xuanshu_arcane_table_bottom", gen_bottom))
    for name, fn in jobs:
        im = fn()
        im.save(os.path.join(BLOCK_TEX, name + ".png"))
        print("->", name)
    # 三联预览（三张并排放大 20 倍）
    cell = SIZE * 20
    canvas = Image.new("RGBA", (cell * 3 + 40, cell + 20), (20, 16, 34, 255))
    for i, (name, _) in enumerate(jobs):
        im = Image.open(os.path.join(BLOCK_TEX, name + ".png"))
        canvas.paste(im.resize((cell, cell), Image.NEAREST), (10 + i * (cell + 10), 10))
    canvas.save(os.path.join(PREVIEW, "arcane_table_tex.png"))
    print("preview:", os.path.join(PREVIEW, "arcane_table_tex.png"))


if __name__ == "__main__":
    main()
