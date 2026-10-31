package skydeity.mysticpivot;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

/**
 * 玄灵三材的出处（第二百一十二批）。
 *
 * <p>用户口径：「给兔子脚：添加杀死兔子 40% 概率掉落；骨头添加：击杀凋零骷髅 40% 概率掉落；
 * 翼膜添加：击杀幻翼 80% 概率掉落」——三件材料各自绑一种怪。</p>
 *
 * <h2>为什么挂 {@link LivingDropsEvent} 而不是写战利品表</h2>
 * <p>这三件是模组材料，不与原版掉落冲突（原版兔子自己那只兔脚、凋零骷髅的骨头、
 * 幻翼的膜都照掉），所以「<b>追加</b>一份」正是 {@code LivingDropsEvent} 的语义：
 * 它拿到的就是最终那份 {@code ItemEntity} 列表，往里塞一件即可，不用碰原版表、
 * 也不会因为别的模组改了战利品表就失效。</p>
 *
 * <h2>口径细节</h2>
 * <ul>
 *   <li><b>只认玩家击杀</b>：与「杀死兔子」这句话对齐，也避免刷怪塔 / 自动农场
 *       用岩浆、摔落、铁傀儡把材料刷出来；玩家用弓箭、法杖打的都算（{@code getEntity()} 仍是玩家）。</li>
 *   <li><b>概率是固定值</b>：不吃抢夺加成（用户给的是确定数字，不是「基础值」）。</li>
 *   <li><b>每次一件</b>：不做 0~N 的浮动。</li>
 * </ul>
 *
 * <p>概率写死在本类的 {@link #RABBIT_FOOT} / {@link #DEMON_BONE} / {@link #WING_MEMBRANE} 三个常量里，
 * 中英文 lore 与之一一对应（改数记得同步 {@code lang/*.json} 那三行）。</p>
 */
@EventBusSubscriber(modid = MysticPivot.MODID)
public final class SpiritRelicDrops {

    /** 击杀兔子掉玄灵兔子脚的概率。 */
    public static final float RABBIT_FOOT = 0.40F;
    /** 击杀凋零骷髅掉神浸魔骨的概率。 */
    public static final float DEMON_BONE = 0.40F;
    /** 击杀幻翼掉翎月翼膜的概率。 */
    public static final float WING_MEMBRANE = 0.80F;

    private SpiritRelicDrops() {
    }

    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.isCanceled() || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        // 只认玩家本人（近战 / 弓箭 / 法杖都算）；环境伤害、别的生物打死的一律不补
        if (!(event.getSource().getEntity() instanceof Player)) {
            return;
        }
        LivingEntity died = event.getEntity();
        ItemStack drop = dropFor(died);
        if (drop.isEmpty() || level.getRandom().nextFloat() >= chanceFor(died)) {
            return;
        }
        event.getDrops().add(new ItemEntity(level,
                died.getX(), died.getY() + 0.5D, died.getZ(), drop));
    }

    /** 这只怪掉哪一件（不是这三种怪就返回空）。 */
    private static ItemStack dropFor(LivingEntity died) {
        if (died instanceof Rabbit) {
            return new ItemStack(ModItems.XUANLING_RABBIT_FOOT.get());
        }
        if (died instanceof WitherSkeleton) {
            return new ItemStack(ModItems.SHENJIN_DEMON_BONE.get());
        }
        if (died instanceof Phantom) {
            return new ItemStack(ModItems.LINGYUE_WING_MEMBRANE.get());
        }
        return ItemStack.EMPTY;
    }

    /** 对应的概率。 */
    private static float chanceFor(LivingEntity died) {
        if (died instanceof Rabbit) {
            return RABBIT_FOOT;
        }
        if (died instanceof WitherSkeleton) {
            return DEMON_BONE;
        }
        if (died instanceof Phantom) {
            return WING_MEMBRANE;
        }
        return 0.0F;
    }
}
