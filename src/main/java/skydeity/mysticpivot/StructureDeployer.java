package skydeity.mysticpivot;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多方块的公共骨架：<b>形制格子</b> + <b>完成度</b> + <b>一键搭建 / 收回</b>。
 *
 * <p>水晶塔（{@link VoidMiningStructure}）与传送门（{@link PortalGateStructure}）只交出两张表
 * —— 相对锚点的偏移、该长什么样 —— 判定 / 算账 / 清场 / 落块 / 按格收回 / 提示文案全在这里。
 * 两边的差别只有一个提示前缀。</p>
 *
 * <h2>约定</h2>
 * <ul>
 *   <li><b>先算账、再动手</b>：任何一格缺料就整单不做，不会留下拆了一半的工地；</li>
 *   <li><b>只问是不是空气</b>：玩家把石英换成混凝土照样算数；</li>
 *   <li><b>带方块实体的一律不碰</b>（箱子、漏斗、别的机器、信标）—— 那是家当不是地形；
 *       搭建时跳过并报数，收回时同样跳过（所以摆下去的信标会留在原地）；</li>
 *   <li>提示文案走 {@code prefix + ".built" / ".lack" / ...}，两个结构各有一套语言键。</li>
 * </ul>
 */
final class StructureDeployer {

    /** 一根结构件：相对锚点的偏移 + 该长什么样。 */
    record Part(BlockPos offset, BlockState state) {

        BlockPos at(BlockPos anchor) {
            return anchor.offset(offset);
        }
    }

    private StructureDeployer() {
    }

    // ------------------------------------------------------------------
    // 完成度
    // ------------------------------------------------------------------

    /** 这一格算不算「填上了」：<b>不是空气就算</b>。 */
    static boolean satisfied(BlockState actual) {
        return !actual.isAir();
    }

    /** 已经立起来几格（界面进度条分子）。 */
    static int filled(Level level, BlockPos anchor, List<Part> parts) {
        int n = 0;
        for (Part part : parts) {
            if (satisfied(level.getBlockState(part.at(anchor)))) {
                n++;
            }
        }
        return n;
    }

    /** 结构是否齐全（任缺一格即 false）。 */
    static boolean isComplete(Level level, BlockPos anchor, List<Part> parts) {
        for (Part part : parts) {
            if (!satisfied(level.getBlockState(part.at(anchor)))) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 一键搭建
    // ------------------------------------------------------------------

    /**
     * 按玩家背包里的材料一次搭齐。
     *
     * @param anchor 机器方块（只用于定位与音效）
     * @param parts  要摆的全部格子（相对 anchor）
     * @param prefix 提示文案前缀，如 {@code message.skydeitymysticpivot.portal}
     */
    static boolean build(ServerLevel level, BlockPos anchor, List<Part> parts,
                         String prefix, Player player) {
        List<Part> todo = new ArrayList<>(parts.size());
        int blocked = 0;
        for (Part part : parts) {
            BlockState current = level.getBlockState(part.at(anchor));
            if (current.isAir()) {
                todo.add(part);
            } else if (current.hasBlockEntity()) {
                blocked++;      // 被箱子 / 信标 / 别的机器占着：不碰，只报数
            }
        }
        if (todo.isEmpty()) {
            player.displayClientMessage(blocked > 0
                    ? Component.translatable(prefix + ".blocked", blocked)
                    : Component.translatable(prefix + ".already"), true);
            return false;
        }

        // 一、算账（缺一格就整单不做）
        Map<Item, Integer> need = tally(todo);
        for (Map.Entry<Item, Integer> entry : need.entrySet()) {
            int lack = entry.getValue() - count(player, entry.getKey());
            if (lack > 0) {
                player.displayClientMessage(Component.translatable(prefix + ".lack",
                        Component.translatable(entry.getKey().getDescriptionId()), lack), true);
                return false;
            }
        }

        // 二、扣料
        for (Map.Entry<Item, Integer> entry : need.entrySet()) {
            consume(player, entry.getKey(), entry.getValue());
        }

        // 三、清场 + 立起来（草丛 / 水面这类可替换方块直接盖过去，其余原地掉落后打掉）
        int cleared = 0;
        for (Part part : todo) {
            BlockPos pos = part.at(anchor);
            if (!level.getBlockState(pos).canBeReplaced()) {
                level.destroyBlock(pos, true);
                cleared++;
            }
            level.setBlock(pos, part.state(), Block.UPDATE_ALL);
        }
        player.displayClientMessage(Component.translatable(prefix + ".built", todo.size()), true);
        if (cleared > 0) {
            player.displayClientMessage(Component.translatable(prefix + ".cleared", cleared), true);
        }
        if (blocked > 0) {
            player.displayClientMessage(Component.translatable(prefix + ".blocked", blocked), true);
        }
        level.playSound(null, anchor, SoundEvents.AMETHYST_BLOCK_PLACE,
                SoundSource.BLOCKS, 0.9F, 1.1F);
        return true;
    }

    // ------------------------------------------------------------------
    // 一键收回
    // ------------------------------------------------------------------

    /** 从上往下收干净：每一格按它自己的战利品表掉（玻璃掉玻璃、羊毛掉羊毛）。 */
    static int dismantle(ServerLevel level, BlockPos anchor, List<Part> parts,
                         String prefix, Player player) {
        int removed = 0;
        int blocked = 0;
        // 列表是从下往上排的，反着走就是从顶往下
        for (int i = parts.size() - 1; i >= 0; i--) {
            BlockPos pos = parts.get(i).at(anchor);
            BlockState current = level.getBlockState(pos);
            if (current.isAir()) {
                continue;
            }
            if (current.hasBlockEntity()) {
                blocked++;
                continue;
            }
            List<ItemStack> drops = Block.getDrops(current, level, pos, null, player,
                    player.getMainHandItem());
            level.removeBlock(pos, false);
            for (ItemStack stack : drops) {
                player.getInventory().placeItemBackInInventory(stack);
            }
            removed++;
        }
        if (removed > 0) {
            player.displayClientMessage(Component.translatable(prefix + ".dismantled", removed), true);
            level.playSound(null, anchor, SoundEvents.AMETHYST_BLOCK_BREAK,
                    SoundSource.BLOCKS, 0.9F, 0.9F);
        }
        if (blocked > 0) {
            player.displayClientMessage(Component.translatable(prefix + ".blocked", blocked), true);
        }
        return removed;
    }

    // ------------------------------------------------------------------
    // 材料
    // ------------------------------------------------------------------

    /** 这一批格子要哪些方块物品、各多少个（按物品合并，楼梯朝向不另算一种）。 */
    static Map<Item, Integer> tally(Collection<Part> parts) {
        Map<Item, Integer> need = new LinkedHashMap<>();
        for (Part part : parts) {
            need.merge(part.state().getBlock().asItem(), 1, Integer::sum);
        }
        return need;
    }

    /** 背包里某种物品的总数（含副手；创造模式玩家算无穷大）。 */
    private static int count(Player player, Item item) {
        if (player.hasInfiniteMaterials()) {
            return Integer.MAX_VALUE;
        }
        int n = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && stack.getItem() == item) {
                n += stack.getCount();
            }
        }
        return n;
    }

    /** 从背包里扣掉若干个（算账之后才调，调用方保证够）。 */
    private static void consume(Player player, Item item, int amount) {
        if (player.hasInfiniteMaterials()) {
            return;
        }
        int left = amount;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && left > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || stack.getItem() != item) {
                continue;
            }
            int take = Math.min(left, stack.getCount());
            stack.shrink(take);
            left -= take;
            if (stack.isEmpty()) {
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        inventory.setChanged();
    }
}
