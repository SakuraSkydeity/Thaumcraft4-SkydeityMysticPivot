#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
四象石砖 · 激活态「八段鎏金楼台」（第二百一十九批：台阶化剖面 + 台面环形符文）。

第二百一十八批按用户「基座的模型上一版，但是贴图不好看 不要一大块纯色，有层次感」
把模型换回八段楼台、贴图按段分色。第二百一十九批再按用户

    「基座盘面上的字改的小一点多一点以圆形围绕，整体像楼梯一级级升高，
      底部还是会有点透视方块，你改为和底部完全接触，鎏金色多一点，
      然后边上四周就不用字了 贴图像古代的楼阁的镂空来一点」

改四件事：① 剖面从 45° 斜裙改成**一级一级的台阶**（竖直墙面 + 一圈水平踏面 / 檐底），
② 最底层外沿拉到 23.99、底面整片实心无格线，**和底部方块完全贴死**，
③ 台面上的符文从 8 枚 6×5 改成 **16 枚 3×3 的小字排成一圈**（内环 11.6 / 外环 19.6 之间的环带），
④ 侧面**不再刻字**，竖直段改成**古代楼阁的镂空花窗**（亮框 + 暗窗洞 + 棂条），整体鎏金再提一档。

剖面（PROFILE，r = 半宽，像素）：

    楼台（3×3 满铺，0 → 8px）        台身（中心 16×16，8 → 16px）
        地栿 0..2   r 23.99              柱身 8..10   r 8.00
        圭角 2..3   r 22.40（收出踏面）   束腰 10..12  r 6.60（收出踏面）
        下枋 3..6   r 22.40              上枋 12..14  r 8.00（挑出檐底）
        下枭 6..8   r 23.99（挑出檐底）   冰盘沿 14..16 r 8.80（挑出檐底）

半径一变就补一圈水平面：变窄的朝上（踏面）、变宽的朝下（檐底）；y=8 那一圈收进
由台面盖板自己代劳。于是每个接缝都是闭合的 —— 既像楼梯一级一级，又没有可透视的洞。
底面（y=0）与台面（y=8）各一张整片盖板，台身顶（y=16）一张压在玄枢台座底下。

<h2>像素坐标（1 格 = 16px，模型中心 = 24,24 = 3×3 的正中）</h2>

<pre>
  y 16.0 ── 台身顶（玄枢台座柱础落在这里）
  y 14.0 ── 冰盘沿起（台面段）
  y  8.0 ── 台面（楼台顶）：半格高，能站人；台身自此升起
  y  0.0 ── 落地（整片底面封死）
</pre>

<h2>用法</h2>

    python tools/gen_terrace_obj.py     # 出台 obj + mtl + 图集 + 两个模型壳子 + blockstate
"""
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot")
MODEL_DIR = os.path.join(ASSETS, "models")
BLOCK_MODEL_DIR = os.path.join(MODEL_DIR, "block")
BLOCKSTATE_DIR = os.path.join(ASSETS, "blockstates")
TEX_DIR = os.path.join(ASSETS, "textures/block")

PX = 16.0
TEX = 256
C = 24.0          # 模型中心（像素）—— 3×3 的正中
ORIGIN = 16.0     # 中心砖块的西北下角

# 图集分区：名字 -> (左列, 顶行, 宽, 高)
TILES = {
    "t_top":   (0,   0, 48, 48),    # 台面（俯视 48×48，覆盖整座 3×3）
    "t_riser": (48,  0, 16, 16),    # 中心台身顶面（16×16）
    "b_under": (96,  0, 48, 48),    # 整片底面（封死，求不漏光）
    "s_side":  (0,  48, 48, 16),    # 竖直面侧条：48px 实宽 × 0~16px 高，全高连续
}

# 八段楼台的剖面：(y0, y1, r)，r = 半宽（像素）。
# 每段是一圈竖直墙面；段与段之间半径一变就补一圈**水平面**（变窄朝上 = 踏面、
# 变宽朝下 = 檐底），于是整座从下往上一级一级收上去，像楼梯，而不是一段斜裙。
# 半径在 y=8 处的收法由台面盖板代劳（盖板本身就是那一圈水平面），不重复画。
PROFILE = (
    (0.0,  2.0, 23.99),   # 地栿（贴地，与底层方块完全接触）
    (2.0,  3.0, 22.40),   # 圭角（收 1.59 → y=2 一圈踏面朝上）
    (3.0,  6.0, 22.40),   # 下枋（竖直，贴花窗）
    (6.0,  8.0, 23.99),   # 下枭（挑 1.59 → y=6 一圈檐底朝下）
    (8.0, 10.0,  8.00),   # 柱身（从台面往上竖直，占满中心格）
    (10.0, 12.0, 6.60),   # 束腰（收 1.40 → y=10 踏面朝上）
    (12.0, 14.0, 8.00),   # 上枋（挑 1.40 → y=12 檐底朝下）
    (14.0, 16.0, 8.80),   # 冰盘沿（再挑 0.80 → y=14 檐底朝下）
)
DECK_Y = 8.0      # 台面高度（半格，能站人）
HALF = 23.99      # 3×3 外沿（几乎贴满，只留 0.01 免得与邻块同面打架）
TOP_R = 8.80      # 冰盘沿外放的半宽（台身顶盖板跟着它）

# ----------------------------------------------------------------------
# 色板：整体鎏金（第二百一十七批定档，第二百一十八批按段分色，
# 第二百一十九批整体再提一档 —— 用户「鎏金色多一点」）
# ----------------------------------------------------------------------
INK = (40, 30, 18)
GOLD0 = (92, 69, 32)      # 阴刻（最深）
GOLD1 = (124, 94, 44)
GOLD2 = (156, 120, 56)
GOLD3 = (182, 143, 68)    # 面
GOLD4 = (204, 164, 88)
GOLD5 = (224, 184, 108)
GOLD6 = (246, 212, 142)   # 亮边


# 古代符文：五枚 **3×3** 的阴刻字样（十 / 王 / 日 / 山 / 爻），1 = 刻痕。
# 第二百一十九批由 6×5 一路收到 3×3：12→16 枚排成一圈才排得下、又不会糊成一条带。
RUNES = (
    (".1.", "111", ".1."),
    ("111", ".1.", "111"),
    ("111", "1.1", "111"),
    ("1.1", "1.1", "111"),
    ("1.1", ".1.", "1.1"),
)

# 台面上的符文**排成一圈**围绕正中（第二百一十九批）：半径 15.5px、16 枚，
# 正好落在内环线 11.6 与外环线 19.6 之间的环带上，不压台沿、也不进台身脚下。
TOP_RUNE_RING = 15.5
TOP_RUNE_COUNT = 16


def grain(lu, lv):
    """确定性抖动（0~1），让金面近看不呆板；同一 (lu,lv) 永远同值，不会闪。"""
    n = (lu * 374761393 + lv * 668265263) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    n ^= n >> 16
    return (n % 8) / 7.0


def gold_field(lu, lv):
    """一整片鎏金：相邻只差几个色阶的抖动，远看是平的，近看才有极细的颗粒。"""
    g = grain(lu, lv)
    if g < 0.12:
        return (172, 135, 62)
    if g > 0.94:
        return (194, 156, 80)
    return GOLD3


def tone(base, lu, lv, span=16):
    """以某个金档作底色，按确定性抖动上下浮几个色阶（近看不呆板，同一像素不闪）。"""
    d = int((grain(lu, lv) - 0.5) * span)
    return (max(0, min(255, base[0] + d)),
            max(0, min(255, base[1] + d)),
            max(0, min(255, base[2] + d)))


def slat(base, lu, lv):
    """竖枋：每 16px（正好一格）一道阴刻、缝旁一像素提亮做倒角，其余是 base 的细抖动。"""
    m = lu % 16
    if m == 0:
        return GOLD0
    if m == 1:
        return (min(255, base[0] + 30), min(255, base[1] + 26), min(255, base[2] + 20))
    return tone(base, lu, lv)


def uv(region, lu, lv):
    rx, ry, rw, rh = TILES[region]
    lu = min(rw - 0.001, max(0.0, lu))
    lv = min(rh - 0.001, max(0.0, lv))
    return ((rx + lu) / TEX, 1.0 - (ry + rh - lv) / TEX)


def planar(p, frame, region):
    """俯视平面映射：frame = 该贴图覆盖的半宽（像素）。"""
    rx, ry, rw, rh = TILES[region]
    a = 2.0 * frame
    return ((p[0] - (C - frame)) / a * rw,
            (p[2] - (C - frame)) / a * rh)


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0])


def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


class Obj:
    """极简 OBJ 累加器：只写 v / vt / vn / f。"""

    def __init__(self, name, mtl, material):
        self.name = name
        self.mtl = mtl
        self.material = material
        self.v = []
        self.vt = []
        self.vn = []
        self.f = []
        self.flipped = 0

    def face(self, pts, region, uvs, expect=None):
        import math
        pts = list(pts)
        uvs = list(uvs)
        n = cross(sub(pts[1], pts[0]), sub(pts[2], pts[0]))
        if expect is not None and dot(n, expect) < 0.0:
            pts.reverse()
            uvs.reverse()
            self.flipped += 1
            n = cross(sub(pts[1], pts[0]), sub(pts[2], pts[0]))
        ln = math.sqrt(dot(n, n))
        if ln < 1e-9:
            raise AssertionError("%s: 退化面 %r" % (self.name, pts))
        n = (n[0] / ln, n[1] / ln, n[2] / ln)
        if len(pts) == 3:
            pts.append(pts[2])
            uvs.append(uvs[2])
        vi, ti = [], []
        for (p, t) in zip(pts, uvs):
            self.v.append(((p[0] - ORIGIN) / PX, p[1] / PX, (p[2] - ORIGIN) / PX))
            vi.append(len(self.v))
            self.vt.append(uv(region, t[0], t[1]))
            ti.append(len(self.vt))
        self.vn.append((round(n[0], 5), round(n[1], 5), round(n[2], 5)))
        ni = len(self.vn)
        self.f.append([(vi[k], ti[k], ni) for k in range(len(pts))])

    def cap(self, x0, x1, z0, z1, y, region, frame, up=True):
        """水平盖板（俯视平面映射，frame = 覆盖半宽）：up=False 时是封底。"""
        pts = [(x0, y, z0), (x1, y, z0), (x1, y, z1), (x0, y, z1)]
        self.face(pts, region, [planar(p, frame, region) for p in pts],
                  expect=(0.0, 1.0 if up else -1.0, 0.0))

    def bounds(self):
        xs = [p[0] for p in self.v]
        ys = [p[1] for p in self.v]
        zs = [p[2] for p in self.v]
        return (min(xs), min(ys), min(zs)), (max(xs), max(ys), max(zs))

    def write(self):
        lo, hi = self.bounds()
        lines = [
            "# SkydeityMysticPivot - %s (batch 219)" % self.name,
            "# 四象石砖激活态：3x3 八段鎏金楼台（地栿/圭角/下枋/下枭 + 柱身/束腰/上枋/冰盘沿），"
            "一级一级的台阶剖面，每圈水平面都补满、整座闭合不透",
            "mtllib %s" % self.mtl,
            "usemtl %s" % self.material,
            "g %s" % self.name,
        ]
        for (x, y, z) in self.v:
            lines.append("v %.5f %.5f %.5f" % (x, y, z))
        for (u, v) in self.vt:
            lines.append("vt %.5f %.5f" % (u, v))
        for (x, y, z) in self.vn:
            lines.append("vn %.5f %.5f %.5f" % (x, y, z))
        for face in self.f:
            lines.append("f " + " ".join("%d/%d/%d" % t for t in face))
        path = os.path.join(MODEL_DIR, self.name + ".obj")
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write("\n".join(lines) + "\n")
        print("  %-12s v=%-5d vt=%-5d vn=%-3d f=%-4d 绕序自动纠正 %d  "
              "包围盒 x[%.3f,%.3f] y[%.3f,%.3f] z[%.3f,%.3f]"
              % (self.name + ".obj", len(self.v), len(self.vt), len(self.vn),
                 len(self.f), self.flipped, lo[0], hi[0], lo[1], hi[1], lo[2], hi[2]))
        return len(self.f)


def ring(o, r, y0, y1):
    """一圈四面竖直墙：半径 r、高度 y0..y1（u = 世界坐标，v = 世界高度）。"""
    lo, hi = C - r, C + r
    # 西（-x）：u = 世界 z
    o.face([(lo, y0, lo), (lo, y0, hi), (lo, y1, hi), (lo, y1, lo)],
           "s_side", [(lo, y0), (hi, y0), (hi, y1), (lo, y1)],
           expect=(-1.0, 0.0, 0.0))
    # 东（+x）
    o.face([(hi, y0, hi), (hi, y0, lo), (hi, y1, lo), (hi, y1, hi)],
           "s_side", [(hi, y0), (lo, y0), (lo, y1), (hi, y1)],
           expect=(1.0, 0.0, 0.0))
    # 北（-z）：u = 世界 x
    o.face([(hi, y0, lo), (lo, y0, lo), (lo, y1, lo), (hi, y1, lo)],
           "s_side", [(hi, y0), (lo, y0), (lo, y1), (hi, y1)],
           expect=(0.0, 0.0, -1.0))
    # 南（+z）
    o.face([(lo, y0, hi), (hi, y0, hi), (hi, y1, hi), (lo, y1, hi)],
           "s_side", [(lo, y0), (hi, y0), (hi, y1), (lo, y1)],
           expect=(0.0, 0.0, 1.0))


def step(o, y, r0, r1):
    """y 高度上的一圈**水平面**：从 r0 到 r1。变窄朝上（踏面），变宽朝下（檐底）。"""
    a0, b0 = C - r0, C + r0
    a1, b1 = C - r1, C + r1
    up = r1 < r0
    reg = "t_top" if up else "b_under"
    expect = (0.0, 1.0 if up else -1.0, 0.0)
    # 西 / 东 / 北 / 南 四条水平梯形，绕序交给 face() 按 expect 自纠
    for pts in (
            [(a0, y, a0), (a0, y, b0), (a1, y, b1), (a1, y, a1)],
            [(b0, y, b0), (b0, y, a0), (b1, y, a1), (b1, y, b1)],
            [(b0, y, a0), (a0, y, a0), (a1, y, a1), (b1, y, a1)],
            [(a0, y, b0), (b0, y, b0), (b1, y, b1), (a1, y, b1)]):
        o.face(pts, reg, [planar(p, 24.0, reg) for p in pts], expect=expect)


def build_terrace():
    o = Obj("terrace", "terrace.mtl", "terrace")
    for i, (y0, y1, r) in enumerate(PROFILE):
        ring(o, r, y0, y1)
        # 下一段半径一变，就在这一段的顶上补一圈水平面（台阶 / 檐口）。
        # y=8 例外：那一圈收进由台面盖板本身代劳，再补一张会与它共面打架。
        if (i + 1 < len(PROFILE) and PROFILE[i + 1][2] != r
                and y1 != DECK_Y):
            step(o, y1, r, PROFILE[i + 1][2])
    # 三张盖板：落地封底、半格台面（能站人）、台身顶（台座柱础落在这里）
    o.cap(C - HALF, C + HALF, C - HALF, C + HALF, 0.0, "b_under", 24.0, up=False)
    o.cap(C - HALF, C + HALF, C - HALF, C + HALF, DECK_Y, "t_top", 24.0)
    o.cap(C - TOP_R, C + TOP_R, C - TOP_R, C + TOP_R, 16.0, "t_riser", TOP_R)
    return o.write()


# ----------------------------------------------------------------------
# 图集绘制
# ----------------------------------------------------------------------
def window(lu, lv):
    """一格古代楼阁的**直棂窗（镂空花窗）**：col 3 / 12 是亮金窗框，窗内暗金底 + 两道竖棂。

    一 16px 一格、一面三道窗；窗洞 2px 宽、满高不留中横棂，读成「一格一格的漏窗」
    而不是砖缝 —— 下枋只有 3px 高，加中横棂会把窗洞切成豆腐块。
    """
    m = lu % 16
    if m in (3, 12):
        return GOLD5                 # 窗框
    if 4 <= m <= 11:
        inner = m - 4                # 0..7
        if inner in (2, 5):
            return GOLD4             # 竖棂
        return tone(GOLD2, lu, lv, 14)   # 窗洞（比金面暗一档，读成镂空又不至于像打洞）
    return slat(GOLD3, lu, lv)


def s_side(lu, lv):
    """楼台侧面（u = 世界坐标 0~48 一圈，v = 世界高度 0~16）。

    第二百一十九批：段色照旧一级一级分明（挑檐亮、收进去的暗），但竖直段换成
    **古代楼阁的镂空花窗**；四周不再刻字 —— 字全部收进台面那一圈。
    """
    if lv == 0:
        base = tone(GOLD2, lu, lv)       # 贴地，不压黑（不能读成悬空）
    elif lv == 1:
        base = slat(GOLD3, lu, lv)       # 地栿
    elif lv == 2:
        base = GOLD4                     # 圭角（台阶立边）
    elif lv <= 5:
        base = window(lu, lv)            # 下枋：直棂窗（镂空花窗）
    elif lv <= 7:
        base = GOLD5 if lv == 6 else GOLD4   # 下枭檐口
    elif lv <= 9:
        base = slat(GOLD2, lu, lv)       # 柱身（整圈收进，压一档）
    elif lv == 10:
        base = tone(GOLD1, lu, lv)       # 束腰下口（暗）
    elif lv == 11:
        base = slat(GOLD2, lu, lv)       # 束腰
    elif lv <= 13:
        base = window(lu, lv)            # 上枋：直棂窗（镂空花窗）
    else:
        base = GOLD6 if lv == 15 else GOLD5   # 冰盘沿
    return base


def top_glyph(lu, lv):
    """台面圆形环带上的符文：16 枚 3×3 的小字均匀排一圈，命中刻痕就返回 True。"""
    import math
    px, pz = lu + 0.5, lv + 0.5
    for i in range(TOP_RUNE_COUNT):
        a = 2.0 * math.pi * i / TOP_RUNE_COUNT
        cx = C + TOP_RUNE_RING * math.cos(a)
        cz = C + TOP_RUNE_RING * math.sin(a)
        col = int(math.floor(px - cx + 1.5))
        row = int(math.floor(pz - cz + 1.5))
        if 0 <= col < 3 and 0 <= row < 3:
            return RUNES[i % len(RUNES)][row][col] == "1"
    return False


def t_top(lu, lv):
    """台面（俯视 48×48，覆盖整座 3×3）。

    方外沿（三道台沿线）+ 两圈**圆环线**，符文排在内环 11.9 与外环 19.0 之间的
    环带上绕中心一圈；九格石板交替深浅只是底子，不再抢字。
    """
    import math
    dx = lu + 0.5 - C
    dz = lv + 0.5 - C
    d = max(abs(dx), abs(dz))
    r = math.sqrt(dx * dx + dz * dz)
    if d >= 22.9:
        return GOLD6           # 与竖直面顶沿相接的那一圈亮边
    if d >= 21.9:
        return GOLD0           # 亮边里侧的一道阴刻
    if d >= 21.0:
        return GOLD4           # 阴刻内侧再收一档，台沿不至于一刀切
    if 19.0 <= r < 19.9:
        return GOLD0           # 外环线
    if 11.0 <= r < 11.9:
        return GOLD0           # 内环线
    if 9.9 <= r < 10.7:
        return GOLD5           # 台身脚下那一圈亮唇
    if 9.0 <= r < 9.9:
        return GOLD0           # 台身脚下的投影圈
    if top_glyph(lu, lv):
        return GOLD0
    base = (198, 160, 88) if ((lu // 16) + (lv // 16)) % 2 == 0 else (178, 142, 76)
    return tone(base, lu, lv, 14)


def t_riser(lu, lv):
    """台身顶面（16×16）：亮边 + 阴刻 + 正中一枚符文，其余是金面。"""
    dx = lu + 0.5 - 8.0
    dz = lv + 0.5 - 8.0
    d = max(abs(dx), abs(dz))
    if d >= 7.4:
        return GOLD6
    if d >= 6.4:
        return GOLD0
    col = lu - 7
    row = lv - 7
    if 0 <= col < 3 and 0 <= row < 3 and RUNES[0][row][col] == "1":
        return GOLD0
    return gold_field(lu, lv)


def b_under(lu, lv):
    """底面：整片实心暗金，**不做格线** —— 贴在下方方块顶上，看上去就是贴死的。"""
    d = max(abs(lu + 0.5 - C), abs(lv + 0.5 - C))
    if d > 23.3:
        return INK
    return tone(GOLD1, lu, lv, 18)


PAINTERS = {"s_side": s_side, "t_top": t_top, "t_riser": t_riser, "b_under": b_under}


def png_write(path, pixels):
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for (r, g, b, a) in row:
            raw.extend((r, g, b, a))

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    ihdr = struct.pack(">IIBBBBB", TEX, TEX, 8, 6, 0, 0, 0)
    blob = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(blob)
    print("  %-22s %dx%d" % (os.path.basename(path), TEX, TEX))


def build_atlas():
    px = [[(18, 20, 26, 255) for _ in range(TEX)] for _ in range(TEX)]
    for name, (ox, oy, rw, rh) in TILES.items():
        fn = PAINTERS[name]
        for lv in range(rh):
            row = px[oy + (rh - 1 - lv)]
            for lu in range(rw):
                c = fn(lu, lv)
                row[ox + lu] = (c[0], c[1], c[2], 255)
    png_write(os.path.join(TEX_DIR, "sixiang_terrace.png"), px)


# ----------------------------------------------------------------------
# 模型壳子 / blockstate
# ----------------------------------------------------------------------
ACTIVE_MODEL_JSON = """{
  "loader": "neoforge:obj",
  "model": "skydeitymysticpivot:models/terrace.obj",
  "textures": {
    "particle": "skydeitymysticpivot:block/sixiang_terrace"
  },
  "ambientocclusion": false,
  "automatic_culling": false,
  "shade_quads": true,
  "flip_v": true,
  "emissive_ambient": false,
  "render_type": "minecraft:solid"
}
"""

# 另外八格：整座台由中心那一格独家渲染，它们只留粒子贴图，不画任何面
EMPTY_MODEL_JSON = """{
  "textures": {
    "particle": "skydeitymysticpivot:block/sixiang_terrace"
  }
}
"""

BRICK_MODEL = "skydeitymysticpivot:block/sixiang_stone_brick"
EMPTY_MODEL = "skydeitymysticpivot:block/sixiang_brick_empty"
ACTIVE_MODEL = "skydeitymysticpivot:block/sixiang_stone_brick_active"

BLOCKSTATE_JSON = """{
  "variants": {
    "active=false,center=false": { "model": "%(brick)s" },
    "active=false,center=true":  { "model": "%(brick)s" },
    "active=true,center=true":   { "model": "%(active)s" },
    "active=true,center=false":  { "model": "%(empty)s" }
  }
}
""" % {"brick": BRICK_MODEL, "active": ACTIVE_MODEL, "empty": EMPTY_MODEL}


def write_text(path, text):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print("  %s" % os.path.relpath(path, ROOT))


if __name__ == "__main__":
    print("OBJ:")
    nf = build_terrace()
    print("图集:")
    build_atlas()
    print("模型 / blockstate:")
    write_text(os.path.join(MODEL_DIR, "terrace.mtl"),
               """# SkydeityMysticPivot - material for the stepped eight-tier terrace (batch 219)
# Kd 纯白，明暗全交给贴图；材质名必须与 OBJ 的 usemtl 一字不差
newmtl terrace
Kd 1.000000 1.000000 1.000000
map_Kd skydeitymysticpivot:block/sixiang_terrace
""")
    write_text(os.path.join(BLOCK_MODEL_DIR, "sixiang_stone_brick_active.json"),
               ACTIVE_MODEL_JSON)
    write_text(os.path.join(BLOCK_MODEL_DIR, "sixiang_brick_empty.json"),
               EMPTY_MODEL_JSON)
    write_text(os.path.join(BLOCKSTATE_DIR, "sixiang_stone_brick.json"),
               BLOCKSTATE_JSON)
    print("done, %d 面" % nf)
