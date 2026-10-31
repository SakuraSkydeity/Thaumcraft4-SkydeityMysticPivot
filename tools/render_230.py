# -*- coding: utf-8 -*-
"""第二百三十批布局预览图：读 bagua.json 画节点与连线。"""
import json, math
from PIL import Image, ImageDraw, ImageFont

PATH = "src/main/resources/data/skydeitymysticpivot/thaumcraft/research/bagua.json"
OUT = ".none/lecture/研究树新布局-230.png"
S = 100
R = 32

with open(PATH, "r", encoding="utf-8") as f:
    data = json.load(f)

pm, names = {}, {}
for e in data["entries"]:
    s = e["key"].split(":")[1]
    pm[s] = (e["display_column"], e["display_row"])
    names[s] = s

xs = [c for c, r in pm.values()]
ys = [r for c, r in pm.values()]
minx, maxx, miny, maxy = min(xs), max(xs), min(ys), max(ys)
W = (maxx - minx + 3) * S
H = (maxy - miny + 3) * S
OX = (1 - minx) * S
OY = (1 - miny) * S

def P(c, r):
    return (OX + c * S, OY + r * S)

img = Image.new("RGB", (W, H), (250, 247, 240))
d = ImageDraw.Draw(img)
try:
    font = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 22)
    small = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 16)
except OSError:
    font = small = ImageFont.load_default()

ZH = {
    "PEDESTAL": "玄枢台座", "BAGUA": "玄枢要素", "STAR_CRAFT": "星辰合成",
    "XUANSHU_INGOT_UNFIRED": "未烬玄枢锭", "XUANSHU_INGOT_FORGED": "玄枢锭",
    "XUANSHU_SPIRIT_CORE": "沟灵核心", "XUANSHU_REPLICATOR": "复刻台",
    "PURIFYING_STONE": "净秽石", "XUANSHU_ARCANE_TABLE": "奥术台",
    "XUANSHU_NODE_MODIFIER": "节点改造器", "XUANSHU_CHARGER": "充能器",
    "INGOTS": "四神兽之锭", "SHARDS": "玄枢碎片", "CLOTHS": "玄枢布匹",
    "MACHINE_FURNACE": "炎炉", "MACHINE_CRUCIBLE": "坩埚", "MACHINE_ALCHEMY": "炼金炉",
    "BAGUA_PICKAXE": "八卦镐", "XUANSHU_HOE": "玄枢锄", "XUANSHU_PICKAXE": "玄枢镐",
    "WANDS": "杖柄", "CAPS": "杖端", "WAND_ASSEMBLY": "法杖装配",
    "FOCUS_GUARDIAN": "乾元罡气", "FOCUS_CHRONO": "瞬", "FOCUS_COSMOS": "寰宇",
    "FOCUS_TELEPORT": "挪移", "TERRAIN": "八卦之地气", "GUA_STONES": "卦石",
    "GUA_QIAN": "乾", "GUA_DUI": "兑", "GUA_LI": "离", "GUA_ZHEN": "震",
    "GUA_XUN": "巽", "GUA_KAN": "坎", "GUA_GEN": "艮", "GUA_KUN": "坤",
}

edges = []
for e in data["entries"]:
    s = e["key"].split(":")[1]
    for par in e.get("parents", []):
        edges.append((par.split(":")[1], s))

for a, b in edges:
    d.line([P(*pm[a]), P(*pm[b])], fill=(150, 145, 135), width=3)

for s, (c, r) in pm.items():
    x, y = P(c, r)
    moved = s in ("MACHINE_FURNACE", "MACHINE_CRUCIBLE", "MACHINE_ALCHEMY",
                  "FOCUS_GUARDIAN", "BAGUA")
    color = (214, 30, 44) if moved else (90, 86, 80)
    d.ellipse([x - R, y - R, x + R, y + R], outline=color, width=4,
              fill=(255, 236, 236) if moved else (255, 255, 255))
    label = ZH.get(s, s)
    d.text((x, y - 12), label, font=font, fill=(40, 38, 35), anchor="mm")
    d.text((x, y + 14), "(%d,%d)" % (c, r), font=small, fill=(120, 115, 108), anchor="mm")

img.save(OUT)
print("saved " + OUT, img.size)
