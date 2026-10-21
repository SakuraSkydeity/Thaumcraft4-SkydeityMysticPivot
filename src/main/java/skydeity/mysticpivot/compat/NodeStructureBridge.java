package skydeity.mysticpivot.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * 窄口桥：把「本 mod 的方块想知道本体灵气节点结构是否还成立」这一件事，
 * 转交给本体自己的公开静态入口 {@code AuraNodeBlock#checkStructure}。
 *
 * <p>★ 这个类**不能**放进 {@code skydeity.mysticpivot.mixin} 包：Mixin 配置把整个
 * {@code ...mixin.*} 声明为 mixin 包后，该包下的类只允许被 Mixin 框架自己加载，
 * 任何普通代码直接引用都会在运行时抛
 * {@code IllegalClassLoadError: ... is in a defined mixin package ... cannot be referenced directly}。
 * 之前的崩溃就是由此而来（稳定器邻居变动 → 引用 mixin 包内的桥类）。</p>
 *
 * <p>{@code BlockBehaviour#neighborChanged} 是 {@code protected}、跨类无法直接唤起，
 * 而本体的 {@code AuraNodeBlock#checkStructure(Level, BlockPos)} 是 {@code public static}，
 * 所以这里只做一次转交。方法签名只用原版类型，加载本类时不会牵扯本体实现类。</p>
 */
public final class NodeStructureBridge {

    private NodeStructureBridge() {
    }

    /** 复核 {@code nodePos} 处灵气节点的「充能节点」装配，并通知其上的节点换能器。 */
    public static void checkStructure(Level level, BlockPos nodePos) {
        dev.tc4port.thaumcraft.block.AuraNodeBlock.checkStructure(level, nodePos);
    }
}
