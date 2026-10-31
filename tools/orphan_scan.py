# -*- coding: utf-8 -*-
"""第 80 批临时脚本 v2：孤儿资源扫描（只报告，不删）。

规则（MC 命名约定 + 注册表对账）：
  textures/item/<n>.png   → 任一 json 含 "skydeitymysticpivot:item/<n>"，或物品 <n> 已注册（模型约定）
  textures/block/<n>.png  → 任一 json 含 "block/<n>"
  textures/gui|misc|models/<n>.png → 任一文本含 "gui|misc|models/<n>"
  textures/aspects/<n>.png → 要素数据 <n>.json 存在（本体按 id 取图）
  models/item/<n>.json    → 物品 <n> 已注册 或 被文本引用 "item/<n>"
  models/block/<n>.json   → 被文本引用 "block/<n>"
  blockstates/<n>.json    → 方块 <n> 已注册

第一百八十四批补三类「视野外引用」（工程坑 #54 的收编 —— 以前每次都要人工复核）：
  * textures/models/<n>.png 法杖杆 / 杖帽：本体渲染器按约定名取图，Java 侧只有
    `wandPart("<n>", …)` / `new WandPreset("<n>", …)`，没有 "models/" 前缀；
  * textures/item/focus_*_orn.png：被 focus_catalog/*.json 的 `ornament_texture`
    以全路径 "skydeitymysticpivot:textures/item/<n>.png" 引用；
  * models/_ 前缀的临时文件：等距预览工具现场生成的预览 OBJ（生成它的
    `preview_void_machine.py` 第 185 批已删，现存的是历史残留），只在 tools/ 里被引用。
"""
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/skydeitymysticpivot")
DATA = os.path.join(RES, "data")
JAVA = os.path.join(ROOT, "src/main/java")

# ---------- 读全部文本 ----------
texts = {}
for base in (RES, JAVA):
    for dirpath, _, files in os.walk(base):
        for f in files:
            if f.endswith((".json", ".java", ".mcmeta", ".toml", ".mtl", ".txt", ".obj")):
                p = os.path.join(dirpath, f)
                try:
                    texts[p] = open(p, encoding="utf-8").read()
                except UnicodeDecodeError:
                    pass

ALL_TEXT = "\n".join(texts.values())

# ---------- 注册名 ----------
moditems = open(os.path.join(JAVA, "skydeity", "mysticpivot", "ModItems.java"), encoding="utf-8").read()
modblocks = open(os.path.join(JAVA, "skydeity", "mysticpivot", "ModBlocks.java"), encoding="utf-8").read()
items = set(re.findall(r'register(?:Simple)?Item\(\s*"([^"]+)"', moditems))
items |= set(re.findall(r'baguaStone\("([^"]+)"', moditems))
blocks = set(re.findall(r'registerBlock\(\s*"([^"]+)"', modblocks))
blocks |= set(re.findall(r'baguaStone\("([^"]+)"\)', modblocks))

print("registered items :", len(items))
print("registered blocks:", len(blocks))
print()

print("=" * 70)
print("A. 贴图孤儿（含判定依据）")
print("=" * 70)
n_orphan = 0
for dirpath, _, files in os.walk(os.path.join(ASSETS, "textures")):
    for f in sorted(files):
        if not f.endswith(".png"):
            continue
        p = os.path.join(dirpath, f)
        sub = os.path.relpath(dirpath, os.path.join(ASSETS, "textures")).replace("\\", "/")
        n = f[:-4]
        ok = False
        if sub == "item":
            ok = ("skydeitymysticpivot:item/" + n) in ALL_TEXT or n in items
            # focus_catalog 的 ornament_texture 走全路径（坑 #54）
            ok = ok or ("textures/item/" + n + ".png") in ALL_TEXT
        elif sub == "block":
            ok = ("block/" + n) in ALL_TEXT or n in blocks
        elif sub == "aspects":
            ok = os.path.exists(os.path.join(DATA, "skydeitymysticpivot/thaumcraft/aspects", n + ".json"))
        elif sub in ("gui", "misc", "models", "particle", "particles"):
            ok = ("%s/%s" % (sub, n)) in ALL_TEXT or (f in ALL_TEXT)
            # 法杖杆 / 杖帽：本体渲染器按约定名取图，Java 侧只有 wandPart / WandPreset
            # 的字符串参数（杆是第一个、帽是第二个），没有 "models/" 前缀（坑 #54）
            ok = ok or ('wandPart("%s"' % n) in ALL_TEXT or ('"%s"' % n) in ALL_TEXT
        else:
            ok = (n in ALL_TEXT)
        if not ok:
            n_orphan += 1
            print("  %s/%s.png" % (sub, f[:-4]))
if n_orphan == 0:
    print("  （无）")

print()
print("=" * 70)
print("B. 模型/其他资源孤儿")
print("=" * 70)
n_orphan = 0
for dirpath, _, files in os.walk(os.path.join(ASSETS, "models")):
    for f in sorted(files):
        p = os.path.join(dirpath, f)
        rel = os.path.relpath(p, ASSETS).replace("\\", "/")
        if rel.endswith(".json"):
            name = rel[len("models/"):-len(".json")]
            sub, n = name.split("/", 1)
            if sub == "item":
                ok = n in items or ("skydeitymysticpivot:item/" + n) in ALL_TEXT
            else:
                ok = ("skydeitymysticpivot:%s/%s" % (sub, n)) in ALL_TEXT
            if not ok:
                n_orphan += 1
                print("  %s" % rel)
        elif rel.endswith((".obj", ".mtl")):
            if f.startswith("_"):
                continue        # 预览工具现场生成的临时件，只在 tools/ 里被引用
            ok = rel.replace("models/", "") in ALL_TEXT or f in ALL_TEXT
            if not ok:
                n_orphan += 1
                print("  %s" % rel)
if n_orphan == 0:
    print("  （无）")

print()
print("=" * 70)
print("C. blockstates 对账（无对应方块注册名）")
print("=" * 70)
n_bad = 0
for f in sorted(os.listdir(os.path.join(ASSETS, "blockstates"))):
    n = f[:-5]
    if n not in blocks:
        n_bad += 1
        print("  %s" % f)
if n_bad == 0:
    print("  （全部有对应方块）")

print()
print("=" * 70)
print("D. 注册了但没有任何 blockstates/模型？")
print("=" * 70)
for n in sorted(blocks):
    if not os.path.exists(os.path.join(ASSETS, "blockstates", n + ".json")):
        print("  block 缺 blockstate:", n)
