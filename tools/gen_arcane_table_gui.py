#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
玄枢奥术台界面的小件（第二百一十四批）。

<h2>为什么有这支脚本</h2>

用户口径：「奥术台将原来玄枢锭一格栏位，改为可以放附魔书（可选）……画一个和附魔书一样的轮廓，
稍微淡一点，把之前锭的也改淡一点」。于是书槽空着的时候要有一本「附魔书的影子」，
玩家才知道这格是放书的；同时原来水晶塔那枚锭的描边要跟着淡一档，两处观感一致。

<h2>描边怎么来</h2>

不手绘剪影 —— 取本 mod 自己那张 `disenchant_book.png` 的 **alpha 通道**当形状
（它就是原版附魔书换色来的，剪影与本体逐像素一致）：
书的斜边、书脊、右上缺角一根不差，画出来就是玩家认得的「附魔书」。在此基础上分两层上色：

  * **轮廓**（自身不透明、四邻里有透明的那些像素）：`#4A5478`、alpha **110**
    （第二百一十四批 150，第二百一十五批按用户「再淡一点」再降一档，
    压在 AE 浅灰蓝底上只剩一道浅影）；
  * **内部**（其余不透明像素）：`#8E93A8`、alpha **30** —— 几乎看不见，只是让书影有块面。

只出这一张 16×16，不碰任何方块 / 物品贴图（锭那枚由 `gen_void_mining_gui.py` 同步改淡）。

用法：
    python tools/gen_arcane_table_gui.py
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/item/disenchant_book.png")
OUT_DIR = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/gui")
OUT = os.path.join(OUT_DIR, "book_ghost.png")

# 描边与内部两层的颜色（RGBA）：比 212 批那枚锭再淡一档；215 批再降一档
EDGE = (0x4A, 0x54, 0x78, 110)
FILL = (0x8E, 0x93, 0xA8, 30)


def main():
    src = Image.open(SRC).convert("RGBA")
    if src.size != (16, 16):
        raise SystemExit("附魔书应当是 16×16，实际 %s" % (src.size,))
    alpha = [[src.getpixel((x, y))[3] for x in range(16)] for y in range(16)]

    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = out.load()
    for y in range(16):
        for x in range(16):
            if alpha[y][x] == 0:
                continue
            edge = False
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if not (0 <= nx < 16 and 0 <= ny < 16) or alpha[ny][nx] == 0:
                    edge = True
                    break
            px[x, y] = EDGE if edge else FILL

    os.makedirs(OUT_DIR, exist_ok=True)
    out.save(OUT)
    print("写出 %s（%d 个不透明像素，其中描边 %d）"
          % (os.path.relpath(OUT, ROOT),
             sum(1 for y in range(16) for x in range(16) if alpha[y][x]),
             sum(1 for y in range(16) for x in range(16) if out.getpixel((x, y))[3] == EDGE[3])))


if __name__ == "__main__":
    main()
