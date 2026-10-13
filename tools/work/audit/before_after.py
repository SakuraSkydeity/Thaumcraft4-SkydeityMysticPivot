#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""12.63 批：魔导书超页「修前 vs 修后」对照（**可复跑**）。

复用 book_lines.py 的度量（复刻本体 ResearchFontLayout），分别喂
同目录下的 {zh_cn,en_us}.json.orig + bagua.json.orig（修前）
与真实资源路径（修后），输出超页清单。

用法（在项目根目录）：
    python tools/work/audit/before_after.py
"""
import importlib.util
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
sys.path.insert(0, HERE)

spec = importlib.util.spec_from_file_location("book_lines", os.path.join(HERE, "book_lines.py"))
bl = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bl)


def audit(lang_zh, lang_en, bagua_path, label):
    zh = json.load(open(lang_zh, encoding="utf-8"))
    en = json.load(open(lang_en, encoding="utf-8"))
    d = json.load(open(bagua_path, encoding="utf-8"))
    over = []
    total = 0
    maxzh = maxen = 0
    for v in d["entries"]:
        for i, pg in enumerate(v.get("pages", [])):
            if pg.get("type") not in ("TEXT", "TEXT_CONCEALED", "LORE"):
                continue
            total += 1
            cap = bl.CAP_TITLE if i == 0 else bl.CAP_PLAIN
            key = pg.get("value", "")
            for loc, table, counter in (("zh", zh, "zh"), ("en", en, "en")):
                txt = table.get(key)
                if txt is None:
                    continue
                n = bl.rows(txt)
                if loc == "zh":
                    maxzh = max(maxzh, n)
                else:
                    maxen = max(maxen, n)
                if n > cap:
                    over.append((n - cap, v["key"].split(":")[-1], i, key, loc, n, cap))
    over.sort(reverse=True)
    print(f"---- {label} ----")
    print(f"text pages: {total} | max zh: {maxzh} | max en: {maxen} | over: {len(over)}")
    for d_, node, i, key, loc, n, cap in over:
        print(f"   +{d_:<3} {node}({i}) {key} [{loc}] {n}/{cap}")
    return len(over)


before = audit(
    os.path.join(HERE, "zh_cn.json.orig"),
    os.path.join(HERE, "en_us.json.orig"),
    os.path.join(HERE, "bagua.json.orig"),
    "修前",
)
after = audit(
    os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/lang/zh_cn.json"),
    os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/lang/en_us.json"),
    os.path.join(ROOT, "src/main/resources/data/skydeitymysticpivot/thaumcraft/research/bagua.json"),
    "修后",
)
print(f"\n超页页数：{before} -> {after}")
