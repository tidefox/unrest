package io.github.tidefox.unrest.display;

import io.github.tidefox.unrest.config.UnrestConfig;
import io.github.tidefox.unrest.core.Dream;
import io.github.tidefox.unrest.core.DreamRoller;
import io.github.tidefox.unrest.core.SleepMath;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.network.play.server.SPacketSoundEffect;
import net.minecraft.network.play.server.SPacketTitle;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The gambling show around each shared roll (DESIGN.md §4.1): a second of
 * spinning numbers as a title, then the rolled number and what it means, with
 * sounds. Also the double-or-nothing countdown and flip.
 */
public final class RollDisplay {

    private static final int SPIN_TICKS = 20;

    private final Random rand = new Random();
    private List<EntityPlayerMP> viewers = new ArrayList<>();
    private int ticks;

    public static String dice() {
        return UnrestConfig.asciiOnly ? "#" : "🎲";
    }

    /** Starts the spin a second before the roll. */
    public void start(List<EntityPlayerMP> viewers) {
        this.viewers = new ArrayList<>(viewers);
        ticks = viewers.isEmpty() ? 0 : SPIN_TICKS;
    }

    /** Called every tick: flickers a random number every other tick, with a rising click. */
    public void tick() {
        if (ticks <= 0) {
            return;
        }
        ticks--;
        if (ticks % 2 != 0) {
            return;
        }
        int n = rand.nextInt(100);
        float pitch = 0.6f + 1.2f * (SPIN_TICKS - ticks) / SPIN_TICKS;
        for (EntityPlayerMP p : viewers) {
            if (p.hasDisconnected()) {
                continue;
            }
            title(p, "§e" + dice() + " §f" + n, "§8rolling the dream…", 0, 6, 0);
            sound(p, SoundEvents.BLOCK_NOTE_HAT, 0.4f, pitch);
        }
    }

    /** Shows the rolled number and what it means. */
    public void reveal(List<EntityPlayerMP> viewers, DreamRoller.Roll roll) {
        ticks = 0;
        String color = color(roll.after);
        String title = color + dice() + " " + (int) roll.value;
        StringBuilder sub = new StringBuilder(color).append(label(roll));
        if (roll.step.chain >= 2) {
            sub.append(" §b§l⛓ ×").append(roll.step.chain);
        }
        if (roll.step.released > 0) {
            sub.append(" §6§l⚡ +").append(Math.round(io.github.tidefox.unrest.core.Momentum.burst(roll.step.released) * 100)).append('%');
        }
        if (!roll.step.forced && !roll.step.nightTerror && SleepMath.nearMiss(roll.value, roll.bands) <= 3) {
            sub.append(" §7§o(so close!)");
        }
        for (EntityPlayerMP p : viewers) {
            if (p.hasDisconnected()) {
                continue;
            }
            title(p, title, sub.toString(), 0, 30, 10);
            revealSound(p, roll);
        }
    }

    private static String label(DreamRoller.Roll roll) {
        if (roll.step.forced) {
            return "THE TERROR LIFTS — DEEP SLEEP";
        }
        if (roll.step.nightTerror) {
            return "§l NIGHT TERROR";
        }
        switch (roll.after) {
            case DEEP:
                return "DEEP SLEEP";
            case RESTLESS:
                return "RESTLESS";
            case NIGHTMARE:
                return "NIGHTMARE";
            default:
                return "SOUND SLEEP";
        }
    }

    private static void revealSound(EntityPlayerMP p, DreamRoller.Roll roll) {
        switch (roll.after) {
            case DEEP:
                sound(p, roll.step.chain >= 2 || roll.step.released > 0
                        ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.ENTITY_PLAYER_LEVELUP, 0.5f, 1.0f);
                break;
            case RESTLESS:
                sound(p, SoundEvents.BLOCK_NOTE_BASS, 0.6f, 0.5f);
                break;
            case NIGHTMARE:
                sound(p, SoundEvents.ENTITY_WITHER_AMBIENT, 0.4f, 0.6f);
                break;
            default:
                sound(p, SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 0.4f, 1.2f);
        }
    }

    /** "DOUBLE OR NOTHING" with the seconds left. */
    public static void gambleCountdown(List<EntityPlayerMP> viewers, int secondsLeft) {
        for (EntityPlayerMP p : viewers) {
            title(p, "§6§l" + dice() + " DOUBLE OR NOTHING", "§e" + secondsLeft + "…", 0, 25, 0);
            sound(p, SoundEvents.BLOCK_NOTE_PLING, 0.5f, 0.8f + 0.1f * (5 - Math.min(5, secondsLeft)));
        }
    }

    /** The flip: the number rolled against the number needed. */
    public static void gambleResult(List<EntityPlayerMP> viewers, boolean win, int value, int need) {
        String title = win ? "§a§l" + dice() + " " + value + " — DOUBLE!" : "§c§l" + dice() + " " + value + " — NOTHING";
        String sub = "§7needed " + need + "+" + (win ? "  §adawn surges ahead" : "  §cbut the ramp doubles");
        for (EntityPlayerMP p : viewers) {
            title(p, title, sub, 0, 40, 15);
            sound(p, win ? SoundEvents.UI_TOAST_CHALLENGE_COMPLETE : SoundEvents.ENTITY_ITEM_BREAK, 0.6f, win ? 1.0f : 0.6f);
        }
    }

    static String color(Dream d) {
        switch (d) {
            case DEEP:
                return "§b";
            case RESTLESS:
                return "§e";
            case NIGHTMARE:
                return "§c";
            default:
                return "§7";
        }
    }

    public static void title(EntityPlayerMP p, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        p.connection.sendPacket(new SPacketTitle(fadeIn, stay, fadeOut));
        p.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.SUBTITLE, new TextComponentString(subtitle)));
        p.connection.sendPacket(new SPacketTitle(SPacketTitle.Type.TITLE, new TextComponentString(title)));
    }

    static void sound(EntityPlayerMP p, SoundEvent sound, float volume, float pitch) {
        if (!UnrestConfig.sounds) {
            return;
        }
        p.connection.sendPacket(new SPacketSoundEffect(sound, SoundCategory.PLAYERS, p.posX, p.posY, p.posZ, volume, pitch));
    }
}
