package skydeity.mysticpivot;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(MysticPivot.MODID)
public class MysticPivot {

    public static final String MODID = "skydeitymysticpivot";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MysticPivot(IEventBus modEventBus, ModContainer modContainer) {
        ModItems.ITEMS.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModBlockEntities.BE_TYPES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModRecipes.RECIPE_TYPES.register(modEventBus);
        ModRecipes.RECIPE_SERIALIZERS.register(modEventBus);
        ModTab.TABS.register(modEventBus);
        ModFocuses.ATTACHMENTS.register(modEventBus);
        ModWandParts.register();
        ModFocuses.register();

        NeoForge.EVENT_BUS.addListener(ModEvents::onItemCrafted);
        NeoForge.EVENT_BUS.addListener(ModEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ModEvents::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(ModEvents::onServerStarted);
        NeoForge.EVENT_BUS.addListener(ModEvents::onAddReloadListener);
        modEventBus.addListener(ModEvents::onRegisterCapabilities);
        // 第七十八批：Curios 饰品登记（玄枢洞虚灵目），走模组事件总线的通用设置期
        modEventBus.addListener(ModEvents::onCommonSetup);
    }
}
