# -*- coding: utf-8 -*-
"""按 OBJ 的 UV 画两张展开图（贴图打底 + 白底线框）。
用法: python uv_layout.py -- <out_dir> <basename> <texture.png> [size]
"""
import os, sys
from PIL import Image, ImageDraw

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else sys.argv[1:]
OUTDIR, BASE, TEX = argv[0], argv[1], argv[2]
S = int(argv[3]) if len(argv) > 3 else 2048
OBJ = os.path.join(OUTDIR, BASE + '.obj')

verts, uvs, faces = [], [], []
for line in open(OBJ, 'r', encoding='utf-8', errors='replace'):
    if line.startswith('v '):
        verts.append(tuple(float(x) for x in line.split()[1:4]))
    elif line.startswith('vt '):
        uvs.append(tuple(float(x) for x in line.split()[1:3]))
    elif line.startswith('f '):
        idx = []
        for tok in line.split()[1:]:
            p = tok.split('/')
            vi = int(p[0]) - 1
            ti = int(p[1]) - 1 if len(p) > 1 and p[1] else vi
            idx.append((vi, ti))
        for k in range(1, len(idx) - 1):
            faces.append((idx[0], idx[k], idx[k + 1]))
print('verts', len(verts), 'uvs', len(uvs), 'tris', len(faces))

# 边(顶点对) -> 各面用到的 UV 对；UV 不一致或只有单面 => 岛缝
edge_uv, edge_face = {}, {}
for f in faces:
    n = len(f)
    for i in range(n):
        a, b = f[i], f[(i + 1) % n]
        key = (min(a[0], b[0]), max(a[0], b[0]))
        pair = (uvs[a[1]], uvs[b[1]]) if a[0] < b[0] else (uvs[b[1]], uvs[a[1]])
        rp = ((round(pair[0][0], 5), round(pair[0][1], 5)),
              (round(pair[1][0], 5), round(pair[1][1], 5)))
        edge_uv.setdefault(key, set()).add(rp)
        edge_face[key] = edge_face.get(key, 0) + 1

seams = [k for k, v in edge_uv.items() if len(v) > 1 or edge_face[k] == 1]
print('edges', len(edge_uv), 'seam/boundary', len(seams))


def px(u, v):
    return (u * S, (1.0 - v) * S)          # OBJ 的 v=0 在图片底部


# 图1：贴图打底 + 青色岛轮廓
tex = Image.open(TEX).convert('RGB').resize((S, S), Image.LANCZOS)
dim = Image.blend(tex, Image.new('RGB', (S, S), (255, 255, 255)), 0.35)
d = ImageDraw.Draw(dim)
for key in seams:                           # 深色宽描边（保证深底可见）
    for (u0, v0), (u1, v1) in edge_uv[key]:
        d.line([px(u0, v0), px(u1, v1)], fill=(0, 40, 90), width=5, joint='curve')
d = ImageDraw.Draw(dim)
for key in seams:                           # 亮色细线
    for (u0, v0), (u1, v1) in edge_uv[key]:
        d.line([px(u0, v0), px(u1, v1)], fill=(0, 230, 255), width=2, joint='curve')
dim.save(os.path.join(OUTDIR, 'UV展开图.png'))

# 图2：白底黑线
w = Image.new('RGB', (S, S), (255, 255, 255))
d = ImageDraw.Draw(w)
for x in range(0, S + 1, S // 8):
    d.line([(x, 0), (x, S)], fill=(225, 225, 225), width=1)
    d.line([(0, x), (S, x)], fill=(225, 225, 225), width=1)
for key in seams:
    for (u0, v0), (u1, v1) in edge_uv[key]:
        d.line([px(u0, v0), px(u1, v1)], fill=(20, 20, 20), width=3, joint='curve')
d.rectangle([0, 0, S - 1, S - 1], outline=(120, 120, 120), width=2)
w.save(os.path.join(OUTDIR, 'UV线框_白底.png'))
print('UV images done ->', OUTDIR)
