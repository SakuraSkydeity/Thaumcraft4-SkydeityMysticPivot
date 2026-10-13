package skydeity.mysticpivot.compat.jei;

import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.library.plugins.vanilla.cooking.AbstractCookingCategory;
import skydeity.mysticpivot.ModBlocks;
import skydeity.mysticpivot.XuanshuInfernalFurnaceBlockEntity;
import skydeity.mysticpivot.XuanshuSmeltingRecipe;

/**
 * 「玄枢炎炉」这一页的版式——直接继承 JEI 给原版熔炉用的那套烹饪版式
 * （{@link AbstractCookingCategory}：输入槽 + 燃料火苗 + 进度箭头 + 输出槽、
 * 自动标注经验与烹饪时长、配方 codec 也已备好），只把配方类型、图标方块与标题换成我们的。
 *
 * <p>这样版式与原版熔炉完全一致，玩家不必重新学；而工作台只有玄枢炎炉一台
 * （见 {@link XuanshuJeiPlugin#registerRecipeCatalysts}），所以也不会误导人去用普通熔炉。</p>
 */
public final class XuanshuSmeltingJeiCategory extends AbstractCookingCategory<XuanshuSmeltingRecipe> {

    public XuanshuSmeltingJeiCategory(IGuiHelper guiHelper) {
        super(guiHelper,
                XuanshuJeiPlugin.XUANSHU_SMELTING,
                ModBlocks.XUANSHU_INFERNAL_FURNACE.get(),
                "gui.skydeitymysticpivot.category.xuanshu_smelting",
                XuanshuInfernalFurnaceBlockEntity.SMELT_INTERVAL);
    }
}
