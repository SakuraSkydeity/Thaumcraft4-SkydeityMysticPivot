package skydeity.mysticpivot;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

/**
 * 玄枢台座：皇宫鎏金橘黄的单格台座（底座四级收分 + 石英柱身 + 台面板，形制仿本体奥术基座），可置物其上。
 *
 * <p>卦象转化：台座持续感应所在处的卦气（{@link Fengshui#qiAt}）——
 * 当地处「乾」之地气（开阔天空、头顶无遮挡）时，台面上的黑曜石会缓缓化为哭泣的黑曜石。
 * 可转化的物事集中在 {@link #TRANSFORMATIONS} 一张表里，JEI / EMI 也从这张表取。</p>
 *
 * <p>卦气呈现：台座上方悬着所在处的卦象单字（{@link XuanshuPedestalRenderer}），
 * 四周浮着一圈斜置椭圆的附魔粒子（{@link #clientTick}）——立在哪里、凝的哪一卦，一眼可辨。</p>
 *
 * <p><b>客户端同步</b>（曾经漏掉、导致「放上去看不见、看着像拿不下来」）：台面上的物品由
 * {@link XuanshuPedestalRenderer} 在<b>客户端那台方块实体</b>上绘制，所以任何改动都必须下发——
 * 只调 {@code setChanged()} 只保证存档，客户端手里的 {@code items} 依旧是空的。
 * 这里三件事配齐：{@code getUpdateTag}（区块下发时带全量数据）、{@code getUpdatePacket}
 * （单点更新）、内容一变就 {@code sendBlockUpdated}（实时推送）。</p>
 */
public class XuanshuPedestalBlockEntity extends BlockEntity {

    /** 单格台面。 */
    public static final int SLOT = 0;

    /** 转化所需总时长（tick），每 10 tick 推进一次，共 3 秒。 */
    public static final int TRANSFORM_TICKS = 60;

    /**
     * 一条「卦象转化」。
     *
     * @param input  台面上要摆的东西（只认物品，件数随意）
     * @param output 转化后的产物
     * @param qi     必须凝着的卦气
     */
    public record Transformation(ItemStack input, ItemStack output, Fengshui.Trigram qi) {

        /** 台面上这堆东西是不是本条要的物事。 */
        public boolean accepts(ItemStack stack) {
            return !stack.isEmpty() && stack.is(this.input.getItem());
        }

        /**
         * 把台面上那堆整体换成产物。
         *
         * <p>★ <b>件数照搬</b>——早先这里写死 {@code new ItemStack(Items.CRYING_OBSIDIAN)}，
         * 于是放一组 64 个黑曜石也只拿回 1 个哭泣的黑曜石，其余的凭空没了。</p>
         */
        public ItemStack convert(ItemStack stack) {
            return new ItemStack(this.output.getItem(), stack.getCount());
        }
    }

    /**
     * 台座可做的全部转化——<b>唯一的一处定义</b>，运行时与配方查看器（JEI / EMI）共用同一张表。
     *
     * <p>乾为天，天光彻照则黑曜石泣。</p>
     */
    public static final List<Transformation> TRANSFORMATIONS = List.of(
            new Transformation(new ItemStack(Items.OBSIDIAN), new ItemStack(Items.CRYING_OBSIDIAN),
                    Fengshui.Trigram.QIAN));

    /**
     * 台面上这堆东西、在当下这片卦气里，该走哪一条转化；无则回 {@code null}。
     *
     * <p>卦气只取优先级最高的那一支（{@link Fengshui#qiAt}）——台座立在开阔的森林里，
     * 得到的是巽而不是乾，也就化不出哭泣的黑曜石。</p>
     */
    public static Transformation findTransformation(ItemStack stack, Fengshui.Trigram qi) {
        for (Transformation transformation : TRANSFORMATIONS) {
            if (transformation.qi() == qi && transformation.accepts(stack)) {
                return transformation;
            }
        }
        return null;
    }


    /**
     * 卦气环：<b>绕 X 轴斜置 26° 的椭圆</b>，长轴 1.10 格、短轴 0.82 格。
     *
     * <p>短长轴之比固定在 0.82 / 1.10 ≈ 0.745（原 0.58 / 0.78 ≈ 0.744）——
     * 放大时<b>只该整体推出去，不该顺手把形状压扁</b>，环的「扁」是它斜置 26° 之外的
     * 第二重读法，动了比例就不再是同一枚环。</p>
     *
     * <p>0.78 → 1.10 的这一跳是「跨出方块本体」：台座自己是 1×1，长轴 1.10 时两端
     * 会探到相邻那两格的上空，正好压在四象石砖的内台上方——环从「盘在柱头」
     * 变成「笼住台口」，与砖唇那圈「包裹」是同一个动作的两处表现。</p>
     */
    private static final double RING_A = 1.10D;
    private static final double RING_B = 0.82D;
    /** 环面倾角：绕 X 轴 26°（斜着盘在台座顶上）。 */
    private static final double RING_TILT = Math.toRadians(26.0D);
    /** 环心高度（台座顶面在 1.0）。 */
    private static final double RING_Y = 1.06D;
    /**
     * 整圈上均匀取几个点。
     *
     * <p>★ <b>环不转，那就得把整圈铺满</b>：以前靠「3 个点 + 旋转」用时间积出一圈，
     * 现在要的是一枚<b>静止</b>的斜椭圆，于是取 22 个点把整条椭圆一次画全。</p>
     *
     * <p>★ <b>点数要跟着半径一起改</b>：16 点是按长轴 0.78 定的密度
     * （周长约 4.2 格、点距约 0.26 格）。放大到 1.10 之后周长涨到约 5.9 格，
     * 点数不动的话点距变成 0.37——环会从「一串符」散成「一堆点」。
     * 22 点把点距拉回约 0.27 格，与旧观感一致。上限是 26：符文贴图一共 26 张，
     * 点数超过 26 就一定有两枚重复，斜对着看会露怯。</p>
     */
    private static final int RING_DOTS = 22;

    /** 开坛后那圈「已启」小符环：平铺的水平圆（不倾斜），贴着柱头。 */
    private static final double INNER_R = 0.44D;
    private static final double INNER_Y = 0.86D;
    private static final int INNER_DOTS = 12;

    /** 单格台面；台面一有变动就存档并下发。 */
    private final ItemStackHandler items = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            syncToClient();
        }
    };

    private int progress;

    /**
     * 台座是否已经「开坛」——四隅立齐四象石砖、以法杖点过之后为真。
     *
     * <p>未开坛的台座<b>不纳物</b>（见 {@code XuanshuPedestalBlock#useItemOn}），
     * 所以这是决定「这座台座能不能用」的开关，也是四砖亮不亮的依据。
     * 它<b>要存档、要下发</b>：客户端渲染器据此多画一圈粒子。</p>
     */
    private boolean active;

    /**
     * 卦气环现存的那一层还剩几 tick（0 = 该补了）。
     *
     * <p><b>只存在于客户端、不存档</b>：它管的是「什么时候再撒一层粒子」，与方块数据无关。</p>
     */
    private int ringCooldown;

    /** 自查周期（tick）：已开坛的台座每 20 tick 确认一次四砖还在不在。 */
    private static final int AUDIT_INTERVAL = 20;

    /**
     * 四隅相对台座的水平偏移——3×3 的<b>四个对角</b>（不含十字四邻）。
     *
     * <p>「四象」镇四方四隅，取对角而非正前后左右：正十字的四格是台座的正面与通道，
     * 摆在那里会挡住玩家走到座前。</p>
     */
    public static final int[][] CORNERS = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};

    public XuanshuPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.XUANSHU_PEDESTAL.get(), pos, state);
    }

    public ItemStackHandler getItems() {
        return this.items;
    }

    public boolean isActive() {
        return this.active;
    }

    /**
     * 四隅是不是各立着一块四象石砖。
     *
     * <p>只认<b>方块种类</b>、不看它此刻 {@code active} 是真是假——正在点亮的途中
     * 也会走到这里，若把状态也算进来就会自己把自己判死。</p>
     */
    public static boolean cornersIntact(Level level, BlockPos pos) {
        for (int[] corner : CORNERS) {
            if (!level.getBlockState(pos.offset(corner[0], 0, corner[1]))
                    .is(ModBlocks.SIXIANG_STONE_BRICK.get())) {
                return false;
            }
        }
        return true;
    }

    /**
     * 把四砖一并点亮 / 灭掉，并顺手把「阶梯朝哪边升」写对（只有服务端会真的写方块）。
     *
     * <p>★ 朝向必须在这里写：模型是照 BlockState 选的，四块砖分散在四个对角上，
     * 各自要「最高的一侧」正对台座才有「四条阶梯合围成一坛」的样子。朝向由
     * {@link ModBlocks.SixiangStoneBrickBlock#cornerFacing} 从偏移量算出，
     * 与 {@link #CORNERS} 一一对应。</p>
     */
    private static void lightBricks(Level level, BlockPos pos, boolean lit) {
        for (int[] corner : CORNERS) {
            BlockPos p = pos.offset(corner[0], 0, corner[1]);
            BlockState state = level.getBlockState(p);
            if (!state.is(ModBlocks.SIXIANG_STONE_BRICK.get())) {
                continue;
            }
            BlockState want = state
                    .setValue(ModBlocks.SixiangStoneBrickBlock.ACTIVE, lit)
                    .setValue(ModBlocks.SixiangStoneBrickBlock.CORNER,
                            ModBlocks.SixiangStoneBrickBlock.cornerFacing(corner[0], corner[1]));
            if (want != state) {
                level.setBlock(p, want, Block.UPDATE_ALL);
            }
        }
    }

    /**
     * 开坛：四隅齐备则点亮四砖并把台座转为「已启」。
     *
     * @return 四隅不齐（或本来就已经开着）时给 {@code false}
     */
    public boolean tryActivate(Level level, BlockPos pos) {
        if (level.isClientSide || !cornersIntact(level, pos)) {
            return false;
        }
        setActive(level, pos, true);
        return true;
    }

    /** 收坛：台座熄火、四砖暗回去（重复调用无副作用）。 */
    public void deactivate(Level level, BlockPos pos) {
        setActive(level, pos, false);
    }

    /**
     * 置「已启 / 已熄」，一并处理存档、下发与四砖的明暗。
     *
     * <p>★ 顺手把四砖也按同一状态写一遍：只改台座自己会让两边对不上
     * （台座已熄、砖还亮着，玩家看着就像「明明亮着却说没启」）。</p>
     */
    public void setActive(Level level, BlockPos pos, boolean value) {
        if (this.active == value) {
            // 状态没变也要保证砖是对的（例如读档后砖的状态丢了）
            if (!level.isClientSide) {
                lightBricks(level, pos, value);
            }
            return;
        }
        this.active = value;
        this.setChanged();
        this.syncToClient();
        lightBricks(level, pos, value);
    }

    /** 台面一有变动就推给客户端；服务端专用，客户端调用为空操作。 */
    public void syncToClient() {
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(),
                    this.getBlockState(), Block.UPDATE_ALL);
        }
    }

    /** 区块下发 / 首次进入视野时的全量数据。 */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return this.saveWithoutMetadata(registries);
    }

    /** 单点实时更新包。 */
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * 客户端每 tick：台座处既凝着卦气，便沿一圈<b>斜置 26° 的椭圆</b>摆上附魔符文
     * （长轴 0.78 / 短轴 0.58）——看着就是原版附魔台那种符文，只是不下落、不漂移。
     *
     * <p>★ <b>用的是自建的零重力符文</b>（{@link ModParticles#QI_GLYPH}）而不是
     * {@link ParticleTypes#ENCHANT}：原版那枚是 {@code FlyTowardsPositionParticle}，
     * 它的 {@code tick()} 里写死了 {@code y = yStart + yd·f − f1⁴ × 1.2} ——
     * <b>寿命末尾必沉 1.2 格，与 {@code yd} 传什么无关</b>，做不到用户要的「不下落」。
     * 自建这枚除了变老什么都不做，贴图仍是原版那 26 张附魔符文（{@code sga_a … sga_z}）。</p>
     *
     * <p>★ <b>整环不转、且只维持一层</b>：{@link #RING_DOTS} 个点一次铺满整条椭圆、
     * 相位固定，<b>等这一层全部灭掉（{@link ModParticles#RING_LIFETIME} tick）才补下一层</b>。
     * 每个位置该显哪一号符文通过粒子参数里的 {@code zd} 传下去，所以同一个点上永远是同一个字。</p>
     *
     * <p>★★ <b>为什么不能「每几 tick 补一遍」</b>：粒子寿命比补描周期长十几倍时，同一个位置上
     * 会叠着十几枚<b>年龄各不相同</b>的符文，而半透明贴图的透明度是相加的 —— 总亮度随各代
     * 年龄此起彼伏，看着就是「一直在刷新、一直在闪」。一层就没有这个问题，整圈只随寿命
     * 缓缓明灭一次。</p>
     *
     * <p>不走 {@code Block.animateTick}：那条路是「每 tick 随机抽 1334 个坐标」的稀疏散点，
     * 单个方块几十 tick 才被抽中一次，凑不成一整圈。走方块实体的客户端 tick 才是每 tick 必到。</p>
     *
     * <p>★ <b>未成坛则一点都不起</b>：没开坛的台座（四象未立齐、或还没以法杖点过）
     * 不测卦气，所以既没有外圈卦气环、也没有内圈「已启」符环——一眼就能分清
     * 「这是一座台座」和「这是一座成了坛的台座」。</p>
     */
    public static void clientTick(Level level, BlockPos pos, XuanshuPedestalBlockEntity be) {
        // ★ 未成坛 = 不测卦气。卦象是「坛成之后才起」的东西：四象没立齐、没以法杖点过，
        //   这座台座就是一块石头，头上不凝字、身边也不该浮粒子的环。
        if (!be.active) {
            be.ringCooldown = 0;
            return;
        }
        boolean hasQi = !Fengshui.qiAll(level, pos).isEmpty();
        if (be.ringCooldown > 0) {
            // ★ 上一层还在，什么都不做 —— 这就是「只有一层」
            be.ringCooldown--;
            return;
        }
        // 补一层：寿命与补描间隔取同一个数，于是上一层刚灭、下一层就到
        be.ringCooldown = ModParticles.RING_LIFETIME - 1;
        double cx = pos.getX() + 0.5D;
        double cy = pos.getY() + RING_Y;
        double cz = pos.getZ() + 0.5D;
        double sinTilt = Math.sin(RING_TILT);
        double cosTilt = Math.cos(RING_TILT);
        if (hasQi) {
            for (int i = 0; i < RING_DOTS; i++) {
                double angle = i * (Math.PI * 2.0D / RING_DOTS);
                // 先在水平面上取椭圆，再整体绕 X 轴倾 26°：y 由短轴分量给出高度、z 被压短
                double ex = Math.cos(angle) * RING_A;
                double ez = Math.sin(angle) * RING_B;
                // 三个速度全传 0（这枚粒子本来也不动）；zd 借来当「第几号符文」
                level.addParticle(ModParticles.QI_GLYPH.get(),
                        cx + ex, cy + ez * sinTilt, cz + ez * cosTilt,
                        0.0D, 0.0D, i);
            }
        }
        if (be.active) {
            // 开坛之后多贴一圈小符环（平铺、不倾斜），压在柱头那一圈上——
            // 与卦气环同生共灭，于是「这座台座是不是启过的」不必凑近看四砖也能认出来。
            for (int i = 0; i < INNER_DOTS; i++) {
                double angle = i * (Math.PI * 2.0D / INNER_DOTS);
                level.addParticle(ModParticles.QI_GLYPH.get(),
                        cx + Math.cos(angle) * INNER_R,
                        pos.getY() + INNER_Y,
                        cz + Math.sin(angle) * INNER_R,
                        0.0D, 0.0D, (i + RING_DOTS) % 26);
            }
        }
    }

    /**
     * 每 10 tick 推进一次卦象转化；另外每 20 tick 自查一次四砖还在不在。
     *
     * <p>★ <b>未成坛则不测卦气</b>：卦象转化（黑曜石 → 哭泣的黑曜石）只在台座「已启」
     * 时进行。台座立对了地方却没开坛，台面上的东西就一直原样搁着——这与
     * 「未开之坛不受外物」是同一条规矩的两面。</p>
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  XuanshuPedestalBlockEntity be) {
        if (level.isClientSide) {
            return;
        }
        if (level.getGameTime() % AUDIT_INTERVAL == 0) {
            if (be.active && !cornersIntact(level, pos)) {
                // 四砖缺了一块：熄火（砖那边 onRemove 也会立刻招呼一次，这里是兜底）
                be.setActive(level, pos, false);
            } else {
                // 把四砖抹平成台座的样子——读档、区块先后加载、砖被替换过，
                // 都可能让两边对不上，十拿九稳的做法是定期往回对齐一次。
                lightBricks(level, pos, be.active);
            }
        }
        if (!be.active) {
            // ★ 未成坛：不测卦气、不转化（进度也一并抹掉，免得开坛瞬间「凭空推进」）
            be.progress = 0;
            return;
        }
        if (level.getGameTime() % 10 != 0) {
            return;
        }
        ItemStack stack = be.items.getStackInSlot(SLOT);
        Transformation transformation = findTransformation(stack, Fengshui.qiAt(level, pos));
        if (transformation == null) {
            be.progress = 0;
            return;
        }
        be.progress += 10;
        if (be.progress % 20 == 0) {
            ((ServerLevel) level).sendParticles(ParticleTypes.PORTAL,
                    pos.getX() + 0.5D, pos.getY() + 1.2D, pos.getZ() + 0.5D,
                    6, 0.2D, 0.1D, 0.2D, 0.5D);
        }
        if (be.progress >= TRANSFORM_TICKS) {
            be.progress = 0;
            // setStackInSlot 会走 onContentsChanged，同步随之下发。
            // ★ 件数照搬：转化的是「这一堆」，不是「一个」。
            be.items.setStackInSlot(SLOT, transformation.convert(stack));
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.BLOCKS, 0.8F, 0.7F);
        }
        be.setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("items", this.items.serializeNBT(registries));
        tag.putInt("progress", this.progress);
        tag.putBoolean("active", this.active);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.items.deserializeNBT(registries, tag.getCompound("items"));
        this.progress = tag.getInt("progress");
        this.active = tag.getBoolean("active");
        // ★ 不在这里写方块：loadAdditional 是在区块反序列化途中调的，
        //   那会儿 setBlock 会把邻块更新插进一段还没搭好的区块里。四砖的对齐交给
        //   serverTick 的定期自查（最多 1 秒，肉眼无感）。
    }
}
