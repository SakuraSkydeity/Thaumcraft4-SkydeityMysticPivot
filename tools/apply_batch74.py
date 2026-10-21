#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
第七十四批数据改动（一次性脚本，可重复执行 = 幂等）：

  1. 研究树 `thaumcraft/research/bagua.json`：新增 **玄枢充能器** 条目，
     父条目接在 `MACHINE_ALCHEMY` 之后 —— 三台工作方块在书里正好排成一条斜线
     （炎炉 1,-5 → 坩埚 3,-4 → 炼金炉 5,-3），新条目落在 **7,-2**，把这条线接着画下去。
  2. 卦象门禁 `mysticpivot/trigram_gate/machines.json`：焚天镐 / 镇海锄的注魔各要**立坎卦**一块。
  3. 配方 `recipe/xuanshu_charger.json`：**去掉玄枢水晶簇（魔晶）**（9 件 → 8 件，仍成对）。
  4. 配方 `recipe/xuanshu_ingot.json`：中心由八卦碎片改为**下界之星**。
  5. 语言文件：改名（焚天镐 / 镇海锄）、更新若干页文本、新增充能器两页与配方页、
     新增隧道掘切换提示。

幂等：条目已存在、键已存在时跳过；文本键直接覆盖为最新文案。
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src/main/resources")
DATA = os.path.join(RES, "data/skydeitymysticpivot")
LANG = os.path.join(RES, "assets/skydeitymysticpivot/lang")
NS = "skydeitymysticpivot"

RESEARCH = os.path.join(DATA, "thaumcraft/research/bagua.json")
GATES = os.path.join(DATA, "mysticpivot/trigram_gate/machines.json")
RECIPE_CHARGER = os.path.join(DATA, "recipe/xuanshu_charger.json")
RECIPE_INGOT = os.path.join(DATA, "recipe/xuanshu_ingot.json")


def load(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def dump(path, doc, trailing_newline=True):
    text = json.dumps(doc, ensure_ascii=False, indent=2) + ("\n" if trailing_newline else "")
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(text)
    print("  写回", os.path.relpath(path, ROOT))


def page(kind, value="", recipes=None):
    """研究页工厂 —— 字段与文件里既有的页完全一致。"""
    return {
        "type": kind,
        "value": value,
        "aspects": {},
        "compound_blueprint": "",
        "recipe_link_policy": "DISABLED",
        "recipe_ids": list(recipes or []),
    }


def insert_after(mapping, anchor, items):
    """把 items 插到 anchor 键之后（键不存在则追加到末尾），返回新 dict。"""
    out = {}
    for key, value in mapping.items():
        out[key] = value
        if key == anchor:
            out.update(items)
    if anchor not in mapping:
        out.update(items)
    return out


# ----------------------------------------------------------------------
# 1. 研究树
# ----------------------------------------------------------------------

CHARGER_ENTRY = {
    "key": "skydeitymysticpivot:XUANSHU_CHARGER",
    "category": "skydeitymysticpivot:BAGUA",
    "aspects": {},
    "display_column": 7,
    "display_row": -2,
    "complexity": 1,
    "icon": {"type": "STACK", "stack": {"id": "skydeitymysticpivot:xuanshu_charger"}},
    "flags": ["ROUND", "AUTO_UNLOCK"],
    "parents": ["skydeitymysticpivot:MACHINE_ALCHEMY"],
    "hidden_parents": [],
    "siblings": [],
    "object_triggers": [],
    "entity_triggers": [],
    "aspect_triggers": [],
    "warp": 0,
    "pages": [
        page("TEXT", "bagua.page.charger_intro"),
        page("TEXT", "bagua.page.charger_intro2"),
        page("INFUSION_CRAFTING", recipes=["skydeitymysticpivot:xuanshu_charger"]),
        page("TEXT", "bagua.recipe.xuanshu_charger"),
    ],
}


def ensure_tunnel_page(doc):
    """把「两种掘法」那一页挂到焚天镐条目里（排在简介页之后）。"""
    entry = next(e for e in doc["entries"] if e["key"].endswith(":XUANSHU_PICKAXE_2"))
    if any(p.get("value") == "bagua.page.pickaxe2_tunnel" for p in entry["pages"]):
        return False
    index = next(i for i, p in enumerate(entry["pages"])
                 if p.get("value") == "bagua.page.pickaxe2_intro")
    entry["pages"].insert(index + 1, page("TEXT", "bagua.page.pickaxe2_tunnel"))
    return True


def patch_research():
    doc = load(RESEARCH)
    entries = doc["entries"]
    changed = False

    if not any(e["key"].endswith(":XUANSHU_CHARGER") for e in entries):
        index = next(i for i, e in enumerate(entries) if e["key"].endswith(":MACHINE_ALCHEMY"))
        entries.insert(index + 1, CHARGER_ENTRY)
        changed = True
        print("1. 研究树：新增 XUANSHU_CHARGER（父 = MACHINE_ALCHEMY，位置 7,-2）")
    else:
        print("1. 研究树：XUANSHU_CHARGER 已存在，跳过新增")

    if ensure_tunnel_page(doc):
        changed = True
        print("1b. 研究树：焚天镐条目补上「两种掘法」页")

    if changed:
        dump(RESEARCH, doc, trailing_newline=False)


# ----------------------------------------------------------------------
# 2. 坎卦门禁
# ----------------------------------------------------------------------

KAN_GATE = {
    "trigram": "kan",
    "mode": "count",
    "min": 1,
    "radius": 2,
    "height": 1,
    "relief": 2,
    "relief_max": 3,
}


def patch_gates():
    doc = load(GATES)
    added = []
    for rid in ("xuanshu_pickaxe_2", "xuanshu_hoe"):
        key = "%s:%s" % (NS, rid)
        if key not in doc:
            doc[key] = dict(KAN_GATE)
            added.append(rid)
    dump(GATES, doc)
    print("2. 卦象门禁：新增", ", ".join(added) if added else "（均已存在）")


# ----------------------------------------------------------------------
# 3./4. 配方
# ----------------------------------------------------------------------


def patch_recipes():
    charger = load(RECIPE_CHARGER)
    before = len(charger["components"])
    charger["components"] = [c for c in charger["components"]
                             if c.get("item") != NS + ":xuanshu_crystal_cluster"]
    dump(RECIPE_CHARGER, charger)
    print("3. 充能器配方：组件 %d → %d（去掉玄枢水晶簇）" % (before, len(charger["components"])))

    ingot = load(RECIPE_INGOT)
    old = ingot["central"]["item"]
    ingot["central"]["item"] = "minecraft:nether_star"
    dump(RECIPE_INGOT, ingot)
    print("4. 玄枢锭配方：中心 %s → minecraft:nether_star" % old)


# ----------------------------------------------------------------------
# 5. 语言
# ----------------------------------------------------------------------

ZH = {
    "item.skydeitymysticpivot.xuanshu_pickaxe_2": "玄枢翎凰焚天镐",
    "item.skydeitymysticpivot.xuanshu_hoe": "玄枢玄冥镇海锄",

    "bagua.page.pickaxe2_intro":
        "§l玄枢翎凰焚天镐§r<BR>八卦开山辟地镐再注玄枢之气而成：耐久五万二、掘速四十、攻击加六、"
        "附魔二十五，样样压过下界合金，挖掘等级亦与下界合金同级（原版没有更高的级）。<BR>"
        "镐身兼三用：石也挖得、木也砍得、土也翻得，且自带§b时运 XVI§r。<BR>"
        "§l升级之路§r 以八卦开山辟地镐为中心，座上玄枢碎片与玄枢锭交错各四。<BR>"
        "§8▍立卦§r §b§l坎卦石§r §8×1（基座下面 5×5）§r：坎为水为险，镐入地穿山，先镇其险。<BR><BR>"
        "§8注魔不保留附魔与属性：升炉之前，先把该附的魔在本体砧上附好。§r",

    "bagua.page.pickaxe2_tunnel":
        "§l焚天镐·两种掘法§r<BR>潜行右键镐身，在两种掘法之间来回切，动作栏会报出当下是哪一种。<BR>"
        "§l单方掘§r 寻常挖法，挖哪个方块，掉哪个方块。<BR>"
        "§l隧道掘§r 挖掉一个方块，镐锋不停，沿你面朝的方向再凿十六格；面朝南，"
        "就往前挖出一条十六格长的洞。<BR>"
        "隧道一路凿下去：§8空气与流体穿过去§0，§8箱子、机器这类方块实体§0与"
        "§8基岩、屏障这类挖不动的§0会叫你停下。<BR><BR>"
        "§8三用之分§r 它砍得动木、翻得动地，是把它当斧当锄使；可它终究是镐，"
        "不会替你去皮、也不会替你耕地成畦。§r",

    "bagua.page.hoe_intro":
        "§l玄枢玄冥镇海锄§r<BR>与焚天镐同炉所出：中心仍是那柄八卦开山辟地镐，"
        "注魔之后不改其形、只易其用：耕种翻地，样样比下界合金锄更顺手。<BR>"
        "§l升级之路§r 以八卦开山辟地镐为中心，座上玄枢碎片四、玄枢锭二，另压一枚钻石锄。<BR>"
        "§8▍立卦§r §b§l坎卦石§r §8×1（基座下面 5×5）§r：坎为水，锄事农桑需水，"
        "玄冥镇海即是此意。<BR><BR>"
        "§8注魔不保留附魔与属性：升炉之前，先把该附的魔在本体砧上附好。§r",

    "bagua.recipe.xuanshu_pickaxe_2":
        "§l玄枢翎凰焚天镐§r 注魔<BR>§8中心§r 八卦开山辟地镐 §8座上§r 玄枢碎片×4、玄枢锭×4"
        "（交错摆一圈）<BR>§8基元§r 地·序各 64，风火水熵各 48 §8不稳§r §c6§r<BR>"
        "§8▍立卦§r §b§l坎卦石§r §8×1（基座下面 5×5）§r<BR><BR>"
        "镐为金石之器，升它用的还是金石：碎片为骨、玄枢锭为肉；坎水镇险，方能穿山。",

    "bagua.recipe.xuanshu_hoe":
        "§l玄枢玄冥镇海锄§r 注魔<BR>§8中心§r 八卦开山辟地镐 §8座上§r 玄枢碎片×4、玄枢锭×2、"
        "钻石锄×1（交错摆一圈）<BR>§8基元§r 地 64，水·序各 48，风火熵各 32 §8不稳§r §c6§r<BR>"
        "§8▍立卦§r §b§l坎卦石§r §8×1（基座下面 5×5）§r<BR><BR>"
        "锄事农桑，属地、属水：故以地、水为纲；那枚钻石锄是翻地的旧骨，"
        "玄枢之气只易其用、不改其形。",

    "bagua.page.charger_intro":
        "§l玄枢充能器§r<BR>立于灵气节点正下方，通以红石：十秒，头顶那枚节点就成了充能灵气节点。<BR>"
        "较本体那套三段装配更快也更省：全程至多抽取节点四十点要素。<BR>"
        "充能之后它不歇：节点各门要素的上限随时间一档一档往上抬，每 tick 产出最高能涨到 §b64/t§r；"
        "红石一断，节点便缓缓退回原状。<BR>"
        "§8它只认自己头顶那一格：正上方不是灵气节点，它就只是一尊摆件。§r",

    "bagua.page.charger_intro2":
        "§l来历与告诫§r<BR>本体要三台机器围成一圈才做得成这桩事，这台把它压进了一格："
        "一架三足的鼎，鼎腹里托着一枚星焰，换能器就架在这团火上。<BR>"
        "§c⚠ 充能之后若把充能器挖走，节点会像本体装配被破坏那般炸开。§r"
        "立它之前先想清楚：这台是长在节点上的，不是摆在仓库里的。<BR><BR>"
        "§8三台机器管「做东西」，这一台管「养节点」，产线之外的那一路到此才算齐。§r",

    "bagua.recipe.xuanshu_charger":
        "§l玄枢充能器§r 注魔<BR>§8中心§r 本体节点换能器 §8座上§r 玄枢锭×2、玄枢碎片×2、"
        "玄枢八卦之盐×2、闪耀之光×2（交错摆一圈）<BR>§8基元§r 六基元各 32 §8不稳§r §c8§r<BR>"
        "§8▍立卦§r §8无（此条不涉枢机）§r<BR><BR>"
        "鼎身三足，腹里点着一枚星焰：节点换能器就架在这团火上。",

    "bagua.recipe.xuanshu_ingot":
        "§l玄枢锭§r 注魔<BR>§8中心§r 下界之星 §8座上§r 四神兽之锭各 2、玄枢八卦之盐×4<BR>"
        "§8基元§r 八卦各 10 §8不稳§r §c5§r<BR>§8▍立卦§r §8无（此条不涉枢机）§r<BR><BR>"
        "四神兽之气在此交汇，青赤白黑混作一处，故锭色说不清是青是赤：玄者，黑而有赤，"
        "正是诸色未分的样子。<BR>中心那枚下界之星是引子：天上落下来的东西压着这一炉神兽之气，"
        "才镇得住八门。",

    "message.skydeitymysticpivot.tunnel_dig.on":
        "§6焚天镐：隧道掘§r §8：挖掉一方块，就沿你面朝的方向再凿十六格§r",
    "message.skydeitymysticpivot.tunnel_dig.off":
        "§b焚天镐：单方掘§r §8：寻常挖法，挖哪个掉哪个§r",
}

EN = {
    "item.skydeitymysticpivot.xuanshu_pickaxe_2": "Xuanshu Plume-Phoenix Heaven-Scorching Pickaxe",
    "item.skydeitymysticpivot.xuanshu_hoe": "Xuanshu Dark-Water Sea-Quelling Hoe",

    "bagua.page.pickaxe2_intro":
        "§lXuanshu Plume-Phoenix Heaven-Scorching Pickaxe§r<BR>"
        "The Bagua World-Splitting Pickaxe infused once more with Xuanshu qi: 52,000 durability, "
        "speed 40, +6 attack, enchantability 25 - above netherite in every number, at the same "
        "mining tier (the game has nothing higher).<BR>"
        "It works three ways: stone, wood and earth alike - and carries §bFortune XVI§r of its own.<BR>"
        "§lThe road up§r Bagua World-Splitting Pickaxe at the centre, four Xuanshu Shards and "
        "four Xuanshu Ingots alternating around it.<BR>"
        "§8▍Gate§r §b§lKan stone§r §8x1 (5x5 under the pedestal)§r - Kan is water and peril; "
        "a pick that bores through mountains calms that peril first.<BR><BR>"
        "§8Infusion keeps neither enchantments nor attributes: enchant it at the base mod's anvil first.§r",

    "bagua.page.pickaxe2_tunnel":
        "§lThe pick's two ways of digging§r<BR>Sneak-right-click the pick to switch between them; "
        "the action bar says which one is on.<BR>"
        "§lSingle dig§r ordinary mining - one block in, one block out.<BR>"
        "§lTunnel dig§r break one block and the pick does not stop: it keeps cutting sixteen blocks "
        "along the way you face. Face south and you drive a sixteen-block hole southwards.<BR>"
        "All the way down the tunnel: §8air and fluids are passed through§0, while "
        "§8block entities such as chests and machines§0 and §8unbreakable blocks such as bedrock "
        "and barriers§0 stop it.<BR><BR>"
        "§8Three uses, one pick§r It chops wood and breaks earth as an axe and a hoe would, "
        "but it is still a pickaxe: it will not strip bark for you, nor turn soil into farmland.§r",

    "bagua.page.hoe_intro":
        "§lXuanshu Dark-Water Sea-Quelling Hoe§r<BR>Out of the same furnace as the Heaven-Scorcher: "
        "the Bagua World-Splitting Pickaxe at the centre again, and infusion changes its use, "
        "not its shape - tilling and turning soil, handier than any netherite hoe.<BR>"
        "§lThe road up§r Bagua World-Splitting Pickaxe at the centre, four Xuanshu Shards, "
        "two Xuanshu Ingots and one Diamond Hoe.<BR>"
        "§8▍Gate§r §b§lKan stone§r §8x1 (5x5 under the pedestal)§r - Kan is water; "
        "the hoe serves the fields, and fields need water. Xuanming stilling the sea, exactly so.<BR><BR>"
        "§8Infusion keeps neither enchantments nor attributes: enchant it at the base mod's anvil first.§r",

    "bagua.recipe.xuanshu_pickaxe_2":
        "§lXuanshu Plume-Phoenix Heaven-Scorching Pickaxe§r infusion<BR>"
        "§8Centre§r Bagua World-Splitting Pickaxe §8Pedestals§r 4 Xuanshu Shard, 4 Xuanshu Ingot "
        "(alternating around the ring)<BR>"
        "§8Aspects§r 64 Terra/Ordo, 48 Aer/Ignis/Aqua/Perditio §8Instability§r §c6§r<BR>"
        "§8▍Gate§r §b§lKan stone§r §8x1 (5x5 under the pedestal)§r<BR><BR>"
        "A pick is a thing of metal and stone, and metal and stone raise it: shard for bone, "
        "Xuanshu Ingot for flesh. Kan's water calms the peril, and only then does it bore the mountain.",

    "bagua.recipe.xuanshu_hoe":
        "§lXuanshu Dark-Water Sea-Quelling Hoe§r infusion<BR>"
        "§8Centre§r Bagua World-Splitting Pickaxe §8Pedestals§r 4 Xuanshu Shard, 2 Xuanshu Ingot, "
        "1 Diamond Hoe (alternating around the ring)<BR>"
        "§8Aspects§r 64 Terra, 48 Aqua/Ordo, 32 Aer/Ignis/Perditio §8Instability§r §c6§r<BR>"
        "§8▍Gate§r §b§lKan stone§r §8x1 (5x5 under the pedestal)§r<BR><BR>"
        "A hoe serves the fields: earth and water lead the way. The diamond hoe is the old bone of "
        "tilling; Xuanshu qi changes its use without changing its shape.",

    "bagua.page.charger_intro":
        "§lXuanshu Charger§r<BR>Set directly below an aura node and powered with redstone: "
        "in ten seconds the node above it is an energized aura node.<BR>"
        "Faster and cheaper than the base mod's three-block assembly - at most forty points "
        "drawn from the node.<BR>"
        "And it does not rest once charged: the node's per-aspect caps keep climbing, one step at "
        "a time, lifting its output as high as §b64/t§r. Cut the redstone and the node slowly "
        "reverts.<BR>"
        "§8It only ever looks at the single block above itself: with no aura node overhead, "
        "it is just an ornament.§r",

    "bagua.page.charger_intro2":
        "§lOrigin, and a warning§r<BR>The base mod needs three machines arranged around the node; "
        "this one squeezes the whole thing into a single block - a three-legged ding with a "
        "star-flame cupped in its belly, and the transducer seated on that flame.<BR>"
        "§c⚠ Once charged, digging the charger up makes the node explode, exactly as breaking the "
        "base mod's assembly does.§r Think before you place it: this one grows on the node, "
        "it does not sit in a chest.<BR><BR>"
        "§8The three machines make things; this one feeds the node - the one line that ran "
        "outside the production chain is now complete.§r",

    "bagua.recipe.xuanshu_charger":
        "§lXuanshu Charger§r infusion<BR>§8Centre§r node transducer §8Pedestals§r 2 Xuanshu Ingot, "
        "2 Xuanshu Shard, 2 Xuanshu Bagua Salt, 2 Nitor (alternating around the ring)<BR>"
        "§8Aspects§r 32 of each primal §8Instability§r §c8§r<BR>"
        "§8▍Gate§r §8none (this one is unconstrained)§r<BR><BR>"
        "Three legs under a ding, a star-flame lit in its belly: that is where the transducer sits.",

    "bagua.recipe.xuanshu_ingot":
        "§lXuanshu Ingot§r infusion<BR>§8Centre§r nether star §8Pedestals§r 2 of each beast ingot, "
        "4 Xuanshu Bagua Salt<BR>"
        "§8Aspects§r 10 of each of the eight §8Instability§r §c5§r<BR>"
        "§8▍Gate§r §8none (this one is unconstrained)§r<BR><BR>"
        "The qi of the four beasts meet here, blue and red and white and black mixed into one: "
        "xuan is black shot with red - the look of colours not yet separated.<BR>"
        "The nether star at the centre is the primer: something fallen from the sky weighs down "
        "a whole furnace of beast qi, and only then do the eight gates hold.",

    "message.skydeitymysticpivot.tunnel_dig.on":
        "§6Heaven-Scorching Pickaxe: tunnel dig§r §8- break one block and it keeps cutting "
        "16 blocks along the way you face§r",
    "message.skydeitymysticpivot.tunnel_dig.off":
        "§bHeaven-Scorching Pickaxe: single dig§r §8- ordinary mining, one block at a time§r",
}


def patch_lang(name, texts):
    path = os.path.join(LANG, name + ".json")
    doc = load(path)
    before = len(doc)

    doc.update(texts)                       # 覆盖：新名 / 新文本
    doc = insert_after(doc, "bagua.page.machine_alchemy2", {
        "bagua.page.charger_intro": doc["bagua.page.charger_intro"],
        "bagua.page.charger_intro2": doc["bagua.page.charger_intro2"],
    })
    doc = insert_after(doc, "bagua.recipe.xuanshu_alchemy_furnace", {
        "bagua.recipe.xuanshu_charger": doc["bagua.recipe.xuanshu_charger"],
    })
    doc = insert_after(doc, "bagua.page.pickaxe2_intro", {
        "bagua.page.pickaxe2_tunnel": doc["bagua.page.pickaxe2_tunnel"],
    })
    doc = insert_after(doc, "message.skydeitymysticpivot.teaching_scroll.none", {
        "message.skydeitymysticpivot.tunnel_dig.on": doc["message.skydeitymysticpivot.tunnel_dig.on"],
        "message.skydeitymysticpivot.tunnel_dig.off": doc["message.skydeitymysticpivot.tunnel_dig.off"],
    })
    dump(path, doc)
    print("5. %s：%d → %d 键" % (name, before, len(doc)))


def main():
    patch_research()
    patch_gates()
    patch_recipes()
    patch_lang("zh_cn", ZH)
    patch_lang("en_us", EN)
    print("第七十四批数据改动完成。")


if __name__ == "__main__":
    main()
