#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""南北斗枢玄星盘 + 「星斗十二座」的资源生成（第一百一十五批：两组星都铺满 3×4）。

<h2>两形态</h2>
<p>用户：「多方块后中间的圆不要，然后六星和七星占满整个 3×4，然后另一个就浮在空中，
你设计好，然后清晰一点。金线改为七星和六星的连线顺序」。</p>
<ul>
  <li><b>七星期</b>（{@code chart=seven}）：北斗七星画在<b>盘面贴图</b>里
      （{@code star_plate_r{r}c{c}}），南斗六星浮在空中（渲染器画，并沿六星连线拉金线）；</li>
  <li><b>六星期</b>（{@code chart=six}）：南斗六星画在盘面上（{@code star_six_r{r}c{c}}），
      北斗七星浮在空中。</li>
</ul>
<p>两套星图都<b>铺满整张 96×128</b>（三列四行的每一行每一列都有星），不再是北半 / 南半各一半。
盘心那圈「阵心小徽」删除（用户：中间的圆不要）。</p>

<h2>清晰度</h2>
<p>贴图是 96×128 铺在 3×4 格上：一格 32px，而方块一个面只有 16 texel，也就是**两倍分辨率**。
所以 1px 的线只有半个 texel、会糊。这一批统一放大：星是 3×3 亮芯 + 四向 4px 芒（9px 见方），
连线是 2px（= 正一个 texel），对比度也拉开了。</p>
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot")
NS = "skydeitymysticpivot"

# 每格贴图边长：32 = 2 倍分辨率（清晰）；16 = 原版分辨率（省体积）。
# 整阵图按 3×4 格排版，所以画布始终是 (3*TILE, 4*TILE)。
# ★ 第一百二十八批用户要求压到 16；32×32 的原图备份在 tools/work/tex-backup-32px/。
TILE = 32
B = os.path.join(ASSETS, "textures/block")

# ---- 仙家配色 ----
JADE = (118, 154, 138)
JADE_L = (158, 192, 172)
JADE_LL = (196, 222, 204)
JADE_D = (84, 116, 102)
INK = (22, 28, 28)
INK_L = (38, 46, 44)
INK_D = (14, 17, 18)
GOLD = (188, 150, 70)
GOLD_L = (232, 202, 124)
GOLD_D = (120, 90, 40)
CINNABAR = (156, 56, 42)
STAR_W = (255, 248, 216)
STAR_W_DIM = (206, 172, 96)
STAR_N = (222, 238, 252)
STAR_N_DIM = (150, 176, 202)

# ---- 两张星图的星位（96×128，跨格铺满整个阵） ----
# ★ 第一百二十一批：按用户要求「盘面改回原来的」——星图回到这一版跨格的斜排，
#   不再强行让每颗星落在自己那一格里。物品则摆到「图案里对应的那颗星」上（见
#   StarDialRenderer.SEATS），所以图上与物上是一一对应的。
# 北斗七星（下标 0~6 = 天枢 天璇 天玑 天权 玉衡 开阳 摇光）：
SEVEN_STARS = [(16, 102), (22, 70), (50, 78), (56, 108), (74, 94), (86, 62), (84, 24)]
SEVEN_LINKS = [(0, 1), (1, 2), (2, 3), (3, 0), (2, 4), (4, 5), (5, 6)]
# 南斗六星（下标 0~5 = 天府 天梁 天机 天同 天相 七杀）：
SIX_STARS = [(16, 58), (44, 20), (58, 52), (26, 80), (76, 62), (88, 108)]
SIX_LINKS = [(0, 1), (1, 2), (2, 3), (3, 0), (2, 4), (4, 5)]


# ------------------------------------------------------------------ 工具
def jitter(c, rng, amp=3):
    return tuple(max(0, min(255, v + rng.randint(-amp, amp))) for v in c)


def lerp(a, b, t):
    return tuple(round(a[i] + (b[i] - a[i]) * t) for i in range(3))


def blur_mix(c1, c2, t):
    return lerp(c1, c2, t)


def rect(px, x0, y0, x1, y1, c, a=255, size=16):
    for y in range(y0, y1):
        for x in range(x0, x1):
            if 0 <= x < size and 0 <= y < size:
                px[x, y] = c + (a,)


def put(px, x, y, c, W, H):
    if 0 <= x < W and 0 <= y < H:
        px[x, y] = c + (255,)



def big_star(px, cx, cy, core, arm, dim, W, H):
    """清楚版星点：3×3 亮芯 + 四向 4px 芒 + 四角暗晕（9px 见方 = 4.5 texel）。"""
    for dy in (-1, 0, 1):
        for dx in (-1, 0, 1):
            put(px, cx + dx, cy + dy, core, W, H)
    for k in range(2, 6):
        for (dx, dy) in ((k, 0), (-k, 0), (0, k), (0, -k)):
            put(px, cx + dx, cy + dy, arm, W, H)
    for (dx, dy) in ((2, 2), (-2, 2), (2, -2), (-2, -2)):
        put(px, cx + dx, cy + dy, dim, W, H)


def hair_line(px, p0, p1, color, W, H):
    """1px 细线（32×32 的小图上，2px 的 thick_line 太粗）。"""
    x0, y0 = p0
    x1, y1 = p1
    steps = max(1, int(max(abs(x1 - x0), abs(y1 - y0)) * 2))
    for k in range(steps + 1):
        x = int(x0 + (x1 - x0) * k / steps)
        y = int(y0 + (y1 - y0) * k / steps)
        put(px, x, y, color, W, H)


def thick_line(px, p0, p1, main, core, W, H):
    """2px 连线（正好一个 texel 宽）：先铺两行 main，再在上沿压一行 core。"""
    x0, y0 = p0
    x1, y1 = p1
    steps = max(1, int(max(abs(x1 - x0), abs(y1 - y0)) * 2))
    for k in range(steps + 1):
        x = int(x0 + (x1 - x0) * k / steps)
        y = int(y0 + (y1 - y0) * k / steps)
        put(px, x, y, main, W, H)
        put(px, x, y + 1, main, W, H)
        put(px, x, y, core, W, H)



# ------------------------------------------------------------------ 阵眼（默认方块顶面）
def dial_top():
    """未成型方块的顶面（第一百二十二批）：一小片星空，南北斗缩到角落。

    <p>用户：「顶面图的南北斗小一点，然后整体背景像星辰天空的背景」。所以底子从「墨玉」
    换成<b>深空</b>：靛蓝渐变 + 三团极淡星云 + 五十来颗碎星，南北斗缩成两小簇
    分居左上 / 右下，1px 连线，外圈一道细金边。整张贴图会沿着方块平铺，所以星点随机、没有接缝感。</p>
    """
    S = 32
    im = Image.new("RGBA", (S, S))
    px = im.load()
    rng = random.Random(53)

    # 一、深空底：左上偏靛、右下偏黑
    for y in range(S):
        for x in range(S):
            t = (x + y) / (2.0 * S)
            px[x, y] = jitter(lerp((20, 24, 42), (7, 9, 17), t), rng, 2) + (255,)

    # 二、星云：三团极淡的蓝紫，给背景一点层次
    for (nx, ny, rad, col, amt) in ((9, 9, 13, (46, 54, 100), 0.16),
                                    (23, 21, 14, (44, 36, 78), 0.14),
                                    (26, 7, 10, (30, 46, 76), 0.10)):
        for y in range(S):
            for x in range(S):
                d = math.hypot(x - nx, y - ny)
                if d < rad:
                    px[x, y] = blur_mix(px[x, y][:3], col, (1.0 - d / rad) ** 2 * amt) + (255,)

    # 三、星海：碎星
    for _ in range(70):
        x, y = rng.randrange(S), rng.randrange(S)
        px[x, y] = rng.choice([(126, 136, 168), (166, 178, 208),
                               (206, 214, 236), (96, 104, 132)]) + (255,)
    for _ in range(6):
        x, y = rng.randrange(2, S - 2), rng.randrange(2, S - 2)
        px[x, y] = (244, 247, 255) + (255,)
        for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            px[x + dx, y + dy] = (168, 180, 212) + (255,)

    # 四、细金边 + 四角铆点
    for i in range(1, S - 1):
        px[i, 1] = GOLD_D + (255,)
        px[i, S - 2] = GOLD_D + (255,)
        px[1, i] = GOLD_D + (255,)
        px[S - 2, i] = GOLD_D + (255,)
    for (ax, ay) in ((1, 1), (S - 2, 1), (1, S - 2), (S - 2, S - 2)):
        px[ax, ay] = GOLD + (255,)

    # 五、南北斗：缩成两小簇（1px 芯 + 1px 芒）
    # 北斗：一小撮，只占右上 8×6
    north = [(4, 9), (6, 6), (9, 8), (5, 12), (8, 13), (11, 10), (12, 6)]
    for (a, b) in ((0, 1), (1, 2), (2, 3), (3, 0), (2, 4), (4, 5), (5, 6)):
        hair_line(px, north[a], north[b], (78, 60, 28), S, S)
    for (x, y) in north:
        put(px, x, y, STAR_W, S, S)

    # 南斗：一小撮，只占右下 7×6
    south = [(21, 23), (23, 20), (25, 22), (22, 26), (24, 27), (27, 24)]
    for (a, b) in ((0, 1), (1, 2), (2, 3), (3, 0), (2, 4), (4, 5)):
        hair_line(px, south[a], south[b], (62, 72, 90), S, S)
    for (x, y) in south:
        put(px, x, y, STAR_N, S, S)
    return im


def side_field(px, rng):
    """台阶侧面的**可见区**：贴图下半 8 行（第 8~15 行）。

    <p>★ 原版 {@code minecraft:block/slab} 的四个侧面 uv 是 {@code [0,8,16,16]}——
    下半砖只露**贴图下半**这 8 行，第 8 行贴面上沿、第 15 行贴面下沿。</p>
    """
    for y in range(8, 16):
        t = (y - 8) / 7.0
        c = lerp(JADE_L, JADE_D, 0.30 + 0.55 * t)
        for x in range(16):
            px[x, y] = jitter(c, rng, 3) + (255,)


def side_frame(px):
    """鎏金框 + 内凹回纹面板（用户给的那张「正常方块侧面」就是这个样子）。"""
    rect(px, 0, 8, 16, 9, GOLD_L)      # 压顶高光
    rect(px, 0, 9, 16, 10, GOLD_D)     # 压顶暗边
    # 框：左右立柱 + 上下横梁
    for y in range(10, 14):
        px[1, y] = GOLD_D + (255,)
        px[2, y] = GOLD + (255,)
        px[13, y] = GOLD + (255,)
        px[14, y] = GOLD_D + (255,)
    for x in range(1, 15):
        px[x, 10] = GOLD + (255,)
        px[x, 14] = GOLD_D + (255,)
    # 面板内凹：比底子略暗
    for y in range(11, 14):
        for x in range(3, 13):
            px[x, y] = blur_mix(px[x, y][:3], JADE_D, 0.30) + (255,)
    # 四角铆点
    for (cx, cy) in ((2, 10), (13, 10), (2, 14), (13, 14)):
        px[cx, cy] = GOLD_L + (255,)
    rect(px, 0, 15, 16, 16, JADE_D)     # 下沿阴影


def dial_side():
    """默认（未成型）方块侧面：青玉 + 鎏金框 + 内凹面板里一道玉光。"""
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    side_field(px, random.Random(21))
    side_frame(px)
    for x in range(5, 11):
        px[x, 12] = JADE_LL + (255,)
        px[x, 13] = blur_mix(JADE_LL, JADE_D, 0.55) + (255,)
    return im


def dial_bottom():
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    rng = random.Random(22)
    for y in range(16):
        for x in range(16):
            px[x, y] = jitter(INK, rng, 2) + (255,)
    for x in range(16):
        px[x, 0] = INK_D + (255,)
        px[x, 15] = INK_D + (255,)
    for y in range(16):
        px[0, y] = INK_D + (255,)
        px[15, y] = INK_D + (255,)
    return im


# ------------------------------------------------------------------ 成型块侧面
def seat_side(north):
    """成型块侧面：同一副鎏金框，面板里换成一条三星连珠（北暖金 / 南冷银）。"""
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    side_field(px, random.Random(31 if north else 32))
    side_frame(px)

    core = STAR_W if north else STAR_N
    arm = (198, 168, 96) if north else (150, 172, 190)
    line = blur_mix(px[8, 12][:3], GOLD, 0.55) + (255,)
    for x in range(4, 13):
        px[x, 12] = line
    for sx in (5, 8, 11):
        px[sx, 12] = core + (255,)
        for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            if 8 <= 12 + dy <= 14:
                px[sx + dx, 12 + dy] = arm + (255,)
    return im


# ------------------------------------------------------------------ 整阵星图（96×128）
def star_chart(kind):
    """一张 96×128 的盘面：墨玉底 + 鎏金细框 + 散星 + 一组星座（seven / six）。

    <p>画布 96×128 = 3 列 × 4 行，每格 32px。十二格各自只取自己那一块 UV，
    拼起来就是这一整张。**盘心不再画圈**（第一百一十五批：中间的圆不要）。</p>
    """
    stars, links = (SEVEN_STARS, SEVEN_LINKS) if kind == "seven" else (SIX_STARS, SIX_LINKS)
    warm = kind == "seven"
    core = STAR_W if warm else STAR_N
    arm = STAR_W_DIM if warm else STAR_N_DIM
    dim = (150, 124, 70) if warm else (96, 112, 128)
    line_main = GOLD_D
    line_core = (150, 118, 56) if warm else (108, 124, 140)

    W, H = 96, 128
    im = Image.new("RGBA", (W, H))
    px = im.load()
    rng = random.Random(77 if warm else 78)
    cx, cy = W / 2.0, H / 2.0
    for y in range(H):
        for x in range(W):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy) / 70.0
            px[x, y] = jitter(lerp(INK_L, INK, min(1.0, d)), rng, 3) + (255,)
    # 四角略沉，中间略透青玉色，免得整张是一块死黑
    for y in range(H):
        for x in range(W):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d < 58:
                t = 1.0 - d / 58.0
                px[x, y] = blur_mix(px[x, y][:3], JADE_D, 0.14 * t) + (255,)

    # 鎏金细框 + 四角铆点
    for x in range(3, W - 3):
        px[x, 3] = GOLD_D + (255,)
        px[x, H - 4] = GOLD_D + (255,)
    for y in range(3, H - 3):
        px[3, y] = GOLD_D + (255,)
        px[W - 4, y] = GOLD_D + (255,)
    for (ax, ay) in ((3, 3), (W - 4, 3), (3, H - 4), (W - 4, H - 4)):
        px[ax, ay] = GOLD + (255,)

    # 连线先画（2px），再把星压上去
    for (a, b) in links:
        thick_line(px, stars[a], stars[b], line_main, line_core, W, H)
    for (x, y) in stars:
        big_star(px, x, y, core, arm, dim, W, H)

    # 散星：填满十二格的空处（比主星暗得多，不抢戏）
    for _ in range(30):
        x, y = rng.randint(7, W - 8), rng.randint(7, H - 8)
        if math.hypot(x - cx, y - cy) < 18:
            continue
        if any(abs(x - sx) < 6 and abs(y - sy) < 6 for (sx, sy) in stars):
            continue
        px[x, y] = (104, 104, 94) + (255,)
        if rng.random() < 0.3:
            px[x, y] = (168, 162, 132) + (255,)
            for (dx, dy) in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                put(px, x + dx, y + dy, (78, 78, 70), W, H)
    return im


def star_glow():
    """浮空星点的贴图：一枚亮芯 + 四向芒 + 柔和光晕（32×32，圆外全透明）。"""
    S = 32
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    px = im.load()
    cx = cy = 16.0
    for y in range(S):
        for x in range(S):
            dx, dy = abs(x + 0.5 - cx), abs(y + 0.5 - cy)
            d = math.hypot(dx, dy)
            if d <= 3.0:
                a = 255
            else:
                a = int(240 * max(0.0, 1.0 - (d - 3.0) / 8.0) ** 2.2)
            # 四向芒
            if (dx < 0.9 or dy < 0.9) and max(dx, dy) < 14.0:
                a = max(a, int(180 * (1.0 - max(dx, dy) / 14.0)))
            if a > 0:
                px[x, y] = (255, 252, 236, a)
    return im


# ------------------------------------------------------------------ 模型 / 状态
def slab_model(top, side_tex, bottom_tex="star_dial_bottom"):
    """原版台阶模型：默认方块用，只换三张贴图。"""
    side = NS + ":block/" + side_tex
    return {
        "parent": "minecraft:block/slab",
        "render_type": "minecraft:cutout",
        "textures": {
            "top": NS + ":block/" + top,
            "side": side,
            "bottom": NS + ":block/" + bottom_tex,
            "particle": side,
        },
    }


def blockstate():
    """13 role × 3 type × 2 星象 = 78 条。实际只出现 {@code type=bottom}，另两条兜底。"""
    variants = {}
    for kind in ("bottom", "top", "double"):
        for chart, prefix in (("seven", "star_plate"), ("six", "star_six")):
            variants["role=none,type=%s,chart=%s" % (kind, chart)] = {"model": NS + ":block/star_dial"}
            for row in range(4):
                for col in range(3):
                    variants["role=r%dc%d,type=%s,chart=%s" % (row, col, kind, chart)] = {
                        "model": "%s:block/%s_r%dc%d" % (NS, prefix, row, col)}
    return {"variants": variants}


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(obj, fh, ensure_ascii=False, indent=2)
        fh.write("\n")


def main():
    os.makedirs(B, exist_ok=True)

    top = dial_top()
    if TILE != top.width:
        top = top.resize((TILE, TILE), Image.LANCZOS)
    top.save(os.path.join(B, "star_dial_top.png"), optimize=True)
    dial_side().save(os.path.join(B, "star_dial_side.png"))
    dial_bottom().save(os.path.join(B, "star_dial_bottom.png"))
    seat_side(True).save(os.path.join(B, "star_seat_north_side.png"))
    seat_side(False).save(os.path.join(B, "star_seat_south_side.png"))
    # 星点光晕只是「一团光」，32 缩到 16 看不出来，文件小四分之三
    star_glow().resize((16, 16), Image.LANCZOS).save(os.path.join(B, "star_glow.png"))

    # 两套整阵图：盘面七星 / 盘面六星；各切成 12 张 32×32
    seven = star_chart("seven")
    six = star_chart("six")
    if TILE != seven.width // 3:
        seven = seven.resize((TILE * 3, TILE * 4), Image.LANCZOS)
        six = six.resize((TILE * 3, TILE * 4), Image.LANCZOS)
    for row in range(4):
        for col in range(3):
            box = (col * TILE, row * TILE, col * TILE + TILE, row * TILE + TILE)
            seven.crop(box).save(os.path.join(B, "star_plate_r%dc%d.png" % (row, col)), optimize=True)
            six.crop(box).save(os.path.join(B, "star_six_r%dc%d.png" % (row, col)), optimize=True)

    write_json(os.path.join(ASSETS, "models/block/star_dial.json"),
               slab_model("star_dial_top", "star_dial_side"))
    write_json(os.path.join(ASSETS, "models/item/star_dial.json"),
               {"parent": NS + ":block/star_dial"})
    for row in range(4):
        for col in range(3):
            side = "star_seat_north_side" if row <= 1 else "star_seat_south_side"
            write_json(os.path.join(ASSETS, "models/block/star_plate_r%dc%d.json" % (row, col)),
                       slab_model("star_plate_r%dc%d" % (row, col), side))
            write_json(os.path.join(ASSETS, "models/block/star_six_r%dc%d.json" % (row, col)),
                       slab_model("star_six_r%dc%d" % (row, col), side))
    write_json(os.path.join(ASSETS, "blockstates/star_dial.json"), blockstate())

    # 上一批的空盘（star_void_*）作废：现在六星期盘面上是六星本身
    removed = []
    for row in range(4):
        for col in range(3):
            for p in (os.path.join(B, "star_void_r%dc%d.png" % (row, col)),
                      os.path.join(ASSETS, "models/block/star_void_r%dc%d.json" % (row, col))):
                if os.path.exists(p):
                    os.remove(p)
                    removed.append(os.path.basename(p))
    print("清理旧资源", len(removed), "个:", ", ".join(removed[:4]) + (" ..." if len(removed) > 4 else ""))

    print("资源生成完毕（8 张单图 + 两套 12 分块 / 1 默认模型 + 24 成型模型 / 78 条 blockstate）")


if __name__ == "__main__":
    main()
