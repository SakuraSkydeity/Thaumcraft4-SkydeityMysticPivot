#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第八十批数据改动（幂等，可重复执行）：

  1. 研究树 `bagua.json`：
     * 分类背景换成八卦版易经贴图（`bagua_researchback.png`，本批新绘）；
     * **八个卦改挂玄枢台座** —— 台座成为八卦的汇聚中心，放射八条线；
     * 玄枢要素（根条目）从台座右邻 (1,0) 挪到左外侧 **(-4,2)**，
       作为台座的「前置」拉开一条长线（4 格多），与八条放射线互不重叠；
     * 法杖之装配 (9,1) → **(10,1)**，与杖柄/杖端两列拉开距离。
  2. 灵目改项链栏（Java 侧见 XuanshuVoidEyeItem）：
     * 物品标签 `curios/tags/item/curio.json` → **`necklace.json`**；
     * 书页（lore）文案：饰品栏 → 项链栏；护甲 +20 → 最大生命 +100%。

背景贴图本体由 `tools/gen_researchback_bagua.py` 生成（本脚本只接线）。
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src/main/resources")
DATA = os.path.join(RES, "data/skydeitymysticpivot")
LANG = os.path.join(RES, "assets/skydeitymysticpivot/lang")
TAGS = os.path.join(RES, "data/curios/tags/item")

RESEARCH = os.path.join(DATA, "thaumcraft/research/bagua.json")
ZH = os.path.join(LANG, "zh_cn.json")
EN = os.path.join(LANG, "en_us.json")

NS = "skydeitymysticpivot"
TRIGRAM_ENTRIES = ("GUA_QIAN", "GUA_DUI", "GUA_LI", "GUA_ZHEN",
                   "GUA_XUN", "GUA_KAN", "GUA_GEN", "GUA_KUN")

BACKGROUND = "skydeitymysticpivot:textures/gui/bagua_researchback.png"
BAGUA_POS = (-4, 2)          # 玄枢要素：台座左外侧
ASSEMBLY_POS = (10, 1)       # 法杖之装配：右移一格

VOID_EYE = "skydeitymysticpivot:xuanshu_void_eye"


def load(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def dump(path, doc):
    text = json.dumps(doc, ensure_ascii=False, indent=2) + "\n"
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(text)
    print("  写回", os.path.relpath(path, ROOT))


def rid(name):
    return "%s:%s" % (NS, name)


# ----------------------------------------------------------------------
# 1. 研究树
# ----------------------------------------------------------------------

def patch_research():
    doc = load(RESEARCH)
    entries = {e["key"].split(":")[-1]: e for e in doc["entries"]}

    # 1a. 分类背景
    for category in doc["categories"]:
        if category["key"] == rid("BAGUA"):
            category["background"] = BACKGROUND
            print("  分类背景 →", BACKGROUND)

    # 1b. 八个卦改挂台座
    for name in TRIGRAM_ENTRIES:
        entries[name]["parents"] = [rid("PEDESTAL")]
    print("  八个卦 ← 玄枢台座（放射八线）")

    # 1c. 玄枢要素挪到台座左外侧
    bagua = entries["BAGUA"]
    old = (bagua["display_column"], bagua["display_row"])
    bagua["display_column"], bagua["display_row"] = BAGUA_POS
    print("  玄枢要素 %s → %s（台座前置，长线）" % (old, BAGUA_POS))

    # 1d. 法杖之装配右移
    asm = entries["WAND_ASSEMBLY"]
    old = (asm["display_column"], asm["display_row"])
    asm["display_column"], asm["display_row"] = ASSEMBLY_POS
    print("  法杖之装配 %s → %s" % (old, ASSEMBLY_POS))

    dump(RESEARCH, doc)


# ----------------------------------------------------------------------
# 2. 灵目的 Curios 物品标签：通用槽 → 项链槽
# ----------------------------------------------------------------------

def patch_curios_tags():
    legacy = os.path.join(TAGS, "curio.json")
    if os.path.exists(legacy):
        os.remove(legacy)
        print("  删除 data/curios/tags/item/curio.json")

    target = os.path.join(TAGS, "necklace.json")
    doc = {"replace": False, "values": [VOID_EYE]}
    text = json.dumps(doc, ensure_ascii=False, indent=2) + "\n"
    with open(target, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(text)
    print("  写回 data/curios/tags/item/necklace.json（与本体 tag 合并）")


# ----------------------------------------------------------------------
# 3. 书页文案
# ----------------------------------------------------------------------

ZH_TEXT = {
    "item.skydeitymysticpivot.xuanshu_void_eye.lore1":
        "置于项链栏：窥见灵气节点与容器要素，如揭示之护目镜",
    "item.skydeitymysticpivot.xuanshu_void_eye.lore2":
        "最大生命 +100%",
}

EN_TEXT = {
    "item.skydeitymysticpivot.xuanshu_void_eye.lore1":
        "Worn in the necklace slot: reveals aura nodes and container aspects, "
        "like the Goggles of Revealing",
    "item.skydeitymysticpivot.xuanshu_void_eye.lore2":
        "+100% Max Health",
}


def patch_lang(path, mapping):
    doc = load(path)
    changed = [k for k, v in mapping.items() if doc.get(k) != v]
    doc.update(mapping)
    dump(path, doc)
    print("  更新 %d 个键" % len(changed))


def main():
    print("1. 研究树：")
    patch_research()
    print("2. Curios 标签：")
    patch_curios_tags()
    print("3. 语言 zh_cn：")
    patch_lang(ZH, ZH_TEXT)
    print("4. 语言 en_us：")
    patch_lang(EN, EN_TEXT)


if __name__ == "__main__":
    main()
