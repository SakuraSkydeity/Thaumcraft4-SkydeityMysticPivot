#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
净界阵激活贴图生成器（迭代版）：同色系重染 vs 灰石体两种方案对比。
方案 A：整石染成该卦象色（照六象石砖先例）。
方案 B：石体保留石灰噪点，只点亮面杯 + 卦画 + 四向通道。
通道几何两方案一致：横 y5-10、纵 x5-10，槽壁 1px（外）+ 亮核 3px。
"""
import os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BLOCK_TEX = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/block")

TRIGRAMS = {
    "qian":  (0xE0, 0xB8, 0x4A),
    "dui":   (0xDC, 0xE3, 0xEA),
    "li":    (0xE2, 0x4B, 0x33),
    "zhen":  (0x3C, 0xB3, 0x71),
    "xun":   (0x2F, 0x8C, 0x86),
    "kan":   (0x3B, 0x5F, 0xC4),
    "gen":   (0x9A, 0x7E, 0x58),
    "kun":   (0xC9, 0xA2, 0x4B),
}


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def scale(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c)


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


def ramp(col):
    return {
        "deep":  scale(col, 0.24),                 # 面板底
        "dark":  scale(col, 0.42),                 # 槽壁 / 石体暗端
        "mid":   scale(col, 0.62),                 # 石体亮端
        "bar":   scale(col, 0.82),                 # 卦画暗行
        "col":   col,                              # 规范象色
        "hot":   mix(col, (255, 255, 255), 0.60),  # 最亮（卦画亮行 / 亮核）
    }


# 通道几何：横通道 y5-10、纵通道 x5-10；槽壁在最外 1px，亮核 3px
SPAN = (5, 10)
CORE = (6, 7, 8)
PLUG = 5  # 通道从块边缘伸进面板的长度（北到 y4、南到 y11、西到 x4、东到 x11）


def draw_channel(px, dark, hot):
    def vline(x, y0, y1):
        for y in range(y0, y1 + 1):
            px[x, y] = hot if x in CORE else dark

    def hline(y, x0, x1):
        for x in range(x0, x1 + 1):
            px[x, y] = hot if y in CORE else dark

    for x in SPAN:
        vline(x, 0, PLUG - 1)          # 北
        vline(x, 16 - PLUG, 15)        # 南
    for y in SPAN:
        hline(y, 0, PLUG - 1)          # 西
        hline(y, 16 - PLUG, 15)        # 东


def variantA(im, r):
    """整石重染：面板->deep，石体->dark..mid，卦画->bar/hot。"""
    src = im.convert("RGBA").load()
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    dst = out.load()
    for y in range(16):
        for x in range(16):
            rr, gg, bb, aa = src[x, y]
            if aa < 16:
                continue
            L = lum((rr, gg, bb))
            in_panel = 3 <= x <= 12 and 3 <= y <= 12
            if L >= 170:
                c = r["hot"]
            elif L >= 130:
                c = r["bar"]
            elif in_panel:
                c = r["deep"]
            else:
                t = max(0.0, min(1.0, (L - 34.0) / 81.0))   # 34..115 -> dark..mid
                c = mix(r["dark"], r["mid"], t)
            dst[x, y] = (c[0], c[1], c[2], aa)
    draw_channel(dst, r["dark"], r["hot"])
    return out


def variantB(im, r):
    """石体保留石灰噪点；面板->deep，卦画->bar/hot，通道上色。"""
    src = im.convert("RGBA").load()
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    dst = out.load()
    for y in range(16):
        for x in range(16):
            rr, gg, bb, aa = src[x, y]
            if aa < 16:
                continue
            L = lum((rr, gg, bb))
            in_panel = 3 <= x <= 12 and 3 <= y <= 12
            if L >= 170:
                c = r["hot"]
            elif L >= 130:
                c = r["bar"]
            elif in_panel:
                c = r["deep"]
            else:
                c = (rr, gg, bb)          # 石体原样
            dst[x, y] = (c[0], c[1], c[2], aa)
    draw_channel(dst, r["dark"], r["hot"])
    return out


def sheet(tag, fn):
    """3x3 拼图 + 单块放大 + 接缝，输出对比图。"""
    layout = [
        ["qian", "dui", "li"],
        ["zhen", "@", "xun"],
        ["kan", "gen", "kun"],
    ]
    # 中心用带通道的净界石激活图（由主脚本生成后读回）
    center = Image.open(os.path.join(BLOCK_TEX, "xuanshu_purifying_stone_active.png")).convert("RGBA")
    big = Image.new("RGBA", (48, 48), (0, 0, 0, 0))
    for ri in range(3):
        for ci in range(3):
            name = layout[ri][ci]
            tile = center if name == "@" else Image.open(os.path.join(BLOCK_TEX, f"bagua_stone_{name}_active.png")).convert("RGBA")
            big.paste(tile, (ci * 16, ri * 16))
    big = big.resize((48 * 8, 48 * 8), Image.NEAREST)

    one = Image.open(os.path.join(BLOCK_TEX, "bagua_stone_qian_active.png")).convert("RGBA").resize((16 * 16, 16 * 16), Image.NEAREST)
    W = 48 * 8 + 40 + 16 * 16
    H = 48 * 8 + 60
    out = Image.new("RGBA", (W, H), (24, 24, 28, 255))
    out.paste(big, (20, 40))
    out.paste(one, (48 * 8 + 30, 40))
    out.save(os.path.join(ROOT, f"tools/preview/array_variant_{tag}.png"))
    print("variant", tag, "->", out.size)


if __name__ == "__main__":
    import sys
    which = sys.argv[1] if len(sys.argv) > 1 else "A"
    for name, col in TRIGRAMS.items():
        src = Image.open(os.path.join(BLOCK_TEX, f"bagua_stone_{name}.png"))
        r = ramp(col)
        out = (variantA if which == "A" else variantB)(src, r)
        out.save(os.path.join(BLOCK_TEX, f"bagua_stone_{name}_active.png"))
    sheet(which, None)
