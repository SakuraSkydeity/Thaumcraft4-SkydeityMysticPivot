#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""玄枢注灵台（`xuanshu_node_modifier`）的**方块模型 + 三张贴图**。

形制：**底板 + 四角立柱 + 顶部方框，中间完全镂空**（与 `ModBlocks.XuanshuNodeModifierBlock`
里的 `VoxelShape` 逐块对齐，一格不差）。

第一百五十七批用户两句：
  ① 「注灵台贴图也不对，而且我需要四周柱子 中间的空的。你现在贴图和模型是错位的，
      模型用 java 描述的吗，修改贴合」
  ② 根因确认：碰撞箱（Java）早就是「底板 + 四角柱 + 顶环」，但**模型（JSON）里仍留着
      一整块实心 `body`**，贴图又是按「实心方块正面」画的 —— 于是玩家看到的是一个
      贴了柱面纹样的实心箱子。本批把 `body` 整块删掉，模型与碰撞箱从此同源。

第一百五十八批用户一句：
  「我希望四周柱子往里一点 贴图符合」—— 四角柱内收 1 像素（0/13 → 1/12，3/16 → 4/15），
  顶环（挑檐）与底板仍占满 0~16，「柱往里收、檐在外」（第 12.153.5 批定下的层次）；
  柱顶补 `up` 柱帽面，按俯视平面图 1:1 取图，内收后柱帽角上不再露出底板托盘纹。

第一百五十九批用户一句：
  「贴图有问题 而且太丑了 花纹多一点」—— 三面重画、花纹加密：柱身加帽沿/双箍/中錾线，
  各带加联珠与铆钉，顶面加角花与梁中铆钉，底面加八向芒。顺手修两处旧问题：
  ① 柱帽角上唯一外露的那一像素，原先被采到「洞」的最深色，俯视像四个小黑洞
     → 唇外那圈改深金，角上各给一枚铆钉；
  ② 顶框八根梁头的外伸端面，原先拿 16 宽的顶框带横向压扁到 3px，纹样全糊
     → 侧面图新增 `x 0..3, y 14..16` 的 3×2 **梁头端帽** UV 区，端面也 1:1。

第一百六十批用户一句：
  「注灵台上面哪一个空心正方形修改一下，太突兀了，符合古代仙家风格」——
  顶框中间那个 10×10 方口原先是一刀切下去的直角洞口，俯视太硬。本批给它加一圈
  一像素宽的「井沿」内唇（贴洞口内沿、比梁面低 1px 的退台），井沿带按俯视平面图
  描金 + 联珠，四角留柱帽铆钉；洞底仍是一贯的灵光托盘。于是方口读作「凿出来的
  灵井口」，不再是戳了一个洞。

UV 分区（这是「不错位」的关键：每个面都按 1:1 取图，不做拉伸）：

  * 侧面图 `xuanshu_node_modifier.png`（16×16，一张图四个区）：
      - `x 0..3, y 0..8`  **柱身**：3 宽 8 高，原样贴到四根角柱的四个侧面；
      - `y 8..10`         **底板带**：16 宽 2 高，贴底板的四个侧面；
      - `y 10..12`        **顶框带**：16 宽 2 高，贴顶框的外侧 / 内侧 / 底面；
                            10 宽的那两面取 `x 3..13`，同样 1:1，不缩放；
      - `y 12..14`        梁底（朝着洞的那一面）：压暗 + 一排铆钉；
      - `x 0..3, y 14..16` **梁头端帽**：3 宽 2 高，贴顶框八根梁头的外伸端面
                            （第一百五十九批新增：端面原先被 16 宽带压扁，纹样全糊）。
  * 顶面图 `_top.png`：**俯视平面图**。顶框四根梁的 `up` 面各自按自己的占地取图
    （北梁取 0..16 × 0..3 等），四块拼起来就是一张完整的回字框；`d == 3 / d == 4`
    两圈是第一百六十批新增的**井沿带**（井沿内唇的 `up` 面按自己占地取这两圈），
    正中 10×10 仍是洞，永远不被采到，压成最深色。
  * 底面图 `_bottom.png`：一块**托盘**。同时用作底板顶面（从洞里往下看到的就是它），
    正中一点灵光。

本脚本同时**生成模型 JSON** —— UV 与元素写在同一处，改形制时不会一半改一半漏。
"""
import json
import os


from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot")
BLOCK_TEX_DIR = os.path.join(ASSETS, "textures/block")
MODEL_DIR = os.path.join(ASSETS, "models/block")
PREVIEW = os.path.join(ROOT, "tools/preview")

TEX = 16
NS = "skydeitymysticpivot"

# 七档鎏金 + 两档灵光（第一百五十六批定稿的「古代鎏金」，不再抖动过渡）
GOLD_H = (245, 217, 138)   # 高光（棱线、箍、铆钉顶）
GOLD_L = (217, 169, 78)    # 亮金
GOLD = (192, 142, 62)      # 金面
GOLD_M = (168, 122, 48)    # 金面（暗半档）
GOLD_D = (122, 82, 32)     # 暗金（缝、收口）
GOLD_S = (94, 62, 24)      # 深缝
DARK = (52, 33, 12)        # 最深（外框、镂空、底缘）
GLOW = (255, 216, 128)     # 灵光外圈
GLOW_L = (255, 246, 214)   # 灵光芯

# ---- 侧面图的四个分区（与 build_model 里的 UV 一一对应，改这里就等于改模型）----
POST_W, POST_H = 3, 8      # 柱身区：0..3 × 0..8
BAND_BASE_V = 8            # 底板带：y 8..10
BAND_RING_V = 10           # 顶框带（梁的四个立面）：y 10..12
BAND_SOFFIT_V = 12         # 梁底（朝着洞的那一面）：y 12..14，压暗，读出厚度
BAND_ENDCAP_V = 14         # 梁头端帽（梁头外伸端面）：x 0..3 × y 14..16
BAND_H = 2
#: 10 宽的面（顶框东西两梁）取顶框带的哪一段：跳过两端的收角，保持 1:1
RING_INNER_U0, RING_INNER_U1 = 3, 13


# ----------------------------------------------------------------------
# 画图小工具
# ----------------------------------------------------------------------

def blank():
    """纯色底：不留任何随机抖动（抖动 = 噪点，见 README §9 第 18 条）。"""
    return Image.new("RGBA", (TEX, TEX), GOLD + (255,))


def rect(px, x0, y0, x1, y1, color):
    for y in range(y0, y1):
        for x in range(x0, x1):
            if 0 <= x < TEX and 0 <= y < TEX:
                px[x, y] = color


# ----------------------------------------------------------------------
# 侧面图：一张图装「柱身 + 底板带 + 顶框带」
# ----------------------------------------------------------------------

def gen_side():
    im = blank()
    px = im.load()

    # ---- 柱身区 x0..2, y0..7：一根有帽沿、双箍、中錾线的鎏金小柱 ----
    # 三列分工：左棱暗、柱身受光、右缘更深，3px 里读得出「圆」
    post = (
        (GOLD_H, GOLD_H, GOLD_M),   # 0 帽沿：左中受光，右缘转暗
        (GOLD_L, GOLD_H, GOLD_D),   # 1 柱颈
        (GOLD_H, GOLD_H, GOLD_M),   # 2 上箍（亮箍，中间亮点当铆钉）
        (GOLD_D, GOLD_M, GOLD_S),   # 3 箍下暗影
        (GOLD_D, GOLD_L, GOLD_S),   # 4 柱身 + 中錾线
        (GOLD_M, GOLD_L, GOLD_D),   # 5 柱身 + 中錾线（稍暗，读出圆柱）
        (GOLD_H, GOLD_H, GOLD_D),   # 6 下箍（亮箍）
        (GOLD_M, GOLD_D, DARK),     # 7 柱脚
    )
    for y in range(POST_H):
        for x in range(POST_W):
            px[x, y] = post[y][x]

    # ---- 梁头端帽区 x0..2, y14..15：顶框八根梁头的外伸端面（3×2，1:1）----
    # 小挑檐：受光棱 → 金面 → 转暗，下一道中缝
    cap = (
        (GOLD_H, GOLD_L, GOLD_D),   # 14 上排
        (GOLD_L, GOLD_M, GOLD_S),   # 15 下排 + 中缝
    )
    for y in range(BAND_H):
        for x in range(POST_W):
            px[x, BAND_ENDCAP_V + y] = cap[y][x]

    # ---- 底板带 y8..9：上棱高光 + 联珠纹，两端收暗当转角 ----
    rect(px, 0, BAND_BASE_V, 16, BAND_BASE_V + 1, GOLD_H)
    rect(px, 0, BAND_BASE_V + 1, 16, BAND_BASE_V + 2, GOLD_M)
    for x in (2, 5, 8, 11, 14):     # 联珠：每三 px 一枚亮珠
        px[x, BAND_BASE_V + 1] = GOLD_L
    for x in (0, 15):
        px[x, BAND_BASE_V] = GOLD_L
        px[x, BAND_BASE_V + 1] = GOLD_S

    # ---- 顶框带 y10..11：梁的四个立面，上棱亮 + 联珠，下沿金面 + 凹点 ----
    rect(px, 0, BAND_RING_V, 16, BAND_RING_V + 1, GOLD_H)
    rect(px, 0, BAND_RING_V + 1, 16, BAND_RING_V + 2, GOLD_L)
    for x in (1, 4, 7, 10, 13):     # 上排亮珠
        px[x, BAND_RING_V] = GOLD_L
    for x in (2, 5, 8, 11, 14):     # 下排凹点（与上排错开）
        px[x, BAND_RING_V + 1] = GOLD_M
    for x in (0, 15):               # 两端转角压暗
        px[x, BAND_RING_V] = GOLD_L
        px[x, BAND_RING_V + 1] = GOLD_D

    # ---- 梁底 y12..13：朝洞的那一面，压到深金，一排铆钉 ----
    rect(px, 0, BAND_SOFFIT_V, 16, BAND_SOFFIT_V + 1, GOLD_S)
    rect(px, 0, BAND_SOFFIT_V + 1, 16, BAND_SOFFIT_V + 2, DARK)
    for x in (2, 5, 8, 11, 14):     # 梁底铆钉（与顶框带凹点上下对齐）
        px[x, BAND_SOFFIT_V] = GOLD_M

    return im


# ----------------------------------------------------------------------
# 顶面图：俯视平面图（四根梁各取自己那一块，拼起来才是一整张）
# ----------------------------------------------------------------------

def gen_top():
    im = Image.new("RGBA", (TEX, TEX), DARK + (255,))
    px = im.load()

    # 回字框：按「到外沿的距离」分四带，3px 正好等于顶框的梁宽
    for y in range(TEX):
        for x in range(TEX):
            d = min(x, y, TEX - 1 - x, TEX - 1 - y)
            if d == 0:
                px[x, y] = GOLD_L     # 外棱（受光最足）
            elif d == 1:
                px[x, y] = GOLD_H     # 梁面
            elif d == 2:
                px[x, y] = GOLD_D     # 内唇（朝着洞口，压在阴影里）
            elif d == 3:
                px[x, y] = GOLD_S     # 唇外深金：只有柱帽采得到，别露黑洞
            # d >= 4：洞，保持最深色
    # 四角角花：三级退台 + 台心暗影，当中一枚铆钉（下面统一画）
    for cx, cy in ((0, 0), (15, 0), (0, 15), (15, 15)):
        sx = 1 if cx == 0 else -1
        sy = 1 if cy == 0 else -1
        px[cx + 2 * sx, cy] = GOLD_D           # 外棱远端压暗（转角）
        px[cx, cy + 2 * sy] = GOLD_D
        px[cx + 2 * sx, cy + sy] = GOLD_L      # 第二级台面提亮
        px[cx + sx, cy + 2 * sy] = GOLD_L
        px[cx + 2 * sx, cy + 2 * sy] = GOLD_S  # 台心暗影

    # 梁面联珠：四梁中段各两枚亮珠（避开角花与梁中铆钉）
    for x in (3, 12):
        px[x, 1] = GOLD_L
        px[x, 14] = GOLD_L
    for z in (3, 12):
        px[1, z] = GOLD_L
        px[14, z] = GOLD_L

    # 铆钉：角花正中各一枚 + 四条梁的中点各一枚（1px，多了就成麻点）
    for (cx, cy) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        px[cx, cy] = GOLD_M
    for (cx, cy) in ((7, 1), (8, 1), (7, 14), (8, 14), (1, 7), (1, 8), (14, 7), (14, 8)):
        px[cx, cy] = GOLD_M

    # 柱帽角铆：四根柱子顶端唯一露在框外的就是这一像素，给枚铆钉
    for (cx, cy) in ((3, 3), (12, 3), (3, 12), (12, 12)):
        px[cx, cy] = GOLD_M

    # ---- 井沿带（第一百六十批）：d==3 / d==4 两圈，井沿内唇的 up 面按自己占地取 ----
    # d==3：井沿外棱，落在顶框内唇的阴影里，压到深金
    # d==4：井沿内沿（朝洞的那一棱），暗金底 + 一排联珠，读作「凿出来的井口描了金」
    # 四角那四枚是柱帽铆，跳过——它们正好坐在井沿外棱的四角上当角钉
    lip_rivet = {(3, 3), (12, 3), (3, 12), (12, 12)}
    for y in range(TEX):
        for x in range(TEX):
            if (x, y) in lip_rivet:
                continue
            d = min(x, y, TEX - 1 - x, TEX - 1 - y)
            if d == 3:
                px[x, y] = GOLD_S     # 井沿外棱：深金，阴影里
            elif d == 4:
                px[x, y] = GOLD_D     # 井沿内沿：暗金底
    # 井沿联珠：四边中点各两枚亮珠（与梁面联珠错开，避开井沿四角）
    for x in (7, 8):
        px[x, 4] = GOLD_L
        px[x, 11] = GOLD_L
    for z in (7, 8):
        px[4, z] = GOLD_L
        px[11, z] = GOLD_L

    return im


# ----------------------------------------------------------------------
# 底面图：托盘（同时是「从洞里往下看到的地板」）
# ----------------------------------------------------------------------

def gen_bottom():
    im = Image.new("RGBA", (TEX, TEX), GOLD_D + (255,))
    px = im.load()

    # 外框 1px 最深
    rect(px, 0, 0, 16, 1, DARK)
    rect(px, 0, 15, 16, 16, DARK)
    rect(px, 0, 0, 1, 16, DARK)
    rect(px, 15, 0, 16, 16, DARK)

    # 方环（不是圆环：16px 里画圆，阶梯边看着像齿轮）
    # d=6 那一道正好落在灵光 4×4 的边界上，会被灵光整个盖掉，故不画
    for y in range(TEX):
        for x in range(TEX):
            d = min(x, y, TEX - 1 - x, TEX - 1 - y)
            if d == 3:
                px[x, y] = GOLD_S     # 洞口内唇（从顶框洞里第一眼看到它）
            elif d == 4:
                px[x, y] = GOLD_S     # 环

    # 环上联珠：八向各一枚亮珠
    for (cx, cy) in ((7, 4), (8, 4), (7, 11), (8, 11), (4, 7), (4, 8), (11, 7), (11, 8)):
        px[cx, cy] = GOLD_L

    # 两环之间的八向芒：灵光从中心透出来（d=5 带，四正向各 2px、四斜向各 1px）
    for (cx, cy) in ((7, 5), (8, 5), (7, 10), (8, 10), (5, 7), (5, 8), (10, 7), (10, 8),
                     (5, 5), (10, 5), (5, 10), (10, 10)):
        px[cx, cy] = GLOW

    # 正中灵光：透过顶框的洞看下去，就落在这一点上
    for y in range(6, 10):
        for x in range(6, 10):
            d = max(abs(x - 7.5), abs(y - 7.5))
            px[x, y] = GLOW_L if d < 1.0 else GLOW
    px[7, 7] = GLOW_L
    return im


# ----------------------------------------------------------------------
# 模型 JSON —— 每块都与 XuanshuNodeModifierBlock.SHAPE 对齐
# ----------------------------------------------------------------------

def f(uv, tex="#side"):
    return {"uv": list(uv), "texture": tex}


def element(name, frm, to, faces):
    return {"name": name, "from": list(frm), "to": list(to), "faces": faces}


# 与 Java VoxelShape 完全相同的盒子
# ★ 第一百五十八批：四角柱往里收 1 像素（0/13 → 1/12），顶环与底板不动
BASE = ((0, 0, 0), (16, 2, 16))
POSTS = (
    ("post_nw", (1, 2, 1)),
    ("post_ne", (12, 2, 1)),
    ("post_sw", (1, 2, 12)),
    ("post_se", (12, 2, 12)),
)
POST_SIZE = 3
POST_TOP = 10
RING_Y = (10, 12)
RINGS = (
    ("ring_north", (0, RING_Y[0], 0), (16, RING_Y[1], 3), (0, 0, 16, 3)),
    ("ring_south", (0, RING_Y[0], 13), (16, RING_Y[1], 16), (0, 13, 16, 16)),
    ("ring_west", (0, RING_Y[0], 3), (3, RING_Y[1], 13), (0, 3, 3, 13)),
    ("ring_east", (13, RING_Y[0], 3), (16, RING_Y[1], 13), (13, 3, 16, 13)),
)
# ★ 第一百六十批：井沿——贴着洞口内沿的一圈 1px 退台（y10..11，比梁面低 1px），
# 把直角方口变成「凿出来的井口」。四块拼成洞内口那圈细框：
# 南北两条 10 宽，东西两条收在两端之间（不与南北重叠）。
LIP_Y = (10, 11)
LIPS = (
    ("lip_north", (3, LIP_Y[0], 3), (13, LIP_Y[1], 4), (3, 3, 13, 4)),
    ("lip_south", (3, LIP_Y[0], 12), (13, LIP_Y[1], 13), (3, 12, 13, 13)),
    ("lip_west", (3, LIP_Y[0], 4), (4, LIP_Y[1], 12), (3, 4, 4, 12)),
    ("lip_east", (12, LIP_Y[0], 4), (13, LIP_Y[1], 12), (12, 4, 13, 12)),
)


def build_model():
    elements = []

    # 1) 底板：四个侧面用底板带；顶面（洞里看得到的地板）与底面都用托盘图
    uy = [0, BAND_BASE_V, 16, BAND_BASE_V + BAND_H]
    faces = {
        "down": dict(f([0, 0, 16, 16], "#bottom"), cullface="down"),
        "up": f([0, 0, 16, 16], "#bottom"),
        "north": f(uy), "south": f(uy), "west": f(uy), "east": f(uy),
    }
    elements.append(element("base", BASE[0], BASE[1], faces))

    # 2) 四角立柱：四个侧面用柱身区（3×8 原样，1:1）；柱顶补柱帽面，
    #    按俯视平面图 1:1 取自己占地那一块（内收 1 像素后角上不再露托盘纹）
    for name, (x, y, z) in POSTS:
        uv = [0, 0, POST_W, POST_H]
        cap = [x, z, x + POST_SIZE, z + POST_SIZE]
        fc = {"north": f(uv), "south": f(uv), "west": f(uv), "east": f(uv),
              "up": f(cap, "#top")}
        elements.append(element(name, (x, y, z), (x + POST_SIZE, POST_TOP, z + POST_SIZE), fc))

    # 3) 顶部方框：四根梁
    band_full = [0, BAND_RING_V, 16, BAND_RING_V + BAND_H]
    band_side = [RING_INNER_U0, BAND_RING_V, RING_INNER_U1, BAND_RING_V + BAND_H]
    soffit_full = [0, BAND_SOFFIT_V, 16, BAND_SOFFIT_V + BAND_H]
    soffit_side = [RING_INNER_U0, BAND_SOFFIT_V, RING_INNER_U1, BAND_SOFFIT_V + BAND_H]
    # 梁头端帽（3×2）：八根梁头的外伸端面 1:1 取图
    # （第一百五十九批前，这些 3 宽的端面拿的是 16 宽的顶框带，纹样全被压扁）
    cap_uv = [0, BAND_ENDCAP_V, POST_W, BAND_ENDCAP_V + BAND_H]
    for name, frm, to, uv_top in RINGS:
        long_side = name in ("ring_north", "ring_south")
        side_uv = band_full if long_side else band_side
        # 长梁（南北）：南北两面 16 宽取整带，东西两端 3 宽取端帽
        # 短梁（东西）：东西两面 10 宽取中段，南北两端 3 宽取端帽
        if long_side:
            sides = {"north": f(side_uv), "south": f(side_uv),
                     "west": f(cap_uv), "east": f(cap_uv)}
        else:
            sides = {"west": f(side_uv), "east": f(side_uv),
                     "north": f(cap_uv), "south": f(cap_uv)}
        fc = {
            # 顶面按自己占地取俯视平面图 —— 四块拼成一张完整的回字框
            "up": f(uv_top, "#top"),
            # 底面（朝着洞的那一面）单独取「梁底」那一条，压暗才能读出框的厚度
            "down": f(soffit_full if long_side else soffit_side),
        }
        fc.update(sides)
        elements.append(element(name, frm, to, fc))

    # 4) 井沿（第一百六十批）：洞内口一圈 1px 退台，顶面按俯视平面图取自己那一带
    #    （d==3 外棱 + d==4 内沿，见 gen_top），朝洞的立面取梁底带（压暗当井壁阴影）
    for name, frm, to, uv_top in LIPS:
        fc = {
            "up": f(uv_top, "#top"),
            "down": f(soffit_side),
            "north": f(soffit_side), "south": f(soffit_side),
            "west": f(soffit_side), "east": f(soffit_side),
        }
        elements.append(element(name, frm, to, fc))

    return {
        "parent": "block/block",
        "ambientocclusion": False,
        "textures": {
            "particle": NS + ":block/xuanshu_node_modifier",
            "side": NS + ":block/xuanshu_node_modifier",
            "top": NS + ":block/xuanshu_node_modifier_top",
            "bottom": NS + ":block/xuanshu_node_modifier_bottom",
        },
        "elements": elements,
        "render_type": "minecraft:cutout",
    }


def main():
    os.makedirs(PREVIEW, exist_ok=True)
    jobs = (
        ("xuanshu_node_modifier", gen_side),
        ("xuanshu_node_modifier_top", gen_top),
        ("xuanshu_node_modifier_bottom", gen_bottom),
    )
    for name, fn in jobs:
        dst = os.path.join(BLOCK_TEX_DIR, name + ".png")
        fn().save(dst)
        print("->", os.path.relpath(dst, ROOT))

    model_path = os.path.join(MODEL_DIR, "xuanshu_node_modifier.json")
    with open(model_path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(build_model(), fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    print("->", os.path.relpath(model_path, ROOT))

    # 三张图 + 分区示意（左：原尺寸放大；右：标出三个 UV 区）
    cell = TEX * 18
    canvas = Image.new("RGBA", (cell * 4 + 50, cell + 20), (24, 20, 32, 255))
    for i, (name, _) in enumerate(jobs):
        im = Image.open(os.path.join(BLOCK_TEX_DIR, name + ".png"))
        canvas.paste(im.resize((cell, cell), Image.NEAREST), (10 + i * (cell + 10), 10))
    canvas.save(os.path.join(PREVIEW, "node_modifier_tex.png"))
    print("preview ->", os.path.relpath(os.path.join(PREVIEW, "node_modifier_tex.png"), ROOT))


if __name__ == "__main__":
    main()
