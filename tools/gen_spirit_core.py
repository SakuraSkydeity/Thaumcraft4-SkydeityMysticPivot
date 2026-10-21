#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""玄枢沟灵核心（xuanshu_spirit_core）方块贴图 + 模型（第一百零二批新增）。

用户原话：「添加一个方块：玄枢沟灵核心 贴图就类似仙家的风格 然后这个用玄枢锭等注魔合成
然后作为奥术台注灵台注魔合成的中间方块」。

「仙家风格」取的是：**白玉底 + 鎏金云纹 + 一颗宝珠芯**——不描龙不画凤，
只留玉的冷白、金的温润、云的卷曲。三张 16×16：

  * `_side`：白玉砖面（2 格一道细金线）+ 正中一枚鎏金圆芯（内嵌宝珠），四角云卷；
  * `_top` ：金环 + 宝珠 + 八向金芒（俯视看得见「心」）；
  * `_bottom`：暗一档的玉 + 一圈金环（底面也有交代，不吃亏）。

方块本身是整格立方体（`cube_bottom_top`），无 BE、无状态。
"""
import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot")
BLOCK_TEX = os.path.join(ASSETS, "textures/block")
MODEL_DIR = os.path.join(ASSETS, "models/block")
PREVIEW = os.path.join(ROOT, "tools/preview")

S = 16
NS = "skydeitymysticpivot"

JADE = (196, 220, 208)
JADE_L = (228, 242, 234)
JADE_D = (150, 182, 168)
PEARL = (250, 253, 255)
GOLD = (232, 200, 120)
GOLD_D = (170, 132, 62)
CLOUD = (118, 158, 146)


def base(color, seed, jitter=6):
    rng = random.Random(seed)
    im = Image.new("RGBA", (S, S))
    px = im.load()
    for y in range(S):
        for x in range(S):
            j = rng.randint(-jitter, jitter)
            px[x, y] = tuple(max(0, min(255, c + j)) for c in color) + (255,)
    return im


def rect(px, x0, y0, x1, y1, c):
    for y in range(y0, y1):
        for x in range(x0, x1):
            if 0 <= x < S and 0 <= y < S:
                px[x, y] = c


def ring(px, r, c, w=0.9, cx=8.0, cy=8.0):
    for y in range(S):
        for x in range(S):
            if abs(math.hypot(x + 0.5 - cx, y + 0.5 - cy) - r) < w:
                px[x, y] = c


def disc(px, r, c, cx=8.0, cy=8.0):
    for y in range(S):
        for x in range(S):
            if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                px[x, y] = c


def gen_side():
    im = base(JADE, 201)
    px = im.load()
    # 外描边（暗玉）＋ 一圈细金线
    rect(px, 0, 0, S, 1, JADE_D)
    rect(px, 0, S - 1, S, S, JADE_D)
    rect(px, 0, 0, 1, S, JADE_D)
    rect(px, S - 1, 0, S, S, JADE_D)
    rect(px, 1, 1, S - 1, 2, GOLD_D)
    rect(px, 1, S - 2, S - 1, S - 1, GOLD_D)
    rect(px, 1, 1, 2, S - 1, GOLD_D)
    rect(px, S - 2, 1, S - 1, S - 1, GOLD_D)
    # 玉砖竖缝
    for x in (5, 10):
        for y in range(2, S - 2):
            px[x, y] = JADE_D
    # 砖面上一道浅高光
    for x in (3, 4, 8, 12):
        for y in range(3, 6):
            px[x, y] = JADE_L
    # 正中鎏金圆芯 + 宝珠（缩小一档，留出玉面）
    ring(px, 2.9, GOLD_D, 0.7)
    disc(px, 1.9, GOLD)
    rect(px, 7, 7, 9, 9, PEARL)
    # 四角云卷（只在格心那一点的斜上方留一朵小卷，别糊成噪点）
    for cx, cy in ((4, 4), (11, 4), (4, 11), (11, 11)):
        px[cx, cy] = GOLD_D
        px[cx + (1 if cx < 8 else -1), cy] = GOLD
    return im


def gen_top():
    im = base(JADE_L, 202)
    px = im.load()
    rect(px, 0, 0, S, 1, JADE_D)
    rect(px, 0, S - 1, S, S, JADE_D)
    rect(px, 0, 0, 1, S, JADE_D)
    rect(px, S - 1, 0, S, S, JADE_D)
    # 八向金芒（拉长，俯视才看得见「心」）
    for k in range(8):
        a = math.pi * 2 * k / 8
        for t in (4.6, 5.4, 6.2):
            x = int(round(8.0 + math.cos(a) * t))
            y = int(round(8.0 + math.sin(a) * t))
            if 1 <= x < S - 1 and 1 <= y < S - 1:
                px[x, y] = GOLD if t < 6.0 else GOLD_D
    ring(px, 3.4, GOLD_D, 0.8)
    disc(px, 2.4, GOLD)
    rect(px, 7, 7, 9, 9, PEARL)
    px[6, 7] = PEARL
    px[9, 8] = PEARL
    return im


def gen_bottom():
    im = base(JADE_D, 203)
    px = im.load()
    rect(px, 0, 0, S, 1, CLOUD)
    rect(px, 0, S - 1, S, S, CLOUD)
    rect(px, 0, 0, 1, S, CLOUD)
    rect(px, S - 1, 0, S, S, CLOUD)
    ring(px, 4.6, GOLD_D, 0.8)
    rect(px, 7, 6, 9, 10, GOLD_D)
    rect(px, 6, 7, 10, 9, GOLD_D)
    rect(px, 7, 7, 9, 9, GOLD)
    return im


def main():
    os.makedirs(PREVIEW, exist_ok=True)
    jobs = (
        ("xuanshu_spirit_core", gen_side),
        ("xuanshu_spirit_core_top", gen_top),
        ("xuanshu_spirit_core_bottom", gen_bottom),
    )
    for name, fn in jobs:
        dst = os.path.join(BLOCK_TEX, name + ".png")
        fn().save(dst)
        print("->", os.path.relpath(dst, ROOT))

    model = {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {
            "top": NS + ":block/xuanshu_spirit_core_top",
            "bottom": NS + ":block/xuanshu_spirit_core_bottom",
            "side": NS + ":block/xuanshu_spirit_core",
        },
    }
    path = os.path.join(MODEL_DIR, "xuanshu_spirit_core.json")
    import json
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(model, fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    print("->", os.path.relpath(path, ROOT))

    cell = S * 18
    canvas = Image.new("RGBA", (cell * 3 + 40, cell + 20), (24, 20, 32, 255))
    for i, (name, _) in enumerate(jobs):
        im = Image.open(os.path.join(BLOCK_TEX, name + ".png"))
        canvas.paste(im.resize((cell, cell), Image.NEAREST), (10 + i * (cell + 10), 10))
    canvas.save(os.path.join(PREVIEW, "spirit_core_tex.png"))
    print("preview ->", os.path.relpath(os.path.join(PREVIEW, "spirit_core_tex.png"), ROOT))


if __name__ == "__main__":
    main()
