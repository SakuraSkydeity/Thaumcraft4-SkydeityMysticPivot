package skydeity.mysticpivot;

import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.aspect.VisPaymentResult;
import dev.tc4port.thaumcraft.api.client.ThaumcraftParticle;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionContext;
import dev.tc4port.thaumcraft.api.focus.action.FocusActionResult;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 「法杖核心:烛龙衔时」的行为（{@code focus_chrono}，装到任意杖柄上使用）。
 *
 * <ul>
 *   <li><b>Shift + 右键指一台机器</b>（第二百二十三批）：消耗六种原始要素各 <b>25</b> 点，
 *       让它以 <b>16 倍速</b>运转 <b>32 秒</b>（640 tick）。
 *       不按 Shift 时本类直接 PASS，那一趟留给方块自己（开界面）。</li>
 *   <li><b>连续催动可以加码</b>：对同一台机器再按一次，倍率翻倍
 *       （16 → 32 → 64 → 128 → 256 → 512 → <b>1024</b> 封顶），时长续满 32 秒。</li>
 *   <li>当前倍率以<b>悬浮字</b>显示在机器正上方（「16x」……「1024x」）。</li>
 *   <li>熔炉、炼金炉、注魔台、通用机械（Mekanism）等一切<b>自己会走</b>的
 *       方块实体都吃这一口；没有方块实体但按随机节律走的方块（作物等）也吃
 *       （催的是它的随机 tick）。</li>
 *   <li>区块卸载时<b>暂停</b>（不催也不计时），机器被拆掉则和平分手。</li>
 * </ul>
 *
 * <h2>一、加速是怎么做出来的（借鉴 TIAB，但不照抄）</h2>
 * <p>参考了 mods 目录里 {@code tiab-neoforge-6.5.4}（Time in a Bottle）的做法：
 * 它放一个{@code TimeAcceleratorEntity}挂在目标方块上，每个 tick 直接拿
 * {@code state.getTicker(level, be.getType())} 的 ticker 多调几次。
 * <b>这条路本身是对的</b>——{@code BlockStateBase#getTicker} 是 1.21.1 里
 * 唯一通用的「这台机器怎么走」入口：本体的炉子、Mekanism 的机器
 * （{@code IHasTileEntity} 的默认 {@code getTicker} 返回服务端 ticker）、
 * 乃至任何走 {@code EntityBlock} 的模组机器都从这儿出，所以「所有机器都能加速」
 * 不需要给任何模组写适配。</p>
 *
 * <p>但 TIAB 那版有几处不完善，本类的实现逐条补上：</p>
 * <ol>
 *   <li><b>没有任何异常防御</b>：ticker.tick 抛一次，整个服务端 tick 就炸
 *       （异常会一路冒到 {@code MinecraftServer}，直接崩服）。本类每一次
 *       补跑都单独 try-catch，某一台机器扛不住时序就只停它一台，
 *       并给施法者一句提示——绝不连坐别的机器。</li>
 *   <li><b>不校验机器还在不在</b>：只比对了方块状态的黑名单标签，
 *       方块实体被换掉、被移除它不知道。本类每次催动前都重新取
 *       方块实体并做<b>同一性校验</b>（{@code getBlockEntity(pos) != entry.be}
 *       或 {@code isRemoved()} 即撤），机器没了绝不对着空气催。</li>
 *   <li><b>无总量上限、无区块卸载处理</b>：加速器可以无限放，
 *       区块卸载后还在对已卸载的世界催。本类封顶 {@value #MAX_ENTRIES} 台，
 *       区块没加载就整条暂停（不催、不计时，等玩家回来接着跑）。</li>
 *   <li><b>到期即消失、无反馈</b>：倍率悬浮字就是反馈——字在，催动就在；
 *       字没了，就是松口了。失败时另给一句提示。</li>
 * </ol>
 *
 * <h2>二、注册表与生命周期</h2>
 * <p>催动中的机器登记在一张静态表里：{@code 维度 → 坐标 → 条目}。
 * 只在服务端 tick（{@link ServerTickEvent.Post}）里读写，全部用并发容器，
 * 单线程语义也足够；服务端停机（{@link ServerStoppingEvent}，
 * 该事件在 {@code stopServer()} 存档<b>之前</b>触发，已反编译确认）
 * 把倍率悬浮字全部撤掉再清表——不清的话那些字会随区块存档落盘，
 * 下次进游戏还浮在机器上（催动却不认了）。登记表本身<b>不写存档</b>：
 * 服务器重启后正在催的机器回到原速（短时行为，不值得为它扩一份世界数据）。</p>
 *
 * <h2>三、倍率悬浮字（TextDisplay）</h2>
 * <p>本体的 {@code Display.TextDisplay} 的 setter 全是 private，但
 * {@code Entity.load(CompoundTag)} 是公开的——于是按 NBT 路线摆字：
 * {@code EntityType.TEXT_DISPLAY.create(level)} 造一个，再用带
 * {@code text}（组件 JSON 串）/ {@code billboard="center"} / {@code Pos}
 * 的 NBT {@code load} 进去，最后 {@code addTag} 打上自家标记
 * {@value #LABEL_TAG}。 TextDisplay 有个坑：<b>区块卸载时它会被存进存档、
 * 重载后是一个新对象</b>，条目里攥着的旧引用就失效了。所以不存引用，
 * 每次改字都在机器正上方一个 1×1×1 的小盒子里按标记<b>现找</b>：
 * 找得到就改它的字，找不到就补一个新的——重载回来的那个会被「收养」，
 * 不会出现两个字叠在一起。撤字同理（小盒子内按标记全撤）。</p>
 *
 * <h2>四、特效（本体 TC4 粒子）</h2>
 * <p>选用本体 {@code destination_wisp_blue}（「飞向终点」的蓝精灵，
 * 与要素罐、源质管里流动的那些是同一个），<b>只保留「飞过去」这一段</b>——
 * 第一百六十六批用户要求：「核心动画只显示飞过去那一段，在方块围绕那一块不需要」。
 * 于是到位后的爆发圈、催动期间的环境微光全部撤掉（连在途特效队列一起删了，
 * 现在发射是一次性调用，不需要每 tick 推进）。</p>
 *
 * <p>发射：从玩家眼睛打出一颗，终点增量直接设为「目标方块中心 − 眼睛」，
 * 粒子沿直线飘向机器（{@code sendParticles(..., count=0, dx, dy, dz, 1.0)}
 * 的 count=0 分支恰好是「精确位置 + 速度=增量×speed」，而蓝精灵的 provider
 * 把速度三元组当作全程位移用）。</p>
 *
 * <p>对比度：蓝精灵自带 {@code alpha = 0.5}，单颗看着发虚（用户：「现在透明多一点」）。
 * 同一点<b>叠三颗</b>（各自带一点微小抖动）——三张半透明面相叠的有效不透明度约
 * {@code 1-0.5³ ≈ 0.88}，淡蓝被压成实色，还顺带多了一点「星屑」的厚度。
 * 本体的粒子接口不暴露 alpha，这是不动 base mod 又能加深观感的唯一办法。</p>
 *
 * <p>特效整段包在 try-catch 里：粒子路径出任何问题都只log不炸，
 * 更不会让已经扣了魔的施法失败。</p>
 */
public final class TemporalCore {

    /** 催动倍率起点（第一次右键 = 16 倍速）。 */
    public static final int RATE_BASE = 16;

    /**
     * 催动倍率上限（连续催动每次翻倍，到此封顶）。
     *
     * <p>★ 第二百二十二批：用户「chrono 加速最高为 1024 倍率」——上限由 256 抬到 1024，
     * 也就是连按 6 次到顶（16 → 32 → 64 → 128 → 256 → 512 → 1024）。</p>
     */
    public static final int RATE_MAX = 1024;

    /** 单次催动时长（tick）：640 = 32 秒。 */
    public static final int DURATION = 640;

    /** 全服同时在催的方块上限；再多了烛龙衔不住（防玩家把全图机器一起挂）。 */
    public static final int MAX_ENTRIES = 32;

    /** 倍率悬浮字的记名标记（scoreboard tag，随实体存档，重载后仍认得出）。 */
    private static final String LABEL_TAG = "skydeity_chrono";

    /** 悬浮字悬在机器中心上方多高（格）。 */
    private static final double LABEL_DY = 0.7D;

    /** 虚空采矿机（第一百八十七批）：机器背后立着星枢光坛（现高 7 格），
     *  字要抬到<b>整座坛之上</b>才露得出来 —— 运行时按结构高度算，改形制不用再改这里。
     *  （不在类初始化时算：{@code VoidMiningStructure} 的静态表要碰 {@code Blocks}，
     *  早于注册表就加载会踩不稳。） */
    private static final double LABEL_CANOPY_EXTRA = 0.6D;

    /*
     * 价码（六元各 25 整 Vis）只写在 data/skydeitymysticpivot/thaumcraft/
     * focus_catalog/chrono.json 的 base_cost 里（1000 厘 = 10 整 Vis）。
     * 改价只改 json——与另外三个核心同一套规矩。
     */

    /** 催动登记表：维度 → 坐标 → 条目。 */
    private static final Map<ResourceKey<Level>, Map<BlockPos, Entry>> REGISTRY = new ConcurrentHashMap<>();

    private TemporalCore() {
    }

    /** 一条催动：催哪台机器、谁催的、还剩多久、当前倍率。 */
    private static final class Entry {
        private final ServerLevel level;
        private final BlockPos pos;
        private final BlockEntity be;
        private final UUID caster;
        private final int rate;
        /** true = 这一格没有 ticker，倍率由机器自己读（虚空采矿机的沟灵核心）。 */
        private final boolean polled;
        private int remaining;

        private Entry(ServerLevel level, BlockPos pos, BlockEntity be, UUID caster,
                      int rate, int remaining, boolean polled) {
            this.level = level;
            this.pos = pos;
            this.be = be;
            this.caster = caster;
            this.rate = rate;
            this.remaining = remaining;
            this.polled = polled;
        }
    }

    // ------------------------------------------------------------------ 右键动作

    /**
     * 核心的右键动作（{@link ModFocuses#register()} 注册进本体）。
     *
     * <p>{@code action} 在本体的两趟里分别传 {@code SIMULATE} 与 {@code EXECUTE}；
     * 「目标合不合格、总量到没到顶」都是纯读，两趟结果必然一致；
     * 只有 {@code EXECUTE} 那趟才写登记表、发特效、摆悬浮字。</p>
     *
     * <p>倍率递推：新条目 {@value #RATE_BASE}×；已在催的再按一次就
     * {@code min(原倍率 × 2, }{@value #RATE_MAX}{@code )}，时长一律续满。</p>
     */
    public static FocusActionResult cast(FocusActionContext context, VisAction action) {
        ServerPlayer player = context.player();

        // ★ 第二百二十三批：用户「Shift + 右键加速机器」——只有潜行那一下才催。
        //   为什么这一句就够：原版 ServerPlayerGameMode#useItemOn（NeoForge 21.1.248 补丁版）
        //   里，潜行只拦住**方块自己**的 useItemOn / useWithoutItem（`flag1` 那个分支），
        //   ItemStack#useOn 照旧在函数末尾调用 —— 而法杖的方块施法入口正是 useOn
        //   （WandItem#useOn → WandUsePipeline.block → 本 mod 的 use/before_formation 绑定）。
        //   于是「潜行 = 不开界面、只施法」是天生的语义，本类只需要把「没潜行」这一趟让开。
        //   返回 PASS 而**不是 FAILED**：FAILED 会把这趟交互判成失败、顺带响一声失败音，
        //   而这里只是「不管这一趟」，方块该开界面照开。
        if (!player.isShiftKeyDown()) {
            return FocusActionResult.PASS;
        }

        if (!(context.authoritativeHit() instanceof BlockHitResult hit)) {
            return FocusActionResult.FAILED;
        }
        ServerLevel level = player.serverLevel();
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return FocusActionResult.FAILED;
        }

        // ★ 第一百八十七批：虚空采矿机只剩一格（背后那座光坛是玩家搭的原版石英），
        //   点机器就是点它自己。它是「自己读倍率」那一类——产出循环在
        //   XuanshuVoidMiningBlockEntity#serverTick 里读 rateAt，不能在这里被强制补跑，
        //   否则倍率会平方（外部补 rate 次 × 自己再乘 rate）。
        boolean polled = state.getBlock() instanceof XuanshuVoidMiningBlock;

        // 目标必须「自己会走」：有方块实体且拿得到服务端 ticker，
        // 或者没有实体但按随机节律走（作物、草莽这类）。否则不耗魔、直接劝退。
        // polled 的那一类例外：倍率由机器自己的 tick 读（见上面的说明）。
        BlockEntity be = polled ? null : level.getBlockEntity(pos);
        BlockEntityTicker<?> ticker = be == null ? null : state.getTicker(level, be.getType());
        if (!polled && (be == null || ticker == null) && !state.isRandomlyTicking()) {
            if (action == VisAction.SIMULATE) {
                message(player, "message.skydeitymysticpivot.chrono.not_machine", ChatFormatting.RED);
                failSound(player);
            }
            return FocusActionResult.FAILED;
        }

        // 总量上限（已在催的那台再右键是加码，不占新名额；到顶也只静默拒绝，不耗魔）
        if (!isTracked(level, pos) && totalEntries() >= MAX_ENTRIES) {
            return FocusActionResult.FAILED;
        }

        VisPaymentResult paid = context.pay(context.configuredCost(), action);
        if (action == VisAction.SIMULATE) {
            if (paid != VisPaymentResult.AVAILABLE) {
                ModFocuses.notifyNoVis(player);
                return FocusActionResult.FAILED;
            }
            return FocusActionResult.SUCCESS;
        }
        if (paid != VisPaymentResult.CONSUMED) {
            return FocusActionResult.FAILED;
        }

        Entry existing = entryAt(level, pos);
        int rate = existing == null ? RATE_BASE : Math.min(existing.rate * 2, RATE_MAX);
        track(level, pos, be, player.getUUID(), rate, polled);
        updateLabel(level, pos, rate, polled);
        // 装置类目标把蓝精灵引到球顶的倍率字上 —— 打进球里就看不见了
        launchFx(player, polled ? labelPos(pos, true) : Vec3.atCenterOf(pos));
        level.playSound(null, player.blockPosition(), SoundEvents.ILLUSIONER_CAST_SPELL,
                SoundSource.PLAYERS, 0.8F, 0.9F);
        return FocusActionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ 催动主循环

    /**
     * 每个服务端 tick 推进所有催动（挂在 {@code ServerTickEvent.Post}，
     * 即本体自己的方块实体都走完之后再补跑，顺序上不会和本体那一步打架）。
     */
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            Map<BlockPos, Entry> map = REGISTRY.get(level.dimension());
            if (map == null || map.isEmpty()) {
                continue;
            }
            // ConcurrentHashMap 的 forEach 是弱一致的：循环里 remove 也安全
            map.forEach((pos, entry) -> {
                try {
                    tickEntry(level, pos, entry, map);
                } catch (Throwable t) {
                    // 最后一层防御：连 tickEntry 自己都出了意外，也只干掉这一条
                    MysticPivot.LOGGER.warn("[烛龙衔时] 催动 {} 时发生未预期错误，已停止该条：{}",
                            pos, t.toString());
                    dropEntry(map, pos, entry, false);
                }
            });
        }
    }

    /** 单条催动走一个 tick：扣时限、校验机器、补跑、冒微光。 */
    private static void tickEntry(ServerLevel level, BlockPos pos, Entry entry, Map<BlockPos, Entry> map) {
        if (!level.isLoaded(pos)) {
            return;                                     // 区块没加载：整条暂停（不催、不计时）
        }
        int left = entry.remaining - 1;
        if (left <= 0) {
            dropEntry(map, pos, entry, false);          // 到期：撤字、静默松口
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (entry.polled) {
            // 自己读倍率的那类目标：只要那一格还是虚空采矿机，就只走时限
            // （光坛被拆只是让它停转，机器还在，催动照旧记着，重新搭起来就接着走）
            if (!(state.getBlock() instanceof XuanshuVoidMiningBlock)) {
                dropEntry(map, pos, entry, false);      // 机器没了：和平分手
                return;
            }
            entry.remaining = left;
            return;
        }
        BlockEntity be = level.getBlockEntity(pos);
        // 机器同一性校验只对「有方块实体」的条目做：催作物这类随机节律方块时
        // getBlockEntity 本来就是 null，不能拿 null 当「机器没了」判。
        // 随机节律那条路靠下面 isRandomlyTicking 的重新判定兜底——变成石头自然就和平分手。
        if (entry.be != null && (be == null || be != entry.be || be.isRemoved())) {
            dropEntry(map, pos, entry, false);          // 机器被拆/被换：和平分手
            return;
        }

        // ★ be 可能为 null（催作物这类随机节律方块时整条路都没有方块实体），
        //   先判空再取 ticker——直接 be.getType() 会 NPE（第一百六十五批踩过）。
        BlockEntityTicker<?> ticker = be == null ? null : state.getTicker(level, be.getType());
        if (ticker != null) {
            for (int i = 0; i < entry.rate; i++) {
                try {
                    runTicker(level, pos, state, be, ticker);
                } catch (Throwable t) {
                    // 这台机器扛不住时序：只停它，并告诉施法者。绝不连坐别的机器。
                    MysticPivot.LOGGER.warn("[烛龙衔时] {} 的 ticker 第 {} 次补跑抛异常，已停止催动：{}",
                            pos, i + 1, t.toString());
                    dropEntry(map, pos, entry, true);
                    return;
                }
            }
        } else if (state.isRandomlyTicking()) {
            // 没有方块实体的随机节律方块（作物等）：催它的随机 tick
            for (int i = 0; i < entry.rate; i++) {
                try {
                    state.randomTick(level, pos, level.random);
                } catch (Throwable t) {
                    MysticPivot.LOGGER.warn("[烛龙衔时] {} 的随机 tick 第 {} 次补跑抛异常，已停止催动：{}",
                            pos, i + 1, t.toString());
                    dropEntry(map, pos, entry, true);
                    return;
                }
            }
        } else {
            dropEntry(map, pos, entry, false);          // 变成了不会自己走的东西
            return;
        }

        entry.remaining = left;
    }

    /**
     * 摘掉一条催动：撤登记、撤悬浮字；{@code notify=true} 时给施法者一句
     * 「扛不住时序」（仅 ticker 抛异常那条路用——其余都是静默的）。
     */
    private static void dropEntry(Map<BlockPos, Entry> map, BlockPos pos, Entry entry, boolean notify) {
        map.remove(pos, entry);
        removeLabel(entry.level, pos, entry.polled);
        if (notify) {
            ServerPlayer player = entry.level.getServer().getPlayerList().getPlayer(entry.caster);
            if (player != null) {
                message(player, "message.skydeitymysticpivot.chrono.failed", ChatFormatting.RED);
            }
        }
    }

    /**
     * 停机前清理（{@link ServerStoppingEvent} 在 {@code stopServer()} 存档<b>之前</b>
     * 触发）：把倍率悬浮字全撤掉再清表，否则那些字会随区块落盘，
     * 下次进游戏还浮在机器上，而催动早就不认了。
     */
    public static void onServerStopping(ServerStoppingEvent event) {
        clearAll();
    }

    /** 停机后兜一次底（正常路径上 {@link #onServerStopping} 已经清干净了）。 */
    public static void onServerStopped(ServerStoppedEvent event) {
        clearAll();
    }

    private static void clearAll() {
        for (Map<BlockPos, Entry> map : REGISTRY.values()) {
            for (Entry entry : map.values()) {
                removeLabel(entry.level, entry.pos, entry.polled);
            }
        }
        REGISTRY.clear();
    }

    /**
     * 调一次 ticker（{@code getTicker} 的返回带类型捕获，直接调不让过；
     * 这里按 TIAB 同款的非泛型调法擦掉捕获——ticker 本就只按手里的
     * 方块实体工作，擦除是安全的）。
     */
    @SuppressWarnings("unchecked")
    private static void runTicker(Level level, BlockPos pos, BlockState state,
                                  BlockEntity be, BlockEntityTicker<?> ticker) {
        ((BlockEntityTicker<BlockEntity>) ticker).tick(level, pos, state, be);
    }

    // ------------------------------------------------------------------ 登记表

    private static void track(ServerLevel level, BlockPos pos, BlockEntity be, UUID caster,
                              int rate, boolean polled) {
        REGISTRY.computeIfAbsent(level.dimension(), k -> new ConcurrentHashMap<>())
                .put(pos, new Entry(level, pos, be, caster, rate, DURATION, polled));  // 再右键 = 加码 + 续满
    }

    /**
     * 某一格当前的催动倍率（第一百八十批新增）：{@code 0} = 没在催。
     *
     * <p>给「自己读倍率」的机器用 —— 虚空采矿机的沟灵核心没有方块实体，
     * 拿不到 ticker，也就不用被反复调 tick，改为每 tick 来问一句「现在几倍」。</p>
     */
    public static int rateAt(Level level, BlockPos pos) {
        Map<BlockPos, Entry> map = REGISTRY.get(level.dimension());
        if (map == null) {
            return 0;
        }
        Entry entry = map.get(pos);
        return entry == null ? 0 : entry.rate;
    }

    private static Entry entryAt(ServerLevel level, BlockPos pos) {
        Map<BlockPos, Entry> map = REGISTRY.get(level.dimension());
        return map == null ? null : map.get(pos);
    }

    private static boolean isTracked(ServerLevel level, BlockPos pos) {
        return entryAt(level, pos) != null;
    }

    private static int totalEntries() {
        int total = 0;
        for (Map<BlockPos, Entry> map : REGISTRY.values()) {
            total += map.size();
        }
        return total;
    }

    // ------------------------------------------------------------------ 倍率悬浮字

    /**
     * 把当前倍率摆到机器正上方：小盒子里按标记现找，找得到就改字，
     * 找不到就补一个新的（区块重载回来的那个会被这样「收养」）。
     */
    private static void updateLabel(ServerLevel level, BlockPos pos, int rate, boolean polled) {
        try {
            Vec3 at = labelPos(pos, polled);
            Display.TextDisplay label = findLabel(level, at);
            boolean fresh = false;
            if (label == null) {
                label = EntityType.TEXT_DISPLAY.create(level);
                if (label == null) {
                    return;
                }
                fresh = true;
            }
            // 先 load 再入世：字在实体被追踪之前就写好，客户端收到的出生包直接带字
            label.load(labelTag(level, at, rate));
            label.addTag(LABEL_TAG);
            if (fresh) {
                level.addFreshEntity(label);
            }
        } catch (Throwable t) {
            // 悬浮字只是观感：摆不上也不该让已经扣魔的催动失败
            MysticPivot.LOGGER.warn("[烛龙衔时] 倍率悬浮字更新失败：{}", t.toString());
        }
    }

    /** 撤掉机器上方的倍率悬浮字（小盒子内按标记全撤）。 */
    private static void removeLabel(ServerLevel level, BlockPos pos, boolean polled) {
        try {
            Vec3 at = labelPos(pos, polled);
            for (Display.TextDisplay label : labelsNear(level, at)) {
                label.discard();
            }
        } catch (Throwable t) {
            MysticPivot.LOGGER.warn("[烛龙衔时] 倍率悬浮字撤销失败：{}", t.toString());
        }
    }

    private static Display.TextDisplay findLabel(ServerLevel level, Vec3 at) {
        for (Display.TextDisplay label : labelsNear(level, at)) {
            return label;
        }
        return null;
    }

    /**
     * 机器上方 1×1×1 小盒子里的自家悬浮字。
     *
     * <p>盒子必须这么小：相邻两台机器的悬浮字相距 1 格，盒子再大就会
     * 摸到别人家的字（误收养 / 误撤）。</p>
     */
    private static java.util.List<Display.TextDisplay> labelsNear(ServerLevel level, Vec3 at) {
        return level.getEntitiesOfClass(Display.TextDisplay.class,
                AABB.ofSize(at, 1.0D, 1.0D, 1.0D), e -> e.getTags().contains(LABEL_TAG));
    }

    /**
     * 倍率字的位置。
     *
     * <p>{@code polled}（虚空采矿机）要抬到<b>光坛之上</b>：机器背后是七格高的六层光环坛，
     * 字浮在机器上方 0.7 格会埋进坛身里，玩家什么也看不见。</p>
     */
    private static Vec3 labelPos(BlockPos pos, boolean polled) {
        double dy = polled ? VoidMiningStructure.height() + LABEL_CANOPY_EXTRA : LABEL_DY;
        return Vec3.atCenterOf(pos).add(0.0D, dy, 0.0D);
    }

    /**
     * 悬浮字的 NBT：{@code text}（组件 JSON 串，如 {@code {"text":"1024x","color":"gold"}}）、
     * {@code billboard="center"}（永远正对玩家）、{@code see_through=true}
     * （多层机器的内部方块也挡不住它——浮在机器表面这件事必须可靠）、
     * {@code NoGravity}，外加 {@code load} 强截的 {@code Pos/Motion/Rotation} 三张表。
     */
    private static CompoundTag labelTag(ServerLevel level, Vec3 at, int rate) {
        Component text = Component.literal(rate + "x").withStyle(ChatFormatting.GOLD);
        CompoundTag tag = new CompoundTag();
        tag.putString("text", Component.Serializer.toJson(text, level.registryAccess()));
        tag.putString("billboard", "center");
        tag.putBoolean("see_through", true);
        tag.putBoolean("NoGravity", true);
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(at.x));
        pos.add(DoubleTag.valueOf(at.y));
        pos.add(DoubleTag.valueOf(at.z));
        tag.put("Pos", pos);
        ListTag motion = new ListTag();
        for (int i = 0; i < 3; i++) {
            motion.add(DoubleTag.valueOf(0.0D));
        }
        tag.put("Motion", motion);
        ListTag rot = new ListTag();
        rot.add(FloatTag.valueOf(0.0F));
        rot.add(FloatTag.valueOf(0.0F));
        tag.put("Rotation", rot);
        return tag;
    }

    // ------------------------------------------------------------------ 特效（TC4 destination_wisp_blue）

    /**
     * 发射：从玩家眼睛打出<b>三颗</b>蓝精灵，全程位移 = 目标方块中心 − 眼睛。
     *
     * <p>{@code count = 0} 的 {@code sendParticles} 在客户端走
     * 「精确位置 + 速度 = (xDist,yDist,zDist) × speed」那一分支，
     * 而蓝精灵的 provider 把速度三元组除以寿命当作全程位移——
     * 于是三颗精灵会各自沿直线飘到机器上（寿命各自随机 36~51 tick，
     * 速度因此略有差异，看起来是一小簇星屑一起飞过去）。</p>
     *
     * <p>为什么要叠三颗：蓝精灵自带 {@code alpha = 0.5}，单颗太虚
     * （用户：「飞过去稍微对比度高一点 现在透明多一点」）。同一点叠三颗，
     * 有效不透明度约 {@code 1-0.5³ ≈ 0.88}，淡蓝变实；粒子接口不暴露 alpha，
     * 这是不动本体又能加深观感的办法。三颗各自抖动 0.06 格，
     * 不会精确重合（重合的话只是单纯变实，没有厚度）。</p>
     */
    private static void launchFx(ServerPlayer player, Vec3 target) {
        try {
            ServerLevel level = player.serverLevel();
            Vec3 from = player.getEyePosition();
            Vec3 delta = target.subtract(from);
            for (int i = 0; i < 3; i++) {
                double jx = (level.random.nextDouble() - 0.5D) * 0.12D;
                double jy = (level.random.nextDouble() - 0.5D) * 0.12D;
                double jz = (level.random.nextDouble() - 0.5D) * 0.12D;
                level.sendParticles(ThaumcraftParticle.DESTINATION_WISP_BLUE.type(),
                        from.x + jx, from.y + jy, from.z + jz, 0, delta.x, delta.y, delta.z, 1.0D);
            }
        } catch (Throwable t) {
            // 粒子路径出问题不影响已经扣魔的催动本身
            MysticPivot.LOGGER.warn("[烛龙衔时] 特效发射失败（不影响催动）：{}", t.toString());
        }
    }

    // ------------------------------------------------------------------ 工具

    private static void message(ServerPlayer player, String key, ChatFormatting color) {
        player.displayClientMessage(
                Component.translatable(key).withStyle(color), true);
    }

    private static void failSound(ServerPlayer player) {
        player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.6F, 0.7F);
    }
}
