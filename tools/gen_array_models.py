#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
八方净界阵「激活态」建模（12.59 批按用户截图反馈重做）。

用户原话：
  「净界阵不要我红色框起来的柱子和柱子上面的部分（尖尖的需要），
    下面的贴图修改加点符文的画风，然后光影协调一点」

即：去掉四角柱 + 浮空金环（上一批 12.58 加的），只留**基座台 + 尖尖的能量晶**；
基座贴图加符文画风；明暗对比放软。

设计：
  - 基座：满宽 16×16×4 石台，九块连成一片坛面；侧面一道金檐 + 竖排符文柱，
    顶面一朵符文圆印（金框 + 圆环 + 四放射刻 + 中央菱印）。
  - 能量晶：台中悬浮的三段收敛晶，与台面留 1.5 格悬浮间隙。
  - 阵心：同语言，台中晶柱更高，顶端出格到 y20.5。
  - ambientocclusion 打开（默认），让台面转角有环境光遮蔽，光影更协调。

贴图布局（16×16）：
  左半 x0-7  侧面（横向色带设计：金檐 / 浅青 / 石身符文 / 深基）
  右上 x8-15,y0-7   顶面符文圆印（up 面采样 8,0,16,8，2× 均匀拉伸，圆不变形）
  右下 x8-15,y8-15  备用（粒子/点缀），竖排符文
"""
import json
import os
import shutil

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_DIR = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/block")
MODEL_DIR = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/models/block")
BS_DIR = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/blockstates")
BACKUP_DIR = os.path.join(ROOT, "tools/model_backup")

GUAS = ["qian", "dui", "li", "zhen", "xun", "kan", "gen", "kun"]

# 青金配色（照核心净界石激活贴图提取），这批把对比放软：
# 金檐不再用刺眼的近白，深基不再近黑。
GOLD = (222, 188, 96)        # 金檐
GOLD_HI = (240, 214, 140)    # 檐上高光
GOLD_DK = (164, 130, 58)     # 金饰暗侧
TEAL_LIT = (150, 208, 198)   # 浅青（符文亮部）
TEAL_SOFT = (96, 146, 138)   # 青灰过渡
TEAL_BODY = (52, 96, 90)     # 石身
TEAL_DARK = (36, 68, 72)     # 深基
TEAL_DEEP = (26, 50, 56)     # 基底
TEAL_HI = (238, 250, 246)    # 符文芯（近白青）


def backup(path):
    if os.path.exists(path):
        bak = os.path.join(BACKUP_DIR, os.path.basename(path) + ".bak59")
        if not os.path.exists(bak):
            shutil.copy2(path, bak)
            print("  backup ->", os.path.basename(bak))


# ---------------------------------------------------------------- 贴图
def build_stone_texture():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    px = im.load()

    def put(x, y, c):
        px[x, y] = c + (255,)

    # ---- 左半：侧面横向色带（贴到 16×4 的台面上会被压扁 4 倍，所以按「带」设计） ----
    for y in range(16):
        for x in range(8):
            if y == 0:
                c = GOLD_HI
            elif y == 1:
                c = GOLD
            elif y <= 3:
                c = TEAL_LIT
            elif y <= 12:
                c = TEAL_BODY
                if x in (1, 6):
                    c = TEAL_SOFT                      # 竖棱
            elif y <= 14:
                c = TEAL_DARK
            else:
                c = TEAL_DEEP
            put(x, y, c)
    # 竖排符文柱：两道，各是一根亮线 + 一道横杠 + 一点（卦位石的「符」）
    for x0 in (1, 5):
        for y in range(4, 13):
            put(x0, y, TEAL_LIT)
        put(x0, 7, GOLD_HI)
        put(x0 + 1, 7, GOLD_HI)
        put(x0, 9, TEAL_HI)
        put(x0 + 1, 9, TEAL_HI)

    # ---- 右上：顶面符文圆印 ----
    for y in range(8):
        for x in range(8, 16):
            put(x, y, TEAL_BODY)
    for x in range(8, 16):          # 金框
        put(x, 0, GOLD)
        put(x, 7, GOLD)
    for y in range(8):
        put(8, y, GOLD)
        put(15, y, GOLD)
    for y in range(1, 7):           # 内圈提亮一点，像磨光的石面
        for x in range(9, 15):
            put(x, y, tuple(min(255, v + 8) for v in TEAL_BODY))
    cx, cy = 11.5, 3.5
    for y in range(1, 7):           # 圆环 + 四放射刻 + 中央菱印
        for x in range(9, 15):
            dx, dy = x - cx, y - cy
            d = (dx * dx + dy * dy) ** 0.5
            if 1.9 < d < 2.6:
                put(x, y, GOLD_HI)
            elif d <= 1.9 and abs(dx) + abs(dy) <= 1.0:
                put(x, y, TEAL_HI)
    for dx, dy in [(0, -3), (0, 3), (-3, 0), (3, 0)]:   # 四放射刻
        put(int(cx + dx), int(cy + dy), GOLD_HI)
    for dx, dy in [(-3, -3), (3, -3), (-3, 3), (3, 3)]:  # 四角金点
        put(int(cx + dx), int(cy + dy), GOLD)

    # ---- 右下：备用区，竖排符文（粒子会采样到） ----
    for y in range(8, 16):
        for x in range(8, 16):
            put(x, y, TEAL_DARK if y < 14 else TEAL_DEEP)
    for x0 in (10, 13):
        for y in range(8, 16):
            put(x0, y, TEAL_SOFT)
        put(x0, 11, GOLD)
        put(x0 + 1, 11, GOLD)
    return im


def build_energy_texture():
    """能量晶：上白下青的竖直渐变 + 六道竖向晶棱（让晶柱看出「面」来）。"""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 255))
    px = im.load()
    ramp = [(246, 255, 252), (214, 250, 244), (172, 236, 224), (140, 220, 206),
            (96, 186, 172), (62, 138, 128), TEAL_DARK, TEAL_DEEP]
    # 每列的明暗偏移：正中两列最亮（主棱），两侧对称地排开三对次棱，最外两列压暗
    col = [-20, -12, -5, 10, 13, -3, -8, 22, 22, -8, -3, 13, 10, -5, -12, -20]
    for y in range(16):
        base = ramp[min(7, y // 2)]
        for x in range(16):
            px[x, y] = tuple(max(0, min(255, v + col[x])) for v in base) + (255,)
    return im


# ---------------------------------------------------------------- 模型
def face(tex, uv):
    return {"texture": tex, "uv": list(uv)}


def solid(name, frm, to, tex, uv_side=(0, 0, 16, 16), uv_up=None, with_down=False):
    """element：四侧面 + up（+ 可选 down）。"""
    f = {k: face(tex, uv_side) for k in ("north", "south", "east", "west")}
    f["up"] = face(tex, uv_up if uv_up is not None else uv_side)
    if with_down:
        f["down"] = face(tex, uv_side)
    return {"name": name, "from": list(frm), "to": list(to), "faces": f}


def plate(name, frm, to, tex, uv):
    """薄片（箍/环）：六面全写，薄归薄，从缝里看不会漏。"""
    return {
        "name": name, "from": list(frm), "to": list(to),
        "faces": {k: face(tex, uv) for k in ("north", "south", "east", "west", "up", "down")},
    }


def station_elements():
    """八方站石：只剩基座台（12.59b 批用户：「尖刺就留中间」，外圈不再插晶）。"""
    E = []
    E.append(solid("plat", (0, 0, 0), (16, 4, 16), "#stone",
                   uv_side=(0, 0, 8, 16), uv_up=(8, 0, 16, 8), with_down=True))
    return E


def core_elements():
    """阵心：基座台 + 加大加华丽的能量晶尖（12.63 批：更高、更宽、三道金箍 + 四道晶鳍）。

    用户「多方块中间的尖刺改的大一点华丽一点」。上一批是四段收到 y18.5；
    这批：五段收到 y22.5（出格 6.5 格），底段由 6.0 宽放到 6.8 宽，
    金箍由两道加到三道，另在腰段四面各贴一道**晶鳍**（薄板，比柱身宽出 1.2 格）。
    """
    E = []
    E.append(solid("plat", (0, 0, 0), (16, 4, 16), "#stone",
                   uv_side=(0, 0, 8, 16), uv_up=(8, 0, 16, 8), with_down=True))
    # 晶柱五段：宽基座 -> 三次收分 -> 针尖；uv 跨整根连续渐变（v 越小白越亮）
    E.append(solid("spire0", (4.6, 4.0, 4.6), (11.4, 8.5, 11.4), "#energy",
                   uv_side=(0, 13, 16, 16), with_down=True))
    E.append(solid("spire1", (5.2, 8.5, 5.2), (10.8, 14.0, 10.8), "#energy",
                   uv_side=(0, 8, 16, 13)))
    E.append(solid("spire2", (6.0, 14.0, 6.0), (10.0, 17.5, 10.0), "#energy",
                   uv_side=(0, 5, 16, 8)))
    E.append(solid("spire3", (6.8, 17.5, 6.8), (9.2, 21.0, 9.2), "#energy",
                   uv_side=(0, 2, 16, 5)))
    E.append(solid("spire4", (7.5, 21.0, 7.5), (8.5, 24.0, 8.5), "#energy",
                   uv_side=(0, 0, 16, 2)))
    # 四道晶鳍：贴在腰段（spire1）四面，比柱身宽出 1.2 格，给「华丽」加料
    E.append(plate("fin_n", (6.4, 9.0, 4.4), (9.6, 15.0, 5.6), "#energy", (0, 8, 16, 13)))
    E.append(plate("fin_s", (6.4, 9.0, 10.4), (9.6, 15.0, 11.6), "#energy", (0, 8, 16, 13)))
    E.append(plate("fin_w", (4.4, 9.0, 6.4), (5.6, 15.0, 9.6), "#energy", (0, 8, 16, 13)))
    E.append(plate("fin_e", (10.4, 9.0, 6.4), (11.6, 15.0, 9.6), "#energy", (0, 8, 16, 13)))
    # 三道金箍（比柱身宽半格，浮在柱面上）
    E.append(plate("band_lo", (4.8, 10.2, 4.8), (11.2, 10.7, 11.2), "#gold", (0, 0, 16, 4)))
    E.append(plate("band_mid", (5.6, 15.2, 5.6), (10.4, 15.7, 10.4), "#gold", (0, 0, 16, 4)))
    E.append(plate("band_hi", (6.5, 20.2, 6.5), (9.5, 20.7, 9.5), "#gold", (0, 0, 16, 4)))
    return E


def build_gold_texture():
    """金箍贴图：两行高光 + 一行金 + 一行暗金（箍有厚度），供晶柱腰间的箍用。"""
    im = Image.new("RGBA", (16, 16), GOLD + (255,))
    px = im.load()
    for x in range(16):
        px[x, 0] = GOLD_HI + (255,)
        px[x, 1] = GOLD_HI + (255,)
        px[x, 2] = GOLD + (255,)
        px[x, 3] = GOLD_DK + (255,)
    return im


def wrap(elements, particle):
    return {
        "parent": "minecraft:block/block",
        # ambientocclusion 用默认（true）：台面转角有环境光遮蔽，光影更协调
        "render_type": "minecraft:cutout",
        "textures": {
            "particle": particle,
            "stone": "skydeitymysticpivot:block/array_station_active",
            "energy": "skydeitymysticpivot:block/array_energy_active",
            "gold": "skydeitymysticpivot:block/array_gold_band",
        },
        "elements": elements,
    }


def validate(model, path):
    ok_angles = {-45.0, -22.5, 0.0, 22.5, 45.0}
    errs = []
    for e in model["elements"]:
        r = e.get("rotation")
        if r and float(r["angle"]) not in ok_angles:
            errs.append("bad angle %s: %s" % (e["name"], r["angle"]))
        for fn, f in e["faces"].items():
            uv = f["uv"]
            if any(float(v) != int(v) for v in uv):
                errs.append("non-int uv %s/%s: %s" % (e["name"], fn, uv))
            if min(uv) < 0 or max(uv) > 16:
                errs.append("uv out of range %s/%s: %s" % (e["name"], fn, uv))
    if errs:
        for e in errs:
            print("VALIDATE FAIL", path, e)
        raise SystemExit(1)


def main():
    os.makedirs(BACKUP_DIR, exist_ok=True)

    stone = build_stone_texture()
    stone.save(os.path.join(TEX_DIR, "array_station_active.png"))
    print("texture -> array_station_active.png", stone.size)
    energy = build_energy_texture()
    energy.save(os.path.join(TEX_DIR, "array_energy_active.png"))
    print("texture -> array_energy_active.png", energy.size)
    gold = build_gold_texture()
    gold.save(os.path.join(TEX_DIR, "array_gold_band.png"))
    print("texture -> array_gold_band.png", gold.size)

    station = wrap(station_elements(), "skydeitymysticpivot:block/array_station_active")
    validate(station, "array_station_active.json")
    with open(os.path.join(MODEL_DIR, "array_station_active.json"), "w", encoding="utf-8") as f:
        json.dump(station, f, ensure_ascii=False, indent=2)
    print("model -> array_station_active.json | elements:", len(station["elements"]))

    core = wrap(core_elements(), "skydeitymysticpivot:block/array_station_active")
    validate(core, "xuanshu_purifying_stone_active.json")
    p = os.path.join(MODEL_DIR, "xuanshu_purifying_stone_active.json")
    backup(p)
    with open(p, "w", encoding="utf-8") as f:
        json.dump(core, f, ensure_ascii=False, indent=2)
    print("model -> xuanshu_purifying_stone_active.json | elements:", len(core["elements"]))

    # blockstate 结构不变（active=true 仍指 array_station_active / 阵心模型），无需重写
    print("blockstates unchanged（active=true 指向未变）")


if __name__ == "__main__":
    main()
