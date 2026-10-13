#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
重绘玄枢法杖杖柄 textures/models/onyx_wand_rod.png（32x32）+ 物品图标（16x16）。

MC 盒模型 UV 布局（已核对 1.21.1 ModelPart.Cube 源码，杖 = addBox(-1,-1,-1, 2,18,2) texOffs(0,8)）：
    使用区 = x0-8 / y8-28：
      x0-2 y10-28  WEST  (-X)  u 增大 -> 朝北(-Z)
      x2-4 y10-28  NORTH (-Z)  u 增大 -> 朝东(+X)
      x4-6 y10-28  EAST  (+X)  u 增大 -> 朝南(+Z)
      x6-8 y10-28  SOUTH (+Z)  u 增大 -> 朝西(-X)
      x4-6 y8-10   UP  (顶面)      x2-4 y8-10 DOWN (底面)
    v=10 是杖的底（model y0），v=28 是杖的顶（model y18）——贴图行与杖高反向。

四张侧面条带按 x 顺序（WEST,NORTH,EAST,SOUTH）恰好绕杖一圈，且每张的右棱接下一张的左棱
（WEST.x2<->NORTH.x2<->...<->SOUTH.x8<->WEST.x0）。因此一条在贴图里「每条带都向右下方
斜」的带子，在三维里就是一条连续上升的螺旋：带心连续坐标 c(v) = (v-10)*k + phase，
第 s 张带 = floor(c/2) mod 4，带内列 = c mod 2。

设计（12.63 批：**整体暗紫为主**，淡紫粉只做点缀）：
  * 杖体：深紫（主体），五档明暗 `DEEP_HI > DEEP > DEEP_MID > DEEP_DK > DEEP_DEEP`
    按面烘焙圆柱感，另加几道竖纹沟 `GRAIN`。
  * 缠绕：**两列并排**的 1px 淡紫粉亮丝（`BAND_OFFSETS=(0,1)`，k=1 ⇒ 45 度螺旋，
    18 行约 2.25 圈）——合成一条宽 1/4 圈的斜带，深紫仍占 3/4 圈。
    上批（12.62）是三条亮线，亮色占 3/8 列，整体读起来是「紫底粉带」；这批收到 2/8，
    暗紫才真正成为主色。
  * 丝上结：每 5 行一个 `LILAC_HI` 亮点，杖腰再点一颗 `GLINT`。
  * 上下箍：v=11 / v=27 两道**中紫**横箍（`BAND` / `BAND_DK` 奇偶相间，
    不再是淡紫粉，免得又被亮色抢戏）。
  * 顶/底面：深紫底 + 中紫边。

实测（本批末重新量，可见面 = x0-8 / y10-28 共 154 px）：
  * 深紫五档 + 纹沟 ≈ **67%**；
  * 淡紫粉亮丝 + 结（`LILAC*`）≈ **20.1%**；
  * 中紫箍（`BAND*`）≈ **13%**。
  ⇒ 「暗紫为主」成立；亮色只剩一条斜带 + 两道箍。

12.66 批追加（用户第 4 条）：**物品图标**（16×16）不再自己另画轮廓，
改成逐行套用 `textures/item/sixiang_wand_rod.png`（四象杖柄）的像素轮廓再涂深紫/粉
—— 见 `draw_item_icon()`。模型贴图（32×32）本批未动。
"""
import os
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MODEL_TEX = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/models")
ITEM_TEX = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/item")

# ---------------------------------------------------------------- 调色板
# 12.63 批：用户「再修改玄枢杖柄的贴图，整体暗紫色为主」——
# 上批（12.62）已经是「深紫杖体 + 淡紫粉螺旋」，但螺旋是**三条** 1px，
# 亮色占了 3/8 列，整体观感仍偏「紫底粉带」。这批把整体压暗：
#   1. 螺旋收到**一条** 1px 亮丝（淡紫粉只出现在 1/8 列上）；
#   2. 上下箍由淡紫粉改成**中紫**；
#   3. 杖体四级明暗整体再往下压一档（暗面更深）。
DEEP_HI = (120, 72, 170)     # 杖体最亮面
DEEP = (98, 56, 142)         # 杖体亮面
DEEP_MID = (80, 44, 120)     # 杖体中调
DEEP_DK = (62, 34, 96)       # 杖体暗面
DEEP_DEEP = (46, 24, 72)     # 杖体最深面（新增，让圆柱更立体）
GRAIN = (44, 22, 72)         # 竖向纹沟（比杖体暗一档的深紫）

LILAC_HI = (246, 220, 244)   # 淡紫粉最亮（丝上结）
LILAC = (226, 186, 230)      # 淡紫粉亮丝

BAND = (150, 104, 176)       # 上下箍：中紫（不再是淡紫粉）
BAND_DK = (122, 80, 148)     # 箍的暗侧

GLINT = (255, 238, 252)      # 灵光（极淡的粉白）

# 绕杖一圈（贴图 x 顺序）各面的底色：杖在游戏里整根全亮度渲染（glowing），
# 立体感全靠贴图自带，所以按面烘焙四级明暗——第一人称右手位（yaw=45）看到的是
# SOUTH+WEST 两张脸，把最亮的放在那两侧。
#   x0-2 WEST 最亮   x2-4 NORTH 中    x4-6 EAST 最暗    x6-8 SOUTH 亮
FACE_BASE = [
    DEEP_HI,
    DEEP_MID,
    DEEP_DEEP,
    DEEP,
]

STRIPS = [(0, "WEST"), (2, "NORTH"), (4, "EAST"), (6, "SOUTH")]  # (x 起点, 面)
BAND_OFFSETS = (0, 1)      # 两列并排 ⇒ 一条真正认得出的「带」（宽 1/4 圈，深紫仍占 3/4）
K = 1.0          # 每行斜几列（1 = 45 度）
PHASE = 3.0      # 螺旋初相（列）


def band_x(v):
    """行 v 处螺旋带心的连续列坐标（0..8 绕一圈）。"""
    return (v - 10) * K + PHASE


def draw_rod():
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = im.load()

    # ---- 杖体：四张侧面条带各一级深紫明暗（烘焙圆柱感），再加几道竖向纹理 ----
    for si, (x0, _) in enumerate(STRIPS):
        base = FACE_BASE[si]
        for v in range(10, 28):
            px[x0, v] = base
            px[x0 + 1, v] = base
    # 竖向木/石纹：每面两三道短沟，别太规矩
    grain = [
        (0, 13, 3), (1, 21, 2),          # WEST
        (2, 16, 3), (3, 24, 2),          # NORTH
        (4, 12, 2), (5, 20, 3),          # EAST
        (6, 15, 3), (7, 25, 2),          # SOUTH
    ]
    for x, v0, ln in grain:
        for i in range(ln):
            px[x, v0 + i] = GRAIN

    # ---- 缠绕：两条并排 1px 淡紫粉亮丝 ⇒ 一条认得出的螺旋带（占 1/4 圈） ----
    #   （亮丝只出现在 2 列上，其余 6 列是深紫杖体）
    for v in range(10, 28):
        c = band_x(v)
        for off in BAND_OFFSETS:
            px[int(c + off) % 8, v] = LILAC

    # ---- 丝上结：每 5 行一个淡紫粉亮点（比上批稀，只做点缀） ----
    for v in range(12, 28, 5):
        core = int(band_x(v)) % 8
        px[core, v] = LILAC_HI
    # 居中那个结点一点灵光
    c = int(band_x(20)) % 8
    px[c, 20] = GLINT

    # ---- 上下中紫箍：v=11 与 v=27 两道，把缠绕收住（不再是淡紫粉） ----
    #   奇偶相间 ⇒ 读起来是一道「编绳箍」而不是整片中紫
    for x in range(0, 8):
        px[x, 11] = BAND if x % 2 == 0 else BAND_DK
        px[x, 27] = BAND_DK if x % 2 == 0 else BAND

    # ---- 顶面 / 底面（各 2x2）：中紫边一道 + 深紫棋盘 ----
    #   UP = x4-5，DOWN = x2-3（照 FACES：up=(4,8,6,10) / down=(2,8,4,10)，各 2x2）
    px[2, 8] = px[3, 8] = BAND_DK               # DOWN 中紫边（挨着侧壁那一圈）
    px[2, 9], px[3, 9] = DEEP_DK, DEEP_MID
    px[4, 8] = px[5, 8] = BAND_DK               # UP 中紫边
    px[4, 9], px[5, 9] = DEEP_MID, DEEP_DK
    px[6, 8] = px[6, 9] = DEEP_DEEP             # UP 右侧不可见邻列，填深色防 mip 渗黑

    im.save(os.path.join(MODEL_TEX, "onyx_wand_rod.png"))
    print("onyx_wand_rod.png written (32x32, used x0-8 y8-28)")
    return im


def draw_item_icon():
    """16x16 扁平图标：**照四象杖柄的轮廓重涂**成深紫 + 粉色（12.66 批）。

    用户第 4 条：「玄枢杖柄的物品贴图以四象为基础修改（就是轮廓借鉴）整体深紫色粉色」。

    做法（不再自己另画一个轮廓）：
      1. 读 `textures/item/sixiang_wand_rod.png`，它就是一条自左下伸向右上的
         四格宽斜带（两端各收一格的「刀带」），是本项目里被认可的杖柄图标轮廓；
      2. **逐行**取非透明像素，按亮度判出「最亮的那一格」= 高光；
      3. 其余格按从左到右依次落到 `DEEP_HI → DEEP_MID → DEEP_DEEP` 三档深紫上，
         高光那格涂粉（`LILAC_HI`，原图偏暗的行退一档用 `LILAC`）。

    这样「轮廓」（每一行有几个像素、在第几列）与四象杖柄**逐像素一致**，
    只换配色 —— 正是「以四象为基础、轮廓借鉴」。
    """
    src = Image.open(os.path.join(ITEM_TEX, "sixiang_wand_rod.png")).convert("RGBA")
    sp = src.load()
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()

    HI = (250, 224, 246)          # 高光：亮粉
    HI_DIM = (226, 180, 222)      # 原图那一格偏暗时退一档
    DARKROW = 30                  # 整行都比这个暗（杖的上下端头）⇒ 全深紫

    for y in range(16):
        cols = [x for x in range(16) if sp[x, y][3] > 0]
        if not cols:
            continue
        lum = [(sp[x, y][0] + sp[x, y][1] + sp[x, y][2]) / 3.0 for x in cols]
        if max(lum) < DARKROW:
            for k, x in enumerate(cols):
                px[x, y] = (DEEP_MID if k == 0 else DEEP_DEEP) + (255,)
            continue
        bright = lum.index(max(lum))
        for k, x in enumerate(cols):
            if k == bright:
                px[x, y] = (HI if lum[k] >= 180 else HI_DIM) + (255,)
                continue
            # 其余格按出现顺序落到三档深紫（多出来的重复最深档）
            rest = [i for i in range(len(cols)) if i != bright].index(k)
            px[x, y] = (DEEP_HI, DEEP_MID, DEEP_DEEP, DEEP_DEEP)[min(rest, 3)] + (255,)

    im.save(os.path.join(ITEM_TEX, "onyx_wand_rod.png"))
    print("item/onyx_wand_rod.png written (16x16, 轮廓照四象杖柄 + 深紫/粉)")
    return im


if __name__ == "__main__":
    draw_rod()
    draw_item_icon()