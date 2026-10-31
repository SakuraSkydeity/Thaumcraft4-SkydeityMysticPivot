#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
schem → 玄枢结构（第一百九十批新增；第一百九十二批改定位）。

<h2>现在还有什么用</h2>

第一百九十批它是 Java 运行时读 schem 的离线复刻；**第一百九十二批起 Java 不再读 schem**
（形制被编译进 `VoidAltarBlueprint`），本脚本退成两个用途：

  1. `tools/gen_void_blueprint.py` 的前端：那个脚本 import 本模块来读文件、算锚点；
  2. 人工核对：丢一份新建筑进来，先看清「它会被当成什么形状、要多少料」。

<h2>规则（必须与 `gen_void_blueprint.py` 一致）</h2>

  * **只取非空气格**：`minecraft:air` 不算，其余（水、草也算）都算一格；
  * **锚点**：非空气包围盒的<b>底面正中</b> —— x / z 取 `(min+max)//2`，y 取 `min`。
    矿机方块就摆在这一格，整座建筑从它往上长；
  * 锚点那一格若在 schem 里本来有方块，**跳过**（那格留给矿机）；
  * 形制**不随朝向转**（你的建筑是照某个方向盖的，转了就不像了）。

改了这里的取格 / 锚点规则，必须回来同步 `tools/gen_void_blueprint.py`；
同步之后记得重跑它，否则游戏里还是旧形制。

用法：
    python tools/schem_structure.py <文件.schem> [--out 图.png]
"""

import gzip
import os
import struct
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_DIR = os.path.join(ROOT, "tools/preview")


# ---------------------------------------------------------------------------
# 一、最小 NBT 读取（sponge schematic v2/v3 都是 gzip + big-endian NBT）
# ---------------------------------------------------------------------------

class _Reader:
    __slots__ = ("b", "i")

    def __init__(self, buf):
        self.b, self.i = buf, 0

    def u1(self):
        v = self.b[self.i]
        self.i += 1
        return v

    def u2(self):
        v = struct.unpack_from(">H", self.b, self.i)[0]
        self.i += 2
        return v

    def i1(self):
        v = struct.unpack_from(">b", self.b, self.i)[0]
        self.i += 1
        return v

    def i2(self):
        v = struct.unpack_from(">h", self.b, self.i)[0]
        self.i += 2
        return v

    def i4(self):
        v = struct.unpack_from(">i", self.b, self.i)[0]
        self.i += 4
        return v

    def i8(self):
        v = struct.unpack_from(">q", self.b, self.i)[0]
        self.i += 8
        return v

    def f4(self):
        v = struct.unpack_from(">f", self.b, self.i)[0]
        self.i += 4
        return v

    def f8(self):
        v = struct.unpack_from(">d", self.b, self.i)[0]
        self.i += 8
        return v

    def string(self):
        n = self.u2()
        v = self.b[self.i:self.i + n].decode("utf-8", "replace")
        self.i += n
        return v


def _payload(r, tag):
    if tag == 1:
        return r.i1()
    if tag == 2:
        return r.i2()
    if tag == 3:
        return r.i4()
    if tag == 4:
        return r.i8()
    if tag == 5:
        return r.f4()
    if tag == 6:
        return r.f8()
    if tag == 7:
        n = r.i4()
        v = list(r.b[r.i:r.i + n])
        r.i += n
        return v
    if tag == 8:
        return r.string()
    if tag == 9:
        inner = r.u1()
        n = r.i4()
        return [_payload(r, inner) for _ in range(n)]
    if tag == 10:
        out = {}
        while True:
            t = r.u1()
            if t == 0:
                return out
            name = r.string()
            out[name] = _payload(r, t)
    if tag == 11:
        n = r.i4()
        v = list(struct.unpack_from(">%di" % n, r.b, r.i))
        r.i += 4 * n
        return v
    if tag == 12:
        n = r.i4()
        v = list(struct.unpack_from(">%dq" % n, r.b, r.i))
        r.i += 8 * n
        return v
    raise ValueError("不认识的 NBT 标签 %d（偏移 %d）" % (tag, r.i))


def read_nbt(path):
    with gzip.open(path, "rb") as f:
        buf = f.read()
    r = _Reader(buf)
    if r.u1() != 10:
        raise ValueError("根标签不是 Compound")
    r.string()
    return _payload(r, 10)


# ---------------------------------------------------------------------------
# 二、schem → 格子
# ---------------------------------------------------------------------------

def _varints(data):
    """schem v3 的 Data 是 varint 流（v2 是定长 2 字节，这里顺带兼容）。"""
    out, i, n = [], 0, len(data)
    while i < n:
        v, shift = 0, 0
        while True:
            b = data[i]
            i += 1
            v |= (b & 0x7F) << shift
            if not b & 0x80:
                break
            shift += 7
        out.append(v)
    return out


def load_schem(path):
    """
    读一份 .schem。

    @return dict：w / h / l、palette（名字 → 序号）、cells（[(x, y, z, 方块名)]，只含非空气）
           以及 offset（WorldEdit 记的玩家位置，仅用于打印，不参与锚点）
    """
    root = read_nbt(path)
    sch = root.get("Schematic", root)
    w = int(sch["Width"])
    h = int(sch["Height"])
    l = int(sch["Length"])
    palette = sch["Blocks"]["Palette"]
    index_of = {v: k for k, v in palette.items()}

    data = sch["Blocks"]["Data"]
    ids = _varints(data)
    if len(ids) != w * h * l:
        raise ValueError("格子数对不上：解出 %d，应为 %d" % (len(ids), w * h * l))

    cells = []
    for idx, pid in enumerate(ids):
        name = index_of.get(pid)
        if name is None:
            raise ValueError("调色板里没有序号 %d" % pid)
        if _is_air(name):
            continue
        x = idx % w
        z = (idx // w) % l
        y = idx // (w * l)
        cells.append((x, y, z, name))
    return {
        "w": w, "h": h, "l": l,
        "palette": palette,
        "cells": cells,
        "offset": sch.get("Offset"),
        "origin": (sch.get("Metadata", {}).get("WorldEdit", {}).get("Origin")),
    }


def _is_air(state):
    """`minecraft:air` / `cave_air` / `void_air` 都算空；带方括号的照样剥。"""
    name = state.split("[")[0]
    return name in ("minecraft:air", "minecraft:cave_air", "minecraft:void_air")


def anchor_of(cells):
    """
    锚点 = 非空气包围盒的底面正中。矿机方块摆在这一格。

    与 `gen_void_blueprint.py` / 游戏内 `VoidMiningStructure.parts()` 同一套规则：
      x = (minX + maxX) // 2、y = minY、z = (minZ + maxZ) // 2
    """
    xs = [c[0] for c in cells]
    ys = [c[1] for c in cells]
    zs = [c[2] for c in cells]
    return ((min(xs) + max(xs)) // 2, min(ys), (min(zs) + max(zs)) // 2)


def structure(cells, anchor):
    """把绝对坐标换成「相对矿机」的偏移，并跳过矿机那一格。"""
    ax, ay, az = anchor
    out = []
    for (x, y, z, name) in cells:
        if (x, y, z) == (ax, ay, az):
            continue
        out.append((x - ax, y - ay, z - az, name))
    return out


def report(path):
    sch = load_schem(path)
    cells = sch["cells"]
    anchor = anchor_of(cells)
    parts = structure(cells, anchor)
    xs = [p[0] for p in parts] or [0]
    ys = [p[1] for p in parts] or [0]
    zs = [p[2] for p in parts] or [0]
    print("文件        %s" % os.path.basename(path))
    print("schem 尺寸   %d × %d × %d（x × y × z），共 %d 格"
          % (sch["w"], sch["h"], sch["l"], sch["w"] * sch["h"] * sch["l"]))
    print("非空气       %d 格 / 调色板 %d 种" % (len(cells), len(sch["palette"]) - 1))
    print("锚点(矿机)   schem 内 (%d, %d, %d)；WorldEdit 记的玩家位置 %s（不参与判定）"
          % (anchor[0], anchor[1], anchor[2], sch["offset"]))
    print("结构件       %d 格，相对矿机 x %d…%d  y %d…%d  z %d…%d"
          % (len(parts), min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)))
    tally = {}
    for p in parts:
        key = p[3].split("[")[0]          # 按方块种类合并：台阶朝向不同也算同一种
        tally[key] = tally.get(key, 0) + 1
    print("材料        %d 种" % len(tally))
    for name, n in sorted(tally.items(), key=lambda kv: -kv[1]):
        print("   %-52s %3d" % (name, n))
    per_layer = {}
    for p in parts:
        per_layer[p[1]] = per_layer.get(p[1], 0) + 1
    print("逐层        " + "  ".join("y%+d:%d" % (y, per_layer[y])
                                     for y in sorted(per_layer)))
    return sch, anchor, parts


# ---------------------------------------------------------------------------
# 三、出图
# ---------------------------------------------------------------------------

BG = (26, 28, 33)
GRID = (46, 50, 58)
INK = (236, 238, 244)
DIM = (150, 156, 170)
MACHINE = (232, 96, 88)

# 方块名（去命名空间）→ 颜色。认不出的按名字哈希取一个灰调色，不至于画不出来。
COLORS = {
    "light_blue_stained_glass": (150, 206, 226),
    "light_blue_stained_glass_pane": (150, 206, 226),
    "blue_wool": (58, 62, 158),
    "chiseled_quartz_block": (232, 226, 214),
    "quartz_bricks": (230, 224, 212),
    "quartz_pillar": (235, 229, 218),
    "quartz_slab": (226, 220, 209),
    "quartz_stairs": (228, 222, 211),
    "sea_lantern": (198, 226, 214),
    "smooth_quartz_slab": (222, 226, 232),
    "end_rod": (232, 186, 96),
}

FONT_PATHS = (
    "C:/Windows/Fonts/msyh.ttc",
    "C:/Windows/Fonts/simhei.ttf",
    "/System/Library/Fonts/PingFang.ttc",
)


def font(size, bold=False):
    for path in FONT_PATHS:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size, index=1 if bold else 0)
            except Exception:
                try:
                    return ImageFont.truetype(path, size)
                except Exception:
                    continue
    return ImageFont.load_default()


def color_of(name):
    key = name.split("[")[0].split(":")[-1]
    if key in COLORS:
        return COLORS[key]
    h = abs(hash(key))
    return (120 + (h % 70), 120 + (h // 70) % 70, 120 + (h // 4900) % 70)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3])


def draw_iso(d, parts, ox, oy, w, h2, hv):
    """画家算法画体素（相机在 +x +y +z 那一侧，远的先画）。"""
    cells = {}
    for (dx, dy, dz, name) in parts:
        cells[(dx, dy, dz)] = name
    order = sorted(cells, key=lambda p: (p[0] + p[1] + p[2]))

    def pt(x, y, z):
        return (ox + (x - z) * w, oy + (x + z) * h2 - y * hv)

    for (x, y, z) in order:
        name = cells[(x, y, z)]
        c = color_of(name)
        # 顶面
        top = [pt(x, y + 1, z), pt(x + 1, y + 1, z), pt(x + 1, y + 1, z + 1), pt(x, y + 1, z + 1)]
        # +x 面（右）
        right = [pt(x + 1, y, z), pt(x + 1, y + 1, z), pt(x + 1, y + 1, z + 1), pt(x + 1, y, z + 1)]
        # +z 面（左）
        left = [pt(x, y, z + 1), pt(x + 1, y, z + 1), pt(x + 1, y + 1, z + 1), pt(x, y + 1, z + 1)]
        fill_top = shade(c, 1.0)
        fill_left = shade(c, 0.74)
        fill_right = shade(c, 0.58)
        d.polygon(left, fill=fill_left, outline=shade(c, 0.5))
        d.polygon(right, fill=fill_right, outline=shade(c, 0.5))
        d.polygon(top, fill=fill_top, outline=shade(c, 0.62))


def main(argv):
    if len(argv) < 2:
        print(__doc__)
        return 2
    path = argv[1]
    out = None
    if "--out" in argv:
        out = argv[argv.index("--out") + 1]
    sch, anchor, parts = report(path)
    out = out or os.path.join(OUT_DIR, "schem_structure.png")
    os.makedirs(os.path.dirname(out), exist_ok=True)

    xs = [p[0] for p in parts]
    ys = [p[1] for p in parts]
    zs = [p[2] for p in parts]
    x0, x1 = min(xs), max(xs)
    y0, y1 = min(ys), max(ys)
    z0, z1 = min(zs), max(zs)

    # ---- 左：等轴 ----
    W, H2, HV = 24, 12, 20

    def sx(x, z):
        return (x - z) * W

    def sy(x, y, z):
        return (x + z) * H2 - y * HV

    corners = [(x, y, z) for x in (x0, x1 + 1) for y in (y0, y1 + 1) for z in (z0, z1 + 1)]
    min_sx = min(sx(x, z) for (x, y, z) in corners)
    max_sx = max(sx(x, z) for (x, y, z) in corners)
    min_sy = min(sy(x, y, z) for (x, y, z) in corners)
    max_sy = max(sy(x, y, z) for (x, y, z) in corners)
    ISO_W = int(max_sx - min_sx) + 80
    ISO_H = int(max_sy - min_sy) + 80

    # ---- 右：逐层俯视 ----
    CELL = 15
    per_row = 7
    layers = sorted({p[1] for p in parts})
    cols = min(per_row, len(layers))
    rows = (len(layers) + cols - 1) // cols
    grid_w = len(range(z0, z1 + 1)) * CELL
    grid_h = len(range(x0, x1 + 1)) * CELL
    GRID_AREA_W = cols * (grid_w + 26)
    GRID_AREA_H = rows * (grid_h + 36) + 8

    title_h = 96
    foot_h = 96
    body_h = max(ISO_H, GRID_AREA_H)
    width = 44 + ISO_W + 40 + GRID_AREA_W + 26
    height = title_h + body_h + foot_h
    img = Image.new("RGB", (width, height), BG)
    d = ImageDraw.Draw(img)
    f_title = font(30, True)
    f_sub = font(15)
    f_layer = font(14, True)
    f_tick = font(11)
    f_small = font(13)

    d.text((44, 22), "你的建筑 · 当作玄枢采矿机多方块", font=f_title, fill=INK)
    d.text((44, 62),
           "%s　%d 格（去空气）　包围盒 %d×%d×%d　锚点 = 底面正中，矿机摆那一格"
           % (os.path.basename(path), len(parts),
              x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1),
           font=f_sub, fill=DIM)

    ox = 44 + 20 - min_sx
    oy = title_h + (body_h - (max_sy - min_sy)) / 2 - min_sy
    draw_iso(d, parts, ox, oy, W, H2, HV)

    # 矿机标记：在锚点格画一只红框
    def pt(x, y, z):
        return (ox + (x - z) * W, oy + (x + z) * H2 - y * HV)

    lo = [(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)]
    hi = [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)]
    for i in range(4):
        d.line([pt(*lo[i]), pt(*lo[(i + 1) % 4])], fill=MACHINE, width=2)
        d.line([pt(*hi[i]), pt(*hi[(i + 1) % 4])], fill=MACHINE, width=2)
        d.line([pt(*lo[i]), pt(*hi[i])], fill=MACHINE, width=2)
    d.text((pt(1, 1, 1)[0] + 6, pt(1, 1, 1)[1] - 6), "矿机", font=f_tick, fill=MACHINE)

    # 逐层俯视
    gx0 = 44 + ISO_W + 40
    gy0 = title_h + 6
    for i, y in enumerate(layers):
        cx = gx0 + (i % cols) * (grid_w + 26)
        cy = gy0 + (i // cols) * (grid_h + 36)
        d.text((cx, cy), "y %+d　%d 格" % (y, sum(1 for p in parts if p[1] == y)),
               font=f_layer, fill=INK)
        top = cy + 20
        for z in range(z0, z1 + 1):
            for x in range(x0, x1 + 1):
                px = cx + (x - x0) * CELL
                py = top + (z - z0) * CELL
                d.rectangle([px, py, px + CELL - 1, py + CELL - 1], fill=(34, 37, 43), outline=GRID)
        for (dx, dy, dz, name) in parts:
            if dy != y:
                continue
            px = cx + (dx - x0) * CELL
            py = top + (dz - z0) * CELL
            d.rectangle([px, py, px + CELL - 1, py + CELL - 1],
                        fill=color_of(name), outline=shade(color_of(name), 0.72))
        if y == 0:
            px = cx + (0 - x0) * CELL + 2
            py = top + (0 - z0) * CELL + 2
            d.rectangle([px, py, px + CELL - 3, py + CELL - 3], outline=MACHINE, width=2)

    fy = height - foot_h + 6
    d.text((44, fy),
           "矿机摆在底面正中那一格（红框），整座建筑往上长 %d 格。" % (y1 + 1),
           font=f_small, fill=(198, 204, 216))
    d.text((44, fy + 24),
           "判定只认「这一格不是空气」：方块种类随便换，只要是实心就行；"
           "结构里没占的格子（空气）随便摆什么。",
           font=f_small, fill=DIM)
    d.text((44, fy + 48),
           "自定义结构不随朝向转 —— 你的建筑朝哪边盖的，矿机就朝哪边摆；"
           "换建筑就换掉 config/skydeitymysticpivot/structure.schem，重启游戏。",
           font=f_small, fill=DIM)
    img.save(out)
    print("\n出图：%s" % out)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
