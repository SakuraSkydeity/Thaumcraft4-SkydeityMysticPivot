# -*- coding: utf-8 -*-
"""第 80 批临时脚本：OBJ 压缩前后保形校验。

对每一条 `f` 行，取新旧两版各自引用的顶点坐标 / UV / 法线，逐值求最大绝对差；
并校验：面数一致、面内顶点数一致、三角形有向体积（绕序）一致。
通过标准：误差 ≤ 各自精度半档（v: 0.005, vt: 0.00005, vn: 0.0005）。
"""
import sys


def parse(path):
    v, vt, vn, faces = [], [], [], []
    for ln in open(path, encoding="utf-8"):
        s = ln.split()
        if not s:
            continue
        if s[0] == "v":
            v.append(tuple(float(x) for x in s[1:4]))
        elif s[0] == "vt":
            vt.append(tuple(float(x) for x in s[1:3]))
        elif s[0] == "vn":
            vn.append(tuple(float(x) for x in s[1:4]))
        elif s[0] == "f":
            tri = []
            for part in s[1:]:
                idx = [int(i) - 1 if i else None for i in part.split("/")]
                tri.append(idx)
            faces.append(tri)
    return v, vt, vn, faces


def maxdiff(a, b):
    return max(abs(x - y) for x, y in zip(a, b))


def signed_volume(vs, faces):
    total = 0.0
    for f in faces:
        if len(f) != 3:
            continue
        a, b, c = (vs[f[k][0]] for k in range(3))
        total += (a[0] * (b[1] * c[2] - b[2] * c[1])
                  - a[1] * (b[0] * c[2] - b[2] * c[0])
                  + a[2] * (b[0] * c[1] - b[1] * c[0])) / 6.0
    return total


def main(old_path, new_path):
    ov, ovt, ovn, of = parse(old_path)
    nv, nvt, nvn, nf = parse(new_path)
    assert len(of) == len(nf), "面数不一致 %d vs %d" % (len(of), len(nf))

    dv = dvt = dvn = 0.0
    pair_bad = 0
    for fo, fn in zip(of, nf):
        assert len(fo) == len(fn), "面内顶点数不一致"
        for io, i_n in zip(fo, fn):
            dev = maxdiff(ov[io[0]], nv[i_n[0]])
            dv = max(dv, dev)
            if dev > 0.006:      # 顶点配对位移（应 ≤ 精度档 0.005）
                pair_bad += 1
            if io[1] is not None:
                dvt = max(dvt, maxdiff(ovt[io[1]], nvt[i_n[1]]))
            if io[2] is not None:
                dvn = max(dvn, maxdiff(ovn[io[2]], nvn[i_n[2]]))

    vo = signed_volume(ov, of)
    vn_ = signed_volume(nv, nf)
    dvol = abs(vn_ - vo) / abs(vo) * 100.0

    print(old_path.rsplit("/", 1)[-1].rsplit("\\", 1)[-1], "->",
          new_path.rsplit("/", 1)[-1].rsplit("\\", 1)[-1])
    print("  faces: %d -> %d" % (len(of), len(nf)))
    print("  max |Δv | = %.6f   (限 0.005)" % dv)
    print("  max |Δvt| = %.6f  (限 0.00005)" % dvt)
    print("  max |Δvn| = %.6f  (限 0.0005)" % dvn)
    print("  顶点配对异常面数 = %d" % pair_bad)
    print("  有向体积 %.6f -> %.6f   (Δ = %.4f%%)" % (vo, vn_, dvol))
    ok = (dv <= 0.005 + 1e-9 and dvt <= 0.00005 + 1e-9 and dvn <= 0.0005 + 1e-9
          and pair_bad == 0 and dvol < 0.5)
    print("  结果:", "PASS" if ok else "FAIL")
    return ok


if __name__ == "__main__":
    a = main(sys.argv[1], sys.argv[2])
    b = main(sys.argv[3], sys.argv[4])
    sys.exit(0 if (a and b) else 1)
