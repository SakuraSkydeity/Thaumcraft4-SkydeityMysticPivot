# -*- coding: utf-8 -*-
"""第二百二十四批：研究树重排（星辰链回左侧 / 玄枢机器汇到锭后 / 整体拉开成弧）。

用法：python tools/_tree_v2.py
输出：指标（交叉 / 穿点 / 边长 / 最近点距）+ build/_crop/tree_v2.png 预览
"""
import json
import io
import os
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src/main/resources/data/skydeitymysticpivot/thaumcraft/research/bagua.json")
LANG = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/lang/zh_cn.json")

d = json.load(io.open(SRC, encoding="utf-8"))
par = {e["key"].split(":")[-1]: [p.split(":")[-1] for p in e["parents"]] for e in d["entries"]}
lang = json.load(io.open(LANG, encoding="utf-8"))
name = {k.split(":")[-1]: v for k, v in lang.items()
        if k.startswith("tc.research_name.skydeitymysticpivot:")}

CAND = {
    # 根与八卦环（半径 2 的八方位）
    "BAGUA": (-4, 2),
    "PEDESTAL": (0, 0),
    "GUA_DUI": (-2, -2), "GUA_QIAN": (0, -2), "GUA_XUN": (2, -2),
    "GUA_LI": (-2, 0), "GUA_KAN": (2, 0),
    "GUA_ZHEN": (-2, 2), "GUA_KUN": (0, 2), "GUA_GEN": (2, 2),
    "TERRAIN": (-2, 5), "GUA_STONES": (-4, 8),

    # 右半：碎片扇出（三炉 / 布匹法杖 / 工具）
    "INGOTS": (5, -1), "SHARDS": (9, 0),
    "MACHINE_FURNACE": (10, -5), "MACHINE_CRUCIBLE": (13, -4), "MACHINE_ALCHEMY": (14, -1),
    "CLOTHS": (14, 1), "WANDS": (13, 4), "CAPS": (10, 5), "WAND_ASSEMBLY": (13, 7),
    "BAGUA_PICKAXE": (7, 6), "XUANSHU_PICKAXE": (9, 9), "XUANSHU_HOE": (5, 9),

    # 四法核：绕艮卦甩出去的一圈弧
    "FOCUS_GUARDIAN": (5, 4), "FOCUS_CHRONO": (4, 7), "FOCUS_COSMOS": (2, 9),
    "FOCUS_TELEPORT": (-1, 9),

    # 左链：星辰合成 → 未烬锭 → 玄枢锭 → 玄枢机器弧
    "STAR_CRAFT": (-7, 2), "XUANSHU_INGOT_UNFIRED": (-8, 4), "XUANSHU_INGOT_FORGED": (-9, 6),
    "XUANSHU_SPIRIT_CORE": (-12, 6), "XUANSHU_NODE_MODIFIER": (-14, 4),
    "XUANSHU_ARCANE_TABLE": (-15, 7), "XUANSHU_CHARGER": (-9, 9),
    "PURIFYING_STONE": (-12, 9),
}
CAND.pop("XUANSHU_PURIFYING_STONE_PLACEHOLDER", None)
CAND["PURIFYING_STONE"] = (-12, 8)
CAND["XUANSHU_CHARGER"] = (-9, 8)

pos = CAND
# 本批改的父子关系：玄枢机器统一挂到「玄枢锭」后面
PARENT_NEW = {
    "STAR_CRAFT": ["BAGUA"],
    "XUANSHU_SPIRIT_CORE": ["XUANSHU_INGOT_FORGED"],
    "XUANSHU_CHARGER": ["XUANSHU_INGOT_FORGED"],
}
par.update(PARENT_NEW)
assert set(pos) == set(par), set(par) ^ set(pos)
edges = [(p, c) for c, ps in par.items() for p in ps]


def ccw(a, b, c):
    return (c[1] - a[1]) * (b[0] - a[0]) - (b[1] - a[1]) * (c[0] - a[0])


def inter(a, b, c, dd):
    d1, d2, d3, d4 = ccw(a, b, c), ccw(a, b, dd), ccw(c, dd, a), ccw(c, dd, b)
    return (d1 * d2 < 0) and (d3 * d4 < 0)


def seg_dist(p, a, b):
    px_, py_ = p
    ax, ay = a
    bx, by = b
    dx, dy = bx - ax, by - ay
    L = dx * dx + dy * dy
    t = 0 if L == 0 else max(0, min(1, ((px_ - ax) * dx + (py_ - ay) * dy) / L))
    qx, qy = ax + t * dx, ay + t * dy
    return ((px_ - qx) ** 2 + (py_ - qy) ** 2) ** 0.5


cross = [(e1, e2) for i, e1 in enumerate(edges) for e2 in edges[i + 1:]
         if len({e1[0], e1[1], e2[0], e2[1]}) == 4 and inter(pos[e1[0]], pos[e1[1]], pos[e2[0]], pos[e2[1]])]
near = [(a, b, n, round(seg_dist(p, pos[a], pos[b]), 2)) for a, b in edges for n, p in pos.items()
        if n not in (a, b) and seg_dist(p, pos[a], pos[b]) < 0.75]
longs = sorted(((round(((pos[a][0] - pos[b][0]) ** 2 + (pos[a][1] - pos[b][1]) ** 2) ** 0.5, 2), a, b)
                for a, b in edges), reverse=True)[:6]
keys = list(pos)
mind = min(((round(((pos[a][0] - pos[b][0]) ** 2 + (pos[a][1] - pos[b][1]) ** 2) ** 0.5, 2), a, b)
            for i, a in enumerate(keys) for b in keys[i + 1:]))

print("交叉:", len(cross))
for e in cross:
    print("   ", e[0], "x", e[1])
print("贴线(<0.75):", len(near))
for a, b, n, dd in near:
    print("    %-22s→%-22s 蹭 %-22s %.2f" % (a, b, n, dd))
print("最长边:", longs)
print("最近两点:", mind)

# 渲染（浅色底，中文标签）
S, PAD, R = 120, 110, 34
cols = [c for c, _ in pos.values()]
rows = [r for _, r in pos.values()]
c0, c1, r0, r1 = min(cols), max(cols), min(rows), max(rows)
W, H = PAD * 2 + (c1 - c0) * S, PAD * 2 + (r1 - r0) * S
img = Image.new("RGB", (W, H), (247, 244, 236))
dr = ImageDraw.Draw(img)
px = lambda p: (PAD + (p[0] - c0) * S, PAD + (p[1] - r0) * S)
for c in range(c0, c1 + 1):
    dr.line([(PAD + (c - c0) * S, 0), (PAD + (c - c0) * S, H)], fill=(232, 226, 212))
for r in range(r0, r1 + 1):
    dr.line([(0, PAD + (r - r0) * S), (W, PAD + (r - r0) * S)], fill=(232, 226, 212))
for a, b in edges:
    dr.line([px(pos[a]), px(pos[b])], fill=(150, 140, 120), width=3)

try:
    font = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 20)
    font2 = ImageFont.truetype("C:/Windows/Fonts/msyhbd.ttc", 21)
except Exception:
    font = font2 = ImageFont.load_default()

for k, p in pos.items():
    x, y = px(p)
    trig = k.startswith("GUA_")
    dr.ellipse([x - R, y - R, x + R, y + R],
               fill=(244, 232, 198) if trig else (196, 208, 224),
               outline=(40, 40, 48), width=3)
    lb = name.get(k, k)
    tw = dr.textlength(lb, font=font2 if not trig else font)
    dr.text((x - tw / 2, y + R + 6), lb, fill=(30, 30, 36), font=font2 if not trig else font)

os.makedirs(os.path.join(ROOT, "build/_crop"), exist_ok=True)
out = os.path.join(ROOT, ".none/lecture/研究树新布局-224.png")
img.save(out)
print("已渲染", out, img.size)
