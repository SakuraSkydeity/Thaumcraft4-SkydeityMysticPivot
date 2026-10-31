package skydeity.mysticpivot;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

/**
 * 玄枢双生书的合成配方（第二百零五批新增）：<b>玄枢双生书 ×1 + 一本附魔书 ×1</b>
 * ⇒ 产出<b>两本一模一样的附魔书</b>。两格一起吃掉，双生书<b>不退回</b>。
 *
 * <h2>「一模一样」的边界</h2>
 * <p>产物直接由输入那一本 {@code copyWithCount(2)} 得来，所以<b>不只是附魔</b>：
 * {@code STORED_ENCHANTMENTS} 的条目与等级、{@code CUSTOM_NAME}（铁砧改过的书名）、
 * 甚至别的模组挂上去的组件都整份复制。<b>不重新拼装</b>，因此不存在「复制时掉级」这种偏差。</p>
 *
 * <h2>为什么只认附魔书、并且只认单件</h2>
 * <p>附魔存在书里是 {@link DataComponents#STORED_ENCHANTMENTS}，与物品自身的
 * {@code ENCHANTMENTS} 是两码事：拿一把附魔剑来应当<b>不匹配</b>（那是
 * {@link DisenchantBookRecipe 祛魔书} 的活）。原版附魔书的堆叠上限本来就是 1，
 * 这里仍照 {@code MapCloningRecipe} 的口径显式要求 {@code count == 1}——
 * 万一将来有人改了堆叠上限，也不至于「一次合成把一整摞各拓一份」。</p>
 *
 * <h2>为什么产物 count 写 2</h2>
 * <p>合成台的产物格本来就允许 count 大于单件上限，取走时由
 * {@code Inventory#add} 自动拆成两格（附魔书的上限是 1）。这样 JEI / EMI 的配方页
 * 能直接显示「×2」，玩家不必猜第二本去哪了。</p>
 */
public class TwinBookRecipe extends CustomRecipe {

    public TwinBookRecipe(CraftingBookCategory category) {
        super(category);
    }

    /**
     * 取出「要被复制的那本附魔书」；不匹配时返回 {@code null}。
     *
     * <p>匹配条件：非空物品恰好两件 = 一本玄枢双生书 + 一本单件、带着
     * {@code STORED_ENCHANTMENTS} 的附魔书。</p>
     */
    @Nullable
    private static ItemStack source(CraftingInput input) {
        boolean tome = false;
        ItemStack book = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(ModItems.TWIN_BOOK.get())) {
                if (tome) {
                    return null;                      // 两本双生书：不匹配
                }
                tome = true;
                continue;
            }
            if (!book.isEmpty()) {
                return null;                          // 第二件非书物品：不匹配
            }
            if (stack.getCount() != 1 || !stack.is(Items.ENCHANTED_BOOK)) {
                return null;                          // 只认单件附魔书
            }
            if (storedEnchantmentsOf(stack).isEmpty()) {
                return null;                          // 空白的书没有可复制的附魔
            }
            book = stack;
        }
        return tome && !book.isEmpty() ? book : null;
    }

    /** 书里存着的附魔（缺省空表）。 */
    private static ItemEnchantments storedEnchantmentsOf(ItemStack stack) {
        ItemEnchantments enchantments = stack.get(DataComponents.STORED_ENCHANTMENTS);
        return enchantments == null ? ItemEnchantments.EMPTY : enchantments;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return source(input) != null;
    }

    /** 产物：原书整份复制两份（组件一样、附魔等级一样）。 */
    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack book = source(input);
        return book == null ? ItemStack.EMPTY : book.copyWithCount(2);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.TWIN_BOOK_SERIALIZER.get();
    }
}
