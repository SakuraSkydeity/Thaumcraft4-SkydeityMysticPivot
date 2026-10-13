package skydeity.mysticpivot;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.flag.FeatureFlags;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** 菜单类型注册。 */
public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, MysticPivot.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<XuanshuInfernalFurnaceMenu>> XUANSHU_INFERNAL_FURNACE =
            MENUS.register("xuanshu_infernal_furnace",
                    () -> new MenuType<>(XuanshuInfernalFurnaceMenu::new, FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<XuanshuCrucibleMenu>> XUANSHU_CRUCIBLE =
            MENUS.register("xuanshu_crucible",
                    () -> new MenuType<>(XuanshuCrucibleMenu::new, FeatureFlags.VANILLA_SET));

    public static final DeferredHolder<MenuType<?>, MenuType<XuanshuAlchemyFurnaceMenu>> XUANSHU_ALCHEMY_FURNACE =
            MENUS.register("xuanshu_alchemy_furnace",
                    () -> new MenuType<>(XuanshuAlchemyFurnaceMenu::new, FeatureFlags.VANILLA_SET));

    private ModMenus() {
    }
}
