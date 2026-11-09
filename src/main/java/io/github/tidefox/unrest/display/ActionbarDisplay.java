package io.github.tidefox.unrest.display;

import io.github.tidefox.unrest.config.UnrestConfig;
import io.github.tidefox.unrest.core.SleepMath;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;

import java.util.List;
import java.util.Locale;

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

    /**
     * Tells a player what keeping watch just took off the wait:
     * "Night watch −6s (+3%) · dawn in 0:42 · your watch 12/20%".
     * {@code saved} is NaN when the meter isn't filling, and only the meter share is shown.
     */
    public static void watch(EntityPlayerMP p, double added, double saved, double earned, double dawn) {
        StringBuilder sb = new StringBuilder("§bNight watch ");
        if (!Double.isNaN(saved) && !Double.isInfinite(saved)) {
            sb.append('−').append(Math.max(1, Math.round(saved))).append("s §3(+").append(pct(added)).append(')');
        } else {
            sb.append('+').append(pct(added));
        }
        if (!Double.isInfinite(dawn)) {
            sb.append("§7 · dawn in ").append(SleepMath.formatDuration(dawn));
        }
        sb.append("§7 · your watch ").append(Math.round(earned * 100)).append('/')
                .append(Math.round(UnrestConfig.watchMaxPerPlayer * 100)).append('%');
        p.sendStatusMessage(new TextComponentString(sb.toString()), true);
    }

    private static String pct(double v) {
        double p = v * 100;
        return (p < 1 && p > 0 ? String.format(Locale.ROOT, "%.1f", p) : Long.toString(Math.round(p))) + "%";
    }
}
