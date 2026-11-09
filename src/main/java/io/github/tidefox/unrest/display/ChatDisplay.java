package io.github.tidefox.unrest.display;

import io.github.tidefox.unrest.config.UnrestConfig;
import io.github.tidefox.unrest.core.Dream;
import io.github.tidefox.unrest.core.DreamReport;
import io.github.tidefox.unrest.core.DreamRoller;
import io.github.tidefox.unrest.core.Momentum;
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
     * {@code 💭 🎲 91 Steve and Alex sink into a deep sleep together. [each counts 3x] ⛓ chain ×2 ⚡ The tension breaks! +16% (luck +2)}.
     * The number hovers with the bands it was rolled against; the effect hovers with each sleeper's Luck.
     * Sends nothing if the roll isn't worth announcing under {@code dreams.announce}.
     */
    public static void dreams(WorldServer world, DreamRoller.Roll roll, double r, double debt) {
        Momentum.Step st = roll.step;
        boolean big = st.released > 0 || st.chain >= 2 || st.nightTerror || st.forced;
        if (!big && !DreamReport.announce(roll.before, roll.after, UnrestConfig.dreamAnnounce)) {
            return;
        }
        List<String> names = new ArrayList<>();
        StringBuilder luckList = new StringBuilder();
        for (EntityPlayerMP p : roll.sleepers) {
            names.add(p.getName());
            luckList.append('\n').append(p.getName()).append(": luck ").append(signed(p.getLuck()));
        }
        TextFormatting color = dreamColor(roll.after);
        ITextComponent line = colored(UnrestConfig.dreamSymbol() + " ", TextFormatting.DARK_AQUA);
        ITextComponent number = colored(RollDisplay.dice() + " " + (int) roll.value, color);
        number.getStyle().setBold(true);
        number.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, bands(roll)));
        line.appendSibling(number);
        if (st.nightTerror) {
            ITextComponent terror = colored(" NIGHT TERROR!", TextFormatting.DARK_RED);
            terror.getStyle().setBold(true);
            line.appendSibling(terror);
        }
        if (st.forced) {
            line.appendSibling(colored(" The terror lifts.", TextFormatting.LIGHT_PURPLE));
        }
        String phrase = DreamReport.phrase(names, roll.before, roll.after,
                roll.woken == null ? null : roll.woken.getName());
        line.appendSibling(colored(" " + phrase + ".", color));
        String effect = roll.after == Dream.NIGHTMARE
                ? "dawn \u2212" + Math.round(nightmareLoss(roll) * 100) + "%"
                : "each counts " + fmt(roll.weight) + "x";
        ITextComponent eff = colored(" [" + effect + "]", TextFormatting.DARK_GRAY);
        eff.getStyle().setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new TextComponentString(
                roll.after.label() + " \u2014 one shared roll for every sleeper" + luckList)));
        line.appendSibling(eff);
        if (st.chain >= 2) {
            ITextComponent chain = colored(" \u26d3 chain \u00d7" + st.chain, TextFormatting.AQUA);
            chain.getStyle().setBold(true);
            line.appendSibling(chain);
        }
        if (st.released > 0) {
            ITextComponent burst = colored(" \u26a1 The tension breaks! +" + Math.round(Momentum.burst(st.released) * 100) + "%",
                    TextFormatting.GOLD);
            burst.getStyle().setBold(true);
            line.appendSibling(burst);
        } else if (st.tension > 0 && (roll.after == Dream.RESTLESS || roll.after == Dream.NIGHTMARE)) {
            line.appendSibling(colored(" \u26a1 tension " + fmt(st.tension) + "/" + fmt(UnrestConfig.tensionMax),
                    TextFormatting.YELLOW));
        }
        String tag = DreamReport.luckTag(roll.luck);
        if (tag != null) {
            line.appendSibling(colored(" (" + tag + ")", roll.luck > 0 ? TextFormatting.GREEN : TextFormatting.DARK_RED));
        }
        toOverworld(world, line);
    }

    /** Meter a Nightmare took. */
    static double nightmareLoss(DreamRoller.Roll roll) {
        return UnrestConfig.nightmareMeterLoss;
    }

    /** The hover on the rolled number: the bands, and what moved them. */
    static ITextComponent bands(DreamRoller.Roll roll) {
        double[] b = roll.bands;
        StringBuilder sb = new StringBuilder();
        sb.append("Rolled ").append((int) roll.value).append(" of 100\n\n");
        sb.append("\u00a7bDeep\u00a7r ").append((int) Math.ceil(b[2])).append("\u2013100\n");
        sb.append("\u00a77Sound\u00a7r ").append((int) Math.ceil(b[1])).append('\u2013').append((int) Math.ceil(b[2]) - 1).append('\n');
        sb.append("\u00a7eRestless\u00a7r ").append((int) Math.ceil(b[0])).append('\u2013').append((int) Math.ceil(b[1]) - 1).append('\n');
        sb.append("\u00a7cNightmare\u00a7r 0\u2013").append(Math.max(0, (int) Math.ceil(b[0]) - 1)).append("\n\n");
        sb.append("Luck ").append(signed(roll.luck));
        if (roll.rubber != 0) {
            sb.append(roll.rubber > 0 ? "\nCatching up: luck +" : "\nToo far ahead: luck ").append(fmt(roll.rubber));
        }
        if (roll.chaos != 0) {
            sb.append(roll.chaos > 0 ? "\nThe moon makes dreams wilder" : "\nThe moon calms the dreams");
        }
        return new TextComponentString(sb.toString());
    }

    /** "🎲 DOUBLE OR NOTHING in 5…" */
    public static void gambleStart(WorldServer world, int seconds, double winChance) {
        ITextComponent msg = colored(RollDisplay.dice() + " DOUBLE OR NOTHING in " + seconds + "\u2026", TextFormatting.GOLD);
        msg.getStyle().setBold(true);
        msg.appendSibling(colored(" (win " + Math.round(winChance * 100) + "%: dawn jumps to "
                + Math.round(UnrestConfig.gambleWinMeter * 100) + "%, lose: back to "
                + Math.round(UnrestConfig.gambleLoseMeter * 100) + "% but the ramp doubles)", TextFormatting.GRAY));
        toOverworld(world, msg);
    }

    public static void gambleResult(WorldServer world, boolean win, int value, int need) {
        ITextComponent msg = colored(RollDisplay.dice() + " " + value + " (needed " + need + "+) \u2014 ",
                TextFormatting.GOLD);
        ITextComponent res = win
                ? colored("DOUBLE! The dream surges to " + Math.round(UnrestConfig.gambleWinMeter * 100) + "%.", TextFormatting.GREEN)
                : colored("NOTHING. The dream collapses, but the ramp doubles.", TextFormatting.RED);
        res.getStyle().setBold(true);
        msg.appendSibling(res);
        toOverworld(world, msg);
    }

    public static void reaperSpawned(WorldServer world, String dreamer, String target, int seconds) {
        ITextComponent msg = colored("\u2620 " + dreamer + "'s nightmare breaks loose: ", TextFormatting.DARK_RED);
        ITextComponent hunt = colored("the Reaper hunts " + target + "!", TextFormatting.RED);
        hunt.getStyle().setBold(true);
        msg.appendSibling(hunt);
        msg.appendSibling(colored(" Kill it within " + seconds + "s: dawn +" + Math.round(UnrestConfig.reaperReward * 100)
                + "%.", TextFormatting.GRAY));
        toOverworld(world, msg);
    }

    public static void reaperSlain(WorldServer world, String killer, double reward) {
        ITextComponent msg = colored("\u2620 " + killer + " slays the Reaper! ", TextFormatting.GOLD);
        msg.getStyle().setBold(true);
        msg.appendSibling(colored("Dawn +" + Math.round(reward * 100) + "%.", TextFormatting.GREEN));
        toOverworld(world, msg);
    }

    public static void reaperFaded(WorldServer world, double penalty) {
        toOverworld(world, colored("\u2620 The Reaper fades into the dark, unslain. Dawn \u2212"
                + Math.round(penalty * 100) + "%.", TextFormatting.DARK_GRAY));
    }

    /** The Fever Dream is always announced, with the moon. */
    public static void feverDream(WorldServer world, String moon) {
        ITextComponent msg = colored("\u2727 " + NightType.FEVER.announcement(), TextFormatting.LIGHT_PURPLE);
        msg.getStyle().setBold(true);
        msg.appendSibling(colored(" Dreams come fast and wild tonight. (" + moon + ")", TextFormatting.DARK_PURPLE));
        toOverworld(world, msg);
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
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
            case FEVER:
                return TextFormatting.LIGHT_PURPLE;
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
