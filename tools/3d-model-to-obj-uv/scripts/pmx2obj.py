# -*- coding: utf-8 -*-
"""PMX (MikuMikuDance) -> OBJ + MTL。纯 Python，不依赖 Blender / 任何插件。
用法: python pmx2obj.py -- <in.pmx> <out_dir> <basename>

PMX 规范要点（踩过的坑都在注释里）：
  * 头部 9 字节里**有 5 个不同的索引长度**（顶点/贴图/材质/骨骼/变形/刚体）。
    权重里的骨骼索引必须用「骨骼索引长度」，误用「顶点索引长度」会整体错位。
  * 顶点结构随权重类型变长（BDEF1/2/4、SDEF、QDEF），只能顺序解析，不能定长跳。
  * 追加 UV 数量 = 头部那个字节，每个占 4 个 float。
"""
import struct, os, sys

argv = sys.argv[sys.argv.index('--') + 1:] if '--' in sys.argv else sys.argv[1:]
assert len(argv) >= 3, "need: <in.pmx> <out_dir> <basename>"
PMX, OUTDIR, BASE = argv[0], argv[1], argv[2]

d = open(PMX, 'rb').read()
pos = 0
def u8():  global pos; v = d[pos]; pos += 1; return v
def i32(): global pos; v = struct.unpack_from('<i', d, pos)[0]; pos += 4; return v
def f32(): global pos; v = struct.unpack_from('<f', d, pos)[0]; pos += 4; return v

def ridx(size, signed=False):
    global pos
    fmt = {1: 'b', 2: 'h', 4: 'i'}[size] if signed else {1: 'B', 2: 'H', 4: 'I'}[size]
    v = struct.unpack_from('<' + fmt, d, pos)[0]; pos += size
    return v

assert d[:4] == b'PMX ', 'not a PMX file'
pos = 4
ver = f32()
_hsize, enc, addUV, vsz, tsz, msz, bsz, mosz, rbsz = struct.unpack_from('<9B', d, pos); pos += 9
codec = 'utf-16-le' if enc == 0 else 'utf-8'
def text():
    global pos
    n = i32(); s = d[pos:pos + n].decode(codec, 'replace'); pos += n
    return s

print('PMX %.1f  name=%r' % (ver, text()))
print('  name_en=%r' % text()); text(); text()
print('  idx sizes: vert=%d tex=%d mat=%d bone=%d morph=%d rb=%d  addUV=%d'
      % (vsz, tsz, msz, bsz, mosz, rbsz, addUV))

# ---------- 顶点 ----------
vcount = i32()
V = []
for i in range(vcount):
    px, py, pz = f32(), f32(), f32()
    nx, ny, nz = f32(), f32(), f32()
    u, v = f32(), f32()
    for _ in range(addUV * 4): f32()
    wt = u8()
    if   wt == 0: ridx(bsz)                                                  # BDEF1
    elif wt == 1: ridx(bsz); ridx(bsz); f32()                                # BDEF2
    elif wt == 2: [ridx(bsz) for _ in range(4)]; [f32() for _ in range(4)]    # BDEF4
    elif wt == 3: ridx(bsz); ridx(bsz); f32(); [f32() for _ in range(9)]      # SDEF
    elif wt == 4: [ridx(bsz) for _ in range(4)]; [f32() for _ in range(4)]    # QDEF
    else: raise ValueError('weight type %d @vert %d' % (wt, i))
    f32()                                                                    # edge scale
    V.append((px, py, pz, nx, ny, nz, u, v))
print('vertices', len(V))

# ---------- 面 ----------
fcount = i32()
F = [ridx(vsz) for _ in range(fcount)]
print('face indices', fcount, '-> tris', fcount // 3)

# ---------- 贴图 ----------
textures = [text() for _ in range(i32())]
print('textures:')
for i, t in enumerate(textures):
    fn = os.path.basename(t.replace('\\', '/'))
    print('   [%d] %r  local_exists=%s' % (i, t, os.path.exists(os.path.join(OUTDIR, fn))))

# ---------- 材质 ----------
mats = []
for i in range(i32()):
    m = dict(name=text(), name_en=text())
    m['diffuse']  = [f32() for _ in range(4)]
    m['specular'] = [f32() for _ in range(3)]
    m['spow']     = f32()
    m['ambient']  = [f32() for _ in range(3)]
    m['flag']     = u8()
    m['edgecol']  = [f32() for _ in range(4)]
    m['edgesize'] = f32()
    m['tex']      = ridx(tsz, True)
    m['sphere']   = ridx(tsz, True)
    m['spmode']   = u8()
    m['toon']     = u8() if u8() == 1 else ridx(tsz, True)    # 共享 toon 是 1 字节，独立 toon 是索引
    m['memo']     = text()
    m['nfaces']   = i32()
    mats.append(m)
print('materials', len(mats))
tot = 0
for i, m in enumerate(mats):
    tn = textures[m['tex']] if 0 <= m['tex'] < len(textures) else None
    print('   [%2d] %-30r faces=%-6d tex=%s alpha=%.2f flag=0x%02x' %
          (i, m['name'], m['nfaces'],
           os.path.basename(tn.replace('\\', '/')) if tn else None,
           m['diffuse'][3], m['flag']))
    tot += m['nfaces']
assert tot == fcount, 'material face ranges (%d) != face indices (%d)' % (tot, fcount)
print('material face ranges OK (%d)' % tot)

# ================= 写 OBJ =================
MIRROR = True      # PMX 是左手系 -> OBJ 右手系：Z 取反，并反转三角绕序
def xf(p):
    return (p[0], p[1], -p[2]) if MIRROR else p

lines = ['# converted from %s (PMX %.1f)' % (os.path.basename(PMX), ver),
         'mtllib %s.mtl' % BASE, 'o %s' % BASE]
for (px, py, pz, _a, _b, _c, _u, _v) in V:
    lines.append('v %.6f %.6f %.6f' % xf((px, py, pz)))
for (_a, _b, _c, nx, ny, nz, _u, _v) in V:
    lines.append('vn %.6f %.6f %.6f' % xf((nx, ny, nz)))
for (_a, _b, _c, _d, _e, _f, u, v) in V:
    lines.append('vt %.6f %.6f' % (u, 1.0 - v))          # PMX 的 V 朝下 -> OBJ 朝上

def mtl_name(m, i):
    return (m['name'] or m['name_en'] or 'mat%d' % i).replace(' ', '_').replace('/', '_')

# PMX 常把同一个材质按面段拆成好几条（名字相同、参数相同）——合并成一条，
# 否则 MTL 里会出现重复的 newmtl，严格的加载器会报错。
_K = lambda m: (mtl_name(m, 0), tuple(m['diffuse']), tuple(m['specular']), round(m['spow'], 4),
                tuple(m['ambient']), m['tex'], m['sphere'], m['spmode'])
uniq, name_of = [], {}
for i, m in enumerate(mats):
    if m['nfaces'] <= 0: continue
    base = mtl_name(m, i)
    k = _K(m)
    if k not in name_of:
        nm = base
        used = set(name_of.values())
        j = 2
        while nm in used:
            nm = '%s_%d' % (base, j); j += 1
        name_of[k] = nm
        uniq.append((nm, m))
    m['_out_name'] = name_of[k]

fi = 0
for mi, m in enumerate(mats):
    n = m['nfaces']
    if n <= 0: continue
    lines.append('usemtl %s' % m['_out_name'])
    for k in range(fi, fi + n, 3):
        a, b, c = F[k], F[k + 1], F[k + 2]
        if MIRROR: a, b, c = a, c, b
        lines.append('f %d/%d/%d %d/%d/%d %d/%d/%d'
                     % (a + 1, a + 1, a + 1, b + 1, b + 1, b + 1, c + 1, c + 1, c + 1))
    fi += n
objp = os.path.join(OUTDIR, BASE + '.obj')
open(objp, 'w', encoding='utf-8', newline='\n').write('\n'.join(lines) + '\n')
print('OBJ ->', objp, os.path.getsize(objp), ' 材质组 %d -> %d' % (len(mats), len(uniq)))

# ================= 写 MTL =================
out = ['# converted from %s' % os.path.basename(PMX)]
for nm, m in uniq:
    out += ['', 'newmtl %s' % nm, '# %s' % m['name'],
            'Ka %.6f %.6f %.6f' % tuple(m['ambient']),
            'Kd %.6f %.6f %.6f' % tuple(m['diffuse'][:3]),
            'Ks %.6f %.6f %.6f' % tuple(m['specular']),
            'Ns %.3f' % min(1000.0, max(0.0, m['spow'])),
            'd %.6f' % m['diffuse'][3], 'illum 2']
    if 0 <= m['tex'] < len(textures):
        out.append('map_Kd %s' % os.path.basename(textures[m['tex']].replace('\\', '/')))
    if 0 <= m['sphere'] < len(textures):
        out.append('# sphere %s mode=%d (OBJ 没法表达，只记录)'
                   % (os.path.basename(textures[m['sphere']].replace('\\', '/')), m['spmode']))
mtlp = os.path.join(OUTDIR, BASE + '.mtl')
open(mtlp, 'w', encoding='utf-8', newline='\n').write('\n'.join(out) + '\n')
print('MTL ->', mtlp, os.path.getsize(mtlp))

xs = [xf(v[:3])[0] for v in V]; ys = [xf(v[:3])[1] for v in V]; zs = [xf(v[:3])[2] for v in V]
print('bbox X[%.3f,%.3f] Y[%.3f,%.3f] Z[%.3f,%.3f]' % (min(xs),max(xs),min(ys),max(ys),min(zs),max(zs)))
print('size %.3f x %.3f x %.3f' % (max(xs)-min(xs), max(ys)-min(ys), max(zs)-min(zs)))
us = [v[6] for v in V]; vs = [v[7] for v in V]
print('UV u[%.4f,%.4f] v[%.4f,%.4f]' % (min(us),max(us),min(vs),max(vs)))
print('DONE')
