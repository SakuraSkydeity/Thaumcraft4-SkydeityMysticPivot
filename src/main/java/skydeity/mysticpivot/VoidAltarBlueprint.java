package skydeity.mysticpivot;

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
 * 玄枢虚空水晶塔的形制（8×8 · 高 13 · 255 格 · 9 种方块）。
 *
 * <p>由 {@code tools/gen_void_blueprint.py} 把 {@code structure.schem} 编译成下面的分层字符画，
 * 运行期零文件 IO、零 NBT。换建筑就重跑那个脚本。</p>
 *
 * <p>形制：底座四角立柱、中段一圈台阶与玻璃、上段收成 4×4 的玻璃芯塔，顶端再收成一根细尖；
 * 塔心是一根 2×2 的玻璃柱（内嵌海晶灯），也就是竖光要压住的那条中轴。</p>
 *
 * <p>材料：light blue stained glass 73 / blue wool 44 / chiseled quartz block 42 / sea lantern 36 / quartz slab 32 / quartz stairs 12 / light blue stained glass pane 8 / quartz bricks 6 / quartz pillar 2</p>
 */
final class VoidAltarBlueprint {

    /** 字符画里用到的字符，与 {@link #STATES} 同序；{@code .} 是空气。 */
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRS";

    /** 方块状态串（带属性；楼梯的 facing / half / shape 都在里面）。 */
    private static final String[] STATES = {
            "minecraft:chiseled_quartz_block",  // A
            "minecraft:blue_wool",  // B
            "minecraft:light_blue_stained_glass",  // C
            "minecraft:quartz_pillar",  // D
            "minecraft:quartz_stairs[facing=south,half=top]",  // E
            "minecraft:quartz_stairs[facing=east,half=top]",  // F
            "minecraft:quartz_stairs[facing=west,half=top]",  // G
            "minecraft:quartz_stairs[facing=north,half=top]",  // H
            "minecraft:light_blue_stained_glass_pane[east=true,south=true]",  // I
            "minecraft:sea_lantern",  // J
            "minecraft:quartz_slab",  // K
            "minecraft:light_blue_stained_glass_pane[north=true,west=true]",  // L
            "minecraft:light_blue_stained_glass_pane[south=true,west=true]",  // M
            "minecraft:light_blue_stained_glass_pane[east=true,north=true]",  // N
            "minecraft:quartz_bricks",  // O
            "minecraft:quartz_stairs[facing=south]",  // P
            "minecraft:quartz_stairs[facing=east]",  // Q
            "minecraft:quartz_stairs[facing=west]",  // R
            "minecraft:quartz_stairs[facing=north]",  // S
    };

    /**
     * 逐层字符画：{@code == y,x,z} 起一层（层号 + 该层左上角），其后每行沿 +Z、
     * 每个字符沿 +X；{@code .} 是空气，其余查 {@link #CHARS}。
     */
    private static final String MAP = """
            == 0,-2,-2
            A....B
            ......
            ......
            ..C...
            ......
            B....D
            == 1,-3,-3
            ..E..A..
            .B....B.
            F......A
            ...CC...
            ...CC...
            A......G
            .B....B.
            ..A..H..
            == 2,-3,-3
            IBA..AA.
            BJKKKKAA
            AK..C.KA
            .K.CCCK.
            .KCCC.K.
            AK..C.KA
            AAKKKKJB
            .DA..ABL
            == 3,-3,-3
            IBA..ABM
            BJBBBBJB
            AB.CC.BA
            .BCJJCB.
            .BCJJCB.
            AB.CC.BA
            BJBBBBJB
            NBA..ABL
            == 4,-3,-3
            .OA..ABM
            OJKKKKJB
            AK.CC.KA
            .KCJJCK.
            .KCJJCK.
            AK.CC.KA
            BJKKKKJO
            NBA..AO.
            == 5,-3,-3
            ..P..AB.
            .O....JB
            Q..CC..A
            ..CJJC..
            ..CJJC..
            A..CC..R
            BJ....O.
            .BA..S..
            == 6,-3,-3
            .....P..
            .A....B.
            ...CC..R
            ..CJJC..
            ..CJJC..
            Q..CC...
            .B....A.
            ..S.....
            == 7,-2,-2
            .....A
            ..CC..
            .CJJC.
            .CJJC.
            ..CC..
            A.....
            == 8,-1,-1
            .CC.
            CJJC
            .CJC
            .CC.
            == 9,0,-1
            C..
            CC.
            CJC
            .C.
            == 10,0,0
            CC
            .C
            == 11,1,0
            C
            C
            == 12,1,0
            C
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
        for (String line : MAP.split("\n")) {
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

    /** 相对水晶塔的总高：13 格。 */
    static int height() {
        return 13;
    }

    static int minX() {
        return -3;
    }

    static int maxX() {
        return 4;
    }

    static int minZ() {
        return -3;
    }

    static int maxZ() {
        return 4;
    }

    /** 中轴（格边界坐标）：8 格宽的建筑正中的那条线。 */
    static double axisX() {
        return 1.0D;
    }

    static double axisZ() {
        return 1.0D;
    }
}
