package skydeity.mysticpivot;

import dev.tc4port.thaumcraft.api.aspect.AspectAmounts;
import dev.tc4port.thaumcraft.api.aspect.AspectApi;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.aspect.AspectPoolApi;
import dev.tc4port.thaumcraft.api.aspect.AspectPoolImport;
import dev.tc4port.thaumcraft.api.aspect.VisAction;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 阿卡西记录：使用后一次性为玩家当前注册表里的<b>全部要素</b>各补 1000 点研究点。
 */
public class AkashicRecordsItem extends Item {

    /** 每个要素授予的研究点数量。 */
    public static final int POINTS = 1000;

    public AkashicRecordsItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer server)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        Map<AspectId, Integer> amounts = new LinkedHashMap<>();
        for (AspectId aspect : AspectApi.definitions().keySet()) {
            amounts.put(aspect, POINTS);
        }
        if (!amounts.isEmpty()) {
            AspectPoolApi.importBatch(server,
                    new AspectPoolImport(Set.of(), new AspectAmounts(amounts)), VisAction.EXECUTE);
        }
        if (!server.isCreative()) {
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }
}
