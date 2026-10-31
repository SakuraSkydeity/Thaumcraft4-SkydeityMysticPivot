package skydeity.mysticpivot;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.level.Level;

/**
 * 八卦开山辟地镐（注册 id 仍是 {@code bagua_pickaxe}）——第一百六十批给两柄镐
 * 统一加「右键掉落模式」，原话见 {@link PickaxeDropModes} 类注释。
 *
 * <p>这一柄原本是光秃秃的 {@link PickaxeItem}（注册在 {@link ModItems} 里，
 * 一行 {@code new PickaxeItem(...)}）。本批只加一件事：<b>普通右键</b>在
 * 正常 / 精准采集 / 自动熔炼三个模式间循环，顺手给条动作栏提示。
 * 挖掘、耐久、附魔、注魔升级一概没碰。</p>
 *
 * <p>它没有「潜行右键」的既有功能（那是焚天镐的隧道掘），所以右键整条都归模式切换。</p>
 */
public class BaguaPickaxeItem extends PickaxeItem {

    public BaguaPickaxeItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    /** 普通右键：三个掉落模式循环。 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            PickaxeDropModes.toggleWithFeedback(stack, player);
        }
        return InteractionResultHolder.success(stack);
    }
}
