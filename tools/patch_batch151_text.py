# -*- coding: utf-8 -*-
"""第 151 批文案补丁：星位供物改成玄枢碎片 · 「法杖激活」一律加粗 · 星阵页指向多方块图。

只改两个文案模块（batch150_zh.py / batch150_en.py）里的取值，不动其它键；
改完照旧跑 `tools/apply_batch150.py` 写回 research json 与 lang。

为什么单独一个脚本：那两份文案是「四十个键的长字典」，手改容易串行；
这里按「键 -> 新值」整条替换，替换前先核对旧值确实存在，避免改错地方。
"""
import io
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ZH_PATH = os.path.join(ROOT, "tools/batch150_zh.py")
EN_PATH = os.path.join(ROOT, "tools/batch150_en.py")

ZH = {
    "bagua.page.star_craft_board":
        "§l一 · 功能§r<BR>玄星盘阵是机器的开关：阵立起来、星位上有东西，机器头顶才会亮起竖线；"
        "线亮了机器才肯干活，不亮只回一句「星象未合」。<BR>"
        "§l二 · 怎么搭§r<BR>十二块南北斗枢玄星盘摆成一整片：横三块、竖四排，一块挨一块，中间不留空。"
        "结构见下一页的多方块图。摆好之后还差一步：得点它一下才成阵，见再下一页。",

    "bagua.page.star_craft_activate":
        "§l成型§r<BR>§l手持法杖右键任意一块星盘，十二块一起亮，阵就成了§r。<BR>"
        "潜行 + 法杖右键 = 收阵；拆掉任意一块，其余十一块也自动退回阵眼，里面的东西一并掉出来。<BR>"
        "方向不用管，阵自己认北。§8星盘要拿镐子挖才会掉。§r",

    "bagua.page.star_craft_use":
        "§l三 · 星位要求§r<BR>机器离开星阵不转，条件靠星位上的东西凑（星位上摆的东西不消耗）：<BR>"
        "§c赤§r 玄枢炎炉：天枢、天璇、天玑、天权 四格各供一枚§l玄枢锭§r，烧东西翻倍。<BR>"
        "§a绿§r 玄枢坩埚：北斗七座（天枢、天璇、天玑、天权、玉衡、开阳、摇光）各供一枚§l玄枢碎片§r，"
        "未烬的玄枢锭才炼得成玄枢锭。<BR>"
        "§f银白§r 玄枢奥术台、玄枢注灵台：南斗五星（天府、天梁、天机、天同、天相）各供一枚§l玄枢碎片§r，"
        "两台才肯干活。<BR>"
        "盘面一次只显一斗：要炼锭就把盘翻成北斗面，要开两台就翻成南斗面，潜行空手右键换。",

    "bagua.page.star_gate_tables":
        "§l三 · 运转条件§r<BR>这台机器离了星阵不转：<BR>"
        "· 把它摆进星阵外圈的 5×6 一格；<BR>"
        "· 盘面切到§b南斗§r，让§b天府、天梁、天机、天同、天相§r 五个星位§l各供一枚玄枢碎片§r；<BR>"
        "· 头顶亮起§f银白§r竖线才算条件齐了；没亮就点它，只会回一句「星象未合」。<BR>"
        "§8七杀那一格不必供。§r",

    "bagua.page.forged_ingot_recipe":
        "§l二 · 合成要求§r<BR>炼制（配方见上一页）：<BR>"
        "· 锅里：未烬的玄枢锭 1<BR>"
        "· 要素：乾、兑、离、震、巽、坎、艮、坤 各 4<BR>"
        "· 条件：北斗七座各供一枚§l玄枢碎片§r，头顶亮起绿线才算数<BR>"
        "§8坩埚要摆进星阵的 5×6 外圈，见「星辰合成」一条。§r",

    "bagua.page.pedestal_altar2":
        "§l二 · 怎么搭§r<BR>① 找一处平地，正中放一座台座；<BR>"
        "② 以台座为中心摆 3×3，只在四个角各放一块四象石砖，上下左右四格留空；<BR>"
        "③ 四块立齐，§l手持法杖右键台座，坛就开了§r；潜行右键收坛。<BR>"
        "§8示意（■ 四象石砖，台 玄枢台座，· 留空）：§r<BR>■ · ■<BR>· 台 ·<BR>■ · ■<BR>"
        "成坛后四砖会变成金色斜面朝台座拱起，成没成一眼可辨。",

    "bagua.page.purify_array3":
        "§l开阵§r<BR>阵摆好了不会自己启动，默认是关的。"
        "§l手持法杖右键正中那块净界玉，阵才立起来§r：九块一齐换形，化作一座青金祭坛。<BR>"
        "再右键一次收阵；外圈任意一块被挖走，阵也会自动散掉。<BR>"
        "成阵之后，作用范围从本区块扩到以本区块为中心的 3×3 共九个区块。",

    "bagua.page.arcane_table":
        "§l一 · 功能§r<BR>把要升的物品放进台面，再放一枚玄枢锭作染灵；§l持法杖右键§r，"
        "台上那件东西的每一条附魔各升一级，单条封顶 128。<BR>"
        "法杖里六种要素各扣 129 点。<BR>"
        "§8附魔不够、锭不够、或已封顶，都只弹一句提示，不动东西。§r",

    "bagua.page.node_modifier":
        "§l一 · 功能§r<BR>给灵气节点添一门要素：<BR>"
        "· 把缸中节点摆在台正上方；<BR>"
        "· 界面里放一瓶天域之华与一枚玄枢锭；<BR>"
        "· §l持法杖右键§r，节点就多一门要素。<BR>"
        "§8先补六门基础，六门齐了就从其余要素里一直抽。§r",
}

EN = {
    "bagua.page.star_craft_board":
        "§l1. What it is§r<BR>The star dial array is a switch: once the array is formed and its seats are fed, "
        "a machine's overhead line lights; only then will it work. Otherwise it says the stars are not aligned.<BR>"
        "§l2. How to build it§r<BR>Twelve dials laid as one slab: three across, four rows, touching, no gaps; "
        "the structure is drawn on the next page. One step is still missing: it has to be struck once "
        "before it becomes an array, see the page after that.",

    "bagua.page.star_craft_activate":
        "§lForming it§r<BR>§lRight-click any one dial with a wand and all twelve light; the array is made§r.<BR>"
        "Sneak + wand takes it down again; break any one dial and the other eleven revert on their own, "
        "spilling whatever they held.<BR>"
        "No need to aim: the array finds north itself. §8A dial drops only when mined with a pickaxe.§r",

    "bagua.page.star_craft_use":
        "§l3. Seat requirements§r<BR>Machines will not run away from the array; the seats supply the condition "
        "(seated items are only held there, never consumed):<BR>"
        "§cRed§r Infernal Furnace: one §lxuanshu ingot§r on each of Tianshu, Tianxuan, Tianji, Tianquan; smelting doubles.<BR>"
        "§aGreen§r Crucible: one §lxuanshu shard§r on each of the seven northern seats "
        "(Tianshu, Tianxuan, Tianji, Tianquan, Yuheng, Kaiyang, Yaoguang); "
        "only then does the unfired ingot become a xuanshu ingot.<BR>"
        "§fSilver§r Arcane Table and Node Modifier: one §lxuanshu shard§r on each of the five southern stars "
        "(Tianfu, Tianliang, Tianji, Tiantong, Tianxiang); only then will they work.<BR>"
        "One dial shows one dipper at a time: flip to the northern face to refine ingots, "
        "to the southern face to run the two tables; sneak-right-click empty-handed to flip.",

    "bagua.page.star_gate_tables":
        "§l3. Running condition§r<BR>Neither table runs away from the array:<BR>"
        "· put it in the 5x6 ring of the array;<BR>"
        "· flip the dial to §bthe southern dipper§r and put one §lxuanshu shard§r on each of "
        "§bTianfu, Tianliang, Tianji, Tiantong, Tianxiang§r;<BR>"
        "· a §fsilver§r line overhead means the condition is met; without it, it only says the stars are not aligned.<BR>"
        "§8The Qisha seat can be left empty.§r",

    "bagua.page.forged_ingot_recipe":
        "§l2. Requirements§r<BR>Boiling (recipe on the previous page):<BR>"
        "· in the pot: 1 unfired ingot<BR>"
        "· aspects: 4 of each of the eight trigrams<BR>"
        "· condition: one §lxuanshu shard§r on each of the seven northern seats; a green line overhead confirms it<BR>"
        "§8The crucible must sit in the 5x6 ring of the star array; see the star array entry.§r",

    "bagua.page.pedestal_altar2":
        "§l2. How to build it§r<BR>1. find flat ground, put the pedestal in the middle;<BR>"
        "2. lay a 3x3 around it: one Sixiang Stone Brick in each of the four corners, leave the other four cells empty;<BR>"
        "3. with all four set, §lright-click the pedestal with a wand to awaken it§r; sneak-right-click to close it.<BR>"
        "§8Diagram (■ brick, 台 pedestal, · empty):§r<BR>■ · ■<BR>· 台 ·<BR>■ · ■<BR>"
        "Once awake the four bricks tilt up in gold toward the pedestal; you can see at a glance.",

    "bagua.page.purify_array3":
        "§lStarting it§r<BR>The array does not start by itself; it is off by default. "
        "§lRight-click the central stone with a wand and it rises§r: all nine reshape at once into a blue-gold altar.<BR>"
        "Right-click again to close it; dig out any outer stone and it falls apart on its own.<BR>"
        "Once formed, the reach goes from one chunk to the 3x3 of chunks around it.",

    "bagua.page.arcane_table":
        "§l1. What it is§r<BR>Put the item to be raised on the top and one xuanshu ingot in the dye slot; "
        "§lright-click with a wand§r and every enchantment on it goes up one level, capped at 128.<BR>"
        "The wand pays 129 of each of the six primal aspects.<BR>"
        "§8No enchantment, no ingot, or already capped: it only prints a line and touches nothing.§r",

    "bagua.page.node_modifier":
        "§l1. What it is§r<BR>It adds an aspect to an aura node:<BR>"
        "· put the jarred node directly above the table;<BR>"
        "· put one ethereal bloom and one xuanshu ingot in the interface;<BR>"
        "· §lright-click with a wand§r and the node gains one aspect.<BR>"
        "§8It fills the six basics first; once those are in, it draws from the rest.§r",
}


def set_entry(path, values):
    """把 path 里 `"key":` 开头的条目整条换成单行取值。"""
    with io.open(path, encoding="utf-8") as handle:
        lines = handle.read().split("\n")

    for key, value in values.items():
        head = '    "%s":' % key
        matches = [i for i, line in enumerate(lines) if line.strip() == head.strip() or line.startswith(head)]
        if not matches:
            # 新键：插到字典末尾（最后一个独占一行的 `}` 之前）
            tail = max(i for i, line in enumerate(lines) if line == "}")
            lines[tail:tail] = ["", head, '        "%s",' % value]
            continue
        if len(matches) != 1:
            raise SystemExit("%s: 键 %s 命中 %d 次" % (os.path.basename(path), key, len(matches)))
        start = matches[0]
        end = start
        while not lines[end].rstrip().endswith('",'):
            end += 1
            if end - start > 40:
                raise SystemExit("%s: 键 %s 的取值没有结尾" % (os.path.basename(path), key))
        lines[start:end + 1] = [head, '        "%s",' % value]

    with io.open(path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(lines))
    print("%s: 换掉 %d 条" % (os.path.basename(path), len(values)))


def main():
    set_entry(ZH_PATH, ZH)
    set_entry(EN_PATH, EN)


if __name__ == "__main__":
    main()
