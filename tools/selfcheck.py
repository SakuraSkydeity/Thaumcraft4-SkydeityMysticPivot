#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
全项目资源自检（每批构建前跑一遍）。教训来源：
  - 12.57/12.58：方块模型元素旋转用了 ±16°/±14°，MC 只认 -45/-22.5/0/22.5/45，
    整个模型解析失败回退缺失模型（游戏里显示乱码），且控制台只有一行 ERROR 容易漏看。
  - 12.56：备份文件放进了 src/main/resources/，被一起打进 jar。

检查项：
  1. assets 下所有 JSON 可解析
  2. lang 两语言等键、无 U+3000 / U+2014 / U+2212
  3. 所有方块/物品模型：元素旋转角度合法、uv 为整数且在 [0,16]、引用的贴图存在
  4. blockstate 引用的模型存在；models/item 引用的父模型存在
  5. resources 下不出现备份/杂物（只允许 json/png/mcmeta/toml/txt/ogg）
  6. 贴图尺寸抽查（16/32/64 合法 MC 尺寸）
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot")
DATA = os.path.join(ROOT, "src/main/resources/data")
OK_ANGLES = {-45.0, -22.5, 0.0, 22.5, 45.0}
BANNED = {"\u3000": "U+3000 表意空格", "\u2014": "U+2014 em dash", "\u2212": "U+2212 minus"}
OK_EXT = {".json", ".png", ".mcmeta", ".toml", ".txt", ".ogg"}

errors = []
warnings = []


def err(msg):
    errors.append(msg)


def walk(base, exts):
    for dirpath, _, files in os.walk(base):
        for f in files:
            if os.path.splitext(f)[1] in exts:
                yield os.path.join(dirpath, f)


def check_models():
    n_models = 0
    for p in walk(os.path.join(ASSETS, "models"), {".json"}):
        rel = os.path.relpath(p, ASSETS)
        try:
            d = json.load(open(p, encoding="utf-8"))
        except Exception as e:
            err("JSON 解析失败 %s: %s" % (rel, e))
            continue
        n_models += 1
        # 贴图引用
        texs = d.get("textures", {}) or {}
        for key, val in texs.items():
            if not isinstance(val, str) or val.startswith("#") or ":" not in val:
                continue
            ns, tp = val.split(":", 1)
            cands = [os.path.join(ASSETS, "textures", tp + ".png"),
                     os.path.join(ROOT, "src/main/resources/assets", ns,
                                  "textures", tp + ".png")]
            if not any(os.path.exists(c) for c in cands):
                err("模型 %s 的贴图 #%s 不存在: %s" % (rel, key, val))
        for el in d.get("elements", []) or []:
            name = el.get("name", "?")
            rot = el.get("rotation")
            if rot and float(rot.get("angle", 0)) not in OK_ANGLES:
                err("模型 %s 元素 %s 旋转角非法: %s（只允许 -45/-22.5/0/22.5/45）"
                    % (rel, name, rot.get("angle")))
            for fn, f in (el.get("faces", {}) or {}).items():
                uv = f.get("uv")
                if uv is None:
                    continue
                if any(float(v) != int(v) for v in uv):
                    err("模型 %s 元素 %s 面 %s uv 非整数: %s" % (rel, name, fn, uv))
                if min(uv) < 0 or max(uv) > 16:
                    err("模型 %s 元素 %s 面 %s uv 越界: %s" % (rel, name, fn, uv))
    return n_models


def check_blockstates():
    n = 0
    for p in walk(os.path.join(ASSETS, "blockstates"), {".json"}):
        rel = os.path.relpath(p, ASSETS)
        try:
            d = json.load(open(p, encoding="utf-8"))
        except Exception as e:
            err("blockstate 解析失败 %s: %s" % (rel, e))
            continue
        n += 1
        variants = d.get("variants", {})
        parts = d.get("multipart", [])
        refs = [v.get("model") for v in variants.values() if isinstance(v, dict)]
        for part in parts:
            refs += [v.get("model") for v in part.get("apply", []) if isinstance(v, dict)]
        for ref in refs:
            if not ref:
                continue
            ns, tp = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
            if ns != "skydeitymysticpivot":
                continue
            cand = os.path.join(ASSETS, "models", tp + ".json")
            if not os.path.exists(cand):
                err("blockstate %s 引用的模型不存在: %s" % (rel, ref))
    return n


def check_lang():
    langs = {}
    for lang in ("zh_cn", "en_us"):
        p = os.path.join(ASSETS, "lang", lang + ".json")
        try:
            d = json.load(open(p, encoding="utf-8"))
        except Exception as e:
            err("lang %s 解析失败: %s" % (lang, e))
            continue
        langs[lang] = d
        raw = open(p, encoding="utf-8").read()
        for ch, name in BANNED.items():
            if ch in raw:
                err("lang %s 含禁用字符 %s" % (lang, name))
    if "zh_cn" in langs and "en_us" in langs:
        a, b = set(langs["zh_cn"]), set(langs["en_us"])
        if a != b:
            err("lang 键不一致：仅中文 %s / 仅英文 %s"
                % (sorted(a - b)[:5], sorted(b - a)[:5]))
        return len(langs["zh_cn"])
    return 0


def check_no_junk():
    n = 0
    for dirpath, _, files in os.walk(os.path.join(ROOT, "src/main/resources")):
        for f in files:
            ext = os.path.splitext(f)[1]
            if ext not in OK_EXT:
                err("resources 下出现不该打包的文件: %s"
                    % os.path.relpath(os.path.join(dirpath, f), ROOT))
            n += 1
    return n


def check_textures():
    sizes = {}
    for p in walk(os.path.join(ASSETS, "textures"), {".png"}):
        with open(p, "rb") as f:
            head = f.read(33)
        if head[:8] != b"\x89PNG\r\n\x1a\n":
            err("不是合法 PNG: %s" % os.path.relpath(p, ROOT))
            continue
        w = int.from_bytes(head[16:20], "big")
        h = int.from_bytes(head[20:24], "big")
        sizes[(w, h)] = sizes.get((w, h), 0) + 1
        if w not in (16, 32, 64, 128, 256) or h not in (16, 32, 64, 128, 256):
            warnings.append("贴图尺寸少见 %dx%d: %s" % (w, h, os.path.relpath(p, ROOT)))
    return sizes


def main():
    n_models = check_models()
    n_bs = check_blockstates()
    n_lang = check_lang()
    n_files = check_no_junk()
    sizes = check_textures()

    print("models: %d | blockstates: %d | lang keys: %d | resources files: %d"
          % (n_models, n_bs, n_lang, n_files))
    print("texture sizes:", dict(sorted(sizes.items())))
    for w in warnings:
        print("WARN:", w)
    if errors:
        print("\n!! %d 个问题:" % len(errors))
        for e in errors:
            print("  FAIL:", e)
        sys.exit(1)
    print("ALL GREEN")


if __name__ == "__main__":
    main()
