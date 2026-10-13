package skydeity.mysticpivot.compat.jei;

import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectDefinition;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import skydeity.mysticpivot.ModBlocks;
import skydeity.mysticpivot.XuanshuCrucibleRecipe;

import java.util.Map;

/**
 * 「玄枢坩埚」这一页的版式：<b>触媒 → （箭头）→ 产物</b>，下方列出这一炉要吃掉的要素与份数。
 *
 * <p>底色与箭头全用 JEI 自带的绘制块（{@code getSlotDrawable} / {@code getRecipeArrow}），
 * 不引任何贴图——省得跟着 JEI 的图集路径改来改去。
 * 要素图标直接取本体要素定义里的 {@code thaumcraft:textures/aspects/*.png}，
 * 所以这一页与原版的要素图标是同一批资源，不会画歪。</p>
 *
 * <p>工作台只登记玄枢坩埚一台（见 {@link XuanshuJeiPlugin}）。这一页列的配方有两种来源：
 * 本模组自己的 {@code skydeitymysticpivot:xuanshu_crucible}，以及本体的
 * {@code thaumcraft:crucible}（附属 / 数据包写的那些）——运行时玄枢坩埚两种都认。</p>
 */
public final class XuanshuCrucibleJeiCategory implements IRecipeCategory<RecipeHolder<XuanshuCrucibleRecipe>> {

    /** 版式尺寸。 */
    public static final int WIDTH = 108;
    public static final int HEIGHT = 72;

    private static final int INPUT_X = 4, INPUT_Y = 4;
    private static final int ARROW_X = 28, ARROW_Y = 5;
    private static final int OUTPUT_X = 58, OUTPUT_Y = 4;
    /** 要素行的起点与步进（每行最多 5 个），以及最多画几行。 */
    private static final int ASPECT_X = 4, ASPECT_Y = 32, ASPECT_STEP = 20, ASPECT_PER_ROW = 5;
    /**
     * 要素图标最多画两行（10 个）。
     *
     * <p>版式高度是死的（{@link #HEIGHT}），而要素条目数没有上限——本体那边
     * {@code MAX_ASPECTS} 是 256。正常配方只有 2~5 条，但数据包真写出十几条时
     * 不能任它画到框外面去：超出的部分折成一句 {@code +N}。</p>
     */
    private static final int ASPECT_ROWS = 2;
    private static final int ASPECT_MAX = ASPECT_PER_ROW * ASPECT_ROWS;

    private final IDrawable icon;
    private final IDrawable slot;
    private final IDrawable arrow;

    public XuanshuCrucibleJeiCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.XUANSHU_CRUCIBLE.get()));
        this.slot = guiHelper.getSlotDrawable();
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public mezz.jei.api.recipe.RecipeType<RecipeHolder<XuanshuCrucibleRecipe>> getRecipeType() {
        return XuanshuJeiPlugin.XUANSHU_CRUCIBLE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.skydeitymysticpivot.category.xuanshu_crucible");
    }

    @Override
    public IDrawable getIcon() {
        return this.icon;
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<XuanshuCrucibleRecipe> holder,
                          IFocusGroup focuses) {
        XuanshuCrucibleRecipe recipe = holder.value();
        // 触媒槽：锅沿上那件「丢进去的东西」
        builder.addSlot(RecipeIngredientRole.CATALYST, INPUT_X + 1, INPUT_Y + 1)
                .addIngredients(recipe.catalyst());
        // 产物槽
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X + 1, OUTPUT_Y + 1)
                .addItemStack(recipe.result());
    }

    @Override
    public void draw(RecipeHolder<XuanshuCrucibleRecipe> holder, IRecipeSlotsView slots,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        this.slot.draw(graphics, INPUT_X, INPUT_Y);
        this.slot.draw(graphics, OUTPUT_X, OUTPUT_Y);
        this.arrow.draw(graphics, ARROW_X, ARROW_Y);

        // 要素行：图标 + 右下角的份数
        Font font = Minecraft.getInstance().font;
        int index = 0;
        int shown = 0;
        for (Map.Entry<AspectId, Integer> entry : holder.value().aspects().entrySet()) {
            if (index >= ASPECT_MAX) {
                break; // 超出两行的部分折成 +N（见 ASPECT_ROWS）
            }
            int ix = ASPECT_X + (index % ASPECT_PER_ROW) * ASPECT_STEP;
            int iy = ASPECT_Y + (index / ASPECT_PER_ROW) * ASPECT_STEP;
            AspectDefinition definition = AspectApi.get(entry.getKey());
            if (definition != null) {
                ResourceLocation icon = definition.texture();
                graphics.blit(icon, ix, iy, 0, 0, 16, 16, 16, 16);
            }
            String amount = Integer.toString(entry.getValue());
            graphics.drawString(font, amount, ix + 16 - font.width(amount), iy + 10,
                    0xFFFFFFFF, true);
            index++;
            shown = index;
        }
        int hidden = holder.value().aspects().size() - shown;
        if (hidden > 0) {
            int ix = ASPECT_X + (shown % ASPECT_PER_ROW) * ASPECT_STEP;
            int iy = ASPECT_Y + (shown / ASPECT_PER_ROW) * ASPECT_STEP;
            graphics.drawString(font, "+" + hidden, ix, iy + 4, 0xFFFFFFFF, true);
        }
    }
}
