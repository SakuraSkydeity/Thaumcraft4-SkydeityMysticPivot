package skydeity.mysticpivot;

import net.minecraft.core.component.DataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Block;
import java.util.List;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MysticPivot.MODID);

    public static final DeferredItem<AkashicRecordsItem> AKASHIC_RECORDS = ITEMS.registerItem("akashic_records",
            AkashicRecordsItem::new, new Item.Properties().rarity(Rarity.EPIC).stacksTo(16)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            Component.translatable("item.skydeitymysticpivot.akashic_records.lore")
                                    .withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false))))));

    /**
     * 玄枢杖的两件材料（{@code onyx_wand_rod} / {@code gilded_wand_cap}）。
     *
     * <p><b>两条配方都</b>受卦象门禁约束：注魔这两件时，祭坛中央台座正下方那一格为中心的
     * 5×5 单层之内须把<b>八种卦位石各立一方</b>（凑齐一套八卦），方能引气入炉
     * （见 {@code InfusionMatrixCraftGateMixin}）。这条提示就写在两件材料上——玩家是在造
     * 这两件东西的时候需要先立石，成品法杖拿到手时石头早立好了，写在那里只是啰嗦。</p>
     */
    private static final String[] ALL_TRIGRAMS =
            {"qian", "dui", "li", "zhen", "xun", "kan", "gen", "kun"};

    /** 四象杖两件：只认艮、离、巽、坤这四卦。 */
    private static final String[] SIXIANG_TRIGRAMS = {"gen", "li", "xun", "kun"};

    /** 朱雀杖两件：只认离、艮两卦。 */
    private static final String[] ZHUQUE_TRIGRAMS = {"li", "gen"};

    public static final DeferredItem<Item> ONYX_WAND_ROD = ITEMS.registerSimpleItem("onyx_wand_rod",
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(16)
                    .component(DataComponents.LORE, new ItemLore(List.of(trigramLore(ALL_TRIGRAMS)))));

    public static final DeferredItem<Item> GILDED_WAND_CAP = ITEMS.registerSimpleItem("gilded_wand_cap",
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(16)
                    .component(DataComponents.LORE, new ItemLore(List.of(trigramLore(ALL_TRIGRAMS)))));

    // 八卦四象法杖的两件部件（四象锭打造）
    public static final DeferredItem<Item> SIXIANG_WAND_ROD = ITEMS.registerSimpleItem("sixiang_wand_rod",
            new Item.Properties().rarity(Rarity.RARE).stacksTo(16)
                    .component(DataComponents.LORE, new ItemLore(List.of(trigramLore(SIXIANG_TRIGRAMS)))));

    public static final DeferredItem<Item> BAGUA_WAND_CAP = ITEMS.registerSimpleItem("bagua_wand_cap",
            new Item.Properties().rarity(Rarity.RARE).stacksTo(16)
                    .component(DataComponents.LORE, new ItemLore(List.of(trigramLore(SIXIANG_TRIGRAMS)))));

    // 朱雀杖部件：宏伟包裹杖柄 / 蝶舞杖端（红金配色，入门档）
    public static final DeferredItem<Item> ZHUQUE_WAND_ROD = ITEMS.registerSimpleItem("zhuque_wand_rod",
            new Item.Properties().rarity(Rarity.RARE).stacksTo(16)
                    .component(DataComponents.LORE, new ItemLore(List.of(trigramLore(ZHUQUE_TRIGRAMS)))));

    public static final DeferredItem<Item> ZHUQUE_WAND_CAP = ITEMS.registerSimpleItem("zhuque_wand_cap",
            new Item.Properties().rarity(Rarity.RARE).stacksTo(16)
                    .component(DataComponents.LORE, new ItemLore(List.of(trigramLore(ZHUQUE_TRIGRAMS)))));

    // 传送核心（法杖核心:传送）：配任一杖柄，潜行右键记点、右键耗六要素各 10 点传送（见 TeleportCore）
    public static final DeferredItem<XuanshuFocusItem> FOCUS_TELEPORT = ITEMS.registerItem("focus_teleport",
            props -> new XuanshuFocusItem(props, ModFocuses.TELEPORT_ID),
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(1)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            grayLore("item.skydeitymysticpivot.focus_teleport.lore1"),
                            grayLore("item.skydeitymysticpivot.focus_teleport.lore2")))));

    // 四象锭：青龙 / 朱雀 / 白虎 / 玄武（高级锭，注魔合成）
    public static final DeferredItem<Item> AZURE_DRAGON_INGOT = ITEMS.registerSimpleItem("azure_dragon_ingot",
            new Item.Properties().rarity(Rarity.UNCOMMON));

    public static final DeferredItem<Item> VERMILION_BIRD_INGOT = ITEMS.registerSimpleItem("vermilion_bird_ingot",
            new Item.Properties().rarity(Rarity.UNCOMMON));

    public static final DeferredItem<Item> WHITE_TIGER_INGOT = ITEMS.registerSimpleItem("white_tiger_ingot",
            new Item.Properties().rarity(Rarity.UNCOMMON));

    public static final DeferredItem<Item> BLACK_TORTOISE_INGOT = ITEMS.registerSimpleItem("black_tortoise_ingot",
            new Item.Properties().rarity(Rarity.UNCOMMON));

    // 四神兽碎片：锭经炼金术化成，各带本兽之气
    public static final DeferredItem<Item> AZURE_DRAGON_SHARD = ITEMS.registerSimpleItem("azure_dragon_shard",
            new Item.Properties().rarity(Rarity.RARE));

    public static final DeferredItem<Item> VERMILION_BIRD_SHARD = ITEMS.registerSimpleItem("vermilion_bird_shard",
            new Item.Properties().rarity(Rarity.RARE));

    public static final DeferredItem<Item> WHITE_TIGER_SHARD = ITEMS.registerSimpleItem("white_tiger_shard",
            new Item.Properties().rarity(Rarity.RARE));

    public static final DeferredItem<Item> BLACK_TORTOISE_SHARD = ITEMS.registerSimpleItem("black_tortoise_shard",
            new Item.Properties().rarity(Rarity.RARE));

    // 玄枢材料：玄枢锭 / 碎片 / 八卦碎片 / 玄枢八卦之盐
    public static final DeferredItem<Item> XUANSHU_INGOT = ITEMS.registerSimpleItem("xuanshu_ingot",
            new Item.Properties().rarity(Rarity.EPIC));

    public static final DeferredItem<Item> XUANSHU_SHARD = ITEMS.registerSimpleItem("xuanshu_shard",
            new Item.Properties().rarity(Rarity.EPIC));

    public static final DeferredItem<Item> BAGUA_SHARD = ITEMS.registerSimpleItem("bagua_shard",
            new Item.Properties().rarity(Rarity.RARE));

    public static final DeferredItem<Item> XUANSHU_BAGUA_SALT = ITEMS.registerSimpleItem("xuanshu_bagua_salt",
            new Item.Properties().rarity(Rarity.EPIC));

    // 布匹：四象布匹（银白）/ 玄枢布匹（黑金）
    public static final DeferredItem<Item> SIXIANG_CLOTH = ITEMS.registerSimpleItem("sixiang_cloth",
            new Item.Properties().rarity(Rarity.RARE));

    public static final DeferredItem<Item> XUANSHU_CLOTH = ITEMS.registerSimpleItem("xuanshu_cloth",
            new Item.Properties().rarity(Rarity.EPIC));

    /**
     * 两个自制法杖核心：寰宇（蓝紫，右键把目标方块化为掉落物）与
     * 玄枢守护（鎏金橘黄，右键获得一颗免死之心）。lore 用灰色注释作用。
     */
    public static final DeferredItem<XuanshuFocusItem> FOCUS_COSMOS = ITEMS.registerItem("focus_cosmos",
            props -> new XuanshuFocusItem(props, ModFocuses.COSMOS_ID),
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(1)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            Component.translatable("item.skydeitymysticpivot.focus_cosmos.lore")
                                    .withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false))))));

    public static final DeferredItem<XuanshuFocusItem> FOCUS_GUARDIAN = ITEMS.registerItem("focus_guardian",
            props -> new XuanshuFocusItem(props, ModFocuses.GUARDIAN_ID),
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(1)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            Component.translatable("item.skydeitymysticpivot.focus_guardian.lore")
                                    .withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false))))));

    /**
     * 玄枢镐的挖掘材质：耐久 52 000（对应玄枢杖容量数字）、挖掘速度全场最快；
     * {@code getIncorrectBlocksForDrops} 沿用下界合金档（其标签为空集，即不误判任何方块的掉落）。
     */
    private static final Tier XUANSHU_TIER = new Tier() {
        @Override
        public int getUses() {
            return 52_000;
        }

        @Override
        public float getSpeed() {
            return 40.0F;
        }

        @Override
        public float getAttackDamageBonus() {
            return 5.0F;
        }

        @Override
        public TagKey<Block> getIncorrectBlocksForDrops() {
            return BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
        }

        @Override
        public int getEnchantmentValue() {
            return 22;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }
    };

    /**
     * 玄枢镐：挖掘等级最高、速度最快、耐久 52 000。
     *
     * <p>注魔炼成（中心是本体神秘镐），基座下面<b>只须立一枚乾卦石</b>——
     * 「立卦」诸条里最省的一档（表见
     * {@code data/skydeitymysticpivot/mysticpivot/trigram_gate/machines.json}）。
     * 立乾不立艮：{@code 《说卦》}曰「乾为金」，镐子是金石之器，归乾所辖。</p>
     */
    public static final DeferredItem<PickaxeItem> XUANSHU_PICKAXE = ITEMS.registerItem("xuanshu_pickaxe",
            props -> new PickaxeItem(XUANSHU_TIER, props),
            new Item.Properties().rarity(Rarity.EPIC)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            grayLore("item.skydeitymysticpivot.xuanshu_pickaxe.lore1"),
                            trigramLore("qian"))))
                    .attributes(PickaxeItem.createAttributes(XUANSHU_TIER, 1.0F, -2.8F)));

    /**
     * 玄枢魔晶（注魔稳定水晶）。lore 写明用法：成对、对称地摆在注魔矩阵四周。
     * 加入本体 {@code thaumcraft:infusion_stabilizers} 方块标签后，
     * 注魔矩阵会像对待蜡烛/头颅一样把它计入稳定计算。
     */
    public static final DeferredItem<Item> XUANSHU_CRYSTAL_CLUSTER = ITEMS.registerItem(
            "xuanshu_crystal_cluster",
            props -> new BlockItem(ModBlocks.XUANSHU_CRYSTAL_CLUSTER.get(), props),
            new Item.Properties().rarity(Rarity.EPIC)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            grayLore("block.skydeitymysticpivot.xuanshu_crystal_cluster.lore1"),
                            grayLore("block.skydeitymysticpivot.xuanshu_crystal_cluster.lore2"),
                            grayLore("block.skydeitymysticpivot.xuanshu_crystal_cluster.lore3")))));

    /**
     * 玄枢炎炉（{@code xuanshu_infernal_furnace}）：炼狱熔炉的单方块版本，
     * <b>三个并行加工位、一份燃料同时驱动三格</b>，1 秒一炉、一炉最多 64 件。
     * lore 一句功能 + 一句所需卦石（炉前须立巽）。
     */
    public static final DeferredItem<Item> XUANSHU_INFERNAL_FURNACE = ITEMS.registerItem(
            "xuanshu_infernal_furnace",
            props -> new BlockItem(ModBlocks.XUANSHU_INFERNAL_FURNACE.get(), props),
            new Item.Properties().rarity(Rarity.RARE)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            grayLore("block.skydeitymysticpivot.xuanshu_infernal_furnace.lore1"),
                            trigramLore("xun")))));

    /**
     * 玄枢台座：<b>须开坛方可用</b>——四角（3×3 的四个对角）各砌一块四象石砖，
     * 再手持法杖右键台座，之后才能往台面上摆东西。立于乾之地气时黑曜石化为哭泣的黑曜石。
     */
    public static final DeferredItem<Item> XUANSHU_PEDESTAL = ITEMS.registerItem(
            "xuanshu_pedestal",
            props -> new BlockItem(ModBlocks.XUANSHU_PEDESTAL.get(), props),
            new Item.Properties().rarity(Rarity.RARE));

    /**
     * 四象石砖：台座开坛用的多方块构件，四角各一块、以法杖点座即成。
     *
     * <p>刻的是「四象」而不是「八卦」：此处要的是<b>四方镇物</b>（东青龙 / 南朱雀 /
     * 西白虎 / 北玄武）拱卫坛场，与八卦那八枚按卦气立石的用途不是一回事，
     * 所以单开一个方块、一张配方。</p>
     */
    public static final DeferredItem<Item> SIXIANG_STONE_BRICK = ITEMS.registerItem(
            "sixiang_stone_brick",
            props -> new BlockItem(ModBlocks.SIXIANG_STONE_BRICK.get(), props),
            new Item.Properties().rarity(Rarity.UNCOMMON));

    /**
     * 玄枢坩埚：本体坩埚的单方块改良版，<b>三个并行工作面</b>可同炼三种配方。
     * lore 一句功能 + 一句所需卦石（炉前须立巽，见 {@code TrigramGate}）。
     */
    public static final DeferredItem<Item> XUANSHU_CRUCIBLE = ITEMS.registerItem(
            "xuanshu_crucible",
            props -> new BlockItem(ModBlocks.XUANSHU_CRUCIBLE.get(), props),
            new Item.Properties().rarity(Rarity.RARE)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            grayLore("block.skydeitymysticpivot.xuanshu_crucible.lore1"),
                            trigramLore("xun")))));

    /**
     * 玄枢炼金炉（{@code xuanshu_alchemy_furnace}）：与坩埚相反方向的机器——
     * 把物品烧成源质，再把源质推给周围 5×5×3 里的原质管／罐。
     * lore 一句功能 + 一句所需卦石（炉前须立巽）。
     */
    public static final DeferredItem<Item> XUANSHU_ALCHEMY_FURNACE = ITEMS.registerItem(
            "xuanshu_alchemy_furnace",
            props -> new BlockItem(ModBlocks.XUANSHU_ALCHEMY_FURNACE.get(), props),
            new Item.Properties().rarity(Rarity.RARE)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            grayLore("block.skydeitymysticpivot.xuanshu_alchemy_furnace.lore1"),
                            trigramLore("xun")))));

    /**
     * 玄枢净界石：立在哪儿就把那一片洗回来——所在区块的腐化方块、腐化群系、
     * 附近的咒流之垢，以及区块内玩家的全部扭曲与腐化负面效果一起扫掉。
     * lore <b>只留一句</b>：直述功能。
     */
    public static final DeferredItem<Item> XUANSHU_PURIFYING_STONE = ITEMS.registerItem(
            "xuanshu_purifying_stone",
            props -> new BlockItem(ModBlocks.XUANSHU_PURIFYING_STONE.get(), props),
            new Item.Properties().rarity(Rarity.EPIC)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            grayLore("block.skydeitymysticpivot.xuanshu_purifying_stone.lore1"),
                            grayLore("block.skydeitymysticpivot.xuanshu_purifying_stone.lore2")))));

    /** 八枚卦位石：须置于与卦象相符的方位方能生效（坎居北，其余类推）。 */
    public static final DeferredItem<Item> BAGUA_STONE_QIAN = baguaStone("bagua_stone_qian", "乾 · 西北");
    public static final DeferredItem<Item> BAGUA_STONE_DUI = baguaStone("bagua_stone_dui", "兑 · 西");
    public static final DeferredItem<Item> BAGUA_STONE_LI = baguaStone("bagua_stone_li", "离 · 南");
    public static final DeferredItem<Item> BAGUA_STONE_ZHEN = baguaStone("bagua_stone_zhen", "震 · 东");
    public static final DeferredItem<Item> BAGUA_STONE_XUN = baguaStone("bagua_stone_xun", "巽 · 东南");
    public static final DeferredItem<Item> BAGUA_STONE_KAN = baguaStone("bagua_stone_kan", "坎 · 北");
    public static final DeferredItem<Item> BAGUA_STONE_GEN = baguaStone("bagua_stone_gen", "艮 · 东北");
    public static final DeferredItem<Item> BAGUA_STONE_KUN = baguaStone("bagua_stone_kun", "坤 · 西南");

    /** lore 统一样式：灰色、不斜体。 */
    private static Component grayLore(String key) {
        return Component.translatable(key)
                .withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false));
    }

    /**
     * 「所需卦象方块」一句：正体靛蓝，与坎卦的象色同族，一眼认得出是卦象的话。
     * 六件杖柄／杖端各按自己的门禁要求列出卦名（分隔符走语言键，中英各一）。
     */
    private static Component trigramLore(String... ids) {
        MutableComponent names = Component.empty();
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) {
                names.append(Component.translatable("skydeitymysticpivot.gate.sep"));
            }
            names.append(Component.translatable("skydeitymysticpivot.trigram." + ids[i]));
        }
        return Component.translatable("item.skydeitymysticpivot.wand_part.lore.trigram", names)
                .withStyle(style -> style.withItalic(false).withColor(0x8FB0FF));
    }

    /** 卦位石物品：共用一条带方位参数的灰色 lore。 */
    private static DeferredItem<Item> baguaStone(String name, String direction) {
        return ITEMS.registerItem(name,
                props -> new BlockItem(ModBlocks.stoneForItem(name).get(), props),
                new Item.Properties().rarity(Rarity.UNCOMMON)
                        .component(DataComponents.LORE, new ItemLore(List.of(
                                Component.translatable("block.skydeitymysticpivot.bagua_stone.lore", direction)
                                        .withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false))))));
    }

    private ModItems() {
    }
}
