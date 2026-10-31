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
        // 第一百九十四批：玄枢源质罐的「要素表」物品组件（掉罐子时靠它把内容带走）
        ModDataComponents.COMPONENTS.register(modEventBus);
        ModWandParts.register();
        ModFocuses.register();

        NeoForge.EVENT_BUS.addListener(ModEvents::onItemCrafted);
        NeoForge.EVENT_BUS.addListener(ModEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ModEvents::onLivingIncomingDamage);
        NeoForge.EVENT_BUS.addListener(ModEvents::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(ModEvents::onServerStarted);
        NeoForge.EVENT_BUS.addListener(ModEvents::onAddReloadListener);
        // 第一百六十四批：烛龙衔时核心的催动主循环（服务端每 tick 补跑被催机器的 ticker），
        // 停机时清表——不清的话静态登记表会攥着上一个世界的 ServerLevel 不放。
        // 第一百六十五批：ServerStoppingEvent 在 stopServer() 存档之前触发（已反编译确认），
        // 倍率悬浮字必须赶在存档前撤掉，否则会随区块落盘、下次进游戏还浮在机器上。
        NeoForge.EVENT_BUS.addListener(TemporalCore::onServerTick);
        NeoForge.EVENT_BUS.addListener(TemporalCore::onServerStopping);
        NeoForge.EVENT_BUS.addListener(TemporalCore::onServerStopped);
        modEventBus.addListener(ModEvents::onRegisterCapabilities);
        // 第七十八批：Curios 饰品登记（玄枢洞虚灵目），走模组事件总线的通用设置期
        modEventBus.addListener(ModEvents::onCommonSetup);
    }
}
