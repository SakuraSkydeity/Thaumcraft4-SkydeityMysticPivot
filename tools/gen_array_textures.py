#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成八方净界阵的「激活」贴图：
  - 8 张 bagua_stone_<卦>_active.png（16x16，整石染成该卦的象色 + 四向连接通道）
  - 更新 xuanshu_purifying_stone_active.png（补四向连接通道，与卦位石通道严格对齐）

设计口径（与六象石砖「开坛后整块砖染成玄枢金」同一 precedent）：
  * 激活 = 整块重染：按非激活贴图的亮度映射到该卦象色的 6 级色阶，
    石的噪声结构原样保留（通体一色，与魔导书所述「象色」一致）。
  * 连接通道：四边各一条 4px 宽的能量槽（外 1px 深色槽壁 + 内 2px 亮核），
    几何在 9 块方石上完全一致（横通道 y6-9、纵通道 x6-9），
    因此任意两块相邻时槽口严丝合缝（连接顺畅）。
  * 每块的通道亮核用本卦象色（符合颜色）；净界石用青亮核 + 深青槽壁。
"""
import os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BLOCK_TEX = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/block")

# 卦 -> (id, 规范象色 RGB)，与 Fengshui.color 一致
TRIGRAMS = {
    "qian":  (0xE0, 0xB8, 0x4A),   # 乾 鎏金
    "dui":   (0xDC, 0xE3, 0xEA),   # 兑 银白
    "li":    (0xE2, 0x4B, 0x33),   # 离 朱赤
    "zhen":  (0x3C, 0xB3, 0x71),   # 震 青碧
    "xun":   (0x2F, 0x8C, 0x86),   # 巽 青苍
    "kan":   (0x3B, 0x5F, 0xC4),   # 坎 靛蓝
    "gen":   (0x9A, 0x7E, 0x58),   # 艮 岩褐
    "kun":   (0xC9, 0xA2, 0x4B),   # 坤 土黄
}


def mix(a, b, t):
    """a 向 b 插值 t"""
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def scale(c, f):
    return tuple(max(0, min(255, int(round(v * f)))) for v in c)


def ramp(col):
    """6 级色阶：深 -> 白亮"""
    return [
        scale(col, 0.20),                # t0 最深（面板底）
        scale(col, 0.38),                # t1 深（槽壁 / 石体暗部）
        scale(col, 0.58),                # t2 中（石体亮部）
        scale(col, 0.80),                # t3 卦画暗行
        col,                             # t4 规范象色
        mix(col, (255, 255, 255), 0.55), # t5 最亮（卦画亮行 / 通道亮核）
    ]


def lum(c):
    return 0.299 * c[0] + 0.587 * c[1] + 0.114 * c[2]


# 通道几何（9 块一致）：横通道 y6-9、纵通道 x6-9，槽壁在外、亮核在内
CORE = (7, 8)      # 亮核坐标（横通道的 y / 纵通道的 x）
SPAN = (6, 9)      # 通道含槽壁的坐标范围
PLUG = 4           # 通道伸进面板的长度（到 y4 / x4 为止，与面板相接）


def recolor(im, tones):
    """按亮度把整幅贴图映射到色阶；面板->t0，石体->t1..t2，卦画->t3/t5。"""
    src = im.convert("RGBA").load()
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    dst = out.load()
    w, h = im.size
    for y in range(h):
        for x in range(w):
            r, g, b, a = src[x, y]
            if a < 16:
                continue
            L = lum((r, g, b))
            in_panel = 3 <= x <= 12 and 3 <= y <= 12
            if L >= 170:                       # 卦画亮行
                c = tones[5]
            elif L >= 130:                     # 卦画暗行
                c = tones[3]
            elif in_panel:                     # 面板底（含极暗石点）
                c = tones[0]
            else:                              # 石体：按亮度在 t1..t2 间铺开
                t = (L - 34.0) / (115.0 - 34.0)
                t = max(0.0, min(1.0, t))
                c = mix(tones[1], tones[2], t)
            dst[x, y] = (c[0], c[1], c[2], a)
    return out


def draw_channel(im, tones, outline, core):
    """四向通道：纵 x6-9（北 y0..PLUG-1、南 y15..11-PLUG+1），横 y6-9（西 x0..PLUG-1、东 x15..）。"""
    px = im.load()
    o = outline
    c = core

    def vline(x, y0, y1):
        for y in range(y0, y1 + 1):
            px[x, y] = c if x in CORE else o

    def hline(y, x0, x1):
        for x in range(x0, x1 + 1):
            px[x, y] = c if y in CORE else o

    # 北：从块边缘(y0)伸到面板(y PLUG-1)
    for x in SPAN:
        vline(x, 0, PLUG - 1)
    # 南：从面板(y 15-PLUG+1)伸到块边缘(y15)
    for x in SPAN:
        vline(x, 16 - PLUG, 15)
    # 西：从块边缘(x0)伸到面板(x PLUG-1)
    for y in SPAN:
        hline(y, 0, PLUG - 1)
    # 东：从面板(x 16-PLUG)伸到块边缘(x15)
    for y in SPAN:
        hline(y, 16 - PLUG, 15)


def gen_bagua():
    for name, col in TRIGRAMS.items():
        src = Image.open(os.path.join(BLOCK_TEX, f"bagua_stone_{name}.png"))
        tones = ramp(col)
        out = recolor(src, tones)
        # 通道：槽壁用 t1（深），亮核用 t5（最亮）——与本卦象色同色系
        draw_channel(out, tones, outline=tones[1], core=tones[5])
        out.save(os.path.join(BLOCK_TEX, f"bagua_stone_{name}_active.png"))
        print("bagua_stone_%s_active.png  col=%s t0=%s t5=%s" % (name, col, tones[0], tones[5]))


def gen_center():
    path = os.path.join(BLOCK_TEX, "xuanshu_purifying_stone_active.png")
    im = Image.open(path).convert("RGBA")
    # 净界石通道：深青槽壁（用其外框色）+ 白青亮核（用其最亮青）
    draw_channel(im, None, outline=(40, 84, 78, 255), core=(208, 250, 242, 255))
    im.save(path)
    print("xuanshu_purifying_stone_active.png updated (channels added)")


def preview():
    """把 3x3 拼成一幅 48x48 预览（按魔导书的标准摆法：乾兑离/震净巽/坎艮坤）。"""
    layout = [
        ["bagua_stone_qian_active", "bagua_stone_dui_active", "bagua_stone_li_active"],
        ["bagua_stone_zhen_active", "xuanshu_purifying_stone_active", "bagua_stone_xun_active"],
        ["bagua_stone_kan_active", "bagua_stone_gen_active", "bagua_stone_kun_active"],
    ]
    sheet = Image.new("RGBA", (48, 48), (0, 0, 0, 0))
    for r in range(3):
        for c in range(3):
            tile = Image.open(os.path.join(BLOCK_TEX, layout[r][c] + ".png")).convert("RGBA")
            sheet.paste(tile, (c * 16, r * 16))
    sheet = sheet.resize((48 * 10, 48 * 10), Image.NEAREST)
    out = os.path.join(ROOT, "tools/preview/array_3x3_active.png")
    sheet.save(out)
    print("preview ->", out)

    # 非激活对照
    layout0 = [
        ["bagua_stone_qian", "bagua_stone_dui", "bagua_stone_li"],
        ["bagua_stone_zhen", "xuanshu_purifying_stone", "bagua_stone_xun"],
        ["bagua_stone_kan", "bagua_stone_gen", "bagua_stone_kun"],
    ]
    sheet0 = Image.new("RGBA", (48, 48), (0, 0, 0, 0))
    for r in range(3):
        for c in range(3):
            tile = Image.open(os.path.join(BLOCK_TEX, layout0[r][c] + ".png")).convert("RGBA")
            sheet0.paste(tile, (c * 16, r * 16))
    sheet0 = sheet0.resize((48 * 10, 48 * 10), Image.NEAREST)
    out0 = os.path.join(ROOT, "tools/preview/array_3x3_inactive.png")
    sheet0.save(out0)
    print("preview ->", out0)


if __name__ == "__main__":
    gen_bagua()
    gen_center()
    preview()
