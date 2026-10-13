package skydeity.mysticpivot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectDefinition;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 玄枢坩埚的配方：<b>一件触媒 + 若干要素 → 一件产物</b>。
 *
 * <p>字段与本体 {@code thaumcraft:crucible} 逐一对应（{@code catalyst} / {@code result} /
 * {@code aspects}），所以本体的坩埚配方可以原样搬过来、只把 {@code type} 一改即可
 * （本模组的配方目录下就是这么整批生成的）。唯一没搬的是 {@code research}：
 * 玄枢坩埚<b>不认研究进度</b>——本体要你先解锁对应研究才能下锅，
 * 这台机器只认「料齐不齐」。</p>
 *
 * <h2>为什么不直接读本体那张配方表</h2>
 * <p>本体 {@code crucible} 配方类住在实现包 {@code dev.tc4port.thaumcraft.recipe}，
 * 不在公开 api jar 里；本项目的口径是<b>只依赖 {@code dev.tc4port.thaumcraft.api.**}</b>，
 * 不许碰实现包、更不许反射。所以自建一个同构的类型，
 * 由数据包承载配方（见 {@code data/skydeitymysticpivot/recipe/xuanshu_crucible/}）。</p>
 *
 * <h2>但「只认自己的那 54 条」是不够的（重要）</h2>
 * <p>自建类型只覆盖「照抄过来的本体配方」，<b>别的附属写的坩埚配方</b>仍旧做不出来——
 * 它们的 {@code type} 是 {@code thaumcraft:crucible}，压根不在我们这张表里。
 * 而「配方对象」本身其实<b>查得到</b>：配方类型是登记表里的普通对象，
 * 拿 id 取出 {@code RecipeType} 就能让 {@link net.minecraft.world.item.crafting.RecipeManager}
 * 用它自己的匹配逻辑（触媒、产物全由本体的实现负责）——唯独 {@code aspects()}
 * 调不到（类在实现包里）。</p>
 * <p>所以 {@link #find} 走<b>两条路</b>：</p>
 * <ol>
 *   <li>先查本模组自己的类型（数据包想直写要素就走这条）；</li>
 *   <li>查不到再按 {@code thaumcraft:crucible} 这个类型 id 让本体去匹配，
 *       匹配到哪张配方，就按它的<b>配方 id</b> 去 {@link XuanshuCrucibleRecipes}
 *       那张重载时抄下来的要素索引里取要素（取不到就现场把配方编回 JSON 再抄一次，
 *       见 {@link #parentAspects}）。</li>
 * </ol>
 * <p>于是本体自带的、其他附属写的、数据包与 KubeJS 生成的坩埚配方<b>全都能用</b>，
 * 而且匹配语义与本体完全一致（我们只负责补上「需要哪些要素」这一块）。
 * 配方查看器那一页也照同一份数据列（见 {@link #allParentRecipes}）。</p>
 */
public final class XuanshuCrucibleRecipe implements Recipe<SingleRecipeInput> {

    /** 触媒：下锅的那一件东西（其余要素由机器自己去原质管里抽）。 */
    private final Ingredient catalyst;

    /** 产物。 */
    private final ItemStack result;

    /** 所需要素：id → 份数。 */
    private final Map<AspectId, Integer> aspects;

    public XuanshuCrucibleRecipe(Ingredient catalyst, ItemStack result, Map<AspectId, Integer> aspects) {
        this.catalyst = catalyst == null ? Ingredient.EMPTY : catalyst;
        this.result = result == null ? ItemStack.EMPTY : result.copy();
        // 保持数据包里的书写顺序：抽要素时按这份顺序取源，轨迹动画的先后才稳定可预期
        this.aspects = Collections.unmodifiableMap(
                new LinkedHashMap<>(aspects == null ? Map.of() : aspects));
    }

    public Ingredient catalyst() {
        return this.catalyst;
    }

    /** 产物的一份拷贝（配方查看器展示用）。 */
    public ItemStack result() {
        return this.result.copy();
    }

    /** 某件物品能不能当本配方的触媒。 */
    public boolean catalystMatches(ItemStack stack) {
        return !stack.isEmpty() && this.catalyst.test(stack);
    }

    /** 所需要素（只读）。 */
    public Map<AspectId, Integer> aspects() {
        return this.aspects;
    }

    /** 某要素的要素色（图标取色用）；要素表里查不到就给白。 */
    public static int colorOf(AspectId aspect) {
        AspectDefinition definition = AspectApi.get(aspect);
        return definition == null ? 0xFFFFFF : definition.color();
    }

    /** 本体坩埚配方的类型 id（附属写的也是这一个）。 */
    private static final ResourceLocation PARENT_TYPE =
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "crucible");

    /**
     * 在当前世界的配方表里找一件物品对应的坩埚配方。
     *
     * <p>先查本模组自己的 {@code xuanshu_crucible}（数据包可以直写要素），
     * 再查本体的 {@code thaumcraft:crucible}——后者是<b>让本体自己去匹配</b>的，
     * 要素从 {@link #parentAspects} 补上，所以<b>附属写的坩埚配方同样做得出</b>。</p>
     *
     * <p>没有世界、配方表还没就绪、或要素一点都取不到时返回 {@code null}。</p>
     */
    @Nullable
    public static XuanshuCrucibleRecipe find(@Nullable Level level, ItemStack input) {
        if (level == null || input.isEmpty()) {
            return null;
        }
        SingleRecipeInput query = new SingleRecipeInput(input);
        XuanshuCrucibleRecipe own = level.getRecipeManager()
                .getRecipeFor(ModRecipes.XUANSHU_CRUCIBLE_TYPE.get(), query, level)
                .map(RecipeHolder::value)
                .orElse(null);
        if (own != null) {
            return own;
        }
        return fromParentType(level, input);
    }

    /** 按 {@code thaumcraft:crucible} 这个类型 id 让本体匹配，再补上要素表。 */
    @Nullable
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static XuanshuCrucibleRecipe fromParentType(Level level, ItemStack input) {
        RecipeHolder<?> holder = parentMatch(level, input);
        if (holder == null) {
            return null;
        }
        Map<AspectId, Integer> aspects = parentAspects(holder, level.registryAccess());
        if (aspects.isEmpty()) {
            return null; // 认不出需要什么要素 → 宁可不做
        }
        return wrap(holder, aspects, level.registryAccess());
    }

    /**
     * 在 {@code thaumcraft:crucible} 这张表里按触媒匹配一张配方。
     *
     * <p>泛型在这里帮不上忙：类型是运行时按 id 取出来的，参数化已经擦除；
     * 用裸类型查就是本意——匹配逻辑全是配方自己的。</p>
     */
    @Nullable
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RecipeHolder<?> parentMatch(Level level, ItemStack input) {
        RecipeType<?> type = parentType();
        if (type == null) {
            return null; // 没装本体
        }
        Optional<RecipeHolder> found = level.getRecipeManager()
                .getRecipeFor((RecipeType) type, new SingleRecipeInput(input), level);
        return found.orElse(null);
    }

    /** 配方类型对象本身（登记表里按 id 取，所以不必碰实现包）。 */
    @Nullable
    @SuppressWarnings("rawtypes")
    private static RecipeType<?> parentType() {
        return BuiltInRegistries.RECIPE_TYPE.get(PARENT_TYPE);
    }

    /**
     * 把配方表里<b>所有</b> {@code thaumcraft:crucible} 配方包成本模组的同构对象。
     *
     * <p>给配方查看器（JEI / EMI）用：玄枢坩埚这一页要列的是「它能做的全部」，
     * 自然要把本体自带的、别的附属写的、数据包与 KubeJS 生成的那些一并摆上——
     * 它们在配方表里的类型是 {@code thaumcraft:crucible}，运行时 {@link #find} 也认，
     * 只是不在本模组自己那张类型表里。★ 本体<b>没有</b> EMI 适配，
     * 所以这一页在 EMI 里是那些配方唯一的去处。</p>
     *
     * <p>要素一点都取不到的配方会被跳过：宁可这页少一条，
     * 也不摆一条「不知道要吃什么要素」的假配方。</p>
     */
    public static List<RecipeHolder<XuanshuCrucibleRecipe>> allParentRecipes(@Nullable RecipeManager manager,
                                                                            @Nullable RegistryAccess registries) {
        if (manager == null) {
            return List.of();
        }
        RecipeType<?> type = parentType();
        if (type == null) {
            return List.of(); // 没装本体
        }
        List<RecipeHolder<XuanshuCrucibleRecipe>> out = new ArrayList<>();
        for (RecipeHolder<?> holder : parentHolders(manager, type)) {
            Map<AspectId, Integer> aspects = parentAspects(holder, registries);
            if (aspects.isEmpty()) {
                continue;
            }
            out.add(new RecipeHolder<>(holder.id(), wrap(holder, aspects, registries)));
        }
        return out;
    }

    /** 配方表里 {@code thaumcraft:crucible} 那一批（泛型按 id 取类型时擦除了，这里补一刀）。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<RecipeHolder<?>> parentHolders(RecipeManager manager, RecipeType<?> type) {
        return (List<RecipeHolder<?>>) (List<?>) manager.getAllRecipesFor((RecipeType) type);
    }

    /**
     * 配方查看器那一页要列的<b>全部</b>配方：本模组自己的 + 本体类型的，且已<b>去重</b>。
     *
     * <h2>为什么要去重</h2>
     * <p>本模组自己的那 54 条是把本体 54 条<b>照抄</b>过来的（只改了 {@code type}），
     * 于是本体那批会以 {@code thaumcraft:crucible} 的形式再出现一次，同一条配方摆两遍。
     * 这里用 {@link #signature()}（触媒可选集 + 产物）当指纹：先收本模组自己的，
     * 本体那批里指纹重复的直接跳过。</p>
     *
     * <p>顺序有意义：本模组自己的排在前面（它们没有研究门槛，是「稳定能用的那一份」）。</p>
     */
    public static List<RecipeHolder<XuanshuCrucibleRecipe>> displayRecipes(@Nullable RecipeManager manager,
                                                                          @Nullable RegistryAccess registries) {
        if (manager == null) {
            return List.of();
        }
        List<RecipeHolder<XuanshuCrucibleRecipe>> own =
                manager.getAllRecipesFor(ModRecipes.XUANSHU_CRUCIBLE_TYPE.get());
        List<RecipeHolder<XuanshuCrucibleRecipe>> out = new ArrayList<>(own);
        Set<String> seen = new HashSet<>();
        for (RecipeHolder<XuanshuCrucibleRecipe> holder : own) {
            seen.add(holder.value().signature());
        }
        for (RecipeHolder<XuanshuCrucibleRecipe> holder : allParentRecipes(manager, registries)) {
            if (seen.add(holder.value().signature())) {
                out.add(holder);
            }
        }
        return out;
    }

    /**
     * 配方的「长相」指纹：<b>触媒能接受哪些物品 + 产物（种类与数量）</b>。
     *
     * <p>不看配方 id（id 一定不同），也不看要素（解析度不同——{@code thaumcraft:ignis}
     * 与 {@code ignis} 指的是同一样），所以只能拿「锅里放什么、出什么」来认。</p>
     */
    public String signature() {
        StringBuilder builder = new StringBuilder();
        builder.append(BuiltInRegistries.ITEM.getKey(this.result.getItem())).append('x')
                .append(this.result.getCount()).append('|');
        List<String> items = new ArrayList<>();
        for (ItemStack stack : this.catalyst.getItems()) {
            items.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        Collections.sort(items);
        return builder.append(String.join(",", items)).toString();
    }

    /** 一张本体类型坩埚配方的需要素：触媒与产物走原版 {@link Recipe} 接口，要素另想办法。 */
    private static XuanshuCrucibleRecipe wrap(RecipeHolder<?> holder, Map<AspectId, Integer> aspects,
                                              @Nullable HolderLookup.Provider registries) {
        NonNullList<Ingredient> ingredients = holder.value().getIngredients();
        Ingredient catalyst = ingredients.isEmpty() ? Ingredient.EMPTY : ingredients.get(0);
        ItemStack result = registries == null ? ItemStack.EMPTY : holder.value().getResultItem(registries);
        return new XuanshuCrucibleRecipe(catalyst, result, aspects);
    }

    /**
     * 求一张 {@code thaumcraft:crucible} 配方需要哪些要素。
     *
     * <p>两条路，先快后稳：</p>
     * <ol>
     *   <li><b>重载时抄下来的索引</b>（{@link XuanshuCrucibleRecipes}）：服务端数据包重载时扫一遍
     *       所有 {@code "type": "thaumcraft:crucible"} 的 JSON，运行时按配方 id O(1) 取。
     *       {@code find} 是每 tick 都可能问一次的，走这条不花钱。</li>
     *   <li><b>把配方用它自己的序列化器编回 JSON</b>（{@link #encodedAspects}）：
     *       配方对象里本来就装着要素，而「序列化」是公开 API ——{@code RecipeSerializer#codec()}。
     *       这条路两端都通，<b>客户端 JEI / EMI 就是靠它拿到要素的</b>
     *       （那边服务端那张索引不存在，也没必要存在）。</li>
     * </ol>
     */
    private static Map<AspectId, Integer> parentAspects(RecipeHolder<?> holder, @Nullable RegistryAccess registries) {
        Map<AspectId, Integer> indexed = XuanshuCrucibleRecipes.aspectsOf(holder.id());
        if (!indexed.isEmpty()) {
            return indexed;
        }
        return registries == null ? Map.of() : encodedAspects(holder.value(), registries);
    }

    /**
     * 把配方编回 JSON，再抄出其中的 {@code aspects}。
     *
     * <p>依据是原版读配方的那一行（{@code RecipeManager#fromJson}）：
     * {@code Recipe.CODEC.parse(registryAccess.createSerializationContext(JsonOps.INSTANCE), json)}。
     * 编解码是对称的，所以「要素」这一栏能原样编回来——本体的序列化器写的就是紧凑名
     * （{@code "ignis": 3}），{@link AspectId#parse} 认这种写法。</p>
     *
     * <p>编不出来（哪个附属换了序列化器、注册表不全、或压根没有 {@code aspects} 这栏）
     * 就当没有要素，回空表——调用方据此跳过这张配方，不会拿半个配方去烧。</p>
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Map<AspectId, Integer> encodedAspects(Recipe<?> recipe, RegistryAccess registries) {
        try {
            Codec codec = recipe.getSerializer().codec().codec();
            DynamicOps<JsonElement> ops = registries.createSerializationContext(JsonOps.INSTANCE);
            Object encoded = codec.encodeStart(ops, recipe).result().orElse(null);
            if (!(encoded instanceof JsonObject json)) {
                return Map.of();
            }
            return readAspects(json.get("aspects"));
        } catch (RuntimeException exception) {
            // 编不出来不是错，是「这张配方我们认不出要素」——交回调用方跳过即可
            return Map.of();
        }
    }

    /** JSON 里的 {@code aspects} 对象 → 要素表（紧凑名与全名两种写法都收）。 */
    public static Map<AspectId, Integer> readAspects(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return Map.of();
        }
        Map<String, Integer> raw = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
            try {
                raw.put(entry.getKey(), entry.getValue().getAsInt());
            } catch (RuntimeException ignored) {
                // 不是数字的项跳过
            }
        }
        return decodeAspects(raw);
    }

    // ------------------------------------------------------------------
    // Recipe
    // ------------------------------------------------------------------

    @Override
    public boolean matches(SingleRecipeInput input, Level level) {
        return this.catalystMatches(input.item());
    }

    @Override
    public ItemStack assemble(SingleRecipeInput input, HolderLookup.Provider registries) {
        return this.result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return this.result.copy();
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return NonNullList.of(Ingredient.EMPTY, this.catalyst);
    }

    /**
     * {@code isSpecial()} 回 {@code true}——<b>只为甩掉配方书的分类</b>。
     *
     * <p>先说清楚它<b>管什么</b>：本体那个 {@code CrucibleRecipe} 里它就是 {@code true}，
     * 而它的作用范围只有<b>配方书</b>（解锁、配方书分类、合成提示，见 {@code ServerRecipeBook#addRecipes}
     * 与 {@code ClientRecipeBook#categorizeAndGroupRecipes}）。配方<b>不是</b>靠它决定要不要下发的——
     * 下发走的是 {@code PlayerList} 里那句
     * {@code new ClientboundUpdateRecipesPacket(server.getRecipeManager().getOrderedRecipes())}，
     * 而 {@code getOrderedRecipes()} 就是全量；{@code getRecipeFor} 也压根不看它。</p>
     *
     * <p>★ 回 {@code true} 的直接收益：{@code categorizeAndGroupRecipes} 的第一句是
     * {@code if (!recipe.isSpecial() && !recipe.isIncomplete())}，
     * 我们这种「自带类型、又没向 {@code RecipeBookManager} 登记分类」的配方，
     * 每条都会在日志里刷一行 {@code Unknown recipe category}（本模组实测 52 条）。</p>
     *
     * <p>不影响 JEI / EMI：它们从 {@code RecipeManager#getAllRecipesFor} 取，与 {@code isSpecial} 无关。</p>
     */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.XUANSHU_CRUCIBLE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.XUANSHU_CRUCIBLE_TYPE.get();
    }

    // ------------------------------------------------------------------
    // 序列化
    // ------------------------------------------------------------------

    /**
     * 要素表的编解码。
     *
     * <p>JSON 里照本体的写法用<b>紧凑名</b>（{@code "ignis": 3}），
     * 解析时统一交 {@link AspectId#parse}——它会给不带命名空间的键补上 {@code thaumcraft:}，
     * 于是本体那种写法与 {@code "thaumcraft:ignis": 3} 两种都能读。
     * 写回（{@code encode}）时只在数据包导出路径上走，用 {@link AspectId#serialized()} 还原紧凑名。</p>
     */
    private static Map<AspectId, Integer> decodeAspects(Map<String, Integer> raw) {
        Map<AspectId, Integer> out = new LinkedHashMap<>();
        raw.forEach((key, amount) -> {
            if (key == null || key.isBlank() || amount == null || amount <= 0) {
                return;
            }
            out.merge(AspectId.parse(key.trim()), amount, Integer::sum);
        });
        return out;
    }

    private static Map<String, Integer> encodeAspects(Map<AspectId, Integer> aspects) {
        Map<String, Integer> out = new LinkedHashMap<>();
        aspects.forEach((aspect, amount) -> out.put(aspect.serialized(), amount));
        return out;
    }

    /** 序列化器（{@code type} 字段写 {@code skydeitymysticpivot:xuanshu_crucible}）。 */
    public static final class Serializer implements RecipeSerializer<XuanshuCrucibleRecipe> {

        private static final MapCodec<XuanshuCrucibleRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Ingredient.CODEC_NONEMPTY.fieldOf("catalyst").forGetter(XuanshuCrucibleRecipe::catalyst),
                        ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result),
                        Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("aspects")
                                .forGetter(recipe -> encodeAspects(recipe.aspects)))
                .apply(instance, (catalyst, result, aspects) ->
                        new XuanshuCrucibleRecipe(catalyst, result, decodeAspects(aspects))));

        /** 网络同构：要素表按「紧凑名 → 份数」的字串表下发（{@code AspectId} 没有自己的 StreamCodec）。 */
        private static final StreamCodec<RegistryFriendlyByteBuf, XuanshuCrucibleRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        Ingredient.CONTENTS_STREAM_CODEC, XuanshuCrucibleRecipe::catalyst,
                        ItemStack.STREAM_CODEC, recipe -> recipe.result,
                        ByteBufCodecs.map(LinkedHashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT),
                        recipe -> encodeAspects(recipe.aspects),
                        (catalyst, result, aspects) ->
                                new XuanshuCrucibleRecipe(catalyst, result, decodeAspects(aspects)));

        @Override
        public MapCodec<XuanshuCrucibleRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, XuanshuCrucibleRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
