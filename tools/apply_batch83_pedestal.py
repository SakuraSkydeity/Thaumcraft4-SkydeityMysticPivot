#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""第 83 批：玄枢台座 BE 改造（吸取改注魔矩阵式 + 附魔粒子环→丝带环的配合改动）。

对 XuanshuPedestalBlockEntity.java 做以下替换（每处先断言旧文本存在）：
  1. 类 Javadoc：吸纳要素与卦气呈现的描述更新；
  2. 常量：SOURCE_REFRESH/RANGE_XZ/RANGE_Y → SEARCH_RANGE + TRAIL_*；
  3. 字段：sources/nextSourceScan/ringCooldown → trailNonce/lastDrawTick/trailSources/trailColors；
  4. clientTick：删附魔符文粒子撒点，改为转发 XuanshuPedestalEffects；
  5. serverTick：删精魂火絮粒子（吸取特效改注魔同款丝线），加轨迹维护；
  6. drawEssentia：改注魔矩阵式 EssentiaApi.extract(nearby)；
  7. 删 refreshSourcesIfDue / outputFaceFor；
  8. 加 recordTrail/bumpTrail + 客户端 getter；
  9. NBT 存取轨迹。

用法：python tools/apply_batch83_pedestal.py
"""
import os
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src/main/java/skydeity/mysticpivot/XuanshuPedestalBlockEntity.java")
BACKUP = os.path.join(ROOT, "tools/work/batch83_removed/XuanshuPedestalBlockEntity.java.bak")

REPLACEMENTS = []


def rep(old, new):
    REPLACEMENTS.append((old, new))


# ---------------------------------------------------------------- 1. Javadoc
rep(
    " * <p><b>吸纳要素</b>：成坛之后，台座还会从<b>周围 5×5×3 的要素端点</b>（原质罐、原质管……）\n"
    " * 里抽取<b>本地卦气对应的那一支要素</b>，攒在台座里（{@link #bufferedAmount}）。\n"
    " * 卦石就是这么来的：台面摆石砖、立于对应卦象的地气、罐子供着对应要素，",
    " * <p><b>吸纳要素</b>：成坛之后，台座会像<b>注魔矩阵</b>那样从<b>基座附近 5×5</b>\n"
    " * 的「要素源」（原质罐、储液池、镜像……）里直接抽取<b>本地卦气对应的那一支</b>要素，\n"
    " * 攒在台座里（{@link #bufferedAmount}）——不必先接管子、也不是非端点不可，\n"
    " * 罐子摆在旁边就会自己吸（吸取时放一条「源→台座」的丝线，与注魔矩阵同款）。\n"
    " * 卦石就是这么来的：台面摆石砖、立于对应卦象的地气、罐子供着对应要素，",
)

rep(
    " * <p>卦气呈现：台座上方悬着所在处的卦象单字（{@link XuanshuPedestalRenderer}），\n"
    " * 四隅各有一缕絮带搭上台座顶心，四周浮着一圈斜置椭圆的附魔粒子\n"
    " * （{@link #clientTick}）——立在哪里、凝的哪一卦，一眼可辨。</p>",
    " * <p>卦气呈现：台座上方悬着所在处的卦象单字（{@link XuanshuPedestalRenderer}），\n"
    " * 四隅各有一缕絮带搭上台座顶心，台口外圈盘着一道<b>金色丝带环</b>\n"
    " * （同见渲染器）——立在哪里、凝的哪一卦，一眼可辨。</p>",
)

# ---------------------------------------------------------------- 2. 常量
rep(
    "    /** 要素端点（罐 / 管）的重扫周期（tick）。 */\n"
    "    private static final int SOURCE_REFRESH = 20;\n"
    "\n"
    "    /**\n"
    "     * 抽要素的搜索范围：水平 ±{@value #RANGE_XZ}、竖直 ±{@value #RANGE_Y}（= 5×5×3）。\n"
    "     *\n"
    "     * <p>与玄枢炼金炉「推送要素」同规格——一座坛配一圈罐子，刚好罩住 3×3 坛身外沿。</p>\n"
    "     */\n"
    "    private static final int RANGE_XZ = 2, RANGE_Y = 1;",
    "    /**\n"
    "     * 抽要素的搜索范围：以台座为中心 ±{@value #SEARCH_RANGE}（5×5×5 见方）。\n"
    "     *\n"
    "     * <p>★ 第 83 批改为与<b>注魔矩阵</b>同款——直接向范围内的「要素源」（原质罐、\n"
    "     * 储液池、镜像……）抽取，不再要求先接上管道、也不再是「端点专属」。</p>\n"
    "     */\n"
    "    private static final int SEARCH_RANGE = 2;\n"
    "\n"
    "    /** 吸取丝线的续描周期（tick）：吸着的时候每这么多 tick 把批号 +1 推一次。 */\n"
    "    private static final int TRAIL_INTERVAL = 5;\n"
    "\n"
    "    /** 吸取丝线的余晖（tick）：停吸超过这么久就停续并清表，客户端那条尾巴自然收掉。 */\n"
    "    private static final int TRAIL_LINGER = 10;\n"
    "\n"
    "    /** 吸取丝线最多记几条源。 */\n"
    "    private static final int MAX_TRAILS = 8;",
)

# ---------------------------------------------------------------- 3. 字段
rep(
    "    /**\n"
    "     * 附近的要素端点（罐 / 管）与「从哪一面抽」——每 {@value #SOURCE_REFRESH} tick 重扫一次。\n"
    "     *\n"
    "     * <p>★ <b>面必须缓存</b>：原质罐只认 {@code UP} 一面进出（见 {@link #outputFaceFor}），\n"
    "     * 侧放、底放的罐子若照「朝向我们的那一面」去抽，一份都抽不出来。</p>\n"
    "     */\n"
    "    private final Map<BlockPos, Direction> sources = new HashMap<>();\n"
    "\n"
    "    /** 下一次重扫要素端点的时刻（game tick）。 */\n"
    "    private long nextSourceScan;\n"
    "\n"
    "    /**\n"
    "     * 卦气环现存的那一层还剩几 tick（0 = 该补了）。\n"
    "     *\n"
    "     * <p><b>只存在于客户端、不存档</b>：它管的是「什么时候再撒一层粒子」，与方块数据无关。</p>\n"
    "     */\n"
    "    private int ringCooldown;",
    "    /**\n"
    "     * 吸取丝线的批号：服务端「刚吸到过要素」时每 {@value #TRAIL_INTERVAL} tick +1 并推一次\n"
    "     * 方块数据；客户端（{@link XuanshuPedestalEffects}）见了批号变化就把「源→台座」\n"
    "     * 那条吸取丝线的尾巴续满。\n"
    "     */\n"
    "    private int trailNonce;\n"
    "\n"
    "    /**\n"
    "     * 上次真的吸到要素的时刻（game tick）；没吸过时 -1。\n"
    "     *\n"
    "     * <p>轨迹续期只看它——停吸超过 {@link #TRAIL_LINGER} 就停续并清表。</p>\n"
    "     */\n"
    "    private long lastDrawTick = -1;\n"
    "\n"
    "    /** 最近吸到过要素的源（画吸取丝线用；客户端读，随方块数据下发）。 */\n"
    "    private final List<BlockPos> trailSources = new ArrayList<>(MAX_TRAILS);\n"
    "\n"
    "    /** 与 {@link #trailSources} 配对的要素色。 */\n"
    "    private final List<Integer> trailColors = new ArrayList<>(MAX_TRAILS);",
)

# ---------------------------------------------------------------- 4. clientTick
rep(
    "    /**\n"
    "     * 客户端每 tick：台座处既凝着卦气，便沿一圈<b>斜置 26° 的椭圆</b>摆上附魔符文\n"
    "     * （长轴 0.78 / 短轴 0.58）——看着就是原版附魔台那种符文，只是不下落、不漂移。\n"
    "     *\n"
    "     * <p>★ <b>用的是自建的零重力符文</b>（{@link ModParticles#QI_GLYPH}）而不是\n"
    "     * {@link ParticleTypes#ENCHANT}：原版那枚是 {@code FlyTowardsPositionParticle}，\n"
    "     * 它的 {@code tick()} 里写死了 {@code y = yStart + yd·f − f1⁴ × 1.2} ——\n"
    "     * <b>寿命末尾必沉 1.2 格，与 {@code yd} 传什么无关</b>，做不到用户要的「不下落」。\n"
    "     * 自建这枚除了变老什么都不做，贴图仍是原版那 26 张附魔符文（{@code sga_a … sga_z}）。</p>\n"
    "     *\n"
    "     * <p>★ <b>整环不转、且只维持一层</b>：{@link #RING_DOTS} 个点一次铺满整条椭圆、\n"
    "     * 相位固定，<b>等这一层全部灭掉（{@link ModParticles#RING_LIFETIME} tick）才补下一层</b>。\n"
    "     * 每个位置该显哪一号符文通过粒子参数里的 {@code zd} 传下去，所以同一个点上永远是同一个字。</p>\n"
    "     *\n"
    "     * <p>★★ <b>为什么不能「每几 tick 补一遍」</b>：粒子寿命比补描周期长十几倍时，同一个位置上\n"
    "     * 会叠着十几枚<b>年龄各不相同</b>的符文，而半透明贴图的透明度是相加的 —— 总亮度随各代\n"
    "     * 年龄此起彼伏，看着就是「一直在刷新、一直在闪」。一层就没有这个问题，整圈只随寿命\n"
    "     * 缓缓明灭一次。</p>\n"
    "     *\n"
    "     * <p>不走 {@code Block.animateTick}：那条路是「每 tick 随机抽 1334 个坐标」的稀疏散点，\n"
    "     * 单个方块几十 tick 才被抽中一次，凑不成一整圈。走方块实体的客户端 tick 才是每 tick 必到。</p>\n"
    "     *\n"
    "     * <p>★ <b>未成坛则一点都不起</b>：没开坛的台座（四象未立齐、或还没以法杖点过）\n"
    "     * 不测卦气，所以既没有外圈卦气环、也没有内圈「已启」符环——一眼就能分清\n"
    "     * 「这是一座台座」和「这是一座成了坛的台座」。</p>\n"
    "     */\n"
    "    public static void clientTick(Level level, BlockPos pos, XuanshuPedestalBlockEntity be) {\n"
    "        // ★ 未成坛 = 不测卦气。卦象是「坛成之后才起」的东西：四象没立齐、没以法杖点过，\n"
    "        //   这座台座就是一块石头，头上不凝字、身边也不该浮粒子的环。\n"
    "        if (!be.active) {\n"
    "            be.ringCooldown = 0;\n"
    "            return;\n"
    "        }\n"
    "        boolean hasQi = !Fengshui.qiAll(level, pos).isEmpty();\n"
    "        if (be.ringCooldown > 0) {\n"
    "            // ★ 上一层还在，什么都不做 —— 这就是「只有一层」\n"
    "            be.ringCooldown--;\n"
    "            return;\n"
    "        }\n"
    "        // 补一层：寿命与补描间隔取同一个数，于是上一层刚灭、下一层就到\n"
    "        be.ringCooldown = ModParticles.RING_LIFETIME - 1;\n"
    "        double cx = pos.getX() + 0.5D;\n"
    "        double cy = pos.getY() + RING_Y;\n"
    "        double cz = pos.getZ() + 0.5D;\n"
    "        double sinTilt = Math.sin(RING_TILT);\n"
    "        double cosTilt = Math.cos(RING_TILT);\n"
    "        if (hasQi) {\n"
    "            for (int i = 0; i < RING_DOTS; i++) {\n"
    "                double angle = i * (Math.PI * 2.0D / RING_DOTS);\n"
    "                // 先在水平面上取椭圆，再整体绕 X 轴倾 26°：y 由短轴分量给出高度、z 被压短\n"
    "                double ex = Math.cos(angle) * RING_A;\n"
    "                double ez = Math.sin(angle) * RING_B;\n"
    "                // 三个速度全传 0（这枚粒子本来也不动）；zd 借来当「第几号符文」\n"
    "                level.addParticle(ModParticles.QI_GLYPH.get(),\n"
    "                        cx + ex, cy + ez * sinTilt, cz + ez * cosTilt,\n"
    "                        0.0D, 0.0D, i);\n"
    "            }\n"
    "        }\n"
    "        if (be.active) {\n"
    "            // 开坛之后多贴一圈小符环（平铺、不倾斜），压在柱头那一圈上——\n"
    "            // 与卦气环同生共灭，于是「这座台座是不是启过的」不必凑近看四砖也能认出来。\n"
    "            for (int i = 0; i < INNER_DOTS; i++) {\n"
    "                double angle = i * (Math.PI * 2.0D / INNER_DOTS);\n"
    "                level.addParticle(ModParticles.QI_GLYPH.get(),\n"
    "                        cx + Math.cos(angle) * INNER_R,\n"
    "                        pos.getY() + INNER_Y,\n"
    "                        cz + Math.sin(angle) * INNER_R,\n"
    "                        0.0D, 0.0D, (i + RING_DOTS) % 26);\n"
    "            }\n"
    "        }\n"
    "    }",
    "    /**\n"
    "     * 客户端每 tick：把「吸取丝线」的尾巴续上（{@link XuanshuPedestalEffects}）。\n"
    "     *\n"
    "     * <p>★ 第 83 批：原「附魔符文粒子环」（沿斜置椭圆撒的一圈零重力符文）已删，\n"
    "     * 基座周围的环改为渲染器画的<b>金色丝带环</b>（{@link XuanshuPedestalRenderer}），\n"
    "     * 与四隅絮带同一套画法。这里只剩吸取丝线的续期（服务端每 {@value #TRAIL_INTERVAL} tick\n"
    "     * 把 {@link #trailNonce} +1 推下来，见了就把尾巴续满）。</p>\n"
    "     */\n"
    "    public static void clientTick(Level level, BlockPos pos, XuanshuPedestalBlockEntity be) {\n"
    "        XuanshuPedestalEffects.clientTick(level, pos, be);\n"
    "    }",
)

# ---------------------------------------------------------------- 5. serverTick 吸取段
rep(
    "        if (cost > 0) {\n"
    "            // 吸纳：抽够这一堆要的份数（每个推进周期限量，抽到了就冒两粒仙尘）\n"
    "            int room = Math.min(ESSENTIA_CAP - be.bufferedAmount, cost - be.bufferedAmount);\n"
    "            if (room > 0) {\n"
    "                int got = be.drawEssentia(want, Math.min(room, DRAW_PER_BATCH));\n"
    "                if (got > 0) {\n"
    "                    be.bufferedAspect = want;\n"
    "                    be.bufferedAmount += got;\n"
    "                    // 抽到要素就冒两粒精魂火絮：玩家据此知道「坛正在纳气」，\n"
    "                    // 与推进转化的紫色门扉粒子分成两色，不混为一谈。\n"
    "                    ((ServerLevel) level).sendParticles(ParticleTypes.SOUL,\n"
    "                            pos.getX() + 0.5D, pos.getY() + 1.1D, pos.getZ() + 0.5D,\n"
    "                            2, 0.25D, 0.1D, 0.25D, 0.02D);\n"
    "                    be.setChanged();\n"
    "                }\n"
    "            }",
    "        if (cost > 0) {\n"
    "            // 吸纳：抽够这一堆要的份数（每个推进周期限量）。\n"
    "            // ★ 第 83 批：抽到要素不再冒精魂火絮——吸取的可视交给「源→台座」的丝线\n"
    "            //   （{@link XuanshuPedestalEffects} 每 tick 生成，与注魔矩阵同款）。\n"
    "            int room = Math.min(ESSENTIA_CAP - be.bufferedAmount, cost - be.bufferedAmount);\n"
    "            if (room > 0) {\n"
    "                int got = be.drawEssentia(want, Math.min(room, DRAW_PER_BATCH));\n"
    "                if (got > 0) {\n"
    "                    be.bufferedAspect = want;\n"
    "                    be.bufferedAmount += got;\n"
    "                    be.setChanged();\n"
    "                }\n"
    "            }",
)

# ---------------------------------------------------------------- 5b. serverTick 尾部轨迹维护
rep(
    "            be.items.setStackInSlot(SLOT, transformation.convert(stack));\n"
    "            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME,\n"
    "                    SoundSource.BLOCKS, 0.8F, 0.7F);\n"
    "        }\n"
    "        be.setChanged();\n"
    "    }",
    "            be.items.setStackInSlot(SLOT, transformation.convert(stack));\n"
    "            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME,\n"
    "                    SoundSource.BLOCKS, 0.8F, 0.7F);\n"
    "        }\n"
    "        be.setChanged();\n"
    "\n"
    "        // 吸取丝线的续期（与坩埚同一套机制）：只在「不久前真的吸到过要素」时才续。\n"
    "        // ★ 别用 trailSources.isEmpty() 当条件——那张表只增不减，会让动画永远停不下来。\n"
    "        long now = level.getGameTime();\n"
    "        long sinceDraw = be.lastDrawTick < 0 ? Long.MAX_VALUE : now - be.lastDrawTick;\n"
    "        if (sinceDraw > TRAIL_LINGER) {\n"
    "            if (!be.trailSources.isEmpty()) {\n"
    "                be.trailSources.clear();\n"
    "                be.trailColors.clear();\n"
    "                be.syncToClient();\n"
    "            }\n"
    "        } else if (now % TRAIL_INTERVAL == 0) {\n"
    "            be.bumpTrail();\n"
    "        }\n"
    "    }",
)

# ---------------------------------------------------------------- 6. drawEssentia 重写
rep(
    "    /**\n"
    "     * 从周围 5×5×3 的要素端点抽 {@code aspect} 最多 {@code want} 份，返回实际抽到的份数。\n"
    "     *\n"
    "     * <p>端点表每 {@value #SOURCE_REFRESH} tick 重扫一次（表里存的「面」由\n"
    "     * {@link #outputFaceFor} 问端点自己）；逐个端点试，抽到多少算多少。</p>\n"
    "     *\n"
    "     * <p>★ <b>与炼金炉的 {@code distribute} 是镜像</b>：那边往端点里<b>推</b>\n"
    "     * （{@link EssentiaApi#add}），这边从端点里<b>抽</b>（{@link EssentiaApi#take}），\n"
    "     * 面试探的坑一模一样——见 {@link #outputFaceFor}。</p>\n"
    "     */\n"
    "    private int drawEssentia(AspectId aspect, int want) {\n"
    "        refreshSourcesIfDue();\n"
    "        int drawn = 0;\n"
    "        for (Map.Entry<BlockPos, Direction> entry : this.sources.entrySet()) {\n"
    "            if (drawn >= want) {\n"
    "                break;\n"
    "            }\n"
    "            BlockPos target = entry.getKey();\n"
    "            if (!this.level.isLoaded(target)) {\n"
    "                continue;\n"
    "            }\n"
    "            if (!(this.level.getBlockEntity(target) instanceof EssentiaTransport transport)) {\n"
    "                continue;\n"
    "            }\n"
    "            int got = EssentiaApi.take(this.level, transport, aspect, want - drawn,\n"
    "                    entry.getValue(), EssentiaTransferMode.EXECUTE);\n"
    "            if (got <= 0) {\n"
    "                continue;\n"
    "            }\n"
    "            drawn += got;\n"
    "        }\n"
    "        return drawn;\n"
    "    }",
    "    /**\n"
    "     * 从基座附近 5×5 内抽 {@code aspect} 最多 {@code want} 份，返回实际抽到的份数。\n"
    "     *\n"
    "     * <p>★ <b>与注魔矩阵同款</b>（{@code InfusionMatrixBlockEntity#drainOneEssentia}）：\n"
    "     * 不接管道、不认端点，直接 {@link EssentiaApi#extract} 向范围内的「要素源」\n"
    "     * （原质罐、储液池、镜像……）抽取——罐子摆在旁边就会自己吸。\n"
    "     * 先按「一次付清」问一遍（省事），付不满再逐份抽（罐里剩一两份也能吸干净）。\n"
    "     * 每抽到一次就 {@link #recordTrail 记一条源}，客户端据此画「源→台座」的丝线。</p>\n"
    "     */\n"
    "    private int drawEssentia(AspectId aspect, int want) {\n"
    "        Optional<EssentiaSourceRef> bulk = EssentiaApi.extract(this.level, this.worldPosition,\n"
    "                aspect, want, EssentiaSearch.nearby(SEARCH_RANGE), EssentiaTransferMode.EXECUTE);\n"
    "        if (bulk.isPresent()) {\n"
    "            this.recordTrail(bulk.orElseThrow().position(), aspect);\n"
    "            return want;\n"
    "        }\n"
    "        int drawn = 0;\n"
    "        while (drawn < want) {\n"
    "            Optional<EssentiaSourceRef> one = EssentiaApi.extract(this.level, this.worldPosition,\n"
    "                    aspect, 1, EssentiaSearch.nearby(SEARCH_RANGE), EssentiaTransferMode.EXECUTE);\n"
    "            if (one.isEmpty()) {\n"
    "                break;\n"
    "            }\n"
    "            this.recordTrail(one.orElseThrow().position(), aspect);\n"
    "            drawn++;\n"
    "        }\n"
    "        return drawn;\n"
    "    }",
)

# ---------------------------------------------------------------- 7. 删旧扫描逻辑
rep(
    "    /** 重扫 5×5×3 里的要素端点（罐 / 管 / 储液池……）。 */\n"
    "    private void refreshSourcesIfDue() {\n"
    "        long time = this.level.getGameTime();\n"
    "        if (time < this.nextSourceScan) {\n"
    "            return;\n"
    "        }\n"
    "        this.nextSourceScan = time + SOURCE_REFRESH;\n"
    "        this.sources.clear();\n"
    "        for (int dy = -RANGE_Y; dy <= RANGE_Y; dy++) {\n"
    "            for (int dx = -RANGE_XZ; dx <= RANGE_XZ; dx++) {\n"
    "                for (int dz = -RANGE_XZ; dz <= RANGE_XZ; dz++) {\n"
    "                    if (dx == 0 && dy == 0 && dz == 0) {\n"
    "                        continue;\n"
    "                    }\n"
    "                    BlockPos target = this.worldPosition.offset(dx, dy, dz);\n"
    "                    if (!this.level.isLoaded(target)) {\n"
    "                        continue;\n"
    "                    }\n"
    "                    if (!(this.level.getBlockEntity(target) instanceof EssentiaTransport transport)) {\n"
    "                        continue;\n"
    "                    }\n"
    "                    Direction face = outputFaceFor(transport, dx, dy, dz);\n"
    "                    if (face == null) {\n"
    "                        continue;\n"
    "                    }\n"
    "                    this.sources.put(target.immutable(), face);\n"
    "                }\n"
    "            }\n"
    "        }\n"
    "    }\n"
    "\n"
    "    /**\n"
    "     * 从端点的<b>哪一面</b>把要素抽出来——这一面同样由端点自己说了算。\n"
    "     *\n"
    "     * <p>★ <b>与炼金炉 {@code inputFaceFor} 是同一个坑的另一半</b>：原质罐的\n"
    "     * {@code canOutputTo} 与 {@code canInputFrom} 一样<b>只认 {@code UP}</b>\n"
    "     * （要素从罐顶进出，魔像喂罐、抽罐都固定走罐顶那一面）。侧放、底放的罐子若照\n"
    "     * 「朝向我们的那一面」去抽，一份都出不来。</p>\n"
    "     *\n"
    "     * <p>正确的顺序：先试「端点朝向我们的那一面」（正经连着的那一侧，管最喜欢它），\n"
    "     * 不通再把六个面逐个试一遍，取第一个 {@code canOutputTo} 为真的。\n"
    "     * 都不给就返回 {@code null}（这一格不是可抽的源头）。</p>\n"
    "     */\n"
    "    @Nullable\n"
    "    private static Direction outputFaceFor(EssentiaTransport transport, int dx, int dy, int dz) {\n"
    "        Direction towardPedestal = Direction.getNearest(-dx, -dy, -dz);\n"
    "        if (transport.canOutputTo(towardPedestal)) {\n"
    "            return towardPedestal;\n"
    "        }\n"
    "        for (Direction direction : Direction.values()) {\n"
    "            if (direction != towardPedestal && transport.canOutputTo(direction)) {\n"
    "                return direction;\n"
    "            }\n"
    "        }\n"
    "        return null;\n"
    "    }",
    "    /**\n"
    "     * 记一条吸取丝线的源（同一格只留一条，色号取该要素的色），并记下「刚吸到东西」的时刻。\n"
    "     *\n"
    "     * <p>与坩埚的 {@code recordTrail} 同一套：源表只增不减，何时停看 {@link #lastDrawTick}；\n"
    "     * 换要素色不影响位置，只把那一格的色号改掉。</p>\n"
    "     */\n"
    "    private void recordTrail(BlockPos source, AspectId aspect) {\n"
    "        if (this.level != null) {\n"
    "            this.lastDrawTick = this.level.getGameTime();\n"
    "        }\n"
    "        BlockPos key = source.immutable();\n"
    "        AspectDefinition definition = AspectApi.get(aspect);\n"
    "        int color = definition == null ? 0xFFFFFF : definition.color();\n"
    "        for (int i = 0; i < this.trailSources.size(); i++) {\n"
    "            if (this.trailSources.get(i).equals(key)) {\n"
    "                this.trailColors.set(i, color);\n"
    "                return;\n"
    "            }\n"
    "        }\n"
    "        while (this.trailSources.size() >= MAX_TRAILS) {\n"
    "            this.trailSources.remove(0);\n"
    "            this.trailColors.remove(0);\n"
    "        }\n"
    "        this.trailSources.add(key);\n"
    "        this.trailColors.add(color);\n"
    "    }\n"
    "\n"
    "    /** 吸取丝线批号 +1（连同源表一起推给客户端）。 */\n"
    "    private void bumpTrail() {\n"
    "        this.trailNonce++;\n"
    "        this.setChanged();\n"
    "        this.syncToClient();\n"
    "    }\n"
    "\n"
    "    /** 吸取丝线批号（客户端 tick 见面即续期）。 */\n"
    "    public int trailNonce() {\n"
    "        return this.trailNonce;\n"
    "    }\n"
    "\n"
    "    public int trailCount() {\n"
    "        return this.trailSources.size();\n"
    "    }\n"
    "\n"
    "    public BlockPos trailSource(int index) {\n"
    "        return this.trailSources.get(index);\n"
    "    }\n"
    "\n"
    "    public int trailColor(int index) {\n"
    "        return this.trailColors.get(index);\n"
    "    }",
)

# ---------------------------------------------------------------- 8. NBT 存取
rep(
    "        if (this.bufferedAspect != null) {\n"
    "            tag.putString(\"essentia_aspect\", this.bufferedAspect.serialized());\n"
    "            tag.putInt(\"essentia_amount\", this.bufferedAmount);\n"
    "        }\n"
    "    }",
    "        if (this.bufferedAspect != null) {\n"
    "            tag.putString(\"essentia_aspect\", this.bufferedAspect.serialized());\n"
    "            tag.putInt(\"essentia_amount\", this.bufferedAmount);\n"
    "        }\n"
    "        // 吸取丝线：批号 + 源表 + 色表（与坩埚同款——顺带存档，重载后动画不突然断）\n"
    "        tag.putInt(\"TrailNonce\", this.trailNonce);\n"
    "        long[] packed = new long[this.trailSources.size()];\n"
    "        for (int i = 0; i < packed.length; i++) {\n"
    "            packed[i] = this.trailSources.get(i).asLong();\n"
    "        }\n"
    "        tag.putLongArray(\"TrailSources\", packed);\n"
    "        int[] colors = new int[this.trailColors.size()];\n"
    "        for (int i = 0; i < colors.length; i++) {\n"
    "            colors[i] = this.trailColors.get(i);\n"
    "        }\n"
    "        tag.putIntArray(\"TrailColors\", colors);\n"
    "    }",
)

rep(
    "        if (tag.contains(\"essentia_aspect\")) {\n"
    "            this.bufferedAspect = parseAspect(tag.getString(\"essentia_aspect\"));\n"
    "            this.bufferedAmount = this.bufferedAspect == null ? 0 : tag.getInt(\"essentia_amount\");\n"
    "        }",
    "        if (tag.contains(\"essentia_aspect\")) {\n"
    "            this.bufferedAspect = parseAspect(tag.getString(\"essentia_aspect\"));\n"
    "            this.bufferedAmount = this.bufferedAspect == null ? 0 : tag.getInt(\"essentia_amount\");\n"
    "        }\n"
    "        this.trailNonce = tag.getInt(\"TrailNonce\");\n"
    "        long[] packed = tag.getLongArray(\"TrailSources\");\n"
    "        int[] colors = tag.getIntArray(\"TrailColors\");\n"
    "        for (int i = 0; i < packed.length && i < colors.length; i++) {\n"
    "            this.trailSources.add(BlockPos.of(packed[i]));\n"
    "            this.trailColors.add(colors[i]);\n"
    "        }",
)

# ---------------------------------------------------------------- imports
rep(
    "import javax.annotation.Nullable;\n"
    "import java.util.HashMap;\n"
    "import java.util.List;\n"
    "import java.util.Map;",
    "import javax.annotation.Nullable;\n"
    "import java.util.ArrayList;\n"
    "import java.util.List;\n"
    "import java.util.Optional;",
)


def main():
    with open(SRC, encoding="utf-8") as f:
        text = f.read()

    failures = []
    for index, (old, new) in enumerate(REPLACEMENTS):
        if old not in text:
            failures.append(index)
            continue
        text = text.replace(old, new, 1)

    if failures:
        print("FAILED replacements:", failures)
        for index in failures:
            print("---- 未命中片段 %d ----" % index)
            print(REPLACEMENTS[index][0][:400])
        raise SystemExit(1)

    os.makedirs(os.path.dirname(BACKUP), exist_ok=True)
    shutil.copy2(SRC, BACKUP)
    with open(SRC, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)
    print("patched %d replacements, backup at %s"
          % (len(REPLACEMENTS), os.path.relpath(BACKUP, ROOT)))


if __name__ == "__main__":
    main()
