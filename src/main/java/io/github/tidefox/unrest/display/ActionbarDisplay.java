package io.github.tidefox.unrest.display;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;

import java.util.List;

/** The "players are waiting" nudge and night watch feedback for awake players (DESIGN.md §4.2, §7). */
public final class ActionbarDisplay {

    private ActionbarDisplay() {}

    public static void nudge(List<EntityPlayerMP> counted, List<EntityPlayerMP> sleepers, boolean watch) {
        int s = sleepers.size();
        if (s == 0) {
            return;
        }
        String text = s == 1 ? "1 player is waiting for you to sleep" : s + " players are waiting for you to sleep";
        if (watch) {
            text += " §7— or keep watch: fight, mine, explore";
        }
        for (EntityPlayerMP p : counted) {
            if (!sleepers.contains(p)) {
                p.sendStatusMessage(new TextComponentString(text), true);
            }
        }
    }

    /** Tells a player what keeping watch just added: "Night watch +3% · dawn 64% · your share 12%". */
    public static void watch(EntityPlayerMP p, double added, double earned, double meter) {
        String text = "§bNight watch +" + pct(added) + "§7 · dawn " + pct(meter)
                + " · your share " + pct(earned);
        p.sendStatusMessage(new TextComponentString(text), true);
    }

    private static String pct(double v) {
        double p = v * 100;
        return (p < 1 && p > 0 ? String.format(java.util.Locale.ROOT, "%.1f", p) : Long.toString(Math.round(p))) + "%";
    }
}
