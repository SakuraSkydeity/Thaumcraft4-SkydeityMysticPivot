# -*- coding: utf-8 -*-
"""把研究树离线画成 PNG —— 排完坐标不用进游戏就能看「挤不挤、连得顺不顺」。

用法：
    python tools/preview_tree.py [输出路径]

画法与游戏里那本书对齐：格子 24 像素、节点 22 像素（图标 16 像素居中）、
连线走节点中心；「卦石」「星辰合成」两支按第 151 批的做法画成蓝色粗线。
节点下面那行小字是给作者看的名（游戏里不显示），用来核对哪一格是谁。
"""
import json
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RESEARCH = os.path.join(ROOT, "src/main/resources/data/skydeitymysticpivot/thaumcraft/research/bagua.json")
ASSETS = os.path.join(ROOT, "src/main/resources/assets")
OUT = sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "tools/preview/tree_batch151.png")

CELL = 24
NODE = 22
SCALE = 3
PAD = 40


# 节点底下的小字（纯 ASCII：Pillow 自带字体没有中文，写中文就是一堆方框）
SHORT = {
    "BAGUA": "bagua", "PEDESTAL": "ped", "TERRAIN": "land", "GUA_STONES": "stone",
    "STAR_CRAFT": "strcr", "XUANSHU_INGOT_UNFIRED": "unfired", "XUANSHU_INGOT_FORGED": "ingot",
    "INGOTS": "beast", "SHARDS": "shard", "CLOTHS": "cloth", "WANDS": "rod", "CAPS": "cap",
    "WAND_ASSEMBLY": "wand", "XUANSHU_SPIRIT_CORE": "core", "XUANSHU_ARCANE_TABLE": "arcane",
    "XUANSHU_NODE_MODIFIER": "nodmod", "MACHINE_FURNACE": "furn", "MACHINE_CRUCIBLE": "cruc",
    "MACHINE_ALCHEMY": "alch", "XUANSHU_CHARGER": "charg", "PURIFYING_STONE": "purif",
    "BAGUA_PICKAXE": "pick1", "XUANSHU_PICKAXE": "pick2", "XUANSHU_HOE": "hoe",
    "FOCUS_GUARDIAN": "fg", "FOCUS_TELEPORT": "ft", "FOCUS_COSMOS": "fc",
}


def icon_path(icon):
    kind = icon.get("type")
    if kind == "STACK":
        item = icon["stack"]["id"].split(":")[-1]
        namespace = icon["stack"]["id"].split(":")[0]
        path = os.path.join(ASSETS, namespace, "textures/item", item + ".png")
        return path if os.path.exists(path) else None
    if kind == "TEXTURE":
        rel = icon["texture"].split(":")[-1]
        namespace = icon["texture"].split(":")[0]
        path = os.path.join(ASSETS, namespace, rel)
        return path if os.path.exists(path) else None
    return None


def link(draw, a, b, colour, width):
    draw.line([a, b], fill=colour, width=width)
    for point in (a, b):
        pass


def main():
    data = json.load(open(RESEARCH, encoding="utf-8"))
    entries = {e["key"]: e for e in data["entries"]}

    cols = [e["display_column"] for e in data["entries"]]
    rows = [e["display_row"] for e in data["entries"]]
    min_col, max_col = min(cols), max(cols)
    min_row, max_row = min(rows), max(rows)

    width = (max_col - min_col + 1) * CELL + PAD * 2
    height = (max_row - min_row + 1) * CELL + PAD * 2 + 14
    canvas = Image.new("RGBA", (width * SCALE, height * SCALE), (24, 24, 30, 255))
    draw = ImageDraw.Draw(canvas)
    font = ImageFont.load_default(size=7 * SCALE)

    def centre(entry):
        x = PAD + (entry["display_column"] - min_col) * CELL + NODE // 2
        y = PAD + (entry["display_row"] - min_row) * CELL + NODE // 2
        return x * SCALE, y * SCALE

    # 细线：普通父子关系
    for entry in data["entries"]:
        for parent_key in entry.get("parents") or []:
            parent = entries.get(parent_key)
            if parent is None:
                continue
            a, b = centre(entry), centre(parent)
            draw.line([a, b], fill=(70, 70, 92, 255), width=SCALE)


    # 节点
    for entry in data["entries"]:
        cx, cy = centre(entry)
        half = (NODE // 2) * SCALE
        box = [cx - half, cy - half, cx + half, cy + half]
        draw.rounded_rectangle(box, radius=3 * SCALE, fill=(58, 58, 72, 255), outline=(120, 120, 140, 255))

        path = icon_path(entry["icon"])
        if path:
            icon = Image.open(path).convert("RGBA").resize((16 * SCALE, 16 * SCALE), Image.NEAREST)
            canvas.alpha_composite(icon, (cx - 8 * SCALE, cy - 8 * SCALE))
        else:
            draw.ellipse([cx - 7 * SCALE, cy - 7 * SCALE, cx + 7 * SCALE, cy + 7 * SCALE],
                         fill=(150, 120, 60, 255))

        name = entry["key"].split(":")[1]
        label = SHORT.get(name, name[:4])
        draw.text((cx, cy + half + 2 * SCALE), label, font=font, fill=(190, 190, 205, 255), anchor="ma")

    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    canvas.save(OUT)
    print("written", OUT, canvas.size)


if __name__ == "__main__":
    main()
