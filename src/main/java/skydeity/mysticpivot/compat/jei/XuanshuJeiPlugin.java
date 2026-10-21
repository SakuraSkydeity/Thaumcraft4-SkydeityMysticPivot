package skydeity.mysticpivot.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import skydeity.mysticpivot.ModItems;
import skydeity.mysticpivot.ModRecipes;
import skydeity.mysticpivot.MysticPivot;
import skydeity.mysticpivot.XuanshuCrucibleRecipe;
import skydeity.mysticpivot.XuanshuPedestalBlockEntity;
import skydeity.mysticpivot.XuanshuSmeltingRecipe;

import java.util.List;

/**
 * JEI 适配。
 *
 * <ol>
 *   <li><b>熔炉工作台</b>：把玄枢炎炉登记成原版烧炼 / 燃料的工作台——与 JEI 自己给原版熔炉登记
 *       的完全一致，于是查「铁锭怎么烧」时炎炉也会作为可用机器列出来；</li>
 *   <li><b>玄枢炎炉专属类别</b>：碎片、玄枢八卦之盐这类高阶烧炼不在原版烧炼表里
 *       （配方类型是 {@code skydeitymysticpivot:xuanshu_smelting}），普通熔炉里查不到、
 *       JEI 也不会自动收录，所以在这里单开一页「玄枢炎炉」，只挂炎炉一台工作台——
 *       玩家一眼看得出这些配方<b>非炎炉不可</b>。</li>
 *   <li><b>玄枢坩埚</b>：同一条道理。这一页列的是「玄枢坩埚能做的全部」：本模组自己的
 *       {@code skydeitymysticpivot:xuanshu_crucible}（本体那批配方整批转过来的，见
 *       {@link XuanshuCrucibleJeiCategory}），<b>加上</b>本体类型的 {@code thaumcraft:crucible}
 *       ——附属写的、数据包自己加的坩埚配方都在后者里，运行时玄枢坩埚照样认，所以也要摆上。
 *       工作台只挂坩埚一台。</li>
 * </ol>
 */
@JeiPlugin
public final class XuanshuJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(MysticPivot.MODID, "jei_plugin");

    /**
     * 专属烧炼在 JEI 里的配方类型。
     *
     * <p>用 {@code createRecipeHolderType} 而不是 {@code create}：JEI 19 起配方一律以
     * {@link RecipeHolder} 为载体（原版烧炼就是 {@code RecipeHolder<SmeltingRecipe>}），
     * 这样默认的 {@code getCodec} 也能走「持有者编解码」这条短路，不必自己写 codec。</p>
     */
    public static final RecipeType<RecipeHolder<XuanshuSmeltingRecipe>> XUANSHU_SMELTING =
            RecipeType.createRecipeHolderType(
                    ResourceLocation.fromNamespaceAndPath(MysticPivot.MODID, "xuanshu_smelting"));

    /**
     * 玄枢坩埚的配方类型（同样是「持有者」形态）。
     *
     * <p>id 与配方类型同名（{@code skydeitymysticpivot:xuanshu_crucible}），
     * 于是 JEI 里按 id 索引、按配方跳转都对得上。</p>
     */
    public static final RecipeType<RecipeHolder<XuanshuCrucibleRecipe>> XUANSHU_CRUCIBLE =
            RecipeType.createRecipeHolderType(
                    ResourceLocation.fromNamespaceAndPath(MysticPivot.MODID, "xuanshu_crucible"));

    /**
     * 玄枢台座的转化页。
     *
     * <p>台座的转化判定写在方块实体里（没有 JSON 配方），所以这里不用
     * {@code RecipeHolder}，直接把
     * {@link XuanshuPedestalBlockEntity.Transformation} 当作"配方"对象。</p>
     */
    public static final RecipeType<XuanshuPedestalBlockEntity.Transformation> XUANSHU_PEDESTAL =
            RecipeType.create(MysticPivot.MODID, "xuanshu_pedestal",
                    XuanshuPedestalBlockEntity.Transformation.class);

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(
                new XuanshuSmeltingJeiCategory(registration.getJeiHelpers().getGuiHelper()),
                new XuanshuCrucibleJeiCategory(registration.getJeiHelpers().getGuiHelper()),
                new XuanshuPedestalJeiCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        // 台座那一页不依赖世界（表是写死的常量），先加，免得 level 还没就绪时连它也没了。
        registration.addRecipes(XUANSHU_PEDESTAL, XuanshuPedestalBlockEntity.TRANSFORMATIONS);

        // ★ 第一百四十八批：星象前置不再走 JEI「信息」条目——改挂到物品 lore 上
        //   （与「所需卦石」同色的淡靛蓝一行，见 ModItems#starLore）。这里只留配方本身。

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        registration.addRecipes(XUANSHU_SMELTING,
                level.getRecipeManager().getAllRecipesFor(ModRecipes.XUANSHU_SMELTING_TYPE.get()));
        registration.addRecipes(XUANSHU_CRUCIBLE, crucibleRecipes(level));
    }

    /**
     * 「玄枢坩埚」这一页要列的全部配方：<b>本模组自己的那批 + 本体类型的那批</b>（已去重）。
     *
     * <p>后者是 {@code thaumcraft:crucible} ——本体自带的、别的附属写的、数据包与 KubeJS
     * 生成的坩埚配方都在里面。玄枢坩埚运行时本来就认它们
     * （见 {@link XuanshuCrucibleRecipe#find}），这一页当然也该列出来，
     * 否则玩家会以为「只有这一页上的才做得出」。</p>
     *
     * <p>本模组自己那 54 条是本体 54 条的照抄版，所以两张表会有重份——
     * 去重交给 {@link XuanshuCrucibleRecipe#displayRecipes}（按触媒 + 产物认指纹）。</p>
     */
    private static List<RecipeHolder<XuanshuCrucibleRecipe>> crucibleRecipes(ClientLevel level) {
        return XuanshuCrucibleRecipe.displayRecipes(level.getRecipeManager(), level.registryAccess());
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        ItemStack furnace = new ItemStack(ModItems.XUANSHU_INFERNAL_FURNACE.get());
        registration.addRecipeCatalyst(furnace,
                XUANSHU_SMELTING, RecipeTypes.SMELTING, RecipeTypes.FUELING);
        // 坩埚这张表只认玄枢坩埚一台工作台
        registration.addRecipeCatalyst(new ItemStack(ModItems.XUANSHU_CRUCIBLE.get()),
                XUANSHU_CRUCIBLE);
        // 台座页同样只挂台座自己
        registration.addRecipeCatalyst(new ItemStack(ModItems.XUANSHU_PEDESTAL.get()),
                XUANSHU_PEDESTAL);
    }
}
