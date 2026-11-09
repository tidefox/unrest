package io.github.tidefox.unrest.core;

import io.github.tidefox.unrest.config.UnrestConfig;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * The sleepers' shared dream roll (DESIGN.md §4). Every few seconds (a random
 * gap) the sleepers roll once together. Their combined Luck, the rubber band
 * and the moon shift the odds; momentum (§4.3) carries over between rolls.
 */
public final class DreamRoller {

    /** One shared roll. */
    public static final class Roll {
        /** The dream before this roll, or null on the first roll. */
        public final Dream before;
        /** The dream that took effect, after momentum. */
        public final Dream after;
        /** The number rolled, 0–100. High is good. */
        public final double value;
        /** The bands it was rolled against (see {@link SleepMath#bands}). */
        public final double[] bands;
        public final List<EntityPlayerMP> sleepers;
        /** The sleepers' combined Luck, already clamped. */
        public final double luck;
        /** Luck the rubber band added (negative when the meter is far ahead). */
        public final double rubber;
        /** Moon chaos, −1 to +1. */
        public final double chaos;
        /** The sleeper a Nightmare wakes, or null. */
        public final EntityPlayerMP woken;
        public final Momentum.Step step;
        /** Each sleeper's weight until the next roll. */
        public final double weight;

        Roll(Dream before, Dream after, double value, double[] bands, List<EntityPlayerMP> sleepers, double luck,
             double rubber, double chaos, EntityPlayerMP woken, Momentum.Step step, double weight) {
            this.before = before;
            this.after = after;
            this.value = value;
            this.bands = bands;
            this.sleepers = sleepers;
            this.luck = luck;
            this.rubber = rubber;
            this.chaos = chaos;
            this.woken = woken;
            this.step = step;
            this.weight = weight;
        }
    }

    private final Momentum momentum = new Momentum();
    private Dream current;
    private double currentWeight = 1.0;

    /** Rolls once for all sleepers. Returns null if dreams are off or nobody sleeps. */
    public Roll roll(List<EntityPlayerMP> sleepers, double r, double debt, double rubber, double chaos,
                     boolean fever, Random rand) {
        if (!UnrestConfig.dreamsEnabled || sleepers.isEmpty()) {
            current = null;
            currentWeight = 1.0;
            return null;
        }
        double luck = luck(sleepers);
        double[] bands = SleepMath.bands(r, debt, luck + rubber, chaos);
        double value = rand.nextDouble() * 100;
        Dream before = current;
        Momentum.Step step = momentum.next(SleepMath.dreamFor(value, bands), fever);
        EntityPlayerMP woken = step.dream == Dream.NIGHTMARE ? unluckiest(sleepers, rand) : null;
        // A Nightmare wakes one sleeper; the rest sleep on soundly.
        current = step.dream == Dream.NIGHTMARE ? Dream.NORMAL : step.dream;
        currentWeight = momentum.weight(current, fever);
        return new Roll(before, step.dream, value, bands, Collections.unmodifiableList(new ArrayList<>(sleepers)),
                luck, rubber, chaos, woken, step, currentWeight);
    }

    /** The sleepers' combined Luck attribute (Luck and Bad Luck potions, items), clamped to ±luckMax. */
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

    public Momentum momentum() {
        return momentum;
    }

    /** Sum of the dream weights of the sleepers. */
    public double weight(List<EntityPlayerMP> sleepers) {
        return sleepers.size() * (UnrestConfig.dreamsEnabled ? currentWeight : 1.0);
    }

    public void clear() {
        current = null;
        currentWeight = 1.0;
        momentum.clear();
    }
}
