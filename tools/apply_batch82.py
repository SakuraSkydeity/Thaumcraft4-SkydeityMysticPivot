#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第 82 批数据改动：研究书「玄枢要素」再往左移。

背景贴图重做为纯星辰（见 tools/gen_researchback_bagua.py，直接生成产物）。

bagua.json：
  * BAGUA（玄枢要素）display_column: -4 → -7（更左、连线更长；行不变）。
  * 其余不动；全树零重叠校验。

用法：python tools/apply_batch82.py
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RESEARCH = os.path.join(
    ROOT, "src/main/resources/data/skydeitymysticpivot/thaumcraft/research/bagua.json")

NEW_BAGUA_COL = -7
NEW_BAGUA_ROW = 2


def main():
    with open(RESEARCH, encoding="utf-8") as f:
        doc = json.load(f)

    entries = {e["key"].split(":")[1]: e for e in doc["entries"]}

    # 1) 玄枢要素再往左移
    bag = entries["BAGUA"]
    old = (bag["display_column"], bag["display_row"])
    bag["display_column"] = NEW_BAGUA_COL
    bag["display_row"] = NEW_BAGUA_ROW
    print("BAGUA: %s -> %s" % (old, (NEW_BAGUA_COL, NEW_BAGUA_ROW)))

    # 2) 玄枢台座的前置仍是玄枢要素（连线对端点）
    ped = entries["PEDESTAL"]
    assert ped["parents"] == ["skydeitymysticpivot:BAGUA"], ped["parents"]
    print("PEDESTAL.parents =", ped["parents"], "(OK)")

    # 3) 分类背景仍是新贴图（纯星辰版，同名同路径）
    cat = doc["categories"][0]
    assert cat["background"] == "skydeitymysticpivot:textures/gui/bagua_researchback.png", \
        cat["background"]
    print("category background =", cat["background"], "(OK)")

    # 4) 全树零重叠
    seen = {}
    for e in doc["entries"]:
        pos = (e["display_column"], e["display_row"])
        assert pos not in seen, ("OVERLAP", e["key"], seen[pos])
        seen[pos] = e["key"]
    print("entries = %d, positions unique (OK)" % len(seen))

    with open(RESEARCH, "w", encoding="utf-8", newline="\n") as f:
        json.dump(doc, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("saved", os.path.relpath(RESEARCH, ROOT))


if __name__ == "__main__":
    main()
