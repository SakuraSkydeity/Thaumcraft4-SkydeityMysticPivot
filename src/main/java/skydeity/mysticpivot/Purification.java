package skydeity.mysticpivot;

import dev.tc4port.thaumcraft.api.aspect.VisAction;
import dev.tc4port.thaumcraft.api.flux.FluxApi;
import dev.tc4port.thaumcraft.api.flux.FluxConsumeContext;
import dev.tc4port.thaumcraft.api.flux.FluxConsumeResult;
import dev.tc4port.thaumcraft.api.player.PlayerWarpApi;
import dev.tc4port.thaumcraft.api.player.PlayerWarpView;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import skydeity.mysticpivot.mixin.ParentTaintAccessor;
import skydeity.mysticpivot.mixin.ParentWarpAccessor;

/**
 * 玄枢净界石的「净化」逻辑：一次扫描把<b>一片地界</b>的四种腐化一起抹掉。
 *
 * <p>范围分两档：单独立着只管<b>它自己所在的那一个区块</b>；围着它砌成
 * {@link #RING_RADIUS} 的 3×3「八方净界阵」——中心一块净界石，八方八格各立一块卦位石、
 * 八种卦各不相同——范围就扩成<b>以本区块为中心的 {@link #CHUNK_RADIUS}×{@link #CHUNK_RADIUS}
 * 共 9 个区块</b>。判据借的是全项目唯一那一处数卦位石的入口（{@code Fengshui.trigramsAt}）：
 * 3×3 除了中心正好剩八格，八格凑齐八种，就是「八方各镇一卦」。</p>
 *
 * <table border="1">
 *   <caption>四路扫除</caption>
 *   <tr><th>对象</th><th>做法</th><th>走哪条路</th></tr>
 *   <tr><td>腐化方块</td><td>按本体自己的 taint 标签整块替换（土→土块、草→草方块、纤维/孢子/触须→空气）</td>
 *       <td>公开 API（方块标签）+ 普通 {@code setBlock}</td></tr>
 *   <tr><td>腐化群系</td><td>把区块内 16 个 4×4 群系格逐列还原</td>
 *       <td>{@link ParentTaintAccessor}（本体没给反向 API）</td></tr>
 *   <tr><td>咒流之垢</td><td>吃掉半径内的 Flux Goo / Gas</td>
 *       <td>{@code FluxApi.consumeNearby}（公开 API）</td></tr>
 *   <tr><td>扭曲与腐化负面效果</td><td>永久/粘性/临时扭曲全部清零，九种腐化效果逐个拔掉，
 *       再挂一层「净界庇护」压住装备带来的扭曲事件</td>
 *       <td>读走 {@code PlayerWarpApi}；写走 {@link ParentWarpAccessor}</td></tr>
 * </table>
 *
 * <h2>为什么要「预算」</h2>
 * <p>一个区块满打满算 16×16×384 ≈ 98 000 格，九个区块就是近九十万格。首次落成时里面可能真有几万格腐化，
 * 一次性写下去会卡一下；所以每轮扫描最多改 {@link #BLOCK_BUDGET} 格，
 * 剩下的留给下一轮——扫描间隔只有 {@value #SWEEP_INTERVAL} tick，
 * 几轮之内必然收敛，而单轮的开销被钉死在一个上界里。</p>
 *
 * <h2>九区块怎么才不卡：先问 section，再走格子</h2>
 * <p>干净区块里逐格 {@code getBlockState} 是纯浪费。每个 {@code LevelChunkSection} 先问一句
 * {@code maybeHas(方块是不是 taint 标签)}——那是一次调色板/位图判断，不落格——
 * 答否就整段跳过。于是「九区块里其实一处腐化都没有」这种最常见的情形，
 * 代价只剩每段一次判断。</p>
 *
 * <h2>为什么群系要先判再还原</h2>
 * <p>{@code restoreOriginalBiomeColumn} 内部会遍历该 chunk 的全部 section 并重发群系包，
 * 是这篇里最贵的一步；所以先用 {@code level.getBiome} 判一下「这一格现在是不是
 * 腐化之地 / 阴森 / 魔法森林」，是才动手。三种都算：本体自己的净化花
 * （{@code isSpecialBiome}）也是这么判的。</p>
 */
public final class Purification {

    /** 扫描间隔（tick）。40 tick = 2 秒扫一遍。 */
    public static final int SWEEP_INTERVAL = 40;

    /** 八方阵的水平半径：1 即 3×3（除中心外正好八格）。 */
    public static final int RING_RADIUS = 1;

    /** 成阵之后往外扫几个区块：1 即「以本区块为中心的 3×3 共 9 个区块」。 */
    public static final int CHUNK_RADIUS = 1;

    /**
     * 每轮扫描最多改写的腐化方块数（超出留给下一轮）。
     *
     * <p>这是<b>整轮的总预算</b>，不是每个区块一份：九区块也照这个数走，
     * 快慢只影响收敛要几轮，不影响单轮卡顿的上界。</p>
     */
    private static final int BLOCK_BUDGET = 6_144;

    /** 单区块时的咒流扫除半径。区块最远对角约 22 格，取 24 即可覆盖整个区块并多兜一点。 */
    private static final int FLUX_RANGE = 24;

    /** 每往外多一个区块，咒流半径要加多少（区块边长 16）。 */
    private static final int FLUX_RANGE_PER_CHUNK = 16;

    /** 单轮最多吃掉的咒流量（够吃掉半径内容量上限的一大批）。 */
    private static final int FLUX_REQUEST = 4_096;

    /** 净界庇护的持续时间；两倍于扫描间隔，保证在场期间不断档。 */
    private static final int WARD_TICKS = SWEEP_INTERVAL * 2;

    /** 审计用的来源标识（咒流消耗事件里会带上）。 */
    private static final ResourceLocation SOURCE =
            ResourceLocation.fromNamespaceAndPath(MysticPivot.MODID, "purifying_stone");

    /** 本体 {@code data/thaumcraft/tags/block/taint.json} 里那八种腐化方块。 */
    private static final TagKey<Block> TAINT =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("thaumcraft", "taint"));

    /** 本体的三种「特殊群系」——与 {@code TaintSpreadLogic.isSpecialBiome} 同一口径。 */
    private static final List<ResourceKey<Biome>> SPECIAL_BIOMES = List.of(
            biome("tainted_land"), biome("eerie"), biome("magical_forest"));

    /** 本体注册的全部「扭曲 / 腐化」负面效果（id 取自 {@code TCEffects} 的登记名）。 */
    private static final List<ResourceLocation> CORRUPT_EFFECTS = List.of(
            effect("flux_taint"),                 // 咒流腐毒
            effect("vis_exhaust"),                // 灵气枯竭
            effect("infectious_vis_exhaust"),     // 传染性灵气枯竭
            effect("unnatural_hunger"),           // 反常饥饿
            effect("death_gaze"),                 // 死亡凝视
            effect("blurred_vision"),             // 视线模糊
            effect("sun_scorned"),                // 畏光
            effect("thaumarhia"));                // 秘毒

    /** 本体自己的「扭曲庇护」——它会跳过周期性的扭曲事件掷骰。 */
    private static final ResourceLocation WARP_WARD = effect("warp_ward");

    private Purification() {
    }

    private static ResourceKey<Biome> biome(String path) {
        return ResourceKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("thaumcraft", path));
    }

    private static ResourceLocation effect(String path) {
        return ResourceLocation.fromNamespaceAndPath("thaumcraft", path);
    }

    // ------------------------------------------------------------------ 主入口

    /**
     * 八方阵成没成——净界石周围 3×3 的八格里，是不是「凑齐了八种卦位石」。
     *
     * <p>3×3 除去中心正好剩八格，八格出现八种卦 ⇒ 每种各一块（{@link Fengshui#trigramsAt}
     * 数的是<b>种类</b>，堆八块同一种不算）。哪一格放哪一卦不限：八卦镇八方，
     * 方位不必和卦位对上——要求严格到方位就成了「得先背下来才能搭」的谜题。</p>
     *
     * <p>这只是「<b>摆好了</b>」，不等于「<b>开起来了</b>」：开阵要玩家拿法杖右键点一下中心
     * （见 {@code ModBlocks.XuanshuPurifyingStoneBlock#useItemOn}）。</p>
     */
    public static boolean arrayComplete(ServerLevel level, BlockPos pos) {
        return Fengshui.trigramsAt(level, pos, RING_RADIUS, 1).size() == 8;
    }

    /**
     * 当下的作用范围，以区块为单位（0 = 只管本区块，{@link #CHUNK_RADIUS} = 3×3 共 9 个区块）。
     *
     * <p>判据取<b>方块自己的 {@code active} 状态</b>，不重算一遍阵势：那个状态由法杖右键开阵、
     * 由计划刻在阵散时关掉，是世界里的唯一答案。这里要是自己再数一遍石头，就会出现
     * 「外观还是亮的（{@code active=true}），扫描却按 3×3 走（自己数出来不够）」这种对不上。</p>
     */
    public static int reachAt(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof ModBlocks.XuanshuPurifyingStoneBlock
                && state.getValue(ModBlocks.XuanshuPurifyingStoneBlock.ACTIVE)
                ? CHUNK_RADIUS : 0;
    }

    /**
     * 扫一遍。返回本轮是否真的动了世界（用来决定要不要冒粒子、出声）。
     */
    public static boolean sweep(ServerLevel level, BlockPos origin) {
        int reach = reachAt(level, origin);
        int blocks = cleanseTaintBlocks(level, origin, reach);
        int biomes = restoreBiomes(level, origin, reach);
        int flux = consumeFlux(level, origin, reach);
        int cleansed = cleansePlayers(level, origin, reach);
        return blocks + biomes + flux + cleansed > 0;
    }

    /** 落成那一下的仪式感：一声钟鸣 + 一圈白烟 + 一行动作栏提示。 */
    public static void onPlaced(ServerLevel level, BlockPos pos, Player player) {
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.6F, 1.4F);
        level.sendParticles(ParticleTypes.END_ROD,
                pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                28, 1.6D, 0.6D, 1.6D, 0.02D);
        player.displayClientMessage(
                Component.translatable("message.skydeitymysticpivot.purifying_stone.placed"), true);
    }

    // ------------------------------------------------------------------ 一、腐化方块

    /**
     * 腐化方块 → 普通方块。判据取「本体自己的 taint 标签」，不硬编码单块。
     *
     * <p>区块按<b>由近及远</b>收：先中心那一个，再外面一圈 —— 先干净的是玩家脚下那一片。
     * 预算 {@link #BLOCK_BUDGET} 是整轮共用的，所以九个区块一起清也不会比一个区块时更卡。</p>
     */
    private static int cleanseTaintBlocks(ServerLevel level, BlockPos origin, int reach) {
        ChunkPos cp = level.getChunkAt(origin).getPos();
        ArrayList<BlockPos> targets = new ArrayList<>();

        collectTaint(level, cp, targets);
        for (int ring = 1; ring <= reach && targets.size() < BLOCK_BUDGET; ring++) {
            for (int dx = -ring; dx <= ring && targets.size() < BLOCK_BUDGET; dx++) {
                for (int dz = -ring; dz <= ring && targets.size() < BLOCK_BUDGET; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;                       // 只走这一圈，别把里面重复数一遍
                    }
                    collectTaint(level, new ChunkPos(cp.x + dx, cp.z + dz), targets);
                }
            }
        }
        if (targets.isEmpty()) {
            return 0;
        }

        // ★ 先收位置、后统一写：边遍历边 setBlock 会让正在读的那个 section 的调色板变动，
        //   遍历本身就不稳（也是「读一半、写一半」最容易出怪东西的地方）。
        int changed = 0;
        for (BlockPos target : targets) {
            BlockState current = level.getBlockState(target);
            if (!current.is(TAINT)) {
                continue;                       // 两轮之间被别人先清了
            }
            if (level.setBlock(target, replacementFor(current.getBlock()), Block.UPDATE_ALL)) {
                changed++;
            }
        }
        return changed;
    }

    /**
     * 收一个区块里所有腐化方块的位置（受 {@link #BLOCK_BUDGET} 限制）。
     *
     * <p><b>先问 section、再走格子</b>：{@code LevelChunkSection.maybeHas} 是一次调色板/位图判断，
     * 不落格；答否就整段跳过。九区块里最常见的情形本来就是「一处腐化都没有」，
     * 这一句把它压成十几个判断。</p>
     *
     * <p>没加载的区块直接跳过（{@code getChunkNow} 不做生成）：为擦一格把区块拉起来不值得，
     * 等它加载了下一轮自然会轮到。</p>
     */
    private static void collectTaint(ServerLevel level, ChunkPos cp, List<BlockPos> targets) {
        if (targets.size() >= BLOCK_BUDGET) {
            return;
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(cp.x, cp.z);
        if (chunk == null) {
            return;
        }
        int minSection = chunk.getMinSection();
        int maxSection = chunk.getMaxSection();
        for (int sectionY = minSection; sectionY < maxSection && targets.size() < BLOCK_BUDGET; sectionY++) {
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY));
            if (section == null || section.hasOnlyAir() || !section.maybeHas(state -> state.is(TAINT))) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(sectionY);
            for (int y = 0; y < 16 && targets.size() < BLOCK_BUDGET; y++) {
                for (int z = 0; z < 16 && targets.size() < BLOCK_BUDGET; z++) {
                    for (int x = 0; x < 16 && targets.size() < BLOCK_BUDGET; x++) {
                        if (section.getBlockState(x, y, z).is(TAINT)) {
                            targets.add(new BlockPos(cp.getMinBlockX() + x, baseY + y, cp.getMinBlockZ() + z));
                        }
                    }
                }
            }
        }
    }

    /**
     * 腐化方块还原成什么。
     *
     * <p>映射照本体自己的 {@code taint_decay} 规则（见
     * {@code data/thaumcraft/thaumcraft/taint_decay/default.json}）：
     * 腐化之土 → 泥土、纤维腐化 / 腐化草 / 腐化植物 / 孢子柄 → 空气。
     * 唯一<b>刻意不同</b>的一处是<b>结壳腐化</b>：本体让它自然衰变时吐一滩咒流之垢
     * （{@code flux_goo} 9 份），净化石的方向是「把它清干净」，所以直接抹成空气
     * ——附近的咒流本来就由 {@link #consumeFlux} 一起吃掉。</p>
     */
    private static BlockState replacementFor(Block tainted) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(tainted);
        String path = id.getPath();
        return switch (path) {
            case "tainted_soil", "tainted_flesh" -> Blocks.DIRT.defaultBlockState();
            case "tainted_grass" -> Blocks.GRASS_BLOCK.defaultBlockState();
            default -> Blocks.AIR.defaultBlockState();
        };
    }

    // ------------------------------------------------------------------ 二、腐化群系

    /** 把范围内各区块里 16 个 4×4 群系格逐列还原（只还原确实是特殊群系的那几格）。 */
    private static int restoreBiomes(ServerLevel level, BlockPos origin, int reach) {
        ChunkPos cp = level.getChunkAt(origin).getPos();
        int restored = 0;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                restored += restoreChunk(level, new ChunkPos(cp.x + dx, cp.z + dz), origin.getY());
            }
        }
        return restored;
    }

    /** 一个区块的群系还原；没加载的区块跳过（{@code getBiome} 会把未加载的区块拉起来）。 */
    private static int restoreChunk(ServerLevel level, ChunkPos cp, int sampleY) {
        if (level.getChunkSource().getChunkNow(cp.x, cp.z) == null) {
            return 0;
        }
        int quartChunkX = QuartPos.fromBlock(cp.getMinBlockX());
        int quartChunkZ = QuartPos.fromBlock(cp.getMinBlockZ());
        int restored = 0;
        for (int qx = 0; qx < 4; qx++) {
            for (int qz = 0; qz < 4; qz++) {
                BlockPos probe = new BlockPos(QuartPos.toBlock(quartChunkX + qx), sampleY,
                        QuartPos.toBlock(quartChunkZ + qz));
                if (!isSpecial(level.getBiome(probe))) {
                    continue;
                }
                ParentTaintAccessor.mysticpivot$restoreOriginalBiomeColumn(level, probe);
                restored++;
            }
        }
        return restored;
    }

    private static boolean isSpecial(Holder<Biome> biome) {
        for (ResourceKey<Biome> key : SPECIAL_BIOMES) {
            if (biome.is(key)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 三、咒流之垢

    /** 吃掉半径内的 Flux Goo / Gas。成阵之后半径随范围一起放大（每多一个区块加 16 格）。 */
    private static int consumeFlux(ServerLevel level, BlockPos origin, int reach) {
        int range = FLUX_RANGE + reach * FLUX_RANGE_PER_CHUNK;
        FluxConsumeResult result = FluxApi.consumeNearby(level, origin, range, FLUX_REQUEST,
                FluxConsumeContext.machine(SOURCE, origin), VisAction.EXECUTE);
        return result.consumedQuanta();
    }

    // ------------------------------------------------------------------ 四、扭曲与负面效果

    /** 对范围内的每个玩家做一次净化。 */
    private static int cleansePlayers(ServerLevel level, BlockPos origin, int reach) {
        ChunkPos cp = level.getChunkAt(origin).getPos();
        int touched = 0;
        for (ServerPlayer player : level.players()) {
            ChunkPos pc = new ChunkPos(player.blockPosition());
            if (Math.abs(pc.x - cp.x) > reach || Math.abs(pc.z - cp.z) > reach) {
                continue;
            }
            touched += cleanse(player);
        }
        return touched;
    }

    private static int cleanse(ServerPlayer player) {
        int touched = 0;
        PlayerWarpView view = PlayerWarpApi.view(player);
        if (view.permanent() > 0) {
            ParentWarpAccessor.mysticpivot$setPermanentWarp(player, 0);
            touched++;
        }
        if (view.sticky() > 0) {
            ParentWarpAccessor.mysticpivot$setStickyWarp(player, 0);
            touched++;
        }
        if (view.temporary() > 0) {
            ParentWarpAccessor.mysticpivot$setTemporaryWarp(player, 0);
            touched++;
        }
        for (ResourceLocation id : CORRUPT_EFFECTS) {
            Holder<MobEffect> effect = lookup(id);
            if (effect != null && player.hasEffect(effect)) {
                player.removeEffect(effect);
                touched++;
            }
        }
        // 净界庇护：扭曲数值清零之后，「随身装备/道具自带的扭曲」仍会走周期性扭曲事件，
        // 这一层是给那种来源兜底的；在场期间一直续，离场后自然过期。
        Holder<MobEffect> ward = lookup(WARP_WARD);
        if (ward != null) {
            player.addEffect(new MobEffectInstance(ward, WARD_TICKS, 0, true, true, true));
        }
        return touched;
    }

    private static Holder<MobEffect> lookup(ResourceLocation id) {
        return BuiltInRegistries.MOB_EFFECT
                .getHolder(ResourceKey.create(Registries.MOB_EFFECT, id))
                .orElse(null);
    }

    // ------------------------------------------------------------------ 客户端氛围

    /**
     * 成阵 / 散阵那一下：给附近玩家一条动作栏提示，成阵时再加一声轻响与一圈灵光。
     *
     * <p>提示必须给：范围从「本区块」跳到「九个区块」是玩家搭对了的<b>唯一反馈</b>，
     * 光靠光亮 8 → 15 这点差别，白天根本看不出来。</p>
     */
    public static void announceArrayChange(ServerLevel level, BlockPos pos, boolean formed) {
        Component message = Component.translatable(formed
                ? "message.skydeitymysticpivot.purifying_stone.array_on"
                : "message.skydeitymysticpivot.purifying_stone.array_off");
        for (Player player : level.players()) {
            if (player.blockPosition().closerThan(pos, 48.0D)) {
                player.displayClientMessage(message, true);
            }
        }
        if (formed) {
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.9F, 1.2F);
            level.sendParticles(ParticleTypes.END_ROD,
                    pos.getX() + 0.5D, pos.getY() + 1.1D, pos.getZ() + 0.5D,
                    40, 1.4D, 0.2D, 1.4D, 0.03D);
        }
    }

    /** 每 8 tick 往上飘一缕灵光，告诉玩家「这台还在工作」。 */
    public static void ambient(ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getGameTime() % 8 != 0) {
            return;
        }
        level.sendParticles(ParticleTypes.END_ROD,
                pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.8D,
                pos.getY() + 1.05D,
                pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.8D,
                1, 0.0D, 0.04D, 0.0D, 0.0D);
    }
}
