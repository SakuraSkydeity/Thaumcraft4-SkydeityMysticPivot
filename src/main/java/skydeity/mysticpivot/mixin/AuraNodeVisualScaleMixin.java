package skydeity.mysticpivot.mixin;

import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.client.AuraNodeRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 钳制灵气节点的<b>视觉尺寸</b>，让它不随要素数值一起膨胀。
 *
 * <h2>病灶</h2>
 * <p>本体 {@code AuraNodeRenderer#renderNodeLayers} 给每一门要素算一层光斑，半径是
 * <b>要素数值的线性函数</b>：</p>
 * <pre>
 *   float scale = 0.2F + pulse * (aspect.getValue() / 50.0F);   // 每门光斑
 *   float coreSize = 0.1F + average / 150.0F;                   // 核心
 * </pre>
 * <p>普通节点的要素值也就是几十，故光斑最大约一两个方块——正常观感。而玄枢充能器为了
 * 把产出顶到 {@code 64/t}，必须把每门要素的<b>上限</b>一路推到 4096 上下
 * （产出 = {@code floor(sqrt(上限 × 系数))}，见
 * {@code ModBlockEntities.XuanshuChargerBlockEntity#growEnergizedNode}）。
 * 充能完成后渲染器读的正是 {@code baseVis}：要素值 4096 ⇒ 单层光斑半径
 * {@code 0.2 + 0.75 × 81.9 ≈ 61.6} 个方块，核心 {@code ≈ 27} 个方块——
 * 节点瞬间胀成一颗吞掉半个屏幕的巨大光球（用户第六十七批反馈：
 * 「增加灵气节点要素数量时灵气节点也会跟着放大」）。</p>
 *
 * <h2>修法</h2>
 * <p>在本体给每门要素算光斑<b>之前</b>，把传进 {@code renderNodeLayers} 的那张
 * 「要素 → 数值」表<b>逐值截断</b>到 {@value #VISUAL_CAP}。只动渲染用的这一份副本，
 * 节点实体里真实存的 {@code baseVis} 一个字节都不碰 ⇒ <b>产出照旧按真实上限算</b>，
 * 只是画面里的节点稳稳停在「一门要素 64」的大小，不再随充能进度膨胀。</p>
 *
 * <p>{@code renderNodeLayers} 是本体三个渲染入口（世界里的自由节点、节点罐、
 * 物品栏里的节点图标）共用的那一层，故此一处即全覆盖。截断只压高值，普通节点
 * （单门数值基本都在 64 以下）完全不受影响。</p>
 *
 * <h2>为什么用 {@code index = 1}</h2>
 * <p>{@code renderNodeLayers} 是 {@code private static}，签名是
 * {@code (AuraNodeState, Map, PoseStack, MultiBufferSource, int, float, double, long, RenderType, RenderType)}。
 * 静态方法没有 {@code this}，所以第二个形参（那张表）的局部变量槽位就是 {@code 1}——
 * 用 {@code argsOnly = true} 明指名次，比靠 {@code ordinal} 数类型稳。</p>
 */
@Mixin(value = AuraNodeRenderer.class, remap = false)
public abstract class AuraNodeVisualScaleMixin {

    /**
     * 渲染用的单门要素上限。取值就是「一门要素 64」时本体自己算出来的光斑大小
     * （{@code 0.2 + pulse × 64/50}），即一颗「很亮但正常」的节点，与玄枢系
     * 「64」这个数字同一口径。
     */
    private static final int VISUAL_CAP = 64;

    @ModifyVariable(method = "renderNodeLayers", at = @At("HEAD"), argsOnly = true, index = 1)
    private static Map<AspectId, Integer> mysticpivot$capVisualAspectAmounts(Map<AspectId, Integer> visualAspects) {
        if (visualAspects == null || visualAspects.isEmpty()) {
            return visualAspects;
        }
        boolean over = false;
        for (int value : visualAspects.values()) {
            if (value > VISUAL_CAP) {
                over = true;
                break;
            }
        }
        if (!over) {
            return visualAspects;           // 常规节点：原样放行，键序与规模都不变
        }
        LinkedHashMap<AspectId, Integer> capped = new LinkedHashMap<>(visualAspects.size());
        visualAspects.forEach((aspect, value) -> capped.put(aspect, Math.min(value, VISUAL_CAP)));
        return capped;
    }
}
