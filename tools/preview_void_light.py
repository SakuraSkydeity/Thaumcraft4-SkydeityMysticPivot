#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
矿机光效离线预览（第一百九十七批重画：四根圆管，站在四块玻璃上）。

<p>画 {@code XuanshuVoidMiningRenderer} 现在画的东西：<b>四道深蓝圆光</b>，
站在塔顶那一圈淡蓝玻璃里、绕塔心四个侧面各一块（现在 {@code dy = 8} 那层）的
<b>上表面</b>上，一路冲到 36 格。</p>

<p>左边侧视（看四根各从哪块玻璃起、多高、多粗），右边俯视（看它们是不是落在
四块露顶玻璃上、有没有出格）。<b>数据源是 Java 常量本身</b>：本脚本直接解析
{@code VoidAltarBlueprint.java} 里的 {@code STATES} / {@code CELLS}，落点规则与
{@code VoidMiningStructure#beamAnchors} 是同一条（从低往高，第一个恰好四块露顶玻璃的层）；
渲染参数在下面「渲染器常量」一段，与 {@code XuanshuVoidMiningRenderer} 一一对应。

用法：

    python tools/preview_void_light.py [out.png]
"""

import math
import os
import re
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "src/main/java/skydeity/mysticpivot/VoidAltarBlueprint.java")
OUT_DIR = os.path.join(ROOT, "tools/preview")

# ---------------------------------------------------------------------------
# 一、渲染器常量（与 XuanshuVoidMiningRenderer 严格对应）
# ---------------------------------------------------------------------------

BEAM_TOP = 36.0        # 绝对高度（相对矿机底面）
BEAM_RADIUS = 0.09     # 半径：全宽 0.18 格
BEAM_SIDES = 12        # 绕轴切 12 份
BEAM_ALPHA = 0.9       # α = 0.9（管壁正反两面都画，留一点透才有厚度）

# 第一百九十八批：光柱根上的纺锤形光苞（用户红笔那个尖头形状）
LENS_RADIUS = 0.55     # 最粗处半径（四根最近相距 1.41 格，再粗就互相穿插）
LENS_HEIGHT = 8.0      # 从根部往上 8 格
LENS_ALPHA = 0.55      # 比光柱淡一档
LENS_RINGS = 12        # 沿轴 12 层，半径 = R·sin(πt)
LENS_SIDES = 16        # 一圈 16 段

COLOR = (0x1C, 0x33, 0xC7)  # 0.11 / 0.20 / 0.78（深蓝）

GLASS = "minecraft:light_blue_stained_glass"

# ---------------------------------------------------------------------------
# 二、读 Java 常量（真源）
# ---------------------------------------------------------------------------

COLORS = {
    "minecraft:chiseled_quartz_block": (232, 226, 214),
    "minecraft:quartz_bricks": (228, 222, 210),
    "minecraft:quartz_pillar": (235, 229, 218),
    "minecraft:quartz_slab": (222, 216, 205),
    "minecraft:quartz_stairs": (226, 220, 208),
    "minecraft:blue_wool": (58, 62, 158),
    "minecraft:light_blue_stained_glass": (150, 206, 226),
    "minecraft:light_blue_stained_glass_pane": (160, 212, 232),
    "minecraft:sea_lantern": (198, 232, 216),
}


def read_blueprint():
    """从生成的 Java 里取出状态表与格子表（不含空气）。"""
    with open(JAVA, encoding="utf-8") as f:
        text = f.read()
    states = re.findall(r'^\s*"(minecraft:[^"]+)",\s*$', text, re.M)
    block = re.search(r"int\[\] CELLS = \{(.*?)\n    \};", text, re.S).group(1)
    block = re.sub(r"//[^\n]*", "", block)
    nums = [int(v) for v in re.findall(r"-?\d+", block)]
    cells = [(nums[i], nums[i + 1], nums[i + 2], nums[i + 3])
             for i in range(0, len(nums), 4)]
    return states, cells


def color_of(spec):
    return COLORS.get(spec.split("[")[0], (140, 140, 150))


def anchors(states, cells):
    """四根光的落点：与 VoidMiningStructure#beamAnchors 同一条规则。

    「露顶玻璃」= 淡蓝玻璃且上面那格是空气；按 dy 分组，取**从低往高第一个恰好四块**的层，
    每块的中心 (x+0.5, dy+1, z+0.5)。凑不出四块就返回空表（Java 那侧会退回中轴一根）。
    """
    filled = {(c[0], c[1], c[2]) for c in cells}
    layers = {}
    for (dx, dy, dz, k) in cells:
        if states[k] != GLASS:
            continue
        if (dx, dy + 1, dz) in filled:
            continue
        layers.setdefault(dy, []).append((dx, dz))
    for dy in sorted(layers):
        if len(layers[dy]) == 4:
            return [(dx + 0.5, dy + 1.0, dz + 0.5) for (dx, dz) in layers[dy]], dy
    return [], None


# ---------------------------------------------------------------------------
# 三、出图
# ---------------------------------------------------------------------------

BG = (18, 22, 34)
INK = (232, 236, 246)
DIM = (138, 148, 172)

FONT_PATHS = ("C:/Windows/Fonts/msyh.ttc", "C:/Windows/Fonts/simhei.ttf")


def font(size, bold=False):
    for path in FONT_PATHS:
        if os.path.exists(path):
            try:
                return ImageFont.truetype(path, size, index=1 if bold else 0)
            except Exception:
                try:
                    return ImageFont.truetype(path, size)
                except Exception:
                    continue
    return ImageFont.load_default()


def draw_side(size, ox, oy, cell, states, cells, spots):
    """侧视：x 为横、y 为竖，所有 z 压平。四根光按各自 x 画成一条竖带。"""
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)

    def px(x):
        return ox + (x + 3.0) * cell

    def py(y):
        return oy + (BEAM_TOP + 1.6 - y) * cell

    for (dx, dy, dz, k) in cells:
        d.rectangle([px(dx), py(dy + 1), px(dx + 1) - 1, py(dy) - 1],
                    fill=color_of(states[k]) + (150,))

    # 四根圆管：侧看就是一条定宽竖带，起点各在各的玻璃顶面
    r = max(1.0, BEAM_RADIUS * cell)
    for (bx, by, bz) in spots:
        # 先画光苞（纺锤），再画管子，管子压在光苞中间 —— 与游戏里的叠放顺序一致
        steps = 48
        outline = []
        for i in range(steps + 1):
            t = i / steps
            rr = LENS_RADIUS * math.sin(math.pi * t)
            outline.append((px(bx - rr), py(by + LENS_HEIGHT * t)))
        for i in range(steps, -1, -1):
            t = i / steps
            rr = LENS_RADIUS * math.sin(math.pi * t)
            outline.append((px(bx + rr), py(by + LENS_HEIGHT * t)))
        d.polygon(outline, fill=COLOR + (int(255 * LENS_ALPHA),))
        d.rectangle([px(bx) - r, py(BEAM_TOP), px(bx) + r, py(by)],
                    fill=COLOR + (int(255 * BEAM_ALPHA),))
    return layer, px, py


def draw_top(size, ox, oy, cell, states, cells, axis_x, axis_z, top_layer, spots):
    """俯视：x 为横、z 为竖。画落点那一层 + 四根光的截面圆。"""
    layer = Image.new("RGBA", size, (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)

    def px(x):
        return ox + (x + 3.0) * cell

    def pz(z):
        return oy + (z + 3.0) * cell

    # 整座塔的投影轮廓（浅）
    for (dx, dy, dz, k) in cells:
        d.rectangle([px(dx), pz(dz), px(dx + 1) - 1, pz(dz + 1) - 1],
                    fill=color_of(states[k]) + (40,))
    # 落点那一层
    for (dx, dy, dz, k) in cells:
        if dy == top_layer:
            d.rectangle([px(dx), pz(dz), px(dx + 1) - 1, pz(dz + 1) - 1],
                        fill=color_of(states[k]) + (225,))

    # 中轴（对照用）
    d.line([px(axis_x), pz(axis_z) - 7, px(axis_x), pz(axis_z) + 7], fill=(120, 128, 150))
    d.line([px(axis_x) - 7, pz(axis_z), px(axis_x) + 7, pz(axis_z)], fill=(120, 128, 150))

    # 四根光的截面：正圆（这就是「不再是十字」的意思）；外面再套一圈更淡的光苞截面
    r = max(4.0, BEAM_RADIUS * cell)
    lr = max(6.0, LENS_RADIUS * cell)
    for (bx, by, bz) in spots:
        d.ellipse([px(bx) - lr, pz(bz) - lr, px(bx) + lr, pz(bz) + lr],
                  fill=COLOR + (int(255 * LENS_ALPHA),),
                  outline=(190, 205, 255, 110))
    for (bx, by, bz) in spots:
        d.ellipse([px(bx) - r, pz(bz) - r, px(bx) + r, pz(bz) + r],
                  fill=COLOR + (255,), outline=(255, 255, 255, 120))
    return layer


def main(argv):
    out = argv[1] if len(argv) > 1 else os.path.join(OUT_DIR, "void_light.png")
    states, cells = read_blueprint()

    xs = [c[0] for c in cells]
    zs = [c[2] for c in cells]
    ys = [c[1] for c in cells]
    min_x, max_x = min(xs), max(xs)
    min_z, max_z = min(zs), max(zs)
    axis_x = min_x + (max_x - min_x + 1) / 2.0
    axis_z = min_z + (max_z - min_z + 1) / 2.0
    height = max(ys) - min(ys) + 1

    spots, layer_dy = anchors(states, cells)
    if layer_dy is None:
        spots, layer_dy = [(axis_x, float(height), axis_z)], max(ys)

    W, H = 1180, 900
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    f_title = font(30, True)
    f_sub = font(15)
    f_lbl = font(13, True)
    f_tick = font(11)

    d.text((44, 22), "玄枢虚空水晶塔 · 四道深蓝圆光 + 根上光苞（第 198 批）", font=f_title, fill=INK)
    d.text((44, 62),
           "塔 %d 格高 / %d 格　光柱：%d 根、半径 %.2f（全宽 %.2f 格）、绕轴 %d 段　顶端 %.0f"
           % (height, len(cells), len(spots), BEAM_RADIUS, BEAM_RADIUS * 2, BEAM_SIDES, BEAM_TOP),
           font=f_sub, fill=DIM)
    d.text((44, 84),
           "落点 = 塔顶 dy=%d 那层露顶的淡蓝玻璃（每块的上表面中心），四根各站一块；"
           "光苞 r=%.2f、高 %.0f 格、α %.2f" % (layer_dy, LENS_RADIUS, LENS_HEIGHT, LENS_ALPHA),
           font=f_sub, fill=DIM)

    # ---- 左：侧视 ----
    cell_s = 15
    ox, oy = 130, 132
    layer, px, py = draw_side((W, H), ox, oy, cell_s, states, cells, spots)
    img = Image.alpha_composite(img.convert("RGBA"), layer).convert("RGB")
    d = ImageDraw.Draw(img)
    d.text((ox - 8, oy - 26), "侧视（沿 z 压平）", font=f_lbl, fill=INK)
    for y in range(0, int(BEAM_TOP) + 1, 4):
        yy = py(y)
        d.line([ox - 14, yy, 560, yy], fill=(38, 44, 60))
        d.text((ox - 42, yy - 6), "%d" % y, font=f_tick, fill=DIM)
    top_spot = max(spots, key=lambda s: s[1])
    d.text((px(top_spot[0]) + 14, py(BEAM_TOP) - 14), "顶端 %.0f" % BEAM_TOP,
           font=f_tick, fill=COLOR)
    d.text((px(top_spot[0]) + 14, py(top_spot[1]) - 16),
           "底座 %.0f（玻璃顶面）" % top_spot[1], font=f_tick, fill=COLOR)

    # ---- 右：俯视 ----
    cell_t = 32
    tx, tz = 700, 200
    layer2 = draw_top((W, H), tx, tz, cell_t, states, cells, axis_x, axis_z, layer_dy, spots)
    img = Image.alpha_composite(img.convert("RGBA"), layer2).convert("RGB")
    d = ImageDraw.Draw(img)
    d.text((tx - 6, tz - 34), "俯视（dy=%d 那一层 + 四根光的截面）" % layer_dy, font=f_lbl, fill=INK)
    foot = tz + (max_z - min_z + 2) * cell_t + 12
    d.text((tx - 6, foot), "深蓝圆 = 光柱截面（正圆，不再是两条交叉的面片）；外面那圈淡的是光苞",
           font=f_tick, fill=DIM)
    d.text((tx - 6, foot + 20), "四个圆各压在一块浅蓝玻璃上；灰色十字 = 塔中轴（只是对照线）",
           font=f_tick, fill=DIM)

    # ---- 底部说明 ----
    fy = H - 112
    d.text((44, fy), "第 198 批按「光束靠近底部位子画一个我红色一样的」在光柱根上加了纺锤形光苞："
                     "半径沿高度按 sin(πt) 走，上下收尖、中间鼓起。",
           font=f_sub, fill=(198, 204, 216))
    d.text((44, fy + 24), "半径 0.55 是贴着四个落点定的：最近两根相距 1.41 格，再粗两枚光苞就会互相穿插。",
           font=f_sub, fill=DIM)
    d.text((44, fy + 48), "★ 渲染类型仍是 debugStructureQuads（只写颜色、不写深度）："
                          "否则会把随后画的玻璃、水「挖掉」一块。", font=f_sub, fill=DIM)
    d.text((44, fy + 72), "本图由 tools/preview_void_light.py 从 VoidAltarBlueprint.java "
                          "的常量直接算出，与游戏里是同一组数字。", font=f_tick, fill=DIM)

    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)
    print("出图：%s（%d 根，dy=%s）" % (out, len(spots), layer_dy))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
