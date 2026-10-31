# -*- coding: utf-8 -*-
"""候选布局评估：写入 positions 后校验交叉 / 穿点 / 边长，并渲染预览。"""
import json
import io
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src/main/resources/data/skydeitymysticpivot/thaumcraft/research/bagua.json")
d = json.load(io.open(SRC, encoding="utf-8"))
par = {e["key"].split(":")[-1]: [p.split(":")[-1] for p in e["parents"]] for e in d["entries"]}

CAND = {
    "BAGUA": (-4, 2),
    "PEDESTAL": (0, 0),
    "GUA_QIAN": (0, -2), "GUA_DUI": (-1, -1), "GUA_XUN": (1, -1), "GUA_LI": (-2, 0),
    "GUA_KAN": (2, 0), "GUA_ZHEN": (-1, 1), "GUA_GEN": (1, 1), "GUA_KUN": (0, 2),
    "INGOTS": (2, -1), "SHARDS": (5, 0),
    "MACHINE_FURNACE": (3, -2), "MACHINE_ALCHEMY": (4, -2), "MACHINE_CRUCIBLE": (5, -2),
    "XUANSHU_CHARGER": (4, -4),
    "BAGUA_PICKAXE": (5, 2), "XUANSHU_PICKAXE": (6, 2), "XUANSHU_HOE": (4, 2),
    "CLOTHS": (6, -1), "XUANSHU_SPIRIT_CORE": (7, -1), "XUANSHU_ARCANE_TABLE": (8, -1),
    "XUANSHU_NODE_MODIFIER": (8, 0),
    "WANDS": (6, 0), "CAPS": (6, 1), "WAND_ASSEMBLY": (7, 1),
    "STAR_CRAFT": (7, -2), "XUANSHU_INGOT_UNFIRED": (8, -2),
    "XUANSHU_INGOT_FORGED": (9, -2), "PURIFYING_STONE": (9, -3),
    "FOCUS_GUARDIAN": (3, 1), "FOCUS_CHRONO": (3, 2), "FOCUS_COSMOS": (2, 3),
    "FOCUS_TELEPORT": (1, 3),
    "TERRAIN": (0, 5), "GUA_STONES": (1, 6),
}
pos = CAND
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
        if n not in (a, b) and seg_dist(p, pos[a], pos[b]) < 0.5]
longs = sorted(((round(((pos[a][0] - pos[b][0]) ** 2 + (pos[a][1] - pos[b][1]) ** 2) ** 0.5, 2), a, b) for a, b in edges), reverse=True)[:6]

print("交叉:", len(cross))
for e in cross:
    print("   ", e[0], "x", e[1])
print("近距穿点:", len(near))
for a, b, n, dd in near:
    print("    %-22s→%-22s 蹭 %-22s %.2f" % (a, b, n, dd))
print("最长边:", longs)

# 渲染
S, PAD, R = 92, 70, 30
cols = [c for c, _ in pos.values()]
rows = [r for _, r in pos.values()]
c0, c1, r0, r1 = min(cols), max(cols), min(rows), max(rows)
W, H = PAD * 2 + (c1 - c0) * S, PAD * 2 + (r1 - r0) * S
img = Image.new("RGB", (W, H), (28, 24, 38))
dr = ImageDraw.Draw(img)
px = lambda p: (PAD + (p[0] - c0) * S, PAD + (p[1] - r0) * S)
for c in range(c0, c1 + 1):
    dr.line([(PAD + (c - c0) * S, 0), (PAD + (c - c0) * S, H)], fill=(40, 35, 52))
for r in range(r0, r1 + 1):
    dr.line([(0, PAD + (r - r0) * S), (W, PAD + (r - r0) * S)], fill=(40, 35, 52))
for a, b in edges:
    dr.line([px(pos[a]), px(pos[b])], fill=(150, 160, 190), width=2)
for k, p in pos.items():
    x, y = px(p)
    trig = k.startswith("GUA_")
    dr.ellipse([x - R, y - R, x + R, y + R], fill=(240, 232, 210) if trig else (188, 190, 200),
               outline=(20, 20, 26), width=3)
    lb = k if len(k) <= 12 else k[:12]
    dr.text((x - dr.textlength(lb) / 2, y + R + 4), lb, fill=(225, 225, 235))
img.save(os.path.join(ROOT, "build/_crop/cand.png"))
print("已渲染 build/_crop/cand.png", img.size)
