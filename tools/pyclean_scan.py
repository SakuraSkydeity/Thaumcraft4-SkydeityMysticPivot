#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
工具脚本体检：扫 `tools/*.py`（不含 `tools/work/` 里的渲染小工具），报出

  1. **没人用的 import**；
  2. **定义了却从未被调用的模块级函数**（只在本文件里找引用）；
  3. **赋值了却从未被引用的模块级常量**。

只报告，不自动改 —— 生成器脚本里有些常量是「给文档/断言用的锚」，得人看过再定。
"""
import ast
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TOOLS = os.path.join(ROOT, "tools")


def targets():
    out = []
    for name in sorted(os.listdir(TOOLS)):
        if name.endswith(".py"):
            out.append(os.path.join(TOOLS, name))
    return out


def names_used(tree):
    used = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Name):
            used.add(node.id)
        elif isinstance(node, ast.Attribute):
            pass
        elif isinstance(node, ast.Import):
            for alias in node.names:
                used.add((alias.asname or alias.name).split(".")[0])
        elif isinstance(node, ast.ImportFrom):
            for alias in node.names:
                used.add(alias.asname or alias.name)
        elif isinstance(node, ast.arg):
            used.add(node.arg)
        elif isinstance(node, ast.Global):
            used.update(node.names)
    return used


def main():
    total = 0
    for path in targets():
        with open(path, encoding="utf-8") as handle:
            src = handle.read()
        tree = ast.parse(src)
        used = names_used(tree)
        short = os.path.relpath(path, ROOT)

        # --- import ---
        unused_imports = []
        for node in ast.walk(tree):
            if isinstance(node, ast.Import):
                for alias in node.names:
                    name = (alias.asname or alias.name).split(".")[0]
                    if name not in used:
                        unused_imports.append("import %s" % alias.name)

        # --- 模块级函数 ---
        unused_funcs = []
        for node in tree.body:
            if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
                if node.name not in used and node.name != "main":
                    unused_funcs.append("%s()  (line %d)" % (node.name, node.lineno))

        # --- 模块级常量 ---
        unused_consts = []
        for node in tree.body:
            if isinstance(node, ast.Assign):
                for target in node.targets:
                    if isinstance(target, ast.Name) and target.id.isupper() and target.id not in used:
                        unused_consts.append("%s  (line %d)" % (target.id, node.lineno))
        # 模块级函数内部的常量（生成器里常用）也算一遍
        for node in ast.walk(tree):
            if isinstance(node, ast.Assign):
                for target in node.targets:
                    if isinstance(target, ast.Name) and target.id.isupper():
                        if target.id not in used and not any(target.id in u for u in unused_consts):
                            unused_consts.append("%s  (line %d)" % (target.id, node.lineno))

        if unused_imports or unused_funcs or unused_consts:
            print("##", short)
            for item in unused_imports:
                print("   未用的 import :", item)
            for item in unused_funcs:
                print("   未调用的函数 :", item)
            for item in unused_consts:
                print("   未引用的常量 :", item)
            print()
            total += len(unused_imports) + len(unused_funcs) + len(unused_consts)

    print("合计 %d 处" % total)
    return 0


if __name__ == "__main__":
    sys.exit(main())
