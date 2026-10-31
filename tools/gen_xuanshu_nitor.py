#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
玄枢耀光（第二百一十批新增）物品贴图。

<h2>用户口径</h2>

「添加一个新的物品 玄枢耀光 颜色用 tc4 闪耀之光改，改为紫色带一点星辰颜色」。

所以结构一点不动 —— 直接取本体 `thaumcraft:nitor`（闪耀之光）那张 16×16 火焰，
**只换颜色**：原来的棕红 → 橙 → 黄 → 白，映到一条**紫到近白的紫色带**上，
再让几颗**飞散的火星**改用星辰色（淡青白）——「紫色带一点星辰」就是这一点。

<h2>为什么参考图放在 tools/ref</h2>

本体的 nitor.png 只有 678 B，摊在 jar 里每次构建去解包反而更脆。放一份在
`tools/ref/tc4_nitor.png`（与 `tools/schematics/*.schem` 同一路数：构建期脚本的
输入资产随项目受管），脚本读它、不读 jar。**产物已提交，日常构建不需要跑这个脚本。**

<h2>画法</h2>

1. 逐像素取亮度 `lum`（0-255）；
2. 亮度查**紫色带**（7 个锚点线性插值，见 `RAMP`），保住原图的明暗层次，
   不重画剪影、不改 alpha；
3. 孤立像素（8 邻域里不透明白像素 ≤ 1）判为「飞散的火星」，改星辰色 `STAR`
   —— 原图顶上那几粒火星正好都在这个判据里；
4. 火焰最亮的核心（lum ≥ 245 且非火星）退一点淡紫白，免得整团发灰。

用法：
    python tools/gen_xuanshu_nitor.py      # 出贴图 + 物品模型 + 临时预览
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot")
TEX_OUT = os.path.join(ASSETS, "textures/item/xuanshu_nitor.png")
MODEL_OUT = os.path.join(ASSETS, "models/item/xuanshu_nitor.json")
REF = os.path.join(ROOT, "tools/ref/tc4_nitor.png")

# 亮度 0 / 40 / 90 / 140 / 190 / 230 / 255 对应的紫色带（暗紫 → 亮紫 → 紫白）
RAMP = [
    (0.00, (26, 10, 40)),
    (0.16, (48, 20, 84)),
    (0.35, (76, 36, 132)),
    (0.55, (110, 58, 184)),
    (0.75, (150, 98, 226)),
    (0.90, (192, 160, 252)),
    (1.00, (230, 220, 255)),
]

# 星辰色（淡青白）：飞散的火星与最亮核心各取一档
STAR = (186, 226, 255)
STAR_CORE = (214, 240, 255)


def lum_of(rgb):
    return (rgb[0] * 299 + rgb[1] * 587 + rgb[2] * 114) // 1000


def ramp(t):
    for i in range(len(RAMP) - 1):
        t0, c0 = RAMP[i]
        t1, c1 = RAMP[i + 1]
        if t <= t1 or i == len(RAMP) - 2:
            k = 0.0 if t1 == t0 else max(0.0, min(1.0, (t - t0) / (t1 - t0)))
            return tuple(int(round(c0[j] + (c1[j] - c0[j]) * k)) for j in range(3))
    return RAMP[-1][1]


def main():
    src = Image.open(REF).convert("RGBA")
    w, h = src.size
    px = [[src.getpixel((x, y)) for x in range(w)] for y in range(h)]

    def opaque(x, y):
        return 0 <= x < w and 0 <= y < h and px[y][x][3] > 0

    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sparks = 0
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[y][x]
            if a == 0:
                continue
            neighbours = sum(1 for dx in (-1, 0, 1) for dy in (-1, 0, 1)
                             if (dx or dy) and opaque(x + dx, y + dy))
            lum = lum_of((r, g, b))
            if neighbours <= 1:
                colour = STAR
                sparks += 1
            elif lum >= 245:
                colour = STAR_CORE
            else:
                colour = ramp(lum / 255.0)
            out.putpixel((x, y), colour + (a,))

    out.save(TEX_OUT)
    print("贴图 %s（%d×%d，火星 %d 粒）" % (os.path.relpath(TEX_OUT, ROOT), w, h, sparks))

    model = ('{\n'
             '  "parent": "minecraft:item/generated",\n'
             '  "textures": {\n'
             '    "layer0": "skydeitymysticpivot:item/xuanshu_nitor"\n'
             '  }\n'
             '}\n')
    with open(MODEL_OUT, "w", encoding="utf-8", newline="\n") as f:
        f.write(model)
    print("模型 %s" % os.path.relpath(MODEL_OUT, ROOT))

    # 预览图只给开发时看，写到临时目录，不进项目
    sheet = Image.new("RGBA", (w * 8, h * 8), (0, 0, 0, 0))
    sheet.alpha_composite(out.resize((w * 8, h * 8), Image.NEAREST))
    preview = os.path.join(os.environ.get("TEMP", ROOT), "xuanshu_nitor_preview.png")
    sheet.save(preview)
    print("预览 %s" % preview)


if __name__ == "__main__":
    main()
