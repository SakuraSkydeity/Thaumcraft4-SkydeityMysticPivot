#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""对账：xuanshu_node_modifier 的 JSON 模型盒子 vs Java VoxelShape 盒子。

从 ModBlocks.java 里抓 XuanshuNodeModifierBlock.SHAPE 的 Block.box(...) 坐标，
与 models/block/xuanshu_node_modifier.json 的 elements 逐一比较（集合级，顺序无关）。

★ 第一百六十批起盒子数是 13（底板 + 四角柱 + 顶框四梁 + 井沿四块），不再写死 9。
"""
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "src/main/java/skydeity/mysticpivot/ModBlocks.java")
MODEL = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/models/block/xuanshu_node_modifier.json")


def java_boxes():
    src = open(JAVA, encoding="utf-8").read()
    # 定位 XuanshuNodeModifierBlock 的 SHAPE（第一个 Shapes.or 到第一个 "));"）
    i = src.index("class XuanshuNodeModifierBlock")
    seg = src[i:]
    j = seg.index("Shapes.or(")
    k = seg.index("));", j)
    # k 指向 "));" 的第一个 ")"——正是最后一个 box 的右括号，要把它包含进来
    seg = seg[j:k + 1]
    boxes = []
    for m in re.finditer(r"Block\.box\(([^)]+)\)", seg):
        nums = [float(v) for v in m.group(1).split(",")]
        assert len(nums) == 6, nums
        boxes.append(tuple(nums))
    return boxes


def json_boxes():
    m = json.load(open(MODEL, encoding="utf-8"))
    return [tuple(e["from"] + e["to"]) for e in m["elements"]]


def norm(b):
    return tuple(round(v, 4) for v in b)


def main():
    jb = sorted(map(norm, java_boxes()))
    js = sorted(map(norm, json_boxes()))
    ok = True
    if len(jb) != len(js):
        print("FAIL 盒子数量 java=%d json=%d" % (len(jb), len(js)))
        ok = False
    for b in jb:
        if b not in js:
            print("FAIL java 有 json 无:", b)
            ok = False
    for b in js:
        if b not in jb:
            print("FAIL json 有 java 无:", b)
            ok = False
    if ok:
        print("OK %d 个盒子逐块相等：" % len(jb))
        for b in jb:
            print("   ", b)
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
