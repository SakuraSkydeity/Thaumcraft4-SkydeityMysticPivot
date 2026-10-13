#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
自写等距渲染器：把 textures/models/onyx_wand_rod.png 按 MC 盒模型 UV 布局
贴到 2x18x2 的杖盒上，验证「螺旋包裹」在三维里是否连续、以及整体观感。

UV 布局与顶点对应关系照 1.21.1 ModelPart.Cube 源码逐面核对（见 gen_wand_rod.py 顶部注释）。
"""
import math
import os
import sys
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_wand_rod import MODEL_TEX  # noqa: E402

TEX = Image.open(os.path.join(MODEL_TEX, "onyx_wand_rod.png")).convert("RGBA")

# 盒：x[-1,1] y[0,18] z[-1,1]。每面 4 顶点 (位置, uv)
V = {
    "v7": ((-1, 0, -1), None), "v": ((1, 0, -1), None),
    "v1": ((1, 18, -1), None), "v2": ((-1, 18, -1), None),
    "v3": ((-1, 0, 1), None), "v4": ((1, 0, 1), None),
    "v5": ((1, 18, 1), None), "v6": ((-1, 18, 1), None),
}
# 面 -> (顶点序, uv矩形[u0,v0,u1,v1], 亮度)
FACES = {
    "up":    (["v1", "v2", "v6", "v5"], (4, 8, 6, 10), 1.00),
    "down":  (["v4", "v3", "v7", "v"],  (2, 8, 4, 10), 0.50),
    "west":  (["v7", "v3", "v6", "v2"], (0, 10, 2, 28), 0.60),
    "north": (["v", "v7", "v2", "v1"],  (2, 10, 4, 28), 0.80),
    "east":  (["v4", "v", "v1", "v5"],  (4, 10, 6, 28), 0.60),
    "south": (["v3", "v4", "v5", "v6"], (6, 10, 8, 28), 0.80),
}


def face_quad(name):
    order, (u0, v0, u1, v1), shade = FACES[name]
    uv_of_index = [(u1, v0), (u0, v0), (u0, v1), (u1, v1)]  # 顶点序 -> uv（照 Polygon.remap）
    quad = []
    for i, key in enumerate(order):
        pos, _ = V[key]
        quad.append((pos, uv_of_index[i]))
    return quad, shade


def rotate(p, yaw, pitch):
    x, y, z = p
    x, z = x * math.cos(yaw) + z * math.sin(yaw), -x * math.sin(yaw) + z * math.cos(yaw)
    y, z = y * math.cos(pitch) - z * math.sin(pitch), y * math.sin(pitch) + z * math.cos(pitch)
    return (x, y, z)


def render(yaw, pitch, scale=11.0, tilt=0.0, bg=(22, 22, 27)):
    W, H = int(8 * scale + 220), int(22 * scale + 120)
    im = Image.new("RGBA", (W, H), bg + (255,))
    px = im.load()
    cx, cy = W / 2, H / 2 + 10

    def project(p):
        q = rotate(p, yaw, pitch)
        if tilt:
            x, y, z = q
            q = (x * math.cos(tilt) - y * math.sin(tilt),
                 x * math.sin(tilt) + y * math.cos(tilt), z)
        return (cx + q[0] * scale, cy - q[1] * scale, q[2])

    entries = []
    for name in FACES:
        quad, shade = face_quad(name)
        # 背面剔除：用法线（顶点序 0,1,2 的叉积）与视线比
        p0 = rotate(quad[0][0], yaw, pitch)
        p1 = rotate(quad[1][0], yaw, pitch)
        p2 = rotate(quad[2][0], yaw, pitch)
        ux, uy, uz = (p1[i] - p0[i] for i in range(3))
        vx, vy, vz = (p2[i] - p0[i] for i in range(3))
        nx, ny, nz = uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx
        if nx * p0[0] + ny * p0[1] + nz * p0[2] < 0 and (nx, ny, nz) != (0, 0, 0):
            # 法线背对相机（相机在 +Z 无穷远看 -Z）：用 dot(n, p-center) 判据太粗，
            # 直接用 dot(n, view_dir) ，view_dir = (0,0,1)
            if nz <= 0:
                continue
        depth = sum(rotate(v[0], yaw, pitch)[2] for v in quad) / 4
        entries.append((depth, name, quad, shade))
    entries.sort(key=lambda e: e[0])

    for _, name, quad, shade in entries:
        pts = [project(v[0]) for v in quad]
        uvs = [v[1] for v in quad]
        tris = [(0, 1, 2), (0, 2, 3)]
        for a, b, c in tris:
            (ax, ay, _), (bx, by, _), (cx2, cy2, _) = pts[a], pts[b], pts[c]
            (au, av), (bu, bv), (cu, cv) = uvs[a], uvs[b], uvs[c]
            minx = max(0, int(math.floor(min(ax, bx, cx2))))
            maxx = min(W - 1, int(math.ceil(max(ax, bx, cx2))))
            miny = max(0, int(math.floor(min(ay, by, cy2))))
            maxy = min(H - 1, int(math.ceil(max(ay, by, cy2))))
            den = (by - cy2) * (ax - cx2) + (cx2 - bx) * (ay - cy2)
            if abs(den) < 1e-9:
                continue
            for yy in range(miny, maxy + 1):
                for xx in range(minx, maxx + 1):
                    l1 = ((by - cy2) * (xx - cx2) + (cx2 - bx) * (yy - cy2)) / den
                    l2 = ((cy2 - ay) * (xx - cx2) + (ax - cx2) * (yy - cy2)) / den
                    l3 = 1 - l1 - l2
                    if l1 < -0.002 or l2 < -0.002 or l3 < -0.002:
                        continue
                    u = l1 * au + l2 * bu + l3 * cu
                    v = l1 * av + l2 * bv + l3 * cv
                    tx, ty = int(math.floor(u)) % 32, int(math.floor(v)) % 32
                    r, g, b, a = TEX.getpixel((tx, ty))
                    if a < 16:
                        continue
                    s = shade * 0.92 + 0.08
                    px[xx, yy] = (min(255, int(r * s)), min(255, int(g * s)),
                                  min(255, int(b * s)), 255)
    return im


if __name__ == "__main__":
    out = []
    # 三个视角：正视 45 度（第一人称右手姿势的 Y 旋转）、略俯、斜持
    views = [
        ("front45", dict(yaw=math.radians(45), pitch=math.radians(24))),
        ("top45", dict(yaw=math.radians(45), pitch=math.radians(38))),
        ("tilted", dict(yaw=math.radians(30), pitch=math.radians(20), tilt=math.radians(-18))),
    ]
    tiles = []
    for name, kw in views:
        im = render(**kw)
        im.save(os.path.join(ROOT := os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                             f"tools/preview/wand_rod_{name}.png"))
        tiles.append(im)
        print("rendered", name)
    # 拼一张
    W = sum(t.width + 16 for t in tiles)
    H = max(t.height for t in tiles)
    sheet = Image.new("RGBA", (W, H), (22, 22, 27, 255))
    x = 0
    for t in tiles:
        sheet.paste(t, (x, 0))
        x += t.width + 16
    sheet.save(os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                            "tools/preview/wand_rod_views.png"))
    print("sheet saved")
