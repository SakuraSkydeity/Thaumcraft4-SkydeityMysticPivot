package skydeity.mysticpivot;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组的粒子类型（只此一枚）。
 *
 * <h2>为什么要自建，而不直接用原版 {@code minecraft:enchant}</h2>
 * <p>原版那枚「附魔符文」是 {@code FlyTowardsPositionParticle}，它的 {@code tick()} 是：</p>
 * <pre>
 *   f  = 1 − age / lifetime        // 剩余比例，1 → 0
 *   f1 = 1 − f                     // 已过比例，0 → 1
 *   y  = yStart + yd · f − f1⁴ × 1.2
 * </pre>
 * <p>最后那一项是<b>写死的</b>：粒子在寿命末尾必然下沉 <b>1.2 格</b>，与 {@code yd} 无关。
 * 传 {@code yd = 0} 是「原地生、沉 1.2」；传 {@code yd = 1.2} 则是「高 1.2 处生、沉到 −1.2」，
 * 总位移恒为 1.2。也就是说——<b>用原版附魔粒子，怎么做都做不到「不下落」</b>。
 * 所以台座的卦气环改用本枚粒子：同一套符文贴图，重力为 0，原地不动。</p>
 */
public final class ModParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, MysticPivot.MODID);

    /** 玄枢台座卦气环上的「附魔符文」：零重力、零漂移（见类注释）。 */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> QI_GLYPH =
            PARTICLE_TYPES.register("qi_glyph", () -> new SimpleParticleType(false));

    /**
     * 卦气环「一层」的存活时长（tick）——台座的补描周期与符文的寿命共用这一个数。
     *
     * <p>★ 这两个数<b>必须相等</b>：台座只在上一层全灭之后才补下一层，于是任何时刻整圈
     * <b>只有一层</b>。从前补描周期（4 tick）比寿命（36~59 tick）短十几倍，同一个位置上
     * 叠着十几枚年龄不同的符文，透明度相加随时间起伏，看着就是「一直在刷新、一直在闪」。</p>
     */
    public static final int RING_LIFETIME = 100;

    private ModParticles() {
    }
}
