package io.github.tidefox.unrest.display;

import io.github.tidefox.unrest.config.UnrestConfig;
import io.github.tidefox.unrest.core.Dream;
import io.github.tidefox.unrest.core.DreamReport;
import io.github.tidefox.unrest.core.DreamRoller;
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

    /**
     * One line for a dream roll, e.g. {@code 💭 Steve sinks into a deep sleep. Alex tosses and turns.}
     * Each name hovers with what the dream does; the symbol hovers with the odds of the roll.
     * Sends nothing if no roll is worth announcing under {@code dreams.announce}.
     */
    public static void dreams(WorldServer world, List<DreamRoller.Roll> rolls, double r, double debt) {
        ITextComponent line = null;
        for (DreamRoller.Roll roll : rolls) {
            if (!DreamReport.announce(roll.before, roll.after, UnrestConfig.dreamAnnounce)) {
                continue;
            }
            if (line == null) {
                line = colored(UnrestConfig.dreamSymbol(), TextFormatting.DARK_AQUA);
                line.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, odds(r, debt)));
            }
            ITextComponent part = colored(" " + DreamReport.phrase(roll.player.getName(), roll.before, roll.after) + ".",
                    dreamColor(roll.after));
            part.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new TextComponentString(roll.after.label() + "\n" + roll.after.effect())));
            line.appendSibling(part);
        }
        if (line != null) {
            toOverworld(world, line);
        }
    }

    static ITextComponent odds(double r, double debt) {
        int[] o = DreamReport.oddsPercent(r, debt);
        return new TextComponentString("Odds each roll right now:\nDeep " + o[2] + "%\nSound " + o[3]
                + "%\nRestless " + o[1] + "%\nNightmare " + o[0] + "%");
    }

    public static TextFormatting dreamColor(Dream d) {
        switch (d) {
            case DEEP:
                return TextFormatting.AQUA;
            case RESTLESS:
                return TextFormatting.YELLOW;
            case NIGHTMARE:
                return TextFormatting.DARK_PURPLE;
            default:
                return TextFormatting.GRAY;
        }
    }

    public static void skipSummary(WorldServer world, int s, int n, NightType type, int seconds,
                                   int deep, int restless, int nightmares) {
        String text = UnrestConfig.sun() + " The night passes. (" + s + "/" + n + " asleep, "
                + type.displayName() + ", " + SleepMath.formatDuration(seconds) + ")";
        ITextComponent msg = colored(text, TextFormatting.GOLD);
        if (UnrestConfig.dreamsEnabled) {
            msg.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(
                    "Dreams tonight:\nDeep " + deep + "\nRestless " + restless + "\nNightmares " + nightmares)));
        }
        toOverworld(world, msg);
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
