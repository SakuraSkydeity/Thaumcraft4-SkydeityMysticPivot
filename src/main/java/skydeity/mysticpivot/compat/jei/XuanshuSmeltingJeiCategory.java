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
 * <p>★ 第一百四十八批：星象「双倍产出」说明已改挂到炎炉物品 lore 上，这一页不再加高、
 * 不再底部加注——版式回到原版烹饪的 82×54。</p>
 */
public final class XuanshuSmeltingJeiCategory extends AbstractCookingCategory<XuanshuSmeltingRecipe> {

    public XuanshuSmeltingJeiCategory(IGuiHelper guiHelper) {
        super(guiHelper,
                XuanshuJeiPlugin.XUANSHU_SMELTING,
                ModBlocks.XUANSHU_INFERNAL_FURNACE.get(),
                "gui.skydeitymysticpivot.category.xuanshu_smelting",
                XuanshuInfernalFurnaceBlockEntity.SMELT_INTERVAL,
                java.util.List.of());
    }
}
