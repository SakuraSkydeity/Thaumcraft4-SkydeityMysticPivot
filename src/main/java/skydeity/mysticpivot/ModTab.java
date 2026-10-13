package skydeity.mysticpivot;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 创造页签，条目按类型分组排序：
 * 功能物品 → 四把成品法杖（终局 → 常规 → 中高阶 → 入门）→ 杖柄 → 杖端 → 诸锭 → 诸碎片
 * → 其余材料 → 工具 → 三个法杖核心 → 方块（炎炉 → 坩埚 → 炼金炉 → 台座 → 八卦位石 → 魔晶）。
 */
public final class ModTab {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MysticPivot.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MysticPivot.MODID))
                    .icon(ModWandParts::gildedCharged)
                    .displayItems((parameters, output) -> {
                        // 一、功能物品
                        output.accept(ModItems.AKASHIC_RECORDS.get());
                        // 二、成品法杖
                        output.accept(ModWandParts.charged());
                        output.accept(ModWandParts.gildedCharged());
                        output.accept(ModWandParts.baguaCharged());
                        output.accept(ModWandParts.zhuqueCharged());
                        // 三、杖柄（放一起）
                        output.accept(ModItems.SIXIANG_WAND_ROD.get());
                        output.accept(ModItems.ONYX_WAND_ROD.get());
                        output.accept(ModItems.ZHUQUE_WAND_ROD.get());
                        // 四、杖端（放一起）
                        output.accept(ModItems.BAGUA_WAND_CAP.get());
                        output.accept(ModItems.GILDED_WAND_CAP.get());
                        output.accept(ModItems.ZHUQUE_WAND_CAP.get());
                        // 五、诸锭（全部放一起）
                        output.accept(ModItems.AZURE_DRAGON_INGOT.get());
                        output.accept(ModItems.VERMILION_BIRD_INGOT.get());
                        output.accept(ModItems.WHITE_TIGER_INGOT.get());
                        output.accept(ModItems.BLACK_TORTOISE_INGOT.get());
                        output.accept(ModItems.XUANSHU_INGOT.get());
                        // 六、诸碎片
                        output.accept(ModItems.AZURE_DRAGON_SHARD.get());
                        output.accept(ModItems.VERMILION_BIRD_SHARD.get());
                        output.accept(ModItems.WHITE_TIGER_SHARD.get());
                        output.accept(ModItems.BLACK_TORTOISE_SHARD.get());
                        output.accept(ModItems.XUANSHU_SHARD.get());
                        output.accept(ModItems.BAGUA_SHARD.get());
                        // 七、其余玄枢材料
                        output.accept(ModItems.XUANSHU_BAGUA_SALT.get());
                        output.accept(ModItems.SIXIANG_CLOTH.get());
                        output.accept(ModItems.XUANSHU_CLOTH.get());
                        // 八、工具
                        output.accept(ModItems.XUANSHU_PICKAXE.get());
                        // 九、法杖核心（三个放一起，方块前）
                        output.accept(ModItems.FOCUS_COSMOS.get());
                        output.accept(ModItems.FOCUS_GUARDIAN.get());
                        output.accept(ModItems.FOCUS_TELEPORT.get());
                        // 十、方块（炎炉 → 坩埚 → 炼金炉 → 台座 → 八卦位石 → 魔晶）
                        output.accept(ModItems.XUANSHU_INFERNAL_FURNACE.get());
                        output.accept(ModItems.XUANSHU_CRUCIBLE.get());
                        output.accept(ModItems.XUANSHU_ALCHEMY_FURNACE.get());
                        output.accept(ModItems.XUANSHU_PEDESTAL.get());
                        output.accept(ModItems.SIXIANG_STONE_BRICK.get());
                        output.accept(ModItems.BAGUA_STONE_QIAN.get());
                        output.accept(ModItems.BAGUA_STONE_DUI.get());
                        output.accept(ModItems.BAGUA_STONE_LI.get());
                        output.accept(ModItems.BAGUA_STONE_ZHEN.get());
                        output.accept(ModItems.BAGUA_STONE_XUN.get());
                        output.accept(ModItems.BAGUA_STONE_KAN.get());
                        output.accept(ModItems.BAGUA_STONE_GEN.get());
                        output.accept(ModItems.BAGUA_STONE_KUN.get());
                        output.accept(ModItems.XUANSHU_CRYSTAL_CLUSTER.get());
                        output.accept(ModItems.XUANSHU_PURIFYING_STONE.get());
                    })
                    .build());

    private ModTab() {
    }
}
