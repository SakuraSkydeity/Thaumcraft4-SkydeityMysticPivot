package skydeity.mysticpivot;

import dev.tc4port.thaumcraft.api.client.ThaumcraftClientEffectsApi;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 玄枢炼金炉的客户端表现：<b>要素从炉心奔向管子</b>。
 *
 * <p>用的还是本体那条 {@link ThaumcraftClientEffectsApi#spawnEssentiaTrail}
 * （{@code FXEssentiaTrail}，注魔矩阵「从源抽要素」时放的同一条粒子），
 * 只是方向反过来——坩埚那条是「从管奔向锅」，这条是「从炉奔向管」。</p>
 *
 * <p>「源表 + 重复包续期」这套与玄枢坩埚完全一致：服务端每
 * {@link XuanshuAlchemyFurnaceBlockEntity#TRANSFER_INTERVAL} tick 把
 * {@link XuanshuAlchemyFurnaceBlockEntity#trailNonce() 批号} +1，
 * 客户端见到批号变了就把倒计时续到 {@value #TRAIL_TICKS}；末尾 5 tick 按
 * {@code ticks²/25} 收细，与本体那条的收尾曲线一致。</p>
 *
 * <p>★ <b>这条尾巴的长度是有讲究的（第 35 批在坩埚、第 36 批搬到这里）</b>：服务端停送之后
 * 还会再续 <b>10 tick</b>（{@code TRAIL_LINGER}），本表的尾巴再走 {@value #TRAIL_TICKS} tick，
 * 于是从「最后一份要素送出去」到「最后一个粒子消散」正好 <b>20 tick = 1 秒</b>。
 * 原先尾巴写 15 ⇒ 合起来 25 tick，并且服务端那条续期条件恒为真、根本不停（见
 * {@code XuanshuAlchemyFurnaceBlockEntity#TRAIL_LINGER}）。</p>
 */
@OnlyIn(Dist.CLIENT)
final class XuanshuAlchemyEffects {

    /** 轨迹表尾巴（tick）。与服务端的余晖合起来＝从停送到彻底消失 1 秒。 */
    private static final int TRAIL_TICKS = 10;

    private static int seenNonce = -1;
    private static int trailTicks;
    private static int tickCount;

    private XuanshuAlchemyEffects() {
    }

    static void clientTick(Level level, BlockPos pos, XuanshuAlchemyFurnaceBlockEntity be) {
        if (!(level instanceof ClientLevel client)) {
            return;
        }
        if (seenNonce != be.trailNonce()) {
            seenNonce = be.trailNonce();
            trailTicks = TRAIL_TICKS;
        }
        if (trailTicks <= 0) {
            return;
        }
        tickCount++;

        int count = be.trailCount();
        Vec3 origin = new Vec3(pos.getX() + 0.5D, pos.getY() + 0.85D, pos.getZ() + 0.5D);
        float scale = trailTicks > 5 ? 1.0F : trailTicks * trailTicks / 25.0F;
        int phase = trailTicks > 5 ? tickCount : tickCount - (5 - trailTicks);
        for (int i = 0; i < count; i++) {
            ThaumcraftClientEffectsApi.spawnEssentiaTrail(client, origin,
                    Vec3.atCenterOf(be.trailTarget(i)), phase, be.trailColor(i), scale);
        }
        trailTicks--;
    }
}
