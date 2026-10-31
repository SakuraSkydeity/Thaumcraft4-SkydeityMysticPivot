package skydeity.mysticpivot;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SimpleCookingSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 本模组的配方类型。
 *
 * <h2>为什么要单开一个烧炼类型</h2>
 * <p>碎片与盐这类高阶烧炼<b>只许在玄枢炎炉里成</b>，普通熔炉一概烧不出来——这一点靠
 * {@code minecraft:smelting} 是做不到的：凡是用原版烧炼表的机器（原版熔炉、高炉、烟熏炉，
 * 以及一切照着 {@code RecipeType.SMELTING} 找配方的模组机器）都会照着做。
 * 所以另立 {@code skydeitymysticpivot:xuanshu_smelting} 这一类型：
 * 配方<b>不在</b>原版烧炼表里，普通熔炉自然查不到；只有玄枢炎炉会去查它。</p>
 *
 * <h2>为什么继承 {@code AbstractCookingRecipe}</h2>
 * <p>魔导书里的「烧炼」页（{@code ResearchPageDefinition.Type.SMELTING}）判定条件是
 * {@code recipe instanceof AbstractCookingRecipe}（见本体 {@code ResearchRecipePageResolver}），
 * 所以专属配方只要还是原版烹饪配方的一支，书页就照旧渲染得出——不必改动研究树里的引用。</p>
 *
 * <h2>配方 JSON 的 type 字段写哪个 id</h2>
 * <p>{@code type} 指的是<b>序列化器</b>（{@code RecipeSerializer}）而不是配方类型。
 * 这里两者同名注册为 {@code skydeitymysticpivot:xuanshu_smelting}（分属两个注册表，互不相干），
 * 于是 JSON 里写 {@code "type": "skydeitymysticpivot:xuanshu_smelting"}，
 * 读出来后 {@code getType()} 返回的正是上面那个专属配方类型。</p>
 */
public final class ModRecipes {

    /** 配方类型注册表。 */
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, MysticPivot.MODID);

    /** 序列化器注册表。 */
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, MysticPivot.MODID);

    /** 玄枢炎炉专属烧炼（碎片、盐等高阶烧炼只此一处可成）。 */
    public static final Supplier<RecipeType<XuanshuSmeltingRecipe>> XUANSHU_SMELTING_TYPE =
            RECIPE_TYPES.register("xuanshu_smelting",
                    () -> RecipeType.simple(rl("xuanshu_smelting")));

    /**
     * 专属烧炼的序列化器：直接借用原版 {@link SimpleCookingSerializer}
     * （字段与 {@code minecraft:smelting} 一模一样：ingredient / result / experience / cookingtime），
     * 默认烹饪时长取 {@value XuanshuInfernalFurnaceBlockEntity#SMELT_INTERVAL} tick——与炉子实际速度一致。
     */
    public static final Supplier<RecipeSerializer<XuanshuSmeltingRecipe>> XUANSHU_SMELTING_SERIALIZER =
            RECIPE_SERIALIZERS.register("xuanshu_smelting",
                    () -> new SimpleCookingSerializer<>(XuanshuSmeltingRecipe::new,
                            XuanshuInfernalFurnaceBlockEntity.SMELT_INTERVAL));

    /**
     * 玄枢坩埚的炼金配方类型（{@code skydeitymysticpivot:xuanshu_crucible}）。
     *
     * <p>与炎炉那条同理：另立一类，是为了让「坩埚的东西只在玄枢坩埚里成」这件事
     * 在配方层面就成立——本体的坩埚不会去查这张表，别家的机器也查不到。</p>
     */
    public static final Supplier<RecipeType<XuanshuCrucibleRecipe>> XUANSHU_CRUCIBLE_TYPE =
            RECIPE_TYPES.register("xuanshu_crucible",
                    () -> RecipeType.simple(rl("xuanshu_crucible")));

    /** 玄枢坩埚配方的序列化器（字段：catalyst / result / aspects，与本体坩埚一致）。 */
    public static final Supplier<RecipeSerializer<XuanshuCrucibleRecipe>> XUANSHU_CRUCIBLE_SERIALIZER =
            RECIPE_SERIALIZERS.register("xuanshu_crucible", XuanshuCrucibleRecipe.Serializer::new);

    /**
     * 玄枢祛魔书的合成序列化器（第一百零一批新增）。
     *
     * <p>「动态产物 + 动态余料」的配方只能用 {@link CustomRecipe} 这一支，
     * 而它们的序列化器在原版是 {@code SimpleCraftingRecipeSerializer}——
     * 原版的 {@code crafting_special_armordye} 等八条全是它。
     * 它的 JSON 只有一个字段 {@code category}（缺省 {@code misc}），所以配方文件长这样：</p>
     *
     * <pre>{@code
     * { "type": "skydeitymysticpivot:disenchant_book", "category": "misc" }
     * }</pre>
     *
     * <p>注意 {@link RecipeSerializer} 注册表的 id 同时就是 JSON 里 {@code type} 的值；
     * 本配方<b>不需要</b>另立 {@code RecipeType}——{@code CustomRecipe} 的 {@code getType()}
     * 由 {@code CraftingRecipe} 默认给到 {@code RecipeType.CRAFTING}，
     * 于是它照旧被合成台 / 配方书 / JEI 的「合成」一栏当成一条合成配方看待。</p>
     */
    public static final Supplier<RecipeSerializer<DisenchantBookRecipe>> DISENCHANT_BOOK_SERIALIZER =
            RECIPE_SERIALIZERS.register("disenchant_book",
                    () -> new SimpleCraftingRecipeSerializer<DisenchantBookRecipe>(DisenchantBookRecipe::new));

    /**
     * 玄枢双生书的合成序列化器（第二百零五批新增）。
     *
     * <p>与上面那条同路：{@link TwinBookRecipe} 也是「产物由输入算出来」的
     * {@link net.minecraft.world.item.crafting.CustomRecipe}，序列化器照旧借原版
     * {@code SimpleCraftingRecipeSerializer}，JSON 只有一个 {@code category} 字段：</p>
     *
     * <pre>{@code
     * { "type": "skydeitymysticpivot:twin_book", "category": "misc" }
     * }</pre>
     *
     * <p>它同样被当成一条<b>合成</b>配方（{@code RecipeType.CRAFTING}），配方书与 JEI
     * 都会把它列在合成一栏，并显示产物 ×2。</p>
     */
    public static final Supplier<RecipeSerializer<TwinBookRecipe>> TWIN_BOOK_SERIALIZER =
            RECIPE_SERIALIZERS.register("twin_book",
                    () -> new SimpleCraftingRecipeSerializer<TwinBookRecipe>(TwinBookRecipe::new));

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(MysticPivot.MODID, path);
    }

    private ModRecipes() {
    }
}
