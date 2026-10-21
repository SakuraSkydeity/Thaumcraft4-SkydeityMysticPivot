package skydeity.mysticpivot;

import net.minecraft.core.component.DataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

    /**
     * 机器类方块的堆叠上限（第一百五十一批）。
     *
     * <p>用户：「所有的机器类，包括玄枢充能器、台座等，都改为 16 堆叠上限」。九台功能方块
     * （炎炉 / 坩埚 / 炼金炉 / 复刻台 / 台座 / 充能器 / 奥术台 / 注灵台 / 净界石）统一走这个数；
     * <b>建筑与成组摆放的构件不在此列</b>——星盘要十二块一座、八卦石各一枚、四象石砖四角一块，
     * 一律保持原版 64，好让玩家成组搬运。</p>
     */
    private static final int MACHINE_STACK = 16;

    /** 北斗七星全（给玄枢锭当合成前置）。 */
    private static final Star[] BEIDOU_ALL = {
            Star.BEIDOU_TIANSHU, Star.BEIDOU_TIANXUAN, Star.BEIDOU_TIANJI, Star.BEIDOU_TIANQUAN,
            Star.BEIDOU_YUHENG, Star.BEIDOU_KAIYANG, Star.BEIDOU_YAOGUANG,
    };

    /** 南斗五星齐明（给奥术台 / 注灵台当前置）。 */
    private static final Star[] NANDOU_FIVE = {
            Star.NANDOU_TIANFU, Star.NANDOU_TIANLIANG, Star.NANDOU_TIANJI,
            Star.NANDOU_TIANTONG, Star.NANDOU_TIANXIANG,
    };

    /** 南斗六星全（给复刻台当前置）。 */
    private static final Star[] NANDOU_ALL = {
            Star.NANDOU_TIANFU, Star.NANDOU_TIANLIANG, Star.NANDOU_TIANJI,
            Star.NANDOU_TIANTONG, Star.NANDOU_TIANXIANG, Star.NANDOU_QISHA,
    };

    public static final DeferredItem<AkashicRecordsItem> AKASHIC_RECORDS = ITEMS.registerItem("akashic_records",
            AkashicRecordsItem::new, new Item.Properties().rarity(Rarity.EPIC).stacksTo(16)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            Component.translatable("item.skydeitymysticpivot.akashic_records.lore")
                                    .withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false))))));

    /**
     * 授业卷轴：右键补全<b>全部研究笔记</b>（第七十二批新增）。
     *
     * <p>与阿卡西记录正好接成一前一后：阿卡西给的是「要素研究点」，本物直接
     * 把本体研究目录里所有未完成的条目做完。见 {@link TeachingScrollItem}。</p>
     */
    public static final DeferredItem<TeachingScrollItem> TEACHING_SCROLL = ITEMS.registerItem("teaching_scroll",
            TeachingScrollItem::new, new Item.Properties().rarity(Rarity.EPIC).stacksTo(16)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            Component.translatable("item.skydeitymysticpivot.teaching_scroll.lore")
                                    .withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false))))));

    /**
     * 玄枢祛魔书（第一百零一批新增）：跟<b>一件**单件**带附魔的物品</b>合成，
     * 把那件物品上的附魔<b>整条拓下来</b>，变成一本附魔书（{@code minecraft:enchanted_book}）；
     * <b>原物的附魔随之散尽，而原物本身被退回</b>（耐久、改名、其余组件全都留着）。
     *
     * <p>玄枢祛魔书自己是<b>一次性</b>的——它就是变成那本附魔书的那一本。
     * 配方与「物品为什么不会被吃掉」的机制全在 {@link DisenchantBookRecipe}；
     * 这里只管物品本体、稀有度与两行 lore。</p>
     *
     * <p>{@code stacksTo(16)}：一格可以摞着放，合成时<b>只吃一本</b>
     * （{@code matches} 只对「被祛魔的那件」要求单件，玄枢祛魔书那格不限数量）。</p>
     */
    public static final DeferredItem<Item> DISENCHANT_BOOK = ITEMS.registerSimpleItem("disenchant_book",
            new Item.Properties().rarity(Rarity.RARE).stacksTo(16)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            grayLore("item.skydeitymysticpivot.disenchant_book.lore1"),
                            grayLore("item.skydeitymysticpivot.disenchant_book.lore2")))));

    /**
     * 玄枢洞虚灵目（第七十八批新增，第八十批改项链栏）：一枚<b>Curios 项链饰品</b>。
     *
     * <p>戴进<b>项链栏</b>（{@code ring} 栏位（第一百零二批由项链栏改为戒指栏））后，效果对齐
     * 本体<b>揭示之护目镜</b>：显现灵气节点（{@code AuraRevealingGear}）、瞄准机器
     * 浮出容器要素（{@code GogglesOverlayGear}），另给<b>护甲 +200%</b>
     * （{@code ICurioItem#getAttributeModifiers}，ADD_MULTIPLIED_TOTAL）。三条能力的
     * 落点与「为什么不走数据组件」的考校都写在 {@link XuanshuVoidEyeItem} 的类注释里。</p>
     *
     * <p>栏位合法性双保险：Java 侧 {@code CuriosApi.registerCurio}
     * （见 {@code ModEvents.onCommonSetup}）让物品成为「已登记饰品」，
     * 数据侧 {@code data/curios/tags/item/ring.json} 再把它挂进
     * {@code curios:ring} 物品标签——戒指槽的 validator 就是这条标签，
     * 本体诸护身符亦走此路（玩家身上的 necklace 槽由本体
     * {@code data/thaumcraft/curios/entities/player.json} 开好，无需另声明）。</p>
     */
    public static final DeferredItem<XuanshuVoidEyeItem> XUANSHU_VOID_EYE = ITEMS.registerItem("xuanshu_void_eye",
            XuanshuVoidEyeItem::new,
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(1)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            grayLore("item.skydeitymysticpivot.xuanshu_void_eye.lore1"),
                            grayLore("item.skydeitymysticpivot.xuanshu_void_eye.lore2"),
                            grayLore("item.skydeitymysticpivot.xuanshu_void_eye.lore3"),
                            grayLore("item.skydeitymysticpivot.xuanshu_void_eye.lore4")))));

    /**
     * 玄枢注灵台（第八十八批新增，原名「玄枢灵气节点修改器」）：一尊 12/16 高的台座，
     * 台面上坐一尊小香炉（第一百批起仿原版附魔台，不再是整格方块）。
     *
     * <p>用法（见 {@link ModBlocks.XuanshuNodeModifierBlock}）：把台放下，<b>缸中节点摆在
     * 它正上方</b>；右键开界面，把<b>任意天域之华</b>放进华槽、<b>玄枢锭</b>放进锭槽；
     * 然后<b>手持法杖右键本台</b>即为罐中节点运转——扣六元各 129 点 + 一瓶天华 + 一枚锭，
     * 让节点<b>先补齐六门基础要素、六门齐了之后一直随机抽</b>。全部逻辑在
     * {@link ModBlockEntities.XuanshuNodeModifierBlockEntity#applyWithWand}。</p>
     */
    public static final DeferredItem<BlockItem> XUANSHU_NODE_MODIFIER = ITEMS.registerItem(
            "xuanshu_node_modifier",
            props -> new BlockItem(ModBlocks.XUANSHU_NODE_MODIFIER.get(), props),
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(MACHINE_STACK)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            grayLore("block.skydeitymysticpivot.xuanshu_node_modifier.lore1"),
                            trigramLore("kan", "xun", "gen"),
                            starLore(NANDOU_FIVE,
                                    Component.translatable("item.skydeitymysticpivot.xuanshu_ingot"))))));

    /**
     * 玄枢奥术台（第八十九批新增）：外貌照原版附魔台（贴图与模型直接复制进本 mod）。
     *
     * <p>用法：右键本台开界面放入要升附魔的物品；然后<b>手持任意法杖右键本台</b>——
     * 从法杖吸 **520 点魔力**（centivis），成功后物品<b>所有附魔 +1</b>
     * （单条最高 128）。魔力不够一分不抽。逻辑见
     * {@link XuanshuArcaneTableBlockEntity#applyWithWand}。</p>
     */
    public static final DeferredItem<BlockItem> XUANSHU_ARCANE_TABLE = ITEMS.registerItem(
            "xuanshu_arcane_table",
            props -> new BlockItem(ModBlocks.XUANSHU_ARCANE_TABLE.get(), props),
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(MACHINE_STACK)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            grayLore("block.skydeitymysticpivot.xuanshu_arcane_table.lore1"),
                            trigramLore("kan", "xun", "gen"),
                            starLore(NANDOU_FIVE,
                                    Component.translatable("item.skydeitymysticpivot.xuanshu_ingot"))))));


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

    // 赤羽焚天瞬核心（法杖核心:赤羽焚天瞬）：配任一杖柄，潜行右键记点、右键耗六要素各 10 点传送（见 TeleportCore）
    public static final DeferredItem<XuanshuFocusItem> FOCUS_TELEPORT = ITEMS.registerItem("focus_teleport",
            props -> new XuanshuFocusItem(props, ModFocuses.TELEPORT_ID),
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(1)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            grayLore("item.skydeitymysticpivot.focus_teleport.lore1"),
                            grayLore("item.skydeitymysticpivot.focus_teleport.lore2"),
                            trigramLore("gen")))));

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
    public static final DeferredItem<Item> XUANSHU_INGOT = ITEMS.registerItem("xuanshu_ingot",
            props -> new Item(props),
            new Item.Properties().rarity(Rarity.EPIC)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            starLore(BEIDOU_ALL,
                                    Component.translatable("item.skydeitymysticpivot.xuanshu_shard"))))));

    /**
     * 未烬的玄枢锭（第一百三十三批）：玄枢锭的「半成品」。
     *
     * <p>贴图沿用玄枢锭的旧图（玄枢锭本身这一批提亮了），丢进玄枢坩埚、
     * 八卦要素各 4 点，就炼成正式那枚。</p>
     */
    public static final DeferredItem<Item> XUANSHU_INGOT_UNFIRED = ITEMS.registerSimpleItem("xuanshu_ingot_unfired",
            new Item.Properties().rarity(Rarity.RARE));

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
     * 两个自制法杖核心：镇狱碎青峰（蓝紫，右键把目标方块化为掉落物）与
     * 乾元罡气（鎏金橘黄，右键获得一颗免死之心）。lore 用灰色注释作用。
     */
    public static final DeferredItem<XuanshuFocusItem> FOCUS_COSMOS = ITEMS.registerItem("focus_cosmos",
            props -> new XuanshuFocusItem(props, ModFocuses.COSMOS_ID),
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(1)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            Component.translatable("item.skydeitymysticpivot.focus_cosmos.lore")
                                    .withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false)),
                            trigramLore("gen")))));

    public static final DeferredItem<XuanshuFocusItem> FOCUS_GUARDIAN = ITEMS.registerItem("focus_guardian",
            props -> new XuanshuFocusItem(props, ModFocuses.GUARDIAN_ID),
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(1)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            Component.translatable("item.skydeitymysticpivot.focus_guardian.lore")
                                    .withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false)),
                            trigramLore("gen")))));

    /**
     * 八卦开山辟地镐的挖掘材质：耐久 52 000（对应玄枢杖容量数字）、挖掘速度全场最快；
     * {@code getIncorrectBlocksForDrops} 走<b>钻石档</b>（与钻石镐同一挖掘等级）。
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
            return BlockTags.INCORRECT_FOR_DIAMOND_TOOL;
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
     * 玄枢镐 / 玄枢锄的挖掘材质：<b>全属性压过下界合金</b>（下界合金：耐久 2031、速度 9.0、
     * 攻击加成 4、附魔 15）。耐久 52 000（约 25 倍，与模组「玄枢杖容量」同一个数字）、
     * 速度 40.0、攻击加成 6.0、附魔 25。
     *
     * <p>挖掘等级走 {@code INCORRECT_FOR_NETHERITE_TOOL}：原版没有「下界合金之上」的挖掘等级
     * （下界合金已是顶格），能挖下界合金能挖的一切即是顶格；「大于下届合金」落在耐久 /
     * 速度 / 攻击 / 附魔这四项硬数值上。修复材料为玄枢锭（{@link #XUANSHU_INGOT}，
     * 方法体内惰性取值——类初始化时注册表还没就绪，不能在此处 {@code .get()}）。</p>
     */
    private static final Tier XUANSHU_TOOL_TIER = new Tier() {
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
            return 6.0F;
        }

        @Override
        public TagKey<Block> getIncorrectBlocksForDrops() {
            return BlockTags.INCORRECT_FOR_NETHERITE_TOOL;
        }

        @Override
        public int getEnchantmentValue() {
            return 25;
        }

        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(ModItems.XUANSHU_INGOT.get());
        }
    };

    /**
     * 八卦开山辟地镐（注册 id 仍为 {@code bagua_pickaxe}）：挖掘等级与钻石镐相同、速度最快、耐久 52 000。
     *
     * <p>注魔炼成（中心是本体神秘镐），基座下面须立<b>乾、坎、兑三卦石各一方</b>
     * （表见 {@code data/skydeitymysticpivot/mysticpivot/trigram_gate/machines.json}）。
     * 乾为金、坎为险、兑为泽：金为器、险为地、泽为润，镐入地穿山须三卦同押。</p>
     *
     * <p>lore 只留一句「所需卦石」（淡靛蓝），其余说明都在书里。</p>
     */
    public static final DeferredItem<PickaxeItem> BAGUA_PICKAXE = ITEMS.registerItem("bagua_pickaxe",
            props -> new PickaxeItem(XUANSHU_TIER, props),
            new Item.Properties().rarity(Rarity.EPIC)
                    .attributes(PickaxeItem.createAttributes(XUANSHU_TIER, 1.0F, 0.0F))
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            trigramLore("qian", "kan", "dui")))));

    /**
     * 玄枢翎凰焚天镐（注册 id 仍是 {@code xuanshu_pickaxe}）：下界合金之上的独立档位，
     * {@link #XUANSHU_TOOL_TIER}。
     *
     * <p>第六十五批起改**注魔升级**：中心是八卦开山辟地镐（{@link #BAGUA_PICKAXE}），
     * 座上玄枢碎片与玄枢锭交错各四——「升炉」而不是另起一炉。
     * ⚠ 本体注魔<b>默认不保留中心物的附魔与属性</b>（{@code InfusionRecipe#assembleFromCentral}
     * 在 result 非空时直接 {@code result.copy()}，中心物的组件一概不带过来；只有
     * {@code input_transformation} 那条特殊通道才会基于中心物变换，本配方不用）。
     * 按用户「默认不可以就不要加」的口径，**不做**任何保留处理，书中页面也已写明
     * 「升炉前先在本体砧上附好魔」。</p>
     *
     * <p>第七十四批按用户要求改名 **玄枢翎凰焚天镐**（原「玄枢镐」），并照同一批的
     * 第 6 条扩成三用神器；第七十五批再把「锄」换成「铲」：<b>既镐、既斧、既铲</b>；
     * 潜行右键切换「单方块 / 隧道」，隧道模式挖掉一方块即沿面向凿穿十六格；
     * <b>自带时运 XVI</b>。全部实现都在 {@link XuanshuHeavenPickaxeItem} 里，
     * 注册这一行只负责选类。</p>
     *
     * <p>注魔门禁为<b>乾、坎、兑三卦石各一方</b>，见
     * {@code mysticpivot/trigram_gate/machines.json}；lore 只留「所需卦石」一句。</p>
     *
     * <p>第一百零四批把注册名理顺：八卦开山辟地镐改为 {@code bagua_pickaxe}，
     * 焚天镐从 {@code xuanshu_pickaxe_2} 收回 {@code xuanshu_pickaxe}（旧 id 的 {@code _2}
     * 后缀本就只是「被占用」的临时办法）。</p>
     */
    public static final DeferredItem<XuanshuHeavenPickaxeItem> XUANSHU_PICKAXE =
            ITEMS.registerItem("xuanshu_pickaxe",
                    props -> new XuanshuHeavenPickaxeItem(XUANSHU_TOOL_TIER, props),
                    new Item.Properties().rarity(Rarity.EPIC)
                            .attributes(PickaxeItem.createAttributes(XUANSHU_TOOL_TIER, 1.0F, 0.0F))
                            .component(DataComponents.LORE, new ItemLore(List.of(
                                    trigramLore("qian", "kan", "dui")))));

    /**
     * 玄枢玄冥镇海锄（注册 id 仍是 {@code xuanshu_hoe}）：与焚天镐同一档位（{@link #XUANSHU_TOOL_TIER}）。
     * 第六十五批起同样由**八卦开山辟地镐注魔升级**而来（中心还是那柄镐），
     * 座上玄枢碎片与玄枢锭交错各四；附魔不保留（同 {@link #XUANSHU_PICKAXE} 的说明）。
     *
     * <p>第七十四批改名为 **玄枢玄冥镇海锄**（原「玄枢锄」）。第一百零四批起注魔门禁为
     * <b>乾、坎、兑三卦石各一方</b>（乾为金、坎为水、兑为泽，金土水三气相济），
     * lore 留一句「所需卦石」。</p>
     *
     * <p>第七十六批照用户第 2 条把这柄锄头做成**农事神器**：右键一次翻 9×9 的耕地、
     * 潜行右键往方块上方放一格水、主手选中时每秒催熟周围 9×9 的作物（无视光照水分）。
     * 全部实现都在 {@link XuanshuHoeItem} 里，注册这一行只负责选类。</p>
     */
    public static final DeferredItem<XuanshuHoeItem> XUANSHU_HOE = ITEMS.registerItem("xuanshu_hoe",
            props -> new XuanshuHoeItem(XUANSHU_TOOL_TIER, props),
            new Item.Properties().rarity(Rarity.EPIC)
                    .attributes(DiggerItem.createAttributes(XUANSHU_TOOL_TIER, 0.0F, 0.0F))
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            trigramLore("qian", "kan", "dui")))));

    /**
     * 玄枢水晶簇（注魔稳定水晶，注册 id 仍为 {@code xuanshu_crystal_cluster}）。
     * 第六十五批起从零重做：旧版七批「方块拼柱子」的历史全部作废，改为
     * {@code models/crystal_cluster.obj}（六棱晶柱 + 尖头 + 岩座，neoforge:obj）。
     * lore 四句：一句状物（六棱晶丛生岩座）+ 三句用法。加入本体
     * {@code thaumcraft:infusion_stabilizers} 方块标签后，注魔矩阵会像对待蜡烛/头颅一样
     * 把它计入稳定计算。
     */
    public static final DeferredItem<Item> XUANSHU_CRYSTAL_CLUSTER = ITEMS.registerItem(
            "xuanshu_crystal_cluster",
            props -> new BlockItem(ModBlocks.XUANSHU_CRYSTAL_CLUSTER.get(), props),
            new Item.Properties().rarity(Rarity.EPIC)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            grayLore("block.skydeitymysticpivot.xuanshu_crystal_cluster.lore1"),
                            grayLore("block.skydeitymysticpivot.xuanshu_crystal_cluster.lore2"),
                            grayLore("block.skydeitymysticpivot.xuanshu_crystal_cluster.lore3"),
                            grayLore("block.skydeitymysticpivot.xuanshu_crystal_cluster.lore4")))));

    /**
     * 玄枢炎炉（{@code xuanshu_infernal_furnace}）：炼狱熔炉的单方块版本，
     * <b>三个并行加工位、一份燃料同时驱动三格</b>，1 秒一炉、一炉最多 64 件。
     * lore 一句功能 + 一句所需卦石（炉前须立巽）。
     */
    public static final DeferredItem<Item> XUANSHU_INFERNAL_FURNACE = ITEMS.registerItem(
            "xuanshu_infernal_furnace",
            props -> new BlockItem(ModBlocks.XUANSHU_INFERNAL_FURNACE.get(), props),
            new Item.Properties().rarity(Rarity.RARE).stacksTo(MACHINE_STACK)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            grayLore("block.skydeitymysticpivot.xuanshu_infernal_furnace.lore1"),
                            trigramLore("xun"),
                            furnaceStarLore(Star.BEIDOU_MOUTH,
                                    Component.translatable("item.skydeitymysticpivot.xuanshu_ingot"))))));

    /**
     * 玄枢复刻台：左格放模板、右格原样复刻——连同全部数据组件一模一样。
     * lore 一句功能 + 一句所需卦石（炉前须立巽，与三台工作方块同款）。
     */
    public static final DeferredItem<Item> XUANSHU_REPLICATOR = ITEMS.registerItem(
            "xuanshu_replicator",
            props -> new BlockItem(ModBlocks.XUANSHU_REPLICATOR.get(), props),
            new Item.Properties().rarity(Rarity.RARE).stacksTo(MACHINE_STACK)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            grayLore("block.skydeitymysticpivot.xuanshu_replicator.lore1"),
                            trigramLore("xun"),
                            starLore(NANDOU_ALL,
                                    Component.translatable("block.minecraft.dragon_egg"))))));

    /**
     * 玄枢台座：<b>须开坛方可用</b>——四角（3×3 的四个对角）各砌一块四象石砖，
     * 再手持法杖右键台座，之后才能往台面上摆东西。立于乾之地气时黑曜石化为哭泣的黑曜石。
     */
    public static final DeferredItem<Item> XUANSHU_PEDESTAL = ITEMS.registerItem(
            "xuanshu_pedestal",
            props -> new BlockItem(ModBlocks.XUANSHU_PEDESTAL.get(), props),
            new Item.Properties().rarity(Rarity.RARE).stacksTo(MACHINE_STACK));

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
            new Item.Properties().rarity(Rarity.RARE).stacksTo(MACHINE_STACK)
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
            new Item.Properties().rarity(Rarity.RARE).stacksTo(MACHINE_STACK)
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
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(MACHINE_STACK)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            grayLore("block.skydeitymysticpivot.xuanshu_purifying_stone.lore1"),
                            grayLore("block.skydeitymysticpivot.xuanshu_purifying_stone.lore2"),
                            trigramLore("kun", "zhen", "gen")))));

    /**
     * 玄枢沟灵核心（第一百零二批新增）：一块白玉鎏金的祭炼核心。
     *
     * <p>两件台（{@link ModBlocks#XUANSHU_ARCANE_TABLE 奥术台} /
     * {@link ModBlocks#XUANSHU_NODE_MODIFIER 注灵台}）的注魔配方都以它为中心；
     * 它自己也是注魔产物（玄枢锭 + 四象锭 + 八卦之盐）。</p>
     */
    public static final DeferredItem<Item> XUANSHU_SPIRIT_CORE = ITEMS.registerItem(
            "xuanshu_spirit_core",
            props -> new BlockItem(ModBlocks.XUANSHU_SPIRIT_CORE.get(), props),
            new Item.Properties().rarity(Rarity.RARE)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            grayLore("block.skydeitymysticpivot.xuanshu_spirit_core.lore1"),
                            trigramLore("kan", "xun", "gen")))));

    /**
     * 南北斗枢玄星盘（第一百零六批）：青玉盘身 + 墨玉盘面，盘面刻北斗七星与南斗六星；
     * 也是「星斗十二座」的唯一构件，十二方按三列四行一摆、法杖一敲即成阵。
     */
    public static final DeferredItem<Item> STAR_DIAL = ITEMS.registerItem("star_dial",
            props -> new BlockItem(ModBlocks.STAR_DIAL.get(), props),
            new Item.Properties().rarity(Rarity.RARE));

    /**
     * 玄枢充能器：立在灵气节点正下方，通红石十秒把节点充能成充能灵气节点——
     * 比本体「稳定器 + 节点 + 换能器」三段装配更快、更省，还把节点每门要素上限翻倍。
     * lore 三句直述：用法、耗时与消耗、上限增益与「断电即还原」。
     */
    public static final DeferredItem<Item> XUANSHU_CHARGER = ITEMS.registerItem(
            "xuanshu_charger",
            props -> new BlockItem(ModBlocks.XUANSHU_CHARGER.get(), props),
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(MACHINE_STACK)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            grayLore("block.skydeitymysticpivot.xuanshu_charger.lore1"),
                            grayLore("block.skydeitymysticpivot.xuanshu_charger.lore2"),
                            grayLore("block.skydeitymysticpivot.xuanshu_charger.lore3"),
                            // 第一百零二批：最后一行（「挖走会炸」那条）按用户要求删去，
                            // 换成与三台工作方块同款的「所需卦石」灰字
                            trigramLore("xun", "qian", "kan")))));

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
     * 造一枚「自带时运 XVI」的玄枢翎凰焚天镐（第一百零二批）。
     *
     * <p>为什么创造页签要用这个方法而不是直接 {@code accept(物品)}：附魔是数据包注册项，
     * 物品注册那一刻拿不到 {@code Holder}，只能等有注册表可查时再写。
     * 合成 / 注魔那两条路走 {@code onCraftedPostProcess}（见
     * {@link XuanshuHeavenPickaxeItem}），创造页签这条路则由
     * {@code CreativeModeTab.ItemDisplayParameters#holders()} 提供注册表。</p>
     */
    public static ItemStack heavenPickaxe(net.minecraft.core.HolderLookup.Provider registries) {
        ItemStack stack = new ItemStack(XUANSHU_PICKAXE.get());
        XuanshuHeavenPickaxeItem.applyFortune(registries, stack);
        return stack;
    }

    /**
     * 「所需卦石」一句：<b>淡靛蓝（{@code 0x8FB0FF}）、不斜体</b>。
     *
     * <p>第一百零三批曾把这句统一成方块的灰；第一百零四批用户改口径：
     * 「LORE 中所需卦石改回淡靛蓝 0x8FB0FF」——卦石是「气」的事，靛蓝比灰更配这句，
     * 于是回到靛蓝，并把所有受卦石门禁约束的物品统统补上这句（见各类注册处）。</p>
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

    /**
     * 「所需星象」一句：淡靛蓝（与「所需卦石」同色 {@code 0x8FB0FF}）、不斜体。
     *
     * <p>第一百四十八批：星象前置从 JEI/EMI「信息」条目迁到物品 lore。内容按用户口径——
     * 需要的星座用「、」分割，再括号里写激活物；
     * 炎炉那条走 {@link #furnaceStarLore}：英文括号 + 「双倍产物烧矿：」前缀。</p>
     *
     * <p>第一百五十一批：<b>不再有「无激活物」的条目</b>——用户指出玄枢锭那一行少了括号，
     * 顺带把星位改成真核供物（见 {@code StarRite}）。五处一律带括号：炎炉写玄枢锭，
     * 玄枢锭（坩埚）写玄枢碎片，复刻台写玄枢碎片，<b>奥术台与注灵台写玄枢锭</b>
     * （第一百五十二批：「奥术台、注灵台用玄枢锭」）。</p>
     */
    private static Component starLore(Star[] stars, Component activation) {
        return starLore("item.skydeitymysticpivot.lore.star", stars, activation, false);
    }

    /** 玄枢炎炉专属：双倍产物烧矿（英文括号）。 */
    private static Component furnaceStarLore(Star[] stars, Component activation) {
        return starLore("item.skydeitymysticpivot.lore.star_double", stars, activation, true);
    }

    private static Component starLore(String labelKey, Star[] stars, Component activation, boolean englishParens) {
        MutableComponent out = Component.translatable(labelKey);
        for (int i = 0; i < stars.length; i++) {
            if (i > 0) {
                out.append(Component.translatable("skydeitymysticpivot.gate.sep"));
            }
            out.append(stars[i].displayName());
        }
        if (activation != null) {
            out.append(englishParens ? "(" : "（")
                    .append(activation)
                    .append(englishParens ? ")" : "）");
        }
        return out.withStyle(style -> style.withItalic(false).withColor(0x8FB0FF));
    }

    private ModItems() {
    }
}
