package skydeity.mysticpivot.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.recipe.EmiCookingRecipe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import skydeity.mysticpivot.ModBlocks;
import skydeity.mysticpivot.ModRecipes;
import skydeity.mysticpivot.MysticPivot;
import skydeity.mysticpivot.XuanshuCrucibleRecipe;
import skydeity.mysticpivot.XuanshuPedestalBlockEntity;
import skydeity.mysticpivot.XuanshuSmeltingRecipe;

/**
 * EMI 适配（与 JEI 那侧同口径）。
 *
 * <p>EMI 的入口注解是 {@link EmiEntrypoint}（{@code EmiPlugin} 只是个接口，没有同名注解）；
 * EMI 的 NeoForge 平台层靠注解扫描发现插件，不需要 {@code META-INF/services} 或 {@code emi.json}。</p>
 *
 * <ul>
 *   <li>玄枢炎炉登记为原版「烧炼 / 燃料」的工作台；</li>
 *   <li>专属烧炼（碎片、玄枢八卦之盐等）单开一个「玄枢炎炉」类别，版式直接复用 EMI 自家给原版
 *       熔炉用的 {@link EmiCookingRecipe}（输入 + 火苗 + 箭头 + 输出），只换类别与工作台；</li>
 *   <li>「玄枢坩埚」一页列两种配方：本模组自己的 {@code xuanshu_crucible}，以及本体的
 *       {@code thaumcraft:crucible}（附属 / 数据包写的坩埚配方）。★ 本体<b>没有</b> EMI 适配，
 *       所以后者在 EMI 里只有这一页能看到。</li>
 * </ul>
 */
@EmiEntrypoint
public final class XuanshuEmiPlugin implements EmiPlugin {

    private static final ResourceLocation CATEGORY_ID =
            ResourceLocation.fromNamespaceAndPath(MysticPivot.MODID, "xuanshu_smelting");

    /** 玄枢炎炉专属类别（图标即炎炉本体）。 */
    private static final EmiRecipeCategory XUANSHU_SMELTING =
            new EmiRecipeCategory(CATEGORY_ID,
                    EmiStack.of(ModBlocks.XUANSHU_INFERNAL_FURNACE.get()));

    /** 玄枢坩埚专属类别（图标即坩埚本体）。 */
    static final EmiRecipeCategory XUANSHU_CRUCIBLE =
            new EmiRecipeCategory(
                    ResourceLocation.fromNamespaceAndPath(MysticPivot.MODID, "xuanshu_crucible"),
                    EmiStack.of(ModBlocks.XUANSHU_CRUCIBLE.get()));

    /** 玄枢台座专属类别（图标即台座本体）。 */
    static final EmiRecipeCategory XUANSHU_PEDESTAL =
            new EmiRecipeCategory(
                    ResourceLocation.fromNamespaceAndPath(MysticPivot.MODID, "xuanshu_pedestal"),
                    EmiStack.of(ModBlocks.XUANSHU_PEDESTAL.get()));

    @Override
    public void register(EmiRegistry registry) {
        EmiStack furnace = EmiStack.of(ModBlocks.XUANSHU_INFERNAL_FURNACE.get());
        registry.addWorkstation(VanillaEmiRecipeCategories.SMELTING, furnace);
        registry.addWorkstation(VanillaEmiRecipeCategories.FUEL, furnace);

        registry.addCategory(XUANSHU_SMELTING);
        registry.addWorkstation(XUANSHU_SMELTING, furnace);

        // 玄枢坩埚：单独一页、只挂坩埚一台
        EmiStack crucible = EmiStack.of(ModBlocks.XUANSHU_CRUCIBLE.get());
        registry.addCategory(XUANSHU_CRUCIBLE);
        registry.addWorkstation(XUANSHU_CRUCIBLE, crucible);

        // 玄枢台座：转化判定写在方块实体里、没有配方 JSON，所以内容直接取那张常量表
        registry.addCategory(XUANSHU_PEDESTAL);
        registry.addWorkstation(XUANSHU_PEDESTAL, EmiStack.of(ModBlocks.XUANSHU_PEDESTAL.get()));
        for (XuanshuPedestalBlockEntity.Transformation transformation
                : XuanshuPedestalBlockEntity.TRANSFORMATIONS) {
            registry.addRecipe(new XuanshuPedestalEmiRecipe(transformation));
        }

        // register() 是在 EMI 的重载流程里调的，所以这里拿得到配方表（客户端）。
        for (RecipeHolder<XuanshuSmeltingRecipe> holder
                : registry.getRecipeManager().getAllRecipesFor(ModRecipes.XUANSHU_SMELTING_TYPE.get())) {
            registry.addRecipe(new XuanshuEmiSmeltingRecipe(holder));
        }

        // ★ 「玄枢坩埚」一页列两种来源：本模组自己的 xuanshu_crucible，以及本体的
        //   thaumcraft:crucible（附属写的、数据包自己加的坩埚配方全在这一批里）。
        //   运行时玄枢坩埚两种都认，所以这一页都列上；重份由 displayRecipes 去掉
        //   （本模组自己那 54 条就是本体 54 条的照抄版）。
        //   本体自己没有 EMI 适配，所以这里是那些配方唯一的去处。
        ClientLevel level = Minecraft.getInstance().level;
        RegistryAccess registries = level == null ? null : level.registryAccess();
        for (RecipeHolder<XuanshuCrucibleRecipe> holder
                : XuanshuCrucibleRecipe.displayRecipes(registry.getRecipeManager(), registries)) {
            registry.addRecipe(new XuanshuCrucibleEmiRecipe(holder));
        }
    }

    /** EMI 的烹饪版式只差一个配方 id，补上它，方便 EMI 按 id 索引 / 显示。 */
    private static final class XuanshuEmiSmeltingRecipe extends EmiCookingRecipe {

        private final ResourceLocation id;

        private XuanshuEmiSmeltingRecipe(RecipeHolder<XuanshuSmeltingRecipe> holder) {
            super(holder.value(), XUANSHU_SMELTING, 1, false);
            this.id = holder.id();
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }
    }
}
