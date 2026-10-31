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

    /**
     * 八卦齐全（八种卦石各一块）。
     *
     * <p>★ 第二百一十三批从下面「法杖部件」那段挪到类首：{@link #XUANSHU_VOID_EYE} 的
     * lore 要用它，而静态字段按源码顺序初始化，留在后面拿到的会是 {@code null}。</p>
     */
    private static final String[] ALL_TRIGRAMS =
            {"qian", "dui", "li", "zhen", "xun", "kan", "gen", "kun"};

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
                            grayLore("item.skydeitymysticpivot.disenchant_book.lore1")))));

    /**
     * 玄枢双生书（第二百零五批新增）：跟<b>一本附魔书</b>合成，产出<b>两本一模一样的附魔书</b>
     * ——存着的附魔、自定义名、其余组件整份复制（见 {@link TwinBookRecipe}）。
     *
     * <p>与 {@link #DISENCHANT_BOOK 玄枢祛魔书} 是同一件事的两头：祛魔书把附魔从物品上
     * <b>取下来</b>、原物退回；双生书把一册附魔书<b>拓成两册</b>。<b>双生书本身一次性</b>
     * ——它就是变成第二本的那一本。</p>
     *
     * <p>{@code stacksTo(16)} 与祛魔书一致：合成时只吃一本（配方只对「被复制的那本书」
     * 要求单件，双生书那格不限数量）。贴图与祛魔书同一套底，只把绑带从粉紫旋到<b>蓝紫偏蓝</b>
     * （见 {@code tools/gen_book_tomes.py}）。</p>
     */
    public static final DeferredItem<Item> TWIN_BOOK = ITEMS.registerSimpleItem("twin_book",
            new Item.Properties().rarity(Rarity.RARE).stacksTo(16)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            grayLore("item.skydeitymysticpivot.twin_book.lore1")))));

    /**
     * 玄枢洞虚灵目（第七十八批新增，第八十批改项链栏）：一枚<b>Curios 项链饰品</b>。
     *
     * <p>戴进<b>项链栏</b>（{@code ring} 栏位（第一百零二批由项链栏改为戒指栏））后，效果对齐
     * 本体<b>揭示之护目镜</b>：显现灵气节点 + 瞄准机器浮出容器要素（{@code PlayerAbilityGear}
     * 对 {@code AURA_REVEALING} 与 {@code GOGGLES_OVERLAY} 都返回真），另给<b>护甲 +200%</b>
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
                            // 第二百一十三批：用户「戒指合成需要卦石，添加 lore」——门槛是八石齐立
                            // （mode=all），所以八种全写，格式与其余门禁物品同一句淡靛蓝。
                            trigramLore(ALL_TRIGRAMS)))));

    /**
     * 玄枢注灵香炉台（第八十八批新增，原名「玄枢灵气节点修改器」）：一尊 12/16 高的台座，
     * 形制是「底板 + 四角立柱 + 顶部方框，中间镂空」（第一百五十七批把模型与碰撞箱对齐）。
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
                            trigramLore("kan", "xun", "gen", "li"),
                            starLore(NANDOU_FIVE,
                                    Component.translatable("item.skydeitymysticpivot.xuanshu_ingot"))))));


    /**
     * 各件杖上「所需卦石」的集合（玄枢杖两件走类首的 {@link #ALL_TRIGRAMS}）。
     *
     * <p>受卦象门禁约束的注魔配方，其材料物品都写一句淡靛蓝「所需卦石：…」——
     * 玩家是在造这件东西的时候需要先立石，成品法杖拿到手时石头早立好了，写在那里只是啰嗦。</p>
     */
    /** 四象杖两件：只认艮、离、巽、坤这四卦。 */
    private static final String[] SIXIANG_TRIGRAMS = {"gen", "li", "xun", "kun"};

    /** 朱雀杖两件：只认离、艮两卦。 */
    private static final String[] ZHUQUE_TRIGRAMS = {"li", "gen"};

    /** 青龙杖两件（第一百六十一批）：只认震、巽两卦（木）。 */
    private static final String[] QINGLONG_TRIGRAMS = {"zhen", "xun"};

    /** 白虎杖两件（第一百六十一批）：只认乾、兑两卦（金）。 */
    private static final String[] BAIHU_TRIGRAMS = {"qian", "dui"};

    /** 玄武杖两件（第一百六十一批）：只认坎、坤两卦（水）。 */
    private static final String[] XUANWU_TRIGRAMS = {"kan", "kun"};

    /**
     * 杖柄 / 杖端小件的统一注册（第一百六十一批收成一行）。
     *
     * <p>八件部件原先一人一段五连行，参数只有「名色」和「所需卦石」两处不同，
     * 收成这个方法后各剩一行。lore 只写「所需卦石」一句——配方说明都在书里。</p>
     */
    private static DeferredItem<Item> wandPart(String id, Rarity rarity, String... trigrams) {
        return ITEMS.registerSimpleItem(id,
                new Item.Properties().rarity(rarity).stacksTo(16)
                        .component(DataComponents.LORE, new ItemLore(List.of(trigramLore(trigrams)))));
    }

    public static final DeferredItem<Item> ONYX_WAND_ROD = wandPart("onyx_wand_rod", Rarity.EPIC, ALL_TRIGRAMS);

    public static final DeferredItem<Item> GILDED_WAND_CAP = wandPart("gilded_wand_cap", Rarity.EPIC, ALL_TRIGRAMS);

    // 八卦四象法杖的两件部件（四象锭打造）
    public static final DeferredItem<Item> SIXIANG_WAND_ROD = wandPart("sixiang_wand_rod", Rarity.RARE, SIXIANG_TRIGRAMS);

    public static final DeferredItem<Item> BAGUA_WAND_CAP = wandPart("bagua_wand_cap", Rarity.RARE, SIXIANG_TRIGRAMS);

    // 朱雀杖部件：宏伟包裹杖柄 / 蝶舞杖端（红金配色，入门档）
    public static final DeferredItem<Item> ZHUQUE_WAND_ROD = wandPart("zhuque_wand_rod", Rarity.RARE, ZHUQUE_TRIGRAMS);

    public static final DeferredItem<Item> ZHUQUE_WAND_CAP = wandPart("zhuque_wand_cap", Rarity.RARE, ZHUQUE_TRIGRAMS);

    // 第一百六十一批：青龙 / 白虎 / 玄武 三圣兽部件（入门档，与朱雀同规格）
    public static final DeferredItem<Item> QINGLONG_WAND_ROD = wandPart("qinglong_wand_rod", Rarity.RARE, QINGLONG_TRIGRAMS);

    public static final DeferredItem<Item> QINGLONG_WAND_CAP = wandPart("qinglong_wand_cap", Rarity.RARE, QINGLONG_TRIGRAMS);

    public static final DeferredItem<Item> BAIHU_WAND_ROD = wandPart("baihu_wand_rod", Rarity.RARE, BAIHU_TRIGRAMS);

    public static final DeferredItem<Item> BAIHU_WAND_CAP = wandPart("baihu_wand_cap", Rarity.RARE, BAIHU_TRIGRAMS);

    public static final DeferredItem<Item> XUANWU_WAND_ROD = wandPart("xuanwu_wand_rod", Rarity.RARE, XUANWU_TRIGRAMS);

    public static final DeferredItem<Item> XUANWU_WAND_CAP = wandPart("xuanwu_wand_cap", Rarity.RARE, XUANWU_TRIGRAMS);

    // 赤羽焚天瞬核心（法杖核心:赤羽焚天瞬）：配任一杖柄，潜行右键记点、右键耗六要素各 10 点传送（见 TeleportCore）
    public static final DeferredItem<XuanshuFocusItem> FOCUS_TELEPORT = ITEMS.registerItem("focus_teleport",
            props -> new XuanshuFocusItem(props, ModFocuses.TELEPORT_ID),
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(1)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            grayLore("item.skydeitymysticpivot.focus_teleport.lore1"),
                            grayLore("item.skydeitymysticpivot.focus_teleport.lore2"),
                            trigramLore("li", "gen")))));

    // 烛龙衔时核心（法杖核心:烛龙衔时）：配任一杖柄，右键指一台机器，耗六要素各 25 点让它 16 倍速运转 32 秒，
    // 连续催动同一台倍率翻倍至 1024 封顶（见 TemporalCore）。
    // ★ 第一百六十六批：用户「合成表删去」——本核心没有注魔配方，也就没有「基座下面立艮卦石」那行，
    //   recipe json、研究页的配方页与门禁表条目一并删掉。
    // ★ 第二百一十批：用户「剩余三个法杖核心你帮我设计注魔合成……CHRONO 的注魔需要八种卦石 lore 写好」——
    //   配方（时钟居中 + 红石 / 水银 / 下界之星 / 玄枢耀光 / 八卦碎片，八卦各 32）与门禁一起回来了，
    //   而且这一枚是四个核心里唯一要**八种卦石齐立**的（mode=all，见 trigram_gate/machines.json），
    //   所以 lore 多出第三行点明「缺一不成」。
    public static final DeferredItem<XuanshuFocusItem> FOCUS_CHRONO = ITEMS.registerItem("focus_chrono",
            props -> new XuanshuFocusItem(props, ModFocuses.CHRONO_ID),
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(1)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            grayLore("item.skydeitymysticpivot.focus_chrono.lore1"),
                            grayLore("item.skydeitymysticpivot.focus_chrono.lore2"),
                            // ★ 第二百一十七批：用户「CHRONO 的 lore 中最后一行卦石的需求改为
                            //   和其他 lore 一样颜色一样，可以看 FOCUS_TELEPORT」——第三行不再用
                            //   灰色的整句说明，改成与赤羽焚天瞬同一个 trigramLore（淡靛蓝 0x8FB0FF、
                            //   不斜体），八种卦石全列出来。
                            trigramLore(ALL_TRIGRAMS)))));

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

    /**
     * 玄枢耀光（第二百一十批新增）：本体「闪耀之光」（{@code thaumcraft:nitor}）的近亲，
     * 玄枢体系里的那一味「引光」材料。
     *
     * <p>贴图见 {@code tools/gen_xuanshu_nitor.py}：剪影与明暗全抄本体那张 16×16 火焰，
     * 只把棕红 → 橙 → 黄换成一整条紫带（暗紫到紫白），飞散的四粒火星改星辰色
     * （淡青白）—— 用户口径「颜色用 tc4 闪耀之光改，改为紫色带一点星辰颜色」。</p>
     *
     * <p>没有独立合成表：它是注魔材料，乾元罡气与烛龙衔时两枚法核要用。</p>
     */
    public static final DeferredItem<Item> XUANSHU_NITOR = ITEMS.registerSimpleItem("xuanshu_nitor",
            new Item.Properties().rarity(Rarity.RARE));

    /**
     * 枫凝菼蒄（第二百一十二批新增）：枫叶里凝出来的一小簇赤橙色冰晶。
     *
     * <p>用户口径「添加一个新物品：枫凝菼蒄。打破枫叶树叶 20% 概率掉落。贴图为 16×16，
     * 不要太清晰，我的世界像素风。冰晶整体颜色为红色橘色」，另说「64 一组」——
     * 64 就是原版默认堆叠，所以这里<b>不写 stacksTo</b>。</p>
     *
     * <p>掉落在枫叶自己的战利品表里（{@code loot_table/blocks/maple_leaves.json}
     * 追加一个 20% 的独立池），不走事件：这样剪刀 / 精准采集拿叶子时也能掉，
     * 与「打破枫叶树叶就掉」这句话一致。贴图见 {@code tools/gen_maple_crystal.py}。</p>
     *
     * <p>★ 第二百一十三批：用户「MAPLE_CRYSTAL 放在翼膜后面，同时添加 lore：破坏枫树树叶 20%
     * 概率掉落」——创造栏挪到翎月翼膜正后面，lore 一句话写明出处与概率，格式与玄灵三材同。</p>
     */
    public static final DeferredItem<Item> MAPLE_CRYSTAL = ITEMS.registerSimpleItem("maple_crystal",
            new Item.Properties().rarity(Rarity.RARE)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            grayLore("item.skydeitymysticpivot.maple_crystal.lore")))));

    // 布匹：四象布匹（银白）/ 玄枢布匹（黑金）
    public static final DeferredItem<Item> SIXIANG_CLOTH = ITEMS.registerSimpleItem("sixiang_cloth",
            new Item.Properties().rarity(Rarity.RARE));

    public static final DeferredItem<Item> XUANSHU_CLOTH = ITEMS.registerSimpleItem("xuanshu_cloth",
            new Item.Properties().rarity(Rarity.EPIC));

    /**
     * 玄灵三材（第二百批新增）：三件小材料，<b>只注册 + 画贴图，别的什么都不做</b>
     * （用户原话：「只注册和画贴图……不用做任何别的」）。
     *
     * <p>没有合成表、没有功能、没有 lore、不进门禁、不进研究树 —— 就是三件
     * 摆在创造栏里看的材料。贴图走 {@code tools/gen_spirit_relics.py}：
     * 各取一件原版剪影（兔脚 / 骨头 / 幻翼膜）按模组画风重画，
     * 玉银 + 鎏金 + 一点灵光，16×16。</p>
     *
     * <p>第二百一十二批起<b>有了来源与 lore</b>：用户「给兔子脚：杀死兔子 40% 概率掉落；
     * 骨头：击杀凋零骷髅 40%；翼膜：击杀幻翼 80%，同时添加 lore」。
     * 掉落走 {@link SpiritRelicDrops}（{@code LivingDropsEvent}，只认玩家亲手击杀），
     * lore 一行写明出处与概率 —— 三件都是「打怪才有的材料」，不写清楚没人知道去哪儿找。</p>
     */
    public static final DeferredItem<Item> XUANLING_RABBIT_FOOT = ITEMS.registerSimpleItem("xuanling_rabbit_foot",
            new Item.Properties().rarity(Rarity.UNCOMMON)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            grayLore("item.skydeitymysticpivot.xuanling_rabbit_foot.lore")))));

    public static final DeferredItem<Item> SHENJIN_DEMON_BONE = ITEMS.registerSimpleItem("shenjin_demon_bone",
            new Item.Properties().rarity(Rarity.UNCOMMON)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            grayLore("item.skydeitymysticpivot.shenjin_demon_bone.lore")))));

    public static final DeferredItem<Item> LINGYUE_WING_MEMBRANE = ITEMS.registerSimpleItem("lingyue_wing_membrane",
            new Item.Properties().rarity(Rarity.UNCOMMON)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            grayLore("item.skydeitymysticpivot.lingyue_wing_membrane.lore")))));

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
                            trigramLore("qian", "dui")))));

    public static final DeferredItem<XuanshuFocusItem> FOCUS_GUARDIAN = ITEMS.registerItem("focus_guardian",
            props -> new XuanshuFocusItem(props, ModFocuses.GUARDIAN_ID),
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(1)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            Component.translatable("item.skydeitymysticpivot.focus_guardian.lore")
                                    .withStyle(style -> style.withColor(ChatFormatting.GRAY).withItalic(false)),
                            trigramLore("kan", "gen")))));

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
     * 三把工具（八卦镐 / 玄枢镐 / 玄枢锄）共用的攻速加成（第一百六十二批：攻击间隔减半）。
     *
     * <p>原版玩家基础攻速是 <b>4.0</b>（= 每 20 刻 / 5 刻一次），本档加成 +4.0 ⇒ 8.0
     * ⇒ 间隔 <b>2.5 刻</b>，正好是原来的<b>一半</b>。伤害另在各物品的
     * {@code createAttributes} 第一参里给（与 Tier 自带加成相加）：</p>
     * <ul>
     *   <li>八卦开山辟地镐：{@link #XUANSHU_TIER} 5.0 + 15.0 = <b>20</b>；</li>
     *   <li>玄枢翎凰焚天镐：{@link #XUANSHU_TOOL_TIER} 6.0 + 60.0 = <b>66</b>；</li>
     *   <li>玄枢玄冥镇海锄：{@link #XUANSHU_TOOL_TIER} 6.0 + 46.0 = <b>52</b>。</li>
     * </ul>
     */
    private static final float TOOL_ATTACK_SPEED = 4.0F;

    /**
     * 八卦开山辟地镐（注册 id 仍为 {@code bagua_pickaxe}）：挖掘等级与钻石镐相同、速度最快、耐久 52 000。
     *
     * <p>注魔炼成（中心是本体神秘镐），基座下面须立<b>乾、坎、兑三卦石各一方</b>
     * （表见 {@code data/skydeitymysticpivot/mysticpivot/trigram_gate/machines.json}）。
     * 乾为金、坎为险、兑为泽：金为器、险为地、泽为润，镐入地穿山须三卦同押。</p>
     *
     * <p>lore 只留一句「所需卦石」（淡靛蓝），其余说明都在书里。</p>
     *
     * <p>第一百六十批起换用 {@link BaguaPickaxeItem}：普通右键在
     * 正常 / 精准采集 / 自动熔炼三个掉落模式间循环（见 {@link PickaxeDropModes}）。
     * 类换了，注册 id 没换，注魔配方里的中心物照旧认这一枚。</p>
     */
    public static final DeferredItem<BaguaPickaxeItem> BAGUA_PICKAXE = ITEMS.registerItem("bagua_pickaxe",
            props -> new BaguaPickaxeItem(XUANSHU_TIER, props),
            new Item.Properties().rarity(Rarity.EPIC)
                    .attributes(PickaxeItem.createAttributes(XUANSHU_TIER, 15.0F, TOOL_ATTACK_SPEED))
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
                            .attributes(PickaxeItem.createAttributes(XUANSHU_TOOL_TIER, 60.0F, TOOL_ATTACK_SPEED))
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
                    .attributes(DiggerItem.createAttributes(XUANSHU_TOOL_TIER, 46.0F, TOOL_ATTACK_SPEED))
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
                            grayLore("block.skydeitymysticpivot.xuanshu_crystal_cluster.lore4"),
                            // 第二百二十一批：星象线下移到第 7 阶，注魔须立离、震、乾、坎四卦。
                            trigramLore("li", "zhen", "qian", "kan")))));

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
     * lore 一句功能 + 一句所需卦石（第二百二十一批起要求八石齐立，与玄枢杖两件同档）。
     */
    public static final DeferredItem<Item> XUANSHU_REPLICATOR = ITEMS.registerItem(
            "xuanshu_replicator",
            props -> new BlockItem(ModBlocks.XUANSHU_REPLICATOR.get(), props),
            new Item.Properties().rarity(Rarity.RARE).stacksTo(MACHINE_STACK)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            grayLore("block.skydeitymysticpivot.xuanshu_replicator.lore1"),
                            trigramLore(ALL_TRIGRAMS),
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
     *
     * <p><b>第二百一十一批：lore 只留卦石那一行</b>（用户「lore 只保留卦石那一行，其他的删掉」）——
     * 原来那句「注魔祭炼而成的白玉之心。奥术台与注灵香炉台，皆以此为基。」连键带代码一并删掉，
     * 成品说明都在魔导书里，物品上只留「基座下面立坎、巽、艮」这条用时真会卡住人的条件。</p>
     */
    public static final DeferredItem<Item> XUANSHU_SPIRIT_CORE = ITEMS.registerItem(
            "xuanshu_spirit_core",
            props -> new BlockItem(ModBlocks.XUANSHU_SPIRIT_CORE.get(), props),
            new Item.Properties().rarity(Rarity.RARE)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            trigramLore("kan", "xun", "gen")))));

    /**
     * 玄枢虚空水晶塔（第一百七十四批新增；第一百九十三批定形制；第一百九十八批改名）：
     * 一台机器 + 背后一座 8×8 / 高 13 格的水晶塔，塔齐了就自动运转，产出 {@code #c:ores} 里的所有矿。
     *
     * <p>第一百九十九批：lore 按用户口径压成<b>两行</b>（「我们需要重新集结部队，艾尔连接着我们所有人。」+
     * 「虚空产出所有矿物，该方块可以自动输出产出」），原来那条讲搭建 / 收回界面的第三行删掉 ——
     * 界面本身空手右键就开，不必占 lore 的位置。</p>
     *
     * <p>第二百一十二批曾补回<b>第三行</b>讲锭槽；第二百一十三批用户「矿机的 lore 你新加的两行
     * 不要」——那一行（在提示框里折成两行）连同语言键一起撤掉，lore 回到两行。
     * 「锭能加效率」这件事改由界面自己说：空槽悬停三行说明 + 槽下一行「效率 +N」。</p>
     */
    public static final DeferredItem<Item> XUANSHU_VOID_MINING = ITEMS.registerItem(
            "xuanshu_void_mining",
            props -> new BlockItem(ModBlocks.XUANSHU_VOID_MINING.get(), props),
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(MACHINE_STACK)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            grayLore("block.skydeitymysticpivot.xuanshu_void_mining.lore1"),
                            grayLore("block.skydeitymysticpivot.xuanshu_void_mining.lore2")))));

    /**
     * 玄枢源质罐（第一百九十三批新增；第一百九十四批起内容走物品组件）：一只装得下所有要素的罐子。
     *
     * <p>堆叠 16：空罐能叠 —— 空罐<b>不挂</b> {@code jar_essentia} 组件（见
     * {@link ModDataComponents#JAR_ESSENTIA}），所以和新合成出来的罐子是同一摞；
     * 装了东西的罐子组件不同，原版按组件分堆，自然就分开，不用另外写逻辑。</p>
     *
     * <p>第二百一十批：lore 按用户口径压成<b>一行</b>「可以存储 32 种不同的源质，每种最多 2048 容量。」
     * （原来讲鎏金液面与通要素面的两行删掉）—— 容量与种类是这条材料唯一需要一眼看到的事。</p>
     */
    public static final DeferredItem<Item> XUANSHU_ESSENTIA_JAR = ITEMS.registerItem(
            "xuanshu_essentia_jar",
            props -> new BlockItem(ModBlocks.XUANSHU_ESSENTIA_JAR.get(), props),
            new Item.Properties().rarity(Rarity.RARE).stacksTo(16)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            grayLore("block.skydeitymysticpivot.xuanshu_essentia_jar.lore1")))));

    /**
     * 玄枢传送门（第一百九十七批新增；第一百九十八批加界面与广场；第一百九十九批提品阶）：
     * 科技风折跃门，空手右键开界面、按「一键搭建」在背后铺开那座折跃门广场。
     *
     * <p>第一百九十九批按用户口径与水晶塔对齐：<b>史诗品质（紫色名字）+ 堆叠 16</b>
     * （{@link #MACHINE_STACK}）—— 两件东西在创造栏里挨着摆，长得也该是一路的。
     * 第二百二十六批按用户口径去掉物品 lore（「传送门和星象仪 lore 全去掉」）。</p>
     */
    public static final DeferredItem<Item> XUANSHU_PORTAL = ITEMS.registerItem(
            "xuanshu_portal",
            props -> new BlockItem(ModBlocks.XUANSHU_PORTAL.get(), props),
            new Item.Properties().rarity(Rarity.EPIC).stacksTo(MACHINE_STACK));

    /**
     * 星象仪（第一百八十六批）：白瓷鎏金悬球台，纯装饰，暂无功能。
     *
     * <p>第二百二十六批按用户口径去掉物品 lore（「传送门和星象仪 lore 全去掉」）。</p>
     */
    public static final DeferredItem<Item> ASTROLABE = ITEMS.registerItem(
            "astrolabe",
            props -> new BlockItem(ModBlocks.ASTROLABE.get(), props),
            new Item.Properties().rarity(Rarity.RARE));

    /**
     * 南北斗枢玄星盘（第一百零六批）：青玉盘身 + 墨玉盘面，盘面刻北斗七星与南斗六星；
     * 也是「星斗十二座」的唯一构件，十二方按三列四行一摆、法杖一敲即成阵。
     */
    public static final DeferredItem<Item> STAR_DIAL = ITEMS.registerItem("star_dial",
            props -> new BlockItem(ModBlocks.STAR_DIAL.get(), props),
            new Item.Properties().rarity(Rarity.RARE)
                    .component(DataComponents.LORE, new ItemLore(List.of(
                            // 第二百二十一批：星象线下移到第 7 阶，注魔须立离、震、乾、坎四卦。
                            trigramLore("li", "zhen", "qian", "kan")))));

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

    /**
     * 枫树三件套的物品形态（第一百六十七批）：枫木原木 / 枫树叶 / 枫树树苗。
     *
     * <p>都是<b>建筑与自然构件</b>，不套 {@link #MACHINE_STACK}（保持原版 64 堆叠，
     * 好让玩家成组搬运），也不挂卦石门禁 lore——树是长出来的，不是注魔注出来的。</p>
     *
     * <p>枫木进了 {@code #minecraft:logs}（熔炉燃料 300 tick、按原版木料计），
     * 枫树苗进了 {@code #minecraft:saplings}（100 tick）；斧头可采原木、锄头可采树叶
     * （{@code #minecraft:mineable/axe} / {@code hoe}）。树形与掉落见
     * {@link MapleTreeGrower} 与 {@code data/.../loot_table/blocks/maple_*.json}。</p>
     */
    public static final DeferredItem<Item> MAPLE_LOG = ITEMS.registerItem(
            "maple_log",
            props -> new BlockItem(ModBlocks.MAPLE_LOG.get(), props),
            new Item.Properties());

    /**
     * 枫木木板（第一百六十八批）：1 块枫木原木无序合成出 4 块（见
     * {@code data/skydeitymysticpivot/recipe/maple_planks.json}）。
     * 进 {@code #minecraft:planks} → 顺带斧采与燃料 300 tick。
     * 创造栏位置：枫木原木之后、枫树叶之前。
     */
    public static final DeferredItem<Item> MAPLE_PLANKS = ITEMS.registerItem(
            "maple_planks",
            props -> new BlockItem(ModBlocks.MAPLE_PLANKS.get(), props),
            new Item.Properties());

    public static final DeferredItem<Item> MAPLE_LEAVES = ITEMS.registerItem(
            "maple_leaves",
            props -> new BlockItem(ModBlocks.MAPLE_LEAVES.get(), props),
            new Item.Properties());

    public static final DeferredItem<Item> MAPLE_SAPLING = ITEMS.registerItem(
            "maple_sapling",
            props -> new BlockItem(ModBlocks.MAPLE_SAPLING.get(), props),
            new Item.Properties());

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
