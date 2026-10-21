#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
阿卡西记录（akashic_records）物品图标 —— 第七十三批 **摆正（撤掉 45° 斜躺）+ 居中**。

用户原话：
  > 2.阿卡细的贴图摆正中间

演进：
  * 第七十批：用户素材 `412020.png`（**立着**的卷轴）**顺时针转 90° 放平** + 金色提亮；
  * 第七十二批：在放平的基础上**再往左（逆时针）转 45°** 让卷轴斜躺，
    并把中段那条绶带的**紫色加重**；
  * 本批：把那条 45° 斜姿**撤掉**，回到水平放平，并把实心图案**对齐到 16×16 正中**。

<h2>为什么撤掉 45°</h2>
斜躺时图案的外接框几乎吃满画布，视觉重心被垂下的飘带拉向右下，
在物品栏里既不像「摆正」，看着也偏。本批恢复水平姿态，并做一次**包围盒居中**：
取 alpha 实心区的 bbox，按 `(16 - w) // 2` / `(16 - h) // 2` 贴到画布正中。

| | 放平后原样 | 本批（居中） |
| --- | --- | --- |
| 实心 bbox | x 1..15, y 1..11 | x 0..14, y 2..12 |
| 与画布中心的偏差 | 偏上 1.5 px、偏右 0.5 px | ≤ 0.5 px（宽高皆为奇数，无法再细） |

★ 仍是 **16×16**、alpha 只有 0 / 255 两档、NEAREST 采样 ⇒ 不产生半透明边缘。
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "../../slash/texture/412020.png")
DST = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/item/akashic_records.png")
PREVIEW_DIR = os.path.join(ROOT, "tools/preview")

BLOCK = 8                 # 源图每块 8 px（16×16 像素画）
SIZE = 16                 # 成品边长

LIGHTEN = 0.26            # 黄色往白里拉的系数（0 = 不动，1 = 全白）
YELLOW_MARGIN = 20        # 「黄」的判定：b 比 r/g 低多少以上

# 紫色加重：红绿压暗、蓝略提（保持色相不变，只把「浓」度提上去）
PURPLE_R = 0.72
PURPLE_G = 0.56
PURPLE_B = 1.00
PURPLE_B_LIFT = 10        # 蓝通道再加一点点，免得压暗后发灰
PURPLE_MARGIN = 30        # 「紫」的判定：b 比 r/g 高多少以上


def is_yellow(r, g, b):
    return b < r - YELLOW_MARGIN and b < g - YELLOW_MARGIN


def is_purple(r, g, b):
    return b > r + PURPLE_MARGIN and b > g + PURPLE_MARGIN


def lighten_yellow(im):
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a < 128 or not is_yellow(r, g, b):
                continue
            px[x, y] = (
                int(round(r + (255 - r) * LIGHTEN)),
                int(round(g + (255 - g) * LIGHTEN)),
                int(round(b + (255 - b) * LIGHTEN)),
                a,
            )
    return im


def deepen_purple(im):
    px = im.load()
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a < 128 or not is_purple(r, g, b):
                continue
            px[x, y] = (
                min(255, int(round(r * PURPLE_R))),
                min(255, int(round(g * PURPLE_G))),
                min(255, int(round(b * PURPLE_B) + PURPLE_B_LIFT)),
                a,
            )
    return im


def lay_flat(im):
    """立着的卷轴 → 顺时针 90° 放平（第七十批）。"""
    return im.rotate(-90, expand=True, resample=Image.NEAREST)


def center_on_canvas(im):
    """把 alpha 实心区摆到画布正中（第七十三批）。

    只做整体平移，不改任何像素 —— 宽或高为奇数时天然差半个像素，取 floor。
    超出画布的平移量不裁剪，直接重贴空白画布，避免被 PIL 截断。
    """
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
    lighten_yellow(out)
    deepen_purple(out)
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
        os.path.join(PREVIEW_DIR, "akashic_records.png"))


if __name__ == "__main__":
    main()
