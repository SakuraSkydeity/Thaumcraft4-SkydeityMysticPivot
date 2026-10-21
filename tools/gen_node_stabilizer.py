#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""玄枢节点稳定器（xuanshu_node_stabilizer）的方块贴图（第 61 批 / README 12.66）。

用户第 4 条：「创建一个新物品玄枢节点稳定器，就和以 tc4 的高级节点稳定器为基础，
额外可以缓慢增加灵气节点元素的数字，最大到 64」。

这台机器立在**灵气节点正下方**，把节点当作「高级节点稳定器」那样锁住（lockStrength = 2），
并在每 5 秒把节点当前的每一项要素 +1，涨到 64 封顶。

贴图刻意沿用本体节点稳定器的「金属机匣」观感：深紫机匣 + 金线框，
顶面多一枚发光核（正对上方节点的「夹持口」）。两张均为 16×16。
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEX_DIR = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/block")
PREVIEW_DIR = os.path.join(ROOT, "tools/preview")

# 机匣配色：深紫为主，金为饰线（与玄枢系列深紫 / 金一致）。
BASE_HI = (126, 100, 182)   # v=0（顶）
BASE_LO = (62, 46, 96)      # v=15（底）
DK = (38, 28, 60)           # 描边 / 内凹
GOLD = (240, 206, 124)      # 金饰线
GOLD_DK = (176, 138, 66)    # 金饰线暗角
CORE = (196, 158, 242)      # 顶面发光核
CORE_HI = (232, 214, 255)   # 核心


def _grad(y):
    t = y / 15.0
    return tuple(int(round(BASE_HI[i] + (BASE_LO[i] - BASE_HI[i]) * t)) for i in range(3))


def _base():
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    for y in range(16):
        c = _grad(y) + (255,)
        for x in range(16):
            px[x, y] = c
    # 1px 描边
    for i in range(16):
        px[i, 0] = DK + (255,)
        px[i, 15] = DK + (255,)
        px[0, i] = DK + (255,)
        px[15, i] = DK + (255,)
    return im, px


def side():
    """机匣侧板：金线方框 + 四角金钉。"""
    im, px = _base()
    for i in range(3, 13):
        px[i, 3] = GOLD + (255,)
        px[i, 12] = GOLD + (255,)
        px[3, i] = GOLD + (255,)
        px[12, i] = GOLD + (255,)
    for (x, y) in ((3, 3), (12, 3), (3, 12), (12, 12)):
        px[x, y] = GOLD_DK + (255,)
    # 内凹的暗格（让方框里读成「机芯」而不是一块平板）
    for y in range(5, 11):
        for x in range(5, 11):
            px[x, y] = DK + (255,)
    for y in range(6, 10):
        for x in range(6, 10):
            px[x, y] = tuple(min(255, v + 14) for v in _grad(y)) + (255,)
    return im


def top():
    """顶板：更大一圈的金框 + 正中的发光核（夹持口）。"""
    im, px = _base()
    for i in range(2, 14):
        px[i, 2] = GOLD + (255,)
        px[i, 13] = GOLD + (255,)
        px[2, i] = GOLD + (255,)
        px[13, i] = GOLD + (255,)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px[x, y] = GOLD_DK + (255,)
    for y in range(5, 11):
        for x in range(5, 11):
            px[x, y] = DK + (255,)
    for y in range(6, 10):
        for x in range(6, 10):
            px[x, y] = CORE + (255,)
    for (x, y) in ((7, 7), (8, 7), (7, 8), (8, 8)):
        px[x, y] = CORE_HI + (255,)
    return im


def main():
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    s = side()
    t = top()
    sp = os.path.join(TEX_DIR, "xuanshu_node_stabilizer.png")
    tp = os.path.join(TEX_DIR, "xuanshu_node_stabilizer_top.png")
    s.save(sp)
    t.save(tp)
    print("side ->", os.path.relpath(sp, ROOT))
    print("top  ->", os.path.relpath(tp, ROOT))

    # 预览：两张并排 → ×14
    sheet = Image.new("RGBA", (33, 16), (0, 0, 0, 0))
    sheet.alpha_composite(s, (0, 0))
    sheet.alpha_composite(t, (17, 0))
    sheet.resize((33 * 14, 16 * 14), Image.NEAREST).save(
        os.path.join(PREVIEW_DIR, "node_stabilizer_tex.png"))
    print("preview ->", os.path.relpath(
        os.path.join(PREVIEW_DIR, "node_stabilizer_tex.png"), ROOT))


if __name__ == "__main__":
    main()
