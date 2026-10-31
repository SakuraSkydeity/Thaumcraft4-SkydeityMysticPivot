#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
离线校验 OBJ 模型能不能被 NeoForge 的 obj loader 正常加载（第一百七十八批新增）。

<h2>为什么需要这个</h2>
第一百七十七批的 altar_base / altar_globe 在游戏里**整个不显示**，而编译、资源自检、
死代码扫描全是绿的。原因是模型渲染链路的错误全在运行时才暴露：

  * `ObjMaterialLibrary#getMaterial` 找不到材质会 `throw NoSuchElementException`；
  * 就算材质对上了，没有 `vt` 的面也只会把整张贴图拉伸到一个面上；
  * 没有 `vn` 时法线靠绕序反推，绕序反了就被背面剔除掉。

所以这里逐条模拟 NeoForge 的解析规则做静态检查，把问题挡在打包之前。

<h2>检查项</h2>
  1. blockstates 引用的模型都存在；`neoforge:obj` 模型的 `model` 指向的 .obj 存在；
  2. .obj 的 `mtllib` 解析出的 .mtl 存在；
  3. 每个 `usemtl` 都能在 .mtl 里找到同名 `newmtl`（大小写敏感）；
  4. .mtl 里每个 `map_Kd` 指向的贴图存在；
  5. 每个面都有 `vt`（三个下标）与 `vn`，且绕序叉积与面法线同向。

用法：python tools/check_obj_models.py
"""
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets")

problems = []
checked = {"models": 0, "objs": 0, "faces": 0}


def where(ns, path):
    """资源 id -> 磁盘路径。"""
    return os.path.join(ASSETS, ns, path)


def split_id(rid):
    """'ns:a/b' -> ('ns', 'a/b')；无命名空间时按 minecraft 处理。"""
    if ":" in rid:
        ns, path = rid.split(":", 1)
        return ns, path
    return "minecraft", rid


def check_obj(obj_path, label):
    checked["objs"] += 1
    verts, normals = [], []
    face_idx = []          # [(usemtl, [(v,vt,vn), ...])]
    used_mats = []
    mtl_paths = []
    cur_mat = None
    obj_dir = os.path.dirname(obj_path)

    with open(obj_path, encoding="utf-8") as f:
        for lineno, line in enumerate(f, 1):
            parts = line.split()
            if not parts or parts[0].startswith("#"):
                continue
            tag = parts[0]
            if tag == "v":
                verts.append(tuple(float(x) for x in parts[1:4]))
            elif tag == "vn":
                normals.append(tuple(float(x) for x in parts[1:4]))
            elif tag == "mtllib":
                ref = parts[1]
                if ":" in ref:
                    ns, path = split_id(ref)
                    p = where(ns, path)
                else:
                    p = os.path.join(obj_dir, ref)
                mtl_paths.append(p)
                if not os.path.isfile(p):
                    problems.append("%s：mtllib %s 指向的 .mtl 不存在（%s）"
                                    % (label, ref, os.path.relpath(p, ROOT)))
            elif tag == "usemtl":
                cur_mat = parts[1]
                used_mats.append(cur_mat)
            elif tag == "f":
                verts_of_face = []
                for tok in parts[1:]:
                    seg = tok.split("/")
                    vi = int(seg[0])
                    has_vt = len(seg) > 1 and seg[1] != ""
                    has_vn = len(seg) > 2 and seg[2] != ""
                    if not has_vt or not has_vn:
                        problems.append("%s 第 %d 行：面顶点 %r 缺 vt 或 vn"
                                        % (label, lineno, tok))
                        continue
                    verts_of_face.append((vi, int(seg[1]), int(seg[2])))
                face_idx.append((cur_mat, verts_of_face))
                checked["faces"] += 1

    # 材质名对账
    defined = set()
    maps = []
    for p in mtl_paths:
        if not os.path.isfile(p):
            continue
        with open(p, encoding="utf-8") as f:
            for line in f:
                parts = line.split()
                if not parts:
                    continue
                if parts[0] == "newmtl":
                    defined.add(parts[1])
                elif parts[0] == "map_Kd":
                    maps.append(parts[-1])
    for m in sorted(set(used_mats)):
        if m not in defined:
            problems.append("%s：usemtl %r 在 MTL 里没有同名 newmtl（有：%s）"
                            % (label, m, ", ".join(sorted(defined)) or "（空）"))
    for ref in maps:
        ns, path = split_id(ref)
        p = where(ns, "textures/" + path + ".png")
        if not os.path.isfile(p):
            problems.append("%s：map_Kd %s 指向的贴图不存在" % (label, ref))

    # 绕序与法线是否同向
    for (mat, face) in face_idx:
        if len(face) < 3:
            continue
        (a, _, na), (b, _, _), (c, _, _) = face[0], face[1], face[2]
        try:
            va, vb, vc = verts[a - 1], verts[b - 1], verts[c - 1]
            n = normals[na - 1]
        except IndexError:
            problems.append("%s：顶点或法线下标越界（f 段）" % label)
            break
        ab = [vb[i] - va[i] for i in range(3)]
        ac = [vc[i] - va[i] for i in range(3)]
        cross = (ab[1] * ac[2] - ab[2] * ac[1],
                 ab[2] * ac[0] - ab[0] * ac[2],
                 ab[0] * ac[1] - ab[1] * ac[0])
        dot = sum(cross[i] * n[i] for i in range(3))
        if dot <= 1e-9:
            problems.append("%s：有面的绕序与法线 %s 反向（会被背面剔除）" % (label, n))
            break


def check_blockstates():
    for ns in sorted(os.listdir(ASSETS)):
        bs_dir = os.path.join(ASSETS, ns, "blockstates")
        if not os.path.isdir(bs_dir):
            continue
        for fn in sorted(os.listdir(bs_dir)):
            if not fn.endswith(".json"):
                continue
            path = os.path.join(bs_dir, fn)
            with open(path, encoding="utf-8") as f:
                data = json.load(f)
            refs = []
            if "variants" in data:
                for v in data["variants"].values():
                    if isinstance(v, list):
                        refs += [x["model"] for x in v]
                    else:
                        refs.append(v["model"])
            for part in data.get("multipart", []):
                refs.append(part["apply"]["model"])
            for rid in refs:
                mns, mpath = split_id(rid)
                # 模型 id 的 "block/xxx" 就是磁盘上的 models/block/xxx.json，前缀要保留
                mp = os.path.join(ASSETS, mns, "models", mpath + ".json")
                if not os.path.isfile(mp):
                    problems.append("%s：blockstate 引用的模型 %s 不存在" % (fn, rid))
                    continue
                checked["models"] += 1
                with open(mp, encoding="utf-8") as f:
                    model = json.load(f)
                if model.get("loader") == "neoforge:obj":
                    ons, opath = split_id(model["model"])
                    op = where(ons, opath)
                    if not os.path.isfile(op):
                        problems.append("%s：obj 模型文件 %s 不存在" % (fn, model["model"]))
                        continue
                    check_obj(op, fn)


if __name__ == "__main__":
    check_blockstates()
    print("blockstate 引用的模型 %d 个 | 其中 obj %d 个 | 共 %d 个面"
          % (checked["models"], checked["objs"], checked["faces"]))
    if problems:
        print("\n发现 %d 处问题：" % len(problems))
        for p in problems:
            print("  -", p)
        sys.exit(1)
    print("OBJ 渲染链路 ALL GREEN")
