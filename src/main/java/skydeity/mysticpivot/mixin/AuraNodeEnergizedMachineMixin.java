package skydeity.mysticpivot.mixin;

import dev.tc4port.thaumcraft.block.AuraNodeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import skydeity.mysticpivot.ModBlocks;

/**
 * 让本体的「充能节点结构完整性」检查把<b>玄枢充能器</b>认作合法装配。
 *
 * <p>本体判据（{@code hasValidEnergizedStructure}）是<b>三段装配</b>：节点正下方是它自己的
 * {@code NodeStabilizerBlockEntity}、且该底座<b>未被红石通电</b>、节点正上方是
 * {@code NodeTransducerBlockEntity}。玄枢充能器把这三段压成了<b>一个方块</b>——
 * 立在节点正下方，一个人既当底座又当换能器——三条判据一条都对不上，若不在此处放宽，
 * 充能节点一成型就会被本体自己的 {@code explodeEnergizedNode} 炸掉（§12.67 批为玄枢节点
 * 稳定器踩过同一个坑；稳定器已在第六十六批整项删除，本条只伺候充能器）。</p>
 *
 * <p>本条规定得比本体宽：<b>只要节点正下方是玄枢充能器即算合法</b>——不要求红石状态
 * （通电是它的工作态而非故障），也不要求上方有换能器（单方块装配没有换能器）。
 * 充能器被挖走时本条自然失效，节点照本体规矩爆炸——与本体「拆稳定器/换能器会炸充能节点」
 * 同一致。</p>
 *
 * <p>同样在 {@code RETURN} 上补判：本体已返回 {@code true} 就放行；否则只补验
 * 「正下方是玄枢充能器」，区块加载判据照抄本体（{@code hasChunksAt(±2)}）。</p>
 */
@Mixin(value = AuraNodeBlock.class, remap = false)
public abstract class AuraNodeEnergizedMachineMixin {

    @Inject(method = "hasValidEnergizedStructure", at = @At("RETURN"), cancellable = true)
    private static void mysticpivot$acceptXuanshuCharger(Level level, BlockPos nodePos,
                                                         CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            return;                     // 本体已经判为合法（完整三段装配）：放行
        }
        BlockPos machinePos = nodePos.below();
        if (!level.hasChunksAt(machinePos.offset(-2, 0, -2), machinePos.offset(2, 0, 2))) {
            return;                     // 区块没加载：不认（与本体同判据）
        }
        if (level.getBlockState(machinePos).is(ModBlocks.XUANSHU_CHARGER.get())) {
            cir.setReturnValue(true);   // 正下方是玄枢充能器：单方块装配合法
        }
    }
}
