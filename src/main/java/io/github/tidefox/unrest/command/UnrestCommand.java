package io.github.tidefox.unrest.command;

import io.github.tidefox.unrest.Unrest;
import io.github.tidefox.unrest.config.ConfigLoader;
import io.github.tidefox.unrest.config.UnrestConfig;
import io.github.tidefox.unrest.config.UnrestConfig.Mode;
import io.github.tidefox.unrest.core.Momentum;
import io.github.tidefox.unrest.core.Moon;
import io.github.tidefox.unrest.core.SleepMath;
import io.github.tidefox.unrest.core.SleepTracker;
import io.github.tidefox.unrest.display.ChatDisplay;
import io.github.tidefox.unrest.display.HeadMenu;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.WorldServer;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** {@code /unrest} and its {@code /sleep} alias (DESIGN.md §7.2). */
public final class UnrestCommand extends CommandBase {

    private static final String USAGE = "/unrest [info|reload|debt <get|set <value>>|mode <active|observe>]";

    private final List<String> aliases;

    public UnrestCommand(boolean sleepAlias) {
        this.aliases = sleepAlias ? Collections.singletonList("sleep") : Collections.<String>emptyList();
    }

    @Override
    public String getName() {
        return "unrest";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return USAGE;
    }

    @Override
    public List<String> getAliases() {
        return aliases;
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        SleepTracker tracker = Unrest.tracker();
        if (tracker == null) {
            throw new CommandException("Unrest isn't running.");
        }
        if (args.length == 0) {
            if (sender instanceof EntityPlayerMP) {
                HeadMenu.open((EntityPlayerMP) sender, tracker);
            } else {
                info(sender, tracker);
            }
            return;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info":
                info(sender, tracker);
                return;
            case "reload":
                requireOp(sender);
                ConfigLoader.reload();
                reply(sender, "Unrest config reloaded.");
                return;
            case "debt":
                requireOp(sender);
                if (args.length == 2 && "get".equalsIgnoreCase(args[1])) {
                    reply(sender, String.format("Unrest is %.2f.", tracker.data().getDebt()));
                    return;
                }
                if (args.length == 3 && "set".equalsIgnoreCase(args[1])) {
                    double v = parseDouble(args[2], 0.0, UnrestConfig.debtMax);
                    tracker.setDebt(v);
                    reply(sender, String.format("Unrest set to %.2f.", tracker.data().getDebt()));
                    return;
                }
                throw new WrongUsageException("/unrest debt <get|set <value>>");
            case "mode":
                requireOp(sender);
                if (args.length == 2) {
                    String m = args[1].toLowerCase(Locale.ROOT);
                    if ("active".equals(m) || "observe".equals(m)) {
                        ConfigLoader.setMode("active".equals(m) ? Mode.ACTIVE : Mode.OBSERVE);
                        reply(sender, "Unrest mode: " + m + ".");
                        return;
                    }
                }
                throw new WrongUsageException("/unrest mode <active|observe>");
            default:
                throw new WrongUsageException(USAGE);
        }
    }

    private static void requireOp(ICommandSender sender) throws CommandException {
        if (!sender.canUseCommand(2, "unrest")) {
            throw new CommandException("commands.generic.permission");
        }
    }

    private static void reply(ICommandSender sender, String text) {
        sender.sendMessage(new TextComponentString(text));
    }

    private static void info(ICommandSender sender, SleepTracker tracker) {
        WorldServer world = tracker.overworld();
        SleepTracker.Night night = tracker.night();
        SleepTracker.Census c = tracker.census();
        double meter = tracker.data().getMeter();
        double debt = tracker.data().getDebt();
        if (night == null) {
            sender.sendMessage(ChatDisplay.colored("No night in progress.", TextFormatting.GRAY));
        } else {
            double dawn = tracker.secondsToDawn(world);
            sender.sendMessage(ChatDisplay.colored(UnrestConfig.moon() + " " + night.type.displayName()
                    + (Double.isInfinite(dawn) ? "" : " — dawn in ~" + SleepMath.formatDuration(dawn)),
                    ChatDisplay.nightColor(night.type)));
        }
        reply(sender, "Meter: " + Math.round(meter * 100) + "%");
        int phase = Moon.phase(world);
        reply(sender, Moon.symbol(phase) + " " + Moon.name(phase) + ": dreams are " + Moon.mood(phase));
        Momentum m = tracker.momentum();
        if (m.tension() > 0 || m.chain() > 0) {
            reply(sender, "Tension " + Math.round(m.tension()) + "/" + Math.round(UnrestConfig.tensionMax)
                    + (m.chain() >= 2 ? ", Deep chain x" + m.chain() : ""));
        }
        if (tracker.reaper().active()) {
            reply(sender, "\u2620 The Reaper hunts " + tracker.reaper().targetName() + " ("
                    + SleepMath.formatDuration(tracker.reaper().secondsLeft()) + " left)");
        }
        reply(sender, String.format("Unrest: %.2f / %.1f", debt, UnrestConfig.debtMax));
        reply(sender, "Asleep: " + c.sleepers.size() + " / " + c.n()
                + " (preset " + tracker.presetName(world) + ", mode " + UnrestConfig.mode.id() + ")");
        if (!c.leavers.isEmpty()) {
            reply(sender, "Left but still counted as awake: " + String.join(", ", c.leavers));
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos pos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "info", "reload", "debt", "mode");
        }
        if (args.length == 2 && "debt".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "get", "set");
        }
        if (args.length == 2 && "mode".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, "active", "observe");
        }
        return Collections.emptyList();
    }
}
