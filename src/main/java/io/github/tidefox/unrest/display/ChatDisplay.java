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

    /**
     * One coloured line for the shared dream roll, e.g.
     * {@code 💭 Steve and Alex sink into a deep sleep together. [every sleeper counts 2x] (luck +2)}.
     * The symbol hovers with the odds; the effect hovers with who rolled and their Luck.
     * Sends nothing if the roll isn't worth announcing under {@code dreams.announce}.
     */
    public static void dreams(WorldServer world, DreamRoller.Roll roll, double r, double debt) {
        if (!DreamReport.announce(roll.before, roll.after, UnrestConfig.dreamAnnounce)) {
            return;
        }
        List<String> names = new ArrayList<>();
        StringBuilder luckList = new StringBuilder();
        for (EntityPlayerMP p : roll.sleepers) {
            names.add(p.getName());
            luckList.append('\n').append(p.getName()).append(": luck ").append(signed(p.getLuck()));
        }
        ITextComponent line = colored(UnrestConfig.dreamSymbol(), TextFormatting.DARK_AQUA);
        line.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, odds(r, debt, roll.luck)));
        String phrase = DreamReport.phrase(names, roll.before, roll.after,
                roll.woken == null ? null : roll.woken.getName());
        line.appendSibling(colored(" " + phrase + ".", dreamColor(roll.after)));
        ITextComponent effect = colored(" [" + roll.after.effect() + "]", TextFormatting.DARK_GRAY);
        effect.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(
                roll.after.label() + " \u2014 one shared roll for every sleeper" + luckList)));
        line.appendSibling(effect);
        String tag = DreamReport.luckTag(roll.luck);
        if (tag != null) {
            line.appendSibling(colored(" (" + tag + ")", roll.luck > 0 ? TextFormatting.GREEN : TextFormatting.DARK_RED));
        }
        toOverworld(world, line);
    }

    static ITextComponent odds(double r, double debt, double luck) {
        int[] o = DreamReport.oddsPercent(r, debt, luck);
        String l = DreamReport.luckTag(luck);
        return new TextComponentString("Odds of the next roll right now" + (l == null ? "" : " (" + l + ")")
                + ":\nDeep " + o[2] + "%\nSound " + o[3] + "%\nRestless " + o[1] + "%\nNightmare " + o[0] + "%"
                + "\n\nMore sleepers and more Luck mean better dreams.");
    }

    private static String signed(double v) {
        long l = Math.round(v);
        return l > 0 ? "+" + l : Long.toString(l);
    }

    public static TextFormatting dreamColor(Dream d) {
        switch (d) {
            case DEEP:
                return TextFormatting.AQUA;
            case RESTLESS:
                return TextFormatting.YELLOW;
            case NIGHTMARE:
                return TextFormatting.RED;
            default:
                return TextFormatting.GRAY;
        }
    }

    public static void skipSummary(WorldServer world, int s, int n, NightType type, int seconds,
                                   int deep, int restless, int nightmares, List<String> watch) {
        String text = UnrestConfig.sun() + " The night passes. (" + s + "/" + n + " asleep, "
                + type.displayName() + ", " + SleepMath.formatDuration(seconds) + ")";
        ITextComponent msg = colored(text, TextFormatting.GOLD);
        StringBuilder hover = new StringBuilder();
        if (UnrestConfig.dreamsEnabled) {
            hover.append("Dreams tonight:\nDeep ").append(deep).append("\nRestless ").append(restless)
                    .append("\nNightmares ").append(nightmares);
        }
        if (!watch.isEmpty()) {
            if (hover.length() > 0) {
                hover.append("\n\n");
            }
            hover.append("Night watch:\n").append(String.join("\n", watch));
        }
        if (hover.length() > 0) {
            msg.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    new TextComponentString(hover.toString())));
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
        if (!census.leavers.isEmpty()) {
            text += "\nLeft, still counted as awake: " + String.join(", ", census.leavers);
        }
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
