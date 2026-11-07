package io.github.tidefox.unrest.display;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;

import java.util.List;

/** The "players are waiting" nudge for awake players (DESIGN.md §7). */
public final class ActionbarDisplay {

    private ActionbarDisplay() {}

    public static void nudge(List<EntityPlayerMP> counted, List<EntityPlayerMP> sleepers) {
        int s = sleepers.size();
        if (s == 0) {
            return;
        }
        String text = s == 1 ? "1 player is waiting for you to sleep" : s + " players are waiting for you to sleep";
        for (EntityPlayerMP p : counted) {
            if (!sleepers.contains(p)) {
                p.sendStatusMessage(new TextComponentString(text), true);
            }
        }
    }
}
