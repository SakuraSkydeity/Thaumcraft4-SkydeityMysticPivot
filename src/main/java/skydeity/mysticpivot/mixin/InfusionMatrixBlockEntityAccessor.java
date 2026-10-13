package skydeity.mysticpivot.mixin;

import dev.tc4port.thaumcraft.block.entity.InfusionMatrixBlockEntity;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import javax.annotation.Nullable;

/**
 * 唯一一个进入本体内部的窄口（见 README 12.12 的取舍说明）：
 * 玄枢魔晶需要把附近注魔矩阵的不稳定度清零，而本体没有提供任何公开 API 能写入它。
 * 目标类是附属 mod 自己的类（运行时不混淆、字段名固定），因此 {@code remap = false}
 * 即可在开发与生产环境同样工作，无需 refmap。
 *
 * <p>{@code recipeId} 是「这一炉正在注的是什么」的<b>唯一权威</b>：
 * 玄枢门禁（{@link InfusionMatrixCraftGateMixin}）要按配方而不是按中心物来判，
 * 因为玄枢杖柄那条配方的中心是银树杖柄、杖端那条才是龙蛋，靠中心物分辨既啰嗦又易错。
 * 该字段在 {@code startCrafting} 里于 {@code crafting = true} <b>之前</b> 就已写入。</p>
 */
@Mixin(value = InfusionMatrixBlockEntity.class, remap = false)
public interface InfusionMatrixBlockEntityAccessor {

    @Accessor(value = "instability", remap = false)
    int getInstability();

    @Accessor(value = "instability", remap = false)
    void setInstability(int value);

    @Nullable
    @Accessor(value = "recipeId", remap = false)
    ResourceLocation mysticpivot$getRecipeId();
}

