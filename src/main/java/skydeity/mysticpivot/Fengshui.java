package skydeity.mysticpivot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 风水气脉判定：把八种卦气对应到世界地形（见魔导书「卦之地气」诸页），供
 * 玄枢台座的卦象转化与卦位石的感应效果共用。
 *
 * <p>判定优先级：地狱森林（离）→ 诸群系（坎/艮/巽/兑/震）→ 深层（坤，Y&lt;10）→
 * 开阔天空（乾，头顶无遮挡）→ 无气。</p>
 */
public final class Fengshui {

    /** 八卦与「无气」。 */
    public enum Trigram {
        NONE, QIAN, DUI, LI, ZHEN, XUN, KAN, GEN, KUN
    }

    /**
     * 判定优先级：一处同时满足好几条卦气时，{@link #qiAt} 按这个次序取第一条。
     *
     * <p>沿用最初的写法——越「特异」的地貌越优先（地狱森林 &gt; 暖水海洋 &gt; 山地 &gt;
     * 森林 &gt; 沼泽 &gt; 恶地 &gt; 深层 &gt; 开阔天空），于是平原上见乾、森林里见巽。</p>
     */
    private static final Trigram[] PRIORITY = {
            Trigram.LI, Trigram.KAN, Trigram.GEN, Trigram.XUN,
            Trigram.DUI, Trigram.ZHEN, Trigram.KUN, Trigram.QIAN
    };

    /**
     * 判定某位置当下凝聚的卦气（<b>只取优先级最高的那一卦</b>）。
     *
     * <p>优先级：离（地狱森林）→ 坎（暖水海洋）→ 艮（山地）→ 巽（森林）→ 兑（沼泽）→
     * 震（恶地）→ 坤（深层）→ 乾（开阔天空）。同一处<b>可能同时满足好几条</b>
     * （例如森林里又见得到天：巽与乾皆成立），要全部列出请用 {@link #qiAll}。</p>
     *
     * <p>台面的黑曜石化哭泣黑曜石只认乾，用这一支即可。</p>
     */
    public static Trigram qiAt(Level level, BlockPos pos) {
        boolean[] hit = matches(level, pos);
        for (Trigram trigram : PRIORITY) {
            if (hit[trigram.ordinal()]) {
                return trigram;
            }
        }
        return Trigram.NONE;
    }

    /**
     * 判定某处<b>同时满足的<b>全部</b>卦气</b>，按卦序（乾兑离震巽坎艮坤）排列；
     * 一处不凝则给空表。
     *
     * <p>台座上方的卦象单字按这个来画：立在山林又见得到天，就该「巽乾」并见，
     * 而不是只挑一个。</p>
     */
    public static List<Trigram> qiAll(Level level, BlockPos pos) {
        boolean[] hit = matches(level, pos);
        List<Trigram> found = new ArrayList<>(2);
        for (Trigram trigram : Trigram.values()) {
            if (trigram != Trigram.NONE && hit[trigram.ordinal()]) {
                found.add(trigram);
            }
        }
        return found;
    }

    /**
     * 逐条判定八处卦气是否成立（不下优先级）。
     *
     * <p>群系对应（与魔导书一致）：坎=暖水海洋；艮=山地（风袭丘陵/尖峭山峰等）；
     * 巽=各类森林；兑=沼泽；震=恶地（粘土山）；离=绯红/诡异森林。</p>
     */
    private static boolean[] matches(Level level, BlockPos pos) {
        boolean[] hit = new boolean[Trigram.values().length];
        if (level.dimension() == Level.NETHER) {
            // 地狱只认离（两种森林），别的一概不凝
            Holder<Biome> biome = level.getBiome(pos);
            if (biome.is(Biomes.CRIMSON_FOREST) || biome.is(Biomes.WARPED_FOREST)) {
                hit[Trigram.LI.ordinal()] = true;
            }
            return hit;
        }
        Holder<Biome> biome = level.getBiome(pos);
        if (biome.is(Biomes.WARM_OCEAN)) {
            hit[Trigram.KAN.ordinal()] = true;
        }
        if (biome.is(Biomes.WINDSWEPT_HILLS) || biome.is(Biomes.WINDSWEPT_GRAVELLY_HILLS)
                || biome.is(Biomes.JAGGED_PEAKS) || biome.is(Biomes.FROZEN_PEAKS)
                || biome.is(Biomes.STONY_PEAKS) || biome.is(Biomes.SNOWY_SLOPES)) {
            hit[Trigram.GEN.ordinal()] = true;
        }
        if (biome.is(BiomeTags.IS_FOREST) || biome.is(Biomes.CHERRY_GROVE)) {
            hit[Trigram.XUN.ordinal()] = true;
        }
        if (biome.is(Biomes.SWAMP) || biome.is(Biomes.MANGROVE_SWAMP)) {
            hit[Trigram.DUI.ordinal()] = true;
        }
        if (biome.is(Biomes.BADLANDS) || biome.is(Biomes.ERODED_BADLANDS)
                || biome.is(Biomes.WOODED_BADLANDS)) {
            hit[Trigram.ZHEN.ordinal()] = true;
        }
        if (pos.getY() < 10) {
            hit[Trigram.KUN.ordinal()] = true;
        }
        // 乾：头顶再无遮挡即是。★ 不能拿 Level#getHeight(…) 与「本格」的 y 比——
        // 台座自己就占着那一格，高度图给出的正是它之上，比出来永远是假（这就是
        // 「立在开阔地却看不到卦象字」的根因）。改判「台座正上方那一格能不能见天」：
        // 天空光满 15 才算通，树叶、水面、玻璃顶都会挡掉。
        if (level.canSeeSky(pos.above())) {
            hit[Trigram.QIAN.ordinal()] = true;
        }
        return hit;
    }

    /**
     * 统计锚点周围「水平 ±{@code radius} × 竖直 {@code height} 层」之内的<b>卦位石</b>数量——
     * 这是全项目<b>唯一</b>一处「检测八卦石」的入口，注魔门禁（{@link TrigramGate}）与
     * 以后任何要立卦的机器都走它。
     *
     * <p><b>半径与层数怎么读</b>：{@code radius = 2} 即 5×5（连中心共 25 格）；
     * {@code height = 1} 就是「只认中心那一层」，{@code height = 2} 则再往上多认一层。
     * 当前的注魔门禁用的是 {@code (2, 1)}，配上传进来的 {@code center = 基座下面那一格}，
     * 正好是「台座下方那一层 5×5」——卦位石既立，其卦气便与坛场相贯通，
     * 出了这一圈则气不相接，纵有石亦如隔重山。</p>
     *
     * <p><b>不再按「整区块」统计</b>（旧写法）：那样只要在区块任何角落埋一块石头就行，
     * 与「在基座前立卦」的动作毫无关系。现在须得<b>实打实地砌在基座下面</b>：
     * {@code center} 由调用方给出（注魔那边是祭坛中央台座下面那一格），
     * 取单层、水平 ±2 即可。</p>
     *
     * <p><b>只收服务端</b>：数石头这件事只由服务端做（门禁在 {@code startCrafting} 里判、
     * 净界阵在计划刻里判）。曾经为了「让客户端动画跟着节拍一起快」把形参放宽成
     * {@link Level}，那套催炉已经删掉了，于是收回 {@link ServerLevel}——
     * 口径窄一点，以后就不会有人误以为客户端也能拿这条数。</p>
     *
     * @param only 只认这几卦；<b>空集或 {@code null} = 任意卦位石都算</b>
     */
    public static int countTrigramStones(ServerLevel level, BlockPos center,
                                         Set<Trigram> only, int radius, int height) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int layers = Math.max(1, height);
        int reach = Math.max(0, radius);
        int count = 0;
        for (int dy = 0; dy < layers; dy++) {
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    BlockState state = level.getBlockState(
                            cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz));
                    Trigram trigram = ModBlocks.trigramOf(state.getBlock());
                    if (trigram == Trigram.NONE) {
                        continue;
                    }
                    if (only == null || only.isEmpty() || only.contains(trigram)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /**
     * 数一数锚点周围<b>出现了哪几种</b>卦位石（同种多块只记一次）。
     *
     * <p>与 {@link #countTrigramStones} 的区别只在「去不去重」：那个数的是<b>块数</b>，
     * 这个数的是<b>种类</b>。门禁里「八种全立」那一档要的就是种类——
     * 堆八块坎石不算立了八卦。</p>
     *
     * <p>范围口径与 {@code countTrigramStones} 完全一致（同一套 {@code radius × height}），
     * 免得两处对「基座下面多大」各说各话。</p>
     */
    public static Set<Trigram> trigramsAt(ServerLevel level, BlockPos center, int radius, int height) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int layers = Math.max(1, height);
        int reach = Math.max(0, radius);
        Set<Trigram> found = EnumSet.noneOf(Trigram.class);
        for (int dy = 0; dy < layers; dy++) {
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    BlockState state = level.getBlockState(
                            cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz));
                    Trigram trigram = ModBlocks.trigramOf(state.getBlock());
                    if (trigram != Trigram.NONE) {
                        found.add(trigram);
                    }
                }
            }
        }
        return found;
    }

    /**
     * 卦象的英文标识（语言键与贴图名用），也就是八种卦气各自对应的「地方」：
     * {@code qian} 开阔天空、{@code dui} 沼泽、{@code li} 地狱森林、{@code zhen} 粘土山、
     * {@code xun} 森林、{@code kan} 暖水海洋、{@code gen} 山地、{@code kun} 至深地底。
     */
    public static String id(Trigram trigram) {
        return switch (trigram) {
            case QIAN -> "qian";
            case DUI -> "dui";
            case LI -> "li";
            case ZHEN -> "zhen";
            case XUN -> "xun";
            case KAN -> "kan";
            case GEN -> "gen";
            case KUN -> "kun";
            default -> null;
        };
    }

    /** 卦象的单字名（如「乾」）；未凝卦气时返回空文本。 */
    public static Component displayName(Trigram trigram) {
        String id = id(trigram);
        return id == null ? Component.empty()
                : Component.translatable("skydeitymysticpivot.trigram." + id);
    }

    /** 卦象的象色（与魔导书所述「通体一色」一致），ARGB。 */
    public static int color(Trigram trigram) {
        return switch (trigram) {
            case QIAN -> 0xFFE0B84A;    // 鎏金
            case DUI -> 0xFFDCE3EA;     // 银白
            case LI -> 0xFFE24B33;      // 朱赤
            case ZHEN -> 0xFF3CB371;    // 青碧
            case XUN -> 0xFF2F8C86;     // 青苍
            case KAN -> 0xFF3B5FC4;     // 靛蓝
            case GEN -> 0xFF9A7E58;     // 岩褐
            case KUN -> 0xFFC9A24B;     // 土黄
            default -> 0xFFFFFFFF;
        };
    }

    private Fengshui() {
    }
}
