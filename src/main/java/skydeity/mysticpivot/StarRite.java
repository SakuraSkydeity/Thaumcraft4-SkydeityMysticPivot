package skydeity.mysticpivot;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;


/**
 * 星象仪式：<b>斗口四星各供一枚玄枢锭 → 工作方块的产出翻倍</b>（第一百二十批）。
 *
 * <p>用户：「添加玄枢熔炉在边上，引导线为红色。然后天枢 + 天璇 + 天玑 + 天权放置玄枢锭，
 * 可以使产物双倍产出（不要额外加烧炼配方，就单独设计双倍产出），因为要适配所有燃烧配方」。</p>
 *
 * <h2>为什么只做「倍数」不做「配方」</h2>
 * <p>炎炉（以及以后接上来的机器）各自认自己的配方表；如果给星象另开一套配方，
 * 换个配方就不灵了。这里只回答一个是非题——<b>「此刻该不该翻倍」</b>——
 * 由机器在自己的出货口乘上去，于是<b>任何</b>配方都自动适用（原版的、附属的、数据包的）。</p>
 *
 * <h2>仪式条件（三条同时满足）</h2>
 * <ol>
 *   <li>这台机器摆在某座阵的 <b>5×6 外圈</b>里；</li>
 *   <li>盘面这一斗里，亮的<b>恰好</b>是天枢、天璇、天玑、天权四颗（北斗斗口合，多一颗都不算）；</li>
 *   <li>这四格上供的<b>每一件都是玄枢锭</b>。</li>
 * </ol>
 *
 * <p>★ 别每 tick 调（要扫方块）：机器自己按 20 tick 缓存一个布尔即可，
 * 见 {@code XuanshuInfernalFurnaceBlockEntity#refreshStarRite()}。</p>
 *
 * <h2>第一百五十一批：星位一律核供物</h2>
 * <p>用户口径：「玄枢锭的 lore 也要用括号写清需要哪个激活物，星位都要真检测」。原来只有
 * 炎炉（供玄枢锭）与复刻台（供玄枢碎片）核物品，坩埚与两台台只看「亮不亮」——摆一块圆石
 * 也算亮。<b>现在四台机器全部按供物判</b>：</p>
 * <ul>
 *   <li>{@link #doubled}：斗口四星各供<b>玄枢锭</b>（炎炉翻倍）；</li>
 *   <li>{@link #beidouShard}：北斗七座各供<b>玄枢碎片</b>（坩埚炼玄枢锭）；</li>
 *   <li>{@link #nandouFiveIngot}：南斗五星各供<b>玄枢锭</b>（奥术台 / 注灵台）；</li>
 *   <li>{@link #nandouSixEggs}：南斗六星各供<b>龙蛋</b>（复刻台）。</li>
 * </ul>
 *
 * <h2>第一百五十二批：两台台改供玄枢锭</h2>
 * <p>用户：「奥术台、注灵台用玄枢锭」。这两台原先按玄枢碎片判，与复刻台同物，玩家摆阵时
 * 容易把三种供物混在一处；改成玄枢锭之后，<b>北斗面供碎片（炼锭）、南斗面供锭（做台）</b>，
 * 一斗一物，看盘面就知道该摆什么。</p>
 */
public final class StarRite {

    /** 仪式成立时产出乘以几。 */
    public static final int MULTIPLIER = 2;

    /** 北斗七星全（第一百五十一批：玄枢坩埚的供物校验，含只有北斗面才有的摇光）。 */
    private static final Star[] BEIDOU_ALL = {
            Star.BEIDOU_TIANSHU, Star.BEIDOU_TIANXUAN, Star.BEIDOU_TIANJI, Star.BEIDOU_TIANQUAN,
            Star.BEIDOU_YUHENG, Star.BEIDOU_KAIYANG, Star.BEIDOU_YAOGUANG,
    };

    /** 南斗五星（给两台机器当前置）：天府、天梁、天机、天同、天相。 */
    private static final Star[] NANDOU_FIVE = {
            Star.NANDOU_TIANFU, Star.NANDOU_TIANLIANG, Star.NANDOU_TIANJI,
            Star.NANDOU_TIANTONG, Star.NANDOU_TIANXIANG,
    };

    /** 南斗六星全（给玄枢复刻台当前置）：天府、天梁、天机、天同、天相、七杀。 */
    private static final Star[] NANDOU_ALL = {
            Star.NANDOU_TIANFU, Star.NANDOU_TIANLIANG, Star.NANDOU_TIANJI,
            Star.NANDOU_TIANTONG, Star.NANDOU_TIANXIANG, Star.NANDOU_QISHA,
    };

    /** 北斗斗口四星：天枢、天璇、天玑、天权。 */
    private static final Star[] CORE = Star.BEIDOU_MOUTH;

    private StarRite() {
    }

    /** 这台机器此刻吃不吃星象加成。 */
    public static boolean doubled(Level level, BlockPos workPos) {
        if (level == null) {
            return false;
        }
        BlockPos origin = StarWork.boardOrigin(level, workPos);
        if (origin == null) {
            return false;
        }
        Constellation constellation = StarWork.at(level, origin);
        // ★ 只要斗口四星都亮着就算数——多亮一颗（玉衡、开阳……）不该把仪式搅黄，
        //   第一百二十三批之前要求「恰好四颗」，玩家多放一件就永远不翻倍。
        for (Star star : CORE) {
            if (!constellation.has(star)) {
                return false;
            }
        }
        // 四格的星位上必须都是玄枢锭（星位由图案定，格子也跟着走）
        StarDialBlock.Chart chart = level.getBlockState(origin).getValue(StarDialBlock.CHART);
        int found = 0;
        for (StarDialBlock.Seat seat : StarDialBlock.seats(chart)) {
            if (!isCore(seat.star())) {
                continue;
            }
            found++;
            BlockEntity be = level.getBlockEntity(StarDialBlock.cellAt(origin, seat.row(), seat.col()));
            if (!(be instanceof StarDialBlockEntity dial)
                    || !dial.getItem().is(ModItems.XUANSHU_INGOT.get())) {
                return false;
            }
        }
        return found == CORE.length;
    }

    /**
     * 玄枢坩埚的前置（第一百五十一批加严）：<b>北斗七座的星位上每一件都是玄枢碎片</b>。
     *
     * <p>第一百三十三批到第一百五十批，坩埚只查「北斗全曜」——也就是七颗星都<b>亮着</b>，
     * 星位上摆什么都行（一块圆石也算）。用户第一百五十一批改口径：
     * 「玄枢锭的 lore 也用括号写清需要哪个激活物，并且星位都要真检测」，于是这里跟
     * {@link #doubled}（斗口四星供玄枢锭）、{@link #nandouSixEggs}（南斗六星供龙蛋）
     * 对齐，把供物钉死成<b>玄枢碎片</b>。</p>
     *
     * <p>北斗的七颗星只在 {@code chart=SEVEN} 的盘面上有座（含摇光），所以这一判同时锁定了
     * 「盘面得翻到北斗」——玩家要炼玄枢锭，就得把十二块盘翻成北斗面、七格各供一枚碎片。</p>
     */
    public static boolean beidouShard(Level level, BlockPos workPos) {
        if (level == null) {
            return false;
        }
        BlockPos origin = StarWork.boardOrigin(level, workPos);
        if (origin == null) {
            return false;
        }
        if (level.getBlockState(origin).getValue(StarDialBlock.CHART) != StarDialBlock.Chart.SEVEN) {
            return false;
        }
        int found = 0;
        for (StarDialBlock.Seat seat : StarDialBlock.seats(StarDialBlock.Chart.SEVEN)) {
            if (!isBeidou(seat.star())) {
                continue;
            }
            found++;
            if (!holdsShard(level, origin, seat)) {
                return false;
            }
        }
        return found == BEIDOU_ALL.length;
    }

    /**
     * 南斗五星的前置（第一百五十二批）：<b>天府、天梁、天机、天同、天相 五座各供一枚玄枢锭</b>。
     *
     * <p>给玄枢奥术台与玄枢注灵台用。第一百五十一批这里判的是玄枢碎片，与复刻台（六星供碎片）
     * 撞了物；用户第一百五十二批改口径：「奥术台、注灵台用玄枢锭」。于是北斗面供碎片炼锭、
     * 南斗面供锭做台，两斗两物互不混淆。七杀那一座不参与：南斗五星齐了就够，
     * 多供一枚不强求、也不妨碍。</p>
     */
    public static boolean nandouFiveIngot(Level level, BlockPos workPos) {
        if (level == null) {
            return false;
        }
        BlockPos origin = StarWork.boardOrigin(level, workPos);
        if (origin == null) {
            return false;
        }
        if (level.getBlockState(origin).getValue(StarDialBlock.CHART) != StarDialBlock.Chart.SIX) {
            return false;
        }
        int found = 0;
        for (StarDialBlock.Seat seat : StarDialBlock.seats(StarDialBlock.Chart.SIX)) {
            if (!isNandouFive(seat.star())) {
                continue;
            }
            found++;
            if (!holds(level, origin, seat, ModItems.XUANSHU_INGOT.get())) {
                return false;
            }
        }
        return found == NANDOU_FIVE.length;
    }

    /** 这一座星位上供的是不是指定物品。 */
    private static boolean holds(Level level, BlockPos origin, StarDialBlock.Seat seat, Item item) {
        BlockEntity be = level.getBlockEntity(StarDialBlock.cellAt(origin, seat.row(), seat.col()));
        return be instanceof StarDialBlockEntity dial && dial.getItem().is(item);
    }

    /** 这一座星位上供的是不是玄枢碎片。 */
    private static boolean holdsShard(Level level, BlockPos origin, StarDialBlock.Seat seat) {
        return holds(level, origin, seat, ModItems.XUANSHU_SHARD.get());
    }

    /**
     * 玄枢复刻台的前置：<b>南斗六星全亮，且六格的星位上每一枚都是龙蛋</b>。
     *
     * <p>用户第一百五十三批：「玄枢复刻台改为龙蛋，六个位子都是龙蛋」。原先是六枚玄枢碎片，
     * 与坩埚、两台台的供物挤在同一档材料上；换成龙蛋之后，复刻台成了这套阵列里**最贵的一档**
     * ——复刻本就是「一模一样地再做一个」，收一枚龙蛋当门票并不算过。</p>
     *
     * <p>与 {@link #doubled} 同款——扫一遍南斗六格的星位，逐格核对供物。
     * 南斗只落在 {@code chart=SIX} 的盘面上，所以这一判自然就锁定了「盘面得是南斗」。</p>
     */
    public static boolean nandouSixEggs(Level level, BlockPos workPos) {
        if (level == null) {
            return false;
        }
        BlockPos origin = StarWork.boardOrigin(level, workPos);
        if (origin == null) {
            return false;
        }
        StarDialBlock.Chart chart = level.getBlockState(origin).getValue(StarDialBlock.CHART);
        int found = 0;
        for (StarDialBlock.Seat seat : StarDialBlock.seats(chart)) {
            if (!isNandou(seat.star())) {
                continue;
            }
            found++;
            BlockEntity be = level.getBlockEntity(StarDialBlock.cellAt(origin, seat.row(), seat.col()));
            if (!(be instanceof StarDialBlockEntity dial)
                    || !dial.getItem().is(Items.DRAGON_EGG)) {
                return false;
            }
        }
        return found == NANDOU_ALL.length;
    }

    private static boolean isCore(Star star) {
        for (Star core : CORE) {
            if (core == star) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNandou(Star star) {
        for (Star nandou : NANDOU_ALL) {
            if (nandou == star) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNandouFive(Star star) {
        for (Star nandou : NANDOU_FIVE) {
            if (nandou == star) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBeidou(Star star) {
        for (Star beidou : BEIDOU_ALL) {
            if (beidou == star) {
                return true;
            }
        }
        return false;
    }
}
