# -*- coding: utf-8 -*-
"""Regenerate crystal_cluster.obj (batch 78): smoother, prettier.

Keeps the batch-77 conventions:
  - 10 hexagonal (six-sided) crystals on no rock base, flat palette UVs
  - one palette column per crystal, palette row follows height fraction
Improvements over batch 77:
  - 9 rings + apex per crystal (was 3), cosine-smoothed taper profile
  - gentle twist along the height, tiny radial jitter for an organic feel
  - smooth vertex normals on the sides (flat on tip / bottom)
Auto-orients every triangle so winding is always outward.

Usage: run from the project root with any Python 3.
"""
import math
import os

OUT = os.path.join('src', 'main', 'resources', 'assets', 'skydeitymysticpivot',
                   'models', 'crystal_cluster.obj')

SIDES = 6
TWIST = math.radians(12)          # total twist from foot to tip (subtle)

# taper profile control points: (height fraction t, radius fraction r)
# foot tucked -> full width -> gentle waist -> long smooth taper -> thin neck
CTRL = [(0.00, 0.68), (0.10, 1.00), (0.32, 0.93), (0.58, 0.66),
        (0.80, 0.34), (0.93, 0.13)]


def profile(t):
    """Cosine-interpolated radius fraction at height fraction t."""
    for (t0, r0), (t1, r1) in zip(CTRL, CTRL[1:]):
        if t0 <= t <= t1:
            u = (t - t0) / (t1 - t0)
            s = (1.0 - math.cos(u * math.pi)) / 2.0
            return r0 + (r1 - r0) * s
    return CTRL[-1][1]


RINGS = [i / 9.0 for i in range(9)]          # 0 .. 0.888..., apex at t = 1

# (cx, cz, height, base radius, seed) -- layout keeps the batch-77 silhouette
CRYSTALS = [
    (0.500, 0.500, 0.85, 0.105, 1),   # tall centre
    (0.680, 0.415, 0.70, 0.090, 2),
    (0.345, 0.585, 0.57, 0.080, 3),
    (0.645, 0.660, 0.49, 0.068, 4),
    (0.360, 0.370, 0.42, 0.062, 5),
    (0.530, 0.715, 0.36, 0.052, 6),
    (0.735, 0.715, 0.31, 0.048, 7),
    (0.300, 0.470, 0.26, 0.040, 8),
    (0.660, 0.515, 0.23, 0.038, 9),
    (0.548, 0.315, 0.20, 0.036, 10),
]

V, VT, VN, F = [], [], [], []
_v_idx, _vt_idx, _vn_idx = {}, {}, {}


def add_v(x, y, z):
    key = (round(x, 6), round(y, 6), round(z, 6))
    if key not in _v_idx:
        _v_idx[key] = len(V) + 1
        V.append(key)
    return _v_idx[key]


def add_vt(u, v):
    key = (round(u, 6), round(v, 6))
    if key not in _vt_idx:
        _vt_idx[key] = len(VT) + 1
        VT.append(key)
    return _vt_idx[key]


def add_vn(x, y, z):
    key = (round(x, 6), round(y, 6), round(z, 6))
    if key not in _vn_idx:
        _vn_idx[key] = len(VN) + 1
        VN.append(key)
    return _vn_idx[key]


def tri(a, b, c, ta, tb, tc, axis_pt):
    """Emit a flat-normal triangle (v/vt/vn x3), auto-oriented outward."""
    (ax, ay, az), (bx, by, bz), (cx, cy, cz) = V[a - 1], V[b - 1], V[c - 1]
    ux, uy, uz = bx - ax, by - ay, bz - az
    vx, vy, vz = cx - ax, cy - ay, cz - az
    nx, ny, nz = uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx
    ln = math.sqrt(nx * nx + ny * ny + nz * nz)
    if ln == 0:
        nx, ny, nz, ln = 0.0, 1.0, 0.0, 1.0
    mx, my, mz = (ax + bx + cx) / 3 - axis_pt[0], (ay + by + cy) / 3 - axis_pt[1], \
                 (az + bz + cz) / 3 - axis_pt[2]
    if nx * mx + ny * my + nz * mz < 0:
        a, b, c, ta, tb, tc = a, c, b, ta, tc, tb
        nx, ny, nz = -nx, -ny, -nz
    ni = add_vn(nx / ln, ny / ln, nz / ln)
    F.append((a, ta, ni, b, tb, ni, c, tc, ni))


def build_crystal(cx, cz, h, r0, seed):
    # palette edge-column centre (pixel 0.5 + 2k), same colour family as batch 77
    col = ((seed - 1) % 6) * 0.125 + 0.03125

    def vt_row(k):
        return 0.03125 + min(k, 9) * 0.0625

    rings = []
    for k, t in enumerate(RINGS):
        rad = r0 * profile(t)
        twist = TWIST * t
        ring = []
        for i in range(SIDES):
            j = 1.0 + 0.05 * math.sin(seed * 1.7 + i * 2.399 + t * 3.1)
            a = seed * 0.7 + i * (2.0 * math.pi / SIDES) + twist
            ring.append(add_v(cx + rad * j * math.cos(a), h * t,
                              cz + rad * j * math.sin(a)))
        rings.append(ring)

    apex = add_v(cx + 0.006 * math.sin(seed * 2.1), h,
                 cz + 0.006 * math.cos(seed * 1.3))
    bottom = add_v(cx, 0.0, cz)

    vt_rings = [[add_vt(col, vt_row(k)) for _ in range(SIDES)] for k in range(len(RINGS))]
    vt_apex = add_vt(col, vt_row(9))
    vt_bottom = add_vt(col, vt_row(0))

    # side quads -> two triangles each, then smooth normals per side vertex
    side_tris = []
    for k in range(len(RINGS) - 1):
        for i in range(SIDES):
            j = (i + 1) % SIDES
            a, b, c, d = rings[k][i], rings[k][j], rings[k + 1][j], rings[k + 1][i]
            ta, tb, tc, td = vt_rings[k][i], vt_rings[k][j], vt_rings[k + 1][j], \
                vt_rings[k + 1][i]
            axis = (cx, h * (RINGS[k] + RINGS[k + 1]) / 2, cz)
            side_tris.append(((a, b, c), (ta, tb, tc), axis))
            side_tris.append(((a, c, d), (ta, tc, td), axis))

    accum = {}
    for (a, b, c), _, _ in side_tris:
        (ax, ay, az), (bx, by, bz), (cx2, cy2, cz2) = V[a - 1], V[b - 1], V[c - 1]
        ux, uy, uz = bx - ax, by - ay, bz - az
        vx, vy, vz = cx2 - ax, cy2 - ay, cz2 - az
        nx, ny, nz = uy * vz - uz * vy, uz * vx - ux * vz, ux * vy - uy * vx
        ln = math.sqrt(nx * nx + ny * ny + nz * nz) or 1.0
        for vi in (a, b, c):
            sx, sy, sz = accum.get(vi, (0.0, 0.0, 0.0))
            accum[vi] = (sx + nx / ln, sy + ny / ln, sz + nz / ln)

    smooth = {}
    for vi, v in accum.items():
        smooth[vi] = add_vn(*_norm(v))

    for (a, b, c), (ta, tb, tc), _ in side_tris:
        F.append((a, ta, smooth[a], b, tb, smooth[b], c, tc, smooth[c]))

    # tip fan: flat normals
    last = len(RINGS) - 1
    for i in range(SIDES):
        j = (i + 1) % SIDES
        a, b = rings[last][i], rings[last][j]
        ta, tb = vt_rings[last][i], vt_rings[last][j]
        axis = (cx, h * RINGS[last], cz)
        tri(a, b, apex, ta, tb, vt_apex, axis)

    # bottom cap: flat down normals
    down = add_vn(0.0, -1.0, 0.0)
    for i in range(SIDES):
        j = (i + 1) % SIDES
        F.append((bottom, vt_bottom, down, rings[0][j], vt_rings[0][j], down,
                  rings[0][i], vt_rings[0][i], down))


def _norm(v):
    ln = math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
    if ln == 0:
        return (0.0, 1.0, 0.0)
    return (v[0] / ln, v[1] / ln, v[2] / ln)


def main():
    for (cx, cz, h, r0, seed) in CRYSTALS:
        build_crystal(cx, cz, h, r0, seed)

    lines = [
        '# SkydeityMysticPivot - xuanshu crystal cluster, 10 hexagonal crystals (batch 78)',
        '# smoother than batch 77: 9 cosine-tapered rings + apex per crystal,',
        '# gentle height twist, tiny radial jitter, smooth side normals,',
        '# flat palette UVs kept (one column per crystal, rows follow height)',
        'mtllib crystal_cluster.mtl',
        'usemtl crystal',
        'g crystal',
    ]
    for (x, y, z) in V:
        lines.append('v %.6f %.6f %.6f' % (x, y, z))
    for (u, v) in VT:
        lines.append('vt %.6f %.6f' % (u, v))
    for (x, y, z) in VN:
        lines.append('vn %.6f %.6f %.6f' % (x, y, z))
    for f in F:
        if len(f) == 9:   # v/vt/vn x3
            lines.append('f %d/%d/%d %d/%d/%d %d/%d/%d' % f)
        else:             # (v, vt, vn) x3 packed flat from tri()
            lines.append('f %d/%d/%d %d/%d/%d %d/%d/%d' % f)

    with open(OUT, 'w', encoding='ascii', newline='\n') as fh:
        fh.write('\n'.join(lines) + '\n')

    print('wrote %s: %d verts, %d uvs, %d normals, %d faces'
          % (OUT, len(V), len(VT), len(VN), len(F)))


if __name__ == '__main__':
    main()
