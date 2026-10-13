package skydeity.mysticpivot;

import dev.tc4port.thaumcraft.api.focus.FocusId;
import dev.tc4port.thaumcraft.api.focus.FocusIdentityItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 本模组的法杖核心物品。实现本体的 {@link FocusIdentityItem}（物品不可堆叠），
 * 即可被 F 键装到法杖上、由本体的安装/渲染/魔耗链路接管；
 * 行为档案由数据包 {@code data/<ns>/thaumcraft/focus_catalog/<id>.json} 提供，
 * 右键动作在 {@link ModFocuses} 里通过 {@code FocusActionApi} 注册。
 */
public class XuanshuFocusItem extends Item implements FocusIdentityItem {

    private final FocusId focusId;

    public XuanshuFocusItem(Properties properties, FocusId focusId) {
        super(properties);
        this.focusId = focusId;
    }

    @Override
    public FocusId focusId(ItemStack stack) {
        return this.focusId;
    }
}
