package skydeity.mysticpivot;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 星象仪（第一百八十六批新增；第一百八十七批「去掉底下的须弥座、改成细座」；
 * 第一百八十九批「砍掉柱身、整座缩进一格」）。
 *
 * <h2>来历</h2>
 * <p>虚空采矿机在第一百八十二~一百八十五批被做成一颗「悬在白瓷台座上的鎏金八卦球」，
 * 用户看完说：这套成型模型单拎出来做一个新方块，叫<b>星象仪</b>；矿机那边整个重做。
 * 于是模型整体搬到本方块名下（`tools/gen_astrolabe_obj.py` 生成 `astrolabe.obj`）。</p>
 *
 * <p>第一百八十七批：「最下面那层底座不需要，只需要很细就可以」—— 须弥座撤成
 * 一片薄底盘 + 一根细柱。第一百八十九批：用户在截图上框出「鎏金底盘以下」那一整段，
 * 说「框起来的部分不要，然后就一个方块的大小」—— 于是<b>细柱、喇叭口、鎏金底盘连同压沿
 * 全部删掉</b>，银白八角主台成了整座的新底面；剩下的「主台 + 托座 + 球 + 相轮刹」
 * 整体缩到 <b>y = 0 ~ 1 格</b>（原来 3.34 格），一颗球连座正好塞进一格。</p>
 *
 * <h2>现在是什么</h2>
 * <p><b>纯装饰</b>：不放光、不产线、不开界面、没有方块实体。「先注册其他功能再说」——
 * 形状与注册先落地，将来要做星象台一类的功能再从本类往下写。</p>
 *
 * <h2>碰撞箱</h2>
 * <p>模型收进一格之后，碰撞箱也回到一格之内，分两段拼：</p>
 * <ul>
 *   <li><b>底座</b>：0 ~ 4.02px 高、6.28~9.72 见方（八角主台的边心距 1.72px）；</li>
 *   <li><b>球</b>：4.02 ~ 15.14px 高、2.44~13.56 见方（球半径 5.56px）。</li>
 * </ul>
 * <p>不按整格算：球只占 0.69 格宽（13.56-2.44），按整格挡会把旁边那圈空气也划成禁区，
 * 贴着它走会被一堵看不见的墙拦住。</p>
 */
public class AstrolabeBlock extends Block {

    /** 底座碰撞：八角主台的边心距 1.72px → 6.28 ~ 9.72，一直顶到球底 4.02。 */
    private static final VoxelShape BASE_SHAPE = Block.box(6.28, 0.0, 6.28, 9.72, 4.02, 9.72);

    /** 球碰撞：半径 5.56px，球底 4.02、球顶 15.14。 */
    private static final VoxelShape ORB_SHAPE = Block.box(2.44, 4.02, 2.44, 13.56, 15.14, 13.56);

    private static final VoxelShape SHAPE = Shapes.or(BASE_SHAPE, ORB_SHAPE);

    public AstrolabeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos,
                                  CollisionContext context) {
        return SHAPE;
    }

    /** 不是整格方块：不让相邻面被剔除，也别把光照挡出个方格影子。 */
    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
