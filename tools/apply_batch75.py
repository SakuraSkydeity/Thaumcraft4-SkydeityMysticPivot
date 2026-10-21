#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
第七十五批数据改动（幂等，可重复执行）：

  1. 研究树 `bagua.json`：
     * 三台工作方块**串成一条连线**：炎炉 → 坩埚 → 炼金炉（原来三台各自连巽卦）；
     * 玄枢充能器挪到**玄枢坩埚的正上方** (3,-5)，父条目改成坩埚。
  2. 卦象门禁 `machines.json`：玄枢充能器注魔要**立巽卦**一块。
  3. 配方 `recipe/xuanshu_charger.json`：座上**加 2 枚本体高级节点稳定器**
     （`thaumcraft:advanced_node_stabilizer`，8 件 → 10 件，前后镜像对称）。
  4. 语言：净界石改名**九天净界玉**（物品名 / 研究条目名 / 书页正文全部同步）、
     补上充能器缺失的 `tc.research_name` / `tc.research_text`（第七十四批漏了这两把钥匙，
     书里那条的名字才会原样显示成键名）、焚天镐三用改「镐斧铲」、切换提示只报模式名。
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

ADV_STABILIZER = "thaumcraft:advanced_node_stabilizer"


def load(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def dump(path, doc, trailing_newline=True):
    text = json.dumps(doc, ensure_ascii=False, indent=2) + ("\n" if trailing_newline else "")
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(text)
    print("  写回", os.path.relpath(path, ROOT))


def insert_after(mapping, anchor, items):
    out = {}
    for key, value in mapping.items():
        out[key] = value
        if key == anchor:
            out.update(items)
    if anchor not in mapping:
        out.update(items)
    return out


# ----------------------------------------------------------------------
# 1. 研究树：三器串链 + 充能器挪到坩埚上方
# ----------------------------------------------------------------------

CHAIN = [
    ("MACHINE_CRUCIBLE", ["skydeitymysticpivot:MACHINE_FURNACE"]),
    ("MACHINE_ALCHEMY", ["skydeitymysticpivot:MACHINE_CRUCIBLE"]),
]
CHARGER_POS = (3, -5)
CHARGER_PARENT = "skydeitymysticpivot:MACHINE_CRUCIBLE"


def patch_research():
    doc = load(RESEARCH)
    entries = {e["key"].split(":")[-1]: e for e in doc["entries"]}

    for name, parents in CHAIN:
        entry = entries[name]
        if entry["parents"] != parents:
            print("1. 三器串链：%s 的父 → %s" % (name, parents[0].split(":")[-1]))
            entry["parents"] = list(parents)

    charger = entries["XUANSHU_CHARGER"]
    if (charger["display_column"], charger["display_row"]) != CHARGER_POS:
        print("1. 充能器位置：(%d,%d) → %s"
              % (charger["display_column"], charger["display_row"], CHARGER_POS))
        charger["display_column"], charger["display_row"] = CHARGER_POS
    if charger["parents"] != [CHARGER_PARENT]:
        print("1. 充能器的父 → %s" % CHARGER_PARENT.split(":")[-1])
        charger["parents"] = [CHARGER_PARENT]

    dump(RESEARCH, doc, trailing_newline=False)


def check_research():
    doc = load(RESEARCH)
    keys = {e["key"] for e in doc["entries"]}
    pos = {}
    bad = []
    for entry in doc["entries"]:
        for parent in entry["parents"] + entry.get("hidden_parents", []):
            if parent not in keys:
                bad.append((entry["key"], parent))
        cell = (entry["display_column"], entry["display_row"])
        if cell in pos:
            bad.append((cell, pos[cell], entry["key"]))
        pos[cell] = entry["key"]
    for name in ("MACHINE_FURNACE", "MACHINE_CRUCIBLE", "MACHINE_ALCHEMY", "XUANSHU_CHARGER"):
        entry = next(e for e in doc["entries"] if e["key"].endswith(":" + name))
        print("   %-16s (%2d,%2d) 父 = %s"
              % (name, entry["display_column"], entry["display_row"],
                 [p.split(":")[-1] for p in entry["parents"]]))
    print("   悬空父 / 坐标重复:", bad if bad else "无")


# ----------------------------------------------------------------------
# 2. 门禁
# ----------------------------------------------------------------------

XUN_GATE = {
    "trigram": "xun",
    "mode": "count",
    "min": 1,
    "radius": 2,
    "height": 1,
    "relief": 2,
    "relief_max": 3,
}


def patch_gates():
    doc = load(GATES)
    key = NS + ":xuanshu_charger"
    if key not in doc:
        doc[key] = dict(XUN_GATE)
        print("2. 门禁：充能器 → 立巽卦")
    else:
        print("2. 门禁：充能器条目已存在（%s）" % doc[key]["trigram"])
    dump(GATES, doc)


# ----------------------------------------------------------------------
# 3. 配方：加两枚高级节点稳定器
# ----------------------------------------------------------------------

# 十件，绕环一圈 —— 前后镜像对称（第 0 与第 9、第 1 与第 8 …… 同位同物）
COMPONENTS = [
    NS + ":xuanshu_ingot",
    ADV_STABILIZER,
    NS + ":xuanshu_shard",
    NS + ":xuanshu_bagua_salt",
    "thaumcraft:nitor",
    "thaumcraft:nitor",
    NS + ":xuanshu_bagua_salt",
    NS + ":xuanshu_shard",
    ADV_STABILIZER,
    NS + ":xuanshu_ingot",
]


def patch_recipe():
    doc = load(RECIPE_CHARGER)
    doc["components"] = [{"item": item} for item in COMPONENTS]
    dump(RECIPE_CHARGER, doc)
    print("3. 充能器配方：组件 %d 件 %s"
          % (len(COMPONENTS), [c.split(":")[-1] for c in COMPONENTS]))
    # 对称性自证
    pairs = [(COMPONENTS[i], COMPONENTS[len(COMPONENTS) - 1 - i])
             for i in range(len(COMPONENTS) // 2)]
    assert all(a == b for a, b in pairs), "环上不对称: %s" % pairs
    print("   镜像对称：%d 对全部同位同物" % len(pairs))


# ----------------------------------------------------------------------
# 4. 语言
# ----------------------------------------------------------------------

ZH = {
    # ---- 净界石 → 九天净界玉
    "block.skydeitymysticpivot.xuanshu_purifying_stone": "九天净界玉",
    "tc.research_name.skydeitymysticpivot:PURIFYING_STONE": "九天净界玉",
    "message.skydeitymysticpivot.purifying_stone.placed": "九天净界玉已落成，此地的腐化正在退去。",
    "message.skydeitymysticpivot.purifying_stone.array_incomplete":
        "八方还没凑齐：净界玉周围一圈的外八格要各立一块卦位石，八种各一。",
    "bagua.page.purify_intro":
        "§l九天净界玉§r<BR>立在腐化之地里，它会把这一带慢慢洗回来。每两秒扫一遍：腐化的方块"
        "还原成泥土与草皮，腐化之地与阴森的群系退回原样，附近的咒流之垢被吃掉；站在这片里的"
        "玩家，永久与粘性扭曲一并清零，身上那些腐化留下的毛病也一并拔掉。<BR><BR>"
        "单独立着，只管它自己所在的那一个区块。",
    "bagua.page.purify_array":
        "§l八方净界阵§r<BR>净界玉单独立着，只洗它自己所在的那一个区块。想让它管宽，"
        "就围着它摆一座 3×3 的阵。<BR><BR>§l一、备料§r<BR>"
        "· 正中一格：§8九天净界玉§0 一块（注魔配方见本条目后几页）。<BR>"
        "· 紧挨它的八格：§8卦位石§0 各一块，乾、兑、离、震、巽、坎、艮、坤八种各一"
        "（坩埚熬法见「卦位石」条目）。",
    "bagua.page.purify_array2":
        "§l二、摆法§r<BR>九块都摆在同一层地面上。<BR>· 正中一格放净界玉。<BR>"
        "· 紧贴它的八格，每格放一块卦位石。<BR>· 八种卦凑齐即算摆好，哪一格放哪一卦不限。",
    "bagua.page.purify_array3":
        "§l三、开阵§r<BR>阵摆好了也不会自己启动，§8默认是关的§0。手持§8法杖§0右键正中心那块"
        "净界玉，阵才立起来：九块石头一齐换形，化作一座青金祭坛。再右键一次收阵；"
        "外圈任意一块被挖走，阵也会自动散掉。<BR><BR>§l成阵之后§r<BR>"
        "作用范围从「本区块」扩到「以本区块为中心的 3×3 共九个区块」。<BR>"
        "卦位石可挖回、可换地方再立，不是消耗品。",
    "bagua.recipe.xuanshu_purifying_stone":
        "§l九天净界玉§r 注魔<BR>§8中心§r 本体咒流清洁器 §8座上§r 玄枢锭×3、八卦碎片×2、"
        "以太之花×2、玄枢八卦之盐×2<BR>§8基元§r 六基元各 32 §8不稳§r §c6§r<BR><BR>"
        "把清洁器收进一块石头里，只是它不擦一时一地，而是把一片地界慢慢洗回原样。",

    # ---- 焚天镐：三用改「镐 · 斧 · 铲」，提示只报模式名
    "message.skydeitymysticpivot.tunnel_dig.on": "§6隧道§r",
    "message.skydeitymysticpivot.tunnel_dig.off": "§b单方块§r",
    "bagua.page.pickaxe2_intro":
        "§l玄枢翎凰焚天镐§r<BR>八卦开山辟地镐再注玄枢之气而成：耐久五万二、掘速四十、攻击加六、"
        "附魔二十五，样样压过下界合金，挖掘等级亦与下界合金同级（原版没有更高的级）。<BR>"
        "镐身兼三用：石也挖得、木也砍得、土也铲得，且自带§b时运 XVI§r。<BR>"
        "§l升级之路§r 以八卦开山辟地镐为中心，座上玄枢碎片与玄枢锭交错各四。<BR>"
        "§8▍立卦§r §b§l坎卦石§r §8×1（基座下面 5×5）§r：坎为水为险，镐入地穿山，先镇其险。<BR><BR>"
        "§8注魔不保留附魔与属性：升炉之前，先把该附的魔在本体砧上附好。§r",
    "bagua.page.pickaxe2_tunnel":
        "§l焚天镐·两种掘法§r<BR>潜行右键镐身，在两种掘法之间来回切，动作栏只报一句："
        "§6隧道§r 或 §b单方块§r。<BR>"
        "§l单方块§r 寻常挖法，挖哪个方块，掉哪个方块。<BR>"
        "§l隧道§r 挖掉一个方块，镐锋不停，沿你面朝的方向再凿十六格；面朝南，"
        "就往前挖出一条十六格长的洞。<BR>"
        "隧道一路凿下去：§8空气与流体穿过去§0，§8箱子、机器这类方块实体§0与"
        "§8基岩、屏障这类挖不动的§0会叫你停下。<BR><BR>"
        "§8三用之分§r 它砍得动木、铲得动土，是把它当斧当铲使；可它终究是镐，"
        "不会替你去皮、也不会替你踩出小径。§r",

    # ---- 充能器：补研究名 / 研究描述，配方页与来历页同步
    "tc.research_name.skydeitymysticpivot:XUANSHU_CHARGER": "玄枢充能器",
    "tc.research_text.skydeitymysticpivot:XUANSHU_CHARGER": "三器之外，养节点的那一路。",
    "bagua.page.charger_intro2":
        "§l来历与告诫§r<BR>本体要三台机器围成一圈才做得成这桩事，这台把它压进了一格："
        "一架三足的鼎，鼎腹里托着一枚星焰，换能器就架在这团火上。<BR>"
        "座上那两枚§5高级节点稳定器§0不是摆设：要像它们一样把节点夹住，才谈得上「充」。<BR>"
        "§c⚠ 充能之后若把充能器挖走，节点会像本体装配被破坏那般炸开。§r"
        "立它之前先想清楚：这台是长在节点上的，不是摆在仓库里的。<BR><BR>"
        "§8三台机器管「做东西」，这一台管「养节点」，产线之外的那一路到此才算齐。§r",
    "bagua.recipe.xuanshu_charger":
        "§l玄枢充能器§r 注魔<BR>§8中心§r 本体节点换能器 §8座上§r 玄枢锭×2、玄枢碎片×2、"
        "玄枢八卦之盐×2、闪耀之光×2、高级节点稳定器×2（交错摆一圈）<BR>"
        "§8基元§r 六基元各 32 §8不稳§r §c8§r<BR>"
        "§8▍立卦§r §b§l巽卦石§r §8×1（基座下面 5×5）§r：巽为风，灵气随风聚散，"
        "先立巽，风才归穴。<BR><BR>"
        "三足的鼎，腹里点着一枚星焰，节点换能器就架在这团火上。",
}

EN = {
    # ---- Purifying Stone → Nine Heavens Purifying Jade
    "block.skydeitymysticpivot.xuanshu_purifying_stone": "Nine Heavens Purifying Jade",
    "tc.research_name.skydeitymysticpivot:PURIFYING_STONE": "Nine Heavens Purifying Jade",
    "message.skydeitymysticpivot.purifying_stone.placed":
        "The Nine Heavens Purifying Jade is set; the corruption here is receding.",
    "message.skydeitymysticpivot.purifying_stone.array_incomplete":
        "The eight directions are not filled yet: each of the eight outer cells around the jade "
        "needs one trigram stone, one of each kind.",
    "bagua.page.purify_intro":
        "§lNine Heavens Purifying Jade§r<BR>Stand it in tainted land and it washes the area back, "
        "a sweep every two seconds: tainted blocks return to dirt and grass, tainted and eerie "
        "biomes revert, nearby flux goo is eaten; players standing in the area have permanent and "
        "sticky warp cleared and the lingering corruptions pulled off them.<BR><BR>"
        "Standing alone it only covers the chunk it sits in.",
    "bagua.page.purify_array":
        "§lThe Eightfold Array§r<BR>Standing alone, the purifying jade washes only its own chunk. "
        "To widen its reach, ring it with a 3x3 array.<BR><BR>§l1. Parts§r<BR>"
        "- Centre cell: one §8Nine Heavens Purifying Jade§0 (infusion recipe later in this entry).<BR>"
        "- The eight cells around it: one §8trigram stone§0 each, one of every trigram "
        "(crucible recipes in the Trigram Stones entry).",
    "bagua.page.purify_array2":
        "§l2. Layout§r<BR>All nine cells sit on the same ground layer.<BR>"
        "- The centre cell takes the purifying jade.<BR>"
        "- The eight cells touching it take one trigram stone each.<BR>"
        "- All eight trigrams must be present; which cell gets which does not matter.",
    "bagua.page.purify_array3":
        "§l3. Raising it§r<BR>A finished array does not start on its own - §8it is off by default§0. "
        "§8Right-click the centre jade with a wand§0 to raise it: all nine stones take altar shape "
        "at once. Right-click again to lower it; dig away any one ring stone and the array drops on "
        "its own.<BR><BR>§lOnce raised§r<BR>The range grows from this one chunk to the nine chunks "
        "centred here.<BR>Trigram stones can be dug back up and re-set elsewhere - they are not "
        "consumed.",
    "bagua.recipe.xuanshu_purifying_stone":
        "§lNine Heavens Purifying Jade§r infusion<BR>§8Centre§r flux scrubber §8Pedestals§r "
        "3 Xuanshu Ingots, 2 Bagua Shards, 2 Ethereal Blooms, 2 Xuanshu Bagua Salt<BR>"
        "§8Aspects§r 32 of each of the six §8Instability§r §c6§r<BR><BR>"
        "The scrubber folded into a single stone - except it does not wipe one spot at a time, "
        "it washes a whole stretch of land back.",

    # ---- Pickaxe: pick / axe / shovel, mode-only feedback
    "message.skydeitymysticpivot.tunnel_dig.on": "§6Tunnel§r",
    "message.skydeitymysticpivot.tunnel_dig.off": "§bSingle block§r",
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
        "§8Infusion keeps neither enchantments nor attributes: enchant it at the base mod's anvil "
        "first.§r",
    "bagua.page.pickaxe2_tunnel":
        "§lThe pick's two ways of digging§r<BR>Sneak-right-click the pick to switch between them; "
        "the action bar reports one word only: §6Tunnel§r or §bSingle block§r.<BR>"
        "§lSingle block§r ordinary mining - one block in, one block out.<BR>"
        "§lTunnel§r break one block and the pick does not stop: it keeps cutting sixteen blocks "
        "along the way you face. Face south and you drive a sixteen-block hole southwards.<BR>"
        "All the way down the tunnel: §8air and fluids are passed through§0, while "
        "§8block entities such as chests and machines§0 and §8unbreakable blocks such as bedrock "
        "and barriers§0 stop it.<BR><BR>"
        "§8Three uses, one pick§r It chops wood and shovels earth as an axe and a shovel would, "
        "but it is still a pickaxe: it will not strip bark for you, nor pave a path.§r",

    # ---- Charger
    "tc.research_name.skydeitymysticpivot:XUANSHU_CHARGER": "Xuanshu Charger",
    "tc.research_text.skydeitymysticpivot:XUANSHU_CHARGER":
        "The line beyond the three machines: feeding the node.",
    "bagua.page.charger_intro2":
        "§lOrigin, and a warning§r<BR>The base mod needs three machines arranged around the node; "
        "this one squeezes the whole thing into a single block - a three-legged ding with a "
        "star-flame cupped in its belly, and the transducer seated on that flame.<BR>"
        "The two §5Advanced Node Stabilizers§0 on the pedestals are not decoration: it has to hold "
        "the node the way they do before there is anything to charge.<BR>"
        "§c⚠ Once charged, digging the charger up makes the node explode, exactly as breaking the "
        "base mod's assembly does.§r Think before you place it: this one grows on the node, "
        "it does not sit in a chest.<BR><BR>"
        "§8The three machines make things; this one feeds the node - the one line that ran "
        "outside the production chain is now complete.§r",
    "bagua.recipe.xuanshu_charger":
        "§lXuanshu Charger§r infusion<BR>§8Centre§r node transducer §8Pedestals§r 2 Xuanshu Ingot, "
        "2 Advanced Node Stabilizer, 2 Xuanshu Shard, 2 Xuanshu Bagua Salt, 2 Nitor "
        "(alternating around the ring)<BR>"
        "§8Aspects§r 32 of each primal §8Instability§r §c8§r<BR>"
        "§8▍Gate§r §b§lXun stone§r §8x1 (5x5 under the pedestal)§r - Xun is wind, and vis gathers "
        "and scatters with the wind; raise Xun first, and the wind settles into the node.<BR><BR>"
        "Three legs under a ding, a star-flame lit in its belly: that is where the transducer sits.",
}

NEW_AFTER = {
    "tc.research_name.skydeitymysticpivot:PURIFYING_STONE": [
        ("tc.research_name.skydeitymysticpivot:XUANSHU_CHARGER", None),
        ("tc.research_text.skydeitymysticpivot:XUANSHU_CHARGER", None),
    ],
}


def patch_lang(name, texts):
    path = os.path.join(LANG, name + ".json")
    doc = load(path)
    before = len(doc)
    doc.update(texts)

    # 新增键（研究名那两把钥匙）插到研究名键那一区，别都堆到文件末尾
    for anchor, items in NEW_AFTER.items():
        payload = {key: (value if value is not None else doc[key]) for key, value in items}
        doc = insert_after(doc, anchor, payload)

    dump(path, doc)
    print("4. %s：%d → %d 键" % (name, before, len(doc)))


def main():
    patch_research()
    check_research()
    patch_gates()
    patch_recipe()
    patch_lang("zh_cn", ZH)
    patch_lang("en_us", EN)
    print("第七十五批数据改动完成。")


if __name__ == "__main__":
    main()
