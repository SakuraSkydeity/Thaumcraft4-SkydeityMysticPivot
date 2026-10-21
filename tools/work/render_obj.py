#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Minimal OBJ previewer: flat-shaded, painter's algorithm, pure PIL."""
import math
import os
import sys

from PIL import Image


def load_obj(path):
    verts, normals, faces = [], [], []
    with open(path, "r", encoding="utf-8", errors="ignore") as f:
        for line in f:
            if line.startswith("v "):
                p = line.split()
                verts.append((float(p[1]), float(p[2]), float(p[3])))
            elif line.startswith("vn "):
                p = line.split()
                normals.append((float(p[1]), float(p[2]), float(p[3])))
            elif line.startswith("f "):
                parts = line.split()[1:]
                idx = []
                for tok in parts:
                    bits = tok.split("/")
                    vi = int(bits[0]) - 1
                    ni = int(bits[2]) - 1 if len(bits) > 2 and bits[2] else -1
                    idx.append((vi, ni))
                faces.append(idx)
    return verts, normals, faces


def render(verts, normals, faces, size=320, yaw=35.0, pitch=22.0, bg=(30, 28, 40), fname=None):
    # center around model centroid, scale to fit
    xs = [v[0] for v in verts]; ys = [v[1] for v in verts]; zs = [v[2] for v in verts]
    cx = (min(xs) + max(xs)) / 2; cy = (min(ys) + max(ys)) / 2; cz = (min(zs) + max(zs)) / 2
    ext = max(max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs)) or 1.0
    scale = size * 0.82 / ext

    ry = math.radians(yaw); rx = math.radians(pitch)
    cyaw, syaw = math.cos(ry), math.sin(ry)
    cpit, spit = math.cos(rx), math.sin(rx)

    def project(p):
        x, y, z = p[0] - cx, p[1] - cy, p[2] - cz
        # rotate about Y
        x1 = x * cyaw + z * syaw
        z1 = -x * syaw + z * cyaw
        # rotate about X (pitch)
        y2 = y * cpit - z1 * spit
        z2 = y * spit + z1 * cpit
        return (x1, y2, z2)

    light = (-0.4, 0.8, 0.45)
    ll = math.sqrt(sum(c * c for c in light))
    light = tuple(c / ll for c in light)

    img = Image.new("RGB", (size, size), bg)
    px = img.load()

    polys = []
    for face in faces:
        pts3 = [project(verts[vi]) for (vi, ni) in face]
        # face normal from cross product
        ax, ay, az = pts3[0]
        bx, by, bz = pts3[1]
        cxx, cyy, czz = pts3[2]
        ux, uy, uz = bx - ax, by - ay, bz - az
        vx, vy, vz = cxx - ax, cyy - ay, czz - az
        nx, ny, nz = uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx
        ln = math.sqrt(nx * nx + ny * ny + nz * nz) or 1.0
        nx, ny, nz = nx / ln, ny / ln, nz / ln
        # camera looks along -z after projection; visible if normal z>0
        if nz <= 0.02:
            continue
        lam = max(0.0, nx * light[0] + ny * light[1] + nz * light[2])
        shade = 0.35 + 0.65 * lam
        depth = sum(p[2] for p in pts3) / len(pts3)
        scr = [(size / 2 + p[0] * scale, size / 2 - p[1] * scale) for p in pts3]
        polys.append((depth, scr, shade))
    polys.sort(key=lambda t: t[0])

    for depth, scr, shade in polys:
        col = (int(150 * shade), int(130 * shade), int(190 * shade))
        # scanline fill of convex polygon
        ys_all = [p[1] for p in scr]
        ymin = max(0, int(math.floor(min(ys_all))))
        ymax = min(size - 1, int(math.ceil(max(ys_all))))
        n = len(scr)
        for y in range(ymin, ymax + 1):
            xsec = []
            for i in range(n):
                x1, y1 = scr[i]
                x2, y2 = scr[(i + 1) % n]
                if (y1 <= y < y2) or (y2 <= y < y1):
                    t = (y - y1) / (y2 - y1)
                    xsec.append(x1 + (x2 - x1) * t)
            xsec.sort()
            for k in range(0, len(xsec) - 1, 2):
                xa = max(0, int(math.ceil(xsec[k])))
                xb = min(size - 1, int(math.floor(xsec[k + 1])))
                for x in range(xa, xb + 1):
                    px[x, y] = col
    if fname:
        img.save(fname)
    return img


def main():
    path = sys.argv[1]
    out = sys.argv[2] if len(sys.argv) > 2 else "out.png"
    verts, normals, faces = load_obj(path)
    print("verts", len(verts), "faces", len(faces))
    n = 6
    tiles = []
    for i in range(n):
        yaw = 360.0 * i / n
        tiles.append(render(verts, normals, faces, yaw=yaw, pitch=20.0))
    sheet = Image.new("RGB", (320 * n, 320), (20, 18, 28))
    for i, t in enumerate(tiles):
        sheet.paste(t, (i * 320, 0))
    sheet.save(out)
    print("saved", out)


if __name__ == "__main__":
    main()
