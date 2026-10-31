#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把一个方块 JSON 模型 + 贴图，按等距视角渲染成 PNG —— 不进游戏也能看整体效果。

只做「看得见的三面」：up / north / east，按 `x + z - y` 从远到近画，
每个面用仿射逆映射采样贴图（最近邻，保住像素边）。

这不是通用渲染器：不支持旋转、不支持 inner/outer UV 旋转，够看自家方块即可。
"""

import json
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot")

C30 = 0.8660254
S30 = 0.5


def project(x, y, z, scale, ox, oy):
    return (ox + (x - z) * C30 * scale, oy + ((x + z) * S30 - y) * scale)


def tex_path(ref):
    ns, path = ref.split(":")
    return os.path.join(ROOT, "src/main/resources/assets", ns, "textures", path + ".png")


def load_textures(model):
    cache = {}
    for key, ref in model["textures"].items():
        if key.startswith("#"):
            continue
        cache["#" + key] = Image.open(tex_path(ref)).convert("RGBA")
    return cache


def face_quad(element, face):
    """返回 (三个屏幕角点, 对应 uv)，都是世界坐标 → 由调用方投影。"""
    x0, y0, z0 = element["from"]
    x1, y1, z1 = element["to"]
    uv = face["uv"]
    u0, v0, u1, v1 = uv[0] / 16.0, uv[1] / 16.0, uv[2] / 16.0, uv[3] / 16.0

    if face["_dir"] == "up":
        # 顺序：西北、东北、东南、西南（俯视顺时针）
        pts = [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)]
        uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
    elif face["_dir"] == "north":
        pts = [(x0, y1, z0), (x1, y1, z0), (x1, y0, z0), (x0, y0, z0)]
        uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
    else:  # east
        pts = [(x1, y1, z0), (x1, y1, z1), (x1, y0, z1), (x1, y0, z0)]
        uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
    return pts, uvs


def draw_face(img, tex, quad, scale, ox, oy, shade=1.0, dirn=None, el=None, zbuf=None):
    """把一块平行四边形按 uv 采样到画布上。"""
    sp = [project(*p, scale, ox, oy) for p in quad]
    xs = [p[0] for p in sp]
    ys = [p[1] for p in sp]
    bx0, bx1 = int(min(xs)) - 1, int(max(xs)) + 1
    by0, by1 = int(min(ys)) - 1, int(max(ys)) + 1

    # 用 (0,0)、(1,0)、(0,1) 三个角解仿射：屏幕 = A + u*B + v*C
    ax, ay = sp[0]
    bx, by = sp[1][0] - ax, sp[1][1] - ay
    cx, cy = sp[3][0] - ax, sp[3][1] - ay
    det = bx * cy - by * cx
    if abs(det) < 1e-9:
        return
    px = img.load()
    tw, th = tex.size
    tpx = tex.load()
    x0, y0, z0 = el["from"]
    x1, y1, z1 = el["to"]
    for y in range(max(0, by0), min(img.height, by1)):
        for x in range(max(0, bx0), min(img.width, bx1)):
            sx, sy = x + 0.5 - ax, y + 0.5 - ay
            u = (sx * cy - sy * cx) / det
            v = (sx * (-by) + sy * bx) / det
            if not (0.0 <= u < 1.0 and 0.0 <= v < 1.0):
                continue

            # 反算世界坐标 → 深度。
            # 本投影 sx=(x-z)C、sy=(x+z)/2-y，视线轴是 (1,1,1)（沿它平移屏幕不动），
            # 所以「越靠近镜头」= x+y+z 越大。早先写成 x+z-y 会让高柱被梁压住。

            if dirn == "up":
                wx, wy, wz = x0 + u * (x1 - x0), y1, z0 + v * (z1 - z0)
            elif dirn == "north":
                wx, wy, wz = x0 + u * (x1 - x0), y1 - v * (y1 - y0), z0
            else:
                wx, wy, wz = x1, y1 - v * (y1 - y0), z0 + u * (z1 - z0)
            depth = wx + wy + wz
            idx = y * img.width + x
            if depth <= zbuf[idx]:
                continue
            zbuf[idx] = depth

            tx = min(tw - 1, int(u * tw))
            ty = min(th - 1, int(v * th))
            c = tpx[tx, ty]
            if c[3] < 8:
                continue
            px[x, y] = (int(c[0] * shade), int(c[1] * shade), int(c[2] * shade), c[3])


def main():
    model_path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ASSETS, "models/block/xuanshu_node_modifier.json")
    out = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, "tools/preview/block_iso.png")
    model = json.load(open(model_path, encoding="utf-8"))
    textures = load_textures(model)

    scale = 15
    W = int(16 * 2 * C30 * scale) + 80
    H = int(16 * 2 * S30 * scale + 16 * scale) + 90
    img = Image.new("RGBA", (W, H), (24, 20, 32, 255))
    zbuf = [-1e9] * (W * H)
    # 投影后屏幕 y ∈ [oy - 16*scale, oy + 16*scale]，锚点放在画布偏下，模型才居中
    ox = W / 2.0
    oy = 16 * scale + 30

    # 远 → 近：按元素中心的 x+z-y 排序（等距视角下的深度）
    elements = []
    for el in model["elements"]:
        x0, y0, z0 = el["from"]
        x1, y1, z1 = el["to"]
        depth = (x0 + x1) / 2 + (z0 + z1) / 2 - (y0 + y1) / 2
        elements.append((depth, el))
    elements.sort(key=lambda t: t[0])

    for _, el in elements:
        for name in ("up", "north", "east"):
            face = el["faces"].get(name)
            if not face:
                continue
            face = dict(face)
            face["_dir"] = name
            ref = face.get("texture") or "#side"
            tex = textures.get(ref) or textures.get("#side")
            quad, _uvs = face_quad(el, face)
            # 方向光：顶面最亮、正面次之、侧面最暗 —— 不加这层，所有面同色就成了木模
            shade = {"up": 1.0, "north": 0.84, "east": 0.68}[name]
            draw_face(img, tex, quad, scale, ox, oy, shade, name, el, zbuf)

    os.makedirs(os.path.dirname(out), exist_ok=True)
    img.save(out)
    print("->", os.path.relpath(out, ROOT), img.size)


if __name__ == "__main__":
    main()
