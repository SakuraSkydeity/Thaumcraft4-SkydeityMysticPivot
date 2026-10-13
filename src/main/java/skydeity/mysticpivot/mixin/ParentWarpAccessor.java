package skydeity.mysticpivot.mixin;

import dev.tc4port.thaumcraft.common.PlayerWarp;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 本体<b>永久扭曲没有任何公开写入口</b>的补丁口。
 *
 * <p>背景（读本体源码确认）：</p>
 * <ul>
 *   <li>{@code ThaumcraftApiHelper.addWarpToPlayer(player, amount, temporary)} /
 *       {@code addStickyWarpToPlayer} 是公开 API，但它们最终落到
 *       {@code PlayerWarp.addTemporaryWarp / addStickyWarp}，而
 *       <b>临时与粘性</b>那一对只挡住 {@code amount == 0} 与「已经是 0 还要再减」，
 *       传负数（-n）确实能把数值压回去。</li>
 *   <li><b>永久扭曲不行</b>：{@code PlayerWarp.addPermanentWarp} 第一行就是
 *       {@code if (player.level().isClientSide || amount <= 0) return;}——负数被直接丢掉。
 *       本体自己那条管理命令 {@code /thaumcraft warp <player> set <n> perm} 走的是
 *       {@code PlayerKnowledgeService.setPermanentWarp}，那才是唯一能「直接写成 0」的口子，
 *       但它不在 api 包里，附属按纪律不能 import。</li>
 * </ul>
 *
 * <p>所以这里沿用本项目<b>进入本体内部的同一条窄口约定</b>（前一条见
 * {@link InfusionMatrixBlockEntityAccessor}）：一个只管转发、不做任何注入逻辑的
 * <b>访问器（accessor）接口 Mixin</b>。目标类是本体的未混淆类，{@code remap = false} 即可。</p>
 *
 * <p>★ 这两个方法在运行期由 Mixin 生成方法体转发到目标静态方法，
 * 调用方写 {@code ParentWarpAccessor.mysticpivot$setPermanentWarp(player, 0)}
 * （静态访问器不需要加命名空间前缀，见 Mixin 约定）。</p>
 */
@Mixin(value = PlayerWarp.class, remap = false)
public interface ParentWarpAccessor {

    /** 直写永久扭曲（0 即彻底抹去）。 */
    @Invoker("setPermanentWarp")
    static void mysticpivot$setPermanentWarp(Player player, int amount) {
        throw new AssertionError("mixin invoker");
    }

    /** 直写粘性扭曲。 */
    @Invoker("setStickyWarp")
    static void mysticpivot$setStickyWarp(Player player, int amount) {
        throw new AssertionError("mixin invoker");
    }

    /** 直写临时扭曲。 */
    @Invoker("setTemporaryWarp")
    static void mysticpivot$setTemporaryWarp(Player player, int amount) {
        throw new AssertionError("mixin invoker");
    }
}
