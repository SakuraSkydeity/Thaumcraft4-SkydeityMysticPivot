#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
schem → Java 常量（第一百九十二批新建；第二百零四批改成「分层字符画」）。

把 {@code tools/schematics/structure.schem} 编译成 {@code VoidAltarBlueprint.java}：
运行期零文件 IO、零 NBT，只有一段字符画 + 一张状态表。

第 192 批的写法是平铺 {@code int[] CELLS}（{@code dx, dy, dz, 序号}），255 格要写一千个数字；
第 204 批换成 {@link blueprint_map} 的分层字符画 —— 同样一格一个字符，还能直接看出每层长什么样。

<h2>取格规则（与 {@code VoidMiningStructure} 一致）</h2>

  * 只取非空气格（air / cave_air / void_air 跳过）；
  * 锚点 = 非空气包围盒的<b>底面正中</b>（x / z 取 {@code (min+max)//2}、y 取 {@code min}），
    水晶塔就摆在这一格，整座塔从它往上长；
  * 锚点那一格若本来有方块，跳过（留给塔自己）；
  * 不随朝向转。

用法：
    python tools/gen_void_blueprint.py [文件.schem] [--out 路径.java]
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import blueprint_map as M
import schem_structure as S

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_IN = os.path.join(ROOT, "tools/schematics/structure.schem")
DEFAULT_OUT = os.path.join(ROOT, "src/main/java/skydeity/mysticpivot/VoidAltarBlueprint.java")


TEMPLATE = '''package skydeity.mysticpivot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 玄枢虚空水晶塔的形制（{size_x}×{size_z} · 高 {height} · {cell_count} 格 · {block_count} 种方块）。
 *
 * <p>由 {@code tools/gen_void_blueprint.py} 把 {@code structure.schem} 编译成下面的分层字符画，
 * 运行期零文件 IO、零 NBT。换建筑就重跑那个脚本。</p>
 *
 * <p>形制：底座四角立柱、中段一圈台阶与玻璃、上段收成 4×4 的玻璃芯塔，顶端再收成一根细尖；
 * 塔心是一根 2×2 的玻璃柱（内嵌海晶灯），也就是竖光要压住的那条中轴。</p>
 *
 * <p>材料：{materials}</p>
 */
final class VoidAltarBlueprint {

    /** 字符画里用到的字符，与 {@link #STATES} 同序；{@code .} 是空气。 */
    private static final String CHARS = "{chars}";

    /** 方块状态串（带属性；楼梯的 facing / half / shape 都在里面）。 */
    private static final String[] STATES = {
{states}
    };

    /**
     * 逐层字符画：{@code == y,x,z} 起一层（层号 + 该层左上角），其后每行沿 +Z、
     * 每个字符沿 +X；{@code .} 是空气，其余查 {@link #CHARS}。
     */
    private static final String MAP = """
{map}
            """;

    /** 展开后的结构件（相对水晶塔的偏移，只读，顺序 = 从下往上）。 */
    private static final List<StructureDeployer.Part> PARTS = build();

    /** 搭一座要哪些材料（按方块物品合并，楼梯朝哪边不另算一种）。 */
    private static final Map<Item, Integer> MATERIALS = tally();

    private VoidAltarBlueprint() {
    }

    private static List<StructureDeployer.Part> build() {
        BlockState[] states = new BlockState[STATES.length];
        for (int i = 0; i < STATES.length; i++) {
            states[i] = state(STATES[i]);
        }
        List<StructureDeployer.Part> parts = new ArrayList<>();
        int y = 0;
        int x0 = 0;
        int z0 = 0;
        int row = 0;
        for (String line : MAP.split("\\n")) {
            if (line.startsWith("== ")) {
                String[] head = line.substring(3).split(",");
                y = Integer.parseInt(head[0]);
                x0 = Integer.parseInt(head[1]);
                z0 = Integer.parseInt(head[2]);
                row = 0;
            } else if (!line.isEmpty()) {
                for (int i = 0; i < line.length(); i++) {
                    int k = CHARS.indexOf(line.charAt(i));
                    if (k >= 0) {
                        parts.add(new StructureDeployer.Part(
                                new BlockPos(x0 + i, y, z0 + row), states[k]));
                    }
                }
                row++;
            }
        }
        parts.sort(Comparator.comparingInt((StructureDeployer.Part p) -> p.offset().getY())
                .thenComparingInt(p -> p.offset().getX())
                .thenComparingInt(p -> p.offset().getZ()));
        return Collections.unmodifiableList(parts);
    }

    private static Map<Item, Integer> tally() {
        Map<Item, Integer> need = new LinkedHashMap<>();
        for (StructureDeployer.Part part : PARTS) {
            need.merge(part.state().getBlock().asItem(), 1, Integer::sum);
        }
        return Collections.unmodifiableMap(need);
    }

    /**
     * {@code minecraft:quartz_stairs[facing=east,half=top,shape=straight]} → 方块状态。
     *
     * <p>属性对不上（未来版本删了某个属性）就跳过那一条，不整格丢掉：宁可少一个
     * {@code waterlogged} 也不要整座塔缺一块。</p>
     */
    private static BlockState state(String spec) {
        int bracket = spec.indexOf('[');
        String idPart = bracket < 0 ? spec : spec.substring(0, bracket);
        ResourceLocation id = ResourceLocation.tryParse(idPart);
        if (id == null) {
            throw new IllegalStateException("内置形制里的方块名不合法：" + idPart);
        }
        Block block = BuiltInRegistries.BLOCK.getOptional(id).orElseThrow(
                () -> new IllegalStateException("内置形制里的方块不存在：" + idPart));
        BlockState st = block.defaultBlockState();
        if (bracket < 0 || !spec.endsWith("]")) {
            return st;
        }
        for (String pair : spec.substring(bracket + 1, spec.length() - 1).split(",")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            Property<?> property = st.getBlock().getStateDefinition()
                    .getProperty(pair.substring(0, eq).trim());
            if (property != null) {
                st = apply(st, property, pair.substring(eq + 1).trim());
            }
        }
        return st;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState apply(BlockState st, Property property, String value) {
        Optional<? extends Comparable> parsed = property.getValue(value);
        return parsed.isEmpty() ? st : st.setValue(property, parsed.get());
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /** 全套结构件（相对水晶塔的偏移，只读，顺序 = 从下往上）。 */
    static List<StructureDeployer.Part> parts() {
        return PARTS;
    }

    /** 搭一座要哪些材料。 */
    static Map<Item, Integer> materials() {
        return MATERIALS;
    }

    /** 相对水晶塔的总高：{height} 格。 */
    static int height() {
        return {height};
    }

    static int minX() {
        return {min_x};
    }

    static int maxX() {
        return {max_x};
    }

    static int minZ() {
        return {min_z};
    }

    static int maxZ() {
        return {max_z};
    }

    /** 中轴（格边界坐标）：{size_x} 格宽的建筑正中的那条线。 */
    static double axisX() {
        return {axis_x};
    }

    static double axisZ() {
        return {axis_z};
    }
}
'''


def main(argv):
    path = next((a for a in argv[1:] if not a.startswith("--")), DEFAULT_IN)
    out = argv[argv.index("--out") + 1] if "--out" in argv else DEFAULT_OUT

    sch = S.load_schem(path)
    anchor = S.anchor_of(sch["cells"])
    cells = S.structure(sch["cells"], anchor)
    states, char_of, text = M.encode(cells)

    xs = [c[0] for c in cells]
    ys = [c[1] for c in cells]
    zs = [c[2] for c in cells]
    tally = {}
    for (_, _, _, name) in cells:
        short = name.split("[")[0].split(":")[-1]
        tally[short] = tally.get(short, 0) + 1
    materials = " / ".join("%s %d" % (k.replace("_", " "), v)
                           for k, v in sorted(tally.items(), key=lambda kv: (-kv[1], kv[0])))

    fields = {
        "chars": M.CHARS[:len(states)],
        "states": M.java_states(states, char_of),
        "map": M.java_map(text),
        "materials": materials,
        "cell_count": len(cells),
        "block_count": len(tally),
        "height": max(ys) - min(ys) + 1,
        "min_x": min(xs),
        "max_x": max(xs),
        "min_z": min(zs),
        "max_z": max(zs),
        "size_x": max(xs) - min(xs) + 1,
        "size_z": max(zs) - min(zs) + 1,
        "axis_x": "%.1fD" % (min(xs) + (max(xs) - min(xs) + 1) / 2.0),
        "axis_z": "%.1fD" % (min(zs) + (max(zs) - min(zs) + 1) / 2.0),
    }
    body = TEMPLATE
    for key, value in fields.items():
        body = body.replace("{%s}" % key, str(value))

    with open(out, "w", encoding="utf-8", newline="\n") as f:
        f.write(body)

    print("schem      %s" % os.path.basename(path))
    print("锚点(塔)    schem 内 (%d, %d, %d)" % anchor)
    print("结构件      %d 格 / %d 种状态 / %d 种方块 / 高 %d 格"
          % (len(cells), len(states), fields["block_count"], fields["height"]))
    print("包围盒      x %d…%d  z %d…%d" % (min(xs), max(xs), min(zs), max(zs)))
    print("写出        %s（%d B）" % (out, os.path.getsize(out)))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
