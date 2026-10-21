package skydeity.mysticpivot;

import dev.tc4port.thaumcraft.api.research.ResearchApi;
import dev.tc4port.thaumcraft.api.research.ResearchCompletionContext;
import dev.tc4port.thaumcraft.api.research.ResearchCompletionReason;
import dev.tc4port.thaumcraft.api.research.ResearchCompletionResult;
import dev.tc4port.thaumcraft.api.research.ResearchKey;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 授业卷轴：右键一次性<b>补全全部研究笔记</b>（把本体研究目录里还没做完的条目全部标记为已完成）。
 *
 * <h2>与阿卡西记录的分工</h2>
 * <ul>
 *   <li>{@link AkashicRecordsItem}（阿卡西记录）：给<b>要素研究点</b>——每门 1000 点，
 *       解决「有知识可点、但点数不够」；</li>
 *   <li>本物（授业卷轴）：直接<b>解锁条目</b>——把研究目录里所有未完成的条目一次做完，
 *       解决「点数够了还得一条条手点」。</li>
 * </ul>
 *
 * <h2>为什么用 {@code ADMIN_COMMAND} 而不是 {@code GAMEPLAY_REWARD}</h2>
 * 本体的 {@link ResearchApi#complete} 只在 {@code ADMIN_COMMAND} 这一档<b>绕过前置条件</b>
 * （见本体 {@code ResearchApi#complete} 里那句
 * {@code context.reason() != ADMIN_COMMAND && !entry.requirementsMet(knowledge)} 的短路）。
 * 本物的语义就是「一次补全所有」，跳前置是必须的；顺带这一档走的是
 * {@code completeResearchSilently}，<b>不会每完成一条弹一次完成提示</b>——
 * 几十上百条一起做完时，那才是真的没法看。
 *
 * <p><b>代价</b>：每条本体研究都照规矩结算一次「禁忌」值（{@code entry.warp()}），
 * 所以一口气补完会把该有的禁忌值一次加满。这是本体的规矩，本物不越权改写。</p>
 */
public class TeachingScrollItem extends Item {

    /** 补全来源标识（写进 {@link ResearchCompletionContext}，本体用它做来源区分）。 */
    public static final ResourceLocation SOURCE =
            ResourceLocation.fromNamespaceAndPath(MysticPivot.MODID, "teaching_scroll");

    public TeachingScrollItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer server)) {
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // 先收集「还没完成」的那些：一条都没有就别浪费玩家一张卷轴。
        List<ResearchKey> pending = new ArrayList<>();
        for (ResearchKey key : ResearchApi.registry().entries().keySet()) {
            if (!ResearchApi.isComplete(server, key)) {
                pending.add(key);
            }
        }
        if (pending.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("message.skydeitymysticpivot.teaching_scroll.none"), false);
            return InteractionResultHolder.pass(stack);
        }

        ResearchCompletionContext context =
                new ResearchCompletionContext(SOURCE, ResearchCompletionReason.ADMIN_COMMAND);
        int completed = 0;
        for (ResearchKey key : pending) {
            if (ResearchApi.complete(server, key, context) == ResearchCompletionResult.COMPLETED) {
                completed++;
            }
        }

        player.displayClientMessage(
                Component.translatable("message.skydeitymysticpivot.teaching_scroll.done", completed), false);
        level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.PLAYERS, 0.9F, 1.15F);
        if (!server.isCreative()) {
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, false);
    }
}
