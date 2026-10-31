package skydeity.mysticpivot;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 创造页签，条目按类型分组排序：
 * 功能物品（阿卡西记录 / 授业卷轴 → 洞虚守一戒 → **祛魔书 / 双生书，第二百零五批按用户口径
 * 挪到戒指正后面**）→ 玄灵三材（骨头 / 翼膜 / **枫凝菼蒄** / 兔子脚，第二百一十三批把枫凝菼蒄
 * 插在翼膜后面）→ 四把成品法杖（终局 → 常规 → 中高阶 → 入门）
 * → 杖柄 → 杖端 → 诸锭 → 诸碎片（八卦碎片 → 玄枢碎片）→ 其余材料
 * → 工具（八卦开山辟地镐 / 玄枢翎凰焚天镐 / 玄枢玄冥镇海锄）→ 四个法杖核心
 * → 方块（炎炉 → 坩埚 → 炼金炉 → 沟灵核心 → 虚空水晶塔 → 传送门 → 源质罐
 *   → **充能器**（第二百二十六批按用户口径挪到源质罐正后面）→ 星象仪 → 玄星盘
 *   → 香炉台 → 奥术台 → **复刻台**（第二百二十六批按用户口径挪到奥术台正后面）
 *   → 台座 → 四象石砖 → 水晶簇 / 净界石
 *   → **枫木原木 / 枫木木板 / 枫树树苗 / 枫树叶** → 八卦位石八块）。
 */
public final class ModTab {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MysticPivot.MODID);

    /**
     * 页签本体的注册。
     *
     * <p>★ 第七十七批清理：原来这里挂着一个 {@code public static final DeferredHolder TAB}
     * 当公开句柄，但全项目从没引用过（注册靠的是上面的 {@link #TABS}，
     * {@code DeferredRegister} 自己会持有已注册的 holder）。于是去掉那个字段，
     * 注册调用直接放进静态初始化块。</p>
     */
    static {
        TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + MysticPivot.MODID))
                    .icon(ModWandParts::gildedCharged)
                    .displayItems((parameters, output) -> {
                        // 一、功能物品
                        output.accept(ModItems.AKASHIC_RECORDS.get());
                        output.accept(ModItems.TEACHING_SCROLL.get());
                        // 玄枢洞虚灵目（第七十八批）：饰品，跟在两件功能物品后
                        output.accept(ModItems.XUANSHU_VOID_EYE.get());
                        // 两本「玄枢书」（第二百零五批按用户口径「将祛魔书放到戒指后面」）：
                        // 祛魔书（第一百零一批）+ 双生书（第二百零五批）成对收在戒指正后面
                        // ——三件都是「拿在手上/戴在身上使」的一类，与随后的灵兽材料分开
                        output.accept(ModItems.DISENCHANT_BOOK.get());
                        output.accept(ModItems.TWIN_BOOK.get());
                        // 玄灵三材（第二百批新增；第二百零二批按用户口径「三个小物品骨头 / 翼膜 /
                        // 兔子脚放在戒指后面」从材料区挪到洞虚守一戒正后面）：
                        // 三件都是「灵兽身上掉下来的东西」，挨着戒指这类饰品才对
                        output.accept(ModItems.SHENJIN_DEMON_BONE.get());
                        output.accept(ModItems.LINGYUE_WING_MEMBRANE.get());
                        // 枫凝菼蒄（第二百一十三批按用户口径「放在翼膜后面」）：
                        // 它也是「打东西才掉的材料」，跟着三材一起收在戒指后面
                        output.accept(ModItems.MAPLE_CRYSTAL.get());
                        output.accept(ModItems.XUANLING_RABBIT_FOOT.get());
                        // 二、成品法杖
                        output.accept(ModWandParts.charged());
                        output.accept(ModWandParts.gildedCharged());
                        output.accept(ModWandParts.baguaCharged());
                        output.accept(ModWandParts.zhuqueCharged());
                        output.accept(ModWandParts.qinglongCharged());
                        output.accept(ModWandParts.baihuCharged());
                        output.accept(ModWandParts.xuanwuCharged());
                        // 三、杖柄（放一起）
                        output.accept(ModItems.SIXIANG_WAND_ROD.get());
                        output.accept(ModItems.ONYX_WAND_ROD.get());
                        output.accept(ModItems.ZHUQUE_WAND_ROD.get());
                        output.accept(ModItems.QINGLONG_WAND_ROD.get());
                        output.accept(ModItems.BAIHU_WAND_ROD.get());
                        output.accept(ModItems.XUANWU_WAND_ROD.get());
                        // 四、杖端（放一起）
                        output.accept(ModItems.BAGUA_WAND_CAP.get());
                        output.accept(ModItems.GILDED_WAND_CAP.get());
                        output.accept(ModItems.ZHUQUE_WAND_CAP.get());
                        output.accept(ModItems.QINGLONG_WAND_CAP.get());
                        output.accept(ModItems.BAIHU_WAND_CAP.get());
                        output.accept(ModItems.XUANWU_WAND_CAP.get());
                        // 五、诸锭（全部放一起）
                        output.accept(ModItems.AZURE_DRAGON_INGOT.get());
                        output.accept(ModItems.VERMILION_BIRD_INGOT.get());
                        output.accept(ModItems.WHITE_TIGER_INGOT.get());
                        output.accept(ModItems.BLACK_TORTOISE_INGOT.get());
                        // 未烬的玄枢锭：玄枢锭的半成品，排在它前面（未烬 → 坩埚炼成）
                        output.accept(ModItems.XUANSHU_INGOT_UNFIRED.get());
                        output.accept(ModItems.XUANSHU_INGOT.get());
                        // 六、诸碎片
                        //     ★ 第七十六批：八卦碎片与玄枢碎片换位 —— 八卦碎片在前。
                        //     八卦碎片是「四象锭 → 八卦碎片」这条主线的产物，玄枢碎片比它更靠后，
                        //     放在前面才顺。
                        output.accept(ModItems.AZURE_DRAGON_SHARD.get());
                        output.accept(ModItems.VERMILION_BIRD_SHARD.get());
                        output.accept(ModItems.WHITE_TIGER_SHARD.get());
                        output.accept(ModItems.BLACK_TORTOISE_SHARD.get());
                        output.accept(ModItems.BAGUA_SHARD.get());
                        output.accept(ModItems.XUANSHU_SHARD.get());
                        // 七、其余玄枢材料
                        output.accept(ModItems.XUANSHU_BAGUA_SALT.get());
                        output.accept(ModItems.XUANSHU_NITOR.get());
                        output.accept(ModItems.SIXIANG_CLOTH.get());
                        output.accept(ModItems.XUANSHU_CLOTH.get());
                        // 八、工具
                        output.accept(ModItems.BAGUA_PICKAXE.get());
                        // 玄枢翎凰焚天镐（第一百零二批）：创造栏里这一枚也要自带时运 XVI，
                        // 所以现造一个栈（附魔要注册表，页签回调里有 holders()）
                        output.accept(ModItems.heavenPickaxe(parameters.holders()));
                        output.accept(ModItems.XUANSHU_HOE.get());
                        // 九、法杖核心（四个放一起，方块前）
                        output.accept(ModItems.FOCUS_COSMOS.get());
                        output.accept(ModItems.FOCUS_GUARDIAN.get());
                        output.accept(ModItems.FOCUS_TELEPORT.get());
                        // 烛龙衔时（第一百六十四批）：催动机器的时序核心
                        output.accept(ModItems.FOCUS_CHRONO.get());
                        // 十、方块（炎炉 → 坩埚 → 炼金炉 → **充能器** → 台座 → 四象石砖
                        //     → 水晶簇 / 净界石 → 八卦位石）
                        //     ★ 第六十七批：把新做的几件（水晶簇 / 净界石 / 充能器）挪到
                        //     八卦位石**前面**，让那八块位石作为一个家族收在页签末尾。
                        //     ★ 第七十一批：充能器再往前挪到**三台工作方块那一连的后一位**
                        //     （炎炉 / 坩埚 / 炼金炉 三台机器本就连成一排），它本就是围着
                        //     「往机器上通要素」这件活计转的，挨着它们才对。
                        output.accept(ModItems.XUANSHU_INFERNAL_FURNACE.get());
                        output.accept(ModItems.XUANSHU_CRUCIBLE.get());
                        output.accept(ModItems.XUANSHU_ALCHEMY_FURNACE.get());
                        // 玄枢沟灵核心（第一百零二批）：两件台的注魔之基，摆在台之前
                        output.accept(ModItems.XUANSHU_SPIRIT_CORE.get());
                        // 玄枢虚空水晶塔（第一百七十四批；第一百八十六批重做；第一百九十八批改名）
                        output.accept(ModItems.XUANSHU_VOID_MINING.get());
                        // 玄枢传送门（第一百九十七批）：第一百九十九批按用户口径挪到水晶塔<b>正后面</b>——
                        // 两台是同一条产线（塔出矿、门立广场），在页签里也该挨着
                        output.accept(ModItems.XUANSHU_PORTAL.get());
                        // 玄枢源质罐（第一百九十三批）：一只罐子装所有要素，挨着机器那排收尾
                        output.accept(ModItems.XUANSHU_ESSENTIA_JAR.get());
                        // 玄枢充能器（第二百二十六批按用户口径「充能器放玄枢源质管后面」：
                        // 从三炉那排挪到源质罐正后面）
                        output.accept(ModItems.XUANSHU_CHARGER.get());
                        // 星象仪（第一百八十六批）：矿机旧成型模型单拎出来的装饰方块
                        output.accept(ModItems.ASTROLABE.get());
                        // 南北斗枢玄星盘（第一百零六批）：一方方块，既是星盘也是星斗阵的构件
                        output.accept(ModItems.STAR_DIAL.get());
                        // 玄枢注灵香炉台（第八十九批改方块）：用户要求摆在充能器与基座之间
                        output.accept(ModItems.XUANSHU_NODE_MODIFIER.get());
                        // 玄枢奥术台（第八十九批）：附魔升级台，与另两台中机器同组
                        output.accept(ModItems.XUANSHU_ARCANE_TABLE.get());
                        // 玄枢复刻台（第二百二十六批按用户口径「复刻台放奥术台后面」：
                        // 从三炉那排挪到奥术台正后面）
                        output.accept(ModItems.XUANSHU_REPLICATOR.get());
                        output.accept(ModItems.XUANSHU_PEDESTAL.get());
                        output.accept(ModItems.SIXIANG_STONE_BRICK.get());
                        output.accept(ModItems.XUANSHU_CRYSTAL_CLUSTER.get());
                        output.accept(ModItems.XUANSHU_PURIFYING_STONE.get());
                        // 枫树四件套（第一百六十七批新增；第一百六十八~一百七十批按用户口径三次调整）：
                        // 现序为 枫木原木 → 枫木木板 → 枫树树苗 → 枫树叶，整体排在八卦位石之前
                        // ——它们是「长出来的树」，与成组摆放的构件分开
                        output.accept(ModItems.MAPLE_LOG.get());
                        output.accept(ModItems.MAPLE_PLANKS.get());
                        output.accept(ModItems.MAPLE_SAPLING.get());
                        output.accept(ModItems.MAPLE_LEAVES.get());
                        output.accept(ModItems.BAGUA_STONE_QIAN.get());
                        output.accept(ModItems.BAGUA_STONE_DUI.get());
                        output.accept(ModItems.BAGUA_STONE_LI.get());
                        output.accept(ModItems.BAGUA_STONE_ZHEN.get());
                        output.accept(ModItems.BAGUA_STONE_XUN.get());
                        output.accept(ModItems.BAGUA_STONE_KAN.get());
                        output.accept(ModItems.BAGUA_STONE_GEN.get());
                        output.accept(ModItems.BAGUA_STONE_KUN.get());
                    })
                .build());
    }

    private ModTab() {
    }
}
