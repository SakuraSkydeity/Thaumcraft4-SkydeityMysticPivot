package skydeity.mysticpivot;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * 玄枢台座卦气环上的一枚附魔符文：<b>零重力、零漂移、原地悬着明灭</b>。
 *
 * <p>贴图直接借用原版附魔台那 26 张符文（{@code minecraft:sga_a} … {@code sga_z}），
 * 所以看着就是「附魔粒子」；但运动学是我们自己的——{@code tick()} 里除了年龄什么都不改，
 * 这也是原版 {@code minecraft:enchant} 做不到的一点（它寿命末必沉 1.2 格，
 * 见 {@link ModParticles} 的类注释）。</p>
 *
 * <p><b>字形为什么借 {@code zd} 传进来</b>：{@code Level#addParticle} 只能带 6 个 double，
 * 没有额外的自定义参数位。这里把 {@code zd} 当作「第几号符文」用（粒子本身不需要任何速度），
 * 于是环上<b>每一个固定的位置永远显同一个字</b>——整圈不会随时间乱闪。</p>
 *
 * <p>淡入淡出只留首尾各约 1/8 段：中段透明度恒为 1，看起来就是一枚静静悬着的符文，
 * 而不是一段动画。</p>
 *
 * <p>★ <b>寿命是固定值</b>（{@link ModParticles#RING_LIFETIME}），不再逐枚随机：整圈
 * 同生同灭，才谈得上「只维持一层」。随机寿命会让整圈一颗一颗地灭掉再补回来，
 * 那是另一种闪。</p>
 */
public class QiGlyphParticle extends TextureSheetParticle {

    /** 原版附魔符文贴图共 26 张。 */
    public static final int GLYPH_COUNT = 26;

    private QiGlyphParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites, int glyph) {
        super(level, x, y, z);
        this.hasPhysics = false;
        this.gravity = 0.0F;
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.lifetime = ModParticles.RING_LIFETIME;
        this.quadSize = 0.10F + this.random.nextFloat() * 0.04F;
        this.setSprite(sprites.get(glyph, GLYPH_COUNT - 1));
        float tint = 0.85F + this.random.nextFloat() * 0.15F;
        this.setColor(tint, tint, tint);
    }

    /**
     * 原地不动：坐标一个像素都不挪，只推进年龄与透明度。
     *
     * <p>这里<b>不能</b>走 {@code super.tick()}——{@code Particle#tick} 会给 {@code yd}
     * 叠上重力、再 {@code move(...)} 一次。整段生命周期只做「变老 + 明灭」。</p>
     */
    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        float life = (float) this.age / (float) this.lifetime;
        this.alpha = Math.min(1.0F, Math.min(life * 8.0F, (1.0F - life) * 8.0F));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** 工厂：见类注释里「字形借 {@code zd} 传进来」那一段。 */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public QiGlyphParticle createParticle(SimpleParticleType type, ClientLevel level,
                                              double x, double y, double z,
                                              double xd, double yd, double zd) {
            int glyph = (int) Math.floorMod((long) zd, GLYPH_COUNT);
            return new QiGlyphParticle(level, x, y, z, this.sprites, glyph);
        }
    }
}
