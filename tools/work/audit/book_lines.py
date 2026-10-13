# -*- coding: utf-8 -*-
"""按本体 ResearchFontLayout 的真实度量，统计每个研究 TEXT 页的**屏幕行数**（**可复跑**）。

度量口径（全部对齐本体 `ResearchBrowserScreen`）：

- 折行宽度 = 139（`renderLegacyResearchText` 的 `final int width`）
- 字宽 = `glyph_sizes.bin`，CJK 走「畸形半字节」分支 → 恒为 9
- 行高 = 9（`textY += 9`）
- 文本起点：`pageIndex == 0` 的页从 `contentTop + 15` 起（让出 `y−13` / `y+4` 两条书名装饰线），
  其余页从 `contentTop − 10` 起
- 纸面下沿 ≈ `contentTop + 195`
  ⇒ 容量 = `(195 − 15) / 9 = 20` 行（首页）/ `(195 + 10) / 9 ≈ 22.8 → 22` 行（其余页）

★★ 行数不是「折行段数」：`layout()` 会把 `wrapFormattedStringToWidth()` 的结果**再按 `\\n` 切一次**，
而折行本身插入的 `\\n` 也在其中 ⇒ **每一个 `<BR>` 都会多切出一整行空行**（书页实为双倍行距）。
所以必须用 `rows()` 的 `len("\\n".join(segs).split("\\n"))`，数折行段数会**低估约 1/3**。

字体表默认读同目录的 `glyph_sizes.bin`（从本体 jar 的
`assets/thaumcraft/font/glyph_sizes.bin` 取出）。

用法（在项目根目录）：
    python tools/work/audit/book_lines.py
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", ".."))
_FONT = os.path.join(HERE, "glyph_sizes.bin")          # 本体 jar 的 assets/thaumcraft/font/glyph_sizes.bin
assert os.path.exists(_FONT), "缺少 glyph_sizes.bin（从本体 jar 的 assets/thaumcraft/font/ 取出）"
GW = open(_FONT, "rb").read()
assert len(GW) == 65536

BOOK_W = 139
CAP_TITLE = 20        # pageIndex == 0（文本从 contentTop+15 起）
CAP_PLAIN = 22        # 其余页（文本从 contentTop-10 起）


def char_width(ch):
    o = ord(ch)
    if o > 0xFFFF:
        return 0
    if ch == '\u00A7':
        return -1
    if ch == ' ':
        return 4
    packed = GW[o]
    if packed == 0:
        return 0
    left = packed >> 4
    right = packed & 15
    if right > 7:            # 畸形半字节 → 整个字格（CJK 走这条：= 9）
        right = 15
        left = 0
    right += 1
    return (right - left) // 2 + 1


def size_string_to_width(text, width):
    n = len(text)
    measured = 0
    index = 0
    last_space = -1
    bold = False
    while index < n:
        v = text[index]
        if v == ' ':
            last_space = index
            measured += 4
            if bold:
                measured += 1
        elif v == '\u00A7':
            if index < n - 1:
                index += 1
                f = text[index]
                if f in 'lL':
                    bold = True
                elif f in 'rR' or f in '0123456789abcdefABCDEF':
                    bold = False
        else:
            measured += char_width(v)
            if bold:
                measured += 1
        if v == '\n':
            index += 1
            last_space = index
            break
        if measured > width:
            break
        index += 1
    return last_space if (index != n and last_space != -1 and last_space < index) else index


def normalize(t):
    t = t.replace("<BR>", "\n").replace("<BR/>", "\n")
    t = t.replace("<LINE>", "\n").replace("<LINE/>", "\n")
    return t


def wrap(text, width=BOOK_W):
    out = []
    for _ in range(600):
        s = size_string_to_width(text, width)
        if len(text) <= s:
            out.append(text)
            return out
        line = text[:s]
        sep = text[s]
        text = text[s + (1 if sep in (' ', '\n') else 0):]
        out.append(line)
        if not text:
            return out
    raise RuntimeError("runaway wrap")


def rows(text):
    """真正的屏幕行数：layout() 把折行结果按 \\n 切开，以 \\n 结尾的段落会多切出一个空行。"""
    segs = wrap(normalize(text))
    return len("\n".join(segs).split("\n"))


def cap_of(page_index):
    return CAP_TITLE if page_index == 0 else CAP_PLAIN


def main():
    base = os.path.join(ROOT, "src/main/resources")
    lang = json.load(open(os.path.join(base, "assets/skydeitymysticpivot/lang/zh_cn.json"), encoding="utf-8"))
    lang_en = json.load(open(os.path.join(base, "assets/skydeitymysticpivot/lang/en_us.json"), encoding="utf-8"))
    research = json.load(open(os.path.join(
        base, "data/skydeitymysticpivot/thaumcraft/research/bagua.json"), encoding="utf-8"))

    out = []
    for e in research["entries"]:
        key = e["key"].split(":")[-1]
        for i, p in enumerate(e.get("pages", [])):
            if p.get("type") not in ("TEXT", "TEXT_CONCEALED", "LORE"):
                continue
            k = p.get("value")
            zh = lang.get(k)
            en = lang_en.get(k)
            out.append((rows(zh) if zh else 0, rows(en) if en else 0, key, i, k))

    out.sort(reverse=True)
    over = 0
    print("%-4s %-4s %-24s %-36s %s" % ("zh", "en", "key(page)", "lang key", "cap"))
    for nz, ne, key, i, k in out:
        cap = cap_of(i)
        bad = ""
        if nz > cap or ne > cap:
            bad = "  <<< OVER"
            over += 1
        elif max(nz, ne) >= cap:
            bad = "  ~ full"
        print("%-4s %-4s %-24s %-36s %-3s%s" % (nz, ne, "%s(%d)" % (key, i), k, cap, bad))
    print("\ntext pages:", len(out), "| max zh:", out[0][0], "| max en:", max(r[1] for r in out), "| over:", over)


if __name__ == "__main__":
    main()
