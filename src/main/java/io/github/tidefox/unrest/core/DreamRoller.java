package io.github.tidefox.unrest.core;

import io.github.tidefox.unrest.config.UnrestConfig;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The sleepers' shared dream roll (DESIGN.md §4). Every interval the sleepers
 * roll once together; their combined Luck shifts the odds, and the result
 * applies to all of them until the next roll.
 */
public final class DreamRoller {

    /** One shared roll. */
    public static final class Roll {
        /** The dream before this roll, or null on the first roll. */
        public final Dream before;
        public final Dream after;
        /** The sleepers who rolled, in sleeper order. */
        public final List<EntityPlayerMP> sleepers;
        /** The sleepers' combined Luck, already clamped. */
        public final double luck;
        /** The sleeper a Nightmare wakes, or null. */
        public final EntityPlayerMP woken;

        Roll(Dream before, Dream after, List<EntityPlayerMP> sleepers, double luck, EntityPlayerMP woken) {
            this.before = before;
            this.after = after;
            this.sleepers = sleepers;
            this.luck = luck;
            this.woken = woken;
        }
    }

    private Dream current;

    /** Rolls once for all sleepers. Returns null if dreams are off or nobody sleeps. */
    public Roll roll(List<EntityPlayerMP> sleepers, double r, double debt, Random rand) {
        if (!UnrestConfig.dreamsEnabled || sleepers.isEmpty()) {
            current = null;
            return null;
        }
        double luck = luck(sleepers);
        Dream before = current;
        Dream after = SleepMath.rollDream(r, debt, luck, rand);
        EntityPlayerMP woken = after == Dream.NIGHTMARE ? unluckiest(sleepers, rand) : null;
        // A Nightmare wakes one sleeper; the rest sleep on soundly.
        current = after == Dream.NIGHTMARE ? Dream.NORMAL : after;
        return new Roll(before, after, Collections.unmodifiableList(new ArrayList<>(sleepers)), luck, woken);
    }

    /** The sleepers' combined Luck attribute (Luck and Unluck potions, items), clamped to ±luckMax. */
    public static double luck(List<EntityPlayerMP> sleepers) {
        double[] l = new double[sleepers.size()];
        for (int i = 0; i < l.length; i++) {
            l[i] = sleepers.get(i).getLuck();
        }
        return SleepMath.groupLuck(l);
    }

    private static EntityPlayerMP unluckiest(List<EntityPlayerMP> sleepers, Random rand) {
        List<EntityPlayerMP> worst = new ArrayList<>();
        float min = Float.MAX_VALUE;
        for (EntityPlayerMP p : sleepers) {
            float l = p.getLuck();
            if (l < min) {
                min = l;
                worst.clear();
            }
            if (l == min) {
                worst.add(p);
            }
        }
        return worst.get(rand.nextInt(worst.size()));
    }

    /** The current shared dream. Before the first roll the sleepers sleep normally. */
    public Dream current() {
        return !UnrestConfig.dreamsEnabled || current == null ? Dream.NORMAL : current;
    }

    /** Whether the sleepers have rolled since the last reset. */
    public boolean hasRolled() {
        return UnrestConfig.dreamsEnabled && current != null;
    }

    /** Sum of the dream weights of the sleepers. */
    public double weight(List<EntityPlayerMP> sleepers) {
        return sleepers.size() * current().weight();
    }

    public void clear() {
        current = null;
    }
}
