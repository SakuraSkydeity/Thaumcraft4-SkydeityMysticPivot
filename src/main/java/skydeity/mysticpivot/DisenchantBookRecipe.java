package skydeity.mysticpivot;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
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
 * 玄枢祛魔书的合成配方（第一百零一批新增）：<b>玄枢祛魔书 ×1 + 一件带附魔的物品 ×1</b>
 * ⇒ 产出<b>一本附魔书</b>（{@code minecraft:enchanted_book}，附魔原样搬过去），
 * 而<b>那件物品本身被退回</b>，附魔清零。
 *
 * <h2>「物品不被吃掉」靠的是什么</h2>
 * <p>原版合成会把每个格子里的东西吃掉一件。要让「被祛魔的那件东西」活下来，
 * 只能走 {@link #getRemainingItems}——它返回的栈会在取走产物时被<b>放回</b>合成格
 * （格子里原本只有 1 件时就地替换，否则塞进背包）。原版的水桶 / 药水就是这条路。
 * 所以本配方是 {@link CustomRecipe}（动态产物 + 动态余料），不是 JSON 能表达的。</p>
 *
 * <h2>为什么目标物品必须「恰好 1 件」</h2>
 * <p>这是<b>防复制</b>的硬条件。假如允许一堆（比如 5 把同款附魔剑）：
 * 取产物时每格只 shrink 1 ⇒ 剩 4 把<b>带附魔</b>的剑，而 {@code getRemainingItems}
 * 又退回 1 把<b>已祛魔</b>的剑，等于用一本玄枢祛魔书把一份附魔复制了 4 次。
 * 与 {@code MapCloningRecipe} 同款处理：{@code count != 1} 直接不匹配。</p>
 *
 * <h2>为什么只认 {@code ENCHANTMENTS}</h2>
 * <p>物品自身的附魔在 {@link DataComponents#ENCHANTMENTS}；附魔书那种「存着的附魔」
 * 在 {@link DataComponents#STORED_ENCHANTMENTS}。这里只索取前者——拿一本附魔书来
 * 祛魔只会白烧一本玄枢祛魔书（换来同样的书），所以直接<b>不匹配</b>，省得玩家吃亏。</p>
 */
public class DisenchantBookRecipe extends CustomRecipe {

    public DisenchantBookRecipe(CraftingBookCategory category) {
        super(category);
    }

    /**
     * 取出「要被祛魔的那件物品」；不匹配时返回 {@code null}。
     *
     * <p>匹配条件：格子里非空物品恰好两件 = 一本玄枢祛魔书 + 一件单件、带附魔、
     * 且附魔存在 {@code ENCHANTMENTS} 里的物品。</p>
     */
    @Nullable
    private static ItemStack target(CraftingInput input) {
        ItemStack tome = ItemStack.EMPTY;
        ItemStack target = ItemStack.EMPTY;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (stack.is(ModItems.DISENCHANT_BOOK.get())) {
                if (!tome.isEmpty()) {
                    return null;                      // 两本玄枢祛魔书：不匹配
                }
                tome = stack;
                continue;
            }
            if (!target.isEmpty()) {
                return null;                          // 第二件非书物品：不匹配
            }
            if (stack.getCount() != 1) {
                return null;                          // ★ 只处理单件，杜绝附魔复制
            }
            if (enchantmentsOf(stack).isEmpty()) {
                return null;                          // 没附魔可祛：不匹配
            }
            target = stack;
        }
        return tome.isEmpty() || target.isEmpty() ? null : target;
    }

    /** 物品自身的附魔（缺省空表）。 */
    private static ItemEnchantments enchantmentsOf(ItemStack stack) {
        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        return enchantments == null ? ItemEnchantments.EMPTY : enchantments;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        return target(input) != null;
    }

    /** 产物：一本附魔书，附魔整条搬过去（等级原样）。 */
    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack source = target(input);
        if (source == null) {
            return ItemStack.EMPTY;
        }
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        book.set(DataComponents.STORED_ENCHANTMENTS, enchantmentsOf(source));
        return book;
    }

    /**
     * 余料：被祛魔的那件物品（复制一份、去掉附魔，其余组件原样保留）。
     * 玄枢祛魔书本身<b>不回退</b>——它就是变成那本附魔书的那一本。
     */
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput input) {
        NonNullList<ItemStack> remaining = NonNullList.withSize(input.size(), ItemStack.EMPTY);
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (stack.isEmpty() || stack.is(ModItems.DISENCHANT_BOOK.get())) {
                continue;
            }
            ItemStack stripped = stack.copyWithCount(1);
            stripped.remove(DataComponents.ENCHANTMENTS);
            remaining.set(i, stripped);
        }
        return remaining;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.DISENCHANT_BOOK_SERIALIZER.get();
    }
}
