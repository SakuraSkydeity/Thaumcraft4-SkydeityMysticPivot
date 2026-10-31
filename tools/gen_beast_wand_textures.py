#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""四圣兽法杖柄/端贴图「一点点修改」（第一百六十二批）。

用户原话：「刚刚做的四个法杖模型中 杖柄和杖端的贴图不要一样 做一点点修改」。

上一批（第一百六十一批）青龙 / 白虎 / 玄武的六张图是照朱雀「同款形状重着色」出来的：
四把杖的杖柄贴图是同一张 wrapped-strip 换个色、杖端是同一个十字章换个色，
放在一起分不清谁是谁。本批给每柄兽的**杖柄**与**杖端**各加一个小记号
（加载现有贴图后做**增量**修改，原有画面一概不动，可反复运行）：

杖柄（textures/models/<兽>_wand_rod.png，使用区 x0-8 / y8-28）加「结」：
  * 朱雀：v=18 一道金环 + v=19 暗影（火纹结）；
  * 青龙：v=14 / v=23 两道亮环 + 暗影（双竹节）；
  * 白虎：v=18 一道留了两道冰裂缺口的霜环 + 暗影；
  * 玄武：v=13 / v=20 四道短斜缝（龟甲片缝，两处斜向相反）。

杖端（textures/models/<兽>_wand_cap.png，十字章 x0-8 / y0-9）加「兽记」：
  * 朱雀：顶杠两端外挑两粒金（火舌）；
  * 青龙：顶杠两端各挑一粒青碧（龙角）；
  * 白虎：左右两臂上各扫两道白（虎爪）；
  * 玄武：茎正中一粒幽蓝亮珠（龟甲珠）。

物品图标（textures/item/*.png，16x16）同步：
  * 杖柄：斜带中段两行加宽成亮结（中间两格涂亮色）；
  * 杖端：圆珠右下暗区点一个小记号（朱雀斜火星、青龙竖鳞、白虎斜爪、玄武小甲）。

调色板全部取自各兽现有贴图里的既有颜色（见 PAL），不加新色。
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_TEX = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/models")
ITEM_TEX = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/item")

# ---------------------------------------------------------------- 调色板
# 全部是各兽贴图里已经出现的颜色（从现有 png 逐个数出来），不引入新色。
PAL = {
    "zhuque":  dict(DARK=(42, 8, 5),    MID=(132, 36, 18), LIGHT=(255, 216, 132)),
    "qinglong": dict(DARK=(18, 40, 44),  MID=(58, 112, 96), LIGHT=(168, 222, 168)),
    "baihu":   dict(DARK=(30, 30, 36),   MID=(96, 98, 108), LIGHT=(212, 214, 220)),
    "xuanwu":  dict(DARK=(12, 16, 34),   MID=(38, 58, 100), LIGHT=(104, 138, 200)),
}
# 玄武甲片缝用比 LIGHT 暗一档的既有蓝（贴图里已有）
XUANWU_SEAM = (78, 108, 168)

OPAQUE = 255


def load(path):
    return Image.open(path).convert("RGBA")


def save(im, path):
    im.save(path)
    print("written", os.path.relpath(path, ROOT))


def setpx(im, x, y, rgb):
    """写一个不透明像素（越界静默跳过）。"""
    if 0 <= x < im.size[0] and 0 <= y < im.size[1]:
        im.load()[x, y] = rgb + (OPAQUE,)


def rod_knot(beast, im):
    """杖柄模型贴图：绕杖一圈的「结」（x0-8 四条面带全画满 = 三维里一道整环）。"""
    p = PAL[beast]
    px = im.load()
    if beast == "zhuque":                      # 火纹结：一道金环 + 一道暗影
        for x in range(8):
            setpx(im, x, 18, p["LIGHT"])
            setpx(im, x, 19, p["DARK"])
    elif beast == "qinglong":                  # 双竹节：两道亮环各托一道暗影
        for v in (14, 23):
            for x in range(8):
                setpx(im, x, v, p["LIGHT"])
                setpx(im, x, v + 1, p["DARK"])
    elif beast == "baihu":                     # 霜环：留两道冰裂缺口
        for x in range(8):
            setpx(im, x, 18, p["MID"] if x in (2, 5) else p["LIGHT"])
            setpx(im, x, 19, p["DARK"])
    elif beast == "xuanwu":                    # 甲片缝：四道短斜缝，两处斜向相反
        for x0 in (0, 3, 6):
            setpx(im, x0, 13, XUANWU_SEAM)
            setpx(im, x0 + 1, 14, XUANWU_SEAM)
            setpx(im, x0 + 1, 20, XUANWU_SEAM)
            setpx(im, x0, 21, XUANWU_SEAM)


def cap_mark(beast, im):
    """杖端模型贴图：十字章上的「兽记」（原有 pixels 一概不动，只补/改几粒）。"""
    p = PAL[beast]
    if beast == "zhuque":                      # 火舌：顶杠两端外挑两粒金
        setpx(im, 1, 0, p["LIGHT"])
        setpx(im, 6, 0, p["LIGHT"])
    elif beast == "qinglong":                  # 龙角：顶杠两端各挑一粒青碧
        setpx(im, 1, 1, p["LIGHT"])
        setpx(im, 6, 1, p["LIGHT"])
    elif beast == "baihu":                     # 虎爪：左右两臂各扫两道白
        for x, y in ((0, 2), (1, 3), (6, 2), (7, 3)):
            setpx(im, x, y, p["LIGHT"])
    elif beast == "xuanwu":                    # 龟甲珠：茎正中一粒幽蓝
        setpx(im, 3, 5, p["LIGHT"])
        setpx(im, 4, 5, p["LIGHT"])


def rod_icon_knot(beast, im):
    """杖柄物品图标（16x16 斜带）：中段两行加宽成亮结。"""
    p = PAL[beast]
    px = im.load()
    for y in (7, 8):
        cols = [x for x in range(16) if px[x, y][3] > 0]
        if len(cols) < 3:
            continue
        setpx(im, cols[0], y, p["DARK"])
        setpx(im, cols[1], y, p["LIGHT"])
        setpx(im, cols[2], y, p["LIGHT"])
        setpx(im, cols[3], y, p["DARK"])


def cap_icon_mark(beast, im):
    """杖端物品图标（16x16 圆珠）：右下暗区点一个小记号。"""
    p = PAL[beast]
    if beast == "zhuque":                      # 斜火星三粒
        for x, y in ((9, 6), (10, 7), (11, 8)):
            setpx(im, x, y, p["LIGHT"])
    elif beast == "qinglong":                  # 竖鳞两粒
        for x, y in ((10, 6), (10, 7)):
            setpx(im, x, y, p["LIGHT"])
    elif beast == "baihu":                     # 斜爪两粒（与火星反向）
        for x, y in ((11, 6), (10, 7)):
            setpx(im, x, y, p["LIGHT"])
    elif beast == "xuanwu":                    # 小甲三粒（右拐角）
        for x, y in ((10, 7), (11, 7), (10, 8)):
            setpx(im, x, y, p["LIGHT"])


def main():
    for beast in ("zhuque", "qinglong", "baihu", "xuanwu"):
        # ---- 杖柄：模型贴图 + 物品图标 ----
        im = load(os.path.join(MODEL_TEX, beast + "_wand_rod.png"))
        rod_knot(beast, im)
        save(im, os.path.join(MODEL_TEX, beast + "_wand_rod.png"))

        im = load(os.path.join(ITEM_TEX, beast + "_wand_rod.png"))
        rod_icon_knot(beast, im)
        save(im, os.path.join(ITEM_TEX, beast + "_wand_rod.png"))

        # ---- 杖端：模型贴图 + 物品图标 ----
        im = load(os.path.join(MODEL_TEX, beast + "_wand_cap.png"))
        cap_mark(beast, im)
        save(im, os.path.join(MODEL_TEX, beast + "_wand_cap.png"))

        im = load(os.path.join(ITEM_TEX, beast + "_wand_cap.png"))
        cap_icon_mark(beast, im)
        save(im, os.path.join(ITEM_TEX, beast + "_wand_cap.png"))


if __name__ == "__main__":
    main()
