#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
把枫树（原版大型橡树形制）离线生成为 PNG —— 不进游戏也能看树形。

<h2>为什么要有这个脚本</h2>
枫树的树形不是本 mod 手写的：{@code MapleTreeGrower} 现构的参数
（FancyTrunkPlacer(3,11,0) + FancyFoliagePlacer(2,4,4) + TwoLayersFeatureSize(0,0,0,4)）
喂给**原版** {@code Feature.TREE}，形状由原版 fancy oak 算法决定。
本脚本把该算法（FancyTrunkPlacer / FancyFoliagePlacer / TreeFeature 的放置流程）
逐行移植成 Python，在空世界里跑一遍，再用枫树自己的贴图渲染出来——
看到的树形就是游戏里长出来的树形（同参数、同算法；随机源不同，
所以每一棵的枝位细节与游戏里那棵不完全一致，形制一致）。

<h2>算法来源</h2>
FancyTrunkPlacer.placeTrunk / makeLimb / makeBranches / treeShape / trimBranches、
FancyFoliagePlacer.createFoliage / shouldSkipLocation、BlobFoliagePlacer.placeLeavesRow、
TreeFeature.doPlace 的 height/radius 计算——全部对照
.net.minecraft 源码移植（见 README-tc §12.170 审计记录）。

用法：
    python tools/preview_maple_tree.py [种子] [输出路径]
"""
import math
import os
import random
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEXDIR = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/block")

SEED = int(sys.argv[1]) if len(sys.argv) > 1 else 20261023
OUT = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, "tools/preview/maple_tree.png")
# 第三参写 "front" 时只出正立面（多树并排对比用）
FRONT_ONLY = len(sys.argv) > 3 and sys.argv[3] == "front"

# 与 MapleTreeGrower 完全一致的参数（第 172 批：树干固定最大高度 14，
# base_height=14 / height_rand_a=0 / height_rand_b=0，不再随机）
BASE_HEIGHT, HEIGHT_RAND_A, HEIGHT_RAND_B = 14, 0, 0
FOLIAGE_RADIUS, FOLIAGE_OFFSET, FOLIAGE_HEIGHT = 2, 4, 4

SCALE = 6          # 每个方块像素的放大倍数
LOG_SIDE = Image.open(os.path.join(TEXDIR, "maple_log_side.png")).convert("RGBA")
LOG_TOP = Image.open(os.path.join(TEXDIR, "maple_log_top.png")).convert("RGBA")
LEAVES = Image.open(os.path.join(TEXDIR, "maple_leaves.png")).convert("RGBA")


# ---------------------------------------------------------------- 原版算法移植
def tree_shape(height, y):
    """FancyTrunkPlacer.treeShape：返回该高度层允许的枝条横向半径，<0 表示不长枝。"""
    if y < height * 0.3:
        return -1.0
    f = height / 2.0
    f1 = f - y
    f2 = math.sqrt(f * f - f1 * f1) if abs(f1) < f else 0.0
    if f1 == 0.0:
        f2 = f
    elif abs(f1) >= f:
        return 0.0
    return f2 * 0.5


def trim_branches(height, branch_base_offset):
    """FancyTrunkPlacer.trimBranches：低于树干 20% 的枝不要。"""
    return branch_base_offset >= height * 0.2


def log_axis(frm, p):
    """FancyTrunkPlacer.getLogAxis：按枝条走向定原木轴向。"""
    axis = "y"
    i = abs(p[0] - frm[0])
    k = abs(p[2] - frm[2])
    m = max(i, k)
    if m > 0:
        axis = "x" if i == m else "z"
    return axis


class TreeSim:
    """空世界 + 原版放置流程的模拟。blocks: {(x,y,z): 'log'|'leaves'}。"""

    def __init__(self, rng):
        self.rng = rng
        self.blocks = {}
        self.attachments = []   # 树冠附着点（枝头）

    # ---- TrunkPlacer 基础件 ----
    def is_free(self, p):
        return p not in self.blocks

    def place_log(self, p, axis="y"):
        self.blocks[p] = ("log", axis)

    def make_limb(self, frm, to, is_trunk):
        """FancyTrunkPlacer.makeLimb：trunk 段落原木（带轴向），枝段只做空间检查。"""
        if not is_trunk and frm == to:
            return True
        dx, dy, dz = to[0] - frm[0], to[1] - frm[1], to[2] - frm[2]
        steps = max(abs(dx), abs(dy), abs(dz))
        if steps == 0:
            return True
        fx, fy, fz = dx / steps, dy / steps, dz / steps
        for j in range(steps + 1):
            p = (frm[0] + math.floor(0.5 + j * fx),
                 frm[1] + math.floor(0.5 + j * fy),
                 frm[2] + math.floor(0.5 + j * fz))
            if is_trunk:
                self.place_log(p, log_axis(frm, p))
            elif not self.is_free(p):
                return False
        return True

    # ---- FancyTrunkPlacer.placeTrunk ----
    def place_trunk(self, height, pos):
        j = height + 2
        k = math.floor(j * 0.618)
        l = min(1, math.floor(1.382 + (1.0 * j / 13.0) ** 2))
        i1 = pos[1] + k
        j1 = j - 5
        coords = [((pos[0], pos[1] + j1, pos[2]), i1)]
        while j1 >= 0:
            f = tree_shape(j, j1)
            if f >= 0.0:
                for _ in range(l):
                    d2 = 1.0 * f * (self.rng.random() + 0.328)
                    d3 = self.rng.random() * 2.0 * math.pi
                    d4 = d2 * math.sin(d3) + 0.5
                    d5 = d2 * math.cos(d3) + 0.5
                    branch = (pos[0] + math.floor(d4), pos[1] + j1 - 1, pos[2] + math.floor(d5))
                    if self.make_limb(branch, (branch[0], branch[1] + 5, branch[2]), False):
                        l1 = pos[0] - branch[0]
                        i2 = pos[2] - branch[2]
                        d6 = branch[1] - math.sqrt(l1 * l1 + i2 * i2) * 0.381
                        j2 = i1 if d6 > i1 else int(d6)
                        base = (pos[0], j2, pos[2])
                        if self.make_limb(base, branch, False):
                            coords.append((branch, base[1]))
            j1 -= 1
        # 主干（带轴向）
        self.make_limb(pos, (pos[0], pos[1] + k, pos[2]), True)
        # makeBranches：把检查通过的枝真正落成原木
        for branch, base_y in coords:
            base = (pos[0], base_y, pos[2])
            if base != branch and trim_branches(j, base_y - pos[1]):
                self.make_limb(base, branch, True)
        # trimBranches 过滤后的树冠附着点
        return [b for b, base_y in coords if trim_branches(j, base_y - pos[1])]

    # ---- FancyFoliagePlacer ----
    def should_skip(self, dx, dz, radius):
        return (dx + 0.5) ** 2 + (dz + 0.5) ** 2 > radius * radius

    def place_leaves_row(self, center, row_radius, y):
        i_extra = 0  # doubleTrunk=False
        for dx in range(-row_radius, row_radius + 1 + i_extra):
            for dz in range(-row_radius, row_radius + 1 + i_extra):
                if not self.should_skip(dx, dz, row_radius):
                    p = (center[0] + dx, y, center[2] + dz)
                    if self.is_free(p):
                        self.blocks[p] = ("leaves", None)

    def create_foliage(self, attachment):
        for i in range(FOLIAGE_OFFSET, FOLIAGE_OFFSET - FOLIAGE_HEIGHT - 1, -1):
            row_radius = FOLIAGE_RADIUS + (1 if (i != FOLIAGE_OFFSET and i != FOLIAGE_OFFSET - FOLIAGE_HEIGHT) else 0)
            self.place_leaves_row(attachment, row_radius, attachment[1] + i)

    # ---- TreeFeature.doPlace 的主流程 ----
    def grow(self, pos):
        height = BASE_HEIGHT + self.rng.randint(0, HEIGHT_RAND_A) + self.rng.randint(0, HEIGHT_RAND_B)
        attachments = self.place_trunk(height, pos)
        for a in attachments:
            self.create_foliage(a)
        return height


# ---------------------------------------------------------------- 渲染
def draw_block(img, ox, oy, tex, flip_v):
    """把一张 16×16 贴图以 SCALE 倍画到 (ox,oy)（块左上角，屏幕像素）。"""
    px = tex.load()
    for ty in range(16):
        for tx in range(16):
            c = px[tx, ty]
            if c[3] < 8:
                continue
            sy = (15 - ty) if flip_v else ty
            for dy in range(SCALE):
                for dx in range(SCALE):
                    img.putpixel((ox + tx * SCALE + dx, oy + sy * SCALE + dy), c)


def render(blocks, base_x, base_z):
    """两视图：正立面（x→右，y→上）+ 顶视图（x→右，z→下），都用枫树自己的贴图。"""
    xs = [p[0] for p in blocks]
    ys = [p[1] for p in blocks]
    zs = [p[2] for p in blocks]
    x0, x1 = min(xs) - 1, max(xs) + 1
    y1 = max(ys) + 1
    z0, z1 = min(zs) - 1, max(zs) + 1
    bw = 16 * SCALE                       # 一个方块的屏幕边长
    w = (x1 - x0 + 1) * bw
    h = y1 * bw
    tw = (z1 - z0 + 1) * bw
    gap = 24
    canvas = Image.new("RGBA", (w + gap + tw, max(h, tw)), (250, 248, 244, 255))
    if FRONT_ONLY:
        canvas = Image.new("RGBA", (w, h), (250, 248, 244, 255))

    # 正立面：每个 (x,y) 优先取 z 最小的**原木**（枝干看得见），没有原木才取最靠前的叶子。
    # 纯「最近方块」画法会把整棵树埋进树冠（原版大型橡树的侧枝本来就长在冠里），
    # 优先画木是「木质 X 光」——树形（干 + 四向侧枝 + 冠）一眼读全。
    for y in range(0, y1):
        for x in range(x0, x1 + 1):
            logs_here = [p for p in blocks if p[0] == x and p[1] == y and blocks[p][0] == "log"]
            if logs_here:
                p = min(logs_here, key=lambda q: q[2])
                draw_block(canvas, (x - x0) * bw, (y1 - 1 - y) * bw, LOG_SIDE, True)
                continue
            col = [p for p in blocks if p[0] == x and p[1] == y]
            if not col:
                continue
            p = min(col, key=lambda q: q[2])
            draw_block(canvas, (x - x0) * bw, (y1 - 1 - y) * bw, LEAVES, True)

    # 顶视图：每个 (x,z) 取 y 最大的方块；y 轴原木顶面是年轮，横枝与原木侧视同纹
    if not FRONT_ONLY:
        for z in range(z0, z1 + 1):
            for x in range(x0, x1 + 1):
                col = [p for p in blocks if p[0] == x and p[2] == z]
                if not col:
                    continue
                p = max(col, key=lambda q: q[1])
                kind, axis = blocks[p]
                tex = LEAVES if kind == "leaves" else (LOG_TOP if axis == "y" else LOG_SIDE)
                draw_block(canvas, w + gap + (x - x0) * bw, (z - z0) * bw, tex, False)

    from PIL import ImageDraw
    d = ImageDraw.Draw(canvas)
    d.text((6, 6), "front (x->right, y->up)", fill=(90, 80, 60, 255))
    if not FRONT_ONLY:
        d.text((w + gap + 6, 6), "top (x->right, z->down)", fill=(90, 80, 60, 255))
    return canvas


def main():
    rng = random.Random(SEED)
    sim = TreeSim(rng)
    height = sim.grow((8, 0, 8))
    logs = sum(1 for v in sim.blocks.values() if v[0] == "log")
    leaves = sum(1 for v in sim.blocks.values() if v[0] == "leaves")
    print("seed=%d  trunk_height=%d  logs=%d  leaves=%d  total=%d"
          % (SEED, height, logs, leaves, len(sim.blocks)))
    img = render(sim.blocks, 8, 8)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("->", OUT, img.size)


if __name__ == "__main__":
    main()
