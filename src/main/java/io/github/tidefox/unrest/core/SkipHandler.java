package io.github.tidefox.unrest.core;

import io.github.tidefox.unrest.config.UnrestConfig;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketTimeUpdate;
import net.minecraft.util.FoodStats;
import net.minecraft.world.WorldServer;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** The skip itself: time jump, weather, waking, hunger cost (DESIGN.md §2.4, §5). */
public final class SkipHandler {

    private SkipHandler() {}

    /**
     * Jumps to the next morning, clears the weather and wakes everyone in bed.
     * Returns the ticks skipped. Like vanilla, the time is left alone when
     * {@code doDaylightCycle} is off; the sleepers are still woken.
     */
    public static long jumpToMorning(WorldServer world) {
        long t = world.getWorldTime();
        long jump = 0;
        if (world.getGameRules().getBoolean("doDaylightCycle")) {
            jump = 24000L - (t % 24000L);
            world.setWorldTime(t + jump);
        }
        world.provider.resetRainAndThunder();
        for (EntityPlayer p : new ArrayList<>(world.playerEntities)) {
            if (p.isPlayerSleeping()) {
                p.wakeUpPlayer(false, false, true);
            }
        }
        world.updateAllPlayersSleepingFlag();
        // The time is shared by every dimension; tell clients now rather than at the next sync.
        world.getMinecraftServer().getPlayerList().sendPacketToAllPlayers(new SPacketTimeUpdate(
                world.getTotalWorldTime(), world.getWorldTime(), world.getGameRules().getBoolean("doDaylightCycle")));
        return jump;
    }

    /** Applies the hunger cost to each sleeper and returns the total food points lost. */
    public static int applyHunger(List<EntityPlayerMP> sleepers, long ticksSkipped, Random rand) {
        if (!UnrestConfig.hungerEnabled) {
            return 0;
        }
        int total = 0;
        for (EntityPlayerMP p : sleepers) {
            FoodStats food = p.getFoodStats();
            int before = food.getFoodLevel();
            int after = SleepMath.applyHunger(before, SleepMath.hungerLoss(ticksSkipped, rand));
            if (after != before) {
                food.setFoodLevel(after);
                total += before - after;
            }
        }
        return total;
    }
}
