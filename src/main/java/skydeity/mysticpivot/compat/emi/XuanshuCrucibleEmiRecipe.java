package skydeity.mysticpivot.compat.emi;

import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import skydeity.mysticpivot.XuanshuCrucibleRecipe;

import java.util.List;
import java.util.Map;

/**
 * EMI 版的「玄枢坩埚」一页：触媒槽 →（箭头）→ 产物槽，箭头下写一行要素与份数。
 *
 * <p>只用了 EMI 那几个签名最稳的部件（{@code addSlot} / {@code addFillingArrow} / {@code addText}），
 * 不引贴图，因此不会跟着 EMI 内部图集变动而失效。</p>
 */
public final class XuanshuCrucibleEmiRecipe implements EmiRecipe {

    /** 版式尺寸：宽度固定，高度按要素行数长出来。 */
    private static final int WIDTH = 108;

    private static final int INPUT_X = 4, INPUT_Y = 4;
    private static final int ARROW_X = 28, ARROW_Y = 5;
    private static final int OUTPUT_X = 58, OUTPUT_Y = 4;

    /**
     * 要素清单的排布。★ 一行只放 3 条：一条「风 3」在默认字宽下约 36px
     * （CJK 名 2 字 = 18px + 空格 + 数字 ≈ 6~12px + 条间 2 空格），
     * 3 条正好铺满 108px；放 4 条就要 144px，会被 EMI 裁掉。
     */
    private static final int ASPECT_X = 4, ASPECT_TOP = 49, ASPECT_ROW = 11, ASPECT_PER_ROW = 3;

    /**
     * 要素最多画几行。条目数本身没有上限（本体那边 {@code MAX_ASPECTS} 是 256），
     * 正常配方只有 2~5 条；真写出十几条时不能让板子无限长下去，超出的折成 {@code +N}。
     */
    private static final int ASPECT_MAX_ROWS = 6;
    private static final int ASPECT_MAX = ASPECT_PER_ROW * ASPECT_MAX_ROWS;

    private final RecipeHolder<XuanshuCrucibleRecipe> holder;

    public XuanshuCrucibleEmiRecipe(RecipeHolder<XuanshuCrucibleRecipe> holder) {
        this.holder = holder;
    }

    @Override
    public EmiRecipeCategory getCategory() {
        return XuanshuEmiPlugin.XUANSHU_CRUCIBLE;
    }

    @Override
    public ResourceLocation getId() {
        return this.holder.id();
    }

    @Override
    public List<EmiIngredient> getInputs() {
        return List.of(EmiIngredient.of(this.holder.value().catalyst()));
    }

    @Override
    public List<EmiStack> getOutputs() {
        return List.of(EmiStack.of(this.holder.value().result()));
    }

    @Override
    public int getDisplayWidth() {
        return WIDTH;
    }

    @Override
    public int getDisplayHeight() {
        return ASPECT_TOP + this.aspectRows() * ASPECT_ROW + 3;
    }

    private int aspectRows() {
        int count = Math.min(this.holder.value().aspects().size(), ASPECT_MAX);
        return Math.max(1, (count + ASPECT_PER_ROW - 1) / ASPECT_PER_ROW);
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        widgets.addSlot(this.getInputs().get(0), INPUT_X, INPUT_Y);
        widgets.addFillingArrow(ARROW_X, ARROW_Y, 20 * 50);
        widgets.addSlot(EmiStack.of(this.holder.value().result()), OUTPUT_X, OUTPUT_Y)
                .recipeContext(this);

        // 逐条写要素：本体的 tooltipName 自带要素色，但 EMI 的 addText 用的是传入色 ⇒
        // 用白色 + 投影，浅色与深色两种主题下都读得清。
        int index = 0;
        for (Map.Entry<AspectId, Integer> entry : this.holder.value().aspects().entrySet()) {
            if (index >= ASPECT_MAX) {
                break; // 超出的折成 +N
            }
            Component line = AspectApi.tooltipName(entry.getKey()).copy()
                    .append(Component.literal(" " + entry.getValue()));
            widgets.addText(line,
                    ASPECT_X + (index % ASPECT_PER_ROW) * 36,
                    ASPECT_TOP + (index / ASPECT_PER_ROW) * ASPECT_ROW,
                    0xFFFFFFFF, true);
            index++;
        }
        int hidden = this.holder.value().aspects().size() - index;
        if (hidden > 0) {
            widgets.addText(Component.literal("+" + hidden),
                    ASPECT_X + (index % ASPECT_PER_ROW) * 36,
                    ASPECT_TOP + (index / ASPECT_PER_ROW) * ASPECT_ROW,
                    0xFFFFFFFF, true);
        }
    }
}
