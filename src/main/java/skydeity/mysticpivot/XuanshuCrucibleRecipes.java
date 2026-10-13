package skydeity.mysticpivot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 「本体坩埚配方的要素索引」：本体重载数据包时，把<b>所有</b> {@code thaumcraft:crucible}
 * 配方里的要素表抄一份下来。
 *
 * <h2>为什么需要它</h2>
 * <p>本体那张坩埚配方（{@code dev.tc4port.thaumcraft.recipe.CrucibleRecipe}）住在<b>实现包</b>里，
 * 不在公开 api jar 里；本项目的口径是只依赖 {@code dev.tc4port.thaumcraft.api.**}，
 * 所以它的 {@code aspects()} 我们<b>调不到</b>。</p>
 * <p>但「匹配哪张配方」这件事完全可以交给本体自己——{@link net.minecraft.world.item.crafting.RecipeManager#getRecipeFor}
 * 拿配方<b>类型 id</b> 就能查（配方类型是登记表里的普通对象，拿 id 取得到），
 * 匹配、产物都走本体的实现，唯独要素要另想办法。</p>
 * <p>于是这里只做一件事：重载时把 {@code recipe/} 与 {@code recipes/} 两个目录下所有 JSON 读一遍，
 * 凡是 {@code "type": "thaumcraft:crucible"} 的，就把它的 {@code aspects} 抄下来，
 * 按<b>配方 id</b> 存成一张表。运行时查到哪张配方，就按 id 来这里取要素。</p>
 *
 * <h2>为什么能涵盖所有附属</h2>
 * <p>扫的是<b>资源包/数据包里的原始 JSON</b>，不是本体的配方对象表——
 * 凡是把 {@code type} 写成 {@code thaumcraft:crucible} 的配方（本体自带的、别的附属写的、
 * 数据包自己加的、KubeJS 生成的）都会出现在这里，一个不漏。</p>
 *
 * <p>只在<b>服务端</b>挂（{@code AddReloadListenerEvent}）：要素表是给方块实体做匹配用的，
 * 界面上的要素需求行由服务端把结果同步下去，客户端不需要这index。</p>
 */
public final class XuanshuCrucibleRecipes implements PreparableReloadListener {

    /** 本体坩埚配方的 {@code type} 值——附属只要照写这个就会被收进来。 */
    private static final ResourceLocation PARENT_TYPE =
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "crucible");

    /** 配方目录：数据包里 {@code recipe/} 与 {@code recipes/} 两种写法都合法。 */
    private static final String[] ROOTS = {"recipe", "recipes"};

    /** 重载监听器本体（服务端事件里注册这一个就够了）。 */
    public static final XuanshuCrucibleRecipes INSTANCE = new XuanshuCrucibleRecipes();

    /** 配方 id → 所需要素。重载时整体换掉（volatile：读发生在别的线程上）。 */
    private static volatile Map<ResourceLocation, Map<AspectId, Integer>> index = Map.of();

    private XuanshuCrucibleRecipes() {
    }

    /** 某张配方的所需要素；索引里没有（或这张配方压根不是坩埚配方）就给空表。 */
    public static Map<AspectId, Integer> aspectsOf(ResourceLocation recipeId) {
        return index.getOrDefault(recipeId, Map.of());
    }

    /** 索引的条目数（调试/日志用）。 */
    public static int size() {
        return index.size();
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
                    index = scanned;
                    MysticPivot.LOGGER.debug("Indexed {} thaumcraft:crucible recipes for the xuanshu crucible",
                            scanned.size());
                }, gameExecutor);
    }

    /** 读两个配方目录下的全部 JSON，拣出本体类型的配方。 */
    private static Map<ResourceLocation, Map<AspectId, Integer>> scan(ResourceManager manager) {
        Map<ResourceLocation, Map<AspectId, Integer>> found = new HashMap<>();
        for (String root : ROOTS) {
            Map<ResourceLocation, Resource> files =
                    manager.listResources(root, location -> location.getPath().endsWith(".json"));
            files.forEach((location, resource) -> {
                ResourceLocation recipeId = recipeId(root, location);
                if (recipeId == null) {
                    return;
                }
                try (Reader reader = resource.openAsReader()) {
                    JsonElement parsed = JsonParser.parseReader(reader);
                    if (!parsed.isJsonObject()) {
                        return;
                    }
                    JsonObject json = parsed.getAsJsonObject();
                    JsonElement type = json.get("type");
                    if (type == null || !PARENT_TYPE.toString().equals(type.getAsString())) {
                        return;
                    }
                    Map<AspectId, Integer> aspects = readAspects(json.get("aspects"));
                    if (!aspects.isEmpty()) {
                        found.put(recipeId, aspects);
                    }
                } catch (Exception ignored) {
                    // 单个坏文件不该拖垮整次重载：跳过即可
                }
            });
        }
        return found;
    }

    /** {@code recipe/crucible/nitor.json} → {@code thaumcraft:crucible/nitor}。 */
    private static ResourceLocation recipeId(String root, ResourceLocation location) {
        String path = location.getPath();
        String prefix = root + "/";
        if (!path.startsWith(prefix) || !path.endsWith(".json")) {
            return null;
        }
        String trimmed = path.substring(prefix.length(), path.length() - ".json".length());
        return ResourceLocation.fromNamespaceAndPath(location.getNamespace(), trimmed);
    }

    /** {@code {"ignis": 3, "thaumcraft:terra": 1}} → 要素表（紧凑名与全名两种写法都收）。 */
    private static Map<AspectId, Integer> readAspects(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return Map.of();
        }
        Map<AspectId, Integer> aspects = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            try {
                int amount = entry.getValue().getAsInt();
                if (amount > 0) {
                    aspects.merge(AspectId.parse(key.trim()), amount, Integer::sum);
                }
            } catch (RuntimeException ignored) {
                // 认不出的键（或不是数字）跳过
            }
        }
        return aspects;
    }
}
