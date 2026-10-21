#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
第七十六批数据改动（幂等，可重复执行）：

  1. 研究树 `bagua.json`：玄枢翎凰焚天镐 / 玄枢玄冥镇海锄两条的**页面重排**——
     原来是「简介（功能与来历混写）→ 掘法 → 合成表 → 配方文本（配方与来历混写）」，
     现在一律改成用户要求的三段式：

         功能 → 合成表 → 背景故事

     镐：功能（属性+三用+时运）→ 两种掘法 → 注魔合成表 → 配方文本 → 来历
     锄：功能（三样）→ 注魔合成表 → 配方文本 → 来历

     来历两页是新键 `bagua.page.pickaxe2_lore` / `bagua.page.hoe_lore`。

  2. 语言（中英）：镐锄两个条目的全部页面文本重写 —— 语言改直白，
     参数、做法、会停在哪里、不看光照水分，一条一条写清楚，去掉文言腔与隐喻。
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src/main/resources")
DATA = os.path.join(RES, "data/skydeitymysticpivot")
LANG = os.path.join(RES, "assets/skydeitymysticpivot/lang")

RESEARCH = os.path.join(DATA, "thaumcraft/research/bagua.json")
ZH = os.path.join(LANG, "zh_cn.json")
EN = os.path.join(LANG, "en_us.json")


def load(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def dump(path, doc):
    text = json.dumps(doc, ensure_ascii=False, indent=2) + "\n"
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(text)
    print("  写回", os.path.relpath(path, ROOT))


def text_page(key):
    return {
        "type": "TEXT",
        "value": key,
        "aspects": {},
        "compound_blueprint": "",
        "recipe_link_policy": "DISABLED",
        "recipe_ids": [],
    }


# ----------------------------------------------------------------------
# 1. 研究树：页面重排
# ----------------------------------------------------------------------

def patch_research():
    doc = load(RESEARCH)
    entries = {e["key"].split(":")[-1]: e for e in doc["entries"]}

    pick = entries["XUANSHU_PICKAXE_2"]
    craft = next(p for p in pick["pages"] if p["type"] == "INFUSION_CRAFTING")
    pick["pages"] = [
        text_page("bagua.page.pickaxe2_intro"),        # 功能
        text_page("bagua.page.pickaxe2_tunnel"),       # 操作：两种掘法
        craft,                                         # 合成表
        text_page("bagua.recipe.xuanshu_pickaxe_2"),   # 配方文本
        text_page("bagua.page.pickaxe2_lore"),         # 背景故事
    ]
    print("1. 研究树：XUANSHU_PICKAXE_2 页面重排 → 功能 / 掘法 / 合成表 / 配方 / 来历")

    hoe = entries["XUANSHU_HOE"]
    craft = next(p for p in hoe["pages"] if p["type"] == "INFUSION_CRAFTING")
    hoe["pages"] = [
        text_page("bagua.page.hoe_intro"),             # 功能
        craft,                                         # 合成表
        text_page("bagua.recipe.xuanshu_hoe"),         # 配方文本
        text_page("bagua.page.hoe_lore"),              # 背景故事
    ]
    print("2. 研究树：XUANSHU_HOE 页面重排 → 功能 / 合成表 / 配方 / 来历")

    dump(RESEARCH, doc)


# ----------------------------------------------------------------------
# 2. 语言：镐锄全部页面文本重写
# ----------------------------------------------------------------------

ZH_TEXT = {
    # ---------------- 焚天镐 ----------------
    "bagua.page.pickaxe2_intro":
        "§l玄枢翎凰焚天镐§r<BR>"
        "§8一把镐，三种用法：挖石、砍木、铲土。§r<BR>"
        "§l基础属性§r 耐久 52000，挖掘速度 40，攻击力 +6，附魔等级 25，"
        "挖掘等级与下界合金相同。<BR>"
        "§l一、镐 · 斧 · 铲§r 对石头、木头、泥土这三类方块都算「用对了工具」，"
        "按本档速度挖掘，掉落照常结算。<BR>"
        "§8它砍得动木头、铲得动土，但不会替你去皮，也不会替你踩出小径："
        "那是斧子和铲子自己的本事。§r<BR>"
        "§l二、自带时运 XVI§r 拿到手就带着，不用附魔台。挖矿时按 16 级时运结算掉落。<BR>"
        "§l三、两种掘法§r 见下一页。",

    "bagua.page.pickaxe2_tunnel":
        "§l焚天镐 · 两种掘法§r<BR>"
        "§l怎么切换§r 潜行右键，在两种掘法之间来回切，动作栏会报当前是哪一种："
        "§6隧道§r 或 §b单方块§r。模式跟着这把镐走，换手、丢进箱子都不会丢。<BR>"
        "§l单方块§r 普通挖法：挖一个方块，掉一个方块。<BR>"
        "§l隧道§r 挖掉一个方块之后，镐不会停，会沿你面朝的水平方向继续往前挖 16 格。"
        "面朝南，就往前挖出 16 格长的一条洞。<BR>"
        "§l什么时候会停§r 路上遇到空气和液体会穿过去继续挖；遇到箱子、机器这类带方块实体的，"
        "或者基岩、屏障这类挖不动的，就停在它前面。<BR>"
        "§8隧道挖掉的每一格都照常掉落、照常算耐久。§r",

    "bagua.recipe.xuanshu_pickaxe_2":
        "§l玄枢翎凰焚天镐§r 注魔合成<BR>"
        "§8中心§r 八卦开山辟地镐<BR>"
        "§8座上§r 玄枢碎片 ×4、玄枢锭 ×4（交错摆成一圈）<BR>"
        "§8要素§r 地 64、序 64、风 48、火 48、水 48、熵 48 §8不稳定度§r §c6§r<BR>"
        "§8卦石§r 基座下面 5×5 立 §b§l坎卦石§r ×1"
        "（坎为水为险，镐入地穿山，先镇其险）<BR>"
        "§8注意§r 注魔不会保留中心镐上的附魔与属性：升炉之前，先把该附的魔在本体砧上附好。",

    "bagua.page.pickaxe2_lore":
        "§l焚天镐 · 来历§r<BR>"
        "翎凰是玄枢炉里一只不肯落地的鸟。取它的翎羽，再借焚天之火，"
        "与八卦开山辟地镐一同入炉，三日炉火不熄，才得此镐。<BR>"
        "镐成之后，执之入山，山石自开；持之临渊，渊水倒流。<BR>"
        "§8相传此物本非人力可铸，是天工借了人的手，方才成就。§r",

    # ---------------- 镇海锄 ----------------
    "bagua.page.hoe_intro":
        "§l玄枢玄冥镇海锄§r<BR>"
        "§8一把锄，管一片地：翻土、引水、催苗。§r<BR>"
        "§l基础属性§r 与焚天镐同炉所出、同一档位（耐久 52000，挖掘速度 40）。<BR>"
        "§l一、一次翻 9×9 耕地§r 右键任意方块，以它为中心，把 9×9 范围内的可耕地"
        "一次翻成耕地：草方块、泥土、砂土、土径、缠根泥土都认，上方有东西的格子不动。"
        "整片一次只花 1 点耐久。<BR>"
        "§l二、潜行右键放一格水§r 在点击方块的正上方放一个水源方块，"
        "用来给翻好的地引水。<BR>"
        "§l三、手持催熟作物§r 主手拿着它的时候，每秒把周围 9×9 范围内还没长成的作物"
        "往前推一级。小麦、胡萝卜、马铃薯、甜菜这类直接长一级；南瓜茎、西瓜茎、"
        "甘蔗、竹、树苗这类相当于替它撒了一次骨粉。<BR>"
        "§8不看光照，也不看水分：地里没水、洞里没光，照样生长。§r<BR>"
        "§8提醒：举着它站在苗圃旁边，树苗也会长得很快。§r",

    "bagua.recipe.xuanshu_hoe":
        "§l玄枢玄冥镇海锄§r 注魔合成<BR>"
        "§8中心§r 八卦开山辟地镐<BR>"
        "§8座上§r 玄枢碎片 ×4、玄枢锭 ×2、钻石锄 ×1（交错摆成一圈）<BR>"
        "§8要素§r 地 64、水 48、序 48、风 32、火 32、熵 32 §8不稳定度§r §c6§r<BR>"
        "§8卦石§r 基座下面 5×5 立 §b§l坎卦石§r ×1"
        "（坎为水，农桑需水，玄冥镇海即是此意）<BR>"
        "§8注意§r 注魔不会保留中心镐上的附魔与属性：升炉之前，先把该附的魔在本体砧上附好。",

    "bagua.page.hoe_lore":
        "§l镇海锄 · 来历§r<BR>"
        "玄冥是北方的水神，掌一海之水。铸这柄锄的时候，炉底先垫了一层北海水精，"
        "所以锄身终年微凉，握久了掌心会沁出水意。<BR>"
        "抡下去，土自己让开；举起来，田里的苗自己往上长。<BR>"
        "§8有人问它为什么叫「镇海」：水能载舟，也能覆舟，田里的水也一样。§r",
}

EN_TEXT = {
    # ---------------- Heaven-Scorching Pickaxe ----------------
    "bagua.page.pickaxe2_intro":
        "§lXuanshu Plume-Phoenix Heaven-Scorching Pickaxe§r<BR>"
        "§8One pick, three jobs: stone, wood and earth.§r<BR>"
        "§lBasics§r 52,000 durability, mining speed 40, +6 attack, enchantability 25, "
        "same harvest tier as netherite.<BR>"
        "§l1. Pick, axe and shovel§r It counts as the right tool for stone, wood and earth alike, "
        "mines them at full speed, and rolls drops normally.<BR>"
        "§8It chops wood and shovels earth, but it will not strip bark or pave a path for you: "
        "those tricks belong to the axe and the shovel themselves.§r<BR>"
        "§l2. Built-in Fortune XVI§r It comes with the enchantment, no anvil needed. "
        "Ores drop as if mined with Fortune XVI.<BR>"
        "§l3. Two ways of digging§r See the next page.",

    "bagua.page.pickaxe2_tunnel":
        "§lThe pick's two ways of digging§r<BR>"
        "§lHow to switch§r Sneak-right-click to toggle between the two modes; the action bar "
        "tells you which one is active: §6Tunnel§r or §bSingle block§r. The mode travels with "
        "the pick: change hands or drop it in a chest and it stays.<BR>"
        "§lSingle block§r Ordinary mining: one block in, one block out.<BR>"
        "§lTunnel§r Break one block and the pick keeps going, cutting 16 blocks onwards along the "
        "horizontal direction you face. Face south and you drive a 16-block tunnel southwards.<BR>"
        "§lWhen it stops§r Air and fluids are passed through; chests, machines and other block "
        "entities, and unbreakable blocks such as bedrock and barriers, stop it short.<BR>"
        "§8Every block the tunnel breaks drops and costs durability as usual.§r",

    "bagua.recipe.xuanshu_pickaxe_2":
        "§lXuanshu Plume-Phoenix Heaven-Scorching Pickaxe§r infusion<BR>"
        "§8Centre§r Bagua World-Splitting Pickaxe<BR>"
        "§8Pedestals§r 4 Xuanshu Shard, 4 Xuanshu Ingot (alternating around the ring)<BR>"
        "§8Aspects§r 64 Terra, 64 Ordo, 48 Aer, 48 Ignis, 48 Aqua, 48 Perditio "
        "§8Instability§r §c6§r<BR>"
        "§8Gate§r §b§lKan stone§r x1 on the 5x5 under the pedestal "
        "(Kan is water and peril; a pick that bores through mountains calms that peril first)<BR>"
        "§8Note§r Infusion keeps neither the enchantments nor the attributes of the central pick: "
        "enchant it at the base mod's anvil first.",

    "bagua.page.pickaxe2_lore":
        "§lThe Heaven-Scorcher: where it came from§r<BR>"
        "The plume-phoenix is a bird in the Xuanshu furnace that never lands. Its plumes, "
        "the fire that scorches the heavens, and the Bagua World-Splitting Pickaxe went into "
        "the furnace together; the fire burned three days and nights before the pick came out.<BR>"
        "Since then, carry it into a mountain and the stone opens; hold it over an abyss and the "
        "water turns back.<BR>"
        "§8They say no mortal could have forged it: heaven lent a hand to mortal hands, "
        "and only then was it finished.§r",

    # ---------------- Sea-Quelling Hoe ----------------
    "bagua.page.hoe_intro":
        "§lXuanshu Dark-Water Sea-Quelling Hoe§r<BR>"
        "§8One hoe for a whole field: turn the soil, bring the water, hurry the sprouts.§r<BR>"
        "§lBasics§r Out of the same furnace as the Heaven-Scorcher, same tier "
        "(52,000 durability, mining speed 40).<BR>"
        "§l1. Till 9x9 in one click§r Right-click any block and every tillable block in the 9x9 "
        "around it becomes farmland: grass block, dirt, coarse dirt, dirt path and rooted dirt "
        "all count. Blocks with something on top are left alone. The whole patch costs "
        "1 durability.<BR>"
        "§l2. Sneak-right-click to place water§r Drops one water source block on top of the "
        "block you clicked, for watering the field you just tilled.<BR>"
        "§l3. Crops grow in hand§r While held in the main hand, every second it pushes every "
        "unripe crop within 9x9 forward by one stage. Wheat, carrots, potatoes and beetroot "
        "advance a stage; melon stems, pumpkin stems, sugar cane, bamboo and saplings are "
        "treated as if you had used bone meal on them.<BR>"
        "§8Light and water are not checked at all: dry soil and dark caves grow just the "
        "same.§r<BR>"
        "§8A warning: stand near a sapling nursery holding this and the saplings will grow into "
        "trees fast.§r",

    "bagua.recipe.xuanshu_hoe":
        "§lXuanshu Dark-Water Sea-Quelling Hoe§r infusion<BR>"
        "§8Centre§r Bagua World-Splitting Pickaxe<BR>"
        "§8Pedestals§r 4 Xuanshu Shard, 2 Xuanshu Ingot, 1 Diamond Hoe "
        "(alternating around the ring)<BR>"
        "§8Aspects§r 64 Terra, 48 Aqua, 48 Ordo, 32 Aer, 32 Ignis, 32 Perditio "
        "§8Instability§r §c6§r<BR>"
        "§8Gate§r §b§lKan stone§r x1 on the 5x5 under the pedestal "
        "(Kan is water; a hoe serves the fields, and fields need water)<BR>"
        "§8Note§r Infusion keeps neither the enchantments nor the attributes of the central pick: "
        "enchant it at the base mod's anvil first.",

    "bagua.page.hoe_lore":
        "§lThe Sea-Queller: where it came from§r<BR>"
        "Xuanming is the water god of the north, keeper of a whole sea. When this hoe was cast, "
        "a layer of northern sea-essence was laid in the furnace first, so the shaft stays cool "
        "all year and your palm grows damp holding it.<BR>"
        "Swing it down and the soil yields on its own; raise it and the sprouts in the field "
        "climb on their own.<BR>"
        "§8Someone asked why it is called the Sea-Queller: water carries the boat and overturns "
        "it, and the water in a field is no different.§r",
}


def patch_lang(path, mapping):
    doc = load(path)
    changed = [k for k, v in mapping.items() if doc.get(k) != v]
    doc.update(mapping)
    dump(path, doc)
    print("  更新 %d 个键" % len(changed))


def main():
    patch_research()
    print("3. 语言 zh_cn：")
    patch_lang(ZH, ZH_TEXT)
    print("4. 语言 en_us：")
    patch_lang(EN, EN_TEXT)


if __name__ == "__main__":
    main()
