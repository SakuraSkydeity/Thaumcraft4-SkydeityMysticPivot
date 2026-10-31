# -*- coding: utf-8 -*-
"""第二百三十批：研究树微调 + 指标复核。

1. 三台工作方块（炎炉/坩埚/炼金炉）挪到玄枢碎片正上方（row -1/0 → -3）。
2. 乾元罡气 FOCUS_GUARDIAN 往下一格 (4,1)→(4,2)。
3. 玄枢要素 BAGUA 往上一格 (-5,2)→(-5,1)。
其余不动。改完跑全套指标：线段交叉 / 贴线 / 最近节点 / 最长边。
"""
import json, math, sys

PATH = "src/main/resources/data/skydeitymysticpivot/thaumcraft/research/bagua.json"

MOVES = {
    "skydeitymysticpivot:MACHINE_FURNACE":  (4, -3),
    "skydeitymysticpivot:MACHINE_CRUCIBLE": (5, -3),
    "skydeitymysticpivot:MACHINE_ALCHEMY":  (6, -3),
    "skydeitymysticpivot:FOCUS_GUARDIAN":   (4, 2),
    "skydeitymysticpivot:BAGUA":            (-4, 1),
}

def load():
    with open(PATH, "r", encoding="utf-8") as f:
        return json.load(f)

def save(data):
    with open(PATH, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")

def pos_map(data):
    return {e["key"].split(":")[1]: (e["display_column"], e["display_row"]) for e in data["entries"]}

def apply_moves(data):
    hit = set()
    for e in data["entries"]:
        short = e["key"].split(":")[1]
        if e["key"] in MOVES:
            c, r = MOVES[e["key"]]
            e["display_column"], e["display_row"] = c, r
            hit.add(e["key"])
    miss = set(MOVES) - hit
    assert not miss, "没找到的节点: %s" % miss

# ---- 指标 ----
def ccw(a, b, c):
    return (c[1]-a[1]) * (b[0]-a[0]) > (b[1]-a[1]) * (c[0]-a[0])

def seg_cross(p1, p2, p3, p4):
    return (ccw(p1,p3,p4) != ccw(p2,p3,p4)) and (ccw(p1,p2,p3) != ccw(p1,p2,p4))

def seg_dist(p, a, b):
    ax, ay = a; bx, by = b; px, py = p
    dx, dy = bx-ax, by-ay
    L2 = dx*dx + dy*dy
    if L2 == 0:
        return math.hypot(px-ax, py-ay)
    t = max(0.0, min(1.0, ((px-ax)*dx + (py-ay)*dy) / L2))
    return math.hypot(px-(ax+t*dx), py-(ay+t*dy))

def check(data, tag):
    pm = pos_map(data)
    edges = []
    for e in data["entries"]:
        short = e["key"].split(":")[1]
        for par in e.get("parents", []):
            pshort = par.split(":")[1]
            edges.append((pshort, short))
    problems = []
    # 交叉（共享端点的边对不算）
    for i in range(len(edges)):
        for j in range(i+1, len(edges)):
            a1, a2 = edges[i]; b1, b2 = edges[j]
            if len({a1, a2, b1, b2}) < 4:
                continue
            if seg_cross(pm[a1], pm[a2], pm[b1], pm[b2]):
                problems.append("交叉: %s-%s × %s-%s" % (a1, a2, b1, b2))
    # 贴线：点到別人的线段 < 0.6
    for e in data["entries"]:
        s = e["key"].split(":")[1]
        for (a, b) in edges:
            if s in (a, b):
                continue
            d = seg_dist(pm[s], pm[a], pm[b])
            if d < 0.6:
                problems.append("贴线: 节点 %s 距边 %s-%s 仅 %.2f" % (s, a, b, d))
    # 最近节点对
    names = list(pm)
    best = (1e9, "", "")
    for i in range(len(names)):
        for j in range(i+1, len(names)):
            a, b = names[i], names[j]
            d = math.dist(pm[a], pm[b])
            if d < best[0]:
                best = (d, a, b)
    # 最长边
    longest = max(((math.dist(pm[a], pm[b]), a, b) for a, b in edges), key=lambda x: x[0])
    print("[%s] 边数 %d | 最近节点 %s-%s = %.2f | 最长边 %s-%s = %.2f" % (
        tag, len(edges), best[1], best[2], best[0], longest[1], longest[2], longest[0]))
    if problems:
        print("  !! 问题 %d 条:" % len(problems))
        for p in problems:
            print("   -", p)
    else:
        print("  交叉/贴线 全绿")
    return problems

def main():
    data = load()
    before = pos_map(data)
    print("== 改动前 ==")
    for k in MOVES:
        s = k.split(":")[1]
        print("  %-22s %s" % (s, before[s]))
    apply_moves(data)
    print("== 改动后 ==")
    pm = pos_map(data)
    for k in MOVES:
        s = k.split(":")[1]
        print("  %-22s %s" % (s, pm[s]))
    problems = check(data, "after")
    if "--write" in sys.argv:
        save(data)
        print("已写回 " + PATH)
        check(load(), "reloaded")
    else:
        print("(预览模式，加 --write 才落盘)")

if __name__ == "__main__":
    main()
