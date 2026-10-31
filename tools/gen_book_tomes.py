#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""两本「玄枢」附魔书（祛魔 / 双生）的物品图标 —— **16×16 原版底 + 双色绑带**。

两本共用原版 {@code enchanted_book} 的底图（原版五种书里**只有它带一条绑带**），
只把绑带整体做一次**色相旋转**，明度一律不动：

| 物品 | 绑带色相 | 饱和度 | 观感 |
|---|---|---|---|
| 玄枢祛魔书 `disenchant_book` | 300°（粉紫） | ×0.88 | 从红带旋到**粉紫**，一眼是「祛」的那本 |
| 玄枢双生书 `twin_book` | 265°（蓝紫偏蓝） | ×0.92 | 再往蓝旋一档，**蓝紫**，与祛魔书成对而不撞色 |

<h2>做法</h2>
<ol>
  <li>从开发用 MC 资源 jar 里取 {@code assets/minecraft/textures/item/enchanted_book.png}；</li>
  <li>把红色系像素（HSV 色相 ≥325° 或 ≤12°、饱和度 &gt; 0.40）整批旋到目标色相，
      饱和度乘系数，**明度原样** ⇒ 绑带的明暗层次与褶皱全保留，只是换了颜色；</li>
  <li>金色扣子（色相约 45°）不动。</li>
</ol>

输出 {@code textures/item/disenchant_book.png} 与 {@code textures/item/twin_book.png}（各 16×16），
外加对比预览 {@code tools/preview/book_tomes.png}（左祛魔、右双生，逐像素放大 + 网格）。
原版贴图属于 Mojang 素材，本 mod 内部自用。
"""
import colorsys
import io
import os
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEM_TEX = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/item")
PREVIEW = os.path.join(ROOT, "tools/preview")
ARTIFACTS = os.path.join(ROOT, "build/moddev/artifacts")
JARS = (
    "neoforge-21.1.248-minecraft-resources-aka-client-extra.jar",
    "neoforge-21.1.248-minecraft-merged.jar",
)
VANILLA = "assets/minecraft/textures/item/enchanted_book.png"

# (文件名, 目标色相, 饱和度系数)
BOOKS = (
    ("disenchant_book", 300.0, 0.88),
    ("twin_book", 265.0, 0.92),
)


def load_vanilla():
    for name in JARS:
        path = os.path.join(ARTIFACTS, name)
        if not os.path.exists(path):
            continue
        with zipfile.ZipFile(path) as z:
            if VANILLA in z.namelist():
                return Image.open(io.BytesIO(z.read(VANILLA))).convert("RGBA")
    raise SystemExit("找不到原版贴图：需要 %s 里的 %s（先跑一次 build 生成 artifacts）"
                     % (os.path.join("build/moddev/artifacts", JARS[0]), VANILLA))


def is_red(r, g, b):
    h, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
    deg = h * 360.0
    return s > 0.40 and (deg >= 325.0 or deg <= 12.0) and v > 0.15


def recolour(im, hue, sat_scale):
    """把红绑带整批旋到 hue，返回改动的像素数与新图（原图不动）。"""
    out = im.copy()
    px = out.load()
    changed = 0
    for y in range(out.size[1]):
        for x in range(out.size[0]):
            r, g, b, a = px[x, y]
            if a == 0 or not is_red(r, g, b):
                continue
            _, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            nr, ng, nb = colorsys.hsv_to_rgb(hue / 360.0, min(1.0, s * sat_scale), v)
            px[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
            changed += 1
    return changed, out


def sheet(images, path, scale=18, pad=14):
    """两（多）张 16×16 并排放大，垫深底 + 细网格，验收时看得清像素。"""
    side = images[0].size[0] * scale
    w = len(images) * (side + pad) + pad
    canvas = Image.new("RGBA", (w, side + pad * 2), (30, 26, 44, 255))
    grid = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    gpx = grid.load()
    for i in range(0, side, scale):
        for j in range(side):
            gpx[i, j] = (255, 255, 255, 26)
            gpx[j, i] = (255, 255, 255, 26)
    for i, im in enumerate(images):
        ox = pad + i * (side + pad)
        canvas.paste(im.resize((side, side), Image.NEAREST), (ox, pad))
        canvas.alpha_composite(grid, (ox, pad))
    canvas.save(path)


def main():
    os.makedirs(PREVIEW, exist_ok=True)
    base = load_vanilla()
    made = []
    for name, hue, sat in BOOKS:
        changed, im = recolour(base, hue, sat)
        dst = os.path.join(ITEM_TEX, name + ".png")
        im.save(dst)
        made.append(im)
        print("%-16s 色相 %3.0f°  绑带 %d 像素 -> %s"
              % (name, hue, changed, os.path.relpath(dst, ROOT)))
    out = os.path.join(PREVIEW, "book_tomes.png")
    sheet(made, out)
    print("preview ->", os.path.relpath(out, ROOT))


if __name__ == "__main__":
    main()
