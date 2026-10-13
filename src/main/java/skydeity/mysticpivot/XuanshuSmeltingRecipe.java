package skydeity.mysticpivot;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CookingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * 玄枢炎炉专属烧炼配方（配方类型 = {@link ModRecipes#XUANSHU_SMELTING_TYPE}）。
 *
 * <p>形制与 {@code minecraft:smelting} 完全一致（同用原版 {@code SimpleCookingSerializer} 读写），
 * 只是 recipe type 换成了本模组自己的那一个。于是：</p>
 * <ul>
 *   <li>原版熔炉 / 高炉 / 烟熏炉，以及所有照 {@code RecipeType.SMELTING} 找配方的机器，
 *       <b>都查不到</b>这些配方——查得到就说明「只能在玄枢炎炉里烧」这条没落到实处；</li>
 *   <li>玄枢炎炉先查这一类型（见 {@code XuanshuInfernalFurnaceBlockEntity#smeltResult}），
 *       查不到再回落到原版烧炼表，所以它同时仍是一台合格的普通熔炉；</li>
 *   <li>魔导书里的「烧炼」页照旧认得它（{@code instanceof AbstractCookingRecipe}）。</li>
 * </ul>
 */
public class XuanshuSmeltingRecipe extends AbstractCookingRecipe {

    /** 序列化器回调（{@code SimpleCookingSerializer} 用）给出的字段顺序与原版烧炼完全相同。 */
    public XuanshuSmeltingRecipe(String group, CookingBookCategory category, Ingredient ingredient,
                                ItemStack result, float experience, int cookingTime) {
        super(ModRecipes.XUANSHU_SMELTING_TYPE.get(), group, category,
                ingredient, result, experience, cookingTime);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.XUANSHU_SMELTING_SERIALIZER.get();
    }

    /**
     * ★★ <b>不能回 {@code true}</b>——JEI 会把整个配方连同页面一起滤掉。
     *
     * <p>曾经为了甩掉 {@code ClientRecipeBook} 的 {@code Unknown recipe category} 日志
     * （它只处理 {@code !isSpecial() && !isIncomplete()} 的配方）把这里写成 {@code true}，
     * 代价是：<b>JEI 的 {@code AbstractCookingCategory#getRecipes} 会
     * {@code .filter(recipe -> !recipe.value().isSpecial())}</b>，而「玄枢炎炉」那一页
     * 正是继承 {@code AbstractCookingCategory} 来画的 ⇒ 这一类型的配方在 JEI 里
     * <b>一条都不显示</b>（表现 = 搜「八卦碎片」「玄枢八卦之盐」时没有来源）。
     * 本品自己那 54 条坩埚配方不受影响，因为坩埚页是自建类别、不走 JEI 的烹饪基类——
     * 这也正是「为什么只有碎片与盐不见了」的原因。</p>
     *
     * <p>日志噪声改用正路解决：见 {@code ModClientEvents#onRegisterRecipeBookCategories}
     * 向 NeoForge 的 {@code RegisterRecipeBookCategoriesEvent} 登记分类查找器。</p>
     */
    @Override
    public boolean isSpecial() {
        return false;
    }
}
