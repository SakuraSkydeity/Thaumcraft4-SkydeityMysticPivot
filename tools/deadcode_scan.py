#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
死代码体检：把 `src/main/java` 下每个源文件扫一遍，报出

  1. **没人用的 import**（简单名在文件其余部分一次都没出现）；
  2. **没人用的私有成员**（`private` 字段 / 方法 / 常量，全项目范围内只出现定义那一处）；
  3. **可疑的静态常量**（`public static final` 但全项目没人引用）；
  4. **空壳覆写**（方法体里只有 `super.xxx(...)` 一行，等于没写）。

只报告、不改动 —— 改哪一处由人看过再定，避免把「给 mixin 反射用」之类的东西误删。
"""
import os
import re
import sys
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA_ROOT = os.path.join(ROOT, "src/main/java")

IMPORT_RE = re.compile(r"^\s*import\s+(?:static\s+)?([\w.]+)\s*;")
TYPE_RE = re.compile(r"\b(?:class|interface|enum|record|@interface)\s+(\w+)")
FIELD_RE = re.compile(r"^\s*(?:public|protected|private)\s+(?:static\s+)?(?:final\s+)?[\w<>\[\],.\s?]+?\s+(\w+)\s*(?:=|;)")
METHOD_RE = re.compile(r"^\s*(?:public|protected|private)\s+(?:static\s+)?(?:final\s+)?[\w<>\[\],.\s?]+?\s+(\w+)\s*\(")
WORD_RE = re.compile(r"[A-Za-z_]\w*")


def java_files():
    out = []
    for dirpath, _, filenames in os.walk(JAVA_ROOT):
        for name in filenames:
            if name.endswith(".java"):
                out.append(os.path.join(dirpath, name))
    return sorted(out)


def strip_comments(text):
    """去注释但**保持行数不变**（块注释按原换行数补齐），好让报出行号对得上原文。"""
    text = re.sub(r"/\*.*?\*/", lambda m: "\n" * m.group(0).count("\n"), text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def main():
    files = java_files()
    sources = {}
    for path in files:
        with open(path, encoding="utf-8") as handle:
            raw = handle.read()
        sources[path] = (raw, strip_comments(raw))

    # 全项目的标识符出现次数（去注释后按词计）—— 用于「公开成员有没有人引用」
    global_words = defaultdict(int)
    for raw, code in sources.values():
        for word in WORD_RE.findall(code):
            global_words[word] += 1

    problems = defaultdict(list)

    for path, (raw, code) in sources.items():
        short = os.path.relpath(path, JAVA_ROOT).replace("\\", "/")
        lines = code.split("\n")

        # **单文件**的标识符出现次数 —— 用于 import 与私有成员（它们只可能在本文件被用）
        local_words = defaultdict(int)
        for word in WORD_RE.findall(code):
            local_words[word] += 1

        # --- 1. import ---
        for line in lines:
            match = IMPORT_RE.match(line)
            if not match:
                continue
            full = match.group(1)
            simple = full.rsplit(".", 1)[-1]
            if simple == "*":
                continue
            if local_words[simple] <= 1:
                problems["没人用的 import"].append("%s: %s" % (short, full))

        # --- 2. 私有成员 ---
        for index, line in enumerate(lines):
            if "import " in line:
                continue
            is_private = re.match(r"^\s*private\s", line) is not None
            if not is_private:
                continue
            match = FIELD_RE.match(line) or METHOD_RE.match(line)
            if not match:
                continue
            name = match.group(1)
            if name in ("if", "for", "while", "return", "new"):
                continue
            if local_words[name] <= 1:
                problems["没人用的私有成员"].append(
                    "%s:%d  %s" % (short, index + 1, line.strip()[:100]))

        # --- 3. 空壳覆写：方法体里只有一行 super.xxx(...)
        for index, line in enumerate(lines):
            if not re.match(r"^\s*(?:public|protected)\s", line) or "(" not in line:
                continue
            name_match = METHOD_RE.match(line)
            if not name_match:
                continue
            depth = 0
            body_lines = []
            started = False
            for follow in lines[index:index + 60]:
                depth += follow.count("{") - follow.count("}")
                if "{" in follow:
                    started = True
                if started:
                    body_lines.append(follow.strip())
                if started and depth == 0:
                    break
            inner = [l for l in body_lines[1:-1] if l and not l.startswith("}")]
            if len(inner) == 1 and re.match(r"^(?:return\s+)?super\.\w+\(.*\);?$", inner[0]):
                problems["空壳覆写（只调 super）"].append(
                    "%s:%d  %s ⇒ %s" % (short, index + 1, name_match.group(1), inner[0][:70]))

    # --- 4. public static final 常量无人引用 ---
    for path, (raw, code) in sources.items():
        short = os.path.relpath(path, JAVA_ROOT).replace("\\", "/")
        for index, line in enumerate(code.split("\n")):
            if not re.match(r"^\s*public\s+static\s+final\s", line):
                continue
            match = FIELD_RE.match(line)
            if not match:
                continue
            name = match.group(1)
            if global_words[name] <= 1:
                problems["公开常量无人引用"].append(
                    "%s:%d  %s" % (short, index + 1, line.strip()[:90]))

    total = 0
    for kind in ("没人用的 import", "没人用的私有成员", "空壳覆写（只调 super）", "公开常量无人引用"):
        items = problems.get(kind, [])
        total += len(items)
        print("## %s：%d" % (kind, len(items)))
        for item in items:
            print("   ", item)
        print()
    print("合计 %d 处" % total)
    return 0


if __name__ == "__main__":
    sys.exit(main())
