# -*- coding: utf-8 -*-
"""第 155 批：书里「卦石」那句话一律标蓝 + 选型页与配方页改成「每行一个内容」。

用户原话：
    「我要的是截图上这种框起来的变蓝，你之前修改了哪里告诉我，然后每行一个内容，
      像这个截图就卦一行、注魔难度一行，都修改。」

第 153 批只在两处总述句上加了蓝（八卦首页的「注魔时必须按配方……」、路线图的
「卦石由台座纳气炼成……」），而配方页与选型页里真正写「基座下面立艮、离、巽、坤」
的地方一处没动，所以用户看不到效果。这一批补齐：

  1. `bagua.page.node_wands2` / `node_caps2` 两个选型页整页重排：
     名字 / 储量耗损 / 难度 / 材料 / 卦石 各占一行；
  2. 六条法杖配方的「要素 X；不稳 Y；基座下面立 Z。」拆成三行；
  3. 其余配方页的「· 卦石：基座下面……」整句包 `§9§l`；
  4. `bagua.gua.qian` 里夹在正文中的那句也包上。

只改 `tools/batch150_zh.py` / `batch150_en.py`（文案源），改完重跑
`tools/apply_batch150.py` 才会写进 lang 与研究条目。

用法：python tools/patch_batch155_note.py [--dry]
"""

import io
import re
import sys

DRY = "--dry" in sys.argv

ZH_PATH = "tools/batch150_zh.py"
EN_PATH = "tools/batch150_en.py"


# ============================================================ 中文：整段替换

ZH_BLOCKS = [
    # ---- 三柄怎么选：一条一行 ----
    (
        '''        "§l二 · 三柄怎么选§r<BR>· 玄枢（银树包裹）：储 2035，耗损 60%，最难，要龙蛋与下界之星，基座下面立齐八卦。<BR>"
        "· 四象（黑曜图层）：储 1949，耗损 80%，次难，基座下面立艮、离、巽、坤。<BR>"
        "· 朱雀（宏伟包裹）：储 918，耗损 90%，最容易，只要金锭与烈焰粉，立离、艮两卦。<BR>"
        "§8耗损越低，同一件事越省魔。§r",''',
        '''        "§l二 · 三柄怎么选§r<BR>"
        "· 玄枢（银树包裹）<BR>"
        "储 2035，耗损 60%<BR>"
        "难度：最难<BR>"
        "要龙蛋与下界之星<BR>"
        "§9§l基座下面立齐八卦。§r<BR>"
        "· 四象（黑曜图层）<BR>"
        "储 1949，耗损 80%<BR>"
        "难度：次难<BR>"
        "§9§l基座下面立艮、离、巽、坤。§r<BR>"
        "· 朱雀（宏伟包裹）<BR>"
        "储 918，耗损 90%<BR>"
        "难度：最容易<BR>"
        "只要金锭与烈焰粉<BR>"
        "§9§l基座下面立离、艮两卦。§r<BR>"
        "§8耗损越低，同一件事越省魔。§r",''',
    ),
    # ---- 三对怎么选：一条一行 ----
    (
        '''        "§l二 · 三对怎么选§r<BR>· 玄枢寰宇鸿蒙：以龙蛋为心，最难，注魔时立齐八卦。<BR>"
        "· 四象麒麟：以下界之星为心，要八卦碎片与四神兽之锭，立艮、离、巽、坤。<BR>"
        "· 朱雀蝶舞：火之碎片居中，最容易，立离、艮两卦。<BR>"
        "§8杖端定耗损，杖柄定储量。§r",''',
        '''        "§l二 · 三对怎么选§r<BR>"
        "· 玄枢寰宇鸿蒙<BR>"
        "中心：龙蛋<BR>"
        "难度：最难<BR>"
        "§9§l注魔时立齐八卦。§r<BR>"
        "· 四象麒麟<BR>"
        "中心：下界之星<BR>"
        "难度：次难<BR>"
        "要八卦碎片与四神兽之锭<BR>"
        "§9§l注魔时立艮、离、巽、坤。§r<BR>"
        "· 朱雀蝶舞<BR>"
        "中心：火之碎片<BR>"
        "难度：最容易<BR>"
        "§9§l注魔时立离、艮两卦。§r<BR>"
        "§8杖端定耗损，杖柄定储量。§r",''',
    ),
    # ---- 乾卦条目：夹在正文里的那句 ----
    (
        "有些配方还要在基座下面立一块乾卦石。",
        "有些配方还要§9§l在基座下面立一块乾卦石§r。",
    ),
]


# ============================================================ 英文：整段替换

EN_BLOCKS = [
    (
        '''        "§l2. Which one§r<BR>· Xuanshu (silverwood): 2035 vis, 60% cost, hardest; wants a dragon egg and a nether star, all eight stones.<BR>"
        "· Sixiang (obsidian): 1949 vis, 80% cost, next; Gen, Li, Xun, Kun under the base.<BR>"
        "· Vermilion (grand wood): 918 vis, 90% cost, easiest; gold and blaze powder only, Li and Gen.<BR>"
        "§8Lower cost means the same spell spends less.§r",''',
        '''        "§l2. Which one§r<BR>"
        "· Xuanshu (silverwood)<BR>"
        "2035 vis, 60% cost<BR>"
        "Difficulty: hardest<BR>"
        "Wants a dragon egg and a nether star<BR>"
        "§9§lAll eight stones under the base.§r<BR>"
        "· Sixiang (obsidian)<BR>"
        "1949 vis, 80% cost<BR>"
        "Difficulty: next<BR>"
        "§9§lGen, Li, Xun, Kun under the base.§r<BR>"
        "· Vermilion (grand wood)<BR>"
        "918 vis, 90% cost<BR>"
        "Difficulty: easiest<BR>"
        "Gold and blaze powder only<BR>"
        "§9§lLi and Gen under the base.§r<BR>"
        "§8Lower cost means the same spell spends less.§r",''',
    ),
    (
        '''        "§l2. Which pair§r<BR>· Cosmic (gilded): dragon egg core, hardest; all eight stones.<BR>"
        "· Sixiang (qilin): nether star core; bagua shards and beast ingots; Gen, Li, Xun, Kun.<BR>"
        "· Vermilion (butterfly): fire shard core, easiest; Li and Gen.<BR>"
        "§8Caps set the cost, rods set the capacity.§r",''',
        '''        "§l2. Which pair§r<BR>"
        "· Cosmic (gilded)<BR>"
        "Core: dragon egg<BR>"
        "Difficulty: hardest<BR>"
        "§9§lAll eight stones at infusion.§r<BR>"
        "· Sixiang (qilin)<BR>"
        "Core: nether star<BR>"
        "Difficulty: next<BR>"
        "Bagua shards and beast ingots<BR>"
        "§9§lGen, Li, Xun, Kun at infusion.§r<BR>"
        "· Vermilion (butterfly)<BR>"
        "Core: fire shard<BR>"
        "Difficulty: easiest<BR>"
        "§9§lLi and Gen at infusion.§r<BR>"
        "§8Caps set the cost, rods set the capacity.§r",''',
    ),
    (
        "It is spent as an aspect in infusion, and some recipes want a Qian stone under the base.",
        "It is spent as an aspect in infusion, and §9§lsome recipes want a Qian stone under the base§r.",
    ),
]


# ============================================================ 正则：通用改写

# 中文：要素 X；不稳 Y；基座下面立 Z。  →  三行
RE_ZH_MERGED = re.compile(r'"要素 ([^；"]*?)；不稳 (\d+)；基座下面(立[^。"]*?)。"')
# 「· 卦石：…」可能独占一行，也可能被 <BR> 串在同一行里，两种都要接住
RE_ZH_STONE_LINE = re.compile(r"· 卦石：(基座下面[^\"<]*?)(?=<BR>|\")")

# 英文：12 of each trigram; instability 8; one of each trigram stone under the base.
RE_EN_MERGED = re.compile(
    r'"(\d+) of each trigram; instability (\d+); ([^"]*?)\.?"'
)
RE_EN_STONE_LINE = re.compile(r"· stones: ([^\"<]*?)(?=<BR>|\")")


def zh_merged(m):
    return '"要素：%s<BR>"\n        "不稳：%s<BR>"\n        "§9§l卦石：基座下面%s。§r"' % (
        m.group(1),
        m.group(2),
        m.group(3),
    )


def zh_stone_line(m):
    return "· §9§l卦石：%s§r" % m.group(1)


def en_merged(m):
    tail = m.group(3).strip()
    if "under the base" not in tail:
        tail = tail.rstrip(".") + " under the base"
    return '"Aspects: %s of each trigram<BR>"\n        "Instability: %s<BR>"\n        "§9§lStones: %s.§r"' % (
        m.group(1),
        m.group(2),
        tail,
    )


def en_stone_line(m):
    return "· §9§lstones: %s§r" % m.group(1)


def patch(path, blocks, subs):
    text = io.open(path, encoding="utf-8").read()
    orig = text
    report = []

    for old, new in blocks:
        n = text.count(old)
        report.append(("整段", old.split("<BR>")[0][:34], n))
        if n:
            text = text.replace(old, new)

    for pattern, repl, label in subs:
        matches = pattern.findall(text)
        report.append((label, "", len(matches)))
        text = pattern.sub(repl, text)

    for label, sample, n in report:
        flag = "OK " if n else "-- "
        print("  %s%s  %-38s %d" % (flag, label, sample, n))

    if not DRY and text != orig:
        io.open(path, "w", encoding="utf-8", newline="\n").write(text)
    return text != orig


print("=== 中文 ===")
patch(
    ZH_PATH,
    ZH_BLOCKS,
    [
        (RE_ZH_MERGED, zh_merged, "合并行拆三行"),
        (RE_ZH_STONE_LINE, zh_stone_line, "卦石行加蓝"),
    ],
)

print("=== 英文 ===")
patch(
    EN_PATH,
    EN_BLOCKS,
    [
        (RE_EN_MERGED, en_merged, "合并行拆三行"),
        (RE_EN_STONE_LINE, en_stone_line, "stones 行加蓝"),
    ],
)

print("dry-run，未写入" if DRY else "已写入")
