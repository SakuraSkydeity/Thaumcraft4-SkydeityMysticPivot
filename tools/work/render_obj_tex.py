#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Textured OBJ previewer that mimics MC 1.21 block rendering:
- backface culling (like render_type cutout)
- MC directional quad shading (up 1.0 / down 0.5 / x 0.6 / z 0.8)
- nearest texture sampling, v=0 == top row of the png (flip_v: false)
"""
import math
import os
import sys

from PIL import Image

DIR_SHADE = {
    (0, 1, 0): 1.00, (0, -1, 0): 0.50,
    (1, 0, 0): 0.60, (-1, 0, 0): 0.60,
    (0, 0, 1): 0.80, (0, 0, -1): 0.80,
}


def nearest_direction(n):
    ax, ay, az = abs(n[0]), abs(n[1]), abs(n[2])
    if ax >= ay and ax >= az:
        return (1 if n[0] > 0 else -1, 0, 0)
    if ay >= az:
        return (0, 1 if n[1] > 0 else -1, 0)
    return (0, 0, 1 if n[2] > 0 else -1)


def load_obj(path):
    v, vt, vn, faces = [], [], [], []
    for line in open(path, encoding="utf-8", errors="ignore"):
        if line.startswith("v "):
            p = line.split()
            v.append((float(p[1]), float(p[2]), float(p[3])))
        elif line.startswith("vt "):
            p = line.split()
            vt.append((float(p[1]), float(p[2])))
        elif line.startswith("vn "):
            p = line.split()
            vn.append((float(p[1]), float(p[2]), float(p[3])))
        elif line.startswith("f "):
            idx = []
            for tok in line.split()[1:]:
                b = tok.split("/")
                vi = int(b[0]) - 1
                ti = int(b[1]) - 1 if len(b) > 1 and b[1] else -1
                ni = int(b[2]) - 1 if len(b) > 2 and b[2] else -1
                idx.append((vi, ti, ni))
            faces.append(idx)
    return v, vt, vn, faces


class Tex:
    def __init__(self, path):
        self.im = Image.open(path).convert("RGBA")
        self.w, self.h = self.im.size
        self.px = self.im.load()

    def sample(self, u, v):
        x = min(self.w - 1, max(0, int(u * self.w)))
        y = min(self.h - 1, max(0, int(v * self.h)))   # flip_v:false -> v0 = top row
        return self.px[x, y]


def render(v, vt, vn, faces, tex, size=360, yaw=35.0, pitch=22.0):
    xs = [p[0] for p in v]; ys = [p[1] for p in v]; zs = [p[2] for p in v]
    cx = (min(xs) + max(xs)) / 2; cy = (min(ys) + max(ys)) / 2; cz = (min(zs) + max(zs)) / 2
    ext = max(max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs)) or 1.0
    scale = size * 0.80 / ext

    ry = math.radians(yaw); rx = math.radians(pitch)
    cyw, syw = math.cos(ry), math.sin(ry)
    cpt, spt = math.cos(rx), math.sin(rx)

    def proj(p):
        x, y, z = p[0] - cx, p[1] - cy, p[2] - cz
        x1 = x * cyw + z * syw
        z1 = -x * syw + z * cyw
        y2 = y * cpt - z1 * spt
        z2 = y * spt + z1 * cpt
        return (x1, y2, z2)

    img = Image.new("RGB", (size, size), (26, 24, 34))
    px = img.load()
    tris = []
    for face in faces:
        pts = [proj(v[a[0]]) for a in face]
        for k in range(1, len(pts) - 1):
            p0, p1, p2 = pts[0], pts[k], pts[k + 1]
            ux, uy, uz = p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2]
            vx, vy, vz = p2[0] - p0[0], p2[1] - p0[1], p2[2] - p0[2]
            nx, ny, nz = uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx
            if nz <= 1e-6:
                continue            # backface culled (cutout render type)
            # shade from the *model space* normal of this triangle
            a0 = v[face[0][0]]; a1 = v[face[k][0]]; a2 = v[face[k + 1][0]]
            mx, my, mz = a1[0] - a0[0], a1[1] - a0[1], a1[2] - a0[2]
            wx, wy, wz = a2[0] - a0[0], a2[1] - a0[1], a2[2] - a0[2]
            mnx, mny, mnz = my * wz - mz * wy, mz * wx - mx * wz, mx * wy - my * wx
            ml = math.sqrt(mnx * mnx + mny * mny + mnz * mnz) or 1.0
            d = nearest_direction((mnx / ml, mny / ml, mnz / ml))
            shade = DIR_SHADE[d]
            depth = (p0[2] + p1[2] + p2[2]) / 3.0
            uvs = [vt[face[0][1]], vt[face[k][1]], vt[face[k + 1][1]]]
            tris.append((depth, p0, p1, p2, uvs, shade))
    tris.sort(key=lambda t: t[0])

    for depth, p0, p1, p2, uvs, shade in tris:
        scr = [(size / 2 + p[0] * scale, size / 2 - p[1] * scale) for p in (p0, p1, p2)]
        (x0, y0), (x1, y1), (x2, y2) = scr
        minx = max(0, int(math.floor(min(x0, x1, x2))))
        maxx = min(size - 1, int(math.ceil(max(x0, x1, x2))))
        miny = max(0, int(math.floor(min(y0, y1, y2))))
        maxy = min(size - 1, int(math.ceil(max(y0, y1, y2))))
        denom = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
        if abs(denom) < 1e-9:
            continue
        for yy in range(miny, maxy + 1):
            for xx in range(minx, maxx + 1):
                px_ = xx + 0.5; py_ = yy + 0.5
                l0 = ((y1 - y2) * (px_ - x2) + (x2 - x1) * (py_ - y2)) / denom
                l1 = ((y2 - y0) * (px_ - x2) + (x0 - x2) * (py_ - y2)) / denom
                l2 = 1.0 - l0 - l1
                if l0 < -0.002 or l1 < -0.002 or l2 < -0.002:
                    continue
                u = l0 * uvs[0][0] + l1 * uvs[1][0] + l2 * uvs[2][0]
                vv = l0 * uvs[0][1] + l1 * uvs[1][1] + l2 * uvs[2][1]
                c = tex.sample(u, vv)
                if c[3] < 128:
                    continue
                r = min(255, int(c[0] * shade))
                g = min(255, int(c[1] * shade))
                b = min(255, int(c[2] * shade))
                px[xx, yy] = (r, g, b)
    return img


def main():
    obj = sys.argv[1]
    tex_path = sys.argv[2]
    out = sys.argv[3]
    n = int(sys.argv[4]) if len(sys.argv) > 4 else 6
    v, vt, vn, faces = load_obj(obj)
    tex = Tex(tex_path)
    print("v", len(v), "vt", len(vt), "faces", len(faces), "tex", tex.im.size)
    tiles = [render(v, vt, vn, faces, tex, yaw=360.0 * i / n, pitch=20.0) for i in range(n)]
    sheet = Image.new("RGB", (360 * n, 360), (18, 16, 24))
    for i, t in enumerate(tiles):
        sheet.paste(t, (i * 360, 0))
    sheet.save(out)
    print("saved", out)


if __name__ == "__main__":
    main()
