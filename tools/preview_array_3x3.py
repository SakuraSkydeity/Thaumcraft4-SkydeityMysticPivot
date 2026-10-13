#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
八方净界阵 3×3 整阵预览：外八格用站石模型、正中用阵心模型，
按游戏里的真实摆法合成渲染（阵心在中央，八方各一站石）。

用法：python tools/preview_array_3x3.py [--yaw 45] [--pitch 28] [--scale 7] [--spin 4]
"""
import argparse
import math
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from preview_block_model import (ROOT, load_model, load_texture_map,  # noqa: E402
                                 model_quads, render)

MODELS = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/models/block")


def build_quads():
    station = load_model(os.path.join(MODELS, "array_station_active.json"))
    core = load_model(os.path.join(MODELS, "xuanshu_purifying_stone_active.json"))
    sq = model_quads(station, load_texture_map("array_station_active.json", station))
    cq = model_quads(core, load_texture_map("xuanshu_purifying_stone_active.json", core))
    quads = []
    for i in range(3):
        for j in range(3):
            src = cq if (i == 1 and j == 1) else sq
            for pts, uvs, f_, sh, nm, qt in src:
                quads.append(([(p[0] + i * 16, p[1], p[2] + j * 16) for p in pts],
                              uvs, f_, sh, nm, qt))
    return quads


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--yaw", type=float, default=45)
    ap.add_argument("--pitch", type=float, default=28)
    ap.add_argument("--scale", type=float, default=7)
    ap.add_argument("--tilt", type=float, default=0)
    ap.add_argument("--spin", type=int, default=0)
    ap.add_argument("--out", default=None)
    args = ap.parse_args()

    quads = build_quads()
    print("array quads:", len(quads))
    views = [(args.yaw, args.pitch, args.tilt)]
    if args.spin:
        for i in range(1, args.spin):
            views.append((args.yaw + i * 90.0 / args.spin, args.pitch, args.tilt))
    tiles = []
    for ya, pi, ti in views:
        im = render(quads, None, math.radians(ya), math.radians(pi),
                    scale=args.scale, tilt=math.radians(ti), span=48.0, span_y=21.0)
        tiles.append(im)
    if len(tiles) == 1:
        out = tiles[0]
    else:
        W = sum(t.width + 16 for t in tiles)
        H = max(t.height for t in tiles)
        out = Image.new("RGBA", (W, H), (22, 22, 27, 255))
        x = 0
        for t in tiles:
            out.paste(t, (x, 0))
            x += t.width + 16
    path = args.out or os.path.join(ROOT, "tools/preview/array_3x3_new.png")
    out.save(path)
    print("saved:", path)


if __name__ == "__main__":
    main()
