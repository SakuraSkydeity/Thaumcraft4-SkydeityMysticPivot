#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
玄枢魔晶（xuanshu_crystal_cluster）—— **真几何建模**版（12.66 批）。

用户第 2 条：「魔晶我希望是有建模的不是这样贴图直接放上去的，就是我给你的图是一个模型
不是只有贴图」。参考图是 Thaumcraft 的「魔晶簇」。

★ 参考图的真相（解包本体 `thaumcraft-...-20733-sources.jar` 得到）：
  `CrystalClusterModel` 的晶体本体就是**一个 16×16×16 的立方体**，
  由 `CrystalClusterRenderer` 画 **6 份**：每份
      `rotation = R_y(yaw) · R_x(pitch)`，`yaw = random(36) + 72*i`（≈每 72° 一份铺满一圈），
      `pitch = 15..30°`（向外倾），`scale = (0.15~0.225, 0.5~0.6, 0.15~0.225)`
  ⇒ **6 根细长的棱柱，从中心向外张开成一朵扇**。参考图上那种「几片斜张晶片」就是这么来的。

★ 落到方块 JSON 上的限制：元素**一次只能绕一根轴**转，做不出 `R_y·R_x` 这种复合旋转。
  所以本模型用能表达的最好近似：
    * 竖直柱 3 根（其中两根用 22.5° 的 Y 旋转错开朝向，免得三根朝一个方向）；
    * 四根柱从**正中起底**、分别朝 ±X / ±Z **外倾 22.5°** —— 俯视就是一朵向外张开的晶簇。
  每根柱 = 2 段（下段等宽 + 上段收窄到 58%）⇒ 顶部有一点收尖，不是一根根方木棍。
  全部 base 收在中心，所以底部是紧实的一束、越往上张得越开，与参考图同一个读法。

★ 一条硬约束（踩过）：绕底面中心转动会让**外侧底角沉到 y=0 以下**（会和下面的方块打架 /
  z-fighting）。故整根先抬高 `半宽 × |tanθ|`，转完最低点恰好落在 y=0（脚本里会断言）。
"""
import json
import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_DIR = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/block")
MODEL_DIR = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/models/block")
BACKUP_DIR = os.path.join(ROOT, "tools/model_backup")

# ---------------------------------------------------------------- 材质贴图
# 左半 8 列 = 鎏金，右半 8 列 = 深紫。
# 每条 = 一个基色 × 8 档「横向亮度」（左缘最亮 → 右缘最暗，模拟晶棱的受光）+ 「上亮下暗」。
GOLD = (240, 206, 124)
PURPLE = (162, 122, 224)
COL_FACTOR = [1.12, 1.03, 0.94, 0.86, 0.79, 0.72, 0.66, 0.60]
TOP_FADE = 1.00      # v=0（柱尖）的亮度系数
BOT_FADE = 0.58      # v=15（柱根）的亮度系数


def _scale(c, k):
    return tuple(max(0, min(255, int(round(v * k)))) for v in c)


def build_texture():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(16):
        fy = TOP_FADE + (BOT_FADE - TOP_FADE) * (y / 15.0)
        for half, base in ((0, GOLD), (8, PURPLE)):
            for i in range(8):
                px[half + i, y] = _scale(base, COL_FACTOR[i] * fy) + (255,)
    return im


# ---------------------------------------------------------------- 建模
# 一根柱的剖面：下段等宽（62% 高）、上段收窄到 58%（做出「收尖」）
PROFILE = [(0.00, 0.62, 1.00), (0.62, 1.00, 0.58)]

#          name,     cx,   cz,   w,    t,   axis, angle, height, purple, yaw
SHARDS = [
    ("v_core",  8.0, 8.0, 2.6, 2.6, None, 0.0, 11.6, False, 0.0),
    ("v_a",     8.9, 8.4, 2.2, 2.6, None, 0.0,  8.6, True, 22.5),
    ("v_b",     7.1, 7.7, 2.0, 2.4, None, 0.0,  6.6, False, -22.5),
    ("l_xp",    8.0, 8.0, 2.4, 1.8, "z", -22.5, 9.6, True, 0.0),
    ("l_xn",    8.0, 8.0, 2.4, 1.8, "z", 22.5, 10.4, False, 0.0),
    ("l_zp",    8.0, 8.0, 1.8, 2.4, "x", 22.5, 8.8, True, 0.0),
    ("l_zn",    8.0, 8.0, 1.8, 2.4, "x", -22.5, 9.6, False, 0.0),
]


def _r(v):
    return round(v, 2)


def shard(name, cx, cz, w, t, axis, angle, height, purple, yaw, profile=PROFILE):
    """一根向外张的棱柱 -> 一组 element。"""
    els = []
    rad = math.radians(angle)
    half_dir = (w / 2.0) if axis == "z" else (t / 2.0)
    lift = half_dir * abs(math.tan(rad)) if axis else 0.0
    u0, u1 = (8, 16) if purple else (0, 8)
    for i, (f0, f1, k) in enumerate(profile):
        y0 = lift + height * f0
        y1 = lift + height * f1
        bw, bt = w * k, t * k
        v_top = max(0, min(15, int(round(16 * (1 - y1 / height)))))
        v_bot = max(v_top + 1, min(16, int(round(16 * (1 - y0 / height)))))
        el = {
            "name": "%s_%d" % (name, i),
            "from": [_r(cx - bw / 2), _r(y0), _r(cz - bt / 2)],
            "to": [_r(cx + bw / 2), _r(y1), _r(cz + bt / 2)],
            "faces": {},
        }
        if axis:
            el["rotation"] = {"origin": [_r(cx), 0.0, _r(cz)], "axis": axis, "angle": angle}
        elif yaw:
            el["rotation"] = {"origin": [_r(cx), 0.0, _r(cz)], "axis": "y", "angle": yaw}
        for f in ("north", "south", "east", "west"):
            el["faces"][f] = {"texture": "#crystal", "uv": [u0, v_top, u1, v_bot]}
        top_v = 0 if i == len(profile) - 1 else v_top
        el["faces"]["up"] = {"texture": "#crystal", "uv": [u0, top_v, u1, min(16, top_v + 4)]}
        bot_v = 12 if i == 0 else max(0, v_bot - 4)
        el["faces"]["down"] = {"texture": "#crystal",
                               "uv": [u0, bot_v, u1, min(16, bot_v + 4)]}
        els.append(el)
    return els


def build_model():
    elements = []
    for (name, cx, cz, w, t, axis, angle, h, purple, yaw) in SHARDS:
        elements += shard(name, cx, cz, w, t, axis, angle, h, purple, yaw)
    return {
        "parent": "minecraft:block/block",
        "ambientocclusion": False,
        "render_type": "minecraft:cutout",
        "textures": {
            "particle": "skydeitymysticpivot:block/xuanshu_crystal_cluster",
            "crystal": "skydeitymysticpivot:block/xuanshu_crystal_cluster",
        },
        "elements": elements,
    }


def validate(model):
    ok_angles = {-45.0, -22.5, 0.0, 22.5, 45.0}
    errs = []
    for e in model["elements"]:
        r = e.get("rotation")
        if r and float(r["angle"]) not in ok_angles:
            errs.append("bad angle %s: %s" % (e["name"], r["angle"]))
        for fname, f in e["faces"].items():
            uv = f["uv"]
            if any(float(v) != int(v) for v in uv):
                errs.append("non-int uv %s/%s: %s" % (e["name"], fname, uv))
            if min(uv) < 0 or max(uv) > 16:
                errs.append("uv out of range %s/%s: %s" % (e["name"], fname, uv))
    return errs


def lowest_y(model):
    lo = 1e9
    for e in model["elements"]:
        frm, to = e["from"], e["to"]
        r = e.get("rotation")
        for cx in (frm[0], to[0]):
            for cy in (frm[1], to[1]):
                for cz in (frm[2], to[2]):
                    x, y, z = cx, cy, cz
                    if r:
                        ox, oy, oz = r["origin"]
                        x, y, z = x - ox, y - oy, z - oz
                        a = math.radians(r["angle"])
                        c, s = math.cos(a), math.sin(a)
                        if r["axis"] == "x":
                            y, z = y * c - z * s, y * s + z * c
                        elif r["axis"] == "z":
                            x, y = x * c - y * s, x * s + y * c
                        x, y, z = x + ox, y + oy, z + oz
                    lo = min(lo, y)
    return lo


def main():
    os.makedirs(BACKUP_DIR, exist_ok=True)
    tex_path = os.path.join(TEX_DIR, "xuanshu_crystal_cluster.png")
    model_path = os.path.join(MODEL_DIR, "xuanshu_crystal_cluster.json")

    for p in (tex_path, model_path):
        if os.path.exists(p):
            bak = os.path.join(BACKUP_DIR, os.path.basename(p) + ".bak66")
            if not os.path.exists(bak):
                with open(p, "rb") as f:
                    data = f.read()
                with open(bak, "wb") as f:
                    f.write(data)
                print("backup ->", bak)

    im = build_texture()
    im.save(tex_path)
    print("texture ->", tex_path, im.size)

    model = build_model()
    errs = validate(model)
    if errs:
        for e in errs:
            print("VALIDATE FAIL:", e)
        raise SystemExit(1)
    lo = lowest_y(model)
    print("lowest y after rotation = %.3f" % lo)
    if lo < -0.001:
        raise SystemExit("模型最低点沉到 y=0 以下，会和下面的方块打架")
    with open(model_path, "w", encoding="utf-8") as f:
        json.dump(model, f, ensure_ascii=False, indent=2)
    print("model ->", model_path, "| elements:", len(model["elements"]))
    print("validate: OK (angles / uv / y>=0)")

    im.resize((16 * 14, 16 * 14), Image.NEAREST).save(
        os.path.join(ROOT, "tools/preview/crystal_tex.png"))


if __name__ == "__main__":
    main()
