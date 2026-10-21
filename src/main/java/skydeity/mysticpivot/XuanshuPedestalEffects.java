package skydeity.mysticpivot;

import dev.tc4port.thaumcraft.api.client.ThaumcraftClientEffectsApi;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.HashMap;
import java.util.Map;

/**
 * 玄枢台座的客户端表现：<b>吸取要素的丝线</b>。
 *
 * <h2>与注魔矩阵、玄枢坩埚同一套</h2>
 * <p>本体的公开客户端特效 API 里有一条
 * {@link ThaumcraftClientEffectsApi#spawnEssentiaTrail}——就是注魔矩阵「从源抽要素」时
 * 放的那条粒子（{@code FXEssentiaTrail}：从源出发、被目标一路吸过去、到跟前收细消失）。
 * 玄枢坩埚抽要素用的就是它；台座这里照用同一条，所以吸取的「方法」与「特效」
 * 都和注魔祭坛一模一样。</p>
 *
 * <h2>源表 + 重复包续期</h2>
 * <p>这条轨迹靠反复生成同一对（源，目标）来形成连续粒子流，单发只会闪一下。
 * 服务端在「刚吸到过要素」时每 {@value XuanshuPedestalBlockEntity#TRAIL_INTERVAL} tick
 * 推一次方块实体数据（{@link XuanshuPedestalBlockEntity#trailNonce} 是批号），
 * 这里见到批号变了就把倒计时续到 {@value #TRAIL_TICKS}——与玄枢坩埚
 * （{@link XuanshuCrucibleEffects}）是同一套机制：服务端余晖与客户端尾巴合起来，
 * 从「最后一滴要素吸进台座」到「最后一个粒子消散」约一秒。</p>
 *
 * <p>★ 第 84 批修正：服务端那份续期原先排在 {@code serverTick} 的多个
 * {@code return} 之后——正在吸要素的周期（缓冲区未满、提前返回）推不出批号，
 * 客户端一条丝线也见不到（「像没在吸」的根源）。如今续期挪到最前、每 tick 都跑；
 * 配合每周期只吸 2 份的慢吸，一件卦石的整段吸纳约两秒半，是一条清清楚楚的粒子流。</p>
 *
 * <h2>★ 为什么按键分开记账，不用静态单例</h2>
 * <p>坩埚那份用的是「全局一个批号」——同场两台机器时，A 的批号变化会替 B 续期，
 * B 的静止批号也会替 A 续期（还会让 B 放出一条它根本没在吸的丝线）。台座这边
 * 按方块位置各记一份 {@code [seenNonce, trailTicks, tickCount]}，互不串门；
 * 单台运行时与坩埚逐 tick 等价。</p>
 */
@OnlyIn(Dist.CLIENT)
final class XuanshuPedestalEffects {

    /** 轨迹尾巴（tick）：与服务端的余晖合起来＝从停吸到彻底消失 1 秒。 */
    private static final int TRAIL_TICKS = 10;

    /** 每座台座一份续期状态：{@code [seenNonce, trailTicks, tickCount]}。 */
    private static final Map<BlockPos, int[]> STATES = new HashMap<>();

    private XuanshuPedestalEffects() {
    }

    static void clientTick(Level level, BlockPos pos, XuanshuPedestalBlockEntity be) {
        if (!(level instanceof ClientLevel client)) {
            return;
        }
        int[] state = STATES.computeIfAbsent(pos.immutable(), ignored -> new int[]{-1, 0, 0});
        if (state[0] != be.trailNonce()) {
            state[0] = be.trailNonce();
            state[1] = TRAIL_TICKS;
        }
        if (state[1] <= 0) {
            return;
        }
        state[2]++;

        int count = be.trailCount();
        // 终点取台口上方：丝线被"吸进台座"而不是撞在台面物品上
        Vec3 target = new Vec3(pos.getX() + 0.5D, pos.getY() + 1.1D, pos.getZ() + 0.5D);
        // 末 5 tick 按 t²/25 收细——与本体那条的收尾曲线一致（坩埚同款）
        float scale = state[1] > 5 ? 1.0F : state[1] * state[1] / 25.0F;
        int phase = state[1] > 5 ? state[2] : state[2] - (5 - state[1]);
        for (int i = 0; i < count; i++) {
            // 源取方块中心：罐/池从方块中心出发才不像「从墙角冒出来」
            ThaumcraftClientEffectsApi.spawnEssentiaTrail(client,
                    Vec3.atCenterOf(be.trailSource(i)), target, phase, be.trailColor(i), scale);
        }
        state[1]--;
    }
}
