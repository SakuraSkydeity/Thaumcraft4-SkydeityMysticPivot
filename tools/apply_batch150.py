# -*- coding: utf-8 -*-
"""第 150 批（第 152 批续改）：把魔导书 35 个条目重排成「功能 / 合成 / 来历」三段，并重排研究树。

第 151 批新增：星辰合成条目插一页「多方块结构图」（3×4 星阵蓝图）、
炼金炉挪到另两台中间、右半边收紧、星支拉开、文案里的星位供物改成玄枢碎片。
第 152 批：连线恢复本体细线（蓝色粗线的类已删干净）、卦石与星辰合成正文改蓝色加粗、
右半边再收两列（10→8）、净界玉挪到坤卦右下角。

做四件事：
  1. 重写 research/bagua.json 每个条目的 pages（只保留必要的配方页，文本页全部换新）；
  2. 重排 display_column / display_row，把过长的连线收短（最长的 7 格收到 4 格，其余 1~2 格）；
  3. 把新文案写进 zh_cn / en_us，并删掉不再被引用的 bagua.* 旧键（selfcheck 要求中英等键）；
  4. 重写全部 tc.research_text（那行橘黄色小字）。

用法：python tools/apply_batch150.py [--dry]
"""
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from batch150_zh import ZH  # noqa: E402
from batch150_en import EN, EN_RESEARCH_TEXT  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RESEARCH = os.path.join(ROOT, "src/main/resources/data/skydeitymysticpivot/thaumcraft/research/bagua.json")
LANG = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/lang")
NS = "skydeitymysticpivot"

ZH_RESEARCH_TEXT = {
    "BAGUA": "万物之上，还有八格",
    "PEDESTAL": "先成坛，才谈得上卦",
    "GUA_QIAN": "三爻皆阳，其象为天",
    "GUA_DUI": "上阴下二阳，其象为泽",
    "GUA_LI": "中阴外阳，其象为火",
    "GUA_ZHEN": "下阳上二阴，其象为雷",
    "GUA_XUN": "下阴上二阳，其象为风",
    "GUA_KAN": "中阳外阴，其象为水",
    "GUA_GEN": "上阳下二阴，其象为山",
    "GUA_KUN": "三爻皆阴，其象为地",
    "TERRAIN": "气随地异，八处各一",
    "GUA_STONES": "石立何处，卦立何处",
    "STAR_CRAFT": "星是号令",
    "XUANSHU_INGOT_UNFIRED": "还是凉的",
    "XUANSHU_INGOT_FORGED": "七星亮，锭才活",
    "INGOTS": "四方之神，各据一锭",
    "SHARDS": "神兽散尽，只剩碎片",
    "CLOTHS": "煮出来的布",
    "WANDS": "骨定其量，端定其耗",
    "CAPS": "一对鞘，把流切细",
    "WAND_ASSEMBLY": "两头同源，方成一杖",
    "XUANSHU_SPIRIT_CORE": "造台先造心",
    "XUANSHU_ARCANE_TABLE": "是喂，不是附",
    "XUANSHU_NODE_MODIFIER": "硬塞进节点的一门气",
    "BAGUA_PICKAXE": "山不肯让路，只好开路",
    "XUANSHU_PICKAXE": "一镐在手，镐斧铲俱全",
    "XUANSHU_HOE": "一锄管一片田",
    "FOCUS_GUARDIAN": "替你挡那一下",
    "FOCUS_TELEPORT": "记一处，一念即回",
    "FOCUS_COSMOS": "指处皆虚",
    "MACHINE_FURNACE": "一份燃料，三格火",
    "MACHINE_CRUCIBLE": "免火，免水",
    "MACHINE_ALCHEMY": "烧物取气",
    "XUANSHU_CHARGER": "节点也能养",
    "PURIFYING_STONE": "腐地自会回青",
}

# ---------------------------------------------------------------- 页面编排
# T: 文本页  INF/CRU/SME/ARC: 配方页  CMP: 多方块蓝图  ASPECTS: 研究要素页
PLAN = {
    "BAGUA": [
        ("T", "bagua.page.intro"), ("T", "bagua.page.source_scan"), ("T", "bagua.page.source_fuse"),
        ("ASPECTS", None), ("ASPECTS", None), ("ASPECTS", None),
        ("T", "bagua.page.rule"), ("T", "bagua.page.caps"), ("T", "bagua.page.materials_b"),
        ("T", "bagua.page.lore"),
    ],
    "PEDESTAL": [
        ("T", "bagua.page.pedestal_altar"), ("T", "bagua.page.pedestal_altar2"),
        ("T", "bagua.page.pedestal_qi"),
        ("CMP", None), ("ARC", "xuanshu_pedestal"), ("CRU", "sixiang_stone_brick"),
        ("T", "bagua.page.pedestal_lands"), ("T", "bagua.page.pedestal_essentia"),
        ("T", "bagua.page.pedestal_lore"),
    ],
    "TERRAIN": [
        ("T", "bagua.page.terrain_all"),
        ("T", "bagua.gua.qian.terrain"), ("T", "bagua.gua.dui.terrain"), ("T", "bagua.gua.li.terrain"),
        ("T", "bagua.gua.zhen.terrain"), ("T", "bagua.gua.xun.terrain"), ("T", "bagua.gua.kan.terrain"),
        ("T", "bagua.gua.gen.terrain"), ("T", "bagua.gua.kun.terrain"),
        ("T", "bagua.page.terrain_lore"),
    ],
    "GUA_STONES": [
        ("T", "bagua.page.stones_what"), ("T", "bagua.page.stones_place"),
        ("T", "bagua.page.stones_recipe"), ("T", "bagua.page.stones_lore"),
    ],
    "STAR_CRAFT": [
        ("T", "bagua.page.star_craft_board"), ("CMP", "compound/star_array"),
        ("T", "bagua.page.star_craft_activate"), ("T", "bagua.page.star_craft_machines"),
        ("T", "bagua.page.star_craft_use"), ("T", "bagua.page.star_craft_lore"),
    ],
    "XUANSHU_INGOT_UNFIRED": [
        ("T", "bagua.page.unfired_ingot"), ("ARC", "xuanshu_ingot_unfired"),
        ("T", "bagua.page.unfired_req"), ("T", "bagua.page.unfired_lore"),
    ],
    "XUANSHU_INGOT_FORGED": [
        ("T", "bagua.page.forged_ingot"), ("T", "bagua.page.forged_ingot_recipe"),
        ("T", "bagua.page.forged_lore"),
    ],
    "INGOTS": [
        ("T", "bagua.page.node_ingots"), ("T", "bagua.page.ingot_req"),
        ("T", "bagua.page.recipe_ingot_note"),
        ("ARC", "azure_dragon_ingot"), ("T", "bagua.recipe.azure_dragon_ingot"),
        ("ARC", "vermilion_bird_ingot"), ("T", "bagua.recipe.vermilion_bird_ingot"),
        ("ARC", "white_tiger_ingot"), ("T", "bagua.recipe.white_tiger_ingot"),
        ("ARC", "black_tortoise_ingot"), ("T", "bagua.recipe.black_tortoise_ingot"),
        ("T", "bagua.page.ingots_lore"),
    ],
    "SHARDS": [
        ("T", "bagua.page.node_shards"), ("T", "bagua.page.shards_req"),
        ("CRU", "azure_dragon_shard"), ("T", "bagua.recipe.azure_dragon_shard"),
        ("CRU", "vermilion_bird_shard"), ("T", "bagua.recipe.vermilion_bird_shard"),
        ("CRU", "white_tiger_shard"), ("T", "bagua.recipe.white_tiger_shard"),
        ("CRU", "black_tortoise_shard"), ("T", "bagua.recipe.black_tortoise_shard"),
        ("CRU", "xuanshu_shard"), ("T", "bagua.recipe.xuanshu_shard"),
        ("SME", "azure_dragon_ingot_to_bagua_shard"), ("T", "bagua.recipe.azure_dragon_ingot_to_bagua_shard"),
        ("SME", "vermilion_bird_ingot_to_bagua_shard"), ("T", "bagua.recipe.vermilion_bird_ingot_to_bagua_shard"),
        ("SME", "white_tiger_ingot_to_bagua_shard"), ("T", "bagua.recipe.white_tiger_ingot_to_bagua_shard"),
        ("SME", "black_tortoise_ingot_to_bagua_shard"), ("T", "bagua.recipe.black_tortoise_ingot_to_bagua_shard"),
        ("SME", "xuanshu_bagua_salt"), ("T", "bagua.recipe.xuanshu_bagua_salt"),
        ("T", "bagua.page.shards_lore"),
    ],
    "CLOTHS": [
        ("T", "bagua.page.node_cloths"),
        ("CRU", "sixiang_cloth"), ("T", "bagua.recipe.sixiang_cloth"),
        ("CRU", "xuanshu_cloth"), ("T", "bagua.recipe.xuanshu_cloth"),
        ("T", "bagua.page.cloths_lore"),
    ],
    "WANDS": [
        ("T", "bagua.page.node_wands"), ("T", "bagua.page.node_wands2"),
        ("INF", "onyx_wand_rod"), ("T", "bagua.recipe.onyx_wand_rod"),
        ("INF", "sixiang_wand_rod"), ("T", "bagua.recipe.sixiang_wand_rod"),
        ("INF", "zhuque_wand_rod"), ("T", "bagua.recipe.zhuque_wand_rod"),
        ("T", "bagua.page.wands_lore"),
    ],
    "CAPS": [
        ("T", "bagua.page.node_caps"), ("T", "bagua.page.node_caps2"),
        ("INF", "gilded_wand_cap"), ("T", "bagua.recipe.gilded_wand_cap"),
        ("INF", "bagua_wand_cap"), ("T", "bagua.recipe.bagua_wand_cap"),
        ("INF", "zhuque_wand_cap"), ("T", "bagua.recipe.zhuque_wand_cap"),
        ("T", "bagua.page.caps_lore"),
    ],
    "WAND_ASSEMBLY": [
        ("T", "bagua.page.wand_assembly"), ("T", "bagua.page.assembly"),
        ("T", "bagua.page.wand_assembly_lore"),
    ],
    "BAGUA_PICKAXE": [
        ("T", "bagua.page.bagua_pickaxe_func"), ("INF", "bagua_pickaxe"),
        ("T", "bagua.recipe.bagua_pickaxe"), ("T", "bagua.page.bagua_pickaxe_lore"),
    ],
    "XUANSHU_PICKAXE": [
        ("T", "bagua.page.pickaxe2_intro"), ("T", "bagua.page.pickaxe2_tunnel"),
        ("INF", "xuanshu_pickaxe"), ("T", "bagua.recipe.xuanshu_pickaxe"),
        ("T", "bagua.page.pickaxe2_lore"),
    ],
    "XUANSHU_HOE": [
        ("T", "bagua.page.hoe_intro"), ("INF", "xuanshu_hoe"),
        ("T", "bagua.recipe.xuanshu_hoe"), ("T", "bagua.page.hoe_lore"),
    ],
    "FOCUS_GUARDIAN": [
        ("T", "bagua.page.focus_guardian_func"), ("INF", "focus_guardian"),
        ("T", "bagua.recipe.focus_guardian"), ("T", "bagua.page.focus_guardian_lore"),
    ],
    "FOCUS_TELEPORT": [
        ("T", "bagua.page.focus_teleport_func"), ("INF", "focus_teleport"),
        ("T", "bagua.recipe.focus_teleport"), ("T", "bagua.page.focus_teleport_lore"),
    ],
    "FOCUS_COSMOS": [
        ("T", "bagua.page.focus_cosmos_func"), ("INF", "focus_cosmos"),
        ("T", "bagua.recipe.focus_cosmos"), ("T", "bagua.page.focus_cosmos_lore"),
    ],
    "MACHINE_FURNACE": [
        ("T", "bagua.page.machine_furnace"), ("INF", "xuanshu_infernal_furnace"),
        ("T", "bagua.recipe.xuanshu_infernal_furnace"), ("T", "bagua.page.machine_furnace2"),
    ],
    "MACHINE_CRUCIBLE": [
        ("T", "bagua.page.machine_crucible"), ("INF", "xuanshu_crucible"),
        ("T", "bagua.recipe.xuanshu_crucible"), ("T", "bagua.page.machine_crucible2"),
    ],
    "MACHINE_ALCHEMY": [
        ("T", "bagua.page.machine_alchemy"), ("INF", "xuanshu_alchemy_furnace"),
        ("T", "bagua.recipe.xuanshu_alchemy_furnace"), ("T", "bagua.page.machine_alchemy2"),
    ],
    "XUANSHU_CHARGER": [
        ("T", "bagua.page.charger_intro"), ("INF", "xuanshu_charger"),
        ("T", "bagua.recipe.xuanshu_charger"), ("T", "bagua.page.charger_intro2"),
    ],
    "PURIFYING_STONE": [
        ("T", "bagua.page.purify_intro"), ("T", "bagua.page.purify_array"),
        ("T", "bagua.page.purify_array2"), ("T", "bagua.page.purify_array3"),
        ("INF", "xuanshu_purifying_stone"), ("T", "bagua.recipe.xuanshu_purifying_stone"),
        ("T", "bagua.page.purify_lore"),
    ],
    "XUANSHU_SPIRIT_CORE": [
        ("T", "bagua.page.spirit_core"), ("INF", "xuanshu_spirit_core"),
        ("T", "bagua.recipe.xuanshu_spirit_core"), ("T", "bagua.page.spirit_core_lore"),
    ],
    "XUANSHU_ARCANE_TABLE": [
        ("T", "bagua.page.arcane_table"), ("INF", "xuanshu_arcane_table"),
        ("T", "bagua.recipe.xuanshu_arcane_table"), ("T", "bagua.page.star_gate_tables"),
        ("T", "bagua.page.arcane_table_lore"),
    ],
    "XUANSHU_NODE_MODIFIER": [
        ("T", "bagua.page.node_modifier"), ("INF", "xuanshu_node_modifier"),
        ("T", "bagua.recipe.xuanshu_node_modifier"), ("T", "bagua.page.star_gate_tables"),
        ("T", "bagua.page.node_modifier_lore"),
    ],
}
for g in ("qian", "dui", "li", "zhen", "xun", "kan", "gen", "kun"):
    PLAN["GUA_" + g.upper()] = [
        ("T", "bagua.gua.%s" % g), ("T", "bagua.gua.%s.scan" % g), ("T", "bagua.gua.%s.lore" % g),
    ]

# ---------------------------------------------------------------- 树布局
# 第一百五十批：旧值最远的一条是 BAGUA(-7,2) -> PEDESTAL(0,0)，七格；收到四格。
# 第一百五十一批（用户逐条指位置）：
#   ① 玄枢炼金炉挪到炎炉与坩埚中间 —— 三台机器并成 (1,-3)(2,-3)(3,-3) 一排，
#      充能器 (2,-5) 正好落在三者的对称点上；
#   ② 玄枢碎片开始的右半边整体收一格（6/8/9/10/11 → 5/7/8/9/10），杖支更紧凑；
#   ③ 玄枢锭与未尽玄枢锭那一条竖线上各隔两格（原来挨着，22px 的节点只差 2px，
#      看着像叠在一起），星辰与未尽之间同样留两格；
#   ④ 卦石挪到 (1,6)：原来贴着地气（1,5）只差一格，24px 的格子被两端 22px 的节点吃掉，
#      那条「蓝色加粗」几乎看不见；拉开两格才有段线可看。
LAYOUT = {
    "BAGUA": (-4, 2),
    "STAR_CRAFT": (-6, 2),
    "XUANSHU_INGOT_UNFIRED": (-6, 4),
    "XUANSHU_INGOT_FORGED": (-6, 6),
    "PEDESTAL": (0, 0),
    "GUA_QIAN": (0, -2),
    "GUA_DUI": (-1, -1),
    "GUA_LI": (-2, 0),
    "GUA_ZHEN": (-1, 1),
    "GUA_XUN": (1, -1),
    "GUA_KAN": (2, 0),
    "GUA_GEN": (1, 1),
    "GUA_KUN": (0, 2),
    "TERRAIN": (1, 4),
    "GUA_STONES": (1, 6),
    "PURIFYING_STONE": (-1, 3),
    # 第一百五十四批：用户「右边两个法杖核心换位子，你的线交叉了」。
    # 三个核心都挂在震卦 (1,1) 上，连线是一把扇形；宇宙核心原先在 (4,3)，
    # 那条最浅的边会从传送核心 (3,3) 的节点里穿过去（离中心 13px，节点半径 11px）。
    # 只把两个位置的图标对调是没用的——边还是那两条；所以对调之后把落在远端的
    # 传送核心抬高一格到 (4,2)，扇形立刻散开（最近的边离节点 32px）。
    "FOCUS_GUARDIAN": (2, 3),
    "FOCUS_TELEPORT": (4, 2),
    "FOCUS_COSMOS": (3, 3),
    "BAGUA_PICKAXE": (-1, -4),
    "XUANSHU_PICKAXE": (-3, -4),
    "XUANSHU_HOE": (-1, -6),
    "MACHINE_FURNACE": (1, -3),
    "MACHINE_CRUCIBLE": (3, -3),
    "MACHINE_ALCHEMY": (2, -3),
    "XUANSHU_CHARGER": (2, -5),
    "INGOTS": (4, -1),
    "SHARDS": (5, 0),
    "CLOTHS": (6, -2),
    "WANDS": (6, 0),
    "CAPS": (6, 2),
    "WAND_ASSEMBLY": (7, 1),
    "XUANSHU_SPIRIT_CORE": (7, -2),
    "XUANSHU_ARCANE_TABLE": (8, -3),
    "XUANSHU_NODE_MODIFIER": (8, -1),
}

# ---------------------------------------------------------------- 蓝色正文
# 第一百五十二批：用户「我要的是文本，书里面文本内容；线就正常的线」。
# 原来把研究树的连线画成蓝色粗线（StarLinkHighlight + mixin）是理解错了——
# 要突出的是<b>书页正文</b>。所以连线恢复本体细线（整个类已删除），
# 卦石与星辰合成两个条目的正文改成一整段蓝色加粗：
#   正文 §9 + 粗、小节标题 §1 + 粗、小字提示 §1（原 §8 深灰在羊皮纸上偏淡）。
BLUE_ENTRIES = ("GUA_STONES", "STAR_CRAFT")


def blue_bold(text):
    """把一整页正文刷成蓝色加粗（保留原有的语义色，如「赤」「绿」「南斗」）。"""
    if not text:
        return text
    out = ["§9§l"]
    i = 0
    while i < len(text):
        if text[i] == "§" and i + 1 < len(text):
            code = text[i + 1].lower()
            if code == "l":
                out.append("§1§l")
            elif code == "r":
                out.append("§9§l")
            elif code in "0123456789abcdef":
                out.append("§1§l" if code in "8f" else "§" + code + "§l")
            else:
                out.append("§" + code)
            i += 2
        else:
            out.append(text[i])
            i += 1
    out.append("§r")
    # 开头紧跟小节标题时，前面那截 §9§l 是多余的
    return "".join(out).replace("§9§l§1§l", "§1§l")


def text_page(key):
    return {"type": "TEXT", "value": key, "aspects": {}, "compound_blueprint": "",
            "recipe_link_policy": "DISABLED", "recipe_ids": []}


def compound_page(blueprint):
    """多方块蓝图页（第一百五十一批：星辰合成的 3×4 星阵）。

    本体 {@code COMPOUND_CRAFTING} 页会在书里画成「等距多方块结构图」，
    蓝图数据取自 {@code data/skydeitymysticpivot/thaumcraft/compound_blueprints/}。
    """
    return {"type": "COMPOUND_CRAFTING", "value": "", "aspects": {},
            "compound_blueprint": "%s:%s" % (NS, blueprint),
            "recipe_link_policy": "DISABLED", "recipe_ids": []}


def main():
    dry = "--dry" in sys.argv
    data = json.load(open(RESEARCH, encoding="utf-8"))
    entries = {e["key"].split(":")[1]: e for e in data["entries"]}

    missing = [k for k in PLAN if k not in entries]
    if missing:
        raise SystemExit("PLAN 里有不存在的条目: %s" % missing)
    if set(PLAN) != set(entries):
        raise SystemExit("PLAN 与 JSON 条目对不上: %s / %s"
                         % (sorted(set(PLAN) - set(entries)), sorted(set(entries) - set(PLAN))))

    # 记下旧页面里能复用的配方页 / 蓝图页 / 要素页
    reused = 0
    for name, entry in entries.items():
        old = entry["pages"]
        aspects = [p for p in old if p["type"] == "ASPECTS"]
        by_recipe = {}
        compound = None
        for p in old:
            if p["type"] == "COMPOUND_CRAFTING":
                compound = p
            for rid in p.get("recipe_ids") or []:
                by_recipe[rid.split(":")[-1]] = p

        pages = []
        asp_i = 0
        for kind, val in PLAN[name]:
            if kind == "T":
                pages.append(text_page(val))
            elif kind == "ASPECTS":
                pages.append(aspects[asp_i])
                asp_i += 1
            elif kind == "CMP":
                pages.append(compound if compound is not None else compound_page(val))
            else:
                pages.append(by_recipe[val])
                reused += 1
        entry["pages"] = pages
        col, row = LAYOUT[name]
        entry["display_column"] = col
        entry["display_row"] = row

    # 连线长度体检：把每条父子边的格距打出来
    print("edge lengths (grid cells):")
    for name, entry in entries.items():
        for parent in entry.get("parents") or []:
            pn = parent.split(":")[1]
            if pn not in entries:
                continue
            dc = entry["display_column"] - entries[pn]["display_column"]
            dr = entry["display_row"] - entries[pn]["display_row"]
            d = (dc * dc + dr * dr) ** 0.5
            if d >= 3.0:
                print("  %-24s <- %-16s (%.1f)" % (name, pn, d))

    # 重叠体检
    seen = {}
    for name, entry in entries.items():
        cell = (entry["display_column"], entry["display_row"])
        if cell in seen:
            raise SystemExit("格子重叠: %s 与 %s 都在 %s" % (name, seen[cell], cell))
        seen[cell] = name

    if dry:
        total = sum(len(e["pages"]) for e in entries.values())
        print("dry run: %d entries, %d pages, %d recipe pages reused" % (len(entries), total, reused))
        return

    json.dump(data, open(RESEARCH, "w", encoding="utf-8", newline="\n"),
              ensure_ascii=False, indent=2)
    print("research json written")

    # ---------------- lang ----------------
    blue_keys = {v for name in BLUE_ENTRIES for kind, v in PLAN[name] if kind == "T"}
    for lang, page_map, rt in (("zh_cn", ZH, ZH_RESEARCH_TEXT), ("en_us", EN, EN_RESEARCH_TEXT)):
        path = os.path.join(LANG, lang + ".json")
        d = json.load(open(path, encoding="utf-8"))
        for k, v in page_map.items():
            d[k] = blue_bold(v) if k in blue_keys else v
        for name, line in rt.items():
            d["tc.research_text.%s:%s" % (NS, name)] = line

        # 仍被引用的页面键
        used = set()
        for e in data["entries"]:
            for p in e["pages"]:
                if p.get("value"):
                    used.add(p["value"])
        dropped = [k for k in d if k.startswith(("bagua.page.", "bagua.recipe.", "bagua.gua.")) and k not in used]
        for k in dropped:
            del d[k]
        json.dump(d, open(path, "w", encoding="utf-8", newline="\n"), ensure_ascii=False, indent=2)
        print("%s: %d keys, dropped %d stale page keys" % (lang, len(d), len(dropped)))
        if dropped:
            print("   ", ", ".join(sorted(dropped)))


if __name__ == "__main__":
    main()
