#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
玄灵三材 · 贴图 + 物品模型（第二百批新增；第二百零一批重画：原版结构 + 非白底色；
第二百零二批：兔脚再换色改形，和原版区别拉大）。

<h2>用户口径（第二百零一批）</h2>

「兔子脚用原版的兔子脚改，骨头，翎月翼膜也是 底色不要白色太怪了」。

第一百批那版是**自绘剪影 + 银白底**——剪影虽然抄了原版的 alpha，但明暗是按
「到透明边距离」自动算的，原版兔脚那只分明脚趾的深色趾线、骨头的骨节起伏
全被抹成了平滑渐变；而且三件都是白银底，在一堆金 / 青 / 橙的模组材料里
白得扎眼。这一批两条一起改：

  1. **结构直接用原版的**：三张原版贴图（`rabbit_foot` / `bone` /
     `phantom_membrane`）按**调色板点位图**逐像素抄进本脚本（`.` = 透明，
     数字 / 字母 = 该像素在原版调色板里的序号，序号按亮度排），上色时
     序号一一对映到新色带——原版的趾线、骨节、膜纹一根不少，改的只有颜色；
  2. **底色全部换掉**：兔脚走原版的棕（暖一档，配鎏金爪尖）；魔骨走
     **暗旧骨的藕褐**（配鎏金嵌线 + 紫焰）；翼膜走**月夜青紫**（配鎏金羽脉
     + 月牙）——没有一件是白的。

<h2>画法</h2>

点位图查表上色（原版结构），装饰（爪尖 / 嵌线 / 羽脉 / 焰 / 月牙）逐像素补。
所有颜色都是固定分档，没有随机噪声。

用法：
    python tools/gen_spirit_relics.py     # 出三张贴图 + 三个物品模型 + 预览图
"""
import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot")
TEX_DIR = os.path.join(ASSETS, "textures/item")
MODEL_DIR = os.path.join(ASSETS, "models/item")
PREVIEW_DIR = os.path.join(ROOT, "tools/preview")

S = 16
ITEMS = ("xuanling_rabbit_foot", "shenjin_demon_bone", "lingyue_wing_membrane")

GOLD = (226, 182, 94)
GOLD_D = (176, 131, 52)
GOLD_HI = (252, 228, 154)

HEX_D = (122, 76, 194)
HEX = (168, 114, 224)
HEX_HI = (218, 182, 250)

MOON = (250, 240, 206)
MOON_D = (224, 202, 152)

# ----------------------------------------------------------------------
# 原版贴图 · 调色板点位图（`.` 透明；字符 = 调色板序号，按亮度升序）
#
# 从 1.21.1 客户端 jar 的 assets/minecraft/textures/item/ 逐像素抄录，
# 抄的是「哪个像素属于哪个明暗档」，所以对映到新色带时原版的
# 趾线 / 骨节 / 膜纹全部保得住。
# ----------------------------------------------------------------------
VANILLA_RABBIT_FOOT = {
    "palette": [(101, 78, 57), (130, 96, 65), (180, 139, 91),
                (199, 158, 103), (216, 178, 129)],
    "rows": [
        "................",
        "................",
        "..........11....",
        ".........1220...",
        ".........13440..",
        ".........14430..",
        ".......1133420..",
        "....1114323320..",
        "...1344443230...",
        "..12443433220...",
        "..1034433220....",
        "..003133200.....",
        "...021200.......",
        "....000.........",
        "................",
        "................",
    ],
}

VANILLA_BONE = {
    "palette": [(123, 126, 107), (203, 198, 165), (232, 229, 210),
                (252, 251, 237), (255, 255, 253)],
    "rows": [
        "................",
        "...........121..",
        "..........12300.",
        "..........134320",
        "..........133210",
        ".........133000.",
        "........1330....",
        ".......1330.....",
        "......1330......",
        "....11330.......",
        "..224430........",
        ".234440.........",
        ".121420.........",
        "..10210.........",
        "....00..........",
        "................",
    ],
}

VANILLA_PHANTOM_MEMBRANE = {
    "palette": [(111, 81, 108), (126, 98, 123), (130, 109, 114),
                (142, 127, 122), (149, 140, 121), (156, 138, 131),
                (169, 155, 135), (195, 185, 161), (220, 217, 192)],
    "rows": [
        "................",
        "................",
        ".........222....",
        ".....22.2788222.",
        "....277278837830",
        "...2788536378830",
        "...2883377332110",
        "..2763778867840.",
        "..2337788578440.",
        ".27867886332110.",
        ".2836666377840..",
        ".2337862788840..",
        ".0378227884410..",
        "..04410022210...",
        "...000..0000....",
        "................",
    ],
}

# ----------------------------------------------------------------------
# 新色带（每条与原版调色板等长，按同一亮度序对映）
# ----------------------------------------------------------------------
# 玄灵兔子脚（第 202 批重定色）：灵玉青 —— 与暖棕的原版一脚就能分出来（配鎏金脚箍 + 灵光爪尖）
RABBIT_RAMP = [(28, 52, 54), (48, 84, 86), (76, 120, 120),
               (112, 158, 154), (168, 210, 202)]
CLAW = (156, 246, 232)
CLAW_D = (86, 176, 176)
# 神浸魔骨：暗旧骨的藕褐（不是白）
BONE_RAMP = [(60, 50, 56), (104, 88, 92), (150, 128, 122),
             (182, 160, 146), (210, 190, 174)]
# 翎月翼膜：月夜青紫（不是白）
MEMBRANE_RAMP = [(54, 50, 88), (76, 70, 116), (98, 90, 140),
                 (120, 110, 160), (140, 130, 180), (158, 148, 198),
                 (178, 170, 216), (200, 194, 230), (220, 216, 244)]


def recolor(vanilla, ramp):
    """点位图 → 新色带：序号按亮度一一对映，结构原样保留。"""
    pal = vanilla["palette"]
    assert len(pal) == len(ramp), "色带长度必须与原版调色板一致"
    px = {}
    for y, row in enumerate(vanilla["rows"]):
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            px[(x, y)] = ramp["0123456789abcdefghijklmnopqrstuvwxyz".index(ch)]
    return px


def put(px, x, y, c):
    if (x, y) in px:
        px[(x, y)] = c


def sparkle(px, cx, cy, core, ray):
    """四角星：中心一点 + 上下左右各一射点。"""
    put(px, cx, cy, core)
    for (dx, dy) in ((0, -1), (0, 1), (-1, 0), (1, 0)):
        put(px, cx + dx, cy + dy, ray)


def save_item(px, name):
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    for (x, y), c in px.items():
        im.putpixel((x, y), c + (255,))
    p = os.path.join(TEX_DIR, name + ".png")
    im.save(p)
    print("  %-28s 16x16" % (name + ".png"))
    return im


def write_item_model(name):
    p = os.path.join(MODEL_DIR, name + ".json")
    with open(p, "w", encoding="utf-8", newline="\n") as f:
        f.write('{\n  "parent": "minecraft:item/generated",\n'
                '  "textures": {\n'
                '    "layer0": "skydeitymysticpivot:item/%s"\n'
                '  }\n}\n' % name)
    print("  models/item/%s.json" % name)


# ----------------------------------------------------------------------
# 一、玄灵兔子脚：原版棕底 + 鎏金爪尖 + 灵光星
# ----------------------------------------------------------------------
def tex_rabbit_foot():
    """玄灵兔子脚（第 202 批重画）。

    用户口径「兔子脚贴图再和原版区别大一点」—— 结构和原版同一张点位图（趾线、爪垫起伏都在），
    但做了四件事，进背包一眼就不会跟原版混：
      1. **整体镜像**：原版朝右上，这只朝左上；
      2. **换色**：暖棕 → 灵玉青（冷色，和原版正好相反）；
      3. **鎏金脚箍**：脚跟那一段整段描金，是原版没有的部件；
      4. **灵光爪尖 + 掌心灵纹**：趾尖四点亮青、掌心一枚 2×2 灵纹。
    """
    px = recolor(VANILLA_RABBIT_FOOT, RABBIT_RAMP)
    # 一、镜像（左右翻）
    px = {(15 - x, y): c for (x, y), c in px.items()}

    # 二、鎏金脚箍：只箍住「踝」那一段（y = 12 那一斜排），脚跟仍旧是玉色 ——
    #     整排都描金会变成一块底板，整只脚看着像条鱼
    for (x, y) in ((7, 12), (8, 12), (9, 12), (10, 12)):
        put(px, x, y, GOLD)
    put(px, 7, 12, GOLD_HI)
    put(px, 10, 12, GOLD_D)

    # 三、灵光爪尖：趾尖最外那四点（镜像后的左上角）点亮青
    for (x, y) in ((4, 2), (3, 3), (2, 4), (2, 5)):
        put(px, x, y, CLAW)
    put(px, 3, 4, CLAW_D)
    put(px, 5, 3, CLAW_D)

    # 四、掌心灵纹：2×2 的一点青光（玄灵魂气从爪心透出来）
    for (x, y) in ((7, 8), (8, 8), (7, 9)):
        put(px, x, y, CLAW)
    put(px, 8, 9, CLAW_D)

    # 五、右上一颗灵光星（浮空，和翼膜的月牙是同一路数）
    sparkle(px, 13, 2, (255, 255, 255), GOLD_HI)
    return px


# ----------------------------------------------------------------------
# 二、神浸魔骨：藕褐旧骨 + 鎏金嵌线 + 两端金箍 + 骨心紫焰
# ----------------------------------------------------------------------
def tex_demon_bone():
    px = recolor(VANILLA_BONE, BONE_RAMP)
    # 鎏金嵌线：顺着骨干的对角走一道（神金锁骨）
    line = [(11, 4), (10, 5), (10, 6), (9, 7), (9, 8), (8, 9),
            (7, 10), (6, 11), (5, 11), (4, 12), (3, 12)]
    for (x, y) in line:
        put(px, x, y, GOLD)
    # 嵌线的暗侧（让金线「嵌」进去，不是浮在上面）
    for (x, y) in line:
        put(px, x + 1, y, GOLD_D)
    # 两端骨节各描一道金箍
    for y in range(2, 6):
        put(px, 11, y, GOLD)
        put(px, 11, y - 1 if y else y, GOLD_D)
    for y in range(10, 13):
        put(px, 1, y, GOLD)
        put(px, 1, y - 1, GOLD_D)
    # 骨心一点紫焰（魔骨的本相）：3×2 焰心 + 上下火苗
    for (x, y) in ((7, 8), (8, 8), (7, 9), (8, 9)):
        put(px, x, y, HEX)
    put(px, 7, 7, HEX_D)
    put(px, 8, 7, HEX)
    put(px, 7, 10, HEX_D)
    put(px, 8, 10, HEX)
    put(px, 7, 8, HEX_HI)
    put(px, 8, 9, HEX_HI)
    return px


# ----------------------------------------------------------------------
# 三、翎月翼膜：月夜青紫膜 + 鎏金羽脉 + 月辉 + 浮空月牙
# ----------------------------------------------------------------------
def tex_wing_membrane():
    px = recolor(VANILLA_PHANTOM_MEMBRANE, MEMBRANE_RAMP)
    # 三道鎏金羽脉：从左下的膜柄向右上展开（翎）
    veins = [
        [(4, 12), (5, 11), (6, 10), (7, 9), (8, 8), (9, 7), (10, 6), (11, 5), (12, 4)],
        [(5, 13), (6, 12), (7, 11), (8, 10), (9, 9), (10, 8), (11, 7), (12, 6), (13, 5)],
        [(3, 11), (4, 10), (5, 9), (6, 8), (7, 7), (8, 6), (9, 5), (10, 4), (11, 3)],
    ]
    for vein in veins:
        for (x, y) in vein:
            put(px, x, y, GOLD)
    # 脉根的结（膜柄）
    put(px, 4, 13, GOLD)
    put(px, 4, 12, GOLD_D)
    # 膜面右上一片月辉（月光掠过翼膜）
    for y in range(3, 8):
        for x in range(9, 14):
            if (x, y) in px and (x + y) % 2 == 0:
                put(px, x, y, mix_local(px[(x, y)], MOON, 0.30))
    # 右上一枚浮空月牙（月）：画在剪影之外，和兔子脚的灵光星一样是浮空饰品
    cx, cy, r_out, r_in, off = 12.6, 2.6, 2.5, 2.05, 1.35
    for y in range(int(cy) - 3, int(cy) + 4):
        for x in range(int(cx) - 3, int(cx) + 4):
            d_out = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            d_in = math.hypot(x + 0.5 - (cx + off), y + 0.5 - (cy - off))
            if d_out <= r_out and d_in > r_in:
                px[(x, y)] = MOON
    # 月牙的下弦描一道暗边
    for (x, y) in ((13, 3), (13, 4), (12, 5), (11, 4), (11, 3)):
        if (x, y) in px:
            px[(x, y)] = MOON_D
    return px


def mix_local(a, b, t):
    t = 0.0 if t < 0.0 else (1.0 if t > 1.0 else t)
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


BUILDERS = {
    "xuanling_rabbit_foot": tex_rabbit_foot,
    "shenjin_demon_bone": tex_demon_bone,
    "lingyue_wing_membrane": tex_wing_membrane,
}


# ----------------------------------------------------------------------
# 预览：三联放大图（8 倍，带像素格）
# ----------------------------------------------------------------------
def preview(images, path, scale=8, pad=10):
    w = len(images) * (S * scale + pad) + pad
    h = S * scale + pad * 2 + 14
    im = Image.new("RGB", (w, h), (24, 20, 32))
    for i, img in enumerate(images):
        ox = pad + i * (S * scale + pad)
        im.paste(img.resize((S * scale, S * scale), Image.NEAREST), (ox, pad + 12))
        for gx in range(0, S * scale + 1, scale):
            for gy in range(0, S * scale + 1, scale):
                if gx % (scale * 4) == 0 and gy % (scale * 4) == 0:
                    continue
                im.putpixel((ox + min(gx, S * scale - 1),
                             pad + 12 + min(gy, S * scale - 1)), (38, 40, 54))
    im.save(path)
    print("  preview ->", os.path.relpath(path, ROOT))


if __name__ == "__main__":
    os.makedirs(TEX_DIR, exist_ok=True)
    os.makedirs(MODEL_DIR, exist_ok=True)
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    imgs = []
    print("贴图:")
    for name in ITEMS:
        imgs.append(save_item(BUILDERS[name](), name))
    print("物品模型:")
    for name in ITEMS:
        write_item_model(name)
    preview(imgs, os.path.join(PREVIEW_DIR, "spirit_relics.png"))
