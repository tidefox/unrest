package io.github.tidefox.unrest.display;

import io.github.tidefox.unrest.Unrest;
import io.github.tidefox.unrest.config.UnrestConfig;
import io.github.tidefox.unrest.core.SleepTracker;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** The [Zz] tag before sleepers' names in the tab list (DESIGN.md §7). */
public final class TabListDisplay {

    @SubscribeEvent
    public void onTabListName(PlayerEvent.TabListNameFormat e) {
        SleepTracker tracker = Unrest.tracker();
        EntityPlayer p = e.getEntityPlayer();
        if (tracker == null || !UnrestConfig.tabList || !tracker.isShownAsSleeper(p.getUniqueID())) {
            return;
        }
        ITextComponent name = e.getDisplayName() != null
                ? e.getDisplayName()
                : new TextComponentString(ScorePlayerTeam.formatPlayerName(p.getTeam(), p.getName()));
        String prefix = UnrestConfig.tabPrefix.isEmpty() ? "" : UnrestConfig.tabPrefix + " ";
        e.setDisplayName(new TextComponentString(prefix).appendSibling(name));
    }

    public static void refresh(EntityPlayerMP p) {
        p.refreshTabListName();
    }
}
