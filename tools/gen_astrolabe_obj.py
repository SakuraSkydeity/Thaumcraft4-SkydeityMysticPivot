#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
星象仪（astrolabe）· 模型与贴图（第一百八十六批新增；第一百八十七批改形制 + 改物品模型）。

<h2>第一百八十九批解决什么</h2>

用户截图上框住的是<b>「鎏金底盘以下」那一整段</b>（压沿 + 喇叭口 + 细柱 + 薄底盘），口径
「框起来的部分不要，然后就一个方块的大小」。于是：

  1. **从 `DAIS`（银白八角主台）往下全部砍掉** —— 主台成了整座仪器的新底面，
     球 + 托座 + 主台自成一个「球坐在八角台上」的整体，不再有柱子；
  2. **整座缩进一格**：保留部分原本 17.35 → 53.50 = 36.15px 高，整体乘
     `SCALE = (16 - 0.04) / 36.15 ≈ 0.4415`，缩完落在 y ∈ [0.02, 15.98]px，
     正好一格（上下各留 0.02px 防共面）。
     缩放放在<b>写顶点那一步</b>（{@link fit}），UV 仍按原来的像素坐标算，
     所以贴图一点不用重画，花纹密度也不会跟着缩水；
  3. 缩到一格之后**物品模型与方块模型完全同一份**（几何已经居中在格心），
     `astrolabe_item.obj` 那份平移版随之作废、删掉。

<h2>第一百八十七批解决什么</h2>

用户原话：「星象仪的物品贴图改为 obj 的模型，然后最下面那层底座不需要，只需要很细就可以」。

于是两件事：

  1. **底下那层须弥座整个去掉**，换「一片薄底盘 + 一根细柱」；
     上面的台座也一整套收细（边心距大多砍到原来的一半上下），球与相轮刹仍然不动，
     于是球（r = 12.6）比托它的柱子宽出十来像素，整座读作「一颗球悬在细针上」。
  2. **物品不再用 16×16 手绘图**，改用 `neoforge:obj` 直接吃模型；
     因为方块模型 y=0 是底面、总高 3.34 格，物品的旋转中心却在格心，
     所以另存一份 `astrolabe_item.obj`（几何整体下移到格心），display 里一点平移都不用写。

<h2>形制（像素坐标，1 格 = 16px；括号里是缩放后的实际位置）</h2>

<p>下表是<b>建模坐标</b>（未缩放的「设计稿」）；真正写进 OBJ 的是
{@link fit} 换算后的数值 —— 整段减去 `CUT` 再乘 0.4415，于是 y 落在 0…16px 之间。</p>

<pre>
  y 53.50 ─ 相轮刹顶（金针收口）
  y 52.20 ─ 相轮盘（边心距 1.5）
  y 51.60 ─ 球顶
  y 39.00 ─ 球心（r = 12.6）
  y 26.40 ─ 球底（与发光台留 1.75px 缝，读作「悬浮」）
  y 24.65 ─ 发光台顶面（自发光错觉）
  y 23.85 ─ 发光小台（边心距 1.55 → 1.40）
  y 23.30 ─ 托座上箍
  y 19.70 ─ 收分托座（莲瓣柱身，2.80 → 1.80）
  y 19.10 ─ 托座下箍
  y 17.35 ─ 银白八角主台（3.60 → 3.90，顶面刻徽记）—— 第一百八十九批的新底面
  ══════ 以下（压沿 / 鎏金底盘 / 喇叭口 / 细柱 / 薄底盘）第一百八十九批整段删除 ══════
  y 17.00 ─ 鎏金底盘压沿（3.40 → 3.20）
  y 16.00 ─ 鎏金底盘（3.40）
  y 13.20 ─ 小喇叭口顶（2.10 → 2.60）
  y  0.40 ─ 细柱底（2.20 → 1.90 微收分）
  y  0.02 ─ 薄底盘（暗石，八角 4.30，上下都封）
</pre>

<p>缩放后：球 r = 5.38px（直径 10.75px，占一格 67%）、主台边心距 1.66px、
总高正好 16px。于是「一颗球坐在一只小八角台上，整座收在一格里」——
方块不再往头顶上长出三格。</p>

<h2>产物</h2>
  * `models/astrolabe.obj` + `astrolabe.mtl`
    + `textures/block/astrolabe_stone.png`（256×256 图集）；
  * `models/block/astrolabe.json`（`neoforge:obj` loader，方块用）；
  * `models/item/astrolabe.json`（同样是 `neoforge:obj`，第一百八十九批起<b>直接读方块那份 obj</b>
    —— 几何本来就居中在格心、高度正好一格，不再需要 `astrolabe_item.obj`）；
  * `blockstates/astrolabe.json`（无属性方块也要包在 `variants` 里，键是空串）；
  * 顺手重建另外两个 blockstate：
    - `xuanshu_void_mining.json`：第一百八十七批多了 `facing`，四个朝向 × 两种激活态共 8 条，
      全指向同一个立方体模型（方块四面同贴图，转向不影响外观）；
    - `xuanshu_spirit_core.json`：无属性，一条 `""` 变体。

用法：
    python tools/gen_astrolabe_obj.py
    python tools/preview_obj_iso.py tools/preview/astrolabe_iso.png 26
"""
import math
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot")
MODEL_DIR = os.path.join(ASSETS, "models")
BLOCK_MODEL_DIR = os.path.join(MODEL_DIR, "block")
BLOCKSTATE_DIR = os.path.join(ASSETS, "blockstates")
TEX_DIR = os.path.join(ASSETS, "textures/block")

PX = 16.0             # 一格 = 16 像素
TEX = 256             # 图集边长

NAME = "astrolabe"

# 图集分区：名字 -> (左列, 顶行, 边长)
# 第一百八十九批删掉 s_foot / s_body / s_waist / b_body / b_foot —— 那五张只服务
# 已经被砍掉的「底盘 + 细柱 + 喇叭口」，留着就是没人取样的空区。
TILES = {
    "orb":      (0,   0,   96),   # 球面（经纬展开）
    "medal":    (96,  0,   48),   # 主台顶面徽记
    "cap":      (144, 0,   48),   # 平面鎏金
    "steel":    (192, 0,   48),   # 平面浅银
    "belt":     (0,   96,  48),   # 球面鎏金（赤道带 / 极帽，均匀）
    "core":     (48,  96,  48),   # 发光面
    "s_dais":   (96,  96,  8),    # 主台侧面
    "s_ped":    (104, 96,  8),    # 托座侧面（莲瓣柱身）
    "s_gold":   (112, 96,  8),    # 鎏金箍侧面
    "s_dark":   (120, 96,  8),    # 暗银侧面
}

C = 24.0              # 模型中心（像素）
ORIGIN = 16.0         # 收进方块坐标：x/z 减 16 → 8.0 才是格心


# ----------------------------------------------------------------------
# 颜色
# ----------------------------------------------------------------------
WHITE = (233, 237, 245)
WHITE_HI = (252, 253, 255)
WHITE_D = (208, 215, 227)

SILVER = (196, 203, 214)
SILVER_HI = (230, 235, 242)
SILVER_D = (158, 167, 181)
SILVER_DD = (112, 121, 136)

GOLD = (226, 182, 94)
GOLD_HI = (252, 228, 154)
GOLD_D = (176, 131, 52)
GOLD_DD = (120, 86, 30)

CORE_HOT = (255, 252, 236)
CORE_LOW = (230, 188, 112)

INK = (58, 62, 74)


def h2(a, b, salt=0):
    """确定性抖动（-0.5..0.5）：同一份脚本每次生成完全一样的贴图。"""
    v = (int(a) * 73856093) ^ (int(b) * 19349663) ^ (int(salt) * 83492791)
    return ((v >> 4) % 1000) / 999.0 - 0.5


def mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def clamp01(x):
    return max(0.0, min(1.0, x))


# ----------------------------------------------------------------------
# OBJ 累加器
# ----------------------------------------------------------------------
def sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0])


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def uv(region, lu, lv):
    """分区内坐标（纹素，lv 向上）-> OBJ 的 (u, v)。"""
    rx, ry, rs = TILES[region]
    lu = min(rs - 0.001, max(0.0, lu))
    lv = min(rs - 0.001, max(0.0, lv))
    return ((rx + lu) / TEX, 1.0 - (ry + rs - lv) / TEX)


def planar(p, apothem, region):
    """顶 / 底面的平面 UV：把 apothem 见方的包围盒铺满整个分区。"""
    rx, ry, rs = TILES[region]
    a = 2.0 * apothem
    return ((p[0] - (C - apothem)) / a * rs,
            (p[2] - (C - apothem)) / a * rs)


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
        """一个面。绕序反了按几何自动纠正（球面手算绕序必错）。"""
        n = cross(sub(pts[1], pts[0]), sub(pts[2], pts[0]))
        ln = math.sqrt(dot(n, n))
        if ln < 1e-9:
            raise AssertionError("%s: 退化面" % self.name)
        n = (n[0] / ln, n[1] / ln, n[2] / ln)
        pts = list(pts)
        uvs = list(uvs)
        if expect is not None and dot(n, expect) < 0.0:
            pts.reverse()
            uvs.reverse()
            n = (-n[0], -n[1], -n[2])
            self.flipped += 1
        if len(pts) == 3:                      # 三角面补退化四边形
            pts.append(pts[2])
            uvs.append(uvs[2])
        vi, ti = [], []
        for (p, t) in zip(pts, uvs):
            # 顶点走 fit()（缩到一格），UV 用设计稿坐标 —— 两边各管各的
            q = fit(p)
            self.v.append(((q[0] - ORIGIN) / PX, q[1] / PX, (q[2] - ORIGIN) / PX))
            vi.append(len(self.v))
            self.vt.append(uv(region, t[0], t[1]))
            ti.append(len(self.vt))
        self.vn.append((round(n[0], 5), round(n[1], 5), round(n[2], 5)))
        ni = len(self.vn)
        self.f.append([(vi[k], ti[k], ni) for k in range(len(pts))])

    def prism(self, y0, y1, a0, a1, side, top=None, bottom=None, sides=8, rot=22.5):
        """八棱柱（可收分）。a = 边心距（中心到平边），等于「半宽」。"""
        n = sides
        k8 = math.cos(math.pi / n)
        R0, R1 = a0 / k8, a1 / k8
        base = math.radians(rot)
        P0, P1 = [], []
        for k in range(n):
            t = base + 2.0 * math.pi * k / n
            P0.append((C + R0 * math.cos(t), y0, C + R0 * math.sin(t)))
            P1.append((C + R1 * math.cos(t), y1, C + R1 * math.sin(t)))
        s = TILES[side][2]
        for k in range(n):
            j = (k + 1) % n
            mid = base + 2.0 * math.pi * (k + 0.5) / n
            self.face([P0[k], P1[k], P1[j], P0[j]], side,
                      [(0.0, 0.0), (0.0, s), (s, s), (s, 0.0)],
                      expect=(math.cos(mid), 0.0, math.sin(mid)))
        if top:
            c = (C, y1, C)
            for k in range(n):
                j = (k + 1) % n
                self.face([c, P1[j], P1[k]], top,
                          [planar(c, a1, top), planar(P1[j], a1, top),
                           planar(P1[k], a1, top)],
                          expect=(0.0, 1.0, 0.0))
        if bottom:
            c = (C, y0, C)
            for k in range(n):
                j = (k + 1) % n
                self.face([c, P0[k], P0[j]], bottom,
                          [planar(c, a0, bottom), planar(P0[k], a0, bottom),
                           planar(P0[j], a0, bottom)],
                          expect=(0.0, -1.0, 0.0))

    def band(self, y0, y1, a_out, a_in, side, top=None, bottom=None, sides=8, rot=22.5):
        """八角环带：外沿减内圈，顶/底是梯形带（不是整片）。"""
        n = sides
        k8 = math.cos(math.pi / n)
        base = math.radians(rot)
        O0, O1, I0, I1 = [], [], [], []
        for k in range(n):
            t = base + 2.0 * math.pi * k / n
            co, si = math.cos(t), math.sin(t)
            O0.append((C + a_out / k8 * co, y0, C + a_out / k8 * si))
            O1.append((C + a_out / k8 * co, y1, C + a_out / k8 * si))
            I0.append((C + a_in / k8 * co, y0, C + a_in / k8 * si))
            I1.append((C + a_in / k8 * co, y1, C + a_in / k8 * si))
        s = TILES[side][2]
        for k in range(n):
            j = (k + 1) % n
            mid = base + 2.0 * math.pi * (k + 0.5) / n
            self.face([O0[k], O1[k], O1[j], O0[j]], side,
                      [(0.0, 0.0), (0.0, s), (s, s), (s, 0.0)],
                      expect=(math.cos(mid), 0.0, math.sin(mid)))
        if top:
            for k in range(n):
                j = (k + 1) % n
                self.face([O1[k], I1[k], I1[j], O1[j]], top,
                          [planar(O1[k], a_out, top), planar(I1[k], a_out, top),
                           planar(I1[j], a_out, top), planar(O1[j], a_out, top)],
                          expect=(0.0, 1.0, 0.0))
        if bottom:
            for k in range(n):
                j = (k + 1) % n
                self.face([O0[k], I0[k], I0[j], O0[j]], bottom,
                          [planar(O0[k], a_out, bottom), planar(I0[k], a_out, bottom),
                           planar(I0[j], a_out, bottom), planar(O0[j], a_out, bottom)],
                          expect=(0.0, -1.0, 0.0))

    def sphere(self, cy, r, seg, lats, band_of):
        """经纬球：`band_of(k)` 给出第 k 段用哪个分区。"""
        rows = []
        for lat in lats:
            rad = math.radians(lat)
            y = cy + r * math.sin(rad)
            rr = r * math.cos(rad)
            if rr < 1e-6:
                rows.append([(C, y, C)] * seg)
            else:
                rows.append([(C + rr * math.cos(2.0 * math.pi * i / seg), y,
                              C + rr * math.sin(2.0 * math.pi * i / seg))
                             for i in range(seg)])
        hub = (C, cy, C)

        def pt(region, u01, v01):
            s = TILES[region][2]
            return (u01 * s, v01 * s) if region == "orb" else (s * 0.5, s * 0.5)

        for k in range(len(lats) - 1):
            region = band_of(k)
            v0 = (lats[k] + 90.0) / 180.0
            v1 = (lats[k + 1] + 90.0) / 180.0
            lower, upper = rows[k], rows[k + 1]
            south = abs(lats[k] + 90.0) < 1e-6
            north = abs(lats[k + 1] - 90.0) < 1e-6
            for i in range(seg):
                j = (i + 1) % seg
                u0, u1 = i / seg, (i + 1) / seg
                if south:
                    pts = [lower[0], upper[j], upper[i]]
                    uvs = [pt(region, 0.5, v0), pt(region, u1, v1), pt(region, u0, v1)]
                elif north:
                    pts = [lower[i], lower[j], upper[0]]
                    uvs = [pt(region, u0, v0), pt(region, u1, v0), pt(region, 0.5, v1)]
                else:
                    pts = [lower[i], lower[j], upper[j], upper[i]]
                    uvs = [pt(region, u0, v0), pt(region, u1, v0),
                           pt(region, u1, v1), pt(region, u0, v1)]
                mx = sum(p[0] for p in pts) / len(pts)
                my = sum(p[1] for p in pts) / len(pts)
                mz = sum(p[2] for p in pts) / len(pts)
                self.face(pts, region, uvs,
                          expect=(mx - hub[0], my - hub[1], mz - hub[2]))

    def bounds(self):
        xs = [p[0] for p in self.v]
        ys = [p[1] for p in self.v]
        zs = [p[2] for p in self.v]
        return (min(xs), min(ys), min(zs)), (max(xs), max(ys), max(zs))

    def write(self, name=None):
        """写盘。`name` 换文件名（第一百八十九批起已经不需要另存物品版）。"""
        name = name or self.name
        lo, hi = self.bounds()
        lines = [
            "# SkydeityMysticPivot - %s (batch 189)" % name,
            "# 星象仪：银白八角主台 + 收分托座 + 悬浮白瓷球 + 相轮刹（整座缩在一格内，y = 0 即方块底面）",
            "# 球面 UV = 经纬展开（96x96 分区）；台座侧面 = 棱面归一化，顶面 = 平面归一化",
            "mtllib %s" % self.mtl,
            "usemtl %s" % self.material,
            "g %s" % name,
        ]
        for (x, y, z) in self.v:
            lines.append("v %.5f %.5f %.5f" % (x, y, z))
        for (u, v) in self.vt:
            lines.append("vt %.5f %.5f" % (u, v))
        for (x, y, z) in self.vn:
            lines.append("vn %.5f %.5f %.5f" % (x, y, z))
        for face in self.f:
            lines.append("f " + " ".join("%d/%d/%d" % t for t in face))
        path = os.path.join(MODEL_DIR, name + ".obj")
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            f.write("\n".join(lines) + "\n")
        print("  %-20s v=%-5d vt=%-5d vn=%-4d f=%-5d 绕序自动纠正 %d  "
              "包围盒 x[%.3f,%.3f] y[%.3f,%.3f] z[%.3f,%.3f]"
              % (name + ".obj", len(self.v), len(self.vt), len(self.vn),
                 len(self.f), self.flipped, lo[0], hi[0], lo[1], hi[1], lo[2], hi[2]))
        return len(self.f)


# ----------------------------------------------------------------------
# 形制尺寸（像素；y=0 = 方块底面）
#
# 第一百八十六批：底座整圈收窄（括号里是旧值），球与相轮刹不动。
# 第一百八十七批：**底下那层须弥座整个不要了**，换成「一片薄底盘 + 一根细柱」，
#                 上面那套台座也一并收细 —— 用户口径「最下面那层底座不需要，
#                 只需要很细就可以」。球（r=12.6）与相轮刹仍然不动，
#                 于是球比托它的柱子宽出十来像素，整个读作「悬」。
# 第一百八十八批：底盘再压薄 —— 用户口径「底下那一层不要那么厚」。
#                 0.87px（≈5.4cm）压到 0.38px（≈2.4cm），薄到像一枚垫片。
# 第一百八十九批：**底盘以下整段砍掉**（用户框起来的那一块），并且整座缩进一格 ——
#                 于是 FOOT / BODY / WAIST 三个尺寸从此只活在文档里，不再有几何。
#                 保留部分：DAIS 17.35 → FINIAL_PIN 53.50，共 36.15px，
#                 乘 SCALE 之后正好 16px = 1 格。
# ----------------------------------------------------------------------
DAIS = (17.35, 19.10, 3.60, 3.90)         # 银白主台（第一百八十九批起是整座的新底面）
COLLAR = (19.10, 19.70, 3.00, 2.80)       # 托座下箍（186 批 7.00→6.60）
PED = (19.70, 23.30, 2.80, 1.80)          # 收分托座（186 批 6.60→4.40）
PED_CAP = (23.30, 23.85, 1.80, 1.65)      # 托座上箍（186 批 4.40→4.00）
SEAT = (23.85, 24.65, 1.55, 1.40)         # 发光小台（186 批 3.60→3.20）

ORB_CY = 39.00                            # 球心
ORB_R = 12.60                             # 球半径：底 26.4、顶 51.6
FINIAL_DISC = (51.50, 52.20, 1.50)        # 相轮盘（骑在球顶）
FINIAL_PIN = (52.20, 53.50, 0.85, 0.60)   # 相轮针（收口）

# ---- 缩到一格（第一百八十九批）--------------------------------------
# 设计稿的最低点是保留部分的最底面（主台底），最高点是相轮针顶：
#   17.35 → 53.50 = 36.15px 高，目标 16px（一格），于是 k ≈ 0.4415。
# 缩放只动**顶点**，UV 仍然是按设计稿的像素坐标算的 —— 贴图不用重画，
# 花纹密度也不跟着缩小（缩小后一个花纹反而占更多屏幕像素，看着更清楚）。
CUT = DAIS[0]                             # 17.35：缩放的原点（主台底面，缩完落 y ≈ 0）
FIT_HEIGHT = 16.0                         # 目标总高 = 一格
# 上下各抬/留 0.02px：底面正好落在 y=0 会和下方方块的顶面共面，z-fight 闪成一片
# （第一百八十八批的 #73 号坑，压薄底盘时踩过一次）。相轮针顶同理留一丝缝。
LIFT = 0.02
SCALE = (FIT_HEIGHT - 2.0 * LIFT) / (FINIAL_PIN[1] - CUT)   # ≈ 0.44150

LATS = (-90.0, -72.0, -54.0, -36.0, -18.0, -8.0,
        8.0, 18.0, 36.0, 54.0, 72.0, 90.0)
SEG = 40
# 0 / 10 = 极帽、5 = 赤道带；其余是白瓷（花纹全在 orb 贴图里）
ORB_BANDS = {0: "belt", 5: "belt", 10: "belt"}


def fit(p):
    """设计稿像素坐标 → 「缩到一格」之后的像素坐标。

    x / z 绕模型轴（C）缩，y 减去 {@link CUT} 再缩、再抬 {@link LIFT} —— 于是保留部分
    落在 0.02 ~ 15.98px 之间（一格之内，上下各留一丝缝防共面），
    x / z 仍然关于格心 0.5 对称。
    """
    return (C + (p[0] - C) * SCALE,
            LIFT + (p[1] - CUT) * SCALE,
            C + (p[2] - C) * SCALE)


def build_astrolabe():
    o = Obj(NAME, NAME + ".mtl", NAME)

    # ---- 台座（从银白八角主台起，往上一整套；主台底面 = 整座的新底面）----
    # 主台成了整座的新底面，底必须封 —— 原来下面垫着鎏金底盘 / 压沿，看不出来；
    # 第一百八十九批把它们全砍掉之后，不封底就能从下方一眼看穿整座仪器（批次 #73 同源）。
    y0, y1, a0, a1 = DAIS
    o.prism(y0, y1, a0, a1, "s_dais", top="medal", bottom="cap")

    y0, y1, a0, a1 = COLLAR
    o.band(y0, y1, a0, a1, "s_gold", top="cap")

    y0, y1, a0, a1 = PED
    o.prism(y0, y1, a0, a1, "s_ped", top="steel")
    y0, y1, a0, a1 = PED_CAP
    o.band(y0, y1, a0, a1, "s_gold", top="cap")

    y0, y1, a0, a1 = SEAT
    o.prism(y0, y1, a0, a1, "s_dark", top="core")

    o.sphere(ORB_CY, ORB_R, SEG, LATS, lambda k: ORB_BANDS.get(k, "orb"))

    # 相轮刹：球顶先骑一只小金盘、再立一根收口针
    y0, y1, a = FINIAL_DISC
    o.prism(y0, y1, a, a, "s_gold", top="cap")
    y0, y1, a0, a1 = FINIAL_PIN
    o.prism(y0, y1, a0, a1, "s_gold", top="cap")
    return o


# ----------------------------------------------------------------------
# 图集绘制
# ----------------------------------------------------------------------
TRIGRAMS = {                     # 自下而上三位，1 = 阳（整条）、0 = 阴（中间断开）
    "qian": (1, 1, 1), "dui": (1, 1, 0), "li": (1, 0, 1), "zhen": (1, 0, 0),
    "xun": (0, 1, 1), "kan": (0, 1, 0), "gen": (0, 0, 1), "kun": (0, 0, 0),
}
ORDER = ("qian", "dui", "li", "zhen", "xun", "kan", "gen", "kun")


def g_orb(lu, lv):
    """球面白瓷（96×96 经纬展开）：八卦爻纹带 + 赤道金带 + 瓜瓣经缝。"""
    t = h2(lu // 4, lv // 4, 3)
    col = mix(WHITE, WHITE_HI, 0.16 + t * 0.14 + (lv / 96.0) * 0.05)

    for (b0, mirror) in ((21.3, True), (65.1, False)):
        if b0 <= lv < b0 + 10.0:
            panel = int(lu // 12) % 8
            tri = TRIGRAMS[ORDER[panel]]
            if mirror:
                tri = tuple(reversed(tri))
            lx = lu % 12
            for row in range(3):
                y = b0 + row * 3.9
                if y <= lv < y + 2.2 and 2.8 <= lx < 9.2:
                    yang = tri[row]
                    broken = (not yang) and (5.2 <= lx < 6.8)
                    if not broken:
                        edge = (lv < y + 0.5) or (lv >= y + 1.7)
                        col = GOLD_D if edge else GOLD
            break

    if 33.0 <= lv <= 63.0 and (lu % 12) < 1.0:
        on_belt = 43.7 <= lv <= 52.3
        col = GOLD_DD if on_belt else GOLD_D
    return col


def g_belt(lu, lv):
    """球面鎏金：赤道带与极帽，均匀（UV 固定取样中心）。"""
    t = h2(lu // 4, 0, 61)
    base = mix(GOLD, GOLD_HI, 0.10 + t * 0.10)
    return mix(base, GOLD_D, 0.14 * abs(lv / 48.0 - 0.5) * 2.0)


def _oct(dx, dy):
    """八边形归一化距离：0 = 中心，1.0 = 平边（图集分区半宽 = 24 纹素）。"""
    return max(abs(dx), abs(dy), abs(dx + dy) * 0.70711, abs(dx - dy) * 0.70711) / 24.0


def g_medal(lu, lv):
    """主台顶面徽记：连珠纹外圈 + 白瓷环 + 八枚金钉 + 鎏金内座。"""
    dx = lu + 0.5 - 24.0
    dy = lv + 0.5 - 24.0
    m = _oct(dx, dy)

    if m > 0.975:
        return INK
    if m > 0.900:
        k = (math.atan2(dy, dx) + math.pi) / (math.pi / 8.0)
        bead = (k - math.floor(k)) < 0.62
        if m > 0.945:
            return mix(GOLD, GOLD_HI, 0.45) if bead else GOLD_D
        return GOLD if bead else mix(GOLD, GOLD_D, 0.45)
    if m > 0.878:
        return GOLD_D
    if m < 0.760:
        return mix(GOLD_D, INK, 0.35)
    if m < 0.800:
        return mix(GOLD, GOLD_D, 0.45)
    if 0.822 <= m <= 0.868:
        ang = math.atan2(dy, dx)
        k = (ang + math.pi) / (math.pi / 4.0)
        if abs(k - round(k)) < 0.10:
            return mix(GOLD, GOLD_HI, 0.35)
    return mix(WHITE, WHITE_D, clamp01((m - 0.80) / 0.10) * 0.45)


def g_cap(lu, lv):
    """平面鎏金：抛光，只有贴边渐暗。"""
    dx = lu + 0.5 - 24.0
    dy = lv + 0.5 - 24.0
    m = _oct(dx, dy)
    t = 0.5 + h2(lu // 4, lv // 4, 31) * 0.4
    base = mix(GOLD, GOLD_HI, 0.08 + t * 0.14)
    return mix(base, GOLD_D, clamp01((m - 0.78) / 0.22) * 0.55)


def g_steel(lu, lv):
    """平面浅银：台阶 / 托座顶面。"""
    t = 0.5 + h2(lu // 4, lv // 4, 37) * 0.4
    return mix(SILVER_HI, SILVER, 0.30 + t * 0.16)


def g_core(lu, lv):
    """发光台顶面：只许均匀渐变，不许任何横线（第 59 条坑）。"""
    dx = lu + 0.5 - 24.0
    dy = lv + 0.5 - 24.0
    r = math.hypot(dx, dy)
    return mix(CORE_HOT, CORE_LOW, clamp01(r / 26.0))


def g_s_dais(lu, lv):
    """主台侧面：竖向拉丝 + 上缘亮金、下缘暗缝。"""
    if lv >= 7.2:
        return GOLD_HI
    if lv >= 6.6:
        return GOLD
    if lv <= 0.6:
        return SILVER_DD
    t = 0.5 + h2(lu, 0, 11) * 0.5
    return mix(SILVER, SILVER_HI, 0.25 + t * 0.35)


def g_s_ped(lu, lv):
    """托座侧面：莲瓣柱身 —— 中道鎏金筋 + 筋侧两道暗槽 + 棱线暗边。"""
    if 3.3 <= lu < 4.7:
        return GOLD_HI if lv >= 5.4 else GOLD
    if 2.5 <= lu < 3.3 or 4.7 <= lu < 5.5:
        return SILVER_DD
    if lu < 0.8 or lu >= 7.2:
        return SILVER_DD
    t = 0.5 + h2(0, lv, 13) * 0.5
    return mix(SILVER_D, SILVER_HI, 0.45 + t * 0.4)


def g_s_gold(lu, lv):
    """鎏金箍侧面：上亮下暗的抛光带。"""
    if lv >= 7.0:
        return GOLD_HI
    if lv <= 0.8:
        return GOLD_DD
    return mix(GOLD, GOLD_HI, 0.18 + (lv / 8.0) * 0.42)


def g_s_dark(lu, lv):
    """暗银侧面（台阶根 / 发光台）：只做明暗，不带花纹。"""
    return mix(SILVER_DD, INK, 0.25 + (1.0 - lv / 8.0) * 0.35)


PAINTERS = {
    "orb": g_orb, "medal": g_medal, "cap": g_cap, "steel": g_steel,
    "belt": g_belt, "core": g_core,
    "s_dais": g_s_dais, "s_ped": g_s_ped, "s_gold": g_s_gold,
    "s_dark": g_s_dark,
}

# 徽记是平面 45° 图形，1:1 取样会被纹素量化成台阶，3×3 超采样磨平
AA_TILES = {"medal", "cap", "core"}


def png_write(path, pixels):
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for (r, g, b, a) in row:
            raw.extend((r, g, b, a))

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    ihdr = struct.pack(">IIBBBBB", len(pixels[0]), len(pixels), 8, 6, 0, 0, 0)
    blob = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(blob)
    print("  %-24s %dx%d" % (os.path.basename(path), len(pixels[0]), len(pixels)))


def build_atlas():
    px = [[(18, 20, 26, 255) for _ in range(TEX)] for _ in range(TEX)]
    for name, (ox, oy, size) in TILES.items():
        fn = PAINTERS[name]
        aa = 3 if name in AA_TILES else 1
        grid = [(i + 0.5) / aa for i in range(aa)]
        for lv in range(size):
            row = px[oy + (size - 1 - lv)]
            for lu in range(size):
                if aa == 1:
                    c = fn(lu, lv)
                else:
                    r = g = b = 0
                    for sy in grid:
                        for sx in grid:
                            t = fn(lu + sx, lv + sy)
                            r += t[0]
                            g += t[1]
                            b += t[2]
                    n = aa * aa
                    c = (r // n, g // n, b // n)
                row[ox + lu] = (c[0], c[1], c[2], 255)
    png_write(os.path.join(TEX_DIR, "astrolabe_stone.png"), px)


# ----------------------------------------------------------------------
# 模型 JSON / MTL / blockstate
# ----------------------------------------------------------------------
# ----------------------------------------------------------------------
# 物品模型（第一百八十七批：直接吃 OBJ，不再画 16×16 图标）
#
# 用户口径「星象仪的物品贴图改为 obj 的模型」。于是 item 模型改用与方块同一套
# `neoforge:obj` loader（写法照抄 xuanshu_charger 那套），只额外补 display 变换，
# 免得在手上 / 地上太大。注意 `flip_v` 必须与方块模型一致，不然 UV 整个翻过来。
#
# 物品读的就是**方块那一份** `astrolabe.obj`（第一百八十九批起）：整座缩进一格之后，
# 几何本来就从 y=0 到 y=16px、x/z 也居中在格心，物品的旋转中心正是格心 ——
# 以前那份「整体下移到格心」的 `astrolabe_item.obj` 已经没有存在的理由，删掉。
# display 的 scale 也跟着变大（0.30 → 0.72）：模型从 3.34 格缩到 1 格，
# 同一个 scale 下手上会小掉三分之二，得按比例补回来。
# ----------------------------------------------------------------------
OBJ_MODEL_JSON = """{
  "loader": "neoforge:obj",
  "model": "skydeitymysticpivot:models/%(name)s.obj",
  "textures": {
    "particle": "skydeitymysticpivot:block/%(atlas)s"
  },
  "ambientocclusion": false,
  "automatic_culling": false,
  "shade_quads": true,
  "flip_v": true,
  "emissive_ambient": false,
  "render_type": "minecraft:solid"
}
"""

ITEM_MODEL_JSON = """{
  "loader": "neoforge:obj",
  "model": "skydeitymysticpivot:models/%(name)s.obj",
  "textures": {
    "particle": "skydeitymysticpivot:block/%(atlas)s"
  },
  "ambientocclusion": false,
  "automatic_culling": false,
  "shade_quads": true,
  "flip_v": true,
  "emissive_ambient": false,
  "render_type": "minecraft:solid",
  "gui_light": "side",
  "display": {
    "gui": {
      "rotation": [ 30, 225, 0 ],
      "translation": [ 0, 0, 0 ],
      "scale": [ 0.72, 0.72, 0.72 ]
    },
    "ground": {
      "rotation": [ 0, 0, 0 ],
      "translation": [ 0, 3, 0 ],
      "scale": [ 0.28, 0.28, 0.28 ]
    },
    "fixed": {
      "rotation": [ 0, 0, 0 ],
      "translation": [ 0, 0, 0 ],
      "scale": [ 0.55, 0.55, 0.55 ]
    },
    "thirdperson_righthand": {
      "rotation": [ 75, 45, 0 ],
      "translation": [ 0, 2.5, 0 ],
      "scale": [ 0.42, 0.42, 0.42 ]
    },
    "firstperson_righthand": {
      "rotation": [ 0, 45, 0 ],
      "translation": [ 0, 0, 0 ],
      "scale": [ 0.46, 0.46, 0.46 ]
    },
    "firstperson_lefthand": {
      "rotation": [ 0, 225, 0 ],
      "translation": [ 0, 0, 0 ],
      "scale": [ 0.46, 0.46, 0.46 ]
    }
  }
}
"""

MINING_CUBE_JSON = """{
  "parent": "minecraft:block/cube_bottom_top",
  "textures": {
    "top": "skydeitymysticpivot:block/xuanshu_void_mining_top",
    "bottom": "skydeitymysticpivot:block/xuanshu_void_mining_bottom",
    "side": "skydeitymysticpivot:block/xuanshu_void_mining_side"
  }
}
"""


def write_text(path, text):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print("  %s" % os.path.relpath(path, ROOT))


def build_wrappers():
    write_text(os.path.join(MODEL_DIR, NAME + ".mtl"),
               """# SkydeityMysticPivot - material for the astrolabe (batch 189)
# Kd 纯白，明暗全交给贴图；材质名必须与 OBJ 的 usemtl 一字不差
newmtl astrolabe
Kd 1.000000 1.000000 1.000000
map_Kd skydeitymysticpivot:block/astrolabe_stone
""")
    write_text(os.path.join(BLOCK_MODEL_DIR, NAME + ".json"),
               OBJ_MODEL_JSON % {"name": NAME, "atlas": NAME + "_stone"})
    # 物品：也吃 astrolabe.obj（第一百八十九批起整座就在一格内，无需另存）
    write_text(os.path.join(MODEL_DIR, "item", NAME + ".json"),
               ITEM_MODEL_JSON % {"name": NAME, "atlas": NAME + "_stone"})
    # 矿机未成型态的立方体壳子（贴图在 gen_void_mining.py，模型在这里）
    write_text(os.path.join(BLOCK_MODEL_DIR, "xuanshu_void_mining.json"), MINING_CUBE_JSON)


def _variants_json(mapping, indent="    "):
    """{"formed=false": {...}, ...} -> variants JSON 文本（键序固定，diff 干净）。"""
    lines = ["{"]
    lines.append('  "variants": {')
    items = list(mapping.items())
    for i, (state, model) in enumerate(items):
        lines.append('%s"%s": { "model": "%s" }%s'
                     % (indent, state, model, "," if i < len(items) - 1 else ""))
    lines.append("  }")
    lines.append("}")
    return "\n".join(lines) + "\n"


def build_blockstates():
    """三个 blockstate 都用 **variants 显式列全状态**（无属性方块键是空串）。

    第一百八十五批的教训：裸 `{"model": …}` 没有定义可加载，游戏里整块变缺失模型。
    """
    write_text(os.path.join(BLOCKSTATE_DIR, NAME + ".json"),
               _variants_json({"": "skydeitymysticpivot:block/" + NAME}))
    # 矿机：第一百八十七批多了 facing（结构长在背面）。
    #       方块是四面同贴图的立方体，所以四个朝向共用同一个模型，只改亮度（6 → 12）。
    mining = {}
    for facing in ("north", "east", "south", "west"):
        for formed in ("false", "true"):
            mining["facing=%s,formed=%s" % (facing, formed)] = \
                "skydeitymysticpivot:block/xuanshu_void_mining"
    write_text(os.path.join(BLOCKSTATE_DIR, "xuanshu_void_mining.json"),
               _variants_json(mining))
    # 沟灵核心：第一百八十六批退回无属性方块
    write_text(os.path.join(BLOCKSTATE_DIR, "xuanshu_spirit_core.json"),
               _variants_json({"": "skydeitymysticpivot:block/xuanshu_spirit_core"}))


if __name__ == "__main__":
    print("OBJ:")
    obj = build_astrolabe()
    nf = obj.write()
    lo, hi = obj.bounds()
    print("  缩放后：y %.3f → %.3f 格（应在一格之内），x/z %.3f → %.3f"
          % (lo[1], hi[1], lo[0], hi[0]))
    print("图集:")
    build_atlas()
    print("模型 / blockstate:")
    build_wrappers()
    build_blockstates()
    print("done, %d 面" % nf)
