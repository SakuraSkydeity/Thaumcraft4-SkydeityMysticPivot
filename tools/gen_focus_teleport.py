#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
「法杖核心:传送」（focus_teleport）的图标与侧边装饰贴图（12.66 批再改：**整体以圆形为主**）。

用户第 1 条：「核心：传送的模型没问题，贴图修改一下，整体还是圆形为主」。
12.63 批把图标画成了「星盘 + 四角外挂十字星」，四枚角星露在圆外，整体读起来
是「一朵尖角星」而不是「一颗珠子」——这一批把外面那些一律收回圆内：

  * `focus_teleport.png`（layer1，物品图标主体）——**一颗圆宝珠**：
    圆形轮廓（半径 6.2，落成一张规整的圆盘）+ 金圈；珠内是深紫星空，
    中心亮、边缘深；正中一枚小十字星（臂长 2），另散三枚星点，圆周四斜位点青灵光。
  * `focus_teleport_orn.png`（layer0 + 法杖上的模型轮廓）——
    **一颗紧凑的十字星簇**：中央一枚大十字星（臂长 3）+ 四斜位四枚小十字星（臂长 2）。
    整簇最大半径 5.15 < 宝珠半径 6.2 ⇒ 全部落在宝珠圆内：
      - 当 layer0 用时被 layer1 的圆盘完全盖住 ⇒ 图标就是一颗干净的圆珠；
      - 当法杖 ornament 用时，本体（`WandFocusRenderer.renderFocusOrnament`）照它的
        alpha 轮廓挤出薄板 ⇒ 杖头仍浮着「几个十字星」，模型观感不变。

★ 一条硬约束（上一批踩过）：图标里的十字星**臂长只能到 2**。臂长 ≥4 会顶到金圈上，
  整颗珠子读成「一个加号」。ornament 里的大星臂长 3 是安全的——它不在图标里露面。
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEM_TEX = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/item")

# ---- 星辰调色板 ----
SPACE_DEEP = (30, 22, 62)       # 珠缘内侧最深的星空紫
SPACE = (48, 36, 96)            # 星空紫
SPACE_HI = (78, 62, 140)        # 星空亮紫（左上受光侧）
NEBULA = (116, 92, 186)         # 星云紫（珠心）
GOLD = (232, 196, 108)          # 鎏金（星芒外段 / 金圈）
GOLD_HI = (250, 232, 176)       # 金芒最亮（星芒内段 / 金圈高光）
STAR = (246, 244, 255)          # 星心（近白）
CYAN = (150, 226, 232)          # 青灵光（点缀）

CX, CY = 7.5, 7.5               # 贴图中心（像素坐标）
RADIUS = 6.2                    # 宝珠半径


def orb_cells():
    """半径 6.2 的实心圆盘：返回 {(x, y)}。"""
    cells = set()
    for y in range(16):
        for x in range(16):
            if ((x + 0.5 - CX) ** 2 + (y + 0.5 - CY) ** 2) <= RADIUS ** 2:
                cells.add((x, y))
    return cells


ORB = orb_cells()
RIM = {c for c in ORB if any((c[0] + dx, c[1] + dy) not in ORB
                             for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))}
# 金圈上四点青灵光：取圆上四个斜位（左上一枚、右上两枚、左下一枚）
RIM_GLINT = ((4, 3), (11, 3), (3, 4), (11, 12))


def dist(x, y):
    return ((x + 0.5 - CX) ** 2 + (y + 0.5 - CY) ** 2) ** 0.5


def star(px, cx, cy, arm, inner=GOLD_HI, outer=GOLD):
    """四正十字星：臂长 arm，臂根 inner → 臂梢 outer，星心 STAR。"""
    for d in range(1, arm + 1):
        c = inner if d == 1 else outer
        for (x, y) in ((cx, cy - d), (cx, cy + d), (cx - d, cy), (cx + d, cy)):
            if 0 <= x < 16 and 0 <= y < 16:
                px[x, y] = c + (255,)
    if 0 <= cx < 16 and 0 <= cy < 16:
        px[cx, cy] = STAR + (255,)


def build_focus():
    """圆宝珠：圆形轮廓 + 金圈 + 珠内星空 + 正中一枚小十字星 + 散点。"""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for (x, y) in ORB:
        d = dist(x, y)
        if (x, y) in RIM:
            c = CYAN if (x, y) in RIM_GLINT else GOLD
        elif d <= 2.2:
            c = NEBULA                       # 珠心星云
        elif x < 7 and y < 7:
            c = SPACE_HI                     # 左上受光侧
        elif d >= RADIUS - 1.6:
            c = SPACE_DEEP                   # 贴近珠缘压深，圆才「鼓」得起来
        else:
            c = SPACE
        px[x, y] = c + (255,)

    star(px, 7, 7, 2)                        # 正中一枚小十字星（整星 5px）
    for (x, y) in ((11, 6), (4, 10), (10, 11)):   # 三枚散点（避开四正，免得和星芒撞）
        if (x, y) in ORB and (x, y) not in RIM:
            px[x, y] = STAR + (255,)
    return im


def build_ornament():
    """紧凑十字星簇：中央一枚大十字星（臂 3）+ 四斜位四枚小十字星（臂 1）+ 斜向细芒。

    ★ 位置全部按「伸出去的最外那一格也要落在宝珠半径 6.2 以内」反推：
      中央星臂 3 ⇒ 尖端半径 3.5；四枚小星放斜位 (4,4)/(10,4)/(4,10)/(10,10)
      （中心半径 4.24），臂 1 ⇒ 尖端半径 5.00。最大 5.00 < 6.2 ⇒ 圆内全部盖得住。
    ★ 小星必须放**斜位**、臂长必须 ≤1：12.66 第一版放四正 (5,5)/(10,5)/(5,10)/(10,10)
      臂 2，五枚星的横臂在 y=5/y=10 上首尾相连，竖臂在 x=5/x=10 上首尾相连，
      整簇糊成一个「#」字。斜位 + 短臂就没有对齐问题。
    """
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()

    def put(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = c + (255,)

    # 细芒：星心 -> 四枚小星，1px 斜线（压在星下面，先画）
    for (tx, ty) in ((4, 4), (10, 4), (4, 10), (10, 10)):
        steps = max(abs(tx - 7), abs(ty - 7))
        for s in range(2, steps):
            x = round(7 + (tx - 7) * s / steps)
            y = round(7 + (ty - 7) * s / steps)
            put(x, y, SPACE_HI)

    for (cx, cy) in ((4, 4), (10, 4), (4, 10), (10, 10)):
        star(px, cx, cy, 1, inner=GOLD_HI, outer=GOLD)
    star(px, 7, 7, 3)
    return im


def main():
    focus = build_focus()
    focus.save(os.path.join(ITEM_TEX, "focus_teleport.png"))
    print("item tex -> focus_teleport.png", focus.size, "| orb cells:", len(ORB))

    orn = build_ornament()
    orn.save(os.path.join(ITEM_TEX, "focus_teleport_orn.png"))
    print("item tex -> focus_teleport_orn.png", orn.size)

    out = os.path.join(ROOT, "tools/preview")
    for name, im in (("focus_teleport", focus), ("focus_teleport_orn", orn)):
        im.resize((16 * 14, 16 * 14), Image.NEAREST).save(
            os.path.join(out, "teleport_%s.png" % name))

    # 合成图：ornament（layer0）在下、宝珠（layer1）在上 —— 就是物品图标实际长什么样
    comp = Image.alpha_composite(orn, focus)
    sheet = Image.new("RGBA", (16 * 3 + 2, 16), (24, 24, 30, 255))
    for i, im2 in enumerate((focus, orn, comp)):
        sheet.paste(im2, (i * 17, 0), im2)
    sheet.resize((sheet.width * 12, sheet.height * 12), Image.NEAREST).save(
        os.path.join(out, "teleport_composite.png"))
    print("preview -> tools/preview/teleport_composite.png (圆形宝珠 | ornament | 合成)")

    # 断言：ornament 的每个不透明像素都落在宝珠圆盘内（否则图标会露尖角）
    leak = [(x, y) for y in range(16) for x in range(16)
            if orn.getpixel((x, y))[3] > 0 and (x, y) not in ORB]
    print("ornament 露在圆外的像素:", leak if leak else "无（图标一定是纯圆）")


if __name__ == "__main__":
    main()
