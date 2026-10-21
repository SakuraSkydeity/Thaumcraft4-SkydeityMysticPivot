package skydeity.mysticpivot;

import net.minecraft.network.chat.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * 一座阵当下的<b>星象</b>：哪些星亮着（第一百一十七批）。
 *
 * <p>不可变快照。由 {@link StarWork#at} 从十二格上的物品读出来，
 * 交给「工作方块」去判特殊合成（见 {@link StarForge}）。</p>
 *
 * <h2>十三颗星各有其位</h2>
 * <p>第一百二十三批起星位由图案决定（见 {@link StarDialBlock.Seat}），
 * 北斗七颗、南斗六颗都能各放一件，所以不再有「隐星自动点亮」那回事。</p>
 */
public final class Constellation {

    /** 什么星也没亮。 */
    public static final Constellation EMPTY = new Constellation(EnumSet.noneOf(Star.class));

    private final Set<Star> lit;

    public Constellation(Collection<Star> lit) {
        EnumSet<Star> set = EnumSet.noneOf(Star.class);
        set.addAll(lit);
        this.lit = Collections.unmodifiableSet(set);
    }

    public boolean has(Star star) {
        return this.lit.contains(star);
    }

    /** 亮着的星（含自动点亮的摇光）。 */
    public Set<Star> lit() {
        return this.lit;
    }

    public boolean isEmpty() {
        return this.lit.isEmpty();
    }

    public int count() {
        return this.lit.size();
    }

    public int beidouCount() {
        return (int) this.lit.stream().filter(Star::beidou).count();
    }

    public int nandouCount() {
        return this.count() - this.beidouCount();
    }

    /** 北斗斗口四星全亮。 */
    public boolean beidouMouth() {
        for (Star star : Star.BEIDOU_MOUTH) {
            if (!has(star)) {
                return false;
            }
        }
        return true;
    }

    /** 南斗斗口四星全亮。 */
    public boolean nandouMouth() {
        for (Star star : Star.NANDOU_MOUTH) {
            if (!has(star)) {
                return false;
            }
        }
        return true;
    }

    /** 北斗七星全亮。 */
    public boolean beidouComplete() {
        return beidouCount() == 7;
    }

    /** 南斗六星全亮。 */
    public boolean nandouComplete() {
        return nandouCount() == 6;
    }

    /**
     * 星象名（lang key 的后半段）。
     *
     * <p>判定由严到宽：全曜 → 两口同合 → 单口合 → 杀相 → 杂曜。</p>
     */
    public String key() {
        if (this.lit.isEmpty()) {
            return "empty";
        }
        if (beidouComplete()) {
            return "beidou_all";
        }
        if (nandouComplete()) {
            return "nandou_all";
        }
        if (beidouMouth() && nandouMouth()) {
            return "both_mouth";
        }
        if (beidouMouth()) {
            return "beidou_mouth";
        }
        if (nandouMouth()) {
            return "nandou_mouth";
        }
        if (has(Star.NANDOU_TIANXIANG) && has(Star.NANDOU_QISHA) && nandouCount() == 2) {
            return "sha_xiang";
        }
        return "mixed";
    }

    /** 星象名：「斗魁」「南斗口合」…… */
    public Component displayName() {
        return Component.translatable("constellation.skydeitymysticpivot." + key());
    }
}
