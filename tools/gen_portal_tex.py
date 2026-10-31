#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
玄枢传送门（xuanshu_portal）· 贴图（第一百九十七批新增；第二百零四批改 16×16 三面）。

<h2>这一批改了什么</h2>

用户口径：「<b>我要你改的是传送门的方块贴图……改为 16×16 也三张</b>」。

上一版是 32×32 四面（front / side / top / bottom）。这一版：

  * 边长降到 <b>16×16</b> —— 与包里其他方块贴图一个密度，像素块看得见；
  * 只出<b>三张</b>：front（门面）/ side（侧面）/ top（顶盖），底面直接用顶盖
    （`models/block/xuanshu_portal.json` 的 `down` 指向 top）；
  * 紫蓝科幻的配色与「线条感」不变：深紫蓝金属做骨架，能量一臂紫一臂蓝，
    门框内缘一道亮线、侧甲两道散热槽 + 电路走线、顶盖同心环 + 迷你漩涡。

<h2>三面</h2>

<pre>
  front   拱形门洞 + 双绞折跃漩涡 + 门槛
  side    竖肋 + 两道散热槽 + 电路走线 + 折跃箭头
  top     同心紫环 + 八角切角 + 俯视迷你漩涡
</pre>

用法：
    python tools/gen_portal_tex.py
"""

import math
import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot")
TEX_DIR = os.path.join(ASSETS, "textures/block")
PREVIEW_DIR = os.path.join(ROOT, "tools/preview")

S = 16
C = S / 2.0
NAME = "xuanshu_portal"

VIOLET_DD = (40, 34, 72)
VIOLET_D = (62, 54, 108)
VIOLET = (88, 76, 148)
VIOLET_HI = (120, 106, 184)
VIOLET_PALE = (158, 144, 212)
LINE_HI = (198, 186, 238)
EV = (176, 96, 248)
EB = (96, 150, 252)
ENERGY_HI = (216, 238, 255)
HOT = (248, 250, 255)


def mix(a, b, t):
    t = 0.0 if t < 0.0 else (1.0 if t > 1.0 else t)
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def jitter(x, y, salt, amp=1.0):
    v = (x * 73856093) ^ (y * 19349663) ^ (salt * 83492791)
    return (((v >> 5) % 1000) / 999.0 - 0.5) * amp


class Canvas:
    def __init__(self, w=S, h=S):
        self.w, self.h = w, h
        self.px = [[(0, 0, 0) for _ in range(w)] for _ in range(h)]

    def put(self, x, y, c):
        if 0 <= x < self.w and 0 <= y < self.h:
            self.px[y][x] = c

    def blend(self, x, y, c, a):
        if 0 <= x < self.w and 0 <= y < self.h and a > 0.0:
            self.px[y][x] = mix(self.px[y][x], c, a)

    def rect(self, x0, y0, x1, y1, c):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            for x in range(min(x0, x1), max(x0, x1) + 1):
                self.put(x, y, c)

    def hline(self, y, x0, x1, c):
        self.rect(x0, y, x1, y, c)

    def vline(self, x, y0, y1, c):
        self.rect(x, y0, x, y1, c)

    def frame(self, inset, c):
        self.hline(inset, inset, self.w - 1 - inset, c)
        self.hline(self.h - 1 - inset, inset, self.w - 1 - inset, c)
        self.vline(inset, inset, self.h - 1 - inset, c)
        self.vline(self.w - 1 - inset, inset, self.h - 1 - inset, c)

    def image(self):
        im = Image.new("RGB", (self.w, self.h))
        im.putdata([self.px[y][x] for y in range(self.h) for x in range(self.w)])
        return im


def metal(cv, salt):
    """底色：上亮下暗的紫蓝金属 + 一格一点抖动（不做成纯色板）。"""
    top, bottom = mix(VIOLET, VIOLET_D, 0.15), VIOLET_HI
    for y in range(S):
        base = mix(top, bottom, y / float(S - 1))
        for x in range(S):
            j = jitter(x, y, salt, 1.0) * 16
            cv.put(x, y, tuple(max(0, min(255, int(base[k] + j))) for k in range(3)))


def vortex(cv, cx, cy, r0, r1, arms=2, colors=(EV, EB)):
    """两臂像素螺旋 + 热核：16×16 里只画一圈半，粗 1 格。"""
    for arm in range(arms):
        base = colors[arm % len(colors)]
        for i in range(96):
            t = i / 95.0
            r = r0 + (r1 - r0) * t
            th = arm * math.pi + 1.6 * math.pi * t
            cv.blend(int(cx + r * math.cos(th)), int(cy + r * math.sin(th)),
                     mix(base, HOT, (1.0 - t) ** 1.6), 1.0)
    for dx, dy in ((0, 0), (1, 0), (0, 1), (1, 1)):
        cv.put(int(cx) + dx - 1, int(cy) + dy - 1, HOT if (dx + dy) % 2 == 0 else ENERGY_HI)


# ----------------------------------------------------------------------
# 门面
# ----------------------------------------------------------------------

def tex_front():
    cv = Canvas()
    metal(cv, 11)

    # 门洞：x 4…11、y 4…13，一层淡紫能量底，漩涡在上面才转得出来
    cv.rect(4, 4, S - 5, S - 4, mix(VIOLET_HI, EV, 0.16))
    cv.rect(5, 4, S - 6, S - 4, mix(VIOLET_PALE, EV, 0.10))

    vortex(cv, 7.5, 8.5, 0.6, 3.1)

    # 两根门柱：x 2…3 与 12…13，朝门洞那一侧提亮
    for xa, xb, lit in ((2, 3, 3), (S - 4, S - 3, S - 4)):
        cv.rect(xa, 4, xb, S - 4, VIOLET_D)
        cv.vline(lit, 4, S - 4, VIOLET_HI)
    # 门楣：两道横梁（上沿亮线、下沿暗）
    cv.hline(2, 2, S - 3, LINE_HI)
    cv.hline(3, 2, S - 3, VIOLET)
    # 拱顶：门洞顶上收窄一格，看着是「门」不是「方框」
    cv.hline(4, 4, 4, VIOLET)
    cv.hline(4, S - 5, S - 5, VIOLET)
    # 门槛：一行深色 + 三点能量
    cv.hline(S - 3, 2, S - 3, VIOLET_DD)
    for x in (5, 8, 10):
        cv.put(x, S - 3, ENERGY_HI)

    # 外框：外深内亮，内缘一道亮紫细线（线条感）
    cv.frame(0, VIOLET_DD)
    cv.frame(1, VIOLET_D)
    cv.vline(1, 4, S - 4, VIOLET)
    for x, y in ((1, 1), (S - 2, 1), (1, S - 2), (S - 2, S - 2)):
        cv.put(x, y, VIOLET_PALE)
    for x in (7, 8):
        cv.put(x, 2, HOT)
    return cv.image()


# ----------------------------------------------------------------------
# 侧面
# ----------------------------------------------------------------------

def tex_side():
    cv = Canvas()
    metal(cv, 21)

    # 中间竖肋（亮线 + 两侧暗边）
    cv.vline(7, 2, S - 4, VIOLET_D)
    cv.vline(8, 2, S - 4, LINE_HI)
    cv.vline(9, 2, S - 4, VIOLET_HI)

    # 两道散热槽：上沿压线、槽口压暗、槽底受光（三段才读得出是「格栅」不是「洞」）
    for y in (5, 10):
        for xa, xb in ((3, 6), (10, 12)):
            cv.hline(y - 1, xa, xb, VIOLET)
            cv.hline(y, xa, xb, mix(VIOLET_D, VIOLET_DD, 0.55))
            cv.hline(y + 1, xa, xb, VIOLET_HI)
            cv.blend(xa, y, EB, 0.8)
            cv.blend(xb, y, EV, 0.8)

    # 电路走线：把两道槽串起来（线条感）
    for x in (2, 13):
        cv.vline(x, 4, 12, mix(VIOLET_HI, LINE_HI, 0.55))
        for y in (5, 6, 10, 11):
            cv.put(x, y, LINE_HI)

    # 折跃箭头：肋条两侧各两枚 ›，尖朝当中
    for x0, y0, d in ((5, 3, 1), (5, 12, 1), (10, 3, -1), (10, 12, -1)):
        for s in range(2):
            cv.put(x0 + d * s, y0 - s, EB)
            cv.put(x0 + d * s, y0 + s, EB)

    cv.frame(0, VIOLET_DD)
    cv.frame(1, VIOLET_D)
    for x, y in ((1, 1), (S - 2, 1), (1, S - 2), (S - 2, S - 2)):
        cv.put(x, y, VIOLET_PALE)
    return cv.image()


# ----------------------------------------------------------------------
# 顶盖
# ----------------------------------------------------------------------

def tex_top():
    cv = Canvas()
    metal(cv, 31)

    # 同心方环：从外往里 亮 → 暗 → 亮 → 暗
    rings = ((7.4, VIOLET), (6.4, VIOLET_HI), (5.4, VIOLET_D), (4.4, LINE_HI))
    for y in range(S):
        for x in range(S):
            d = max(abs(x + 0.5 - C), abs(y + 0.5 - C))
            for edge, col in rings:
                if d <= edge:
                    cv.put(x, y, col)

    vortex(cv, 8.0, 8.0, 0.5, 3.2)

    # 八向细线（线条感）
    for k in range(8):
        th = 2.0 * math.pi * k / 8.0 + math.pi / 8.0
        cv.blend(int(C + 6.0 * math.cos(th)), int(C + 6.0 * math.sin(th)), LINE_HI, 0.95)

    # 八角切角
    for y in range(S):
        for x in range(S):
            d = abs(x + 0.5 - C) + abs(y + 0.5 - C)
            if d > 13.0:
                cv.put(x, y, VIOLET_DD)
            elif d > 12.0:
                cv.put(x, y, VIOLET)
    cv.frame(0, VIOLET_DD)
    return cv.image()


# ----------------------------------------------------------------------
# 预览
# ----------------------------------------------------------------------

def preview_sheet(faces, path, scale=12, pad=12):
    w = len(faces) * (S * scale + pad) + pad
    h = S * scale + pad * 2 + 22
    im = Image.new("RGB", (w, h), (24, 26, 36))
    dr = ImageDraw.Draw(im)
    for i, (label, img) in enumerate(faces):
        ox = pad + i * (S * scale + pad)
        im.paste(img.resize((S * scale, S * scale), Image.NEAREST), (ox, pad + 16))
        for gx in range(0, S * scale + 1, scale):
            for gy in range(0, S * scale + 1, scale):
                if gx % (scale * 4) == 0 and gy % (scale * 4) == 0:
                    continue
                im.putpixel((ox + min(gx, S * scale - 1), pad + 16 + min(gy, S * scale - 1)),
                            (40, 42, 56))
        dr.text((ox, pad), label.upper(), fill=(200, 206, 224))
    im.save(path)


def preview_iso(front, side, top, path, size=420):
    im = Image.new("RGB", (size, size), (22, 24, 34))
    s = size * 0.30
    X = (0.866 * s, 0.5 * s)
    Z = (-0.866 * s, 0.5 * s)
    Y = (0.0, -s)

    def P(x, y, z):
        return (x * X[0] + y * Y[0] + z * Z[0], x * X[1] + y * Y[1] + z * Z[1])

    pts = [P(x, y, z) for x in (0, 1) for y in (0, 1) for z in (0, 1)]
    ox = size * 0.5 - (max(p[0] for p in pts) + min(p[0] for p in pts)) * 0.5
    oy = size * 0.54 - (max(p[1] for p in pts) + min(p[1] for p in pts)) * 0.5

    quads = [
        (top, P(0, 1, 0), X, Z, 1.00),
        (front, P(1, 0, 0), Z, Y, 0.86),
        (side, P(1, 0, 1), (-X[0], -X[1]), Y, 0.72),
    ]
    px = im.load()
    for tex, o, a, b, shade in quads:
        o = (o[0] + ox, o[1] + oy)
        w, h = tex.size
        src = tex.load()
        det = a[0] * b[1] - a[1] * b[0]
        corners = [o, (o[0] + a[0], o[1] + a[1]),
                   (o[0] + a[0] + b[0], o[1] + a[1] + b[1]), (o[0] + b[0], o[1] + b[1])]
        for y in range(int(max(0, min(c[1] for c in corners))),
                       int(min(size - 1, max(c[1] for c in corners) + 1))):
            for x in range(int(max(0, min(c[0] for c in corners))),
                           int(min(size - 1, max(c[0] for c in corners) + 1))):
                dx, dy = x + 0.5 - o[0], y + 0.5 - o[1]
                u = (dx * b[1] - dy * b[0]) / det
                v = (a[0] * dy - a[1] * dx) / det
                if 0.0 <= u < 1.0 and 0.0 <= v < 1.0:
                    c = src[min(w - 1, int(u * w)), min(h - 1, int(v * h))]
                    px[x, y] = (int(c[0] * shade), int(c[1] * shade), int(c[2] * shade))
    im.save(path)


def main():
    os.makedirs(TEX_DIR, exist_ok=True)
    os.makedirs(PREVIEW_DIR, exist_ok=True)

    front, side, top = tex_front(), tex_side(), tex_top()
    faces = [("front", front), ("side", side), ("top", top)]
    for suffix, img in faces:
        p = os.path.join(TEX_DIR, "%s_%s.png" % (NAME, suffix))
        img.save(p)
        print("wrote %s (%dx%d)" % (os.path.relpath(p, ROOT), img.size[0], img.size[1]))

    preview_sheet(faces, os.path.join(PREVIEW_DIR, "portal_faces.png"))
    preview_iso(front, side, top, os.path.join(PREVIEW_DIR, "portal_iso.png"))
    print("wrote tools/preview/portal_faces.png, portal_iso.png")


if __name__ == "__main__":
    main()
