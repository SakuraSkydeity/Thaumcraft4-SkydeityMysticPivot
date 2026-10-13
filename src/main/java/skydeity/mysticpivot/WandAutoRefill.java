package skydeity.mysticpivot;

import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.aspect.VisChannel;
import dev.tc4port.thaumcraft.api.aspect.VisCost;
import dev.tc4port.thaumcraft.api.wand.WandApi;
import dev.tc4port.thaumcraft.api.wand.WandView;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 「宇宙中子素法杖」不进节点也能一直满魔力：只要它在背包里，就每 5 tick（0.25 秒）
 * 把六个原始要素补齐到容量上限，用掉之后同样立刻补回。
 *
 * <p>杖芯的 {@code recharge} 声明为 {@code NONE}，本体 {@code WandItem.inventoryTick}
 * 会直接返回，所以两套充能逻辑不会互相覆盖。</p>
 */
@EventBusSubscriber(modid = MysticPivot.MODID)
public final class WandAutoRefill {

    /** 检查周期（tick）。 */
    private static final int CHECK_INTERVAL = 5;

    private WandAutoRefill() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.tickCount % CHECK_INTERVAL != 0) {
            return;
        }
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            refill(player, inventory.getItem(slot));
        }
    }

    private static void refill(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        WandView view = WandApi.view(stack).orElse(null);
        if (view == null || !ModWandParts.ROD.equals(view.rod())) {
            return;
        }
        for (Map.Entry<VisChannel, Integer> entry : view.capacityCentivis().entrySet()) {
            VisChannel channel = entry.getKey();
            int missing = entry.getValue() - view.visCentivis().getOrDefault(channel, 0);
            if (missing > 0) {
                WandApi.insert(player, stack, VisCost.ofCentivis(channel, missing), VisAction.EXECUTE);
            }
        }
    }
}
