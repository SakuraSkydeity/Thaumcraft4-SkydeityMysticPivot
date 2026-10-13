package skydeity.mysticpivot.compat.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Ingredient;
import skydeity.mysticpivot.Fengshui;
import skydeity.mysticpivot.MysticPivot;
import skydeity.mysticpivot.XuanshuPedestalBlockEntity;

import java.util.List;

/**
 * EMI 版的「玄枢台座」一页（与 JEI 那侧同口径）：输入槽 →（卦气）→ 产物槽，
 * 下面写明要立在哪一卦的地气里。
 *
 * <p>内容取 {@link XuanshuPedestalBlockEntity#TRANSFORMATIONS}，与运行时判定同一张表。</p>
 */
public final class XuanshuPedestalEmiRecipe implements EmiRecipe {

    /** 宽度与坩埚那页（108）对齐；高度容两行说明。 */
    private static final int WIDTH = 108;
    private static final int HEIGHT = 48;
    private static final int INPUT_X = 4, INPUT_Y = 4;
    private static final int ARROW_X = 28, ARROW_Y = 5;
    private static final int OUTPUT_X = 58, OUTPUT_Y = 4;
    private static final int TEXT_X = 4, TEXT_Y = 26, TEXT_LINE = 10;

    private final XuanshuPedestalBlockEntity.Transformation transformation;
    private final ResourceLocation id;

    public XuanshuPedestalEmiRecipe(XuanshuPedestalBlockEntity.Transformation transformation) {
        this.transformation = transformation;
        // id 取「输入物品」的名字（台座目前只有一条转化，够用；再多也不会撞车）。
        this.id = ResourceLocation.fromNamespaceAndPath(MysticPivot.MODID,
                "pedestal/" + BuiltInRegistries.ITEM.getKey(transformation.input().getItem()).getPath());
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return XuanshuEmiPlugin.XUANSHU_PEDESTAL;
    }

    @Override
    public ResourceLocation getId() {
        return this.id;
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return List.of(EmiIngredient.of(Ingredient.of(this.transformation.input())));
    }

    @Override
    public List<EmiStack> getOutputs() {
        return List.of(EmiStack.of(this.transformation.output()));
    }

    @Override
    public int getDisplayWidth() {
        return WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return HEIGHT;
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(this.getInputs().get(0), INPUT_X, INPUT_Y);
        widgets.addFillingArrow(ARROW_X, ARROW_Y, 20 * 50);
        widgets.addSlot(EmiStack.of(this.transformation.output()), OUTPUT_X, OUTPUT_Y)
                .recipeContext(this);

        // ★ 白字 + 投影，与坩埚那页同一约定：EMI 会跟主题换深浅背景，固定灰会糊掉。
        Fengshui.Trigram qi = this.transformation.qi();
        widgets.addText(Component.translatable("gui.skydeitymysticpivot.pedestal_qi",
                Fengshui.displayName(qi)), TEXT_X, TEXT_Y, 0xFFFFFFFF, true);
        widgets.addText(Component.translatable("gui.skydeitymysticpivot.pedestal_qi_hint"),
                TEXT_X, TEXT_Y + TEXT_LINE, 0xFFFFFFFF, true);
    }
}
