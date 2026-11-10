package io.github.tidefox.unrest.core;

import io.github.tidefox.unrest.config.UnrestConfig;
import net.minecraft.world.World;

/**
 * The vanilla moon phase and what it does to the night's dreams (DESIGN.md §3.3).
 * Phase 0 is the full moon, 4 the new moon, as {@link #phase(World)} returns.
 */
public final class Moon {

    private static final String[] NAMES = {
        "Full Moon", "Waning Gibbous", "Last Quarter", "Waning Crescent",
        "New Moon", "Waxing Crescent", "First Quarter", "Waxing Gibbous"
    };
    private static final String[] SYMBOLS = {"🌕", "🌖", "🌗", "🌘", "🌑", "🌒", "🌓", "🌔"};
    /** +1 wild (full moon) to −1 calm (new moon). */
    private static final double[] CHAOS = {1.0, 0.5, 0.0, -0.5, -1.0, -0.5, 0.0, 0.5};

    private Moon() {}

    /**
     * The world's moon phase, 0 to 7.
     *
     * <p>Use this instead of {@code World.getMoonPhase()}: that method is
     * client-only and is missing from the dedicated server jar, so calling it
     * there throws {@code NoSuchMethodError}. The provider's method is what
     * vanilla uses on both sides.
     */
    public static int phase(World world) {
        return world.provider.getMoonPhase(world.getWorldTime());
    }

    private static int index(int phase) {
        return ((phase % 8) + 8) % 8;
    }

    /** How wild the dreams are: +1 on a full moon, −1 on a new moon, 0 when the moon is off. */
    public static double chaos(int phase) {
        return UnrestConfig.moonEnabled ? CHAOS[index(phase)] : 0.0;
    }

    public static String name(int phase) {
        return NAMES[index(phase)];
    }

    public static String symbol(int phase) {
        return UnrestConfig.asciiOnly || !UnrestConfig.moonEnabled ? UnrestConfig.moon() : SYMBOLS[index(phase)];
    }

    /** "wild", "restless", "steady", "quiet" or "calm". */
    public static String mood(int phase) {
        double c = chaos(phase);
        if (c >= 1) {
            return "wild";
        }
        if (c > 0) {
            return "restless";
        }
        if (c == 0) {
            return "steady";
        }
        return c <= -1 ? "calm" : "quiet";
    }
}
