#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
形制 → 分层字符画（两个多方块共用，第二百零四批）。

<h2>为什么不再写四元组</h2>

第 192 / 198 批把 schem 编译成 {@code int[] CELLS}（平铺 {@code dx, dy, dz, 状态序号}）。
一个格子至少 8 个字符，1099 格就是一万多字节，而且**看不出形制**。

这里换成「逐层字符画」：一层一段，段首一行写层号与左上角坐标，其后每行沿 +Z、
每个字符沿 +X，一个字符 = 一个格子。空气写 {@code .}，其余照调色板查。
同样 1099 格只占一千多个非空字符，剩下的都是整齐的占位点。

<h2>格式</h2>

<pre>
== 0,-11,6          ← y = 0，该层左上角 (x, z)
....GGGGGG....      ← z = 6
...GPPPPPPG...      ← z = 7
</pre>

第一个 {@code ==} 行之前的空行会被忽略；{@code ==} 行与行之间的空行也跳过。
字符集见 {@link #CHARS}，与 {@code STATES} 同序。

用法（只给两个生成脚本调）：
    from blueprint_map import encode
    states, char_of, text = encode(cells)
"""

CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

AIR = "."

#: 等于方块默认值的属性，写出来纯属占地方（{@code state()} 从 defaultBlockState 起，删掉等价）。
DEFAULTS = {
    "waterlogged": "false",
    "half": "bottom",
    "shape": "straight",
    "type": "bottom",
    "axis": "y",
    "up": "false",
    "down": "false",
    "north": "false",
    "south": "false",
    "east": "false",
    "west": "false",
}


def simplify(spec):
    """去掉取默认值的属性：{@code quartz_stairs[facing=east,half=bottom,shape=straight]} → 只留 facing。"""
    if "[" not in spec:
        return spec
    head, body = spec.split("[", 1)
    keep = []
    for pair in body.rstrip("]").split(","):
        key, _, value = pair.partition("=")
        if DEFAULTS.get(key.strip()) != value.strip():
            keep.append(pair.strip())
    return "%s[%s]" % (head, ",".join(keep)) if keep else head



def encode(cells):
    """
    格子表 → 分层字符画。

    @param cells [(x, y, z, 方块状态串)]，顺序随意
    @return (states, char_of, text)：状态表（下标 = {@link #CHARS} 下标）、
            状态 → 字符、字符画正文（不含 {@code \"\"\"}）
    """
    ordered = sorted(cells, key=lambda c: (c[1], c[2], c[0]))

    states = []
    index = {}
    for cell in ordered:
        name = simplify(cell[3])
        if name not in index:
            if len(states) >= len(CHARS):
                raise ValueError("状态 %d 种，单字符放不下" % (len(states) + 1))
            index[name] = len(states)
            states.append(name)
    char_of = {name: CHARS[i] for name, i in index.items()}

    lines = []
    for y in sorted({c[1] for c in ordered}):
        layer = [c for c in ordered if c[1] == y]
        x0 = min(c[0] for c in layer)
        x1 = max(c[0] for c in layer)
        z0 = min(c[2] for c in layer)
        z1 = max(c[2] for c in layer)
        grid = {(c[0], c[2]): char_of[simplify(c[3])] for c in layer}
        lines.append("== %d,%d,%d" % (y, x0, z0))
        for z in range(z0, z1 + 1):
            lines.append("".join(grid.get((x, z), AIR) for x in range(x0, x1 + 1)))
    return states, char_of, "\n".join(lines)


def java_states(states, char_of):
    """状态表 → Java 数组字面量（每行带注释，标出它在字符画里的字符）。"""
    return "\n".join('            "%s",%s' % (spec, "  // " + char_of[spec])
                     for spec in states)


def java_map(text, indent="            "):
    """字符画正文 → Java text block 的内容（每行加缩进，空行留空）。"""
    return "\n".join(indent + line if line else "" for line in text.split("\n"))
