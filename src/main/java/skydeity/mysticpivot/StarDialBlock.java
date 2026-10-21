package skydeity.mysticpivot;

import dev.tc4port.thaumcraft.api.wand.WandApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 南北斗枢玄星盘（第一百零六批新增、第一百一十批改成**真·台阶**、第一百一十七批改成**十二星位**）。
 *
 * <h2>一方块，多种身份</h2>
 * <ul>
 *   <li>单独放置：**阵眼**盘面（{@code role=none}），亮度 7；</li>
 *   <li>摆成 3×4、用法杖激活：十二格各自记「第几行第几列」，顶面换成整阵星图里自己那一块，
 *       <b>每一格同时是一颗星的星位</b>（见 {@link #starAt}）。</li>
 * </ul>
 *
 * <h2>阵型（从上往下看，三列四行，共 12 格）</h2>
 * <pre>
 *        西(-1)   中(0)   东(+1)
 * 北(-1)   r0c0    r0c1    r0c2     ← 北斗：天枢 天璇 天玑
 *     (0)  r1c0    r1c1    r1c2     ← 北斗：天权 玉衡 开阳
 *     (+1) r2c0    r2c1    r2c2     ← 南斗：天府 天梁 天机
 * 南(+2)   r3c0    r3c1    r3c2     ← 南斗：天同 天相 七杀
 * </pre>
 * <p>方位固定：北 = -Z，西 = -X。阵心（{@code r1c1}）是十二格坐标的锚点。
 * 北斗第七星「摇光」没有星位：北斗六星全亮时自动现（{@link Constellation}）。</p>
 *
 * <h2>交互（第一百一十七批）</h2>
 * <ul>
 *   <li><b>法杖</b>：成型；潜行 + 法杖 = 收阵；</li>
 *   <li><b>持物右键任意一格</b>：把东西供到那颗星上（一格一件，放下即「这顆星亮了」）；</li>
 *   <li><b>空手右键</b>：取回；</li>
 *   <li><b>潜行 + 空手右键</b>：整盘翻面（七星落盘 ⇄ 六星落盘），其余十一格跟着换。</li>
 * </ul>
 * <p>物品浮在格心、头上显示星名（{@link StarDialRenderer}）。外圈 5×6 的工作方块
 * 通过 {@link StarWork} 读这座阵的星象去做特殊合成（见 {@link StarForge}）。</p>
 *
 * <h2>破一格 = 整座失效</h2>
 * <p>拆掉任意一格，其余十一格一并退回阵眼，十二格里的物品全部掉出来。</p>
 */
public class StarDialBlock extends SlabBlock implements EntityBlock {

    /**
     * 盘面星象：哪一组星<b>落在盘面上</b>（另一组由渲染器浮在空中）。
     *
     * <p>写在方块状态里而不是实体里，是因为**模型**要跟着换（两套十二块贴图），
     * 模型只能由 blockstate 选。</p>
     */
    public enum Chart implements StringRepresentable {
        /** 北斗七星在盘面上，南斗六星浮空。 */
        SEVEN("seven"),
        /** 南斗六星在盘面上，北斗七星浮空。 */
        SIX("six");

        private final String name;

        Chart(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        public Chart flip() {
            return this == SEVEN ? SIX : SEVEN;
        }
    }

    /**
     * 一方在阵中的身份：未成型，或「第几行第几列」。
     *
     * <p>枚举顺序刻意排成 {@code NONE, R0C0..R3C2}，{@link #roleFor} 才好按下标取。</p>
     */
    public enum Role implements StringRepresentable {
        NONE("none"),
        R0C0("r0c0"), R0C1("r0c1"), R0C2("r0c2"),
        R1C0("r1c0"), R1C1("r1c1"), R1C2("r1c2"),
        R2C0("r2c0"), R2C1("r2c1"), R2C2("r2c2"),
        R3C0("r3c0"), R3C1("r3c1"), R3C2("r3c2");

        private final String name;

        Role(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** 已成型。{@code NONE} 之外都算。 */
        public boolean formed() {
            return this != NONE;
        }

        /** 这一格在阵里的行（0~3）。只对 {@link #formed()} 的取值有意义。 */
        public int row() {
            return (this.ordinal() - 1) / 3;
        }

        /** 这一格在阵里的列（0~2）。只对 {@link #formed()} 的取值有意义。 */
        public int col() {
            return (this.ordinal() - 1) % 3;
        }
    }

    /**
     * 一个星位：哪颗星、落在哪一格、在整阵 96×128 上的坐标。
     *
     * @param px 盘面 x（0~96），{@code py} 盘面 y（0~128）
     */
    public record Seat(Star star, int row, int col, float px, float py) {
    }

    /**
     * 北斗七星的七个星位——<b>星画在哪一格，就点哪一格</b>。
     *
     * <p>★ 第一百二十三批改的根子：星图是跨格画的（北斗从右下挑到右上），
     * 所以「上两行 = 北斗」那种按行切的办法，点到的格子和看见的星根本不在同一格，
     * 玩家得点别处才能把东西放到眼前的星上。现在星位<b>由图案本身决定</b>：
     * 坐标落在哪一格，那一格就是这颗星的星位。</p>
     *
     * <p>★ 坐标与 {@code tools/gen_star_dial.py} 的 {@code SEVEN_STARS} 一一对应，
     * 改一处就要改另一处。</p>
     */
    private static final Seat[] BEIDOU_SEATS = {
            new Seat(Star.BEIDOU_TIANSHU, 3, 0, 16.0F, 102.0F),
            new Seat(Star.BEIDOU_TIANXUAN, 2, 0, 22.0F, 70.0F),
            new Seat(Star.BEIDOU_TIANJI, 2, 1, 50.0F, 78.0F),
            new Seat(Star.BEIDOU_TIANQUAN, 3, 1, 56.0F, 108.0F),
            new Seat(Star.BEIDOU_YUHENG, 2, 2, 74.0F, 94.0F),
            new Seat(Star.BEIDOU_KAIYANG, 1, 2, 86.0F, 62.0F),
            new Seat(Star.BEIDOU_YAOGUANG, 0, 2, 84.0F, 24.0F),
    };

    /** 南斗六星的六个星位。坐标与 {@code SIX_STARS} 一一对应。 */
    private static final Seat[] NANDOU_SEATS = {
            new Seat(Star.NANDOU_TIANFU, 1, 0, 16.0F, 58.0F),
            new Seat(Star.NANDOU_TIANLIANG, 0, 1, 44.0F, 20.0F),
            new Seat(Star.NANDOU_TIANJI, 1, 1, 58.0F, 52.0F),
            new Seat(Star.NANDOU_TIANTONG, 2, 0, 26.0F, 80.0F),
            new Seat(Star.NANDOU_TIANXIANG, 1, 2, 76.0F, 62.0F),
            new Seat(Star.NANDOU_QISHA, 3, 2, 88.0F, 108.0F),
    };

    /** 当前这一斗的星位表。 */
    public static Seat[] seats(Chart chart) {
        return chart == Chart.SEVEN ? BEIDOU_SEATS : NANDOU_SEATS;
    }

    /** 这一格在当前这一斗里是哪颗星；不是星位就给 {@code null}。 */
    public static Seat seatAt(Chart chart, int row, int col) {
        for (Seat seat : seats(chart)) {
            if (seat.row() == row && seat.col() == col) {
                return seat;
            }
        }
        return null;
    }

    /** 行列 → 身份。{@code NONE} 占了下标 0，所以偏移一位。 */
    private static Role roleFor(int row, int col) {
        return Role.values()[1 + row * 3 + col];
    }

    public static final EnumProperty<Role> ROLE = EnumProperty.create("role", Role.class);
    public static final EnumProperty<Chart> CHART = EnumProperty.create("chart", Chart.class);

    /** 三列四行，十二格全要。 */
    private static final boolean[][] CELLS = {
            {true, true, true},
            {true, true, true},
            {true, true, true},
            {true, true, true},
    };

    private static final int ROWS = CELLS.length;
    private static final int COLS = CELLS[0].length;

    /** 阵心的行 / 列（四行取靠中的第二行）。十二格的坐标都相对它算。 */
    private static final int CENTER_ROW = 1;
    private static final int CENTER_COL = 1;

    public StarDialBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(ROLE, Role.NONE)
                .setValue(CHART, Chart.SEVEN)
                .setValue(TYPE, SlabType.BOTTOM)
                .setValue(WATERLOGGED, Boolean.FALSE));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StarDialBlockEntity(pos, state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ROLE, CHART);
    }

    /**
     * 只放成**下半砖**。原版会按点击位置在上下半砖间选，这里钉死成 {@code BOTTOM}。
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState placed = super.getStateForPlacement(context);
        return placed == null ? null : placed.setValue(TYPE, SlabType.BOTTOM);
    }

    /** 不允许拿半砖叠成整砖（叠起来会变成 {@code TYPE=double}，模型对不上）。 */
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        return false;
    }

    // ------------------------------------------------------------------
    // 交互
    // ------------------------------------------------------------------

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (WandApi.isBuiltinWand(stack)) {
            if (level.isClientSide()) {
                return ItemInteractionResult.SUCCESS;
            }
            if (!(level instanceof ServerLevel server)) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (player.isSecondaryUseActive()) {
                BlockPos origin = originOf(pos, state.getValue(ROLE));
                if (origin != null) {
                    dissolve(server, origin);
                }
            } else if (!tryForm(server, pos)) {
                player.displayClientMessage(Component.translatable(
                        "message.skydeitymysticpivot.star_dial.incomplete"), true);
            }
            return ItemInteractionResult.SUCCESS;
        }
        if (!state.getValue(ROLE).formed()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide()) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel server)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // 潜行 + 空手 = 整盘翻面（第一百一十七批从「右键外围任意格」改到这一手势）
        if (stack.isEmpty() && player.isSecondaryUseActive()) {
            toggleChart(server, pos, state);
            return ItemInteractionResult.SUCCESS;
        }
        placeOrTake(server, pos, state, player, stack);
        return ItemInteractionResult.SUCCESS;
    }

    /** 空手右键：取回这一格的供物。 */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!state.getValue(ROLE).formed()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && level instanceof ServerLevel server) {
            if (player.isSecondaryUseActive()) {
                toggleChart(server, pos, state);
            } else {
                placeOrTake(server, pos, state, player, ItemStack.EMPTY);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * 一格一件：放上 / 取回。
     *
     * <p>槽里有东西时<b>不覆盖</b>——先空手取走再来放，免得一不小心把玩家的东西换没了。</p>
     *
     * <p>★ 第一百一十九批：<b>只认当前这一斗</b>。盘面是北斗就只能往北斗六格放，
     * 南斗六格放了也白放——直接不受理（没有星名、没有音效），省得玩家以为放上了。
     * 取回不受限制：翻面之后原来那斗的东西还能拿回来。</p>
     */
    private static void placeOrTake(ServerLevel server, BlockPos pos, BlockState state,
                                    Player player, ItemStack held) {
        if (!(server.getBlockEntity(pos) instanceof StarDialBlockEntity dial)) {
            return;
        }
        ItemStack stored = dial.getItem();
        if (held.isEmpty()) {
            if (stored.isEmpty()) {
                return;
            }
            dial.setItem(ItemStack.EMPTY);
            if (!player.getInventory().add(stored)) {
                player.drop(stored, false);
            }
            server.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.6F, 1.3F);
            return;
        }
        if (!stored.isEmpty()) {
            return;
        }
        Role role = state.getValue(ROLE);
        if (seatAt(state.getValue(CHART), role.row(), role.col()) == null) {
            // 这一格不属于当前这一斗：不受理（不放、不出声、也不显示星名）
            return;
        }
        dial.setItem(held.copyWithCount(1));
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        server.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6F, 1.2F);
    }

    /** 整盘翻面（七星 ⇄ 六星）。 */
    private static void toggleChart(ServerLevel server, BlockPos pos, BlockState state) {
        BlockPos origin = originOf(pos, state.getValue(ROLE));
        if (origin == null) {
            return;
        }
        Chart next = state.getValue(CHART).flip();
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                if (!CELLS[row][col]) {
                    continue;
                }
                BlockPos cell = cellAt(origin, row, col);
                BlockState cellState = server.getBlockState(cell);
                if (cellState.getBlock() instanceof StarDialBlock && cellState.getValue(ROLE).formed()) {
                    server.setBlock(cell, cellState.setValue(CHART, next), 3);
                }
            }
        }
        server.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.15F);
    }

    // ------------------------------------------------------------------
    // 成型 / 收阵
    // ------------------------------------------------------------------

    /**
     * 从被点的那一格出发找阵心：十二格全齐的位置里取 z 最小、再 x 最小的那个。
     *
     * <p>方位因此固定（北 = -Z、西 = -X），点哪一格都得到同一座阵。</p>
     */
    private static BlockPos findFormation(ServerLevel server, BlockPos clicked) {
        BlockPos best = null;
        for (int dz = -ROWS; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) {
                BlockPos origin = clicked.offset(dx, 0, dz);
                if (!canForm(server, origin)) {
                    continue;
                }
                if (best == null || origin.getZ() < best.getZ()
                        || (origin.getZ() == best.getZ() && origin.getX() < best.getX())) {
                    best = origin;
                }
            }
        }
        return best;
    }

    /** 十二格全齐就成型（已成型时是空操作，不重播动画）。 */
    private static boolean tryForm(ServerLevel server, BlockPos clicked) {
        BlockPos origin = findFormation(server, clicked);
        if (origin == null) {
            return false;
        }
        if (!isFormed(server, origin)) {
            form(server, origin);
        }
        return true;
    }

    /** 判定只看这一圈方块：十二格必须全是星盘（成型的也算，补回一格之后能就地补齐）。 */
    private static boolean canForm(ServerLevel server, BlockPos origin) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                if (!CELLS[row][col]) {
                    continue;
                }
                if (!(server.getBlockState(cellAt(origin, row, col)).getBlock() instanceof StarDialBlock)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 十二格身份是否已经全对。 */
    private static boolean isFormed(ServerLevel server, BlockPos origin) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                if (!CELLS[row][col]) {
                    continue;
                }
                BlockState state = server.getBlockState(cellAt(origin, row, col));
                if (!(state.getBlock() instanceof StarDialBlock) || state.getValue(ROLE) != expected(row, col)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 成型：写身份，星象回到「七星落盘」。 */
    private static void form(ServerLevel server, BlockPos origin) {
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                if (!CELLS[row][col]) {
                    continue;
                }
                BlockPos cell = cellAt(origin, row, col);
                server.setBlock(cell, server.getBlockState(cell)
                        .setValue(ROLE, expected(row, col))
                        .setValue(CHART, Chart.SEVEN), 3);
            }
        }
        server.playSound(null, origin, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.8F, 1.4F);
        server.sendParticles(ParticleTypes.END_ROD, origin.getX() + 0.5, origin.getY() + 0.75,
                origin.getZ() + 0.5, 48, 1.4, 0.4, 1.8, 0.02);
    }

    /** 收阵：十二格退回阵眼，格子里的物品全部掉出来。 */
    private static void dissolve(ServerLevel server, BlockPos origin) {
        boolean changed = false;
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                if (!CELLS[row][col]) {
                    continue;
                }
                BlockPos cell = cellAt(origin, row, col);
                BlockState state = server.getBlockState(cell);
                if (!(state.getBlock() instanceof StarDialBlock) || !state.getValue(ROLE).formed()) {
                    continue;
                }
                dropStored(server, cell);
                server.setBlock(cell, state.setValue(ROLE, Role.NONE), 3);
                changed = true;
            }
        }
        if (changed) {
            server.playSound(null, origin, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.7F, 1.2F);
        }
    }

    /** 把某一格里供着的东西吐到地上（没有就什么也不做）。 */
    private static void dropStored(ServerLevel server, BlockPos pos) {
        if (!(server.getBlockEntity(pos) instanceof StarDialBlockEntity dial)) {
            return;
        }
        ItemStack stack = dial.takeItem();
        if (!stack.isEmpty()) {
            Block.popResource(server, pos, stack);
        }
    }

    // ------------------------------------------------------------------
    // 破一格 = 整座失效
    // ------------------------------------------------------------------

    /**
     * 拆掉任意一格 → 其余十一格一并退回阵眼，格里的物品掉出来。
     *
     * <p>只认「真的被换成别的方块」这一种：{@code dissolve} 自己写 {@code role=none} 时
     * {@code newState} 仍是星盘，这里直接放过，不会自己叫自己。</p>
     */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && level instanceof ServerLevel server
                && !(newState.getBlock() instanceof StarDialBlock)) {
            dropStored(server, pos);
            if (state.getValue(ROLE).formed()) {
                BlockPos origin = originOf(pos, state.getValue(ROLE));
                if (origin != null) {
                    dissolve(server, origin);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** 阵心 + 行列 → 世界坐标。 */
    public static BlockPos cellAt(BlockPos origin, int row, int col) {
        return origin.offset(col - CENTER_COL, 0, row - CENTER_ROW);
    }

    /** 反推：这一格在阵里，阵心在哪。未成型返回 {@code null}。 */
    private static BlockPos originOf(BlockPos pos, Role role) {
        if (!role.formed()) {
            return null;
        }
        return pos.offset(-(role.col() - CENTER_COL), 0, -(role.row() - CENTER_ROW));
    }

    /** 每一格应有的身份：就是它自己的行列。 */
    private static Role expected(int row, int col) {
        return roleFor(row, col);
    }
}
