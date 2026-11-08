package io.github.tidefox.unrest;

import io.github.tidefox.unrest.command.UnrestCommand;
import io.github.tidefox.unrest.config.ConfigLoader;
import io.github.tidefox.unrest.config.QuarkCheck;
import io.github.tidefox.unrest.config.UnrestConfig;
import io.github.tidefox.unrest.core.ActivityTracker;
import io.github.tidefox.unrest.core.SleepTracker;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLServerStoppingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Server-only entry point (DESIGN.md §0, §11). */
@Mod(modid = Unrest.MODID, name = Unrest.NAME, version = Unrest.VERSION,
        acceptableRemoteVersions = "*", acceptedMinecraftVersions = "[1.12.2]")
public final class Unrest {

    public static final String MODID = "unrest";
    public static final String NAME = "Unrest";
    public static final String VERSION = "1.0.0";

    public static final Logger LOG = LogManager.getLogger(NAME);

    private static SleepTracker tracker;
    private static ActivityTracker activity;

    public static SleepTracker tracker() {
        return tracker;
    }

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent e) {
        ConfigLoader.init(e.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent e) {
        activity = new ActivityTracker();
        tracker = new SleepTracker(e.getServer(), activity);
        MinecraftForge.EVENT_BUS.register(activity);
        MinecraftForge.EVENT_BUS.register(tracker);
        e.registerServerCommand(new UnrestCommand(UnrestConfig.registerSleepAlias));
        QuarkCheck.run();
        LOG.info("Unrest started in {} mode.", UnrestConfig.mode.id());
    }

    @Mod.EventHandler
    public void serverStopping(FMLServerStoppingEvent e) {
        if (tracker != null) {
            tracker.shutdown();
            MinecraftForge.EVENT_BUS.unregister(tracker);
        }
        if (activity != null) {
            MinecraftForge.EVENT_BUS.unregister(activity);
        }
        tracker = null;
        activity = null;
    }
}
