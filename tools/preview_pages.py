# -*- coding: utf-8 -*-
"""把改后的书页文案渲染成 PNG，用来对照「一行一个内容 + 卦石标蓝」的效果。

不是游戏截图，是按 lang 文件里的真实字符串（含 § 颜色码）逐行画的示意图：
米黄底、深褐正文、蓝色加粗的卦石句、灰褐小字。行距与游戏里接近，
一眼就能看出每行有没有超宽、蓝色落在哪一句上。

用法：python tools/preview_pages.py
输出：tools/preview/book_pages_155.png
"""

import json
import os
import re

from PIL import Image, ImageDraw, ImageFont

LANG = "src/main/resources/assets/skydeitymysticpivot/lang/zh_cn.json"
OUT = "tools/preview/book_pages_155.png"

FONT = r"C:\Windows\Fonts\msyh.ttc"
FONT_B = r"C:\Windows\Fonts\msyhbd.ttc"

PAPER = (233, 221, 194)
INK = (42, 33, 24)
SOFT = (110, 98, 80)
BLUE = (60, 70, 190)

# 和 Minecraft 的颜色码对齐（只用到这几个）
COLOURS = {
    "0": INK, "1": (40, 48, 130), "2": (40, 100, 50), "3": (40, 120, 120),
    "4": (140, 45, 45), "5": (110, 60, 140), "6": (160, 120, 30),
    "7": (105, 105, 105), "8": SOFT, "9": BLUE, "a": (50, 140, 60),
    "b": (50, 140, 140), "c": (170, 55, 55), "d": (160, 90, 180),
    "e": (170, 145, 40), "f": (245, 245, 240),
}


def segments(line):
    """把一行拆成 [(文字, 颜色, 是否粗体)]。"""
    out = []
    colour = INK
    bold = False
    i = 0
    buf = ""
    while i < len(line):
        if line[i] == "§" and i + 1 < len(line):
            if buf:
                out.append((buf, colour, bold))
                buf = ""
            code = line[i + 1].lower()
            if code in COLOURS:
                colour = COLOURS[code]
                bold = False
            elif code == "l":
                bold = True
            elif code == "r":
                colour = INK
                bold = False
            i += 2
            continue
        buf += line[i]
        i += 1
    if buf:
        out.append((buf, colour, bold))
    return out


def draw_page(draw, x, y, width, title, lines, line_h=24):
    """画一页：标题 + 若干行，返回结束时的 y。"""
    f_title = ImageFont.truetype(FONT_B, 19)
    fonts = {
        False: ImageFont.truetype(FONT, 16),
        True: ImageFont.truetype(FONT_B, 16),
    }
    draw.text((x, y), title, font=f_title, fill=(35, 28, 20))
    y += 34
    for line in lines:
        cx = x
        for text, colour, bold in segments(line):
            draw.text((cx, y), text, font=fonts[bold], fill=colour)
            cx += draw.textlength(text, font=fonts[bold])
        y += line_h
    return y


def page_lines(key, lang, width=380):
    raw = lang[key]
    return [ln for ln in raw.split("<BR>") if ln.strip()], width


def main():
    lang = json.load(open(LANG, encoding="utf-8"))

    pad = 34
    col_w = 400
    W = pad * 2 + col_w * 2 + 24
    H = 700

    img = Image.new("RGB", (W, H), PAPER)
    d = ImageDraw.Draw(img)

    # 纸面质感：几道很淡的横向色差，别太花
    for gy in range(0, H, 3):
        shade = (231 + (gy // 3) % 3, 219 + (gy // 3) % 3, 191 + (gy // 3) % 2)
        d.line([(0, gy), (W, gy)], fill=shade)

    # 中线
    d.line([(pad + col_w + 11, 40), (pad + col_w + 11, H - 40)], fill=(198, 183, 155), width=1)

    left, _ = page_lines("bagua.page.node_wands2", lang)
    right, _ = page_lines("bagua.page.node_caps2", lang)

    f_note = ImageFont.truetype(FONT, 13)
    d.text((pad, 8), "改后 · 三柄怎么选（每行一个内容，卦石句为蓝色）", font=f_note, fill=SOFT)
    d.text((pad + col_w + 24, 8), "改后 · 三对怎么选", font=f_note, fill=SOFT)

    draw_page(d, pad, 30, col_w, left[0].replace("§l", "").replace("§r", ""), left[1:])
    draw_page(d, pad + col_w + 24, 30, col_w,
              right[0].replace("§l", "").replace("§r", ""), right[1:])

    # 底部：一条配方页示例
    d.line([(pad, 470), (W - pad, 470)], fill=(198, 183, 155), width=1)
    d.text((pad, 478), "改后 · 配方页：要素 / 不稳 / 卦石 各占一行（注魔物料那一行是长句，全书通例，本批未动）",
           font=f_note, fill=SOFT)

    rec = lang["bagua.recipe.gilded_wand_cap"].split("<BR>")
    draw_page(d, pad, 498, W - pad * 2, rec[0].replace("§l", "").replace("§r", ""), rec[1:], line_h=26)

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("written", OUT, img.size)


if __name__ == "__main__":
    main()
