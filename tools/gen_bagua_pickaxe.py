#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""八卦开山辟地镐（改名后 id = {@code bagua_pickaxe}）物品图标 —— 鎏金 32×32。

用户原话：「八卦镐贴图改为 [465197.png] 主体颜色鎏金 金色 橘黄色 柄稍微暗一点」，
第一百零四批再补一句「你新改的贴图也改为 32x32，不然细节丢失太多了」。

<h2>源图</h2>
<p>{@code slash/texture/465197.png} 是 128×128、8 px 一块的 16×16 像素画（7 个色阶，
从近黑到近白），一张灰度稿——正合适整盘换色。</p>

<h2>怎么换色</h2>
<ul>
  <li><b>镐头</b>（{@code y <= 7} 或 {@code x >= 10}）：暗铜 → 橘金 → 鎏金 → 亮金；</li>
  <li><b>镐柄</b>（左下那条斜带）：同一套色阶<b>整体压暗一档</b>，读作「木柄包金」；</li>
  <li>最暗一阶单独立作描边（近黑深褐），勾住轮廓。</li>
</ul>

<h2>为什么 32×32 而不是把 16 放大</h2>
<p>16×16 直接放大只是四倍方块，谈不上细节。这里按 32 的网格重排：每个原画像素落成
<b>2×2</b>，并对每块的<b>上沿提亮一档、下沿压暗一档</b>——这一步是把「一层平面色」
凿出倒角，32 的分辨率才有意义（先在 16 上换色，再按块做倒角，最后只挑镐头上最亮的
两点提白做刃口反光）。</p>

产出 {@code textures/item/bagua_pickaxe.png}（32×32）。
"""
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "../../slash/texture/465197.png")
DST = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/item/bagua_pickaxe.png")
PREVIEW = os.path.join(ROOT, "tools/preview")

BLOCK = 8          # 源图每块 8px
OUT = 32           # 交付尺寸

OUTLINE = (64, 61, 60)
DEEP = (112, 108, 105)
GRAY_A = (158, 152, 148)
GRAY_B = (160, 154, 150)
GRAY_C = (193, 185, 181)
GRAY_D = (208, 200, 195)
LIGHT = (223, 214, 209)

# 镐头：暗铜 → 橘金 → 鎏金 → 亮金
HEAD_MAP = {
    OUTLINE: (70, 38, 8),
    DEEP: (222, 146, 36),
    GRAY_A: (238, 176, 52),
    GRAY_B: (232, 168, 48),
    GRAY_C: (250, 204, 96),
    GRAY_D: (252, 212, 108),
    LIGHT: (255, 232, 150),
}

# 镐柄：同一套压暗一档
HANDLE_MAP = {
    OUTLINE: (46, 26, 8),
    DEEP: (92, 56, 16),
    GRAY_A: (126, 80, 24),
    GRAY_B: (120, 76, 22),
    GRAY_C: (146, 96, 30),
    GRAY_D: (152, 100, 32),
    LIGHT: (166, 110, 36),
}


def is_handle(x, y):
    """镐柄 = 左下那条斜带（镐头压在右上，右下那截短柄仍算「头」）。"""
    return y >= 8 and x <= 9


def clamp(c):
    return tuple(max(0, min(255, v)) for v in c)


def main():
    if not os.path.exists(SRC):
        raise SystemExit("素材不存在: %s" % SRC)
    big = Image.open(SRC).convert("RGBA")
    if big.size != (16 * BLOCK, 16 * BLOCK):
        big = big.resize((16 * BLOCK, 16 * BLOCK), Image.NEAREST)

    im = Image.new("RGBA", (OUT, OUT), (0, 0, 0, 0))
    dst = im.load()
    src = big.load()
    rng = random.Random(131)

    light_cells = []
    # 先在 32 网格上换色：每个原画像素 = 2×2
    for by in range(16):
        for bx in range(16):
            c = src[bx * BLOCK + BLOCK // 2, by * BLOCK + BLOCK // 2]
            if c[3] < 128:
                continue
            table = HANDLE_MAP if is_handle(bx, by) else HEAD_MAP
            col = table.get(c[:3])
            if col is None:
                lum = (c[0] * 299 + c[1] * 587 + c[2] * 114) // 1000
                col = (min(255, lum + 120), int(lum * 0.62), 20)
            j = rng.randint(-2, 2)
            base = clamp(tuple(v + j for v in col))
            hi = clamp(tuple(v + 12 for v in col))
            lo = clamp(tuple(v - 14 for v in col))
            # 2×2 一块，同色平铺（倒角留到轮廓上统一做，见下）
            for dy in range(2):
                for dx in range(2):
                    dst[bx * 2 + dx, by * 2 + dy] = base + (255,)
            if not is_handle(bx, by) and c[:3] == LIGHT:
                light_cells.append((bx, by))

    # 轮廓倒角：只在「外轮廓」上做——下沿无像素则压暗一格、上沿无像素则提亮一格。
    # 内置块一律保持原色，否则 2×2 的明暗会在整把镐上排成横条。
    snap = im.copy()
    src_px = snap.load()

    def opaque(x, y):
        return 0 <= x < OUT and 0 <= y < OUT and src_px[x, y][3] > 0

    for y in range(OUT):
        for x in range(OUT):
            c = src_px[x, y]
            if c[3] == 0:
                continue
            if not opaque(x, y + 1):
                dst[x, y] = clamp(tuple(v - 26 for v in c[:3])) + (255,)
            elif not opaque(x, y - 1):
                dst[x, y] = clamp(tuple(v + 18 for v in c[:3])) + (255,)

    # 刃口反光：只挑镐头上最亮的两个原画像素，各点一颗亮白
    for (bx, by) in light_cells[-2:]:
        dst[bx * 2, by * 2] = (255, 246, 198, 255)
        dst[bx * 2 + 1, by * 2] = (255, 238, 176, 255)

    os.makedirs(os.path.dirname(DST), exist_ok=True)
    im.save(DST)
    print("->", os.path.relpath(DST, ROOT), im.size)

    cell = OUT * 10
    canvas = Image.new("RGBA", (cell + 20, cell + 20), (26, 22, 38, 255))
    canvas.paste(im.resize((cell, cell), Image.NEAREST), (10, 10))
    os.makedirs(PREVIEW, exist_ok=True)
    canvas.save(os.path.join(PREVIEW, "bagua_pickaxe.png"))
    print("preview ->", os.path.relpath(os.path.join(PREVIEW, "bagua_pickaxe.png"), ROOT))


if __name__ == "__main__":
    main()
