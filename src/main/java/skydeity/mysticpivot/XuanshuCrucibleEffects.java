package skydeity.mysticpivot;

import dev.tc4port.thaumcraft.api.client.ThaumcraftClientEffectsApi;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 玄枢坩埚的客户端表现：<b>抽要素</b>与<b>翻泡</b>。
 *
 * <h2>为什么不用自己搭一套动画</h2>
 * <p>本体的公开客户端特效 API 里正好有一条
 * {@link ThaumcraftClientEffectsApi#spawnEssentiaTrail}——它就是注魔矩阵「从源抽要素」时
 * 放的那条粒子（{@code FXEssentiaTrail}：从源出发、被目标一路吸过去、到跟前收细消失）。
 * 这里直接调它，所以玄枢坩埚抽要素的样子与注魔矩阵<b>一模一样</b>，
 * 而不是画了个「像」的东西。翻泡同理，用的是本体坩埚那颗粒子
 * （{@link ThaumcraftClientEffectsApi#spawnBubble}）。</p>
 *
 * <h2>为什么是「源表 + 重复包续期」</h2>
 * <p>这条轨迹靠反复生成同一对（源，目标）来形成连续粒子流，单发只会闪一下。
 * 本体的 {@code EssentiaSourceEffectManager} 就是「收到一次包 → 记若干 tick，
 * 每 tick 生成一发；期间再收到包就续满」。</p>
 *
 * <p>服务端那边<b>在「刚吸到过要素」时</b>每 {@value XuanshuCrucibleBlockEntity#TRAIL_INTERVAL} tick
 * 推一次方块实体数据（{@link XuanshuCrucibleBlockEntity#trailNonce} 是批号），
 * 这里见到批号变了就把倒计时续到 {@value #TRAIL_TICKS}。</p>
 *
 * <p>★ <b>这条尾巴的长度是有讲究的（第 35 批）</b>：服务端停吸后还会再续
 * {@code TRAIL_LINGER}×1 tick（10 tick），本表的尾巴再走 {@value #TRAIL_TICKS} tick（10 tick），
 * 于是从「最后一滴要素吸进锅」到「最后一个粒子消散」正好 <b>20 tick = 1 秒</b>。
 * 末尾 5 tick 按 {@code ticks²/25} 收细——与本体那条的收尾曲线一致。</p>
 */
@OnlyIn(Dist.CLIENT)
final class XuanshuCrucibleEffects {

    /** 轨迹表尾巴（tick）。与服务端的余晖合起来＝从停吸到彻底消失 1 秒。 */
    private static final int TRAIL_TICKS = 10;

    /** 上一次见到的批号。 */
    private static int seenNonce = -1;

    /** 轨迹表剩余。 */
    private static int trailTicks;

    /** 粒子相位计数（给轨迹的抖动做种子，与本体同款：就取当前 tick 数）。 */
    private static int tickCount;

    private XuanshuCrucibleEffects() {
    }

    static void clientTick(Level level, BlockPos pos, XuanshuCrucibleBlockEntity be) {
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
        Vec3 target = new Vec3(pos.getX() + 0.5D, pos.getY() + 0.92D, pos.getZ() + 0.5D);
        float scale = trailTicks > 5 ? 1.0F : trailTicks * trailTicks / 25.0F;
        int phase = trailTicks > 5 ? tickCount : tickCount - (5 - trailTicks);
        for (int i = 0; i < count; i++) {
            // 源取方块中心：源多半是原质管，从管心出发才不像「从墙角冒出来」
            ThaumcraftClientEffectsApi.spawnEssentiaTrail(client, Vec3.atCenterOf(be.trailSource(i)),
                    target, phase, be.trailColor(i), scale);
        }

        // 锅底翻泡：抄本体坩埚的口径——位置在液面、age 恒为 1、上升速度 0.002。
        // ★ age 不是「第几颗泡」：本体里 remainingAge = age + 2 + 8/(rand*0.8+0.2)，
        //   传自增计数会让泡越活越久、最后变成赖着不走的一层膜。本体自己就传常数 1。
        if (client.random.nextInt(4) == 0) {
            int rgb = count > 0 ? be.trailColor(0) : 0x8080B3;
            double ox = (client.random.nextDouble() - 0.5D) * 0.5D;
            double oz = (client.random.nextDouble() - 0.5D) * 0.5D;
            ThaumcraftClientEffectsApi.spawnBubble(client,
                    new Vec3(pos.getX() + 0.5D + ox, pos.getY() + 0.22D, pos.getZ() + 0.5D + oz),
                    1, rgb, 0.002D);
        }

        trailTicks--;
    }
}
