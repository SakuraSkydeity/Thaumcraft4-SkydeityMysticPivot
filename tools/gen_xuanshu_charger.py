#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
玄枢充能器（xuanshu_charger）—— 第六十九批 **鼎内外加图案**。

用户原话：「1.鼎内外加一点图案」。

<h2>为什么图案只能做成「三维浮雕」而不是「贴图画花」</h2>
八棱腹的**每一个面**在贴图上只摊到**一条极窄的竖带**（本版 2 px），
贴图里画任何横向花纹，进游戏都会被拉成一条**横贯整个棱面的长带**——
这正是第六十七批用户反馈的「贴图不要一层一层太丑了」的成因之一。
所以本版的所有纹样都走**几何**：浮雕是三维的，从侧面看有真实的轮廓与明暗，
不会被 UV 拉伸吃掉。

<h2>本批加的东西</h2>
  * **外面**：8 条**扉棱**（沿八棱的 8 条棱缝，从腹底立到口沿，向外凸 0.022 的鎏金竖脊，
    商周青铜鼎的经典形制）+ **每面一枚星辰钉**（正八面体，鎏金）——
    于是外壁读作「黑金腹、鎏金竖脊、面心一点星」；
  * **里面**：碗内壁拆成**两段**——下段暗铜、口内一圈**鎏金弦纹带**，
    再在每面钉一枚**内壁星钉**（发光时随鎏金带一起提亮）。

<h2>形制（方块局部坐标 0..1，Y 向上）</h2>
  * 三足（收分方柱）→ 八棱腹 → 口沿外翻 → 内沿斜面 → 碗内壁 → 碗底；
  * 双耳：口沿上 ±x 一对立耳（整只走鎏金那一档）；
  * 星辰鼎心：腹内一蓬八棱晶焰，待机昏暝、激活炽亮，正上方浮着灵气节点。

<h2>贴图（32×32，UV 约定 flip_v: false ⇒ vt.y = 0 对贴图顶行）</h2>
<pre>
   u:  0        16        24        32
       ┌─────────┬─────────┬─────────┐
       │ 八棱腹   │ 星辰鼎心 │ 碗内壁   │
       │ 8面×2px │ 8面×1px │ 8面×1px │
       └─────────┴─────────┴─────────┘
   横分三段，各段行向含义：
     腹：rows 0..7 口沿鎏金箍 → rows 8..31 黑金腹（**只两段**，段内极缓渐变）；
     心：rows 0..31 晶尖近白 → 晶根浓紫；
     内：rows 0..9 口内鎏金弦纹带 → rows 10..31 暗铜碗底。
</pre>
★ 模型 JSON 的 {@code flip_v} 本批由 true 改回 **false**：全项目其余 OBJ（水晶簇）
与离线预览工具都按「vt.y = 0 就是贴图顶行」这套约定走，之前 true 会让整张贴图在
游戏里**上下颠倒**（口沿的金箍跑到鼎底、晶尖的亮色跑到根部）。

产出：
  - models/xuanshu_charger.obj + .mtl
  - textures/block/xuanshu_charger.png         （待机，32×32）
  - textures/block/xuanshu_charger_active.png  （32×128 四帧动画）+ .mcmeta
"""
import math
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_DIR = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/models")
BLOCK_TEX_DIR = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/block")

MTL_NAME = "xuanshu_charger"
TEX_NAME = "xuanshu_charger"
MTL_MTL = "ding"

TEX = 32                 # 贴图边长
ROWS = 32

# 整鼎放大系数（用户第七十批：「鼎的模型稍微大一点」）。
#
# 做法是在**落盘前**把所有顶点统一变换一次，而不是去改那一堆几何常量 ——
# 常量之间互相引用（腹半径 → 扉棱落点 → 星钉落点 → 碗内壁…），手改必错。
# 变换以「方块中心为水平原点、地面 y=0 为竖直原点」：底下的三足照旧踩在 y=0，
# 口沿、耳、晶焰一起长高，整尊鼎在方块里占得更满。
MODEL_SCALE = 1.10

BODY_U = (0.0, 0.5)      # 左半 16 px = 八棱鼎腹的八条竖带（每面 2 px）
CORE_U = (0.5, 0.75)     # 中段 8 px = 星辰鼎心的八条竖带
INNER_U = (0.75, 1.0)    # 右段 8 px = 碗内壁的八条竖带

# 八棱各面的基础明度偏移（正是「八棱」读数的来源）
BODY_TONE = [14, 6, -2, -10, 10, 2, -6, -14]
CORE_TONE = [22, 10, -4, -14, 16, 4, -8, -18]
INNER_TONE = [10, 4, -2, -8, 8, 2, -4, -10]

# 所有三维星钉取的那一个「鎏金」像素（见 _draw：它在腹的金箍里，本来就是纯金色，
# 借来给星钉不会破坏腹面 —— 第六十八批为了让星钉发白发亮，曾在贴图上单独落点，
# 而八棱腹每面只有 1 px 宽，落点会被拉成一条横带；本版改用现成的金色像素，零副作用）
SPARK_UV = (1.5 / TEX, 1.5 / TEX)


# ---------------------------------------------------------------- 网格 ----

def num(v):
    """紧凑写小数：最多 4 位、去掉尾零（OBJ 因此小一半，0.0001 的精度对 16 格方块绰绰有余）。"""
    text = ("%.4f" % v).rstrip("0").rstrip(".")
    return text if text not in ("", "-0") else "0"


class Mesh:
    def __init__(self):
        self.v = []
        self.vt = []
        self.vn = []
        self.faces = []
        self.marks = []

    def vertex(self, p):
        self.v.append(p)
        return len(self.v)

    def uv(self, uv):
        self.vt.append((float(uv[0]), float(uv[1])))
        return len(self.vt)

    def normal(self, n):
        key = tuple(round(c, 5) for c in n)
        for i, existing in enumerate(self.vn):
            if tuple(round(c, 5) for c in existing) == key:
                return i + 1
        self.vn.append(n)
        return len(self.vn)

    def _tri(self, a, b, c, uva, uvb, uvc):
        ax, ay, az = self.v[a - 1]
        bx, by, bz = self.v[b - 1]
        cx, cy, cz = self.v[c - 1]
        ux, uy, uz = bx - ax, by - ay, bz - az
        vx, vy, vz = cx - ax, cy - ay, cz - az
        nx, ny, nz = uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx
        length = math.sqrt(nx * nx + ny * ny + nz * nz) or 1.0
        ni = self.normal((nx / length, ny / length, nz / length))
        ia, ib, ic = self.uv(uva), self.uv(uvb), self.uv(uvc)
        self.faces.append((a, ia, ni, b, ib, ni, c, ic, ni))

    def tri(self, a, b, c, uva, uvb, uvc):
        self._tri(a, b, c, uva, uvb, uvc)

    def quad(self, a, b, c, d, uva, uvb, uvc, uvd):
        self._tri(a, b, c, uva, uvb, uvc)
        self._tri(a, c, d, uva, uvc, uvd)

    def volume(self, f0=0, f1=None):
        f1 = len(self.faces) if f1 is None else f1
        vol = 0.0
        for face in self.faces[f0:f1]:
            a, b, c = self.v[face[0] - 1], self.v[face[3] - 1], self.v[face[6] - 1]
            vol += (a[0] * (b[1] * c[2] - b[2] * c[1])
                    - a[1] * (b[0] * c[2] - b[2] * c[0])
                    + a[2] * (b[0] * c[1] - b[1] * c[0])) / 6.0
        return vol

    def primitive(self, name, convex=True):
        return _Primitive(self, name, convex)


class _Primitive:
    def __init__(self, mesh, name, convex):
        self.mesh, self.name, self.convex = mesh, name, convex

    def __enter__(self):
        self.f0 = len(self.mesh.faces)
        self.v0 = len(self.mesh.v)
        return self

    def __exit__(self, *exc):
        if exc[0] is not None:
            return False
        n = len(self.mesh.faces)
        vol = self.mesh.volume(self.f0, n)
        self.mesh.marks.append((self.name, n - self.f0, vol))
        if self.convex:
            if vol <= 1e-9:
                raise AssertionError("图元 %s 有向体积 %.6f ≤ 0：有面朝里" % (self.name, vol))
            pts = self.mesh.v[self.v0:]
            cx = sum(p[0] for p in pts) / len(pts)
            cy = sum(p[1] for p in pts) / len(pts)
            cz = sum(p[2] for p in pts) / len(pts)
            for k, face in enumerate(self.mesh.faces[self.f0:n]):
                a, b, c = (self.mesh.v[face[0] - 1], self.mesh.v[face[3] - 1],
                           self.mesh.v[face[6] - 1])
                fx = (a[0] + b[0] + c[0]) / 3.0 - cx
                fy = (a[1] + b[1] + c[1]) / 3.0 - cy
                fz = (a[2] + b[2] + c[2]) / 3.0 - cz
                ux, uy, uz = b[0] - a[0], b[1] - a[1], b[2] - a[2]
                vx, vy, vz = c[0] - a[0], c[1] - a[1], c[2] - a[2]
                nx, ny, nz = uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx
                if nx * fx + ny * fy + nz * fz <= 0.0:
                    raise AssertionError("图元 %s 第 %d 个面朝向不对" % (self.name, k))
        return False


def ring(cx, cz, r, y, seg, phase):
    return [(cx + math.cos(phase + 2.0 * math.pi * i / seg) * r, y,
             cz + math.sin(phase + 2.0 * math.pi * i / seg) * r)
             for i in range(seg)]


def add_wall(mesh, cx, cz, r_bot, r_top, y_bot, y_top, seg, phase, uv_fn, outward=True):
    """一圈收分侧壁。outward=False 时整圈反过来（朝轴心），用于碗内壁。"""
    bot = ring(cx, cz, r_bot, y_bot, seg, phase)
    top = ring(cx, cz, r_top, y_top, seg, phase)
    for i in range(seg):
        j = (i + 1) % seg
        pa, pb, pc, pd = uv_fn(i, j)
        if outward:
            mesh.quad(mesh.vertex(bot[j]), mesh.vertex(bot[i]),
                      mesh.vertex(top[i]), mesh.vertex(top[j]), pb, pa, pd, pc)
        else:
            mesh.quad(mesh.vertex(bot[i]), mesh.vertex(bot[j]),
                      mesh.vertex(top[j]), mesh.vertex(top[i]), pa, pb, pc, pd)


def add_cap(mesh, cx, cz, r, y, seg, phase, uv, up=True):
    """一片圆盘封口：up=True 朝 +y，否则朝 -y。"""
    pts = ring(cx, cz, r, y, seg, phase)
    c = mesh.vertex((cx, y, cz))
    for i in range(seg):
        j = (i + 1) % seg
        if up:
            mesh.tri(mesh.vertex(pts[j]), mesh.vertex(pts[i]), c, uv, uv, uv)
        else:
            mesh.tri(mesh.vertex(pts[i]), mesh.vertex(pts[j]), c, uv, uv, uv)


def add_hexahedron(mesh, lo, hi, uv_side, uv_top=None, uv_bot=None):
    """由底层四边形 lo[4] 与顶层四边形 hi[4]（**坐标点**、同绕向、同顺序）拼一个六面体。

    绕序与 {@link add_tbox} 完全一致 —— 那套已经被 {@code primitive} 的两级校验
    与游戏内观感双重验过。uv_side = (u0, u1, v_bot, v_top)：四个侧面共用一条竖带。
    """
    u0, u1, vb, vt = uv_side
    b = [mesh.vertex(p) for p in lo]
    t = [mesh.vertex(p) for p in hi]
    mesh.quad(b[1], b[0], t[0], t[1], (u1, vb), (u0, vb), (u0, vt), (u1, vt))   # 第 0 边
    mesh.quad(b[2], b[1], t[1], t[2], (u1, vb), (u0, vb), (u0, vt), (u1, vt))   # 第 1 边
    mesh.quad(b[3], b[2], t[2], t[3], (u1, vb), (u0, vb), (u0, vt), (u1, vt))   # 第 2 边
    mesh.quad(b[0], b[3], t[3], t[0], (u1, vb), (u0, vb), (u0, vt), (u1, vt))   # 第 3 边
    tu = uv_top or (u0, vt)
    bu = uv_bot or (u0, vb)
    mesh.quad(t[3], t[2], t[1], t[0], tu, tu, tu, tu)                           # 顶（+y）
    mesh.quad(b[0], b[1], b[2], b[3], bu, bu, bu, bu)                           # 底（-y）


def add_tbox(mesh, x0, z0, x1, z1, tx0, tz0, tx1, tz1, y0, y1, uv, v_span=(1.0, 0.0),
             uv_top=None, uv_bot=None):
    """上下两个矩形口径的收分方柱（外向）。uv 为侧面某一列 (u0, u1)。

    {@code v_span} 是这个部件在贴图上取的竖向区间（底 → 顶）。★ 必须显式给：
    留默认的 1.0→0.0 会把整张贴图全铺到一根柱子上，进游戏就是一条条横纹。
    """
    lo = [(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)]
    hi = [(tx0, y1, tz0), (tx1, y1, tz0), (tx1, y1, tz1), (tx0, y1, tz1)]
    add_hexahedron(mesh, lo, hi, (uv[0], uv[1], v_span[0], v_span[1]), uv_top, uv_bot)


# ---------------------------------------------------------------- 几何 ----

SEG = 8
PHASE = math.pi / 8.0        # 八棱：让正南面正对镜头，UV 竖带与面对齐
STEP = 2.0 * math.pi / SEG   # 每面的圆心角
LEG_R = 0.235

LEG_BOT_Y, LEG_TOP_Y = 0.0, 0.185
BELLY_BOT_Y, BELLY_MID_Y, LIP_Y = 0.185, 0.455, 0.545
R_BELLY_BOT, R_BELLY_MID, R_LIP = 0.235, 0.372, 0.430
R_MOUTH = 0.350              # 口沿内沿半径（碗口）
MOUTH_TOP_Y = LIP_Y + 0.030  # 内沿斜面顶 = 碗口高度
R_FLOOR, FLOOR_Y = 0.135, 0.315
INNER_BAND_Y = 0.505         # 碗内壁鎏金弦纹带的下沿
CORE_BOT_Y, CORE_TOP_Y, CORE_APEX_Y = 0.295, 0.655, 0.775
R_CORE_BOT, R_CORE_TOP = 0.156, 0.062

# 纹样参数
FLANGE_OUT = 0.022           # 扉棱向外凸出
FLANGE_HW = 0.017            # 扉棱的切向半宽
STAR_Y_OUT = 0.400           # 外面那圈星徽的高度
STAR_Y_IN = 0.470            # 内壁星钉的高度
STAR_R_OUT = 0.021
STAR_R_IN = 0.018


def body_uv(v_bot, v_top):
    """八棱鼎身的侧面 uv：第 i 面取左半第 i 条竖带。"""
    def fn(i, j):
        a = BODY_U[0] + (BODY_U[1] - BODY_U[0]) * i / float(SEG)
        b = BODY_U[0] + (BODY_U[1] - BODY_U[0]) * j / float(SEG)
        return ((a, v_bot), (b, v_bot), (b, v_top), (a, v_top))
    return fn


def core_uv(v_bot, v_top):
    def fn(i, j):
        a = CORE_U[0] + (CORE_U[1] - CORE_U[0]) * i / float(SEG)
        b = CORE_U[0] + (CORE_U[1] - CORE_U[0]) * j / float(SEG)
        return ((a, v_bot), (b, v_bot), (b, v_top), (a, v_top))
    return fn


def inner_uv(v_bot, v_top):
    """碗内壁的侧面 uv：第 i 面取右段第 i 条竖带（暗铜 / 鎏金两种行档）。"""
    def fn(i, j):
        a = INNER_U[0] + (INNER_U[1] - INNER_U[0]) * i / float(SEG)
        b = INNER_U[0] + (INNER_U[1] - INNER_U[0]) * j / float(SEG)
        return ((a, v_bot), (b, v_bot), (b, v_top), (a, v_top))
    return fn


def belly_radius(y):
    """八棱腹外壁在高度 y 处的**顶点**半径（两段收分之间线性插值）。"""
    if y <= BELLY_MID_Y:
        t = (y - BELLY_BOT_Y) / (BELLY_MID_Y - BELLY_BOT_Y)
        return R_BELLY_BOT + (R_BELLY_MID - R_BELLY_BOT) * t
    t = (y - BELLY_MID_Y) / (LIP_Y - BELLY_MID_Y)
    return R_BELLY_MID + (R_LIP - R_BELLY_MID) * min(1.0, max(0.0, t))


def mouth_radius(y):
    """碗内壁在高度 y 处的顶点半径（从碗底圆到碗口的一条直线）。"""
    t = (y - FLOOR_Y) / (MOUTH_TOP_Y - FLOOR_Y)
    return R_FLOOR + (R_MOUTH - R_FLOOR) * min(1.0, max(0.0, t))


def facet_plane(y, seg=SEG):
    """高度 y 处，某个棱面**平面**到轴心的距离（正多边形的边心距 = R·cos(π/n)）。"""
    return belly_radius(y) * math.cos(math.pi / seg)


def add_leg(mesh, cx, cz):
    """一根收分方柱足：底半宽 0.040 → 顶半宽 0.056（上粗下细）。"""
    hw0, hw1 = 0.040, 0.056
    add_tbox(mesh, cx - hw0, cz - hw0, cx + hw0, cz + hw0,
             cx - hw1, cz - hw1, cx + hw1, cz + hw1,
             LEG_BOT_Y, LEG_TOP_Y, (BODY_U[0], BODY_U[0] + 0.0625),
             v_span=(1.00, 0.90), uv_top=(0.10, 0.92), uv_bot=(0.10, 0.99))


def add_ding(mesh):
    """鼎身：外壁 + 口沿 + 内沿 + 碗内壁（两段）+ 碗底 + 鼎底（一个非凸的碗壳）。"""
    with mesh.primitive("ding_shell", convex=False):
        # 外壁：腹下沿 → 腹中 → 口沿外翻
        add_wall(mesh, 0.5, 0.5, R_BELLY_BOT, R_BELLY_MID, BELLY_BOT_Y, BELLY_MID_Y,
                 SEG, PHASE, body_uv(0.97, 0.44))
        add_wall(mesh, 0.5, 0.5, R_BELLY_MID, R_LIP, BELLY_MID_Y, LIP_Y,
                 SEG, PHASE, body_uv(0.42, 0.28))
        # 口沿内沿：从外沿收回到碗口（仍是外向面，就是唇边的上表面）
        add_wall(mesh, 0.5, 0.5, R_LIP, R_MOUTH, LIP_Y, MOUTH_TOP_Y,
                 SEG, PHASE, body_uv(0.20, 0.04))
        # 碗内壁：朝轴心（outward=False），下段暗铜 + 口内一圈鎏金弦纹带
        add_wall(mesh, 0.5, 0.5, R_FLOOR, mouth_radius(INNER_BAND_Y), FLOOR_Y, INNER_BAND_Y,
                 SEG, PHASE, inner_uv(0.95, 0.40), outward=False)
        add_wall(mesh, 0.5, 0.5, mouth_radius(INNER_BAND_Y), R_MOUTH, INNER_BAND_Y, MOUTH_TOP_Y,
                 SEG, PHASE, inner_uv(0.22, 0.02), outward=False)
        # 碗底盘（朝 +y）与鼎底（朝 -y）
        add_cap(mesh, 0.5, 0.5, R_FLOOR, FLOOR_Y, SEG, PHASE, (0.30, 0.92), up=True)
        add_cap(mesh, 0.5, 0.5, R_BELLY_BOT, BELLY_BOT_Y, SEG, PHASE, (0.30, 0.98), up=False)


def add_flange(mesh, theta):
    """一条扉棱：沿八棱的某条**棱缝**从腹底立到口沿，向外凸 {@link FLANGE_OUT}。

    截面是一个 (径向 × 切向) 的矩形，底层的矩形贴在下腹的棱上、顶层贴着口沿的棱上，
    于是它天然顺着腹的收分斜上去（不是一根直柱子）。整条走鎏金那一档贴图行。
    """
    co, si = math.cos(theta), math.sin(theta)

    def quad(y):
        r = belly_radius(y)
        return [(0.5 + (r + s) * co - t * si, y, 0.5 + (r + s) * si + t * co)
                for (s, t) in ((0.0, -FLANGE_HW), (FLANGE_OUT, -FLANGE_HW),
                               (FLANGE_OUT, FLANGE_HW), (0.0, FLANGE_HW))]

    lo = quad(BELLY_BOT_Y + 0.004)
    hi = quad(LIP_Y)
    add_hexahedron(mesh, lo, hi, (BODY_U[0], BODY_U[0] + 0.0625, 0.22, 0.04),
                   uv_top=(0.05, 0.06), uv_bot=(0.05, 0.24))


def add_ear(mesh, sx):
    """一只立耳：贴在口沿上的收分板（整只走鎏金那一档）。sx = ±1。"""
    cx = 0.5 + sx * 0.370
    hw_bot_x, hw_top_x = 0.046, 0.034
    hw_bot_z, hw_top_z = 0.086, 0.060
    add_tbox(mesh, cx - hw_bot_x, 0.5 - hw_bot_z, cx + hw_bot_x, 0.5 + hw_bot_z,
             cx - hw_top_x, 0.5 - hw_top_z, cx + hw_top_x, 0.5 + hw_top_z,
             0.512, 0.726, (BODY_U[0] + 0.125, BODY_U[0] + 0.1875),
             v_span=(0.22, 0.04), uv_top=(0.06, 0.06), uv_bot=(0.06, 0.24))


def add_core(mesh):
    """星辰鼎心：八棱晶焰，收尖。"""
    add_wall(mesh, 0.5, 0.5, R_CORE_BOT, R_CORE_TOP, CORE_BOT_Y, CORE_TOP_Y,
             SEG, PHASE, core_uv(0.96, 0.06))
    top = ring(0.5, 0.5, R_CORE_TOP, CORE_TOP_Y, SEG, PHASE)
    apex = mesh.vertex((0.5, CORE_APEX_Y, 0.5))
    for i in range(SEG):
        j = (i + 1) % SEG
        a = CORE_U[0] + (CORE_U[1] - CORE_U[0]) * i / float(SEG)
        b = CORE_U[0] + (CORE_U[1] - CORE_U[0]) * j / float(SEG)
        mesh.tri(mesh.vertex(top[j]), mesh.vertex(top[i]), apex,
                 (b, 0.06), (a, 0.06), ((a + b) / 2.0, 0.00))
    add_cap(mesh, 0.5, 0.5, R_CORE_BOT, CORE_BOT_Y, SEG, PHASE, (0.62, 0.97), up=False)


def add_spark(mesh, name, cx, cy, cz, r, uv=SPARK_UV):
    """一粒三维小星钉（正八面体）—— 外面贴在棱面上、里面贴在碗内壁上。

    八面体是**闭合凸体** ⇒ 照样过 {@code primitive} 的两级校验（体积 + 逐面背离形心），
    不像贴图落点那样会被棱面拉成一条横带。
    """
    with mesh.primitive(name):
        xp = mesh.vertex((cx + r, cy, cz))
        xm = mesh.vertex((cx - r, cy, cz))
        yp = mesh.vertex((cx, cy + r, cz))
        ym = mesh.vertex((cx, cy - r, cz))
        zp = mesh.vertex((cx, cy, cz + r))
        zm = mesh.vertex((cx, cy, cz - r))
        for (a, b, c) in ((yp, zp, xp), (yp, xm, zp), (yp, zm, xm), (yp, xp, zm),
                          (ym, xp, zp), (ym, zp, xm), (ym, xm, zm), (ym, zm, xp)):
            mesh.tri(a, b, c, uv, uv, uv)


def add_outer_stars(mesh):
    """外面：每一面的**面心**钉一枚星辰（八枚正好围成一圈）。"""
    for k in range(SEG):
        theta = PHASE + (k + 0.5) * STEP
        rr = facet_plane(STAR_Y_OUT) + 0.008
        cx = 0.5 + math.cos(theta) * rr
        cz = 0.5 + math.sin(theta) * rr
        add_spark(mesh, "star_out_%d" % k, cx, STAR_Y_OUT, cz, STAR_R_OUT)


def add_inner_stars(mesh):
    """里面：碗内壁每一面的面心钉一枚星辰（朝轴心，从上面看得到）。"""
    r_surf = mouth_radius(STAR_Y_IN)
    rr = r_surf * math.cos(math.pi / SEG) - 0.006
    for k in range(SEG):
        theta = PHASE + (k + 0.5) * STEP
        cx = 0.5 + math.cos(theta) * rr
        cz = 0.5 + math.sin(theta) * rr
        add_spark(mesh, "star_in_%d" % k, cx, STAR_Y_IN, cz, STAR_R_IN)


def build_obj():
    mesh = Mesh()
    for k in range(3):
        ang = math.pi / 2.0 + 2.0 * math.pi * k / 3.0
        with mesh.primitive("leg_%d" % k):
            add_leg(mesh, 0.5 + math.cos(ang) * LEG_R, 0.5 + math.sin(ang) * LEG_R)
    with mesh.primitive("core"):
        add_core(mesh)
    add_ding(mesh)
    # 八条扉棱：坐落在八棱的**棱缝**上（PHASE + k·STEP）。双耳在**面心**那条线上
    # （0° / 180°），所以耳与扉棱天然错开、互不打架。
    for k in range(SEG):
        with mesh.primitive("flange_%d" % k):
            add_flange(mesh, PHASE + k * STEP)
    add_outer_stars(mesh)
    add_inner_stars(mesh)
    with mesh.primitive("ear_+x"):
        add_ear(mesh, +1)
    with mesh.primitive("ear_-x"):
        add_ear(mesh, -1)

    # ★ 整鼎放大：水平以 0.5 为中心、竖直以 y=0 为原点（三足照旧踩地）
    mesh.v = [(0.5 + (x - 0.5) * MODEL_SCALE, y * MODEL_SCALE, 0.5 + (z - 0.5) * MODEL_SCALE)
              for (x, y, z) in mesh.v]

    os.makedirs(MODEL_DIR, exist_ok=True)
    obj_path = os.path.join(MODEL_DIR, MTL_NAME + ".obj")
    mtl_path = os.path.join(MODEL_DIR, MTL_NAME + ".mtl")
    with open(obj_path, "w", encoding="utf-8") as f:
        f.write("# SkydeityMysticPivot - xuanshu charger as a gilded star ding (batch 69)\n")
        f.write("# three legs, octagonal belly, flared lip, open bowl, two ears, crystal flame core\n")
        f.write("# ornaments: 8 flanges + 8 outer star studs + gilt inner band + 8 inner star studs\n")
        f.write("mtllib %s.mtl\n" % MTL_NAME)
        f.write("usemtl %s\n" % MTL_MTL)
        f.write("g ding\n")
        for (x, y, z) in mesh.v:
            f.write("v %s %s %s\n" % (num(x), num(y), num(z)))
        for (u, v) in mesh.vt:
            f.write("vt %s %s\n" % (num(u), num(v)))
        for (x, y, z) in mesh.vn:
            f.write("vn %s %s %s\n" % (num(x), num(y), num(z)))
        for face in mesh.faces:
            f.write("f %d/%d/%d %d/%d/%d %d/%d/%d\n" % face)
    with open(mtl_path, "w", encoding="utf-8") as f:
        f.write("# SkydeityMysticPivot - material for the xuanshu star ding\n")
        f.write("newmtl %s\n" % MTL_MTL)
        f.write("Kd 1.000000 1.000000 1.000000\n")
        f.write("map_Kd skydeitymysticpivot:block/%s\n" % TEX_NAME)
    # ★ 激活态的第二份材质库：OBJ 的贴图**只认 MTL 里的 map_Kd**（模型 JSON 的
    # textures 只喂给 owner.getMaterial("#…") 那条路），所以两份 JSON 共用同一个 OBJ 时
    # 一直取的是同一张贴图 —— 也就是说激活态那张 4 帧动画从来没被用到过。
    # 这里补一份 map_Kd 指向 _active 的材质库，由 block/xuanshu_charger_active.json 的
    # mtl_override 指过来（NeoForge 的 obj loader 明确支持这个字段）。
    active_mtl_path = os.path.join(MODEL_DIR, MTL_NAME + "_active.mtl")
    with open(active_mtl_path, "w", encoding="utf-8") as f:
        f.write("# SkydeityMysticPivot - active (animated) material for the xuanshu star ding\n")
        f.write("newmtl %s\n" % MTL_MTL)
        f.write("Kd 1.000000 1.000000 1.000000\n")
        f.write("map_Kd skydeitymysticpivot:block/%s_active\n" % TEX_NAME)

    xs = [p[0] for p in mesh.v]
    ys = [p[1] for p in mesh.v]
    zs = [p[2] for p in mesh.v]
    out = [p for p in mesh.v if not (0.0 <= p[0] <= 1.0 and 0.0 <= p[1] <= 1.0 and 0.0 <= p[2] <= 1.0)]
    print("obj ->", os.path.relpath(obj_path, ROOT),
          "| v", len(mesh.v), "vt", len(mesh.vt), "vn", len(mesh.vn), "f", len(mesh.faces))
    print("bounds: x %.3f~%.3f  y %.3f~%.3f  z %.3f~%.3f"
          % (min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)))
    for (name, nfaces, vol) in mesh.marks:
        print("   %-12s faces %3d  vol %+.6f" % (name, nfaces, vol))
    print("vertices out of [0,1]:", len(out))
    assert not out, "有顶点探出方块局部坐标 %s" % (out[:3],)
    return obj_path


def selftest():
    """用同一批函数搭几个**确实闭合**的体，先验一遍「外向 / 朝轴心」两条代码路径。

    凸图元的朝向由 {@code Mesh.primitive} 逐面查（法线必须背离形心）；
    反向那一路的闭合体整体体积应为**负**——这正是碗内壁要的效果。
    """
    # ① 外向：棱台 + 上下封口
    m = Mesh()
    with m.primitive("selftest_outward_frustum"):
        add_wall(m, 0.5, 0.5, 0.20, 0.35, 0.10, 0.40, SEG, PHASE, body_uv(1.0, 0.0))
        add_cap(m, 0.5, 0.5, 0.20, 0.10, SEG, PHASE, (0.30, 1.0), up=False)
        add_cap(m, 0.5, 0.5, 0.35, 0.40, SEG, PHASE, (0.30, 0.0), up=True)
    print("   [selftest] outward frustum   vol %+.6f  (must be > 0)" % m.volume())

    # ② 朝轴心：同一形状整圈反过来（封口也跟着反过来）
    n = Mesh()
    add_wall(n, 0.5, 0.5, 0.20, 0.35, 0.10, 0.40, SEG, PHASE,
             body_uv(1.0, 0.0), outward=False)
    add_cap(n, 0.5, 0.5, 0.20, 0.10, SEG, PHASE, (0.30, 1.0), up=True)
    add_cap(n, 0.5, 0.5, 0.35, 0.40, SEG, PHASE, (0.30, 0.0), up=False)
    print("   [selftest] inward frustum    vol %+.6f  (must be < 0)" % n.volume())

    # ③ 方柱（三足 / 双耳走的就是这条）
    b = Mesh()
    with b.primitive("selftest_tbox"):
        add_tbox(b, 0.40, 0.40, 0.60, 0.60, 0.42, 0.42, 0.58, 0.58, 0.0, 0.2,
                 (0.0, 0.0625), v_span=(1.0, 0.90), uv_top=(0.02, 0.92), uv_bot=(0.02, 1.0))
    print("   [selftest] tapered box       vol %+.6f  (must be > 0)" % b.volume())

    # ④ 扉棱走的六面体路径（六条腿的坐标不同，绕向必须同源）
    f = Mesh()
    with f.primitive("selftest_flange"):
        add_flange(f, PHASE)
    print("   [selftest] flange            vol %+.6f  (must be > 0)" % f.volume())

    # ⑤ 星钉（八面体）
    s = Mesh()
    with s.primitive("selftest_spark"):
        add_spark(s, "spark", 0.5, 0.5, 0.5, 0.05)
    print("   [selftest] star stud         vol %+.6f  (must be > 0)" % s.volume())

    assert m.volume() > 1e-9, "外向棱台绕序不对"
    assert n.volume() < -1e-9, "朝轴心的棱台绕序不对"
    assert b.volume() > 1e-9, "收分方柱绕序不对"
    assert f.volume() > 1e-9, "扉棱绕序不对"
    assert s.volume() > 1e-9, "星钉绕序不对"


# ---------------------------------------------------------------- 贴图 ----

def _mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def _clamp(c):
    return tuple(max(0, min(255, v)) for v in c)


def _shift(c, d):
    return _clamp(tuple(v + d for v in c))


# 黑金 / 鎏金
GOLD_HI = (244, 214, 140)
GOLD_DK = (220, 178, 96)
GOLD = (238, 204, 116)          # 激活时鎏金的提亮基准
BODY_TOP = (74, 63, 55)         # 腹上（受光）——暖调的黑金
BODY_BOT = (38, 33, 32)         # 腹下（背光）
# 碗内壁：口内一道鎏金弦纹，下面全是暗铜
COPPER_HI = (198, 148, 76)
COPPER_DK = (162, 114, 56)
COPPER_DEEP = (86, 66, 44)
COPPER_BOT = (42, 32, 24)
# 星辰
STAR = (252, 246, 224)

GOLD_ROWS = 8                   # 腹：前 8 行是口沿鎏金箍
INNER_GOLD_ROWS = 10            # 内壁：前 10 行是口内鎏金弦纹带


def _body_row(y):
    """左段：鼎身一行色（y=0 口沿 → y=31 鼎底）。

    ★ 只保留**两段**：rows 0..7 = 口沿那道鎏金箍，rows 8..31 = 黑金腹。
    两段内部都是极缓的渐变；上一版在腹中间又插了两道金箍，
    一条鼎腹被切成四五道横纹，进游戏就是一环一环的「一层一层」。
    """
    if y < GOLD_ROWS:
        return _mix(GOLD_HI, GOLD_DK, y / float(GOLD_ROWS - 1))
    return _mix(BODY_TOP, BODY_BOT, (y - GOLD_ROWS) / float(ROWS - 1 - GOLD_ROWS))


def _inner_row(y):
    """右段：碗内壁一行色（y=0 碗口 → y=31 碗底）。

    rows 0..9 = 口内那圈鎏金弦纹带，rows 10..31 = 暗铜碗身。
    两段之间的硬边就是那道「弦纹」本身 —— 故意的。
    """
    if y < INNER_GOLD_ROWS:
        return _mix(COPPER_HI, COPPER_DK, y / float(INNER_GOLD_ROWS - 1))
    return _mix(COPPER_DEEP, COPPER_BOT, (y - INNER_GOLD_ROWS) / float(ROWS - 1 - INNER_GOLD_ROWS))


def _core_row(y):
    """中段：星辰鼎心**待机**一行色（y=0 晶尖 → y=31 晶根）——昏暝的窄紫。"""
    return _mix((148, 136, 178), (78, 56, 116), y / float(ROWS - 1))


def _core_active(y):
    """中段：星辰鼎心**全盛**一行色——尖上白金星焰，根上紫金。"""
    t = y / float(ROWS - 1)
    glow = _mix(STAR, GOLD, min(1.0, t * 1.30))
    return _mix(_core_row(y), glow, 0.80)


# 鼎心星辰的像素坐标（列已在 CORE_U 区间内：16..23）
CORE_STARS = [(17, 12), (20, 8), (22, 18), (18, 24), (21, 28), (16, 20)]


def _draw(px, level):
    """level 0 = 待机昏暝，1 = 全盛。三段各画满整张贴图，不留黑块。"""
    # ---- 左段：八棱鼎身（8 面 × 2 px）----
    for k in range(SEG):
        tone = BODY_TONE[k]
        for y in range(ROWS):
            c = _body_row(y)
            if y < GOLD_ROWS and level > 0:
                c = _mix(c, GOLD, 0.35 * level)      # 鎏金箍随激活微亮
            c = _shift(c, tone)
            px[k * 2, y] = c + (255,)
            px[k * 2 + 1, y] = c + (255,)
    # ---- 中段：星辰鼎心（8 面 × 1 px）----
    core_x0 = int(round(CORE_U[0] * TEX))
    for k in range(SEG):
        x = core_x0 + k
        tone = CORE_TONE[k]
        for y in range(ROWS):
            c = _core_row(y) if level <= 0 else _mix(_core_row(y), _core_active(y), 0.85 * level)
            px[x, y] = _shift(c, tone) + (255,)
    # 鼎心里的星辰随激活缓慢上浮（循环），读作「星在转」
    off = -int(round(level * 6)) % ROWS
    for (sx, sy) in CORE_STARS:
        yy = (sy + off) % ROWS
        r, g, b = px[sx, yy][:3]
        px[sx, yy] = _mix((r, g, b), STAR, 0.30 + 0.55 * level) + (255,)
    # ---- 右段：碗内壁（8 面 × 1 px）----
    inner_x0 = int(round(INNER_U[0] * TEX))
    for k in range(SEG):
        x = inner_x0 + k
        tone = INNER_TONE[k]
        for y in range(ROWS):
            c = _inner_row(y)
            if y < INNER_GOLD_ROWS and level > 0:
                c = _mix(c, GOLD, 0.30 * level)      # 内壁鎏金带也随激活发亮
            px[x, y] = _shift(c, tone) + (255,)


def build_textures():
    os.makedirs(BLOCK_TEX_DIR, exist_ok=True)

    idle = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 255))
    assert SPARK_UV[0] * TEX >= 1.0, "星钉取的像素不能落在第一列（那里是腹面的起点）"
    _draw(idle.load(), 0.0)
    idle_path = os.path.join(BLOCK_TEX_DIR, TEX_NAME + ".png")
    idle.save(idle_path)
    print("block tex ->", os.path.relpath(idle_path, ROOT), idle.size)

    strip = Image.new("RGBA", (TEX, TEX * 4), (0, 0, 0, 255))
    for i, level in enumerate([0.45, 1.0, 0.72, 0.45]):
        frame = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 255))
        _draw(frame.load(), level)
        strip.paste(frame, (0, i * TEX))
    active_path = os.path.join(BLOCK_TEX_DIR, TEX_NAME + "_active.png")
    # 第一百二十六批：动画条 32×128 → 16×64（四帧仍是 16×16），方块面本来就是 16 格，缩一半看不出来
    strip.resize((TEX // 2, TEX * 2), Image.LANCZOS).convert("RGB") \
        .quantize(colors=128, method=Image.MEDIANCUT, dither=Image.NONE) \
        .save(active_path, optimize=True)
    with open(active_path + ".mcmeta", "w", encoding="utf-8") as f:
        f.write('{\n  "animation": {\n    "frametime": 4\n  }\n}\n')
    print("active tex ->", os.path.relpath(active_path, ROOT), strip.size)


def main():
    selftest()
    # ★ 第一百二十六批的教训：默认<b>只出贴图</b>。
    #   build_obj() 会重写 models/xuanshu_charger.obj —— 生成器那份是 460 顶点的完整网格（58 KB），
    #   而项目里用的是减面后的 268 顶点版本（22.7 KB）。以前重跑一次就把模型换掉、体积反涨 36 KB。
    #   真要重建模型：python gen_xuanshu_charger.py --with-model
    if "--with-model" in sys.argv:
        build_obj()
    build_textures()


if __name__ == "__main__":
    main()
