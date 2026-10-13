package skydeity.mysticpivot.mixin;

import dev.tc4port.thaumcraft.block.entity.ArcanePedestalBlockEntity;
import dev.tc4port.thaumcraft.block.entity.InfusionMatrixBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import skydeity.mysticpivot.Fengshui;
import skydeity.mysticpivot.TrigramGate;

import java.util.Set;

/**
 * 卦位石的注魔感应：某些注魔配方<b>必须先在坛边立卦</b>才能起炉——
 * 「哪条配方要哪几卦、要几块、多大范围、多出来的石头压多少不稳」全部由
 * {@link TrigramGate} 从数据包读进来（表见
 * {@code data/skydeitymysticpivot/mysticpivot/trigram_gate/bagua.json}），
 * 本类只负责在配料那一下把表查出来、数石头、决定放行还是掐掉。
 *
 * <p>掐掉的那一下会给玩家一条<b>动作栏</b>提示（快捷栏正上方），缺哪几卦就点哪几卦的名——
 * 门禁最怕玩家不知道自己被什么拦着：光冒烟不出声，就只剩谜题了。</p>
 *
 * <p><b>判据是「配方 id」，不是「中心物品」</b>：玄枢杖柄那条配方的中心是银树杖柄、
 * 杖端那条才是龙蛋，靠「中心是不是龙蛋」来认门，杖柄那条就整个漏掉了。所以这里读本体自己的
 * {@link net.minecraft.resources.ResourceLocation} 字段 {@code recipeId}
 * （见 {@link InfusionMatrixBlockEntityAccessor}）——那才是「这一炉到底在做什么」的唯一权威。</p>
 *
 * <p><b>实现要点（踩过的坑，勿改回去）：</b></p>
 * <ul>
 *   <li>本体方法签名是 {@code startCrafting(Player)}，回调的第一个参数<b>必须写 {@code Player}</b>；
 *       写成 {@code ServerPlayer}（哪怕方法体内用得到 ServerLevel）会被 Mixin 判为
 *       {@code Invalid descriptor}，在 mod 加载期直接 FATAL，并连带把 thaumcraft 的整个注册事件
 *       拖崩（表现为 {@code unbound value: thaumcraft:runic_matrix / nitor} 的 NPE —— 那是注册表
 *       回滚的连锁，不是真因）。</li>
 *   <li>注入点用 {@code @At("RETURN")}：方法里 {@code return} 分支很多、中段还夹着 lambda，
 *       用 {@code INVOKE_ASSIGN} 定位 {@code centralPedestal()} 在 Mixin 0.8 已不再支持。
 *       改为在 RETURN 处看返回值：{@code true} 才说明这次真的把 {@code crafting} 打开了。</li>
 *   <li>{@code centralPedestal()} / {@code changedAndSync()} 在本体里都是 <b>private</b>，
 *       影子成员要用「有方法体的 stub」形式声明（abstract + private 不是合法 Java）。</li>
 *   <li>★ <b>判定中心取「中央台座正下方那一格」</b>——台座在 {@code worldPosition.below(2)}
 *       （本体 {@code centralPedestal()} 的定义），故中心是 {@code below(3)}。
 *       <b>不是</b>矩阵那一格：矩阵在台座上方两格，拿矩阵当中心会把 5×5 抬到半空中，
 *       玩家在坛边怎么摆都验不上。取「下方那一层」也是刻意的：这一层是坛底地面，
 *       石头要实打实地砌进坛脚，而不是随手丢在台座边上。</li>
 *   <li>★★ 加新门禁<b>不要往这里加常量</b>：改
 *       {@code data/.../mysticpivot/trigram_gate/*.json} 即可，表里没有的配方一律不受约束。</li>
 * </ul>
 *
 * <p>沿用本项目进入本体内部的唯一窄口约定（见 {@link InfusionMatrixBlockEntityAccessor}）：
 * 目标类是附属 mod 自己的类，运行时不混淆，{@code remap = false} 即可。</p>
 */
@Mixin(value = InfusionMatrixBlockEntity.class, remap = false)
public abstract class InfusionMatrixCraftGateMixin {

    @Shadow(remap = false)
    private boolean crafting;

    @Shadow(remap = false)
    private int instability;

    @Shadow(remap = false)
    private ArcanePedestalBlockEntity centralPedestal() {
        throw new AssertionError("shadow");
    }

    @Shadow(remap = false)
    private void changedAndSync() {
        throw new AssertionError("shadow");
    }

    /**
     * 立卦门禁 + 多石镇压。放在 RETURN：只有返回 {@code true}（这次真的开始了合成）时才处理，
     * 其余「条件不足直接 return false」的分支一律放过。
     */
    @Inject(method = "startCrafting(Lnet/minecraft/world/entity/player/Player;)Z",
            at = @At("RETURN"), cancellable = true)
    private void mysticpivot$gateTrigramCrafting(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        InfusionMatrixBlockEntity self = (InfusionMatrixBlockEntity) (Object) this;
        Level level = self.getLevel();
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        // ★ 必须先转成 Object 再转接口：本体类是 final，Java 不允许 final 类直接 cast 到
        //   无关接口（mixin 的接口合并发生在运行期，编译期编译器并不知道）。
        ResourceLocation recipeId =
                ((InfusionMatrixBlockEntityAccessor) (Object) self).mysticpivot$getRecipeId();
        TrigramGate.Requirement requirement = TrigramGate.forRecipe(recipeId);
        if (requirement == null) {
            return;                     // 这条配方没有门禁要求（表里没写）
        }
        ArcanePedestalBlockEntity central = this.centralPedestal();
        if (central == null) {
            return;
        }
        BlockPos pos = self.getBlockPos();
        BlockPos foot = central.getBlockPos().below();
        int stones = TrigramGate.count(server, foot, requirement);
        Set<Fengshui.Trigram> kinds =
                Fengshui.trigramsAt(server, foot, requirement.radius(), requirement.height());

        if (!TrigramGate.satisfied(requirement, stones, kinds)) {
            // 坛边没立卦：撤销这次启动（配方字段留着也会因 crafting=false 而不被消费）
            this.crafting = false;
            cir.setReturnValue(false);
            this.changedAndSync();
            // ★ 提示走动作栏（快捷栏正上方那一行），不是聊天栏：这条信息是「这一次点下去为什么没成」，
            //   和方块交互的即时反馈同类，聊天栏刷屏反而看不见。
            player.displayClientMessage(
                    TrigramGate.missingMessage(TrigramGate.missing(requirement, kinds)), true);
            server.sendParticles(ParticleTypes.SMOKE,
                    pos.getX() + 0.5D, pos.getY() + 1.0D, pos.getZ() + 0.5D,
                    12, 0.3D, 0.3D, 0.3D, 0.01D);
            server.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 0.8F);
            return;
        }

        // 多立可镇压：每多一块压一次，至多按 reliefMax 块算
        int relief = TrigramGate.reliefOf(stones, requirement);
        if (relief > 0) {
            this.instability = Math.max(0, this.instability - relief);
            this.changedAndSync();
        }
    }
}
