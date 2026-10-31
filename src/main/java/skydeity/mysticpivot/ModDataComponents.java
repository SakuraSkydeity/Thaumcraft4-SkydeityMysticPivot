package skydeity.mysticpivot;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 物品数据组件注册（第一百九十四批新增：只为玄枢源质罐开场）。
 *
 * <h2>为什么罐子需要自己的组件</h2>
 * <p>第一版的思路是「挖掉时手写 {@code playerDestroy} + {@code saveToItem}」，把整个方块实体
 * 塞进 {@code BLOCK_ENTITY_DATA}。真机上没成：那条路只在<b>生存模式正常挖掉</b>时走，
 * 创造模式破坏（{@code ServerPlayerGameMode#destroyBlock} 里 {@code isCreative()} 直接
 * {@code return true}，<b>根本不调</b> {@code playerDestroy}）一只都不掉；
 * 而且它绕开了战利品表，别的模组（假人、机械手、区块清理器）拆罐子时也一样丢内容。</p>
 *
 * <p>现在改成和本体防护罐<b>完全同一套</b>：内容是一个真正的物品组件，方块实体在
 * {@code collectImplicitComponents} 里把它写出去，战利品表用 {@code minecraft:copy_components}
 * 从方块实体上复制回物品。掉落的每一条路径（手挖 / 创造 / 爆炸 / 别的模组拆）都走同一张表，
 * 行为自然一致。</p>
 *
 * <h2>格式</h2>
 * <p>一个平铺的 {@link CompoundTag}：{@code 要素 id → 份数}，例如
 * {@code {"thaumcraft:ignis": 128, "thaumcraft:aqua": 64}}。用平铺而不是列表，
 * 是因为 Jade、指令、资源包作者手改起来都最省事；空的罐子<b>不写</b>这个组件，
 * 于是空罐之间照旧能叠成一摞。</p>
 */
public final class ModDataComponents {

    /** 数据组件注册表。 */
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MysticPivot.MODID);

    /**
     * 玄枢源质罐里的要素表：{@code 要素 id → 份数}。
     *
     * <p>只存 {@code > 0} 的项；空罐不挂这个组件（这样空罐才能叠）。
     * 同时登记了持久化（存档 / 物品 NBT）与网络同步（背包里点到就能读）两条编解码器。</p>
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> JAR_ESSENTIA =
            COMPONENTS.register("jar_essentia", () -> DataComponentType.<CompoundTag>builder()
                    .persistent(CompoundTag.CODEC)
                    .networkSynchronized(ByteBufCodecs.COMPOUND_TAG)
                    .build());

    private ModDataComponents() {
    }
}
