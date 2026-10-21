#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
玄枢净界石（xuanshu_purifying_stone）—— 第七十四批：**从 26 件石坛改回纯单方块**。

用户原话：
  > 1.净界石建模太乱了，改回方块

<h2>为什么退回去</h2>
第七十 / 七十二批把它从「一个方块」堆成三层须弥座 + 四角望柱 + 石鼓 + 壁龛 + 檐口 + 三收冠
+ 宝顶座 + 顶珠，**26 件 element**。机器渲出来是座小塔，进游戏落在草丛里、立在阵心正中
都是一坨碎件，用户判定「太乱」。本批整套撤回：

* 模型回到 `minecraft:block/cube_all`（一张贴图吃六个面）；
* 碰撞 / 遮挡形状回到整块 `Shapes.block()`（Java 里那两段逐件 VoxelShape 一并作废）；
* 贴图**重画成一张六面通用的方块面** —— 老贴图是按「须弥座 / 石鼓 / 壁龛」分区画的
  （左半横向色带、右下两幅卦、右上圆印），六面同一张会花得离谱。

<h2>新贴图的设计</h2>
沿用第七十二批定下来的配色（深青石身 + 鎏金线脚 + 浅青井水），只留三样东西：

<pre>
  ┌────────────────┐
  │ 鎏金外框（1px） │  ← 与卦位石同一套「框住一块石头」的读法
  │  ┌──────────┐  │
  │  │   石面    │  │  ← 深青石，四角各提亮一格
  │  │   ┌──┐   │  │
  │  │   │井印│   │  │  ← 金方框 + 浅青井水（「净化之井」的记号）
  │  │   └──┘   │  │
  │  └──────────┘  │
  └────────────────┘
</pre>

六面完全同图：从哪个方向看都是一块「刻着净井印的青金石」，没有方向性，也不碎。

★ 仍是 **16×16**，且不透明（alpha 全 255）—— 方块不需要 cutout，
  `render_type` 也随之下掉（`cube_all` 走默认 solid）。
"""
import json
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TEXDIR = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/textures/block")
BLOCKDIR = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/models/block")
PREVIEW_DIR = os.path.join(ROOT, "tools/preview")
TEX = os.path.join(TEXDIR, "xuanshu_purifying_stone.png")

SIZE = 16

# 配色 —— 全部取自第七十二批那版贴图的实测色，一件不改
STONE_D = (34, 68, 74)      # 石身暗部
STONE = (46, 88, 92)        # 石身主色
STONE_L = (74, 124, 126)    # 石身亮部（四角）
GOLD = (217, 180, 94)       # 鎏金
GOLD_L = (242, 216, 148)    # 鎏金高光
GOLD_D = (168, 132, 62)     # 鎏金暗部
WATER = (134, 192, 190)     # 井水
WATER_L = (220, 248, 242)   # 井水高光

MODEL_ID = "skydeitymysticpivot:block/xuanshu_purifying_stone"


def frame(px, x0, y0, x1, y1, color):
    """在 (x0,y0)-(x1,y1) 闭区间的**边框**上刷色（1 px）。"""
    for x in range(x0, x1 + 1):
        px[y0][x] = color
        px[y1][x] = color
    for y in range(y0, y1 + 1):
        px[y][x0] = color
        px[y][x1] = color


def rect(px, x0, y0, x1, y1, color):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            px[y][x] = color


def build_tile():
    px = [[STONE_D for _ in range(SIZE)] for _ in range(SIZE)]

    rect(px, 1, 1, 14, 14, STONE)            # 石面主体
    for cx, cy in ((1, 1), (14, 1), (1, 14), (14, 14)):
        px[cy][cx] = STONE_L                 # 四角提亮一格，免得石面发死

    frame(px, 0, 0, 15, 15, GOLD)            # 外框：鎏金
    frame(px, 1, 1, 14, 14, GOLD_D)          # 内框线：鎏金暗部（双线金框）

    frame(px, 5, 5, 10, 10, GOLD)            # 井印外框
    rect(px, 6, 6, 9, 9, WATER)              # 井水
    rect(px, 7, 7, 8, 8, WATER_L)            # 井心高光

    # 井印与四边之间各点一枚小金点，把「一块方正石头」的读法点出来
    for gx, gy in ((3, 3), (12, 3), (3, 12), (12, 12)):
        px[gy][gx] = GOLD_L

    im = Image.new("RGBA", (SIZE, SIZE))
    im.putdata([px[y][x] + (255,) for y in range(SIZE) for x in range(SIZE)])
    return im


def write_cube_all(path, texture):
    model = {
        "parent": "minecraft:block/cube_all",
        "textures": {"all": texture},
    }
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        json.dump(model, handle, ensure_ascii=False, indent=2)
        handle.write("\n")


def main():
    tile = build_tile()
    tile.save(TEX)
    print("block tex   ->", os.path.relpath(TEX, ROOT), tile.size)

    write_cube_all(os.path.join(BLOCKDIR, "xuanshu_purifying_stone.json"), MODEL_ID)
    write_cube_all(os.path.join(BLOCKDIR, "xuanshu_purifying_stone_active.json"),
                   "skydeitymysticpivot:block/array_station_active")
    print("block model -> cube_all ×2（常态 = 净界石贴图 / 成阵态 = 青金祭坛贴图）")

    os.makedirs(PREVIEW_DIR, exist_ok=True)
    tile.resize((SIZE * 16, SIZE * 16), Image.NEAREST).save(
        os.path.join(PREVIEW_DIR, "purifying_stone_tex.png"))
    print("preview     -> tools/preview/purifying_stone_tex.png")


if __name__ == "__main__":
    main()
