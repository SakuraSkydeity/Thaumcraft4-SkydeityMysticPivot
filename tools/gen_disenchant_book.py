#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""玄枢祛魔书（disenchant_book）物品图标 —— **16×16 原版底 + 粉紫绑带**。

第一百零三批曾把整本重画成 64×64 的仙家书；第一百零四批用户澄清：
「玄枢祛魔书贴图该回去 16×16，我要改的是魔导书我的模组章节的背景」——
也就是说 64×64 那套要用在**研究书的章节背景**（见
{@code tools/gen_researchback_bagua.py}），物品图标回到 16×16 的原版底。

<h2>做法（沿用第一百零二批）</h2>
<ol>
  <li>从开发用 MC 资源 jar 里取 {@code assets/minecraft/textures/item/enchanted_book.png}
      （原版五种书里**只有它带一条红色绑带**）；</li>
  <li>把红色系像素（HSV 色相 ≥325° 且高饱和）整体**色相旋到 300°**（粉紫），
      饱和度 ×0.88，**明度不动** ⇒ 绑带的明暗层次原样保留，只是红变粉紫；</li>
  <li>金色扣子（色相约 45°）不动。</li>
</ol>

输出 {@code textures/item/disenchant_book.png}（16×16）。原版贴图属于 Mojang 素材，
本 mod 内部自用（与第九十一批「附魔台贴图直接复制进本 mod」同一做法）。
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

# 粉色带的目标色相（度）与饱和度系数
TARGET_HUE = 300.0
SAT_SCALE = 0.88


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


def recolour(im):
    px = im.load()
    changed = 0
    for y in range(im.size[1]):
        for x in range(im.size[0]):
            r, g, b, a = px[x, y]
            if a == 0 or not is_red(r, g, b):
                continue
            _, s, v = colorsys.rgb_to_hsv(r / 255.0, g / 255.0, b / 255.0)
            nr, ng, nb = colorsys.hsv_to_rgb(TARGET_HUE / 360.0, min(1.0, s * SAT_SCALE), v)
            px[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
            changed += 1
    return changed


def main():
    os.makedirs(PREVIEW, exist_ok=True)
    im = load_vanilla()
    changed = recolour(im)
    dst = os.path.join(ITEM_TEX, "disenchant_book.png")
    im.save(dst)
    print("红色绑带像素改色:", changed, "-> ", os.path.relpath(dst, ROOT), im.size)

    cell = im.size[0] * 20
    canvas = Image.new("RGBA", (cell + 20, cell + 20), (30, 26, 44, 255))
    canvas.paste(im.resize((cell, cell), Image.NEAREST), (10, 10))
    canvas.save(os.path.join(PREVIEW, "disenchant_book.png"))
    print("preview ->", os.path.relpath(os.path.join(PREVIEW, "disenchant_book.png"), ROOT))


if __name__ == "__main__":
    main()
