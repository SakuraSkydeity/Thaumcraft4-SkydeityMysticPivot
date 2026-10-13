package skydeity.mysticpivot;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 让本模组法杖按杖分档带 EPIC（浅紫）/ RARE（蓝）名色，注册方块的能力暴露，
 * 并在服务端数据包就绪后挂上八卦要素来源。
 *
 * <p>名色是物品堆叠上的 {@code minecraft:rarity} 组件，而本体的 {@code WandItem} 不覆写稀有度、
 * 装配配方的产物也不会携带组件，所以只能在这里补：</p>
 * <ul>
 *   <li>合成（含奥术工作台装配）出来的那一下立即补；</li>
 *   <li>每 1 秒扫一遍玩家背包，兜住 /give、JEI 作弊、箱子拾取，以及本次改动前
 *       就已经存在的旧法杖。</li>
 * </ul>
 * 组件只在缺失或不是 EPIC 时才写，所以稳态下每秒只做一次「读组件 + 比较」。
 *
 * <p>要素来源见 {@link ModAspectSources}：它必须在服务端数据包加载完之后才能注册
 * （八卦要素是数据包注册表，mod 构造期还不存在），所以挂在服务端启动事件上，
 * 并在 {@code ServerStartedEvent} 兜一次重试。</p>
 */
public final class ModEvents {

    /** 背包扫描间隔（tick）。 */
    private static final int SCAN_INTERVAL = 20;

    private ModEvents() {
    }

    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        ModWandParts.stamp(event.getCrafting());
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.tickCount % SCAN_INTERVAL != 0) {
            return;
        }
        var inventory = player.getInventory();
        for (ItemStack stack : inventory.items) {
            ModWandParts.stamp(stack);
        }
        for (ItemStack stack : inventory.offhand) {
            ModWandParts.stamp(stack);
        }
    }

    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        ModAspectSources.install();
    }

    public static void onServerStarted(ServerStartedEvent event) {
        ModAspectSources.install();
    }

    /**
     * 数据包重载，挂两件事：
     *
     * <ul>
     *   <li>{@link XuanshuCrucibleRecipes}：把<b>所有</b> {@code thaumcraft:crucible} 配方里的要素表
     *       抄成一份索引——★ 理由见该类：本体那张配方类在实现包里，{@code aspects()} 我们调不到；
     *       但配方「类型」能从登记表按 id 取出来让本体自己去匹配，于是只差「需要哪些要素」这一块，
     *       重载时读原始 JSON 补齐即可，顺带把本体自带的、其他附属写的、数据包与 KubeJS 生成的
     *       配方全都涵盖进来。</li>
     *   <li>{@link TrigramGate}：把「哪条注魔配方要立哪几卦」的门禁表读进来——
     *       这样加门禁<b>不用改 Java</b>，在数据包里加一行就行。</li>
     * </ul>
     */
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(XuanshuCrucibleRecipes.INSTANCE);
        event.addListener(TrigramGate.INSTANCE);
    }

    /** 注册方块实体对外暴露的自动化能力（mod 事件总线调用）。 */
    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        // 按面暴露：关闭面全拒、输入面只收（输入槽 + 燃料槽）、输出面只给（输出槽）
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.XUANSHU_INFERNAL_FURNACE.get(),
                (be, side) -> be.getExternalItems(side));
        // 玄枢坩埚同理（两格：输入只收触媒、输出只给）
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.XUANSHU_CRUCIBLE.get(),
                (be, side) -> be.getExternalItems(side));
        // 玄枢炼金炉只有进料（产物是源质、走管子出去）：开启面只收烧得出要素的东西
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.XUANSHU_ALCHEMY_FURNACE.get(),
                (be, side) -> be.getExternalItems(side));
    }
}
