#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
通用方块模型等距预览器：加载任意 assets/.../models/block/*.json + 同名 textures/block/*.png，
按 MC 1.21.1 语义渲染，用于在进游戏前验证自定义建模。

严格对齐的点：
- element from/to 为未旋转坐标，rotation 在渲染时绕 origin 施加（右手系，JOML 约定）：
    绕 x: y' = y*cos - z*sin,  z' = y*sin + z*cos
    绕 y: x' = x*cos + z*sin,  z' = -x*sin + z*cos
    绕 z: x' = x*cos - y*sin,  y' = x*sin + y*cos
  （已用原版 big_dripleaf_full_tilt / coral_fan 核对方向）
- 面顶点 uv：MC 按面类型取盒子的 4 个角，uv 矩形 [u0,v0,u1,v1] 映射
    north: (x0,y1,z0)(x1,y1,z0)(x1,y0,z0)(x0,y0,z0)  -> (u0,v0)(u1,v0)(u1,v1)(u0,v1)
    south: (x0,y1,z1)(x0,y0,z1)(x1,y0,z1)(x1,y1,z1)  -> (u0,v0)(u1,v0)(u1,v1)(u0,v1)
    west/east/up/down 同理（v0 永远在上的角）
- 背面剔除：法线背对相机（相机 +Z 看向 -Z）的面不画
- 画家算法按面深度排序； shade 默认 true 时按面朝向乘明暗（同 MC：up 1.0 / down 0.5 / x 向 0.6 / z 向 0.8）

用法：
    python preview_block_model.py <model.json> [--tex <texture.png>] [--out <png>]
        [--yaw 45] [--pitch 24] [--scale 14] [--tilt 0] [--spin N]
    --spin N: 额外渲染绕 y 轴每 90/N 度一张，横排拼一张（看四向）。
"""
import argparse
import glob
import json
import math
import os
import sys

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot")

# MC 每个面的顶点序（模型坐标，未旋转）：(dx, dy, dz) 角点。
# 绕序保证 (v1-v0)x(v2-v0) 指向面外方（背面剔除用）。
FACES = {
    "down":  [(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)],
    "up":    [(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)],
    "north": [(0, 1, 0), (1, 1, 0), (1, 0, 0), (0, 0, 0)],
    "south": [(0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)],
    "west":  [(0, 1, 1), (0, 1, 0), (0, 0, 0), (0, 0, 1)],
    "east":  [(1, 1, 0), (1, 1, 1), (1, 0, 1), (1, 0, 0)],
}
FACE_SHADE = {"up": 1.00, "down": 0.50, "north": 0.80, "south": 0.80,
              "west": 0.60, "east": 0.60}


def face_uv(face, corner, u0, v0, u1, v1):
    """按角点的模型空间位置取 uv（与 MC 一致：侧面的 v0 在最高处，即贴图行从上往下）。
    贴图设计成 u 向近似对称，故 u 的方向不影响判读。"""
    cx, cy, cz = corner
    if face in ("north", "south"):
        u = u1 if cx else u0
        v = v0 if cy else v1
    elif face in ("west", "east"):
        u = u1 if cz else u0
        v = v0 if cy else v1
    else:  # up / down：u 沿 x，v 沿 z
        u = u1 if cx else u0
        v = v1 if cz else v0
    return (u, v)


def rot_axis(p, o, axis, deg):
    """绕 axis 过 o 点旋转 deg（右手系）。"""
    x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
    t = math.radians(deg)
    c, s = math.cos(t), math.sin(t)
    if axis == "x":
        y, z = y * c - z * s, y * s + z * c
    elif axis == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:  # z
        x, y = x * c - y * s, x * s + y * c
    return (x + o[0], y + o[1], z + o[2])


def load_model(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def resolve_texture(model_path, model, tex_override=None):
    if tex_override:
        return tex_override
    # textures.particle 或第一个 #var
    var = None
    tex = model.get("textures", {})
    for key in ("particle", "all", "crystal", "0", "layer0"):
        if key in tex:
            var = tex[key]
            break
    if var is None:
        for v in tex.values():
            if isinstance(v, str) and ":" in v and not v.startswith("#"):
                var = v
                break
    if not (var and ":" in var and not var.startswith("#")):
        var = "skydeitymysticpivot:block/" + os.path.splitext(os.path.basename(model_path))[0]
    if os.path.isabs(var) and os.path.exists(var):
        return var
    ns, p = var.split(":", 1)
    # p 可能已含 block/ 或 item/ 子目录
    if p.startswith(("block/", "item/", "models/")):
        cand = os.path.join(ROOT, "src/main/resources/assets", ns, "textures", p + ".png")
    else:
        cand = os.path.join(ROOT, "src/main/resources/assets", ns, "textures/block", p + ".png")
    if os.path.exists(cand):
        return cand
    cand2 = os.path.join(ROOT, "src/main/resources/assets", ns, "textures/item",
                         os.path.basename(p) + ".png")
    return cand2 if os.path.exists(cand2) else None


def load_texture_map(model_path, model):
    """把 model.textures 里的 #var 解析成 {var: PIL.Image}；粒子贴图兜底。"""
    tmap = {}
    for key, val in model.get("textures", {}).items():
        if not isinstance(val, str) or val.startswith("#") or ":" not in val:
            continue
        if os.path.isabs(val) and os.path.exists(val):
            tmap[key] = Image.open(val).convert("RGBA")
            continue
        ns, p = val.split(":", 1)
        if p.startswith(("block/", "item/", "models/")):
            cand = os.path.join(ROOT, "src/main/resources/assets", ns, "textures", p + ".png")
        else:
            cand = os.path.join(ROOT, "src/main/resources/assets", ns, "textures/block", p + ".png")
        if not os.path.exists(cand):
            cand = os.path.join(ROOT, "src/main/resources/assets", ns, "textures/item",
                                os.path.basename(p) + ".png")
        if os.path.exists(cand):
            tmap[key] = Image.open(cand).convert("RGBA")
    return tmap


def model_quads(model, tex_map):
    """产出 [(quad4点, uv4点, face, shade, element_name, tex_image)]，坐标 0-16 方块空间。"""
    out = []
    default_tex = next(iter(tex_map.values())) if tex_map else None
    for el in model.get("elements", []):
        frm = [float(v) for v in el["from"]]
        to = [float(v) for v in el["to"]]
        rot = el.get("rotation")
        origin = [float(v) for v in rot["origin"]] if rot else None
        axis = rot["axis"] if rot else None
        angle = float(rot["angle"]) if rot else 0.0
        name = el.get("name", "?")
        shade_on = el.get("shade", True)
        for face, corners in FACES.items():
            spec = el.get("faces", {}).get(face)
            if not spec:
                continue
            uv = spec.get("uv")
            if uv is None:
                # 无 uv：按面尺寸自动取整块（兼容省略 uv 的模型）
                u0, v0, u1, v1 = 0, 0, 16, 16
            else:
                u0, v0, u1, v1 = [float(v) for v in uv]
            tex_ref = spec.get("texture", "")
            timg = default_tex
            if isinstance(tex_ref, str) and tex_ref.startswith("#"):
                timg = tex_map.get(tex_ref[1:], default_tex)
            pts, uvs = [], []
            for i, (cx, cy, cz) in enumerate(corners):
                raw = [frm[0] + cx * (to[0] - frm[0]),
                       frm[1] + cy * (to[1] - frm[1]),
                       frm[2] + cz * (to[2] - frm[2])]
                uvs.append(face_uv(face, (cx, cy, cz), u0, v0, u1, v1))
                if rot:
                    raw = rot_axis(raw, origin, axis, angle)
                pts.append(tuple(raw))
            sh = FACE_SHADE[face] if shade_on else 1.0
            out.append((pts, uvs, face, sh, name, timg))
    return out


def render(quads, tex, yaw, pitch, scale=14.0, tilt=0.0, bg=(22, 22, 27),
           pad=40, ambient=0.92, span=16.0, span_y=22.0):
    W = int(span * scale) + 2 * pad + 120
    H = int(span_y * scale) + 2 * pad + 60
    im = Image.new("RGBA", (W, H), bg + (255,))
    px = im.load()
    cx, cy = W / 2, H / 2 + span_y * scale / 2   # y=0 落在画面下半，模型整体居中

    def rotate(p, ya, pi):
        x, y, z = p
        x, z = x * math.cos(ya) + z * math.sin(ya), -x * math.sin(ya) + z * math.cos(ya)
        y, z = y * math.cos(pi) - z * math.sin(pi), y * math.sin(pi) + z * math.cos(pi)
        return (x, y, z)

    # 按几何包围盒的中心来居中（不论 1 格零件还是 N×N 整阵都自动对中）
    allx = [p[0] for q in quads for p in q[0]]
    allz = [p[2] for q in quads for p in q[0]]
    ox = (min(allx) + max(allx)) / 2.0
    oz = (min(allz) + max(allz)) / 2.0

    def project(p):
        q = rotate((p[0] - ox, p[1], p[2] - oz), yaw, pitch)
        if tilt:
            x, y, z = q
            q = (x * math.cos(tilt) - y * math.sin(tilt),
                 x * math.sin(tilt) + y * math.cos(tilt), z)
        return (cx + q[0] * scale, cy - q[1] * scale, q[2])

    entries = []
    for pts, uvs, face, shade, name, qtex in quads:
        if qtex is None:
            continue
        rp = [rotate(p, yaw, pitch) for p in pts]
        # 背面剔除：相机在 +Z，法线（顶点 0,1,2 叉积）朝 +Z 才画
        ux, uy, uz = (rp[1][i] - rp[0][i] for i in range(3))
        vx, vy, vz = (rp[2][i] - rp[0][i] for i in range(3))
        nx, ny, nz = uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx
        if nz < 0:
            continue
        depth = sum(p[2] for p in rp) / 4
        entries.append((depth, pts, uvs, face, shade, qtex))
    entries.sort(key=lambda e: e[0])

    for _, pts, uvs, face, shade, qtex in entries:
        pp = [project(p) for p in pts]
        tw, th = qtex.size
        tris = [(0, 1, 2), (0, 2, 3)]
        for a, b, c in tris:
            (ax, ay, _), (bx, by, _), (ccx, ccy, _) = pp[a], pp[b], pp[c]
            (au, av), (bu, bv), (cu, cv) = uvs[a], uvs[b], uvs[c]
            minx = max(0, int(math.floor(min(ax, bx, ccx))))
            maxx = min(W - 1, int(math.ceil(max(ax, bx, ccx))))
            miny = max(0, int(math.floor(min(ay, by, ccy))))
            maxy = min(H - 1, int(math.ceil(max(ay, by, ccy))))
            den = (by - ccy) * (ax - ccx) + (ccx - bx) * (ay - ccy)
            if abs(den) < 1e-9:
                continue
            for yy in range(miny, maxy + 1):
                for xx in range(minx, maxx + 1):
                    l1 = ((by - ccy) * (xx - ccx) + (ccx - bx) * (yy - ccy)) / den
                    l2 = ((ccy - ay) * (xx - ccx) + (ax - ccx) * (yy - ccy)) / den
                    l3 = 1 - l1 - l2
                    if l1 < -0.002 or l2 < -0.002 or l3 < -0.002:
                        continue
                    u = l1 * au + l2 * bu + l3 * cu
                    v = l1 * av + l2 * bv + l3 * cv
                    tx, ty = int(math.floor(u)) % tw, int(math.floor(v)) % th
                    r, g, b, a = qtex.getpixel((tx, ty))
                    if a < 16:
                        continue
                    s = shade * ambient
                    px[xx, yy] = (min(255, int(r * s)), min(255, int(g * s)),
                                  min(255, int(b * s)), 255)
    return im


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("model")
    ap.add_argument("--tex")
    ap.add_argument("--out")
    ap.add_argument("--yaw", type=float, default=45)
    ap.add_argument("--pitch", type=float, default=24)
    ap.add_argument("--scale", type=float, default=14)
    ap.add_argument("--tilt", type=float, default=0)
    ap.add_argument("--spin", type=int, default=0, help="额外渲染 N 个方位拼横排")
    ap.add_argument("--grid", type=int, default=1, help="把模型在 N×N 平面上排开（看多方块）")
    ap.add_argument("--span", type=float, default=0, help="覆盖画面跨度（小尺寸零件用）")
    ap.add_argument("--span-y", type=float, default=0, help="覆盖画面纵向跨度")
    args = ap.parse_args()

    model = load_model(args.model)
    tex_map = load_texture_map(args.model, model)
    if not tex_map:
        sys.exit("no textures resolved for %s" % args.model)
    quads = model_quads(model, tex_map)
    grid = max(1, args.grid)
    if grid > 1:
        big = []
        for i in range(grid):
            for j in range(grid):
                for pts, uvs, f_, sh, nm, qt in quads:
                    big.append(([(p[0] + i * 16, p[1], p[2] + j * 16) for p in pts],
                                uvs, f_, sh, nm, qt))
        quads = big
    span = args.span if args.span else 16.0 * grid
    print("model:", args.model, "| elements:", len(model.get("elements", [])),
          "| quads:", len(quads), "| textures:", sorted(tex_map))

    views = [(args.yaw, args.pitch, args.tilt)]
    if args.spin:
        for i in range(1, args.spin):
            views.append((args.yaw + i * 90.0 / args.spin, args.pitch, args.tilt))
    tiles = []
    for ya, pi, ti in views:
        im = render(quads, None, math.radians(ya), math.radians(pi),
                    scale=args.scale, tilt=math.radians(ti), span=span,
                    span_y=(args.span_y if args.span_y else max(22.0, span)))
        tiles.append(im)
    if len(tiles) == 1:
        out = tiles[0]
    else:
        W = sum(t.width + 12 for t in tiles)
        H = max(t.height for t in tiles)
        out = Image.new("RGBA", (W, H), (22, 22, 27, 255))
        x = 0
        for t in tiles:
            out.paste(t, (x, 0))
            x += t.width + 12
    out_path = args.out or os.path.join(
        ROOT, "tools/preview",
        os.path.splitext(os.path.basename(args.model))[0] + "_prev.png")
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    out.save(out_path)
    print("saved:", out_path)


if __name__ == "__main__":
    main()
