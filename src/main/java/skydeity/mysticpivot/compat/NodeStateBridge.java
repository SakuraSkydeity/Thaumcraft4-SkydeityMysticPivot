package skydeity.mysticpivot.compat;

import dev.tc4port.thaumcraft.api.node.AuraNodeState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * 窄口桥：整体覆写本体灵气节点方块实体的状态（type / modifier / baseVis / currentVis / instanceId）。
 *
 * <p><b>为什么是这个形态：</b>「把普通灵气节点换成充能灵气节点」之后，新方块实体的状态是空的，
 * 必须写回一份完整状态；而本体没有公开 API 能写充能节点——
 * {@code NodeApi#replaceLoadedState} 的文档明文只认 {@code aura_node} 与 {@code node_jar}
 * （ energized nodes ... are not [mutable owners] ）。本体自己的
 * {@code NodeTransducerBlockEntity#energizeNode} 也是换完方块直接调私有的
 * {@code setNodeState}——附属拿不到那个私有方法，但 {@code setNodeState(AuraNodeState)}
 * 本身是 <b>public</b>，所以直接调即可。</p>
 *
 * <p><b>为什么不用 Mixin：</b>曾试过接口混入（{@code @Mixin} 一个 interface 贴到
 * {@code AuraNodeBlockEntity} 上把方法透出来），启动即 FATAL——
 * <b>接口混入要求目标类也必须是接口</b>（{@code InvalidMixinException: target type mismatch}，
 * 见 §12.69.6）。类混入又没法在普通代码里 cast 过去。既然方法本来就是 public，
 * 一纸桥类直接调最直白，还顺带少一个 Mixin（本模组 Mixin 从 10 个降到 9 个）。</p>
 *
 * <p>与 {@link NodeStructureBridge} 同一路数：普通类放在 {@code compat} 包（**不能**放
 * {@code ...mixin} 包，那整个包被声明为 mixin 包后普通代码引用会抛
 * {@code IllegalClassLoadError}）；方法签名只用原版 / api 类型，方法体才出现本体实现类
 * 的全限定名，加载本类不牵扯本体。只在充能 / 掉电那一瞬调用，绝不每 tick 写。</p>
 */
public final class NodeStateBridge {

    private NodeStateBridge() {
    }

    /**
     * 覆写 {@code pos} 处本体灵气节点方块实体的状态。
     *
     * @return true = 写成功（位置上确实是本体的灵气节点方块实体）；false = 位置不对，调用方应放弃
     */
    public static boolean writeNodeState(Level level, BlockPos pos, AuraNodeState state) {
        if (level.getBlockEntity(pos)
                instanceof dev.tc4port.thaumcraft.block.entity.AuraNodeBlockEntity node) {
            node.setNodeState(state);
            return true;
        }
        return false;
    }
}
