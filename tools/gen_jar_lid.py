#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
玄枢源质罐的<b>蓝紫盖子</b>贴图生成器（第一百九十四批）。

<h2>为什么是「改色」而不是「重画」</h2>

用户口径：「模型和贴图全用 tc4 的源质罐子」，后来只加一条「盖子改为蓝紫色」。
于是这张图<b>不发明细</b>：它直接读本体的 {@code jar_side.png} / {@code jar_top.png}，
把盖子那一层（本来就画成深棕色的那块）的<b>色相</b>换成蓝紫，
明度 / 透明度 / 每个像素的明暗起伏全部原样保留 —— 看上去就是「同一只罐子换了个盖」。

<h2>怎么认出「盖子的像素」</h2>

不是按坐标写死（贴图以后就算被换掉，坐标就错了），而是按<b>颜色</b>筛：

* 盖子那块是深棕色：红分量明显大于蓝分量（{@code r > b + 0.02}）；
* 而且偏暗：最大分量 {@code < 0.55}。

玻璃那层是偏紫的半透明白，蓝分量本来就大于红，永远不会命中；
水印 / 空像素 alpha 为 0，先跳过。

命中之后按亮度重算颜色：先取亮度 {@code L}，再乘一支固定的蓝紫
（{@code LID}），于是原来「深一块浅一块」的凹凸全都还在，只是整体换成了蓝紫。

用法：

    python tools/gen_jar_lid.py

产物（两个文件，都是 16×16 RGBA）：

* {@code textures/block/essentia_jar_side.png}（盖子换了色）
* {@code textures/block/essentia_jar_top.png}（盖子换了色）
* {@code textures/block/essentia_jar_bottom.png}（罐底，原样复制）
"""

import os
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TC_JAR = os.path.join(ROOT, ".none/1.21.1-neoforge-20748-dev/tc.jar")
OUT_DIR = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/block")

#: 盖子的目标色（蓝紫），亮度归一化时用的基准亮度
LID = (0.54, 0.36, 0.86)
LID_BASE = 0.215

#: 判定「这是盖子的深棕色」的阈值
BROWN_GAP = 0.02
DARK_MAX = 0.55


def recolor(img):
    out = Image.new("RGBA", img.size)
    src = img.load()
    dst = out.load()
    changed = 0
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = src[x, y]
            if a == 0:
                dst[x, y] = (r, g, b, a)
                continue
            rf, gf, bf = r / 255.0, g / 255.0, b / 255.0
            if rf > bf + BROWN_GAP and max(rf, gf, bf) < DARK_MAX:
                lum = 0.299 * rf + 0.587 * gf + 0.114 * bf
                k = lum / LID_BASE
                dst[x, y] = (
                    min(255, int(LID[0] * k * 255)),
                    min(255, int(LID[1] * k * 255)),
                    min(255, int(LID[2] * k * 255)),
                    a,
                )
                changed += 1
            else:
                dst[x, y] = (r, g, b, a)
    return out, changed


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    with zipfile.ZipFile(TC_JAR) as jar:
        for name in ("jar_side", "jar_top"):
            member = "assets/thaumcraft/textures/block/%s.png" % name
            with jar.open(member) as handle:
                img = Image.open(handle).convert("RGBA")
            out, changed = recolor(img)
            target = os.path.join(OUT_DIR, "essentia_%s.png" % name)
            out.save(target)
            print("%-16s %s  ->  %s（%d 个像素换色）" % (name, img.size, target, changed))

        # 罐底原样搬一份过来：模型要是直接引用 thaumcraft:block/jar_bottom，
        # tools/selfcheck.py 的「贴图必须存在」只查本模组的 assets，会当成缺图报红。
        # 复制过来之后「这只罐子的每一张贴图都是我们自己的」，自检也不再要跨命名空间。
        member = "assets/thaumcraft/textures/block/jar_bottom.png"
        with jar.open(member) as handle:
            img = Image.open(handle).convert("RGBA")
        target = os.path.join(OUT_DIR, "essentia_jar_bottom.png")
        img.save(target)
        print("%-16s %s  ->  %s（原样复制）" % ("jar_bottom", img.size, target))


if __name__ == "__main__":
    main()
