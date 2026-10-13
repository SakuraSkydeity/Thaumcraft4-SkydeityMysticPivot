package skydeity.mysticpivot;

import dev.tc4port.thaumcraft.api.ThaumcraftApiCommon;
import dev.tc4port.thaumcraft.api.aspect.AspectAmounts;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * 把八个八卦要素挂到「稍有门槛」的原版物品上（需要少量合成或采集工序），
 * 让玩家拿神秘测量仪扫一遍就能认识八卦。这些物品同时是坩埚煮取八卦源质的原料——
 * 把它们投入坩埚烧炼，就能直接得到对应的八卦要素源质。
 *
 * <h2>为什么必须走这个 API，而不是数据包</h2>
 * <p>数据包 {@code object_aspects} 的 {@code direct} 段是 <b>put</b> 语义：addon 的同名条目会
 * <b>整条覆盖</b>本体的条目。{@code stone} / {@code dirt} / {@code torch} 这类基础物品本体早已
 * 写进 {@code direct}，所以数据包改不动它们；{@code tags} 段又只在 {@code direct} 查不到时才生效。
 * 只有运行时 API 条目是<b>最后</b>评估的，{@link ThaumcraftApiCommon#registerAdditionalObjectAspects}
 * 还是 <b>additive</b>（叠加）语义，因此能在不抹掉本体原有要素的前提下给它们补上八卦。</p>
 *
 * <p>注册时机：八卦要素本身是<b>数据包注册表</b>（{@code data/<ns>/thaumcraft/aspects/*.json}），
 * 在 mod 构造期还不存在；而 {@code AspectAmounts} 在校验时会查要素目录，
 * 认不出就抛 {@code Unknown object aspect}。所以必须等到服务端数据包加载完成
 * （{@code ServerAboutToStartEvent}）。客户端不需要注册——运行时条目会随快照同步过去。</p>
 */
public final class ModAspectSources {

    private static boolean installed;

    private ModAspectSources() {
    }

    /**
     * 幂等安装；失败时不置位，留给下一次服务端事件重试。
     *
     * @return 本次是否完成安装
     */
    public static synchronized boolean install() {
        if (installed) {
            return true;
        }
        try {
            add(Items.DAYLIGHT_DETECTOR, "qian", 2);  // 乾 · 天：日光照临（玻璃+石英+台阶）
            add(Items.GOLDEN_APPLE, "dui", 2);        // 兑 · 泽金：金锭裹果
            add(Items.FIREWORK_ROCKET, "li", 2);      // 离 · 火：火药腾焰
            add(Items.BELL, "zhen", 2);               // 震 · 雷：洪钟震响
            add(Items.NOTE_BLOCK, "xun", 2);          // 巽 · 风木：八音之盒
            add(Items.SEAGRASS, "kan", 2);            // 坎 · 水：海草近渊
            add(Items.BLAST_FURNACE, "gen", 2);       // 艮 · 山：坚炉矿石
            add(Items.HAY_BLOCK, "kun", 2);           // 坤 · 地：稼穑所积
        } catch (RuntimeException | LinkageError failure) {
            MysticPivot.LOGGER.warn("[skydeitymysticpivot] 八卦要素来源注册失败，稍后重试", failure);
            return false;
        }
        installed = true;
        MysticPivot.LOGGER.info("[skydeitymysticpivot] 已把八卦要素挂到 8 类常见物品上");
        return true;
    }

    private static void add(Item item, String aspect, int amount) {
        ThaumcraftApiCommon.registerAdditionalObjectAspects(item, AspectAmounts.of(aspectId(aspect), amount));
    }

    private static AspectId aspectId(String path) {
        return AspectId.of(ResourceLocation.fromNamespaceAndPath(MysticPivot.MODID, path));
    }
}
