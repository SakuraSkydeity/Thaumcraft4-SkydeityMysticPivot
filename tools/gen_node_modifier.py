#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""玄枢注灵台的**方块模型 + 贴图**（仿原版附魔台的「台」形制）。

历史：第八十八批是「司南」物品，第八十九批改成方块（OBJ 司南台），第九十六批换**香炉** OBJ，
第九十七批修好它的「全黑 + 透视」，第九十八批**OBJ 整套退役**改成 `cube_bottom_top` 整格立方体。

第一百批用户原话：「将灵气节点修改器改为像原版附魔台一样不是一整个方块 你帮我设计」。
于是照原版附魔台的思路重做：**只有 12/16 高**，方块碰撞箱收到 `box(0,0,0,16,12,16)`。

第一百零二批：「注灵台上面不要那一坨小的」——台面正中那尊小香炉整组删掉。

第一百五十三批用户两句：
  ① 「注灵台的四周柱子往里收一点」——四根角柱由 0~3 / 13~16 收到 1~4 / 12~15，
     整根柱子退到台面外沿以内，四角各露一道挑檐；
  ② 「然后贴图再美化 整体鎏金色」——青玉为体的那版整体换成**鎏金**：
     金为面、暗金为缝、亮金为棱，只在正中留一点灵光当「神」。
     （第一百零三到一百零五批那套「白玉 / 青玉」配色整组退役。）

形制（三层收分台座 + 四根角柱 + 一圈挑檐，全部轴对齐方盒、JSON 模型、零旋转）：
  * 底座 `[0,0,0]→[16,2,16]`：外沿一圈，落地稳。
  * 台身 `[1,2,1]→[15,10,15]`：收进 1 格，形成「束腰」。
  * 角柱 ×4 `[1,2,1]→[4,10,4]` 等：包住束腰四角，柱面錾暗金缝。
  * 挑檐 ×4 `[0,10,0]→[16,12,3]` 等：台面四边外挑 1 格，围出中间的方口。
  * 台面顶面用 `_top`（金盘 + 中心灵珠），底面 `_bottom`（压暗的金）。

贴图三张 16×16：`_side`（鎏金砖面）/ `_top`（金盘灵珠）/ `_bottom`（暗金底）。
UV 分段：0~2 行给底座与挑檐的侧沿，2~10 行给台身与角柱。

本脚本同时**生成模型 JSON**（UV 与元素一一对应，手写容易错位）。
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot")
BLOCK_TEX_DIR = os.path.join(ASSETS, "textures/block")
MODEL_DIR = os.path.join(ASSETS, "models/block")
PREVIEW = os.path.join(ROOT, "tools/preview")

TEX = 16
NS = "skydeitymysticpivot"

# 第一百五十三批：青玉 → 鎏金（金为面、暗金为缝、亮金为棱，灵光只留一点）
GOLD = (206, 162, 74)
GOLD_L = (242, 210, 132)
GOLD_D = (146, 102, 38)
GOLD_S = (110, 74, 26)
DARK = (54, 42, 26)
GLOW = (255, 214, 120)
GLOW_L = (255, 244, 208)


# ----------------------------------------------------------------------
# 贴图
# ----------------------------------------------------------------------

def base(color, seed, jitter=5):
    rng = random.Random(seed)
    im = Image.new("RGBA", (TEX, TEX))
    px = im.load()
    for y in range(TEX):
        for x in range(TEX):
            j = rng.randint(-jitter, jitter)
            px[x, y] = tuple(max(0, min(255, c + j)) for c in color) + (255,)
    return im


def rect(px, x0, y0, x1, y1, color):
    for y in range(y0, y1):
        for x in range(x0, x1):
            if 0 <= x < TEX and 0 <= y < TEX:
                px[x, y] = color


def ring_at(px, cx, cy, r, color, w=0.9):
    for y in range(TEX):
        for x in range(TEX):
            if abs(math.hypot(x + 0.5 - cx, y + 0.5 - cy) - r) < w:
                px[x, y] = color


def ring(px, r, color, w=0.9):
    for y in range(TEX):
        for x in range(TEX):
            if abs(math.hypot(x + 0.5 - 8.0, y + 0.5 - 8.0) - r) < w:
                px[x, y] = color


def sheen(px, seed, n=9):
    """鎏金的「反光点」：几粒亮金随机洒在面上，看着像灯光扫过錾面。"""
    rng = random.Random(seed)
    for _ in range(n):
        x = rng.randint(1, 14)
        y = rng.randint(3, 9)
        px[x, y] = GOLD_L if rng.random() < 0.6 else GOLD


def gen_side():
    """台身 / 角柱侧面：**整面鎏金**。

    <p>0~2 行是底座与挑檐共用的上沿（亮金棱 + 金面 + 暗金缝），
    2~10 行是台身：金面打底，中缝一道暗金，上下各一道錾线，四角两枚亮金铆钉。</p>
    """
    im = base(GOLD, 11, jitter=5)
    px = im.load()
    sheen(px, 21)
    # 0~2：上沿（亮金棱 → 金面 → 暗金缝）
    rect(px, 0, 0, 16, 1, GOLD_L)
    rect(px, 0, 1, 16, 2, GOLD)
    rect(px, 0, 2, 16, 3, GOLD_D)
    # 2~10：台身。左右各一列暗金当转角阴影，底边一道暗金收口
    rect(px, 0, 3, 1, 10, GOLD_D)
    rect(px, 15, 3, 16, 10, GOLD_D)
    rect(px, 0, 9, 16, 10, GOLD_S)
    # 顶部一道亮金錾线（把台身与上沿分开）
    rect(px, 1, 3, 15, 4, GOLD_L)
    # 中缝：竖着一道暗金，左右各留半格金面
    rect(px, 7, 4, 8, 9, GOLD_D)
    # 上下两道錾线
    rect(px, 1, 5, 15, 6, GOLD_S)
    rect(px, 1, 8, 15, 9, GOLD_D)
    # 四角铆钉：2×2 亮金 + 1 像素暗影，錾面立刻有「钉」的感觉
    for (rx, ry) in ((2, 6), (12, 6)):
        rect(px, rx, ry, rx + 2, ry + 2, GOLD_L)
        px[rx + 1, ry + 1] = GOLD
        px[rx, ry + 2] = GOLD_S
    return im


def gen_top():
    """台面：一整块**金盘**，正中一点灵光。

    <p>五层靠半径拉开：暗金外框 → 亮金盘 → 6.2 暗金环 → 5.1 八向卦刻 → 4.1 金环 →
    中心灵珠（暖金芯，替掉原来那颗紫珠）。</p>
    """
    im = base(GOLD_L, 12, jitter=4)
    px = im.load()
    sheen(px, 22, 12)
    # 外框
    rect(px, 0, 0, 16, 1, GOLD_D)
    rect(px, 0, 15, 16, 16, GOLD_D)
    rect(px, 0, 0, 1, 16, GOLD_D)
    rect(px, 15, 0, 16, 16, GOLD_D)
    # 外暗金环 / 内金环
    ring(px, 6.2, GOLD_D)
    ring(px, 4.1, GOLD, 0.6)
    # 八向卦刻：两环之间的单像素刻痕
    for k in range(8):
        a = math.pi * 2 * k / 8
        x = int(round(8.0 + math.cos(a) * 5.15))
        y = int(round(8.0 + math.sin(a) * 5.15))
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = GOLD_S
    # 四角云卷：各两笔
    for (sx, sy) in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        px[8 + sx * 3, 8 + sy * 3] = GOLD_D
        px[8 + sx * 2, 8 + sy * 3] = GOLD_S
    # 中心灵珠：暖金核
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - 8.0, y + 0.5 - 8.0)
            if d <= 1.8:
                px[x, y] = GLOW if d > 1.1 else GLOW_L
    px[7, 7] = GOLD_L
    px[8, 8] = GOLD_S
    return im


def gen_bottom():
    """底面：压暗的金 + 一圈暗金 + 三点足印。"""
    im = base(GOLD_D, 13, jitter=5)
    px = im.load()
    rect(px, 0, 0, 16, 1, DARK)
    rect(px, 0, 15, 16, 16, DARK)
    rect(px, 0, 0, 1, 16, DARK)
    rect(px, 15, 0, 16, 16, DARK)
    ring(px, 5.4, GOLD_S, 0.8)
    rect(px, 4, 11, 6, 14, GOLD_S)
    rect(px, 10, 11, 12, 14, GOLD_S)
    rect(px, 7, 3, 9, 6, GOLD_S)
    return im


# ----------------------------------------------------------------------
# 模型 JSON
# ----------------------------------------------------------------------

def faces(tex, uv_side=None, uv_up=None, uv_down=None, skip=(), cull=None,
          tex_up=None, tex_down=None):
    """生成一个元素的 faces 字典；uv 一律整数且在 [0,16]。"""
    cull = cull or {}
    out = {}
    for name in ("down", "up", "north", "south", "west", "east"):
        if name in skip:
            continue
        if name == "up":
            uv = uv_up if uv_up is not None else (uv_side or [0, 0, 16, 16])
            t = tex_up or tex
        elif name == "down":
            uv = uv_down if uv_down is not None else (uv_side or [0, 0, 16, 16])
            t = tex_down or tex
        else:
            uv = uv_side if uv_side is not None else [0, 0, 16, 16]
            t = tex
        face = {"uv": list(uv), "texture": t}
        if name in cull:
            face["cullface"] = cull[name]
        out[name] = face
    return out


#: 角柱由外沿收到「离边 1 格」（第一百五十三批：四周柱子往里收一点）
#: 台身跟着由 1~15 再收一格到 2~14，角柱才凸得出 1 像素、看得见「柱」
POST_LO = 1
POST_HI = 4
BODY_LO = 2
POST_HI_MIRROR = 16 - POST_LO      # 15
POST_LO_MIRROR = 16 - POST_HI      # 12


def build_model():
    posts = [
        ("post_nw", POST_LO, POST_LO),
        ("post_ne", POST_LO_MIRROR, POST_LO),
        ("post_sw", POST_LO, POST_LO_MIRROR),
        ("post_se", POST_LO_MIRROR, POST_LO_MIRROR),
    ]
    elements = [
        {   # 底座：外沿一圈（顶面被台身压住，只留 1 格边）
            "name": "base",
            "from": [0, 0, 0],
            "to": [16, 2, 16],
            "faces": faces("#side", uv_side=[0, 0, 16, 2], uv_up=[0, 0, 16, 16],
                           uv_down=[0, 0, 16, 16], cull={"down": "down"}),
        },
        {   # 台身：收进 2 格，四角留给角柱凸出 1 像素（第一百五十三批）
            "name": "body",
            "from": [BODY_LO, 2, BODY_LO],
            "to": [16 - BODY_LO, 10, 16 - BODY_LO],
            "faces": faces("#side", uv_side=[BODY_LO, 2, 16 - BODY_LO, 10], skip=("up", "down")),
        },
    ]
    for name, x, z in posts:
        elements.append({
            "name": name,
            "from": [x, 2, z],
            "to": [x + POST_HI - POST_LO, 10, z + POST_HI - POST_LO],
            "faces": faces("#side", uv_side=[0, 2, 3, 10], skip=("up", "down")),
        })
    # 挑檐：四边各一条，围出中间的方口（外沿仍是整格 0~16）
    bars = [
        ("ring_north", [0, 10, 0], [16, 12, 3], [0, 0, 16, 2]),
        ("ring_south", [0, 10, 13], [16, 12, 16], [0, 0, 16, 2]),
        ("ring_west", [0, 10, 3], [3, 12, 13], [0, 0, 16, 2]),
        ("ring_east", [13, 10, 3], [16, 12, 13], [0, 0, 16, 2]),
    ]
    for name, frm, to, uv in bars:
        is_top = name in ("ring_north", "ring_south")
        elements.append({
            "name": name,
            "from": frm,
            "to": to,
            "faces": faces("#side", uv_side=uv,
                           uv_up=[0, 0, 16, 16] if is_top else [0, 10, 16, 12],
                           uv_down=[0, 0, 16, 16],
                           tex_up="#top", tex_down="#bottom"),
        })
    return {
        "parent": "block/block",
        "ambientocclusion": False,
        "textures": {
            "particle": NS + ":block/xuanshu_node_modifier",
            "side": NS + ":block/xuanshu_node_modifier",
            "top": NS + ":block/xuanshu_node_modifier_top",
            "bottom": NS + ":block/xuanshu_node_modifier_bottom",
        },
        "elements": elements,
    }


def main():
    os.makedirs(PREVIEW, exist_ok=True)
    jobs = (
        ("xuanshu_node_modifier", gen_side),
        ("xuanshu_node_modifier_top", gen_top),
        ("xuanshu_node_modifier_bottom", gen_bottom),
    )
    for name, fn in jobs:
        dst = os.path.join(BLOCK_TEX_DIR, name + ".png")
        fn().save(dst)
        print("->", os.path.relpath(dst, ROOT))

    model_path = os.path.join(MODEL_DIR, "xuanshu_node_modifier.json")
    with open(model_path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(build_model(), fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    print("->", os.path.relpath(model_path, ROOT))

    # 四联贴图预览
    cell = TEX * 18
    canvas = Image.new("RGBA", (cell * 4 + 50, cell + 20), (24, 20, 32, 255))
    for i, (name, _) in enumerate(jobs):
        im = Image.open(os.path.join(BLOCK_TEX_DIR, name + ".png"))
        canvas.paste(im.resize((cell, cell), Image.NEAREST), (10 + i * (cell + 10), 10))
    canvas.save(os.path.join(PREVIEW, "node_modifier_tex.png"))
    print("preview ->", os.path.relpath(os.path.join(PREVIEW, "node_modifier_tex.png"), ROOT))


if __name__ == "__main__":
    main()
