# -*- coding: utf-8 -*-
"""12.63 批：魔导书超页修复。

背景（实测，见 book_lines.py）：
  * 页宽 139、行高 9、纸面可容 ~22 行（pageIndex==0 的页因标题条低 25px ⇒ ~19 行）。
  * ★ 本体的折行把「以 \n 结尾的段落」再切一刀 ⇒ **每一个 <BR> 都会多出一整行空行**，
    所以书页实际是「双倍行距」，作者原来按 20 行写的内容一定会溢出。

策略：
  * 能加页的条目（无配方页下标约束）→ 拆页；
  * 有条目内含配方页的（配方页必须落在偶数下标）→ 一次**加偶数页**，下标奇偶不变；
  * 只差一两行的 → 收紧文字，不拆页。
"""
import io
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", ".."))
LANG = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/lang")
RES = os.path.join(ROOT, "src/main/resources/data/skydeitymysticpivot/thaumcraft/research/bagua.json")

ZH = {}
EN = {}

# ---------------------------------------------------------------- 1. 玄枢净界石
ZH["bagua.page.purify_array"] = (
    "§l八方净界阵§r<BR>净界石单独立着，只洗它自己所在的那一个区块。想让它管宽，"
    "就围着它摆一座 3×3 的阵。<BR><BR>"
    "§l一、备料§r<BR>"
    "· 正中一格：§8玄枢净界石§0 一块（注魔配方见本条目后几页）。<BR>"
    "· 紧挨它的八格：§8卦位石§0 各一块，乾、兑、离、震、巽、坎、艮、坤八种各一"
    "（坩埚熬法见「卦位石」条目）。"
)
ZH["bagua.page.purify_array2"] = (
    "§l二、摆法§r<BR>九块都摆在同一层地面上。<BR>"
    "· 正中一格放净界石。<BR>"
    "· 紧贴它的八格，每格放一块卦位石。<BR>"
    "· 八种卦凑齐即算摆好，哪一格放哪一卦不限。"
)
ZH["bagua.page.purify_array3"] = (
    "§l三、开阵§r<BR>阵摆好了也不会自己启动，§8默认是关的§0。"
    "手持§8法杖§0右键正中心那块净界石，阵才立起来：九块石头一齐换形，化作一座青金祭坛。"
    "再右键一次收阵；外圈任意一块被挖走，阵也会自动散掉。<BR><BR>"
    "§l成阵之后§r<BR>作用范围从「本区块」扩到「以本区块为中心的 3×3 共九个区块」。<BR>"
    "卦位石可挖回、可换地方再立，不是消耗品。"
)

EN["bagua.page.purify_array"] = (
    "§lThe Eightfold Array§r<BR>Standing alone, the Purifying Stone washes only its own chunk. "
    "To widen its reach, ring it with a 3x3 array.<BR><BR>"
    "§l1. Parts§r<BR>"
    "- Centre cell: one §8Xuanshu Purifying Stone§0 (infusion recipe later in this entry).<BR>"
    "- The eight cells around it: one §8trigram stone§0 each, one of every trigram "
    "(crucible recipes in the Trigram Stones entry)."
)
EN["bagua.page.purify_array2"] = (
    "§l2. Layout§r<BR>All nine cells sit on the same ground layer.<BR>"
    "- The centre cell takes the Purifying Stone.<BR>"
    "- The eight cells touching it take one trigram stone each.<BR>"
    "- All eight trigrams must be present; which cell gets which does not matter."
)
EN["bagua.page.purify_array3"] = (
    "§l3. Raising it§r<BR>A finished array does not start on its own - §8it is off by default§0. "
    "§8Right-click the centre stone with a wand§0 to raise it: all nine stones take altar shape at once. "
    "Right-click again to lower it; dig away any one ring stone and the array drops on its own.<BR><BR>"
    "§lOnce raised§r<BR>The range grows from this one chunk to the nine chunks centred here.<BR>"
    "Trigram stones can be dug back up and re-set elsewhere - they are not consumed."
)

# ---------------------------------------------------------------- 2. 四神兽之锭
ZH["bagua.page.ingot_a"] = (
    "§l四神兽之锭的摆法§r<BR>四种锭都在§5奥术工作台§0里合成，每次耗六基元各一百。"
    "按 3×3 的九宫格摆：<BR>"
    "§8正中§0 神秘锭<BR>§8四角§0 世界盐各一（共四块）<BR>§8下排正中§0 钻石"
)
ZH["bagua.page.ingot_a2"] = (
    "§8上排正中、中排两侧§0 按兽种各放一味：<BR>"
    "青龙 上排正中放末地水晶，中排两侧放龙息。<BR>"
    "朱雀 上排正中放烈焰粉，中排两侧放闪耀之光。"
)
ZH["bagua.page.ingot_a3"] = (
    "白虎 上排正中放方解石，中排两侧放水银。<BR>"
    "玄武 上排正中放海龟鳞甲，中排两侧放海晶碎片。"
)

EN["bagua.page.ingot_a"] = (
    "§lHow to lay out the four beast ingots§r<BR>"
    "All four ingots are made in the §5Arcane Workbench§0, costing 100 of each primal aspect. "
    "Lay them out on a 3x3 grid:<BR>"
    "§8Centre§0 Mystic Ingot<BR>§8Four corners§0 one World Salt each (four in all)<BR>"
    "§8Bottom middle§0 Diamond"
)
EN["bagua.page.ingot_a2"] = (
    "§8Top middle and both middle sides§0 one reagent per beast:<BR>"
    "Azure Dragon: Ender Crystal top middle, Dragon Breath on both middle sides.<BR>"
    "Vermilion Bird: Blaze Powder top middle, Glowstone Dust on both middle sides."
)
EN["bagua.page.ingot_a3"] = (
    "White Tiger: Calcite top middle, Quicksilver on both middle sides.<BR>"
    "Black Tortoise: Turtle Scute top middle, Prismarine Shard on both middle sides."
)

# ---------------------------------------------------------------- 3. 八卦之地气
ZH["bagua.page.terrain_all"] = (
    "§l八卦之地气§r<BR>八卦各自在一类地方凝气。一处地方对上哪几条，就凝哪几卦，"
    "可能只中一条，也可能一条都不中。<BR>"
    "§f乾§0 头顶无遮挡的开阔天空<BR>§3兑§0 沼泽（含红树林沼泽）<BR>"
    "§4离§0 地狱的绯红森林与诡异森林<BR>§6震§0 粘土山（恶地）"
)
ZH["bagua.page.terrain_all2"] = (
    "§2巽§0 各类森林<BR>§1坎§0 暖水海洋<BR>§8艮§0 山地（风袭丘陵、尖峭山峰等）<BR>"
    "§8坤§0 第十层以下的深地（Y<10）"
    "<LINE>同时中好几条时，台座头顶会把字一并显出来，但台面上的转化只认优先级最高的那一卦，"
    "次序是离、坎、艮、巽、兑、震、坤、乾。各卦的详细走法见后面几页。"
)

EN["bagua.page.terrain_all"] = (
    "§lThe Earthly Qi of the Eight Trigrams§r<BR>"
    "Each trigram gathers its qi in a certain kind of place. Wherever a place matches one, "
    "that qi gathers there - a spot may match only one, or none at all.<BR>"
    "§fQian§0 open sky, nothing overhead<BR>§3Dui§0 swamps (mangrove swamps included)<BR>"
    "§4Li§0 the Nether's crimson and warped forests<BR>§6Zhen§0 badlands (mesa)"
)
EN["bagua.page.terrain_all2"] = (
    "§2Xun§0 forests of every kind<BR>§1Kan§0 warm oceans<BR>"
    "§8Gen§0 mountains (windswept hills, jagged peaks and the like)<BR>§8Kun§0 deep ground below Y=10"
    "<LINE>Where several match at once the pedestal shows them all above its top, but a transformation "
    "on its face heeds only the highest-priority one, in the order Li, Kan, Gen, Xun, Dui, Zhen, Kun, Qian. "
    "The pages that follow give each trigram's haunts in detail."
)

# ---------------------------------------------------------------- 4. 玄枢台座
ZH["bagua.page.pedestal_altar"] = (
    "§l玄枢台座·成坛之法§r<BR>台座单摆着不成坛，卦象也无从谈起。要让它认气，"
    "先把四角的四象石砖立齐：<BR>"
    "① 找一处平地，正中放一座玄枢台座。<BR>"
    "② 以台座为中心摆一个 3×3：只在§l四个角§r各放一块四象石砖，"
    "紧挨着台座的上、下、左、右四格留空，不要占。"
)
ZH["bagua.page.pedestal_altar2"] = (
    "③ 四块立齐，手持法杖右键台座一下，坛就开了。<BR><BR>"
    "开坛之后，四砖会一起变成金色的斜面、齐齐朝台座拱起，成没成一眼可辨。"
    "要收坛：潜行着再用法杖右键一下。"
)
ZH["bagua.page.pedestal_lands"] = (
    "§l哪里能用§r<BR>八卦各在一类地方凝气，台座认的就是这个：<BR>"
    "§f乾§0 头顶无遮挡的开阔天空<BR>§3兑§0 沼泽（含红树林沼泽）<BR>"
    "§4离§0 地狱的绯红森林与诡异森林<BR>§6震§0 粘土山（恶地）"
)
ZH["bagua.page.pedestal_lands2"] = (
    "§2巽§0 各类森林<BR>§1坎§0 暖水海洋<BR>§8艮§0 山地（风袭丘陵、尖峭山峰等）<BR>"
    "§8坤§0 第十层以下的深地"
    "<LINE>一处地方可能同时对上好几条，那时台座会把字都显出来；但转化只认优先级最高的那一卦，"
    "次序是离、坎、艮、巽、兑、震、坤、乾。"
)

EN["bagua.page.pedestal_altar"] = (
    "§lXuanshu Pedestal: raising the altar§r<BR>"
    "A pedestal on its own is not an altar, and reads no trigram qi. To wake it, "
    "set the four Sixiang stone bricks at the corners first:<BR>"
    "1) Find flat ground and put one Xuanshu Pedestal in the middle.<BR>"
    "2) Lay a 3x3 around it: one Sixiang stone brick in each of the §lfour corners§r, "
    "and leave the four cells touching the pedestal empty."
)
EN["bagua.page.pedestal_altar2"] = (
    "3) With all four in place, right-click the pedestal with a wand and the altar opens.<BR><BR>"
    "Once open, the four bricks turn into golden slopes leaning in towards the pedestal, "
    "so you can tell at a glance. To close it, sneak and right-click with the wand again."
)
EN["bagua.page.pedestal_lands"] = (
    "§lWhere It Works§r<BR>Each trigram gathers its qi in a certain kind of place, "
    "and that is what the pedestal reads:<BR>"
    "§fQian§0 open sky, nothing overhead<BR>§3Dui§0 swamps (mangrove swamps included)<BR>"
    "§4Li§0 the Nether's crimson and warped forests<BR>§6Zhen§0 badlands (mesa)"
)
EN["bagua.page.pedestal_lands2"] = (
    "§2Xun§0 forests of every kind<BR>§1Kan§0 warm oceans<BR>"
    "§8Gen§0 mountains (windswept hills, jagged peaks and the like)<BR>§8Kun§0 deep ground below Y=10"
    "<LINE>A spot may match several at once; then the pedestal shows them all, but a transformation "
    "heeds only the highest-priority one - Li, Kan, Gen, Xun, Dui, Zhen, Kun, Qian."
)

# ---------------------------------------------------------------- 5. 只差一两行的：收紧文字
ZH["bagua.page.source_scan"] = (
    "§l一、扫描现成之物（最快）§r<BR>用神秘测量仪扫一下它们，就能认识对应的卦：<BR>"
    "乾 日光传感器<BR>兑 金苹果<BR>离 烟花火箭<BR>震 钟<BR>巽 音符盒<BR>坎 海草<BR>"
    "艮 高炉<BR>坤 干草块"
)
EN["bagua.page.source_scan"] = (
    "§l1. Scan what already exists (fastest)§r<BR>Scan these with a Thaumometer:<BR>"
    "Qian - Daylight Detector<BR>Dui - Golden Apple<BR>Li - Firework Rocket<BR>Zhen - Bell<BR>"
    "Xun - Note Block<BR>Kan - Seagrass<BR>Gen - Blast Furnace<BR>Kun - Hay Bale"
)
EN["bagua.page.intro"] = (
    "§lXuanshu Aspects§r<BR>The eight trigrams are the key to the xuanshu line. Once you know them you "
    "can scan and combine, and use them to infuse the xuanshu works and build the wands.<BR><BR>"
    "All things are made of aspects, and above the aspects stand the eight trigrams: Qian, Dui, Li, "
    "Zhen, Xun, Kan, Gen and Kun. Each is formed from two aspects you already know, so each is both a "
    "key to research and a stone set into a wand to steady infusion.<BR><BR>"
    "The pages that follow cover how to gain the trigrams, then the order of forging."
)
ZH["bagua.page.caps"] = (
    "§l万法归枢§r<BR>玄枢诸物大体分成三条线，各条目的先后即其炼成顺序：<BR>"
    "§l坎 → 玄枢之锭 → 玄枢碎片§r，碎片再生布匹、杖柄与杖端。<BR>"
    "§l玄枢之锭§r 奥术工作台与注魔两法，附四神兽之阵。<BR>"
    "§l玄枢碎片§r 炼金与玄枢炎炉烧炼，各附五方。<BR>"
    "§l布匹 / 杖柄 / 杖端§r 包裹与装配，各附三方。<BR>"
    "§l八卦之地气§r 由坤卦引出，记八处卦气所凝之地。"
)
EN["bagua.page.caps"] = (
    "§lAll Ways Return to the Pivot§r<BR>The xuanshu works fall into three lines, listed in the "
    "order in which they are forged:<BR>"
    "§lKan -> Xuanshu Ingot -> Xuanshu Shard§r, and shards become cloth, rods and caps.<BR>"
    "§lXuanshu Ingot§r - arcane crafting and infusion, plus the four beast array.<BR>"
    "§lXuanshu Shard§r - alchemy and the Infernal Furnace, five recipes each.<BR>"
    "§lCloth / Rod / Cap§r - wrapping and assembly, three recipes each.<BR>"
    "§lBagua Terrain§r - hung off Kun, records where each qi gathers."
)
ZH["bagua.page.focus"] = (
    "§l玄枢核心（注魔）§r<BR>"
    "§l寰宇§r 元始珍珠居中、六系碎片座之，八卦各百二十八。装于法杖，右键指方块，"
    "耗六基元各一，方块本体立化为掉落物，纵使坚硬如基岩亦然。<BR>"
    "§l玄枢守护§r 不死图腾居中，水银二、闪耀之光二、世界盐四、魔力布匹四座之，八卦各百二十八。"
    "右键耗六基元各二百，得一心绕身旋转，免死一次，心即碎。<BR>"
    "§l传送§r 元始珍珠居中、六系碎片座之，八卦各百二十八。潜行右键把脚下坐标记进杖里，"
    "右键耗六基元各十，即回到那里；落点被堵会向上寻落脚处，找不到则原地不动。"
)
EN["bagua.page.focus"] = (
    "§lXuanshu Foci (infusion)§r<BR>"
    "§lCosmos§r - Pearl centre, six shards around it, 128 of each trigram. On a wand, right-click a "
    "block and spend one of each primal aspect: the block drops as an item, even bedrock.<BR>"
    "§lGuardian§r - Totem centre, 2 Quicksilver, 2 Glowstone Dust, 4 Salis Mundus, 4 enchanted "
    "fabric, 128 of each trigram. Right-click, spend 200 of each aspect: a heart circles you, takes "
    "one death, and breaks.<BR>"
    "§lTeleport§r - Pearl centre, six shards around it, 128 of each trigram. Shift right-click binds "
    "your spot; right-click spends 10 of each aspect to return. A blocked landing searches upward."
)
ZH["bagua.page.stones_place"] = (
    "§l怎么摆§r<BR>以基座下面那一格为中心，铺一层 5×5 的卦位石；只认地面这一层，"
    "多铺的层不算。玄枢台座作为基座时也是一样。<BR>"
    "要几块、要哪几卦，写在那条配方自己的页上；少一块就不开工，配方不被消费，"
    "只冒一股青烟，动作栏会写明缺的是哪一卦。<BR>"
    "§8▍石头不是消耗品§r 立卦只是「摆位」：石头能挖回来、能换地方再立。<BR>"
    "§8▍多立可镇不稳：比要求多出来的每一块压 2 点不稳，至多按三块算。§r"
)
EN["bagua.page.stones_place"] = (
    "§lHow to place them§r<BR>Lay a single 5x5 layer of trigram stones centred on the cell directly "
    "under the pedestal; only the ground layer counts. The same goes when the Xuanshu Pedestal is the "
    "base.<BR>How many and which trigrams is written on that recipe's own page. One stone short and "
    "nothing starts: nothing is consumed, you just get a puff of smoke and the action bar names the "
    "missing trigram.<BR>"
    "§8▍ Stones are not consumed§r Dig them up and set them elsewhere.<BR>"
    "§8▍ Extra stones quell instability: each extra shaves 2 off, up to three.§r"
)
EN["bagua.page.stones_use"] = (
    "§lSetting the Gate§r<BR>A few infusion recipes want trigram stones set first; without them the fire "
    "will not start. The recipe is not consumed: a puff of smoke, and the action bar names the missing "
    "trigram.<BR><BR>"
    "§8▍Which ring§r The §l5x5 single layer§r centred on the §lblock directly below the base§r - "
    "the ground layer only.<BR><BR>"
    "§8▍Extra stones press instability down§r Every stone beyond what the recipe asks for presses "
    "§b2§8 off instability, up to three stones counted (so at most -6).<BR><BR>"
    "§8Which recipe wants which stones is written on its own recipe page.§r"
)

# ---------------------------------------------------------------- 页面结构改动
# 插入页：{entry: [(插入位置, 新页 value 键), ...]}
INSERT = {
    "skydeitymysticpivot:PURIFYING_STONE": [
        (2, "bagua.page.purify_array2"),
        (3, "bagua.page.purify_array3"),
    ],
    "skydeitymysticpivot:INGOTS": [
        (2, "bagua.page.ingot_a2"),
        (3, "bagua.page.ingot_a3"),
    ],
    "skydeitymysticpivot:TERRAIN": [
        (1, "bagua.page.terrain_all2"),
    ],
    "skydeitymysticpivot:PEDESTAL": [
        (1, "bagua.page.pedestal_altar2"),
        (5, "bagua.page.pedestal_lands2"),
    ],
}


def check_roundtrip(path, obj):
    raw = io.open(path, encoding="utf-8").read()
    again = json.dumps(obj, ensure_ascii=False, indent=2)
    return raw.rstrip("\n") == again.rstrip("\n")


def main():
    for name, table in (("zh_cn", ZH), ("en_us", EN)):
        p = os.path.join(LANG, "%s.json" % name)
        d = json.load(io.open(p, encoding="utf-8"), object_pairs_hook=dict)
        assert check_roundtrip(p, d), "%s 不是标准 json.dump(indent=2) 格式，不敢重写" % name
        for k, v in table.items():
            d[k] = v
        io.open(p, "w", encoding="utf-8", newline="\n").write(
            json.dumps(d, ensure_ascii=False, indent=2))
        print("lang ->", p, "+%d keys" % len(table))

    doc = json.load(io.open(RES, encoding="utf-8"), object_pairs_hook=dict)
    # 用户第 6 条前半：玄枢净界石从「坤的右下方」(4,4) 挪到「坤的左下方」(-2,4)
    for e in doc["entries"]:
        if e["key"] == "skydeitymysticpivot:PURIFYING_STONE":
            print("node PURIFYING_STONE: (%s,%s) -> (-2,4)"
                  % (e["display_column"], e["display_row"]))
            e["display_column"] = -2
            e["display_row"] = 4
    for e in doc["entries"]:
        ins = INSERT.get(e["key"])
        if not ins:
            continue
        for pos, key in ins:
            e["pages"].insert(pos, {
                "type": "TEXT",
                "value": key,
                "aspects": {},
                "compound_blueprint": "",
                "recipe_link_policy": "DISABLED",
                "recipe_ids": [],
            })
        print("pages ->", e["key"], "=>", len(e["pages"]))
    io.open(RES, "w", encoding="utf-8", newline="\n").write(
        json.dumps(doc, ensure_ascii=False, indent=2))
    print("research ->", RES)


if __name__ == "__main__":
    main()
