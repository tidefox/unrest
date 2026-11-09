package io.github.tidefox.unrest.core;

import io.github.tidefox.unrest.config.UnrestConfig;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Last-activity time per player, AFK detection and the active-before-bed rule
 * (DESIGN.md §6). Kept in memory only.
 */
public final class ActivityTracker {

    private final Map<UUID, Long> lastActive = new HashMap<>();
    private final Map<UUID, float[]> lastRotation = new HashMap<>();
    private final Map<UUID, Long> bedEntry = new HashMap<>();
    private final Map<UUID, Boolean> activeBeforeBed = new HashMap<>();

    private static long now() {
        return System.currentTimeMillis();
    }

    public void markActive(EntityPlayer player) {
        if (player instanceof EntityPlayerMP && !player.world.isRemote) {
            lastActive.put(player.getUniqueID(), now());
        }
    }

    /**
     * Called once per second for each online player. Looking around counts as
     * activity, except while lying in bed.
     */
    public void update(EntityPlayerMP player) {
        UUID id = player.getUniqueID();
        float[] rot = {player.rotationYaw, player.rotationPitch};
        float[] prev = lastRotation.put(id, rot);
        boolean inBed = player.isPlayerSleeping();
        if (prev != null && !inBed && !bedEntry.containsKey(id) && (prev[0] != rot[0] || prev[1] != rot[1])) {
            lastActive.put(id, now());
        }
        if (!lastActive.containsKey(id)) {
            lastActive.put(id, now());
        }
        if (inBed) {
            if (!bedEntry.containsKey(id)) {
                long t = now();
                bedEntry.put(id, t);
                long idle = t - lastActive.get(id);
                activeBeforeBed.put(id, idle <= UnrestConfig.activeBeforeBedSeconds * 1000L);
            }
        } else {
            bedEntry.remove(id);
            activeBeforeBed.remove(id);
        }
    }

    /** AFK players aren't counted. Players in bed are never AFK. */
    public boolean isAfk(EntityPlayerMP player) {
        if (player.isPlayerSleeping()) {
            return false;
        }
        return idleMillis(player) > UnrestConfig.afkTimeoutSeconds * 1000L;
    }

    public long idleMillis(EntityPlayerMP player) {
        Long t = lastActive.get(player.getUniqueID());
        return t == null ? 0 : Math.max(0, now() - t);
    }

    /** When the player was last active, in epoch millis. */
    public long lastActiveMillis(EntityPlayerMP player) {
        Long t = lastActive.get(player.getUniqueID());
        return t == null ? now() : t;
    }

    /** Whether the player was active shortly before getting into their current bed. */
    public boolean wasActiveBeforeBed(EntityPlayerMP player) {
        Boolean b = activeBeforeBed.get(player.getUniqueID());
        return b == null || b;
    }

    /** Milliseconds since the player got into bed, or 0. */
    public long millisInBed(EntityPlayerMP player) {
        Long t = bedEntry.get(player.getUniqueID());
        return t == null ? 0 : Math.max(0, now() - t);
    }

    public void forget(UUID id) {
        lastActive.remove(id);
        lastRotation.remove(id);
        bedEntry.remove(id);
        activeBeforeBed.remove(id);
    }

    // ---- activity events ----

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        // Clicking a bed is how you get into it, so it must not count as being
        // active before bed, or the rule would never stop anyone.
        IBlockState state = e.getWorld().getBlockState(e.getPos());
        if (state.getBlock().isBed(state, e.getWorld(), e.getPos(), e.getEntityPlayer())) {
            return;
        }
        markActive(e.getEntityPlayer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        markActive(e.getEntityPlayer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock e) {
        markActive(e.getEntityPlayer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onEntityInteract(PlayerInteractEvent.EntityInteract e) {
        markActive(e.getEntityPlayer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onAttack(AttackEntityEvent e) {
        markActive(e.getEntityPlayer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onBreak(BlockEvent.BreakEvent e) {
        markActive(e.getPlayer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onChat(ServerChatEvent e) {
        markActive(e.getPlayer());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onCommand(CommandEvent e) {
        if (e.getSender() instanceof EntityPlayerMP) {
            markActive((EntityPlayerMP) e.getSender());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onContainerOpen(PlayerContainerEvent.Open e) {
        markActive(e.getEntityPlayer());
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        markActive(e.player);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        forget(e.player.getUniqueID());
    }
}
