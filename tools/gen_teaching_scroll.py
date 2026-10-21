#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
授业卷轴（teaching_scroll）物品图标 —— 第七十三批 **与阿卡西记录同步摆正 + 居中**。

用户原话（本批只点了阿卡西，但两件「纸卷」第七十二批就是同一姿态，必须同改）：
  > 2.阿卡细的贴图摆正中间

演进：
  * 第七十二批新增：`rotate(-90)` 放平 + 往左 `rotate(45)` 斜躺（与阿卡西同一姿态），
    中段绶带由深褐改橘黄；
  * 本批：撤掉 45° 斜姿，回到水平放平，并按 alpha 包围盒**居中到 16×16 正中**。

★ 仍是 **16×16**、alpha 只有 0 / 255 两档、NEAREST 采样 ⇒ 不产生半透明边缘。
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "../../slash/texture/164536.png")
DST = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/item/teaching_scroll.png")
PREVIEW_DIR = os.path.join(ROOT, "tools/preview")

BLOCK = 8                 # 源图每块 8 px（16×16 像素画）
SIZE = 16                 # 成品边长

# 绶带（深褐）→ 橘黄。参数用**红通道**，两端就是色阶的明暗端。
BROWN_MAX_R = 180         # 红通道低于此值 + r>g>b ⇒ 判为绶带（纸身的淡金红通道 ≥ 188）
BROWN_R0, BROWN_R1 = 118, 164
ORANGE_DEEP = (198, 104, 20)
ORANGE_LIGHT = (255, 190, 86)


def is_band(r, g, b):
    return r < BROWN_MAX_R and r > g > b


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def orangeize(im):
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a < 128 or not is_band(r, g, b):
                continue
            t = (r - BROWN_R0) / float(BROWN_R1 - BROWN_R0)
            t = 0.0 if t < 0.0 else (1.0 if t > 1.0 else t)
            px[x, y] = mix(ORANGE_DEEP, ORANGE_LIGHT, t) + (a,)
    return im


def lay_flat(im):
    """立着的卷轴 → 顺时针 90° 放平。"""
    return im.rotate(-90, expand=True, resample=Image.NEAREST)


def center_on_canvas(im):
    """把 alpha 实心区摆到画布正中（第七十三批，与阿卡西记录同一套）。"""
    bb = im.getchannel("A").getbbox()
    if not bb:
        return im
    w, h = bb[2] - bb[0], bb[3] - bb[1]
    canvas = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    canvas.paste(im.crop(bb), ((SIZE - w) // 2, (SIZE - h) // 2))
    return canvas


def build():
    if not os.path.exists(SRC):
        raise SystemExit("素材不存在: %s" % SRC)
    src = Image.open(SRC).convert("RGBA")
    if src.size != (SIZE * BLOCK, SIZE * BLOCK):
        raise SystemExit("源图应为 %dpx：%s" % (SIZE * BLOCK, src.size))
    small = src.resize((SIZE, SIZE), Image.NEAREST)
    out = lay_flat(small)
    orangeize(out)
    return center_on_canvas(out)


def main():
    im = build()
    im.save(DST)
    print("item tex ->", os.path.relpath(DST, ROOT), im.size)

    filled = sum(1 for y in range(SIZE) for x in range(SIZE) if im.getpixel((x, y))[3] > 0)
    print("不透明像素:", filled, "/ 256")
    print("实心 bbox :", im.getchannel("A").getbbox())

    os.makedirs(PREVIEW_DIR, exist_ok=True)
    im.resize((SIZE * 16, SIZE * 16), Image.NEAREST).save(
        os.path.join(PREVIEW_DIR, "teaching_scroll.png"))


if __name__ == "__main__":
    main()
