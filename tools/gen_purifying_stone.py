#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
重绘玄枢净界石（未激活态）的方块贴图 textures/block/xuanshu_purifying_stone.png（16x16）。

背景（12.63 批）：
  用户「净界石的方块贴图改一下，美观一点」。旧图只有 6 个色阶的一张「同心方框」，
  平、暗、也看不出「净化」这件事。

新设计（一块刻了符框的青石）：
  * 外圈 1px 深青描边；
  * 内缩 2px 一圈**金框**（磨金 + 上左高光）；
  * 框中是**青石面**：三档青灰噪点（用固定哈希撒点，不是随机，保证可复现）；
  * 正中一口**净化之井**：深青方环 + 中心亮青核；
  * 四向各一枚金点（罗盘四正），四角一枚金角钉。

因为方块是 cube_all（六面同图），纹样做四向对称，从任何一面看都成立。
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BLOCK_TEX = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/block")

EDGE = (0x16, 0x26, 0x2C)        # 最外 1px 描边（近黑青）
GOLD_HI = (0xF2, 0xD8, 0x94)     # 金框高光
GOLD = (0xD9, 0xB4, 0x5E)        # 金框
GOLD_DK = (0xA8, 0x84, 0x3E)     # 金框暗侧
STONE = (0x3A, 0x6A, 0x6C)       # 青石本体
STONE_D = (0x2E, 0x58, 0x5C)     # 青石暗点
STONE_DD = (0x22, 0x44, 0x4A)    # 青石更深点
STONE_L = (0x4A, 0x7C, 0x7E)     # 青石亮点
WELL_WALL = (0x0E, 0x26, 0x30)   # 井壁（最深）
WELL_RIM = (0x86, 0xC0, 0xBE)    # 井口亮圈
WELL_CORE = (0xDC, 0xF8, 0xF2)   # 井心（亮青）


def speckle(x, y):
    """固定哈希撒点：同一坐标永远同一档，图可复现。"""
    h = (x * 7919 + y * 104729 + 1013) % 1024
    if h % 5 == 0:
        return STONE_L
    if h % 7 == 0:
        return STONE_D
    if h % 13 == 0:
        return STONE_DD
    return STONE


def build():
    im = Image.new("RGBA", (16, 16), EDGE + (255,))
    px = im.load()

    def put(x, y, c):
        px[x, y] = c + (255,)

    # ---- 青石面（1..14）：密一点的青灰噪点 + 上左受光 / 下右压暗的斜面 ----
    for y in range(1, 15):
        for x in range(1, 15):
            c = speckle(x, y)
            if x <= 2 or y <= 2:
                c = STONE_L if c is STONE else c
            if x >= 13 or y >= 13:
                c = STONE_D if c is STONE else c
            put(x, y, c)

    # ---- 金框（石面上的 1px 内框）----
    for i in range(2, 14):
        put(i, 2, GOLD_HI)
        put(i, 13, GOLD_DK)
        put(2, i, GOLD_HI)
        put(13, i, GOLD_DK)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        put(x, y, GOLD)

    # ---- 净化之井：亮圈 + 深青方环 + 亮青井心 ----
    for i in range(5, 11):
        put(i, 5, WELL_RIM)
        put(i, 10, WELL_RIM)
        put(5, i, WELL_RIM)
        put(10, i, WELL_RIM)
    for i in range(6, 10):
        put(i, 6, WELL_WALL)
        put(i, 9, WELL_WALL)
        put(6, i, WELL_WALL)
        put(9, i, WELL_WALL)
    for y in range(7, 9):
        for x in range(7, 9):
            put(x, y, WELL_CORE)

    # ---- 四向金点（罗盘四正），落在金框与井圈之间 ----
    for (x, y) in ((7, 3), (8, 3), (7, 12), (8, 12), (3, 7), (3, 8), (12, 7), (12, 8)):
        put(x, y, GOLD_HI)

    return im


def main():
    im = build()
    path = os.path.join(BLOCK_TEX, "xuanshu_purifying_stone.png")
    im.save(path)
    print("texture ->", path, im.size)
    # 放大预览（16x 最近邻），方便肉眼核对
    im.resize((16 * 12, 16 * 12), Image.NEAREST).save(
        os.path.join(ROOT, "tools/preview/purifying_stone_tex.png"))
    print("preview -> tools/preview/purifying_stone_tex.png")


if __name__ == "__main__":
    main()
