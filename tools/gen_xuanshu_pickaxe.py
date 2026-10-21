#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""玄枢翎凰焚天镐（改名后 id = {@code xuanshu_pickaxe}）物品图标 —— 32×32 原稿。

第一百零四批：用户要把「新鎏金图」给**八卦开山辟地镐**（改名后 {@code bagua_pickaxe}），
而焚天镐的图**改回原来的**、并按「细节别丢」提到 **32×32**。
原 16×16 那版是从 {@code slash/texture/pickaxe.png} 缩下来的；这里直接从源图
（128×128、8 px 一块 ⇒ 16×16 像素画）按 **BOX 面积平均**缩到 32×32：
一对 4×4 源块正好落进一个输出像素，**颜色零损失**，成图就是原画的两倍像素网格。
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "../../slash/texture/pickaxe.png")
DST = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/item/xuanshu_pickaxe.png")
PREVIEW = os.path.join(ROOT, "tools/preview")


def main():
    if not os.path.exists(SRC):
        raise SystemExit("素材不存在: %s" % SRC)
    im = Image.open(SRC).convert("RGBA")
    small = im.resize((32, 32), Image.BOX)
    os.makedirs(os.path.dirname(DST), exist_ok=True)
    small.save(DST)
    print("->", os.path.relpath(DST, ROOT), small.size)

    cell = 32 * 10
    canvas = Image.new("RGBA", (cell + 20, cell + 20), (26, 22, 38, 255))
    canvas.paste(small.resize((cell, cell), Image.NEAREST), (10, 10))
    os.makedirs(PREVIEW, exist_ok=True)
    canvas.save(os.path.join(PREVIEW, "xuanshu_pickaxe.png"))
    print("preview ->", os.path.relpath(os.path.join(PREVIEW, "xuanshu_pickaxe.png"), ROOT))


if __name__ == "__main__":
    main()
