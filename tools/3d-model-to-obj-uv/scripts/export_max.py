# -*- coding: utf-8 -*-
"""无头 Blender：.max -> .blend / .obj / .mtl
用法: blender.exe -b --factory-startup --python export_max.py -- <in.max> <out_dir> <basename>
"""
import bpy, os, sys, glob, array, mathutils, traceback

argv = sys.argv
argv = argv[argv.index('--') + 1:] if '--' in argv else []
assert len(argv) >= 3, "need: <in.max> <out_dir> <basename>"
MAXPATH, OUTDIR, BASE = argv[0], argv[1], argv[2]

MOD = "bl_ext.user_default.io_scene_max"
try:
    bpy.ops.preferences.addon_enable(module=MOD)
except Exception as e:
    print("ENABLE FAILED:", e)
    raise
from importlib import import_module
im = import_module(MOD + ".import_max")

# 清空场景
for o in list(bpy.data.objects):
    bpy.data.objects.remove(o, do_unlink=True)
for me in list(bpy.data.meshes):
    bpy.data.meshes.remove(me)

print("IMPORT", MAXPATH)
# 注意：无头模式不能走 im.load()（内部 context.window.cursor_set 会炸）
im.read(bpy.context, MAXPATH, mathutils.Matrix.Scale(1.0, 4),
        {'MATERIAL', 'UV', 'PRIMITIVE', 'EMPTY'}, True, True)

meshes = [o for o in bpy.data.objects if o.type == 'MESH']
print("MESHES", [(o.name, len(o.data.vertices), len(o.data.polygons)) for o in meshes])
if not meshes:
    raise SystemExit("no mesh imported")
ob = meshes[0]
me = ob.data

# --- UV 的 V 归一到 [0,1)（io_scene_max 常给出 v < 0） ---
uvd = me.uv_layers.active.data
buf = array.array('f', [0.0]) * (len(uvd) * 2)
uvd.foreach_get('uv', buf)
print("UV before u[%.4f,%.4f] v[%.4f,%.4f]" % (min(buf[0::2]), max(buf[0::2]),
                                               min(buf[1::2]), max(buf[1::2])))
if max(buf[1::2]) <= 0.0:
    for i in range(1, len(buf), 2):
        buf[i] += 1.0
elif min(buf[1::2]) < 0.0:
    for i in range(1, len(buf), 2):
        buf[i] %= 1.0
uvd.foreach_set('uv', buf)
uvd.foreach_get('uv', buf)
print("UV after  u[%.4f,%.4f] v[%.4f,%.4f]" % (min(buf[0::2]), max(buf[0::2]),
                                               min(buf[1::2]), max(buf[1::2])))

# --- 烘焙物体变换 ---
bpy.context.view_layer.objects.active = ob
for o in bpy.data.objects:
    o.select_set(o is ob)
bpy.ops.object.transform_apply(location=True, rotation=True, scale=True)

ws = [ob.matrix_world @ v.co for v in me.vertices]
xs = [p.x for p in ws]; ys = [p.y for p in ws]; zs = [p.z for p in ws]
print("FINAL bbox x[%.3f,%.3f] y[%.3f,%.3f] z[%.3f,%.3f]" %
      (min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)))
print("FINAL size %.3f x %.3f x %.3f" %
      (max(xs) - min(xs), max(ys) - min(ys), max(zs) - min(zs)))

for img in bpy.data.images:
    if img.source == 'FILE' and img.filepath:
        print("IMG", img.name, "->", img.filepath)

ob.name = BASE
me.name = BASE

os.makedirs(OUTDIR, exist_ok=True)
blendpath = os.path.join(OUTDIR, BASE + '.blend')
bpy.ops.wm.save_as_mainfile(filepath=blendpath)
print("BLEND", blendpath)

objpath = os.path.join(OUTDIR, BASE + '.obj')
kwargs = dict(filepath=objpath, export_selected_objects=False, export_uv=True,
              export_normals=True, export_materials=True, export_triangulated_mesh=False,
              apply_modifiers=True, export_material_groups=False, export_object_groups=False,
              path_mode='RELATIVE', forward_axis='NEGATIVE_Z', up_axis='Y')
valid = set(bpy.ops.wm.obj_export.get_rna_type().properties.keys())
bpy.ops.wm.obj_export(**{k: v for k, v in kwargs.items() if k in valid})
print("OBJ", objpath, os.path.getsize(objpath))
print("DONE")
