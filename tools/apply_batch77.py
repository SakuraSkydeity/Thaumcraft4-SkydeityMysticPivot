#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
第七十七批数据改动（幂等，可重复执行）：

  1. 卦象门禁 `machines.json`：
     * 九天净界玉**新增**门禁 —— 坤 + 震 + 艮 三卦齐立（mode=all）；
     * 玄枢充能器门禁由「巽 ×1」改成 **巽 + 乾 + 坎** 三卦齐立（mode=all）。
  2. 配方要素：净界石与充能器的 aspects 各添三个**卦象要素**
     （项目里早就有 `skydeitymysticpivot:kun` 这类自定义要素，八卦碎片那条线一直在用）：
     立哪三卦，就喂哪三卦的要素，各 20。
  3. 研究树 `bagua.json` 连线重排：
     * 巽卦 → 炎炉 / 坩埚 / 炼金炉 三条线，各自直连（三台机器之间不再连）；
     * 三台机器 → 充能器（充能器同时挂三个父条目）；
     * 充能器从 (3,-5) 挪到 (3,-7)，与三台机器拉开距离，三条汇聚线才看得清。
  4. 语言：两条配方的书页同步改写（要素行加三卦、立卦行改成三卦）。
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src/main/resources")
DATA = os.path.join(RES, "data/skydeitymysticpivot")
LANG = os.path.join(RES, "assets/skydeitymysticpivot/lang")

RESEARCH = os.path.join(DATA, "thaumcraft/research/bagua.json")
GATES = os.path.join(DATA, "mysticpivot/trigram_gate/machines.json")
RECIPE_PURIFY = os.path.join(DATA, "recipe/xuanshu_purifying_stone.json")
RECIPE_CHARGER = os.path.join(DATA, "recipe/xuanshu_charger.json")
ZH = os.path.join(LANG, "zh_cn.json")
EN = os.path.join(LANG, "en_us.json")

NS = "skydeitymysticpivot"
RING = {"mode": "count", "min": 1, "radius": 2, "height": 1, "relief": 2, "relief_max": 3}

PURIFY_TRIGRAMS = ["kun", "zhen", "gen"]
CHARGER_TRIGRAMS = ["xun", "qian", "kan"]
ELEMENT_AMOUNT = 20


def load(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def dump(path, doc):
    text = json.dumps(doc, ensure_ascii=False, indent=2) + "\n"
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(text)
    print("  写回", os.path.relpath(path, ROOT))


# ----------------------------------------------------------------------
# 1. 卦象门禁
# ----------------------------------------------------------------------

def patch_gates():
    gates = load(GATES)
    for recipe, trigrams in (("xuanshu_purifying_stone", PURIFY_TRIGRAMS),
                             ("xuanshu_charger", CHARGER_TRIGRAMS)):
        gate = dict(RING)
        gate.update({"trigram": trigrams, "mode": "all"})
        gates["%s:%s" % (NS, recipe)] = gate
        print("  门禁 %-28s ← %s（三卦齐立）" % (recipe, " + ".join(trigrams)))
    dump(GATES, gates)


# ----------------------------------------------------------------------
# 2. 配方要素
# ----------------------------------------------------------------------

def patch_recipe(path, trigrams):
    recipe = load(path)
    aspects = recipe["aspects"]
    for trigram in trigrams:
        aspects["%s:%s" % (NS, trigram)] = ELEMENT_AMOUNT
    # 键序整理：本体六基元在前，卦象要素在后
    recipe["aspects"] = {k: aspects[k]
                         for k in sorted(aspects, key=lambda k: (":" in k, k))}
    dump(path, recipe)


# ----------------------------------------------------------------------
# 3. 研究树连线
# ----------------------------------------------------------------------

def rid(name):
    return "%s:%s" % (NS, name)


def patch_research():
    doc = load(RESEARCH)
    entries = {e["key"].split(":")[-1]: e for e in doc["entries"]}

    # 三台机器：全部直连巽卦，彼此不再连
    for machine in ("MACHINE_FURNACE", "MACHINE_CRUCIBLE", "MACHINE_ALCHEMY"):
        entries[machine]["parents"] = [rid("GUA_XUN")]
    print("  三台工作方块改直连巽卦（炎炉 / 坩埚 / 炼金炉）")

    # 充能器：三个父条目 + 挪远
    charger = entries["XUANSHU_CHARGER"]
    charger["parents"] = [rid("MACHINE_FURNACE"), rid("MACHINE_CRUCIBLE"), rid("MACHINE_ALCHEMY")]
    charger["display_column"] = 3
    charger["display_row"] = -7
    print("  充能器 ← 三台机器共同作父，位置 (3,-5) → (3,-7)")

    dump(RESEARCH, doc)


# ----------------------------------------------------------------------
# 4. 书页文案
# ----------------------------------------------------------------------

ZH_TEXT = {
    "bagua.recipe.xuanshu_purifying_stone":
        "§l九天净界玉§r 注魔<BR>"
        "§8中心§r 本体咒流清洁器 §8座上§r 玄枢锭×3、八卦碎片×2、以太之花×2、"
        "玄枢八卦之盐×2<BR>"
        "§8要素§r 六基元各 32，坤·震·艮三卦各 20 §8不稳§r §c6§r<BR>"
        "§8▍立卦§r §b§l坤卦石 · 震卦石 · 艮卦石§r §8各 ×1"
        "（基座下面 5×5，三卦齐立）§r：坤为地以载，震为雷以动，艮为山以止；"
        "三卦定住，一片地界才净得下来。<BR><BR>"
        "把清洁器收进一块石头里，只是它不擦一时一地，而是把一片地界慢慢洗回原样。",

    "bagua.recipe.xuanshu_charger":
        "§l玄枢充能器§r 注魔<BR>"
        "§8中心§r 本体节点换能器 §8座上§r 玄枢锭×2、玄枢碎片×2、玄枢八卦之盐×2、"
        "闪耀之光×2、高级节点稳定器×2（交错摆一圈）<BR>"
        "§8要素§r 六基元各 32，巽·乾·坎三卦各 20 §8不稳§r §c8§r<BR>"
        "§8▍立卦§r §b§l巽卦石 · 乾卦石 · 坎卦石§r §8各 ×1"
        "（基座下面 5×5，三卦齐立）§r：巽为风、乾为天、坎为水；"
        "风自天来、水往下走，节点才充得实。<BR><BR>"
        "三足的鼎，腹里点着一枚星焰，节点换能器就架在这团火上。",
}

EN_TEXT = {
    "bagua.recipe.xuanshu_purifying_stone":
        "§lNine Heavens Purifying Jade§r infusion<BR>"
        "§8Centre§r Flux Scrubber §8Pedestals§r 3 Xuanshu Ingot, 2 Bagua Shard, "
        "2 Ethereal Bloom, 2 Xuanshu Bagua Salt<BR>"
        "§8Aspects§r 32 of each primal, 20 each of Kun, Zhen and Gen "
        "§8Instability§r §c6§r<BR>"
        "§8Gate§r §b§lKun + Zhen + Gen stone§r §8x1 each "
        "(all three on the 5x5 under the pedestal)§r: Kun is earth that bears, "
        "Zhen is thunder that moves, Gen is mountain that rests; with the three set, "
        "a stretch of land can settle.<BR><BR>"
        "The scrubber folded into a stone: it no longer cleans one spot, but washes "
        "a whole stretch of land slowly back to what it was.",

    "bagua.recipe.xuanshu_charger":
        "§lXuanshu Charger§r infusion<BR>"
        "§8Centre§r Node Transducer §8Pedestals§r 2 Xuanshu Ingot, 2 Xuanshu Shard, "
        "2 Xuanshu Bagua Salt, 2 Nitor, 2 Advanced Node Stabilizer "
        "(alternating around the ring)<BR>"
        "§8Aspects§r 32 of each primal, 20 each of Xun, Qian and Kan "
        "§8Instability§r §c8§r<BR>"
        "§8Gate§r §b§lXun + Qian + Kan stone§r §8x1 each "
        "(all three on the 5x5 under the pedestal)§r: Xun is wind, Qian is heaven, "
        "Kan is water; wind comes from heaven and water runs downward, and only then "
        "does the node charge true.<BR><BR>"
        "A three-legged cauldron with a star-flame in its belly, the node transducer "
        "resting on that flame.",
}


def patch_lang(path, mapping):
    doc = load(path)
    changed = [k for k, v in mapping.items() if doc.get(k) != v]
    doc.update(mapping)
    dump(path, doc)
    print("  更新 %d 个键" % len(changed))


def main():
    print("1. 卦象门禁：")
    patch_gates()
    print("2. 配方要素：")
    patch_recipe(RECIPE_PURIFY, PURIFY_TRIGRAMS)
    patch_recipe(RECIPE_CHARGER, CHARGER_TRIGRAMS)
    print("3. 研究树：")
    patch_research()
    print("4. 语言 zh_cn：")
    patch_lang(ZH, ZH_TEXT)
    print("5. 语言 en_us：")
    patch_lang(EN, EN_TEXT)


if __name__ == "__main__":
    main()
