#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
枫凝菼蒄（MAPLE_CRYSTAL）的 16×16 物品贴图 —— 第二百二十五批按参考图重画梗。

用户原话：「maple_crystal 贴图的柄要和我给到的图片一样」——参考图（双樱桃）的梗是：
顶端一个**十字小枝杈**（竖一截 + 横一杠），从横杠右端一条**阶梯斜线**向右下接
**右边（略高）**的果子，从横杠下方一根**竖直短梗**垂下来接**左边（略低）**的果子；
左侧还伸出一小截横臂。第二百二十四批曾把左右高低画反、杈也太小，本批整个梗照参考图重排，
果子仍是 7×7 圆盘、配色仍是一整片鲜红 + 橘（梗为橘色）。

形制：右果略高（行 8~14）、左果略低（行 9~15），两果相抵；
梗全部单色橘、扁平无暗面（照参考图的扁平梗）。16px 里全是硬边、不掺半透明。

    图例  o=深红描边 r=正红 R=亮红 h=橘白高光 g=橘色梗
"""
import os
import struct
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/item/maple_crystal.png")

W = H = 16

PALETTE = {
    "o": (104, 12, 20),      # 深红描边
    "r": (214, 30, 44),      # 正红
    "R": (242, 70, 64),      # 亮红
    "h": (255, 178, 130),    # 橘白高光
    "g": (234, 136, 46),     # 橘色梗
}

# 梗的结构（列, 行）：十字杈 = 竖 (5,0)~(5,1) + 横杠 (3,2)~(7,2)；
# 斜线 (8,3)→(9,4)→(9,5)→(10,6)→(11,7) 接右果顶 (11,8)；
# 竖梗 (4,3)~(4,8) 接左果顶 (4,9)；横臂 (1,4)~(4,4) 从竖梗向左伸出。
# 右果 = 7×7 圆盘放 (8,8)，左果 = 7×7 圆盘放 (1,9)。
ART = [
    ".....g..........",
    ".....g..........",
    "...ggggg........",
    "....g...g.......",
    ".gggg....g......",
    "....g....g......",
    "....g.....g.....",
    "....g......g....",
    "..........ooo...",
    "...ooo..ohhRo...",
    "..ohhRo.ohRRRRo.",
    ".ohRRRRoorRRRRo.",
    ".orRRRRoorRRRRo.",
    ".orRRRRo.orrro..",
    "..orrro...ooo...",
    "...ooo..........",
]


def build():
    raw = bytearray()
    for row in ART:
        assert len(row) == W, row
        raw.append(0)
        for ch in row:
            if ch == ".":
                raw.extend((0, 0, 0, 0))
            else:
                c = PALETTE[ch]
                raw.extend((c[0], c[1], c[2], 255))

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    ihdr = struct.pack(">IIBBBBB", W, H, 8, 6, 0, 0, 0)
    blob = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))
    with open(OUT, "wb") as f:
        f.write(blob)
    print("written %s (%dx%d)" % (os.path.relpath(OUT, ROOT), W, H))


if __name__ == "__main__":
    build()
