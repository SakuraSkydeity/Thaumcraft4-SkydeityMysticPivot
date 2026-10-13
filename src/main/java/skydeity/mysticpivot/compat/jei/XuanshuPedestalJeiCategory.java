package skydeity.mysticpivot.compat.jei;

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
import net.minecraft.world.item.ItemStack;
import skydeity.mysticpivot.Fengshui;
import skydeity.mysticpivot.ModBlocks;
import skydeity.mysticpivot.XuanshuPedestalBlockEntity;

/**
 * 「玄枢台座」这一页：<b>台面上放什么 →（卦气）→ 化作什么</b>。
 *
 * <p>台座的转化不是配方（没有 JSON，判定写在
 * {@link XuanshuPedestalBlockEntity#serverTick} 里），JEI 不可能自动收录，
 * 所以单开一页；内容直接读 {@link XuanshuPedestalBlockEntity#TRANSFORMATIONS}，
 * 与运行时判定<b>同一张表</b>，不会两处各写一份而走岔。</p>
 *
 * <p>页面上除输入输出，还写明<b>要立在哪种卦气里</b>——黑曜石化哭泣的黑曜石只认
 * {@link Fengshui.Trigram#QIAN 乾}，也就是「头顶无遮挡的开阔之地」。立错了地方，
 * 放上去什么都不会发生（不是概率问题）。</p>
 */
public final class XuanshuPedestalJeiCategory
        implements IRecipeCategory<XuanshuPedestalBlockEntity.Transformation> {

    /** 版式尺寸：一行槽位 + 两行说明。宽度与坩埚那页（108）对齐。 */
    public static final int WIDTH = 108;
    public static final int HEIGHT = 48;

    private static final int INPUT_X = 4, INPUT_Y = 4;
    private static final int ARROW_X = 28, ARROW_Y = 5;
    private static final int OUTPUT_X = 58, OUTPUT_Y = 4;
    /** 说明行的起点。★ 两行都要塞进 108px：中文 ≤9 字（≈81px）、英文 ≤17 字（≈94px）。 */
    private static final int TEXT_X = 4, TEXT_Y = 26, TEXT_LINE = 10;

    private final IDrawable icon;
    private final IDrawable slot;
    private final IDrawable arrow;

    public XuanshuPedestalJeiCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemStack(new ItemStack(ModBlocks.XUANSHU_PEDESTAL.get()));
        this.slot = guiHelper.getSlotDrawable();
        this.arrow = guiHelper.getRecipeArrow();
    }

    @Override
    public mezz.jei.api.recipe.RecipeType<XuanshuPedestalBlockEntity.Transformation> getRecipeType() {
        return XuanshuJeiPlugin.XUANSHU_PEDESTAL;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("gui.skydeitymysticpivot.category.xuanshu_pedestal");
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
    public void setRecipe(IRecipeLayoutBuilder builder,
                          XuanshuPedestalBlockEntity.Transformation transformation, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, INPUT_X + 1, INPUT_Y + 1)
                .addItemStack(transformation.input());
        builder.addSlot(RecipeIngredientRole.OUTPUT, OUTPUT_X + 1, OUTPUT_Y + 1)
                .addItemStack(transformation.output());
    }

    @Override
    public void draw(XuanshuPedestalBlockEntity.Transformation transformation, IRecipeSlotsView slots,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        this.slot.draw(graphics, INPUT_X, INPUT_Y);
        this.slot.draw(graphics, OUTPUT_X, OUTPUT_Y);
        this.arrow.draw(graphics, ARROW_X, ARROW_Y);

        Font font = Minecraft.getInstance().font;
        Fengshui.Trigram qi = transformation.qi();
        // ★ 文字用「白字 + 投影」（与坩埚那页同一约定）：JEI 有浅色/深色两种主题，
        //   固定深灰在深色主题下会糊成一团，白色带投影两边都读得清。
        // 第一行：这一炉要的是哪一卦、立在什么地方
        graphics.drawString(font,
                Component.translatable("gui.skydeitymysticpivot.pedestal_qi",
                        Fengshui.displayName(qi)), TEXT_X, TEXT_Y, 0xFFFFFFFF, true);
        // 第二行：立错了地方就什么都不会发生
        graphics.drawString(font,
                Component.translatable("gui.skydeitymysticpivot.pedestal_qi_hint"),
                TEXT_X, TEXT_Y + TEXT_LINE, 0xFFFFFFFF, true);
    }
}
