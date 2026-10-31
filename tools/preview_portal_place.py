#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
传送门广场「落在哪 + 长什么样」预览 + 偏移自检（第一百九十八批；第二百零四批重写）。

<h2>它核对什么</h2>

{@code PortalGateStructure} 在游戏里做三件事：把蓝图往方块背后挪、往下沉一格、按朝向绕 Y 转。
这个脚本用<b>同一套公式</b>在离线复算一遍，然后：

  1. 打出四个朝向各自的包围盒，断言<b>深度 0</b>（建筑最前排就在方块自己那一列）；
  2. 断言<b>没有一格压在方块自己那一格</b>（{@code (0,0,0)} 必须是空的）；
  3. 出三张图：{@code portal_place_iso.png}（等距）、{@code portal_place_top.png}
     （俯视，四朝向并排）、{@code portal_place_front.png}（正视 + 标高）。

<h2>公式必须与 Java 一致</h2>

    z' = z + FRONT_SHIFT        FRONT_SHIFT = 0 - minZ
    y' = y + Y_SHIFT            Y_SHIFT = -1
    北 (x, z')   东 (-z', x)   南 (-x, -z')   西 (z', -x)

<h2>数据从哪来</h2>

直接解析 {@code PortalGateBlueprint.java} 里的字符画（{@code CHARS} + {@code STATES} + {@code MAP}），
不读 schem —— 与游戏里跑的是同一份常量。

用法：
    python tools/preview_portal_place.py
"""

import os
import re
import sys
from collections import deque

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import schem_structure as S
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "tools/preview")
BLUEPRINT = os.path.join(ROOT, "src/main/java/skydeity/mysticpivot/PortalGateBlueprint.java")

FACINGS = ("north", "east", "south", "west")
Y_SHIFT = -1
MACHINE = (232, 96, 88)

#: 这座门用到的 12 种方块，按它们本来的颜色给（兜底才用哈希）
COLORS = {
    "minecraft:chiseled_quartz_block": (232, 226, 214),
    "minecraft:quartz_stairs": (226, 220, 208),
    "minecraft:quartz_slab": (222, 216, 204),
    "minecraft:quartz_pillar": (238, 234, 224),
    "minecraft:gold_block": (247, 208, 92),
    "minecraft:purpur_block": (170, 112, 172),
    "minecraft:sea_lantern": (198, 240, 222),
    "minecraft:obsidian": (44, 36, 62),
    "minecraft:blue_stained_glass": (78, 100, 214),
    "minecraft:blue_stained_glass_pane": (86, 108, 222),
    "minecraft:light_blue_stained_glass_pane": (150, 206, 226),
    "minecraft:beacon": (120, 236, 216),
}


def color_of(name):
    return COLORS.get(name.split("[")[0], S.color_of(name))


# ---------------------------------------------------------------------------
# 解析蓝图字符画
# ---------------------------------------------------------------------------

def parse_blueprint(path):
    src = open(path, encoding="utf-8").read()
    chars = re.search(r'CHARS = "([^"]*)"', src).group(1)
    states = re.findall(r'^\s*"([^"]+)",\s*//', src, re.M)
    body = src[src.index('MAP = """') + len('MAP = """'):]
    body = body[: body.index('"""')]

    cells = []
    y = x0 = z0 = row = 0
    for line in body.split("\n"):
        line = line.strip()
        if line.startswith("== "):
            y, x0, z0 = (int(v) for v in line[3:].split(","))
            row = 0
        elif line:
            for i, ch in enumerate(line):
                k = chars.find(ch)
                if k >= 0:
                    cells.append((x0 + i, y, z0 + row, states[k].split("[")[0]))
            row += 1
    return cells


def rotate(x, z, facing, front_shift):
    z = z + front_shift
    if facing == "east":
        return -z, x
    if facing == "south":
        return -x, -z
    if facing == "west":
        return z, -x
    return x, z


def placed(parts, facing, front_shift):
    return [(rotate(x, z, facing, front_shift)[0], y + Y_SHIFT,
             rotate(x, z, facing, front_shift)[1], name)
            for (x, y, z, name) in parts]


def components(points):
    """四连通块数。"""
    seen, comps = set(), 0
    for p in points:
        if p in seen:
            continue
        comps += 1
        q = deque([p])
        seen.add(p)
        while q:
            c = q.popleft()
            for d in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                n = (c[0] + d[0], c[1] + d[1])
                if n in points and n not in seen:
                    seen.add(n)
                    q.append(n)
    return comps


# ---------------------------------------------------------------------------
# 图
# ---------------------------------------------------------------------------

def draw_iso(tiles, size):
    """等距：相机在 (+x, +y, +z) 一侧，远的先画。"""
    w, d = size
    cw = max(7, min(16, 780 // (w + d)))
    h2, hv = cw // 2, cw * 3 // 2

    def pt(x, y, z):
        return ((x - z) * cw, (x + z) * h2 - y * hv)

    corners = [(x, y, z) for (x, y, z, _) in tiles] + [(0, 0, 0)]
    sx = [pt(x, y + 1, z)[0] for (x, y, z) in corners]
    sy = [pt(x, y, z)[1] for (x, y, z) in corners]
    img = Image.new("RGB", (int(max(sx) - min(sx)) + 120, int(max(sy) - min(sy)) + 130), S.BG)
    dr = ImageDraw.Draw(img)
    ox, oy = 60 - min(sx), 62 - min(sy)

    def P(x, y, z):
        px, py = pt(x, y, z)
        return (px + ox, py + oy)

    def box(x, y, z, fill, edge):
        dr.polygon([P(x, y, z + 1), P(x + 1, y, z + 1), P(x + 1, y + 1, z + 1), P(x, y + 1, z + 1)],
                   fill=S.shade(fill, 0.70), outline=S.shade(fill, 0.40))
        dr.polygon([P(x + 1, y, z), P(x + 1, y + 1, z), P(x + 1, y + 1, z + 1), P(x + 1, y, z + 1)],
                   fill=S.shade(fill, 0.50), outline=S.shade(fill, 0.40))
        dr.polygon([P(x, y + 1, z), P(x + 1, y + 1, z), P(x + 1, y + 1, z + 1), P(x, y + 1, z + 1)],
                   fill=fill, outline=S.shade(fill, 0.55))

    cells = {(x, y, z): name for (x, y, z, name) in tiles}
    for (x, y, z) in sorted(cells, key=lambda p: (p[0] + p[1] + p[2])):
        box(x, y, z, color_of(cells[(x, y, z)]), None)
    box(0, 0, 0, MACHINE, (255, 255, 255))

    dr.text((18, 12), "玄枢传送门 · %d×%d 八角台基 + 折跃门" % (w, d),
            fill=S.INK, font=S.font(15, True))
    dr.text((18, 34), "红格 = 方块自己（站在广场前沿、脚下就是最底层铺地）",
            fill=S.DIM, font=S.font(11))
    return img


CELL_TOP = 7


def draw_top(tiles, facing):
    xs = [t[0] for t in tiles]
    zs = [t[2] for t in tiles]
    x0, z0 = min(xs), min(zs)
    img = Image.new("RGB", ((max(xs) - x0 + 1) * CELL_TOP + 30,
                            (max(zs) - z0 + 1) * CELL_TOP + 62), S.BG)
    dr = ImageDraw.Draw(img)
    for (x, y, z, name) in sorted(tiles, key=lambda t: t[1]):
        c = color_of(name)
        k = 0.45 + 0.55 * min(1.0, (y + 2) / 18.0)
        px, pz = 15 + (x - x0) * CELL_TOP, 40 + (z - z0) * CELL_TOP
        dr.rectangle([px, pz, px + CELL_TOP - 1, pz + CELL_TOP - 1], fill=S.shade(c, k))
    px, pz = 15 + (0 - x0) * CELL_TOP, 40 + (0 - z0) * CELL_TOP
    dr.rectangle([px, pz, px + CELL_TOP - 1, pz + CELL_TOP - 1], fill=MACHINE, outline=(255, 255, 255))
    dr.text((8, 6), "facing=%s（红格 = 方块）" % facing, fill=S.INK, font=S.font(11, True))
    return img


CELL_FRONT = 13


def draw_front(parts, front_shift):
    """正视（从 −Z 往 +Z 看）：每个 (x, y) 只画最靠前那一格。"""
    tiles = placed(parts, "north", front_shift)
    near = {}
    for (x, y, z, name) in tiles:
        if (x, y) not in near or z < near[(x, y)][0]:
            near[(x, y)] = (z, name)

    xs = [t[0] for t in tiles]
    ys = [t[1] for t in tiles]
    x0, y1 = min(xs), max(ys)
    cw = (max(xs) - x0 + 1) * CELL_FRONT
    img = Image.new("RGB", (cw + 170, (y1 + 3) * CELL_FRONT + 92), S.BG)
    dr = ImageDraw.Draw(img)

    def px(x):
        return 40 + (x - x0) * CELL_FRONT

    def py(y):
        return 44 + (y1 - y) * CELL_FRONT

    for (x, y), (z, name) in sorted(near.items(), key=lambda kv: (kv[1][0], kv[0][0])):
        c = color_of(name)
        k = 1.0 - 0.35 * min(1.0, z / 20.0)
        dr.rectangle([px(x), py(y), px(x) + CELL_FRONT - 1, py(y) + CELL_FRONT - 1],
                     fill=S.shade(c, k), outline=S.shade(c, 0.55))
    dr.rectangle([px(0), py(0), px(0) + CELL_FRONT - 1, py(0) + CELL_FRONT - 1],
                 fill=MACHINE, outline=(255, 255, 255))

    for (y, label, color) in ((0, "y 0　方块自己", MACHINE),
                              (-1, "y −1　广场底层", (150, 156, 170)),
                              (y1, "y %d　顶" % y1, (198, 240, 222))):
        yy = py(y) + CELL_FRONT - 1
        dr.line([(34, yy), (cw + 40, yy)], fill=(58, 62, 72))
        dr.text((cw + 46, yy - 7), label, fill=color, font=S.font(10))

    dr.text((16, 12), "玄枢传送门 · 正视（从门正面看）", fill=S.INK, font=S.font(15, True))
    dr.text((16, 34), "八角台基逐层收分，井口立两根方柱，井心一道蓝玻璃柱",
            fill=S.DIM, font=S.font(11))
    return img


# ---------------------------------------------------------------------------
# main
# ---------------------------------------------------------------------------

def main():
    parts = parse_blueprint(BLUEPRINT)
    min_z = min(p[2] for p in parts)
    front_shift = 0 - min_z
    xs = [p[0] for p in parts]
    zs = [p[2] for p in parts]
    ys = [p[1] for p in parts]
    print("蓝图 %d 格；局部 x %d…%d，z %d…%d，y %d…%d"
          % (len(parts), min(xs), max(xs), min(zs), max(zs), min(ys), max(ys)))
    print("FRONT_SHIFT = 0 - minZ = %d；Y_SHIFT = %d" % (front_shift, Y_SHIFT))

    base = {(p[0], p[2]) for p in parts if p[1] == min(ys)}
    print("最底层 %d 格、四连通块 %d 个（旧形制本来就有一格孤岛与中央方井，不算错）"
          % (len(base), components(base)))

    ok = True
    tiles_all = []
    for facing in FACINGS:
        tiles = placed(parts, facing, front_shift)
        tx = [t[0] for t in tiles]
        tz = [t[2] for t in tiles]
        ty = [t[1] for t in tiles]
        depth = {"north": min(tz), "south": -max(tz),
                 "east": -max(tx), "west": min(tx)}[facing]
        front_y = sorted({t[1] for t in tiles
                          if {"north": t[2], "south": -t[2],
                              "east": -t[0], "west": t[0]}[facing] == depth})
        clash = any((t[0], t[1], t[2]) == (0, 0, 0) for t in tiles)
        flag = []
        if depth != 0:
            flag.append("深度应 0")
        if front_y != [-1]:
            flag.append("前沿不在脚下")
        if clash:
            flag.append("有格子压在方块上")
        ok = ok and not flag
        print("  %-5s 深度 %2d  前沿 y %s  高 %d…%d  格数 %d  %s"
              % (facing, depth, front_y, min(ty), max(ty), len(tiles),
                 "OK" if not flag else "!! " + "、".join(flag)))
        tiles_all.append((facing, tiles))
    print("四朝向都不压住方块、前沿都在脚下：%s" % ("OK" if ok else "不通过"))

    os.makedirs(OUT_DIR, exist_ok=True)
    draw_iso(tiles_all[0][1], (max(xs) - min(xs) + 1, max(zs) - min(zs) + 1)).save(
        os.path.join(OUT_DIR, "portal_place_iso.png"))

    panels = [draw_top(tiles, facing) for facing, tiles in tiles_all]
    top = Image.new("RGB", (sum(p.width for p in panels) + 10 * (len(panels) - 1),
                            max(p.height for p in panels)), S.BG)
    x = 0
    for p in panels:
        top.paste(p, (x, 0))
        x += p.width + 10
    top.save(os.path.join(OUT_DIR, "portal_place_top.png"))

    draw_front(parts, front_shift).save(os.path.join(OUT_DIR, "portal_place_front.png"))

    print("出图：tools/preview/portal_place_iso.png")
    print("出图：tools/preview/portal_place_top.png")
    print("出图：tools/preview/portal_place_front.png")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
