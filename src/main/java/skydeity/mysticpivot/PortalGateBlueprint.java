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
 * 玄枢传送门广场的形制（23×25 · 高 18 · 1099 格 · 12 种方块）。
 *
 * <p>由 {@code tools/gen_portal_blueprint.py} 把 {@code chuansongmen.schem} 编译成下面的
 * 分层字符画，运行期零文件 IO、零 NBT。换建筑就重跑那个脚本。</p>
 *
 * <p>形制：逐层收分的八角台基（最外一层是歪成菱形的铺地，金块压角、紫珀嵌线）→ 中央方井 →
 * 井口两侧各一根方柱（石英 + 黄金 + 紫珀，隔一层一圈黑曜石束腰）→ y 8 起两柱之间一道蓝玻璃横梁
 * → 井心一根蓝玻璃柱（内嵌海晶灯，y 8…12 是 5×5 的灯笼层）→ 顶上一串散开的蓝玻璃板；
 * y 4 的三颗信标是整座门真正的「芯」。</p>
 *
 * <p>材料：chiseled quartz block 519 / gold block 106 / purpur block 104 / quartz stairs 91 / blue stained glass 64 / sea lantern 59 / quartz slab 47 / obsidian 42 / blue stained glass pane 38 / light blue stained glass pane 13 / quartz pillar 13 / beacon 3</p>
 *
 * <p>注：三颗信标是方块实体，{@code StructureDeployer} 按规矩不碰带方块实体的格子，
 * 所以一键收回会在原地留下它们（并在提示里点名）。</p>
 */
final class PortalGateBlueprint {

    /** 字符画里用到的字符，与 {@link #STATES} 同序；{@code .} 是空气。 */
    private static final String CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij";

    /** 方块状态串（带属性；楼梯的 facing / half / shape 都在里面）。 */
    private static final String[] STATES = {
            "minecraft:purpur_block",  // A
            "minecraft:gold_block",  // B
            "minecraft:chiseled_quartz_block",  // C
            "minecraft:quartz_stairs[facing=east]",  // D
            "minecraft:quartz_stairs[facing=west]",  // E
            "minecraft:quartz_stairs[facing=east,shape=outer_right]",  // F
            "minecraft:quartz_stairs[facing=south]",  // G
            "minecraft:quartz_stairs[facing=south,shape=inner_left]",  // H
            "minecraft:quartz_stairs[facing=north,shape=inner_left]",  // I
            "minecraft:quartz_stairs[facing=north,shape=outer_right]",  // J
            "minecraft:quartz_stairs[facing=north]",  // K
            "minecraft:quartz_stairs[facing=west,shape=outer_right]",  // L
            "minecraft:quartz_stairs[facing=east,shape=outer_left]",  // M
            "minecraft:quartz_stairs[facing=north,shape=inner_right]",  // N
            "minecraft:quartz_stairs[facing=west,shape=inner_left]",  // O
            "minecraft:quartz_stairs[facing=south,shape=outer_right]",  // P
            "minecraft:quartz_stairs[facing=north,shape=outer_left]",  // Q
            "minecraft:quartz_pillar",  // R
            "minecraft:quartz_slab",  // S
            "minecraft:obsidian",  // T
            "minecraft:sea_lantern",  // U
            "minecraft:blue_stained_glass",  // V
            "minecraft:beacon",  // W
            "minecraft:light_blue_stained_glass_pane[east=true]",  // X
            "minecraft:light_blue_stained_glass_pane[west=true]",  // Y
            "minecraft:blue_stained_glass_pane[south=true]",  // Z
            "minecraft:blue_stained_glass_pane[north=true]",  // a
            "minecraft:blue_stained_glass_pane[east=true]",  // b
            "minecraft:blue_stained_glass_pane[east=true,west=true]",  // c
            "minecraft:blue_stained_glass_pane[west=true]",  // d
            "minecraft:blue_stained_glass_pane",  // e
            "minecraft:light_blue_stained_glass_pane[south=true]",  // f
            "minecraft:blue_stained_glass_pane[north=true,south=true]",  // g
            "minecraft:light_blue_stained_glass_pane[north=true,south=true]",  // h
            "minecraft:light_blue_stained_glass_pane[east=true,west=true]",  // i
            "minecraft:light_blue_stained_glass_pane[north=true]",  // j
    };

    /**
     * 逐层字符画：{@code == y,x,z} 起一层（层号 + 该层左上角），其后每行沿 +Z、
     * 每个字符沿 +X；{@code .} 是空气，其余查 {@link #CHARS}。
     */
    private static final String MAP = """
            == 0,-11,-12
            ...........A...........
            ..........ABA..........
            .........CCCCC.........
            ........CCCCCCC........
            .......DCCCCCCCE.......
            .....FGHCCCCCCCE.......
            .....HCC.......C.......
            ....GCC........CGG.....
            ...BCC..........CCCB...
            ..DCCC...........CCCE..
            .DCCCC...........CCCCE.
            DCCCCC...........CCCCCE
            DCCCCC...........CCCCCE
            DCCCCC...........CCCCCE
            .DCCCC..........CCCCCI.
            .JKCCC...........CCCKL.
            ...BCCCCCCCCCCCC..CB...
            ....MKKKNCCCCCIKKKK....
            ........DCCCCCOP.......
            .......DCCCCCCCE.......
            .......JCCCCCCCQ.......
            ........CCCCCCC........
            .........CCCCC.........
            ..........ABA..........
            R..........A...........
            == 1,-10,-11
            ..........B..........
            ........SBTBS........
            .......DBTTTBE.......
            .......CCTUTCC.......
            .......TCCCCCT.......
            ......DTTCCCTT.......
            .....GTC...CTTT......
            ...BCTC.......TCCB...
            ..BBCTC.........CBB..
            .CCCCTC.........CCCC.
            AACCCCC.........CCCAA
            RACCCCC...U.....CCCAR
            AACCCCC.........CCCAA
            .CCCCTC........TCCCC.
            ..BBCTT........CCBB..
            ...BSSTTCCCCCTTKKB...
            ........TCCCT........
            ........TCCCT........
            .......TCCCCCT.......
            .......CCTUTCC.......
            .......KBTTTBK.......
            ........SBTBS........
            ..........B..........
            == 2,-9,-9
            .........S.........
            ......SB.V.BS......
            ......KCBTBCK......
            .......CABAC.......
            ......DUCC..E......
            .....GUCCC..UG.....
            ...BSUCCCC....UB...
            DCBCUCC........CBCK
            ACCCCCC........CCCA
            ACCCCCC..U.....CCCA
            ACCCCCC........CCCA
            KCBC.CC.......UCBCK
            ...BUUCCCCCCCUCB...
            .....UUCCCCCUU.....
            .......UCCCU.......
            .......CABAC.......
            ......DCBTBCK......
            ......SB.V.BS......
            .........S.........
            == 3,-10,-6
            ........S...S........
            ........CASAC........
            .......SCARACS.......
            ....S.CCCCCCCCCCS....
            ...SCCCCCCCCCCCCCS...
            .KCKACAAACCCAAACAKCK.
            KCCKACAAACCCAAACAKCCK
            .KCKACAAACCCAAACAKCK.
            ...SCCCCCCCCCCCCCS...
            ....SCCCCCCCCCCCS....
            ......SSCARACSS......
            ........CASAC........
            ........S...S........
            == 4,-10,-4
            ........S...S........
            ........CAGAC........
            ....CBCBCSSSCBCBC....
            .CCCCCK.KKKKK.KCCCCC.
            CUCCCU.WSSWSSW.UCCCUC
            .CCCCCK.KKKKK.KCCCCC.
            ....CBCBCSSSCBCBC....
            ........CAKAC........
            ........S...S........
            == 5,-8,-2
            .CBC.........CBC.
            CCCA.........ACCC
            CCCA.........ACCC
            CCCA.........ACCC
            .CBC.........CBC.
            == 6,-9,-2
            .CBC...........CBC.
            BCCA...........ACCB
            BCUV...........VUCB
            BCCA...........ACCB
            .CBC...........CBC.
            == 7,-9,-2
            .BC.............CB.
            RCA.............ACR
            CCA.............ACC
            RCA.............ACR
            .BC.............CB.
            == 8,-9,-2
            .BC.............CB.
            RCA.....VVV.....ACR
            CUV.....VVV.....VUC
            RCA.....VVV.....ACR
            .BC.............CB.
            == 9,-9,-4
            ........XY.........
            ...................
            .BC.....VVV.....CB.
            CCA....VUUUV....ACC
            CCA..Z.VUUUV....ACC
            CCA..a.VUUUV....ACC
            .BC.....VVV.....CB.
            ...................
            ........bcd........
            == 10,-9,-4
            .......bcccY.......
            ......e.....e......
            ..BC.e..VVV..f.CB..
            CBAA...VUUUV.g.AABC
            VUUV.Z.VUUUV.g.VUUV
            CBAA.h.VUUUV.g.AABC
            ..BC.a..VVV..a.CB..
            ............e......
            .......XiciY.......
            == 11,-9,-4
            .........bd........
            ......e............
            ........VVV........
            .BAB...VUUUV.Z.BAB.
            BCCA...VUUUV.g.ACCB
            .BAB...VUUUV.j.BAB.
            ........VVV........
            ...................
            ........XiY........
            == 12,-8,-1
            .B.....VVV.....B.
            AB.....VVV.....BA
            .B.....VVV.....B.
            == 13,-8,-1
            .......e.e.......
            B.......e.......B
            .......e.e.......
            == 14,-1,-1
            e.e
            .e.
            ..e
            == 15,0,0
            e.
            .e
            == 16,0,0
            e
            == 17,0,0
            e
            """;

    /** 展开后的结构件（局部坐标，只读，顺序 = 从下往上）。 */
    private static final List<StructureDeployer.Part> PARTS = build();

    /** 搭一座要哪些材料（按方块物品合并，玻璃朝向不另算一种）。 */
    private static final Map<Item, Integer> MATERIALS = tally();

    private PortalGateBlueprint() {
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
        for (StructureDeployer.Part piece : PARTS) {
            need.merge(piece.state().getBlock().asItem(), 1, Integer::sum);
        }
        return Collections.unmodifiableMap(need);
    }

    /**
     * {@code minecraft:chiseled_quartz_block} → 方块状态。
     *
     * <p>属性对不上（未来版本删了某个属性）就跳过那一条，不整格丢掉：宁可少一个
     * {@code waterlogged} 也不要整座门缺一块。</p>
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

    /** 全套结构件（局部坐标，只读，顺序 = 从下往上）。 */
    static List<StructureDeployer.Part> parts() {
        return PARTS;
    }

    /** 搭一座要哪些材料。 */
    static Map<Item, Integer> materials() {
        return MATERIALS;
    }

    /** 相对锚点的总高：18 格。 */
    static int height() {
        return 18;
    }

    static int minX() {
        return -11;
    }

    static int maxX() {
        return 11;
    }

    static int minZ() {
        return -12;
    }

    static int maxZ() {
        return 12;
    }
}
