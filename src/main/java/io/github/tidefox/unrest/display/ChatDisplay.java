package io.github.tidefox.unrest.display;

import io.github.tidefox.unrest.config.UnrestConfig;
import io.github.tidefox.unrest.core.NightType;
import io.github.tidefox.unrest.core.SleepMath;
import io.github.tidefox.unrest.core.SleepTracker;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.event.HoverEvent;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.List;

/** Chat messages to everyone in the Overworld (DESIGN.md §7). */
public final class ChatDisplay {

    private ChatDisplay() {}

    public static void toOverworld(WorldServer world, ITextComponent msg) {
        for (EntityPlayer p : new ArrayList<>(world.playerEntities)) {
            p.sendMessage(msg);
        }
    }

    public static void announceNight(WorldServer world, NightType type) {
        toOverworld(world, colored(type.announcement(), nightColor(type)));
    }

    public static void liesDown(WorldServer world, EntityPlayerMP p, int s, int n,
                                SleepTracker.Census census, double secondsToDawn) {
        StringBuilder sb = new StringBuilder();
        sb.append(p.getName()).append(" lies down (").append(s).append('/').append(n).append(')');
        if (!Double.isInfinite(secondsToDawn)) {
            sb.append(" — dawn in ~").append(SleepMath.formatDuration(secondsToDawn));
        }
        ITextComponent msg = colored(sb.toString(), TextFormatting.GRAY);
        msg.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, whoIsAsleep(census)));
        toOverworld(world, msg);
    }

    public static void getsUp(WorldServer world, EntityPlayerMP p, int s, int n) {
        toOverworld(world, colored(p.getName() + " gets up (" + s + "/" + n + ")", TextFormatting.GRAY));
    }

    public static void nightmare(WorldServer world, EntityPlayerMP p) {
        toOverworld(world, colored(p.getName() + " wakes up in a cold sweat.", TextFormatting.DARK_PURPLE));
    }

    public static void skipSummary(WorldServer world, int s, int n, NightType type, int seconds) {
        String text = UnrestConfig.sun() + " The night passes. (" + s + "/" + n + " asleep, "
                + type.displayName() + ", " + SleepMath.formatDuration(seconds) + ")";
        toOverworld(world, colored(text, TextFormatting.GOLD));
    }

    static ITextComponent whoIsAsleep(SleepTracker.Census census) {
        List<String> asleep = new ArrayList<>();
        List<String> awake = new ArrayList<>();
        for (EntityPlayerMP p : census.counted) {
            (census.sleepers.contains(p) ? asleep : awake).add(p.getName());
        }
        String text = "Asleep: " + (asleep.isEmpty() ? "nobody" : String.join(", ", asleep))
                + "\nAwake: " + (awake.isEmpty() ? "nobody" : String.join(", ", awake));
        return new TextComponentString(text);
    }

    /** Up to {@code max} names, then "+N". */
    public static String names(List<EntityPlayerMP> players, int max) {
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(max, players.size());
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(players.get(i).getName());
        }
        int rest = players.size() - shown;
        if (rest > 0) {
            sb.append(shown > 0 ? " +" : "+").append(rest);
        }
        return sb.toString();
    }

    public static TextFormatting nightColor(NightType type) {
        switch (type) {
            case CALM:
                return TextFormatting.GREEN;
            case RESTLESS:
                return TextFormatting.YELLOW;
            case HAUNTED:
                return TextFormatting.RED;
            default:
                return TextFormatting.BLUE;
        }
    }

    public static ITextComponent colored(String text, TextFormatting color) {
        ITextComponent c = new TextComponentString(text);
        c.getStyle().setColor(color);
        return c;
    }
}
