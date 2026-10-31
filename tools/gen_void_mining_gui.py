#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
玄枢虚空水晶塔界面的小件（第二百一十二批）。

<h2>为什么有这支脚本</h2>

用户口径：「给产物栏左边隔远一点添加竖着的 3 个格子，<b>里面画上锭的描边</b>，
整体亮色 AE 风格不变」。空槽里要有一枚「锭」的影子，玩家才知道这三格是插锭的。
直接画一枚彩色铁锭会太像真的，所以只做**描边 + 极淡的填充**。

<h2>描边怎么来</h2>

不手绘剪影 —— 取原版 `iron_ingot.png` 的 **alpha 通道**当形状：
原版那块锭的斜边、右上缺角一根不差，画出来就是玩家认得的「锭」。
在此基础上分两层上色：

  * **轮廓**（自身不透明、四邻里有透明的那些像素）：`#4A5478`、alpha 235，
    比面板描边浅一点，压在 AE 浅灰蓝底上不刺眼；
  * **内部**（其余不透明像素）：`#8E93A8`、alpha 70 —— 几乎只剩一层雾，
    一眼看得出是「空槽的提示」而不是「里面有东西」。

只出这一张 16×16，不碰任何方块 / 物品贴图。

用法：
    python tools/gen_void_mining_gui.py
"""
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "tools/ref/vanilla_iron_ingot.png")
OUT_DIR = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/gui")
OUT = os.path.join(OUT_DIR, "ingot_ghost.png")

# 描边与内部两层的颜色（RGBA）
# 第二百一十四批按用户口径「把之前锭的也改淡一点」整体降一档：描边 235 → 150、填充 70 → 44，
# 与新的附魔书描边（`gen_arcane_table_gui.py`）同一浓度 —— 两个界面里
# 「空槽提示」都只是一层影，不再像真放了东西。
# 第二百一十五批用户：「矿机锭的轮廓和奥术台附魔书的轮廓再淡一点」—— 再降一档：
# 描边 150 → 110、填充 44 → 30。压在 AE 浅灰蓝底上只剩一道浅影，抬头才看得见。
EDGE = (0x4A, 0x54, 0x78, 110)
FILL = (0x8E, 0x93, 0xA8, 30)


def main():
    src = Image.open(SRC).convert("RGBA")
    if src.size != (16, 16):
        raise SystemExit("原版铁锭应当是 16×16，实际 %s" % (src.size,))
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
    opaque = sum(1 for y in range(16) for x in range(16) if alpha[y][x])
    print("写出 %s（%d 个不透明像素，其中描边 %d）"
          % (os.path.relpath(OUT, ROOT), opaque,
             sum(1 for y in range(16) for x in range(16)
                 if out.getpixel((x, y))[3] == EDGE[3])))


if __name__ == "__main__":
    main()
