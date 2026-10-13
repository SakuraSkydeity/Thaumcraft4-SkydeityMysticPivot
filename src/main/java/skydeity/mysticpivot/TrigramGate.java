package skydeity.mysticpivot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import skydeity.mysticpivot.Fengshui.Trigram;

import javax.annotation.Nullable;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 「卦象门禁」总表：<b>哪条注魔配方要求坛边立起哪几种卦位石、要几块、要多大范围、
 * 多出来的石头能压多少不稳</b>——全部由数据包里的 JSON 说了算，代码里不再写死配方清单。
 *
 * <h2>为什么要有这张表</h2>
 * <p>门禁一开始是写在 Mixin 里的两条常量（玄枢杖柄 / 玄枢杖端），可后续要立卦的配方只会越来越多，
 * 每加一条就改一次 Java、编译一次，既啰嗦又容易和配方本体脱节。改成表之后，
 * <b>新增一条门禁 = 在数据包里加一行</b>，附属与整合包也能自己加。</p>
 *
 * <h2>文件位置与写法</h2>
 * <p>数据包路径 {@code data/<命名空间>/mysticpivot/trigram_gate/*.json}，
 * 每个文件都是「配方 id → 要求」的一张表（<b>多个文件会合并</b>，所以附属可以各写各的）：</p>
 * <pre>{@code
 * {
 *   "skydeitymysticpivot:onyx_wand_rod": {
 *     "trigram": "kan",     // 或 ["kan","qian"]（任一即可）、"any"（任意卦位石都算）、"坎"
 *     "mode": "count",      // count（默认）= 凑够 min 块即可；all = 列出的每一种都要出现
 *     "min": 1,             // 至少要几块，默认 1（mode=all 时忽略，改成「每种各一块」）
 *     "radius": 2,          // 水平半径：2 = 5×5，1 = 3×3，默认 2
 *     "height": 1,          // 竖直层数（自中心那层往上数），默认 1
 *     "relief": 15,         // 每多出一块压多少不稳，默认 0（不压）
 *     "relief_max": 3       // 至多按几块「多出来的」算，默认 0
 *   }
 * }</pre>
 *
 * <h2>两种模式的区别</h2>
 * <p><b>{@code count}</b>：只数块数，凑够 {@code min} 块就行——「堆两枚坎石」也算立卦。
 * <b>{@code all}</b>：列出的每一种卦都必须<b>至少出现一次</b>（按种类去重），堆同一种不算数——
 * 「凑齐八卦」「四象四卦」用这一档。</p>
 *
 * <h2>判定中心</h2>
 * <p>中心由调用方给：注魔那一处取<b>祭坛中央台座正下方那一格</b>
 * （本体 {@code centralPedestal()} 是 {@code worldPosition.below(2)}，再下一格即是坛脚），
 * 于是 {@code radius=2, height=1} 正好是「台座下方那一层 5×5」。</p>
 *
 * <p>与 {@link XuanshuCrucibleRecipes} 一样只在服务端挂：门禁要读方块、改不稳度，纯服务端逻辑，
 * 客户端不需要这份表。</p>
 */
public final class TrigramGate implements PreparableReloadListener {

    /** 数据包目录（{@code data/<ns>/} 之后的那一段）。 */
    private static final String ROOT = "mysticpivot/trigram_gate";

    /** 重载监听器本体。 */
    public static final TrigramGate INSTANCE = new TrigramGate();

    /** 配方 id → 门禁要求。重载时整体换掉（volatile：读发生在别的线程上）。 */
    private static volatile Map<ResourceLocation, Requirement> requirements = Map.of();

    private TrigramGate() {
    }

    /** 判定方式：凑数（{@code count}）还是凑齐种类（{@code all}）。 */
    public enum Mode {
        /** 数够 {@code min} 块即可（默认）。 */
        COUNT,
        /** 列出的每一种卦都要至少出现一次。 */
        ALL
    }

    /**
     * 一条门禁要求。
     *
     * @param mode      判定方式（{@link Mode#COUNT} / {@link Mode#ALL}）
     * @param trigrams  认哪些卦；<b>空集 = 任意卦位石都算</b>（JSON 里写 {@code "any"}）
     * @param min       至少要有几块才算「立了卦」（{@code mode=all} 时不用）
     * @param radius    水平半径（格）
     * @param height    竖直层数（自中心那层往上）
     * @param relief    每多出一块压多少不稳
     * @param reliefMax 「多出来的」至多按几块算（封顶）
     */
    public record Requirement(Mode mode, Set<Trigram> trigrams, int min, int radius,
                              int height, int relief, int reliefMax) {

        /** 这一块卦位石算不算数。 */
        public boolean accepts(Trigram trigram) {
            return trigrams.isEmpty() || trigrams.contains(trigram);
        }
    }

    /**
     * 这条要求是否已经满足。
     *
     * @param stones 已数到的<b>块数</b>（{@link #count} 的结果）
     * @param kinds  已出现的<b>种类</b>（{@link Fengshui#trigramsAt} 的结果）
     */
    public static boolean satisfied(Requirement requirement, int stones, Set<Trigram> kinds) {
        if (requirement.mode() == Mode.ALL) {
            return kinds.containsAll(requirement.trigrams());
        }
        return stones >= requirement.min();
    }

    /**
     * 还差哪几卦（要求里列了、但坛边还没出现的）。
     *
     * <p>按要求的书写顺序给出（{@code LinkedHashSet}），好让提示里「坎、艮」的顺序稳定；
     * {@code any} 模式无从点名，返回空集，调用方改说「缺少卦石」。</p>
     */
    public static Set<Trigram> missing(Requirement requirement, Set<Trigram> kinds) {
        Set<Trigram> need = new LinkedHashSet<>(requirement.trigrams());
        need.removeAll(kinds);
        return need;
    }

    /**
     * 物料不够时那条动作栏提示（显示在快捷栏正上方）。
     *
     * <p>能点名就写「缺少卦石：坎、艮」，点不出名（{@code any} 模式，或只是数量不够）
     * 就退化成「缺少卦石」。</p>
     */
    public static Component missingMessage(Set<Trigram> missing) {
        if (missing.isEmpty()) {
            return Component.translatable("skydeitymysticpivot.gate.missing_any")
                    .withStyle(ChatFormatting.RED);
        }
        MutableComponent names = Component.empty();
        boolean first = true;
        for (Trigram trigram : missing) {
            if (!first) {
                names.append(Component.translatable("skydeitymysticpivot.gate.sep"));
            }
            names.append(Fengshui.displayName(trigram));
            first = false;
        }
        return Component.translatable("skydeitymysticpivot.gate.missing", names)
                .withStyle(ChatFormatting.RED);
    }

    /** 某条配方的门禁要求；表里没有（= 这条配方不用立卦）就给 {@code null}。 */
    @Nullable
    public static Requirement forRecipe(ResourceLocation recipeId) {
        return recipeId == null ? null : requirements.get(recipeId);
    }

    /** 表里的条目数（调试/日志用）。 */
    public static int size() {
        return requirements.size();
    }

    /**
     * 数一数这条要求在那个位置能认到几块卦位石。
     *
     * <p>调用方传的 {@code center} 通常已经是「台座正下方那一格」，
     * 所以这里的 {@code height=1} 就是「只认那一层」。</p>
     */
    public static int count(ServerLevel level, BlockPos center, Requirement requirement) {
        return Fengshui.countTrigramStones(level, center, requirement.trigrams(),
                requirement.radius(), requirement.height());
    }

    /**
     * 按「多出来的石头」算出该压多少不稳：{@code relief × min(块数 − 基准数, reliefMax)}。
     *
     * <p>基准数就是「刚好够用」的块数：{@code count} 模式取 {@code min}，
     * {@code all} 模式取所列卦数（八种全立就是 8）。刚好够数（或不够）时给 0——
     * 刚够数只是能开工，不额外镇压。</p>
     */
    public static int reliefOf(int stones, Requirement requirement) {
        if (requirement.relief() <= 0 || requirement.reliefMax() <= 0) {
            return 0;
        }
        int base = requirement.mode() == Mode.ALL
                ? requirement.trigrams().size() : requirement.min();
        int extra = Math.min(Math.max(stones - base, 0), requirement.reliefMax());
        return requirement.relief() * extra;
    }

    // ------------------------------------------------------------------
    // 重载
    // ------------------------------------------------------------------

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager manager,
                                          ProfilerFiller preparationsProfiler, ProfilerFiller reloadProfiler,
                                          Executor backgroundExecutor, Executor gameExecutor) {
        return CompletableFuture.supplyAsync(() -> scan(manager), backgroundExecutor)
                .thenCompose(barrier::wait)
                .thenAcceptAsync(scanned -> {
                    requirements = scanned;
                    MysticPivot.LOGGER.debug("Indexed {} trigram gate rules", scanned.size());
                }, gameExecutor);
    }

    /** 读目录下全部 JSON，合并成一张表（后读到的同名条目覆盖先读到的）。 */
    private static Map<ResourceLocation, Requirement> scan(ResourceManager manager) {
        Map<ResourceLocation, Requirement> found = new HashMap<>();
        Map<ResourceLocation, Resource> files =
                manager.listResources(ROOT, location -> location.getPath().endsWith(".json"));
        files.forEach((location, resource) -> {
            try (Reader reader = resource.openAsReader()) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (!parsed.isJsonObject()) {
                    return;
                }
                for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
                    if (!entry.getValue().isJsonObject()) {
                        continue;
                    }
                    ResourceLocation recipeId = ResourceLocation.tryParse(entry.getKey());
                    if (recipeId == null) {
                        MysticPivot.LOGGER.warn("Trigram gate: bad recipe id '{}' in {}",
                                entry.getKey(), location);
                        continue;
                    }
                    try {
                        found.put(recipeId, parse(entry.getValue().getAsJsonObject()));
                    } catch (RuntimeException ex) {
                        // 单条坏条目不该拖垮整次重载
                        MysticPivot.LOGGER.warn("Trigram gate: bad entry for {} in {}: {}",
                                recipeId, location, ex.toString());
                    }
                }
            } catch (Exception ignored) {
                // 单个坏文件同理：跳过
            }
        });
        return found;
    }

    private static Requirement parse(JsonObject json) {
        Set<Trigram> trigrams = new LinkedHashSet<>();
        JsonElement raw = json.has("trigram") ? json.get("trigram") : json.get("trigrams");
        if (raw != null) {
            if (raw.isJsonArray()) {
                for (JsonElement element : raw.getAsJsonArray()) {
                    readTrigram(trigrams, element.getAsString());
                }
            } else {
                readTrigram(trigrams, raw.getAsString());
            }
        }
        int min = intOr(json, "min", 1);
        int radius = intOr(json, "radius", 2);
        int height = intOr(json, "height", 1);
        int relief = intOr(json, "relief", 0);
        int reliefMax = json.has("relief_max") ? intOr(json, "relief_max", 0) : intOr(json, "reliefMax", 0);
        Mode mode = "all".equalsIgnoreCase(stringOr(json, "mode", "count")) ? Mode.ALL : Mode.COUNT;
        // ★ 用 LinkedHashSet 而不是 Set.copyOf：后者迭代序未定义，
        //   而「缺少卦石：坎、艮」这类提示要按 JSON 里书的顺序排。
        Set<Trigram> ordered = Collections.unmodifiableSet(new LinkedHashSet<>(trigrams));
        return new Requirement(mode, ordered, Math.max(1, min), Math.max(0, radius),
                Math.max(1, height), Math.max(0, relief), Math.max(0, reliefMax));
    }

    /** {@code "kan"} / {@code "坎"} / {@code "any"} 都收。 */
    private static void readTrigram(Set<Trigram> sink, String name) {
        String key = name == null ? "" : name.trim();
        if (key.isEmpty()) {
            return;
        }
        if (key.equalsIgnoreCase("any") || key.equals("*")) {
            sink.clear();
            return;
        }
        sink.add(switch (key) {
            case "乾" -> Trigram.QIAN;
            case "兑" -> Trigram.DUI;
            case "离" -> Trigram.LI;
            case "震" -> Trigram.ZHEN;
            case "巽" -> Trigram.XUN;
            case "坎" -> Trigram.KAN;
            case "艮" -> Trigram.GEN;
            case "坤" -> Trigram.KUN;
            default -> Trigram.valueOf(key.toUpperCase(Locale.ROOT));
        });
    }

    private static int intOr(JsonObject json, String key, int fallback) {
        JsonElement element = json.get(key);
        return element == null ? fallback : element.getAsInt();
    }

    private static String stringOr(JsonObject json, String key, String fallback) {
        JsonElement element = json.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsString();
    }
}
