package skydeity.mysticpivot;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import java.util.List;

/**
 * 两柄镐的「掉落模式」生效处（第一百六十批）——
 * 挂 {@link BlockDropsEvent}，只在<b>非默认模式</b>下改掉落。
 *
 * <h2>一、为什么挑这个事件</h2>
 * <p>{@code BlockDropsEvent} 在「方块已被挖掉、掉落物已生成、正要撒出来」的
 * 那一刻触发，且 {@link #getDrops()} 就是最终那份 {@code ItemEntity} 列表。
 * 改它即终稿：不动破坏流程、不动时运结算、不用取消事件再自己补一套流程，
 * 自然最不易错（详见 {@link PickaxeDropModes} 类注释第四节）。</p>
 *
 * <h2>二、判定</h2>
 * <p>{@link #getTool()} 就是挖这一下方块用的物品。是镐且模式非正常才接手；
 * 其他任何工具、任何模组机器挖方块，本处理器直接无视。默认模式连查都不查。</p>
 *
 * <h2>三、两个模式各干什么</h2>
 * <ul>
 *   <li><b>精准采集</b>：整份掉落清空，换成<b>方块本身</b>一件
 *       （{@code new ItemStack(state.getBlock())}）。没有物品形态的方块
 *       （极少数结构方块之类）则原样放过，不制造空掉落。</li>
 *   <li><b>自动熔炼</b>：逐样看过往掉落，按<b>原版熔炉配方表</b>查产物，
 *       查得到就换成产物（份量按掉落份数翻倍），查不到原样留下——
 *       于是「能熔炼的都要」，不能熔的也不丢。</li>
 * </ul>
 *
 * <p>经验球一律不碰（{@code setDroppedExperience} 不动）：精准采矿石照常给经验，
 * 和原版时运逆一个口径。</p>
 */
@EventBusSubscriber(modid = MysticPivot.MODID)
public final class PickaxeDropEvents {

    private PickaxeDropEvents() {
    }

    @SubscribeEvent
    public static void onBlockDrops(BlockDropsEvent event) {
        if (event.isCanceled()) {
            return;
        }
        PickaxeDropModes.Mode mode = PickaxeDropModes.get(event.getTool());
        if (mode == PickaxeDropModes.Mode.NORMAL) {
            return;                                   // 正常模式：零插手
        }

        if (mode == PickaxeDropModes.Mode.SILK) {
            replaceWithBlockItself(event);
        } else {
            smeltDrops(event);
        }
    }

    /** 精准采集：整份掉落换成方块本身（没有物品形态就原样留着）。 */
    private static void replaceWithBlockItself(BlockDropsEvent event) {
        Block block = event.getState().getBlock();
        ItemStack asItem = block.asItem() == Items.AIR
                ? ItemStack.EMPTY
                : new ItemStack(block);
        if (asItem.isEmpty()) {
            return;
        }
        BlockPos pos = event.getPos();
        Level level = event.getLevel();
        List<ItemEntity> drops = event.getDrops();
        drops.clear();
        drops.add(new ItemEntity(level,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                asItem));
    }

    /** 自动熔炼：逐样查原版熔炉配方表，烧得动就换成产物，烧不动原样留下。 */
    private static void smeltDrops(BlockDropsEvent event) {
        Level level = event.getLevel();
        if (level == null) {
            return;
        }
        for (ItemEntity drop : event.getDrops()) {
            ItemStack input = drop.getItem();
            ItemStack result = smelt(level, input);
            if (result != input) {
                drop.setItem(result);
            }
        }
    }

    /**
     * 查一样物品的熔炼产物；查不到返回原物（同一引用，调用方据此判断「没动」）。
     *
     * <p>写法与 {@code XuanshuInfernalFurnaceBlockEntity#smeltResult} 同源：
     * {@code getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level)}。
     * 只查<b>原版</b>烧炼表——凡熔炉烧得的（矿石→锭、沙→玻璃、生肉→熟肉、
     * 圆石→石头、陶片→琉璃砖……）都在里面，这正是用户要的「能熔炼的都要」。</p>
     */
    private static ItemStack smelt(Level level, ItemStack input) {
        if (input.isEmpty()) {
            return input;
        }
        RecipeManager manager = level.getRecipeManager();
        return manager
                .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(input), level)
                .map(holder -> holder.value().getResultItem(level.registryAccess()))
                .filter(product -> !product.isEmpty())
                .map(product -> {
                    ItemStack out = product.copy();
                    out.setCount(product.getCount() * input.getCount());
                    return out;
                })
                .orElse(input);
    }
}
