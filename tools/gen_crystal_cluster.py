#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
玄枢水晶簇（xuanshu_crystal_cluster）—— 第七十五批改梭形，第七十六批放宽中腰，
第七十七批 **把中腰收回来、两头不再尖，轮廓改平滑**。

<h2>第七十九批（本批）：回退重生成，只把几枝弄歪一点</h2>
用户原话：「水晶该回去。你这样太规整了，将原来的几个歪一点斜一点就好。」

第七十八批用一份新生成器（tools/gen_crystal.py）把晶簇换成了「九环余弦收分 + 全高 12° 扭转 +
全竖直」，结果十枝都直挺挺地立着、剪影太规整。本批**回退到本文件第七十七批的梭形十枝**，
只做一件事：把其中几枝的倾角 `tilt_deg` 加大（19~27°），让晶簇里冒出几枝歪一点、斜一点的，
整体读起来更自然、不再那么整齐划一。其余（枝数、大小、半径、调色板贴图、平滑度）一律不动。

<h2>第七十七批（本批）：上一批鼓过头了</h2>
用户原话：「水晶中间太大了，不要像圆锥，整理平滑一点。」

第七十六批把中腰鼓到上环的 2.7~2.9 倍、底环收到中腰的 0.27~0.31 倍，
本意是「梭」，实际读出来却是**一根下粗上尖的圆锥**——因为上段从最宽处一路收到底，
尖又长（tip 0.34~0.50），整枝看着就是锥。

本批把三个比例一起往「匀」的方向拉：

| 参数 | 第七十六批 | 第七十七批 | 读出来的样子 |
| --- | --- | --- | --- |
| 中腰 / 上环 半径倍数 | 2.7 ~ 2.9 | **1.9 ~ 2.0** | 中腰只是「略鼓」，不再是鼓包 |
| 底环 / 中腰 半径比 | 0.27 ~ 0.31 | **0.46 ~ 0.49** | 柱脚不再收成尖，站得稳 |
| 上环半径（最粗那枝） | 0.086 | **0.128** | 上段加粗，不再一路细到尖 |
| 尖长比 `tip_ratio` | 0.34 ~ 0.50 | **0.32 ~ 0.42** | 尖短一档，顶端是「小尖」不是「长针」 |
| 中腰高度比 `waist_at` | 0.34 ~ 0.58 | **0.40 ~ 0.52** | 最宽处收到中段附近，各枝更齐 |

一句话：**上下两段的收放都变缓**，柱身接近「两头略收的六棱柱」，
只在离顶端三分之一处挑一个短尖 —— 像水晶，不像锥。

<h2>第七十六批：放宽中腰、放大收拢、减到十枝（已回退掉比例部分）</h2>
用户原话：「水晶模型在中间宽一点，整体大一点紧凑一点，数量可以少一点。」
那批把中腰倍数从 1.79 提到 2.9、枝数 14 → 10、整丛放大收拢（`POS_K` 0.74 → 0.66），
其中**枝数、整丛大小与收拢度这两条本批保留**（用户没再提意见），
只有「中腰有多鼓」这一条按本批回调。

<h2>第七十五批：形制从「等宽柱 + 尖头」改成梭形</h2>
第七十批那版是「等宽六棱柱 + 一个尖头」，十四枝并排站着像一捆六棱棒：**只有大小之分，
没有形制之分**。那批把每枝拆成四段 —— 底环（窄）→ 中腰（最宽）→ 上环（窄）→ 长锥尖。

底环半径收到中腰的 55%~62%（第七十六批进一步收到 **33%~40%**），于是每一枝都是两头收、
中间鼓，站在草丛里像一丛真的晶芽，而不是十几根切平的柱子。

<h2>其余一切照旧</h2>
贴图仍是 **16×16**、一行一套调色板（十六套，取自原版紫水晶的六档平色再混色相）、
每棱面 2 px（首列亮棱线）；整丛按 {@code POS_K} 0.66 收拢、{@code LEAN} 0.20 外倾、
**没有岩座**、**没有竖向渐变**。两把尺子也还在：每个图元断言「有向体积 > 0」
且「每个面背离形心」—— 柱身分成两段之后，这两条断言照样全过（下段外撑、上段内收，
两段的面都朝外）。

<h2>「正常一点」是照什么改的（搜到的参照）</h2>
把 **原版紫水晶（vanilla amethyst）** 的贴图从客户端 jar 里扒出来逐像素看过：
`amethyst_cluster.png` / `large_amethyst_bud.png` 用的是**同一套六档平色**，
相邻档只差 10~30 级，**完全没有竖向渐变**，靠「棱面各取一档 + 边缘一条更亮的棱线」
读出晶体感。本版就照这套：

<pre>
   L0 (93,58,154)   L1 (100,71,158)   L2 (122,91,181)
   L3 (141,106,204) L4 (166,120,241)  L5 (200,144,240)
   点缀 (254,203,230) 粉紫高光   (255,253,213) 白光点
</pre>

<h2>贴图 16×16 = 16 个调色格（每格一行）</h2>
去渐变之后，**行方向不再承载信息**（原来那 32 行是拿来做「尖白→根浓」渐变的），
于是干脆把 16 行当作 **16 套调色板**：第 s 行 = 第 s 号晶柱的配色。

<pre>
   行 s（= 晶柱 s 的调色板）
     u: 0   2   4   6   8  10  12 | 12..15
        ┌───┬───┬───┬───┬───┬───┐
        │0  │1  │2  │3  │4  │5  │  ← 每 2 px = 六棱柱的一个棱面
        └───┴───┴───┴───┴───┴───┘
          棱面的第 1 px = 亮棱线，第 2 px = 棱面本色
</pre>
16 行 × 16 列里，前 12 列是六个棱面（2 px 一个），后 4 列填该行的本色（备用、防溢出）。
六个棱面的档位取 **[L4 L3 L2 L1 L2 L3]**（对称的一圈明暗）+ 每面首列亮一档 ⇒
配上引擎自己的逐面光照，「六棱」依然一眼读得出来，但**不再是那道难看的长渐变**。

<h2>整丛更紧、更大</h2>
- 位置往中心收：{@code POS_K} 0.74 → <b>0.66</b>（最远的芽从 0.84 收到 0.78）；
- 三个半径不再靠 {@code R_K} 缩，而是在表里给足（{@code R_K} = 1.00）；
- 外倾量 {@code lean} 0.22 → <b>0.20</b>（收得更紧之后，外倾再大就会各奔东西）；
- 枝数 14 → <b>10</b>，高度整体抬高约 9% ⇒ 每枝更壮、站得更拢，整丛视觉上「又胖又高」。

仍是第六十七批那两把尺子：所有面**外向逆时针**，逐图元断言「有向体积 > 0」且「每个面背离形心」。

产出：models/crystal_cluster.obj + .mtl、textures/block/xuanshu_crystal_cluster.png（16×16）。
"""
import math
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_DIR = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/models")
BLOCK_TEX_DIR = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/block")
PREVIEW_DIR = os.path.join(ROOT, "tools/preview")

TEX_NAME = "xuanshu_crystal_cluster"
MTL_NAME = "crystal_cluster"
MTL_MTL = "crystal"

TEX = 16                      # 贴图边长（用户点名要 16×16）
FACET_PX = 2                  # 每个棱面占 2 px（首列亮棱线 + 末列本色）
SEG = 6                       # 六棱柱

# 原版紫水晶实测出来的六档平色（见文件头）
LADDER = [
    (93, 58, 154),
    (100, 71, 158),
    (122, 91, 181),
    (141, 106, 204),
    (166, 120, 241),
    (200, 144, 240),
]
ACCENT = (254, 203, 230)      # 粉紫高光（备用）
SPARKLE = (255, 253, 213)     # 白光点（备用）

# 六个棱面各取哪一档（对称的一圈明暗：第 0 面最受光、第 3 面最背光）
FACET_STEP = [4, 3, 2, 1, 2, 3]

# 16 套调色板的色相（在紫水晶那六档上混向这个色，0.6 的混比）
TINTS = [
    (122, 91, 181),    # 0  紫晶（本色，等于没混）
    (78, 88, 182),     # 1  蓝紫
    (158, 68, 172),    # 2  品红
    (56, 134, 156),    # 3  青蓝
    (172, 96, 152),    # 4  粉紫
    (100, 90, 186),    # 5  青莲
    (62, 100, 172),    # 6  星蓝
    (134, 108, 192),   # 7  浅紫
    (92, 60, 158),     # 8  深紫
    (68, 118, 186),    # 9  天青
    (166, 74, 158),    # 10 洋红
    (62, 142, 140),    # 11 薄荷
    (118, 84, 188),    # 12 紫罗兰
    (140, 120, 196),   # 13 星辉
    (86, 70, 164),     # 14
    (74, 128, 180),    # 15
]
TINT_MIX = 0.60

# 整丛：位置往中心收得比过去更狠（POS_K 0.74 → 0.66），枝围则在表里直接调粗
# （R_K 只作总放缩系数，本批取 1.00 = 表里的数字就是最终半径）——「更大」与「更紧凑」
# 这两件事一起做，结果就是每枝更壮、彼此更近，而不是简单地整体缩一圈。
POS_K = 0.66
R_K = 1.00
LEAN = 0.20


# ---------------------------------------------------------------- 网格 ----

class Mesh:
    """极简 OBJ 网格累积器；另记每个图元的面区间，便于逐图元校验。"""

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

    def _tri_raw(self, a, b, c, uva, uvb, uvc):
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
        self._tri_raw(a, b, c, uva, uvb, uvc)

    def quad(self, a, b, c, d, uva, uvb, uvc, uvd):
        self._tri_raw(a, b, c, uva, uvb, uvc)
        self._tri_raw(a, c, d, uva, uvc, uvd)

    def volume_between(self, f0, f1):
        vol = 0.0
        for face in self.faces[f0:f1]:
            ia, ib, ic = face[0], face[3], face[6]
            a, b, c = self.v[ia - 1], self.v[ib - 1], self.v[ic - 1]
            vol += (a[0] * (b[1] * c[2] - b[2] * c[1])
                    - a[1] * (b[0] * c[2] - b[2] * c[0])
                    + a[2] * (b[0] * c[1] - b[1] * c[0])) / 6.0
        return vol

    def primitive(self, name):
        return _Primitive(self, name)


class _Primitive:
    """with 语句包住一段图元构建，退出时两级校验：有向体积 + 逐面背离形心。"""

    def __init__(self, mesh, name):
        self.mesh = mesh
        self.name = name

    def __enter__(self):
        self.f0 = len(self.mesh.faces)
        self.v0 = len(self.mesh.v)
        return self

    def __exit__(self, *exc):
        if exc[0] is None:
            n = len(self.mesh.faces)
            vol = self.mesh.volume_between(self.f0, n)
            self.mesh.marks.append((self.name, n - self.f0, vol))
            if vol <= 1e-9:
                raise AssertionError("图元 %s 有向体积 %.6f ≤ 0：有面朝里（绕序反了）"
                                     % (self.name, vol))
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
                    raise AssertionError("图元 %s 第 %d 个面朝里（法线背离形心为负）"
                                         % (self.name, k))
        return False


def ring(cx, cz, r, y, seg, phase):
    return [(cx + math.cos(phase + 2.0 * math.pi * i / seg) * r, y,
             cz + math.sin(phase + 2.0 * math.pi * i / seg) * r)
            for i in range(seg)]


def add_crystal(mesh, name, cx, cz, r_bot, r_mid, r_top, y_top, waist_at, phase,
                tilt_deg, tilt_dir, slot, tip_ratio=0.44, lean=LEAN):
    """一枝六棱晶柱：**底窄 → 中腰最宽 → 上窄 → 长锥尖**（第七十五批改的形制）。

    第七十批那版是「等宽柱身 + 一个尖头」，十四枝站在一起像一捆六棱棒，形制太单一。
    用户要求「模型再多样一点，可以中间宽然后两头尖锐」，于是把柱身拆成两段：

    <pre>
                                                 ▲  apex
                                                ╱ ╲          ← 尖（长 = 柱高 × tip_ratio）
                                               ╱   ╲
                                          ┌───┴─────┴───┐     ← 上环（y_top，窄）
                                          │             │
                                          │     中腰     │     ← 中腰环（y = y_top × waist_at，最宽）
                                          │             │
                                          └─────┬─────┘      ← 底环（y = 0，也窄）
    </pre>

    每枝的三个比例各不同 —— {@code waist_at}（最宽处在哪）、{@code r_mid}（鼓多少）、
    {@code tip_ratio}（尖多长）—— 但第七十七批把三个比例的**幅度都收窄**了
    （1.9~2.0 倍 / 0.46~0.49 / 0.32~0.42），所以十枝之间仍有粗有细、有高有矮，
    却不会再一枝是梭、一枝是锥。{@code slot} 决定取贴图哪一行调色板；
    整枝只有一个 v（去渐变之后长度方向不该再变色）。
    """
    y_bot = 0.0
    y_waist = y_top * waist_at
    row_v = (slot + 0.5) / float(TEX)
    tilt = math.radians(tilt_deg)
    total_h = max(1e-6, y_top - y_bot)
    apex_y = y_top + total_h * tip_ratio

    dx = math.cos(tilt_dir) * math.sin(tilt) * lean
    dz = math.sin(tilt_dir) * math.sin(tilt) * lean

    bot = ring(cx, cz, r_bot, y_bot, SEG, phase)
    mid = [(x + dx * waist_at, y, z + dz * waist_at)
           for (x, y, z) in ring(cx, cz, r_mid, y_waist, SEG, phase)]
    top = [(x + dx, y, z + dz) for (x, y, z) in ring(cx, cz, r_top, y_top, SEG, phase)]
    apex = (cx + dx * (1.0 + tip_ratio), apex_y, cz + dz * (1.0 + tip_ratio))

    def uv_i(i):
        """棱面 i 的起始 u（该面占 2 px：首列亮棱线、末列本色）。"""
        return (i * FACET_PX) / float(TEX), row_v

    with mesh.primitive("%s(slot%d)" % (name, slot)):
        # 柱身两段：底环→中腰（往外撑）、中腰→上环（往回收）
        for lower, upper in ((bot, mid), (mid, top)):
            for i in range(SEG):
                j = (i + 1) % SEG
                mesh.quad(mesh.vertex(lower[j]), mesh.vertex(lower[i]),
                          mesh.vertex(upper[i]), mesh.vertex(upper[j]),
                          uv_i(j), uv_i(i), uv_i(i), uv_i(j))
        for i in range(SEG):
            j = (i + 1) % SEG
            mesh.tri(mesh.vertex(top[j]), mesh.vertex(top[i]), mesh.vertex(apex),
                     uv_i(j), uv_i(i), uv_i(i))
        # 底面（贴地，仍封上，免得 cutout 下看出空洞）
        c_bot = mesh.vertex((cx, y_bot, cz))
        for i in range(SEG):
            j = (i + 1) % SEG
            mesh.tri(mesh.vertex(bot[i]), mesh.vertex(bot[j]), c_bot,
                     uv_i(i), uv_i(j), uv_i(i))


# 十枝晶柱，大小分五档（巨 / 高 / 中 / 小 / 芽）。第七十七批把三围调匀：
#   中腰 = 上环 ×1.9~2.0，底环 = 中腰 ×0.46~0.49，尖长比 0.32~0.42，中腰高度比 0.40~0.52
#   —— 上下两段收放都变缓，是「两头略收的六棱柱 + 短尖」，不是锥。
# 第七十九批：只把几枝的倾角°加大（tall 13→19、mid_b 17→26、small_a 19→27、small_c 15→23、
#   bud_b 14→21），其余几枝保持原样，中央 huge 照旧竖直 —— 晶簇有几枝歪斜、整体不齐整。
# (名字, cx, cz, 底半径, 中腰半径, 上半径, 顶高, 中腰高度比, 相位, 倾角°, 倾方向 rad, 调色行, 尖长比)
CRYSTALS = [
    ("huge",    0.500, 0.500, 0.108, 0.205, 0.115, 0.655, 0.46, 0.00,  0.0, 0.00, 0, 0.30),
    ("tall",    0.706, 0.386, 0.100, 0.190, 0.107, 0.598, 0.42, 1.05, 19.0, 5.30, 1, 0.32),
    ("mid_a",   0.298, 0.612, 0.076, 0.145, 0.080, 0.455, 0.48, 0.52, 12.0, 2.15, 2, 0.32),
    ("mid_b",   0.694, 0.688, 0.070, 0.135, 0.074, 0.420, 0.42, 2.62, 26.0, 0.70, 3, 0.36),
    ("small_a", 0.330, 0.322, 0.055, 0.105, 0.058, 0.325, 0.50, 1.57, 27.0, 3.70, 4, 0.32),
    ("small_b", 0.548, 0.784, 0.050, 0.098, 0.054, 0.295, 0.44, 0.20, 12.0, 1.55, 5, 0.34),
    ("small_c", 0.828, 0.788, 0.043, 0.085, 0.046, 0.255, 0.50, 3.55, 23.0, 0.95, 6, 0.32),
    ("bud_a",   0.220, 0.470, 0.035, 0.070, 0.038, 0.195, 0.46, 2.95,  9.0, 3.05, 7, 0.34),
    ("bud_b",   0.774, 0.560, 0.032, 0.064, 0.034, 0.175, 0.44, 4.10, 21.0, 4.20, 8, 0.34),
    ("bud_c",   0.560, 0.238, 0.028, 0.057, 0.030, 0.150, 0.48, 0.85, 11.0, 5.65, 9, 0.32),
]


def compacted():
    """把布局往中心收：位置 ×POS_K；三个半径 ×R_K（本批 1.00，表里的数字就是最终值）。"""
    out = []
    for (name, cx, cz, rb, rm, rt, yt, waist, ph, tilt, tdir, slot, tip) in CRYSTALS:
        out.append((name,
                    0.5 + (cx - 0.5) * POS_K,
                    0.5 + (cz - 0.5) * POS_K,
                    rb * R_K, rm * R_K, rt * R_K,
                    yt, waist, ph, tilt, tdir, slot, tip))
    return out


def report_layout(rows):
    """打印两两柱基的净间距（负 = 柱基圆相交）与整丛（按中腰）外接半径。"""
    worst = None
    for a in range(len(rows)):
        for b in range(a + 1, len(rows)):
            gap = math.hypot(rows[a][1] - rows[b][1], rows[a][2] - rows[b][2]) - (rows[a][3] + rows[b][3])
            if worst is None or gap < worst[0]:
                worst = (gap, rows[a][0], rows[b][0])
    span = min(min(r[1] - r[3] for r in rows), min(r[2] - r[3] for r in rows))
    far = max(max(r[1] + r[4] for r in rows), max(r[2] + r[4] for r in rows))
    print("柱基最紧的一对: %s / %s  净间距 %+.4f" % (worst[1], worst[2], worst[0]))
    print("整丛柱基外接: %.3f ~ %.3f（按中腰算；收拢系数 %.2f）" % (span, far, POS_K))


def build_obj():
    rows = compacted()
    mesh = Mesh()
    for (name, cx, cz, rb, rm, rt, yt, waist, ph, tilt, tdir, slot, tip) in rows:
        add_crystal(mesh, name, cx, cz, rb, rm, rt, yt, waist, ph, tilt, tdir, slot, tip)

    os.makedirs(MODEL_DIR, exist_ok=True)
    obj_path = os.path.join(MODEL_DIR, MTL_NAME + ".obj")
    mtl_path = os.path.join(MODEL_DIR, MTL_NAME + ".mtl")
    with open(obj_path, "w", encoding="utf-8") as f:
        f.write("# SkydeityMysticPivot - xuanshu crystal cluster, %d prisms / flat 16x16 palette (batch 79)\n"
                % len(rows))
        f.write("# every prism: slightly tapering foot -> gently wider waist -> body -> short tip\n")
        f.write("# batch 77: waist pulled back in (x1.9~2.0 the top ring) so it reads as crystal, not a cone\n")
        f.write("# batch 79: a few prisms lean a little more crooked (tilt 19~27 deg) for a natural askew cluster\n")
        f.write("# no rock base; no vertical gradient; every face wound outward\n")
        f.write("mtllib %s.mtl\n" % MTL_NAME)
        f.write("usemtl %s\n" % MTL_MTL)
        f.write("g crystal\n")
        for (x, y, z) in mesh.v:
            f.write("v %.6f %.6f %.6f\n" % (x, y, z))
        for (u, v) in mesh.vt:
            f.write("vt %.6f %.6f\n" % (u, v))
        for (x, y, z) in mesh.vn:
            f.write("vn %.6f %.6f %.6f\n" % (x, y, z))
        for face in mesh.faces:
            f.write("f %d/%d/%d %d/%d/%d %d/%d/%d\n" % face)
    with open(mtl_path, "w", encoding="utf-8") as f:
        f.write("# SkydeityMysticPivot - material for the crystal cluster\n")
        f.write("newmtl %s\n" % MTL_MTL)
        f.write("Kd 1.000000 1.000000 1.000000\n")
        f.write("map_Kd skydeitymysticpivot:block/%s\n" % TEX_NAME)

    xs = [p[0] for p in mesh.v]
    ys = [p[1] for p in mesh.v]
    zs = [p[2] for p in mesh.v]
    out = [p for p in mesh.v if not (0.0 <= p[0] <= 1.0 and 0.0 <= p[1] <= 1.0 and 0.0 <= p[2] <= 1.0)]
    vol = mesh.volume_between(0, len(mesh.faces))
    print("obj ->", os.path.relpath(obj_path, ROOT),
          "| v", len(mesh.v), "vt", len(mesh.vt), "vn", len(mesh.vn), "f", len(mesh.faces))
    print("bounds: x %.3f~%.3f  y %.3f~%.3f  z %.3f~%.3f"
          % (min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)))
    print("signed volume: %+.6f  (must be > 0 = every face wound outward)" % vol)
    print("crystals: %d  palette rows used: %d" % (len(rows), len(set(r[11] for r in rows))))
    report_layout(rows)
    assert not out, "有顶点探出方块局部坐标 %s" % (out[:3],)
    assert vol > 1e-9, "整体有向体积非正：面朝向反了"
    assert max(ys) <= 1.0, "最高点 %.3f 超出方块" % max(ys)
    return obj_path


# ---------------------------------------------------------------- 贴图 ----

def _mix(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def palette_ladder(slot):
    """第 slot 号调色板：紫水晶那六档平色混向该行的色相。"""
    if slot == 0:
        return list(LADDER)                  # 0 号保持原版紫水晶本色，做基准
    tint = TINTS[slot]
    return [_mix(tone, tint, TINT_MIX) for tone in LADDER]


def build_block_texture():
    """16×16：一行一套调色板，行内前 12 列 = 六个棱面（各 2 px）。"""
    im = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 255))
    px = im.load()
    for slot in range(TEX):
        lad = palette_ladder(slot)
        for i in range(SEG):
            body = lad[FACET_STEP[i]]
            edge = lad[min(5, FACET_STEP[i] + 1)]
            px[i * FACET_PX, slot] = edge + (255,)
            px[i * FACET_PX + 1, slot] = body + (255,)
        # 后 4 列备用（填本色，万一将来 UV 溢出也不至于露出黑块）
        tail = lad[2]
        for x in range(SEG * FACET_PX, TEX):
            px[x, slot] = tail + (255,)

    path = os.path.join(BLOCK_TEX_DIR, TEX_NAME + ".png")
    im.save(path)
    print("block tex ->", os.path.relpath(path, ROOT), im.size)
    return path


def build_swatch():
    """16 行调色板横排成一张对照图。"""
    src = Image.open(os.path.join(BLOCK_TEX_DIR, TEX_NAME + ".png"))
    im = Image.new("RGBA", (TEX + 4, TEX + 4), (24, 22, 32, 255))
    im.paste(src, (2, 2))
    im.resize((TEX * 22, TEX * 22), Image.NEAREST).save(
        os.path.join(PREVIEW_DIR, "crystal_palette.png"))
    print("palette ->", os.path.relpath(os.path.join(PREVIEW_DIR, "crystal_palette.png"), ROOT))


def main():
    build_obj()
    build_block_texture()
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    Image.open(os.path.join(BLOCK_TEX_DIR, TEX_NAME + ".png")).resize(
        (TEX * 12, TEX * 12), Image.NEAREST).save(
        os.path.join(PREVIEW_DIR, "crystal_block_tex.png"))
    build_swatch()


if __name__ == "__main__":
    main()
