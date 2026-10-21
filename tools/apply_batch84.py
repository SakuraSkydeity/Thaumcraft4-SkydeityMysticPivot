# -*- coding: utf-8 -*-
"""第 84 批改动：玄枢台座「一次只纳一件」+ 吸取丝线续期修复 + 慢吸节奏 + 文案同步。

背景（用户两条诉求）：
  1. 台座改为只能放一个物品上去；吸收了要素、条件对了就合成。
  2. 「我没看到类似注魔祭坛要素被吸过去的动画」——根因：台座 serverTick 里
     「吸取丝线续期」排在多个 return 之后，正在吸要素的周期（缓冲区未满提前返回）
     根本走不到，批号推不出去、客户端一条丝线也画不出来（坩埚版是每 tick 独立跑的，
     所以坩埚看得见、台座看不见）。

涉及文件：
  * XuanshuPedestalBlockEntity.java — 槽上限 1 / 续期挪到最前 / 抽吸节奏 / 注释
  * ModBlocks.java（XuanshuPedestalBlock）— ticker 注释 / 类注释 / 放置注释
  * XuanshuPedestalEffects.java — 类文档补修正说明
  * lang/zh_cn.json / lang/en_us.json — 四组书页文案

每个替换都带「必须存在且唯一」断言，跑一遍即全部落盘。
用法：python tools/apply_batch84.py
"""

import io
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

BE = os.path.join(ROOT, "src/main/java/skydeity/mysticpivot/XuanshuPedestalBlockEntity.java")
MB = os.path.join(ROOT, "src/main/java/skydeity/mysticpivot/ModBlocks.java")
FX = os.path.join(ROOT, "src/main/java/skydeity/mysticpivot/XuanshuPedestalEffects.java")
ZH = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/lang/zh_cn.json")
EN = os.path.join(ROOT, "src/main/resources/assets/skydeitymysticpivot/lang/en_us.json")

REPLACEMENTS = {}  # path -> [(old, new), ...]


def add(path, old, new):
    REPLACEMENTS.setdefault(path, []).append((old, new))


# ======================================================================
# 一、XuanshuPedestalBlockEntity.java
# ======================================================================

# 1) 类文档：吸纳要素段落重写（单件口径 + 丝线可见）
add(BE,
    """ * <p><b>吸纳要素</b>：成坛之后，台座会像<b>注魔矩阵</b>那样从<b>基座附近 5×5</b>
 * 的「要素源」（原质罐、储液池、镜像……）里直接抽取<b>本地卦气对应的那一支</b>要素，
 * 攒在台座里（{@link #bufferedAmount}）——不必先接管子、也不是非端点不可，
 * 罐子摆在旁边就会自己吸（吸取时放一条「源→台座」的丝线，与注魔矩阵同款）。
 * 卦石就是这么来的：台面摆石砖、立于对应卦象的地气、罐子供着对应要素，
 * 攒够 {@value #ESSENTIA_PER_CRAFT} 份一件，整堆石砖便尽数化为该卦之石
 * （与旧坩埚配方同价：一件石砖 + 本卦要素 8 份）。</p>""",
    """ * <p><b>吸纳要素</b>：成坛之后，台座会像<b>注魔矩阵</b>那样从<b>基座附近 5×5</b>
 * 的「要素源」（原质罐、储液池、镜像……）里直接抽取<b>本地卦气对应的那一支</b>要素，
 * 攒在台座里（{@link #bufferedAmount}）——不必先接管子、也不是非端点不可，
 * 罐子摆在旁边就会自己吸。吸取时一条要素丝线从源一路牵进台座
 * （{@link XuanshuPedestalEffects}，与注魔矩阵同款）；每周期只吸
 * {@value #DRAW_PER_BATCH} 份，把八份拉成两秒上下的连续丝线——「看得见在被吸」。</p>
 *
 * <p>卦石就是这么来的：台面放石砖（<b>一次只纳一件</b>，见 {@link #items} 的槽上限）、
 * 立于对应卦象的地气、罐子供着对应要素，攒够 {@value #ESSENTIA_PER_CRAFT} 份，
 * 那件石砖便化为该卦之石（与旧坩埚配方同价：一件石砖 + 本卦要素 8 份）。</p>""")

# 2) 搜索范围注释：对齐实际搜索体
add(BE,
    "     * 抽要素的搜索范围：以台座为中心 ±{@value #SEARCH_RANGE}（5×5×5 见方）。",
    "     * 抽要素的搜索范围：以台座为中心 ±{@value #SEARCH_RANGE}（水平 5×5、上下两格之内）。")

# 3) 常量：删 ESSENTIA_CAP，DRAW_PER_BATCH 16→2，加快车道常量
add(BE,
    """    /**
     * 吸纳缓冲的上限：一整组（64 件）卦石的量。
     *
     * <p>台面最多放一组石砖，一组就要 {@code 8 × 64} 份——上限照此取，
     * 于是「缓冲」永远够手头这一堆用，不必再多。</p>
     */
    public static final int ESSENTIA_CAP = ESSENTIA_PER_CRAFT * 64;

    /** 每个推进周期（10 tick）最多从罐子里抽多少份。 */
    private static final int DRAW_PER_BATCH = 16;""",
    """    /**
     * 每个推进周期（10 tick）最多从罐子里抽多少份。
     *
     * <p>★ 第 84 批由 16 调到 2：一口气抽完只在屏幕上闪一下、玩家根本看不见；
     * 每周期 2 份 → 一件卦石的 8 份要吸 4 个周期（约 2 秒），丝线便成一条
     * <b>看得见的连续粒子流</b>，观感与注魔矩阵抽要素一致。</p>
     */
    private static final int DRAW_PER_BATCH = 2;

    /**
     * 旧存档兼容的抽吸上限：台面若还压着旧口径的「整堆」（见 {@link #items} 的槽上限），
     * 走这条快车道——不然一整组 64 件按每周期 2 份要吸上两分钟。
     */
    private static final int DRAW_PER_BATCH_BULK = 16;""")

# 4) items：槽上限 1（一次只纳一件的落点）
add(BE,
    """    /** 单格台面；台面一有变动就存档并下发。 */
    private final ItemStackHandler items = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            syncToClient();
        }
    };""",
    """    /**
     * 单格台面；台面一有变动就存档并下发。
     *
     * <p>★ 第 84 批起<b>每格只纳一件</b>（{@code getSlotLimit → 1}）：放置时
     * {@code insertItem} 自动只收一件、余数留在玩家手里，以新换旧同此。
     * 于是「整堆石砖一起变卦石」的旧口径不复存在——一件一件来，
     * 每一件的吸纳过程都看得见。</p>
     */
    private final ItemStackHandler items = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            syncToClient();
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }
    };""")

# 5) Transformation 记录：input 说明 + convert 说明
add(BE,
    "     * @param input        台面上要摆的东西（只认物品，件数随意）",
    """     * @param input        台面上要摆的东西（只认物品；第 84 批起台面一次只纳一件，
     *                     旧存档里可能残留旧口径的整堆）""")
add(BE,
    """        /**
         * 把台面上那堆整体换成产物。
         *
         * <p>★ <b>件数照搬</b>——早先这里写死 {@code new ItemStack(Items.CRYING_OBSIDIAN)}，
         * 于是放一组 64 个黑曜石也只拿回 1 个哭泣的黑曜石，其余的凭空没了。</p>
         */""",
    """        /**
         * 把台面上的东西换成产物。
         *
         * <p>★ <b>件数照搬</b>——早先这里写死 {@code new ItemStack(Items.CRYING_OBSIDIAN)}，
         * 于是放一组 64 个黑曜石也只拿回 1 个哭泣的黑曜石，其余的凭空没了。
         * 台面只纳一件之后件数恒为 1；照搬是为了兜住旧存档里可能残留的整堆。</p>
         */""")

# 6) serverTick Javadoc：次序表重写
add(BE,
    """     * <h2>★ 这一趟的次序</h2>
     * <ol>
     *   <li><b>熄坛即散</b>：未成坛不测卦气、不转化，攒下的要素也一并散去（进度抹掉，
     *       免得开坛瞬间「凭空推进」）；</li>
     *   <li><b>对不上卦气就清零</b>：台面没东西、或这东西在当下这片卦气里没有对应转化，
     *       进度归零，<b>也不去抽要素</b>——闲着也是空吸玩家的罐子，不干这事；</li>
 *   <li><b>吸纳</b>：本条转化要要素时，从基座附近 5×5 的「要素源」里抽本地卦气那一支，
 *       直到够这一堆（每周期最多 {@value #DRAW_PER_BATCH} 份，吸取丝线见
 *       {@link XuanshuPedestalEffects}）；</li>
     *   <li><b>推进</b>：要素够 {@code 每件价 × 件数} 才走 3 秒的转化，成时扣要素、换产物。</li>
     * </ol>""",
    """     * <h2>★ 这一趟的次序</h2>
     * <ol>
     *   <li><b>续丝</b>（每 tick，排在最前）：吸取丝线的续期 / 清表——★ 第 84 批
     *       从末尾挪来；原先它排在多个 {@code return} 之后，正在吸要素的那些周期
     *       （缓冲区未满、提前返回）根本走不到，批号推不出去、客户端一条丝线
     *       也画不出来——「像没在吸」的根源就在这；</li>
     *   <li><b>熄坛即散</b>：未成坛不测卦气、不转化，攒下的要素也一并散去（进度抹掉，
     *       免得开坛瞬间「凭空推进」）；</li>
     *   <li><b>对不上卦气就清零</b>：台面没东西、或这东西在当下这片卦气里没有对应转化，
     *       进度归零，<b>也不去抽要素</b>——闲着也是空吸玩家的罐子，不干这事；</li>
     *   <li><b>吸纳</b>：本条转化要要素时，从基座附近 5×5 的「要素源」里抽本地卦气那一支，
     *       直到够这一件（每周期最多 {@value #DRAW_PER_BATCH} 份——慢慢吸，丝线才
     *       看得清，见 {@link XuanshuPedestalEffects}）；</li>
     *   <li><b>推进</b>：要素攒够才走 3 秒的转化，成时扣要素、换产物。</li>
     * </ol>""")

# 7) serverTick 开头：把续期挪到最前
add(BE,
    """    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  XuanshuPedestalBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        if (level.getGameTime() % AUDIT_INTERVAL == 0) {""",
    """    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  XuanshuPedestalBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        // 吸取丝线的续期：★ 第 84 批从函数末尾挪到这里——原来它排在多个 `return`
        // 之后，正在吸要素的那几个周期（缓冲区未满、提前返回）根本走不到，批号
        // 推不出去、客户端一条丝线也画不出来（「看着像没在吸」的根源）。
        // 现在独立于下方的转化流程，每 tick 都续，与坩埚的写法对齐。
        // （停续条件只看 lastDrawTick，别用 trailSources.isEmpty()——那张表只增不减。）
        long now = level.getGameTime();
        long sinceDraw = be.lastDrawTick < 0 ? Long.MAX_VALUE : now - be.lastDrawTick;
        if (sinceDraw > TRAIL_LINGER) {
            if (!be.trailSources.isEmpty()) {
                be.trailSources.clear();
                be.trailColors.clear();
                be.syncToClient();
            }
        } else if (now % TRAIL_INTERVAL == 0) {
            be.bumpTrail();
        }

        if (level.getGameTime() % AUDIT_INTERVAL == 0) {""")

# 8) serverTick 末尾：删掉原来的续期块
add(BE,
    """        be.setChanged();

        // 吸取丝线的续期（与坩埚同一套机制）：只在「不久前真的吸到过要素」时才续。
        // ★ 别用 trailSources.isEmpty() 当条件——那张表只增不减，会让动画永远停不下来。
        long now = level.getGameTime();
        long sinceDraw = be.lastDrawTick < 0 ? Long.MAX_VALUE : now - be.lastDrawTick;
        if (sinceDraw > TRAIL_LINGER) {
            if (!be.trailSources.isEmpty()) {
                be.trailSources.clear();
                be.trailColors.clear();
                be.syncToClient();
            }
        } else if (now % TRAIL_INTERVAL == 0) {
            be.bumpTrail();
        }
    }""",
    """        be.setChanged();
    }""")

# 9) 吸纳段：节奏（单件 2/周期、整堆快车道）+ room 计算去掉旧上限
add(BE,
    """        int cost = transformation.essentiaCost() * stack.getCount();
        if (cost > 0) {
            // 吸纳：抽够这一堆要的份数（每个推进周期限量）。
            // ★ 第 83 批：抽到要素不再冒精魂火絮——吸取的可视交给「源→台座」的丝线
            //   （{@link XuanshuPedestalEffects} 每 tick 生成，与注魔矩阵同款）。
            int room = Math.min(ESSENTIA_CAP - be.bufferedAmount, cost - be.bufferedAmount);
            if (room > 0) {
                int got = be.drawEssentia(want, Math.min(room, DRAW_PER_BATCH));""",
    """        int cost = transformation.essentiaCost() * stack.getCount();
        if (cost > 0) {
            // 吸纳：抽够这一件要的份数（每个推进周期限量）。
            // ★ 第 84 批：槽上限 1 → 台面只有一件，每周期只吸 {@value #DRAW_PER_BATCH}
            //   份，把八份拉成两秒上下的连续丝线（看得见的过程）；旧存档残留的
            //   整堆走快车道，免得吸上两分钟。
            int batch = stack.getCount() > 1 ? DRAW_PER_BATCH_BULK : DRAW_PER_BATCH;
            int room = cost - be.bufferedAmount;
            if (room > 0) {
                int got = be.drawEssentia(want, Math.min(room, batch));""")


# ======================================================================
# 二、ModBlocks.java（XuanshuPedestalBlock）
# ======================================================================

# 1) 类注释：补「一次只纳一件」
add(MB,
    """     * 玄枢台座方块：底座 + 柱 + 顶板三段造型（克隆本体奥术基座元素），
     * 右键放物 / 取物（台面有物时再右键即取回，手中另持一物则以新换旧），
     * 物品由方块实体存储、由渲染器摆到台面上。""",
    """     * 玄枢台座方块：底座 + 柱 + 顶板三段造型（克隆本体奥术基座元素），
     * 右键放物 / 取物（台面有物时再右键即取回，手中另持一物则以新换旧；
     * <b>一次只纳一件</b>——整组放上去也只收一个，余数留在手里），
     * 物品由方块实体存储、由渲染器摆到台面上。""")

# 2) ticker 注释：过期的「附魔粒子环」
add(MB,
    """            if (level.isClientSide) {
                // 客户端 tick 只负责画卦气环（附魔粒子）""",
    """            if (level.isClientSide) {
                // 客户端 tick 只负责「吸取丝线」的续期（见 XuanshuPedestalEffects）""")

# 3) 放置分支注释（空槽）
add(MB,
    """                if (!level.isClientSide) {
                    ItemStack leftover = pedestal.getItems().insertItem(
                            XuanshuPedestalBlockEntity.SLOT, stack.copy(), false);
                    stack.setCount(leftover.getCount());
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            if (!level.isClientSide) {""",
    """                if (!level.isClientSide) {
                    // ★ 槽上限为 1（见 BE 的 items）：即便手里是一整组，也只放上去一件，
                    //   余数原样留手——「一次只纳一件」就落在这里。
                    ItemStack leftover = pedestal.getItems().insertItem(
                            XuanshuPedestalBlockEntity.SLOT, stack.copy(), false);
                    stack.setCount(leftover.getCount());
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            if (!level.isClientSide) {""")

# 4) 换物分支注释
add(MB,
    """                if (!stack.isEmpty()) {
                    ItemStack leftover = pedestal.getItems().insertItem(
                            XuanshuPedestalBlockEntity.SLOT, stack.copy(), false);
                    stack.setCount(leftover.getCount());
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);""",
    """                if (!stack.isEmpty()) {
                    // 以新换旧同样只放上去一件（槽上限 1，余数留手）
                    ItemStack leftover = pedestal.getItems().insertItem(
                            XuanshuPedestalBlockEntity.SLOT, stack.copy(), false);
                    stack.setCount(leftover.getCount());
                }
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);""")


# ======================================================================
# 三、XuanshuPedestalEffects.java — 文档补修正说明
# ======================================================================
add(FX,
    """ * 这里见到批号变了就把倒计时续到 {@value #TRAIL_TICKS}——与玄枢坩埚
 * （{@link XuanshuCrucibleEffects}）是同一套机制：服务端余晖与客户端尾巴合起来，
 * 从「最后一滴要素吸进台座」到「最后一个粒子消散」约一秒。</p>""",
    """ * 这里见到批号变了就把倒计时续到 {@value #TRAIL_TICKS}——与玄枢坩埚
 * （{@link XuanshuCrucibleEffects}）是同一套机制：服务端余晖与客户端尾巴合起来，
 * 从「最后一滴要素吸进台座」到「最后一个粒子消散」约一秒。</p>
 *
 * <p>★ 第 84 批修正：服务端那份续期原先排在 {@code serverTick} 的多个
 * {@code return} 之后——正在吸要素的周期（缓冲区未满、提前返回）推不出批号，
 * 客户端一条丝线也见不到（「像没在吸」的根源）。如今续期挪到最前、每 tick 都跑；
 * 配合每周期只吸 2 份的慢吸，一件卦石的整段吸纳约两秒半，是一条清清楚楚的粒子流。</p>""")


# ======================================================================
# 四、语言文件
# ======================================================================
add(ZH,
    "④ 台面放上石砖，攒够一件 8 份要素，石砖便尽数化为该卦之石。<BR><BR>吸纳中的台座会冒起几点幽蓝细絮；要素不足时它不疾不徐地等着，不推进、也不空耗。",
    "④ 台面§l一次只纳一件§r：放上石砖，攒够 8 份要素，它便化为该卦之石。取走成品，再放下一件。<BR><BR>吸纳时可见一条要素丝线从罐中牵入台座（与注魔祭坛同款）；要素不足时它不疾不徐地等着，不推进、也不空耗。")
add(ZH,
    "③ 坛周围 5×5×3 摆上供着本卦要素的罐子，台座会自行吸纳；",
    "③ 坛周围摆上供着本卦要素的罐子，台座会自行吸纳；")
add(ZH,
    "卦石不再走坩埚：台面摆上石砖，把坛§l立在对应卦象的地气里§r，再让罐子供着本卦要素，台座自会从周围 5×5×3 的罐中吸纳，攒够一件之数，整堆石砖便尽数化为该卦之石。",
    "卦石不再走坩埚：台面放上一块石砖（一次只纳一件），把坛§l立在对应卦象的地气里§r，再让罐子供着本卦要素，台座自会从周围 5×5 内的罐中吸纳，攒够一件之数，那块石砖便化为该卦之石；取走成品，再放下一块。")
add(ZH,
    "头顶浮出卦象单字，四周转起一圈符环。",
    "头顶浮出卦象单字，台口盘起一圈金丝之环。")
add(ZH,
    "成坛的台座还能从要素里取气：以坛为中心、水平两格、上下一格（5×5×3）之内，但凡摆着原质罐、原质管这类要素端点，里面供着的是§l本地卦气那一支要素§r，台座便会缓缓抽取，攒在自己身上。",
    "成坛的台座还能从要素里取气：以坛为中心、水平 5×5、上下两格之内，但凡摆着原质罐、储液池这类要素源，里面供着的是§l本地卦气那一支要素§r，台座便会缓缓抽取，攒在自己身上（与注魔祭坛同一门抽取之法，吸时牵出一条要素丝线）。")
add(ZH,
    "§8▍攒来做什么§r 卦石就靠这个炼：台面摆石砖、立于对应卦象的地气、罐子供着对应要素，攒够一件 8 份，整堆石砖尽数化为该卦之石（详见「卦石」一条）。",
    "§8▍攒来做什么§r 卦石就靠这个炼：台面放一块石砖（一次只纳一件）、立于对应卦象的地气、罐子供着对应要素，攒够一件 8 份，那块石砖便化为该卦之石（详见「卦石」一条）。")

add(EN,
    "4. Put stone bricks on its face; once eight essentia per item are gathered, the whole stack turns to that trigram's stones.<BR><BR>While it draws, the pedestal throws up a few pale blue motes. Short of essentia it simply waits - it neither advances nor wastes.",
    "4. Its face takes §lone item at a time§r: set a stone brick on it; once the eight essentia for that one brick are gathered, it turns to the trigram's stone. Take the finished stone, then set the next.<BR><BR>While it draws, watch the essentia trail run from the jar into the pedestal - the same trail the infusion altar makes. Short of essentia it simply waits - it neither advances nor wastes.")
add(EN,
    "3. Within 5x5x3 of the altar, keep jars stocked with that trigram's essentia - the pedestal draws from them on its own;",
    "3. Around the altar, keep jars stocked with that trigram's essentia - the pedestal draws from them on its own;")
add(EN,
    "The crucible route is gone: lay stone bricks on the pedestal, stand the altar §lon the matching qi§r, and keep jars of that trigram's essentia nearby - the pedestal draws from every jar within 5x5x3, and once enough is gathered for one item, the whole stack turns to that trigram's stones.",
    "The crucible route is gone: set a single stone brick on the pedestal (one at a time), stand the altar §lon the matching qi§r, and keep jars of that trigram's essentia nearby - the pedestal draws from every jar within 5x5, and once the eight essentia for that one brick are gathered, it turns to that trigram's stone. Take it, then set the next.")
add(EN,
    "the characters of that trigram float above its top and a ring of glyphs turns around it.",
    "the characters of that trigram float above its top and a ring of golden thread curls around its rim.")
add(EN,
    "within two blocks horizontally and one vertically of the altar (5x5x3), any essentia endpoint - jars, tubes - holding §lthe essentia of the local qi§r is slowly drained into the pedestal.",
    "within a 5x5 footprint around the altar and two blocks above or below, any essentia source - jars, reservoirs - holding §lthe essentia of the local qi§r is slowly drained into the pedestal (the very method the infusion altar uses; a trail of essentia runs out as it draws).")
add(EN,
    "§8What it is for§r This is how trigram stones are made: stone bricks on its face, the altar standing on the matching qi, jars of the matching essentia nearby; once eight per item are gathered, the whole stack turns to that trigram's stones (see the \\\"Trigram Stones\\\" entry).",
    "§8What it is for§r This is how trigram stones are made: one stone brick on its face (one at a time), the altar standing on the matching qi, jars of the matching essentia nearby; once the eight essentia for that brick are gathered, it turns to that trigram's stone (see the \\\"Trigram Stones\\\" entry).")


def main():
    total = 0
    for path, pairs in REPLACEMENTS.items():
        text = io.open(path, encoding="utf-8").read()
        for old, new in pairs:
            count = text.count(old)
            if count != 1:
                sys.stderr.write("!! %s\n   片段出现 %d 次（应为 1）：%r\n" % (
                    os.path.relpath(path, ROOT), count, old[:90]))
                sys.exit(1)
            text = text.replace(old, new)
            total += 1
        io.open(path, "w", encoding="utf-8", newline="\n").write(text)
        print("updated %-72s (%d 处)" % (os.path.relpath(path, ROOT), len(pairs)))
    print("all %d replacements applied" % total)


if __name__ == "__main__":
    main()
