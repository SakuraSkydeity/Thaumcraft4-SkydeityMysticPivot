#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
枫树四件套贴图 —— 第一百六十七批新增，第一百六十九/一百七十批按用户反馈两次重做。

<h2>第 169 批用户原话</h2>
> 1. 枫叶原木贴图改画银树原木，整体深一点。树叶用樱花树叶改色 粉色就改为红色 绿色点缀改为橘色点缀
> 2. 原木分解的代码有问题，合成不了。树苗贴图用橡树树苗改，创造栏位子改为枫树数叶后面

<h2>第 170 批用户原话</h2>
> 2. 枫树原木贴图还是有问题，顶部透明的，你就用银树原木改颜色，然后枫树树叶在红一点，鲜红。
>    枫树木板纹理按原版橡木木板修改

<h2>第 171 批用户原话</h2>
> 1. 生成我还没看到 然后树的形状使用代码写的吗
> 2. 原木颜色往褐色一点，木板贴图干净一点

<h2>第 172 批用户原话</h2>
> 1. 枫树的形状我只需要最大的那款 其他的删去
> 2. 枫树原木和木板贴图重新设计：原木贴图风格基础不变的情况下学习橡木的颜色，
>    木板也一样，直接用橡木木板修改

<h2>第 173 批用户原话</h2>
> 1. 检查模组大小分布，哪些占用内存大
> 2. 树叶颜色再鲜红一点；原木的颜色再暗一点深一点 → 深褐色；
>    木板颜色整体深一点，光线纹路仿照橡木木板

<h2>第 174 批用户原话（原木配色）</h2>
> 3. 枫树原木配色有点不协调，因为是按照原来白色的调整的，你修改

<h2>现行口径（第 174 批终版）</h2>
* **原木侧面 / 顶面**：结构**改用原版橡木原木**（oak_log / oak_log_top 的真实字符矩阵，
  不再用银树结构）——第 172/173 批用「银树结构 + 橡木色」导致明暗层次还是银树式、
  与棕褐色不协调；这一批换成橡木自己的纹理走向，配色压成**深褐**（色相偏褐红不偏灰）。
* **木板**：结构仍用原版橡木木板，**整体深一点但光线纹路仿照橡木木板**——保留橡木
  7 档明暗层次（板缝 / 受光上缘 / 亮棱的对比关系不变），只把每档色值整体压暗。
* **树叶**：樱花树叶结构 + **再鲜红**（第 173 批把 G/B 压得更低、R 更纯，去掉粉感）。
* **树苗**：橡树树苗结构 + 红橘叶；茎跟着原木压成深褐。
* **树形**：树干固定最大高度（base_height=14 / 随机项归零，恒定 14 格）。

<h2>为什么参考图以「字符图章」内嵌，而不是读 jar</h2>
gen 脚本要能脱离参考料独立重跑（§7.10：源码树自包含）。参考贴图都是 16×16、
调色板有限，直接把字符矩阵与色表写进本文件，改色就是换一张色表的事。
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEXDIR = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/block")

SIZE = 16

# ---------------------------------------------------------------- 调色板
# 原木（第 172 批：结构学银树、颜色学橡木棕黄；第 173 批：用户要「再暗一点深一点」；
# 第 174 批误把结构换成橡木；第 176 批：用户明确「就是银树结构 + 橡木色」——
# 结构恢复银树原木，配色回到**橡木棕黄**。之所以之前「按白色调整的不协调」，
# 是因为银树是白灰（亮度 93~224）、亮档 e/f 占大面积，若配色压得太深（深褐最亮才
# lum 107），整块木头发闷发脏、明暗错位。所以配色对齐**橡木原木的亮度跨度**
# （最暗 ~45、最亮 ~124），色相取橡木棕黄（R>G>B，约 2:1.5:1），让银树细密纹理
# 配协调的棕黄明暗。）
SW_RAMP = [
    (56, 43, 24),       # a 节疤 / 最深缝（= 橡木最暗）
    (76, 61, 38),       # b 深缝（= 橡木）
    (95, 74, 43),       # c 暗柱棱（= 橡木）
    (116, 90, 54),      # d 柱身主色（= 橡木主色）
    (145, 113, 66),     # e 亮柱（= 橡木亮）
    (152, 120, 73),     # f 高光（= 橡木高光）
]

# 原木顶面（第 176 批：结构恢复银树顶面 9 档，配色取橡木棕黄亮度区间）。
SW_TOP_RAMP = [
    (56, 43, 24),       # a 深年轮缝
    (70, 55, 32),       # b
    (84, 66, 39),       # c
    (98, 76, 45),       # d
    (112, 86, 52),      # e 年轮主色
    (126, 98, 58),      # f 亮年轮
    (140, 110, 66),     # g
    (150, 118, 72),     # h
    (158, 126, 78),     # i 年轮高光
]

# 枫木木板（第 172 批用原版橡木木板结构；第 173 批：整体深一点，但光线纹路仍仿照
# 橡木木板——保留橡木 7 档的明暗层次/板缝/受光上缘对比，只把每档色值整体压暗）。
PLANK_RAMP = [
    (78, 60, 34),       # a 板缝 / 最深
    (96, 74, 42),       # b
    (114, 88, 50),      # c
    (122, 100, 60),     # d
    (134, 108, 64),     # e
    (142, 114, 72),     # f
    (150, 122, 78),     # g 最亮棱
]

# 枫叶（樱花结构改色，第 173 批：再鲜红——把 G/B 压得更低、R 更纯，去掉粉感）
# 第一百八十六批：整条 ramp 从「粉红」推到「鲜红」——
# 旧色最高一档是 (250,120,108)，G/B 都上了 100，16×16 缩到物品栏里就是一片粉；
# 新色把 G/B 全部压到 80 以下（R 保持 130~244），色相锁在正红偏一点点橘。
LEAF_RAMP = [
    (244, 80, 66),      # a 最浅红（受光边）
    (238, 62, 50),      # b
    (230, 48, 38),      # c
    (220, 36, 28),      # d
    (210, 26, 22),      # e
    (198, 19, 17),      # f
    (186, 14, 14),      # g
    (172, 11, 12),      # h
    (160, 9, 11),       # i
    (148, 8, 10),       # j
    (134, 7, 10),       # k 最深红（裂间阴影）
]
LEAF_ORN = [
    (222, 140, 48),     # l 橘点缀亮
    (198, 116, 36),     # m 橘点缀中
    (168, 90, 26),      # n 橘点缀暗
]

# 树苗（橡树结构改色）：茎跟原木一样压成深褐（第 173 批）+ 叶红橘四档
SAP_STEM = [
    (132, 102, 66),     # a 茎亮棱（深褐）
    (98, 74, 47),       # b 茎主色
    (80, 60, 38),       # c 茎暗
    (64, 47, 29),       # d 茎最深
]
# 第一百八十六批：树叶推鲜红，树苗的四档也跟着往红里靠（原来是纯橘，站在鲜红树冠下面像另一个树种）
SAP_LEAF = [
    (228, 82, 40),      # e 亮橘红（受光叶）
    (202, 62, 28),      # f 橘红
    (164, 40, 20),      # g 深红橘
    (126, 26, 14),      # h 最深红
]


def canvas():
    """全透明 16×16 画布（像素表，None = 透明）。"""
    return [[None for _ in range(SIZE)] for _ in range(SIZE)]


def px(gr, x, y, color):
    if 0 <= x < SIZE and 0 <= y < SIZE:
        gr[y][x] = color


def rect(gr, x0, y0, x1, y1, color):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px(gr, x, y, color)


def vline(gr, x, y0, y1, color):
    for y in range(y0, y1 + 1):
        px(gr, x, y, color)


def hline(gr, y, x0, x1, color):
    for x in range(x0, x1 + 1):
        px(gr, x, y, color)


def stamp(gr, rows, colors, ox=0, oy=0):
    """把字符图章盖到 (ox,oy)：rows 是字符串表，colors 是字符→RGB 表，'.' 透明。"""
    for j, row in enumerate(rows):
        for i, ch in enumerate(row):
            if ch != ".":
                px(gr, ox + i, oy + j, colors[ch])


def save(gr, name):
    im = Image.new("RGBA", (SIZE, SIZE))
    flat = []
    for y in range(SIZE):
        for x in range(SIZE):
            c = gr[y][x]
            flat.append(c + (255,) if c else (0, 0, 0, 0))
    im.putdata(flat)
    path = os.path.join(TEXDIR, name)
    im.save(path)
    print("written", os.path.relpath(path, ROOT), im.size)


# ---------------------------------------------------------------- 原木侧面
# 本体银树原木侧面（silverwoodside.png）按亮度量化成 6 档的字符矩阵：
# a 最暗 → f 最亮。第 176 批：恢复银树结构（第 174 批误换橡木，已回退）——
# 银树是白灰细密纹理，亮档 e/f 占大面积，配橡木棕黄时要把配色对齐橡木亮度跨度。
SILVERWOOD_SIDE = [
    "cceeffffeeffeedd",
    "cdefffdcdefffddc",
    "cceffffedddeeedb",
    "cdeeefffffffeedc",
    "dceedeffffeefedd",
    "deefeefffeadfdee",
    "ddefffffedeeeddd",
    "ddefffcbcededdcc",
    "cbadfffedfefeddc",
    "ddeffefffffffdcd",
    "ddeeffffffeffedd",
    "ddeeffffffeeeedd",
    "cdeeffffffeedddc",
    "cdcdffffffacdeec",
    "ddefffeddeefecdd",
    "ddefffffffffeedc",
]
SW_COLORS = {ch: SW_RAMP[i] for i, ch in enumerate("abcdef")}


def log_side():
    """枫木原木侧面 = 本体银树原木侧面的结构 + 橡木棕黄 palette。"""
    g = canvas()
    stamp(g, SILVERWOOD_SIDE, SW_COLORS)
    return g


# ---------------------------------------------------------------- 原木顶面
# 本体银树原木顶面（silverwoodtop.png）按亮度量化成 9 档。原图 256/256 全不透明——
# 第 169 批自绘「圆盘 + 四角透明」正是用户报的「顶部透明」bug 的根源，换真结构后不存在。
SILVERWOOD_TOP = [
    "feghiiiihhihhhff",
    "dfbcddcaccdccage",
    "eacdfdeecedgebad",
    "daegcdfffefdgebd",
    "fafcfigfghhddfbf",
    "fcfcfgefehghdecg",
    "fcfdhfdgeeehdebg",
    "fcfdgfghegehdebf",
    "eafdehgigfehdfbe",
    "gbfeffdgfdfhdfbf",
    "fcfegffegefgcfcf",
    "fbfdeigggghfcgbf",
    "ebdedggggffcfcbe",
    "eabdededdedgcdae",
    "ffbcddcbbccdbagf",
    "ffhiihhiiiiihggd",
]
SW_TOP_COLORS = {ch: SW_TOP_RAMP[i] for i, ch in enumerate("abcdefghi")}


def log_top():
    """枫木原木顶面 = 本体银树原木顶面的结构 + 橡木年轮色（整面不透明）。"""
    g = canvas()
    stamp(g, SILVERWOOD_TOP, SW_TOP_COLORS)
    return g


# ---------------------------------------------------------------- 枫叶
# 原版樱花树叶（cherry_leaves.png）的字符矩阵：a~k 是 11 档粉（浅→深），
# l~n 是 3 档绿点缀（浅→深），'.' 透明。第 169 批只换色表不换结构。
CHERRY_LEAVES = [
    "..hl.h..dcc..hih",
    ".iaaahd..ccc.bh.",
    "dhbabihdllc.bdb.",
    "abgbgbalmldihbdd",
    "aabjbaa.ldhdflld",
    "abgbgba..ddaaamn",
    ".ibabihh..lbabi.",
    ".haaa.hihabgbgba",
    "hhid...hdaabjbaa",
    ".bil.bbb.bbgbgba",
    "bdbmlcbchhibabi.",
    ".b.bcjejci.aaa..",
    "ciibbekebb.ee..c",
    "ccibcjejcbeeelcd",
    "dchnmcbcihhelmcc",
    "cilmlbbbdh..llhc",
]
LEAF_COLORS = {ch: LEAF_RAMP[i] for i, ch in enumerate("abcdefghijk")}
LEAF_COLORS.update({ch: LEAF_ORN[i] for i, ch in enumerate("lmn")})


def leaves():
    """枫叶：原版樱花树叶的结构 + 粉改红（第 170 批推鲜红）/ 绿点缀改橘点缀。

    覆盖率 216/256 ≈ 84%（原版樱花叶的密花冠），树冠是「一球红花」。
    """
    g = canvas()
    stamp(g, CHERRY_LEAVES, LEAF_COLORS)
    return g


# ---------------------------------------------------------------- 树苗
# 原版橡树树苗（oak_sapling.png）的字符矩阵：a~d 是茎的四档棕（亮→暗），
# e~h 是叶的四档绿（亮→暗），'.' 透明。第 169 批：叶绿改红橘、茎棕改深银。
OAK_SAPLING = [
    "................",
    "...........f....",
    ".......e..gg....",
    "....h.fe..habh..",
    "....bbgfhhabcc..",
    "..efegb.hbbcge..",
    "...efbac.bb.efe.",
    "......bbdb...ee.",
    ".....ghbef.hg...",
    "...cbcgefegbcd..",
    "..fggbcbadbcdgf.",
    ".fff.cebbdcd.ff.",
    ".....efgbfe...f.",
    ".....fedbef.....",
    ".......cbde.....",
    ".......ccd......",
]
SAP_COLORS = {ch: SAP_STEM[i] for i, ch in enumerate("abcd")}
SAP_COLORS.update({ch: SAP_LEAF[i] for i, ch in enumerate("efgh")})


def sapling():
    """枫树树苗：原版橡树树苗的结构 + 叶子红橘化 + 茎深银化。"""
    g = canvas()
    stamp(g, OAK_SAPLING, SAP_COLORS)
    return g


# ---------------------------------------------------------------- 枫木木板
# 原版橡木木板（oak_planks.png）的字符矩阵：a~g 是 7 档棕（暗→亮），
# '.' 透明（原图无透明）。第 172 批：用户要「直接用橡木木板修改」——
# 结构与颜色都照搬原版橡木木板（四道横板 + 竖接缝 + 木纹）。
OAK_PLANKS = [
    "fefgggggfgggggfc",
    "ffeecdefffeeffdd",
    "efffefeddddeeffc",
    "cbbccbaabbabaaba",
    "fgdggggdgggfeedf",
    "effededcdeeffffe",
    "ddefeeecfffeeddd",
    "aabbcbaaaabbbcba",
    "fggfeeggggggggfd",
    "feffffedddededdc",
    "gfeeddddeeeeddfd",
    "aabccbaaabcbbaaa",
    "gdfggffdgggdgfgg",
    "eeffddeddffefeee",
    "eddefedcffeddddd",
    "abbaabcccbabaaaa",
]
PLANK_COLORS = {ch: PLANK_RAMP[i] for i, ch in enumerate("abcdefg")}


def planks():
    """枫木木板：原版橡木木板的结构 + 原版橡木木板的棕黄配色。"""
    g = canvas()
    stamp(g, OAK_PLANKS, PLANK_COLORS)
    return g


if __name__ == "__main__":
    save(log_side(), "maple_log_side.png")
    save(log_top(), "maple_log_top.png")
    save(leaves(), "maple_leaves.png")
    save(sapling(), "maple_sapling.png")
    save(planks(), "maple_planks.png")
    print("done")
