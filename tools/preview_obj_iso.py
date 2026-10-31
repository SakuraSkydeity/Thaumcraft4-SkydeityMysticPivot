#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
把一组 OBJ 按格子摆好、等距投影成 PNG —— 用来在不开游戏的情况下看模型长什么样。

用法：
    python tools/preview_obj_iso.py out.png [scale]
内置的是「玄枢虚空采矿机成型塔」的摆法（3x3 台基 + 核心格 + 顶盖格）。

投影与 tools/preview_block_iso.py 一致：
    sx = (x - z) * C          C = cos(30)
    sy = (x + z) / 2 - y
视线轴是 (1,1,1)，所以「越靠近镜头」= x + y + z 越大。
"""
import math
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot")
MODEL_DIR = os.path.join(ASSETS, "models")
TEX_DIR = os.path.join(ASSETS, "textures")

C = math.cos(math.radians(30.0))
# 视线是从 +x+y+z 方向看进来的，光也得从那一侧打 —— 否则等距图上看到的全是背光面，
# 白瓷会被读成土黄色，白白误判成「贴图不对」
LIGHT = np.array([0.52, 0.72, 0.46])
LIGHT = LIGHT / np.linalg.norm(LIGHT)


# ---------------------------------------------------------------- 解析
def parse_mtl(path):
    """只关心 newmtl 与 map_Kd。"""
    mats = {}
    cur = None
    with open(path, encoding="utf-8") as f:
        for line in f:
            s = line.strip()
            if not s or s.startswith("#"):
                continue
            parts = s.split()
            if parts[0] == "newmtl":
                cur = parts[1]
                mats[cur] = {"map": None, "kd": (1, 1, 1)}
            elif parts[0] == "map_Kd" and cur:
                mats[cur]["map"] = parts[1]
            elif parts[0] == "Kd" and cur:
                mats[cur]["kd"] = tuple(float(x) for x in parts[1:4])
    return mats


def parse_obj(path):
    """返回 [(material, [(pos, uv, normal) * 4]), ...]。"""
    verts, uvs, norms = [], [], []
    quads = []
    mtl_file = None
    cur = None
    with open(path, encoding="utf-8") as f:
        for line in f:
            s = line.strip()
            if not s or s.startswith("#"):
                continue
            parts = s.split()
            tag = parts[0]
            if tag == "v":
                verts.append(tuple(float(x) for x in parts[1:4]))
            elif tag == "vt":
                uvs.append((float(parts[1]), float(parts[2]) if len(parts) > 2 else 0.0))
            elif tag == "vn":
                norms.append(tuple(float(x) for x in parts[1:4]))
            elif tag == "usemtl":
                cur = parts[1]
            elif tag == "mtllib":
                mtl_file = parts[1]
            elif tag == "f":
                face = []
                for tok in parts[1:]:
                    idx = tok.split("/")
                    vi = int(idx[0]) - 1
                    ti = int(idx[1]) - 1 if len(idx) > 1 and idx[1] else None
                    ni = int(idx[2]) - 1 if len(idx) > 2 and idx[2] else None
                    face.append((verts[vi],
                                 uvs[ti] if ti is not None else (0.0, 0.0),
                                 norms[ni] if ni is not None else (0, 1, 0)))
                quads.append((cur, face))
    return quads, mtl_file


def load_texture(ref):
    """'ns:block/foo' -> 贴图路径。"""
    if ":" in ref:
        ref = ref.split(":", 1)[1]
    path = os.path.join(TEX_DIR, ref + ".png")
    return Image.open(path).convert("RGBA")


# ---------------------------------------------------------------- 渲染
def render(placements, out_path, scale=26, mode="iso", pad=18, bg=(28, 30, 34, 255)):
    """placements: [(obj_name, offset_xyz), ...]。画布按内容自动裁，不留大片空白。"""
    if mode == "iso":
        def project(p):
            x, y, z = p
            return ((x - z) * C, (x + z) * 0.5 - y, x + y + z)
    else:                       # 正交正视图：从北往南看，只比高度和收分
        def project(p):
            x, y, z = p
            return (x, -y, -z)

    tris = []
    bbox = [1e9, 1e9, -1e9, -1e9]
    for (name, (ox, oy, oz)) in placements:
        quads, mtl_file = parse_obj(os.path.join(MODEL_DIR, name + ".obj"))
        mtl_path = os.path.join(MODEL_DIR, mtl_file) if mtl_file else None
        mats = parse_mtl(mtl_path) if mtl_path else {}
        for (mat, face) in quads:
            info = mats.get(mat)
            if info is None or info["map"] is None:
                raise SystemExit("%s: 材质 %r 在 MTL 里找不到或没有 map_Kd" % (name, mat))
            img = load_texture(info["map"])
            kd = info["kd"]
            # 三角形（八面体结晶）与四边形都要支持
            fans = ((0, 1, 2),) if len(face) == 3 else ((0, 1, 2), (0, 2, 3))
            for idx in fans:
                pts = []
                for k in idx:
                    (p, uv, n) = face[k]
                    world = (p[0] + ox, p[1] + oy, p[2] + oz)
                    pts.append((world, uv, n))
                    sp = project(world)
                    bbox[0] = min(bbox[0], sp[0])
                    bbox[1] = min(bbox[1], sp[1])
                    bbox[2] = max(bbox[2], sp[0])
                    bbox[3] = max(bbox[3], sp[1])
                tris.append((pts, img, kd))

    W = int((bbox[2] - bbox[0]) * scale) + pad * 2
    H = int((bbox[3] - bbox[1]) * scale) + pad * 2
    ox_screen = -bbox[0] * scale + pad
    oy_screen = -bbox[1] * scale + pad

    pixels = np.zeros((H, W, 4), dtype=np.uint8)
    pixels[:, :] = bg
    zbuf = np.full((H, W), -1e9, dtype=np.float64)

    def to_screen(p):
        sp = project(p)
        return (sp[0] * scale + ox_screen, sp[1] * scale + oy_screen, sp[2])

    for (pts, img, kd) in tris:
        sp = [to_screen(p[0]) for p in pts]
        a = np.array(pts[0][0])
        b = np.array(pts[1][0])
        c = np.array(pts[2][0])
        n = np.cross(b - a, c - a)
        ln = np.linalg.norm(n)
        if ln < 1e-9:
            continue
        n = n / ln
        shade = 0.72 + 0.30 * max(0.0, float(np.dot(n, LIGHT)))

        (x0, y0, d0), (x1, y1, d1), (x2, y2, d2) = sp
        minx = int(max(0, math.floor(min(x0, x1, x2))))
        maxx = int(min(W - 1, math.ceil(max(x0, x1, x2))))
        miny = int(max(0, math.floor(min(y0, y1, y2))))
        maxy = int(min(H - 1, math.ceil(max(y0, y1, y2))))
        if minx > maxx or miny > maxy:
            continue

        den = (y1 - y2) * (x0 - x2) + (x2 - x1) * (y0 - y2)
        if abs(den) < 1e-9:
            continue

        ys, xs = np.mgrid[miny:maxy + 1, minx:maxx + 1]
        px = xs + 0.5
        py = ys + 0.5
        w0 = ((y1 - y2) * (px - x2) + (x2 - x1) * (py - y2)) / den
        w1 = ((y2 - y0) * (px - x2) + (x0 - x2) * (py - y2)) / den
        w2 = 1.0 - w0 - w1
        inside = (w0 >= -0.002) & (w1 >= -0.002) & (w2 >= -0.002)
        if not inside.any():
            continue

        depth = w0 * d0 + w1 * d1 + w2 * d2
        u = w0 * pts[0][1][0] + w1 * pts[1][1][0] + w2 * pts[2][1][0]
        v = w0 * pts[0][1][1] + w1 * pts[1][1][1] + w2 * pts[2][1][1]

        sub_z = zbuf[miny:maxy + 1, minx:maxx + 1]
        mask = inside & (depth > sub_z)
        if not mask.any():
            continue
        sub_z[mask] = depth[mask]

        arr = np.array(img)
        ih, iw = arr.shape[:2]
        # uv 0..1 直接乘贴图边长，不要再乘 (边长-1)：那样整张贴会被压进 31/32，
        # 逐像素看只差一格，但正好能把 8px 处的灰缝错过去
        uu = np.clip((u[mask] * iw).astype(np.int32), 0, iw - 1)
        vv = np.clip(((1.0 - v[mask]) * ih).astype(np.int32), 0, ih - 1)
        rgba = arr[vv, uu].astype(np.float64)
        rgba[:, :3] = np.clip(rgba[:, :3] * shade * np.array(kd), 0, 255)
        rgba[:, 3] = 255
        pixels[miny:maxy + 1, minx:maxx + 1][mask] = rgba.astype(np.uint8)

    Image.fromarray(pixels, "RGBA").save(out_path)
    print("written", os.path.relpath(out_path, ROOT), (W, H))


# 第一百八十六批起预览的是**星象仪**：单一整体 obj，坐标已含跨格偏移
# （球比一格宽 0.28 格、高 3.34 格），挂在单一格上，placements 只有一项。
TOWER = [("astrolabe", (0.0, 0.0, 0.0))]

if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "tools/preview/astrolabe_iso.png")
    scale = float(sys.argv[2]) if len(sys.argv) > 2 else 26.0
    mode = sys.argv[3] if len(sys.argv) > 3 else "iso"
    os.makedirs(os.path.dirname(out), exist_ok=True)
    render(TOWER, out, scale=scale, mode=mode)
