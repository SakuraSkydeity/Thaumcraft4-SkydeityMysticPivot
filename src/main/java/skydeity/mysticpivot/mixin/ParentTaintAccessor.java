package skydeity.mysticpivot.mixin;

import dev.tc4port.thaumcraft.block.TaintSpreadLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 本体「还原腐化群系列」的补丁口。
 *
 * <p>背景：本体 api 包的 {@code TaintBiomeApi} <b>只能腐化</b>
 * （{@code taintLoadedColumn}），没有任何<b>反向</b>入口；真正把一列群系还原回去的
 * {@code TaintSpreadLogic.restoreOriginalBiomeColumn(ServerLevel, BlockPos)} 在实现包里，
 * 附属按纪律（README §2）不能 import。本体自己的净化花
 * （{@code EtherealBloomBlockEntity#purifyColumn}）走的就是这个方法。</p>
 *
 * <p>沿用本项目进入本体内部的同一条窄口约定（见
 * {@link InfusionMatrixBlockEntityAccessor} 与 {@link ParentWarpAccessor}）：
 * 一个只转发、不注入任何逻辑的访问器接口 Mixin，{@code remap = false}（目标类未混淆）。</p>
 *
 * <p>★ 该方法本身是<b>幂等且带兜底</b>的（读源码确认）：有历史就按历史还原，
 * 没有历史就退回<b>世界生成的原始群系</b>，且拒绝把群系还原成 tainted_land / eerie /
 * magical_forest 这三种特殊群系。但仍然<b>不要每个 tick 无脑调</b>——它内部会遍历
 * 该 chunk 的全部 section 并重发群系包，所以调用方先判「当前群系是不是特殊群系」再进来。</p>
 */
@Mixin(value = TaintSpreadLogic.class, remap = false)
public interface ParentTaintAccessor {

    /** 把 {@code pos} 所在的那一列（1.21 里 = 一个 4x4 群系 quart 格）还原成原始群系。 */
    @Invoker("restoreOriginalBiomeColumn")
    static void mysticpivot$restoreOriginalBiomeColumn(ServerLevel level, BlockPos pos) {
        throw new AssertionError("mixin invoker");
    }
}
