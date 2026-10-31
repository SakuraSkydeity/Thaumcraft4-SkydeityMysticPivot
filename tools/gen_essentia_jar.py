#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
玄枢源质罐的液体贴图生成器（第一百九十三批）。

<h2>为什么只有一张近白的贴图</h2>

鎏金的颜色是<b>顶点色</b>上的（见 {@code XuanshuEssentiaJarRenderer} 的 DEEP / MID / LIGHT
三档），贴图只负责给液面一点「不是纯色板」的细节，所以这张图几乎是白的：
色相全部交给顶点色，贴图只提供明暗的轻微起伏。想要改金光，改 Java 里那三组色，
不用回来重画这张图。

<h2>画什么</h2>

16×16，基底 244 灰，叠两道很缓的竖向条纹（读作液体内部的流痕），再加 ±4 的细噪点。
噪点是<b>固定种子</b>的，重跑一次得到的是同一张图，jar 指纹不会因为重新生成而变。

用法：

    python tools/gen_essentia_jar.py
"""

import os
import random

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/block/essentia_glow.png")

SIZE = 16
BASE = 244
STREAK = 9       # 竖条纹的振幅
GRAIN = 4        # 细噪点振幅
SEED = 193


def main():
    rng = random.Random(SEED)
    img = Image.new("RGBA", (SIZE, SIZE))
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            # 两道缓竖纹：让液体内部有一点走向，避免整片死平
            streak = STREAK * (0.55 * _wave(x, 2, SIZE) + 0.45 * _wave(x, 5, SIZE))
            grain = rng.uniform(-GRAIN, GRAIN)
            value = int(round(BASE + streak + grain))
            value = max(0, min(255, value))
            px[x, y] = (value, value, value, 255)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    img.save(OUT)
    print("出图：%s" % OUT)


def _wave(x, period, size):
    """周期为 {@code period} 个像素的正弦，取值 {@code [-1,1]}。"""
    import math
    return math.sin(2.0 * math.pi * period * x / size)


if __name__ == "__main__":
    main()
