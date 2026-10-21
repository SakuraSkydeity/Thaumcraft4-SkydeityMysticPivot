#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
玄枢锄（xuanshu_hoe）物品贴图 —— 第六十八批 **改深紫 + 星辰**。

用户第 4 条：「锄头的贴图用这个改成主色为深紫色星辰 32x32」（素材 `slash/texture/axe.png`）。

<h2>源图</h2>
`axe.png` 是 128×128、8 px 一块的 **16×16 像素画**（干净四倍放大，逐块核过：块内无杂色）。
形制是一把**锄/镐头在右上、柄斜向左下**的旧骨。

<h2>本版做三件事</h2>
  1. **整盘换色 → 深紫 + 鎏金星辰**：按源图调色板逐色映射——
     主色（原蓝紫 88,76,162 / 48,54,152）→ **深紫**（94,63,158 / 56,36,102）；
     描边（38,34,88）→ 近黑紫；柄上灰蓝（80,82,112 / 120,122,152）→ 暗紫；
     高光（196~242 的淡紫白）→ 更冷的淡紫；
     **原图那几粒洋红挑色 → 鎏金**，正好落在刃口上，读作鎏金边；
  2. **加星辰**：在器身内部的实心主色格上挑 4 处落**纯白星点**（脚本自选：取「四邻都有像素」
     的主色格，按面积四等分点均匀取样，保证落在器物上而不是描边或镂空里）；
  3. **柄末端收细**（沿用第六十六批用户要求）：左下角柄末端原为 3 格平头，抹掉末端
     两行最内侧一格 ⇒ 平头变 3→2→2 的收细尖头。

产出：`textures/item/xuanshu_hoe.png`（32×32 —— 16×16 像素画的二倍，NEAREST 无损）。
"""
import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "../../slash/texture/axe.png")
DST = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/item/xuanshu_hoe.png")
PREVIEW_DIR = os.path.join(ROOT, "tools/preview")

BLOCK = 8          # 源图每块 8px（16×16 像素画）

# ---- 源图调色板 → 玄枢调色板（键就是源图里的精确 RGB 值）----
REMAP = {
    (38, 34, 88):    (24, 18, 40),      # 描边 → 近黑紫
    (48, 54, 152):   (56, 36, 102),     # 深蓝 → 深紫（暗面）
    (88, 76, 162):   (94, 63, 158),     # 主蓝紫 → 主深紫
    (80, 82, 112):   (62, 52, 88),      # 柄暗灰蓝 → 暗紫
    (120, 122, 152): (108, 92, 146),    # 柄亮灰蓝 → 中紫
    (134, 102, 200): (150, 114, 212),   # 亮紫 → 提亮紫
    (196, 172, 222): (180, 154, 232),   # 淡紫高光
    (202, 178, 228): (186, 160, 238),
    (226, 206, 238): (216, 196, 246),
    (238, 218, 248): (228, 210, 252),
    (242, 222, 250): (238, 222, 254),
    (255, 255, 255): (250, 244, 255),   # 源净白 → 星白
    # 洋红挑色 → 鎏金（落在刃口上，读作鎏金边）
    (190, 82, 196):  (238, 192, 96),
    (205, 92, 205):  (244, 206, 116),
    (224, 112, 218): (248, 218, 138),
    (232, 122, 226): (252, 230, 160),
}
STAR = (252, 248, 236)
STAR_DIM = (168, 138, 216)
PURPLE_MAINS = {(94, 63, 158), (56, 36, 102)}   # 可以落星点的主色


def remap(im):
    px = im.load()
    for by in range(16):
        for bx in range(16):
            c = px[bx * BLOCK + 4, by * BLOCK + 4]
            if c[3] < 128:
                continue
            new = REMAP.get(c[:3])
            if new is None:
                continue
            for dy in range(BLOCK):
                for dx in range(BLOCK):
                    px[bx * BLOCK + dx, by * BLOCK + dy] = new + (255,)
    return im


def add_stars(im, want=3, min_gap=7):
    """在 32×32 的成品图上落星点（**降采样之后**再点，才点得出小星）。

    每颗星 = 星心一像素纯白 + 上下左右四像素淡紫。候选点取「四邻都不透明」的主色像素
    （保证落在器物上，不会挂到描边或镂空），并且彼此至少隔 {@code min_gap} 像素。
    """
    px = im.load()
    w, h = im.size

    def op(x, y):
        return 0 <= x < w and 0 <= y < h and px[x, y][3] >= 128

    cands = []
    for y in range(h):
        for x in range(w):
            c = px[x, y]
            if c[3] < 128 or c[:3] not in PURPLE_MAINS:
                continue
            if not all(op(x + dx, y + dy) for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                continue
            cands.append((x, y))

    # 按竖向三等分各挑一颗（避免三颗都挤在扫描时最靠前的同一行里）
    picks = []
    for band in range(want):
        lo = h * band // want
        hi = h * (band + 1) // want
        mid = (lo + hi) / 2.0
        inband = [p for p in cands if lo <= p[1] < hi
                  and all(math.dist(p, q) >= min_gap for q in picks)]
        if inband:
            picks.append(min(inband, key=lambda p: abs(p[1] - mid)))
    for (x, y) in picks:
        for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if op(x + dx, y + dy):
                px[x + dx, y + dy] = STAR_DIM + (255,)
        px[x, y] = STAR + (255,)
    return im, picks


def thin_handle_tip(im):
    """柄末端（左下角）收细：抹掉末端两行最内侧那一格，平头变收细尖头。"""
    px = im.load()
    for (bx, by) in [(2, 14), (2, 15)]:
        for dy in range(BLOCK):
            for dx in range(BLOCK):
                px[bx * BLOCK + dx, by * BLOCK + dy] = (0, 0, 0, 0)
    return im


def main():
    if not os.path.exists(SRC):
        raise SystemExit("素材不存在: %s" % SRC)
    im = Image.open(SRC).convert("RGBA")
    remap(im)
    thin_handle_tip(im)
    small = im.resize((32, 32), Image.NEAREST)      # 先降采样
    _, picks = add_stars(small)                     # 再在成品图上点星
    print("stars at", picks)
    os.makedirs(os.path.dirname(DST), exist_ok=True)
    small.save(DST)
    print("hoe tex ->", os.path.relpath(DST, ROOT), small.size)

    os.makedirs(PREVIEW_DIR, exist_ok=True)
    small.resize((32 * 14, 32 * 14), Image.NEAREST).save(
        os.path.join(PREVIEW_DIR, "xuanshu_hoe.png"))


if __name__ == "__main__":
    main()
