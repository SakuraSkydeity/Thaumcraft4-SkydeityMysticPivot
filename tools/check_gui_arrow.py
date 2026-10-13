#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""进度箭「内芯不越界」回归检查（12.66 批新增，**可复跑**）。

背景：用户报「箭头（青色下面的部分）有点超出了」。根因是 `XuanshuGui.progressArrow`
里的内芯（槽底 / 青芯）自己另算了一套 (headLeft+1, headW−2) 的三角形，
逐行 `Math.round` 的取整和描边对不上 —— 越到三角下缘偏得越多，青芯从描边外面戳出来。
修法是让内芯与描边**共用同一组几何**、只靠 `inset` 内缩 1 像素。

这个脚本把 `XuanshuGui` 的画法在离线按 1:1 像素重放（复用 `preview_gui_arrow.py`，
颜色/几何常量直接解析 Java 源），然后断言：
    **每一格内芯像素（槽底 / 槽顶影 / 箭尖灰 / 青三段 / 灵光）都落在「描边掩膜」之内。**

用法（项目根目录）：python tools/check_gui_arrow.py [比例...]
退出码 0 = 全部通过；1 = 有越界像素（会打印坐标）。
"""
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import preview_gui_arrow as P  # noqa: E402

W, H = 26, 14                       # = 三屏的 BAR_W / BAR_H
SYM = {
    "ARROW_EDGE": "#", "ARROW_SHADOW": ".", "ARROW_CHANNEL": "=",
    "ARROW_CHANNEL_TOP": "-", "ARROW_HEAD": "h", "ARROW_FILL_LO": "L",
    "ARROW_FILL_MID": "M", "ARROW_FILL_HI": "H", "ARROW_GLINT": "*",
}
NAME = {P.C[k] & 0xFFFFFF: v for k, v in SYM.items()}
INNER = {P.C[k] & 0xFFFFFF for k in SYM if k != "ARROW_EDGE" and k != "ARROW_SHADOW"}
DEFAULT_RATIOS = (0.0, 0.05, 0.25, 0.5, 0.75, 0.9, 0.99, 1.0)


def outline_mask():
    """只画描边（不画投影）得到「箭形」掩膜。"""
    g = P.Canvas(W, H, (0, 0, 0, 0))
    P.outline(g, 0, 0, W, H, P.C["ARROW_EDGE"])
    return {(x, y) for y in range(H) for x in range(W) if g.px[x, y][3] > 0}


def main():
    ratios = [float(a) for a in sys.argv[1:]] or list(DEFAULT_RATIOS)
    mask = outline_mask()
    bad_total = 0
    for ratio in ratios:
        g = P.Canvas(W, H, (0, 0, 0, 0))
        P.progress_arrow(g, 0, 0, W, H, ratio)
        bad = []
        for y in range(H):
            for x in range(W):
                p = g.px[x, y]
                if p[3] == 0:
                    continue
                rgb = (p[0] << 16) | (p[1] << 8) | p[2]
                if rgb in INNER and (x, y) not in mask:
                    bad.append((x, y, NAME.get(rgb, "?")))
        bad_total += len(bad)
        flag = "OK" if not bad else "FAIL"
        print("[%s] ratio=%.2f  越界内芯像素: %s" % (flag, ratio, bad if bad else "无"))
    if bad_total:
        print("\n!! 共 %d 个越界像素：内芯会从描边外面戳出来" % bad_total)
        sys.exit(1)
    print("\nALL GREEN：%d 个比例下内芯全部落在描边之内" % len(ratios))


if __name__ == "__main__":
    main()
