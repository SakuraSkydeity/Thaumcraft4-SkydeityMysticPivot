package skydeity.mysticpivot;

import net.minecraft.client.RecipeBookCategories;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RegisterRecipeBookCategoriesEvent;

/** 客户端注册：菜单屏幕、方块实体渲染器、粒子与配方书分类。 */
@EventBusSubscriber(modid = MysticPivot.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ModClientEvents {

    @SubscribeEvent
    public static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.XUANSHU_INFERNAL_FURNACE.get(), XuanshuInfernalFurnaceScreen::new);
        event.register(ModMenus.XUANSHU_CRUCIBLE.get(), XuanshuCrucibleScreen::new);
        event.register(ModMenus.XUANSHU_ALCHEMY_FURNACE.get(), XuanshuAlchemyFurnaceScreen::new);
    }

    /**
     * 给专属烧炼配方登记一个配方书分类。
     *
     * <p>★ {@code ClientRecipeBook#categorizeAndGroupRecipes} 只处理
     * {@code !isSpecial()} 的配方，然后拿 {@code RecipeBookManager.findCategories} 求分类；
     * 求不到就每条刷一行 {@code Unknown recipe category}。原来靠 {@code isSpecial()=true}
     * 绕过它，但那会让 JEI 的烹饪类别把配方整个滤掉（见
     * {@link XuanshuSmeltingRecipe#isSpecial()}），所以改成在这里<b>正路登记</b>：
     * 把 {@code xuanshu_smelting} 归入原版「熔炉 · 杂项」页。</p>
     *
     * <p>这只是让分类有名字、日志干净，<b>不代表这些配方能在熔炉的配方书里做</b>——
     * 配方书只列玩家已解锁的配方，而这些配方没有任何解锁途径。</p>
     */
    @SubscribeEvent
    public static void onRegisterRecipeBookCategories(RegisterRecipeBookCategoriesEvent event) {
        event.registerRecipeCategoryFinder(ModRecipes.XUANSHU_SMELTING_TYPE.get(),
                holder -> RecipeBookCategories.FURNACE_MISC);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.XUANSHU_PEDESTAL.get(),
                XuanshuPedestalRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.QI_GLYPH.get(), QiGlyphParticle.Provider::new);
    }

    private ModClientEvents() {
    }
}
