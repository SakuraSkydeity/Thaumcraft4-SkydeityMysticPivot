package skydeity.mysticpivot.mixin;

import dev.tc4port.thaumcraft.block.entity.ArcanePedestalBlockEntity;
import dev.tc4port.thaumcraft.block.entity.InfusionMatrixBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import skydeity.mysticpivot.TrigramAccel;

/**
 * 坛脚立卦<b>催炉</b>：以中央台座下方那一格为中心的 9×9 单层里立了多少块卦位石，
 * 整炉合成就跑多快（倍率见 {@link TrigramAccel}）。
 *
 * <h2>这一版和上一版的区别（上一版为什么被删）</h2>
 * <p>上一版走的是「改写本体进度字段」的路子：在 {@code serverTick} 开头把 {@code craftCount} /
 * {@code countDelay} 重置成自己算的节拍。结果是合成整炉点不着——那套字段是本体状态机自己的账本，
 * 由 {@code craftCycle()} 按步推进（吸一份要素、吸一件材料、推进一次吸收……每步都在里面改这个账本），
 * 外面横插一杠子把账本撕了重写，状态机当然就走不下去。</p>
 *
 * <p>所以这一版改成<b>只改节拍、不碰账本</b>，而且是「<b>额外补跑一步</b>」而不是「把本体的那一步挪走」：</p>
 * <ol>
 *   <li>本体的节拍一个字不改——{@code count % countDelay == 0} 时它照旧跑它那一步；</li>
 *   <li>我们另外算一个更密的节拍 {@code fast = countDelay / 倍率}，
 *       在 {@code count % fast == 0} 的 tick 上<b>再补跑一次</b> {@code craftCycle()}；</li>
 *   <li>本体已经跑过的那一 tick（{@code count % countDelay == 0}）不补，免得同一 tick 跑两遍。</li>
 * </ol>
 * <p>这样每 {@code fast} tick 推进正好一步，等于把整炉的「等待时间」按倍率压掉，
 * 而<b>总步数一点没变</b>——每步里那次 {@code random.nextInt(500) <= instability} 的不稳判定
 * 也因此<b>不会因为提速而多触发</b>（这是上一版失败的另一半原因：当时步数被放大，
 * 不稳事件跟着成倍出现，炉子在中途就炸了）。</p>
 *
 * <p><b>顺序也要紧</b>：{@code count} 是在 {@code serverTick} 开头自增的，注入点选 {@code TAIL}，
 * 此时 {@code count} 已是本 tick 的最终值，和本体判定用的是同一个数，补跑与本体那一步不会撞车。</p>
 *
 * <p>沿用本项目进入本体内部的窄口约定（见 {@link InfusionMatrixBlockEntityAccessor}）：
 * 目标类是附属 mod 自己的类，运行时不混淆，{@code remap = false} 即可。</p>
 */
@Mixin(value = InfusionMatrixBlockEntity.class, remap = false)
public abstract class InfusionMatrixAccelMixin {

    @Shadow(remap = false)
    private int count;

    @Shadow(remap = false)
    private int countDelay;

    @Shadow(remap = false)
    private ArcanePedestalBlockEntity centralPedestal() {
        throw new AssertionError("shadow");
    }

    @Shadow(remap = false)
    private void craftCycle() {
        throw new AssertionError("shadow");
    }

    @Inject(
            method = "serverTick(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/world/level/block/state/BlockState;"
                    + "Ldev/tc4port/thaumcraft/block/entity/InfusionMatrixBlockEntity;)V",
            at = @At("TAIL"))
    private static void mysticpivot$accelerateCrafting(Level level, BlockPos pos, BlockState state,
                                                       InfusionMatrixBlockEntity self, CallbackInfo ci) {
        // 只在「坛子立起来了、而且这一炉真的在跑」时才做，其余时候一点开销都不付。
        if (!self.active() || !self.crafting()) {
            return;
        }
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        InfusionMatrixAccelMixin matrix = (InfusionMatrixAccelMixin) (Object) self;
        ArcanePedestalBlockEntity central = matrix.centralPedestal();
        if (central == null) {
            return;
        }
        int multiplier = TrigramAccel.multiplierAt(server, central.getBlockPos().below());
        if (multiplier <= 1) {
            return;                             // 坛脚一块石头也没立：原速
        }
        int fast = Math.max(1, matrix.countDelay / multiplier);
        if (fast >= matrix.countDelay) {
            return;                             // 节拍已经压不下去了
        }
        if (matrix.count % fast != 0 || matrix.count % matrix.countDelay == 0) {
            return;                             // 本 tick 不轮到补跑 / 本体已经跑过这一步
        }
        matrix.craftCycle();
        self.setChanged();
    }
}
