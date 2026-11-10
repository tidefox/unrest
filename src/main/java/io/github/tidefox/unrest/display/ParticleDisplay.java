package io.github.tidefox.unrest.display;

import io.github.tidefox.unrest.config.UnrestConfig;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.network.play.server.SPacketSoundEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.WorldServer;

import java.util.List;
import java.util.Random;

/** Cloud puffs above sleepers, the meter chimes and the nightmare sound (DESIGN.md §7). */
public final class ParticleDisplay {

    private ParticleDisplay() {}

    public static void puffs(WorldServer world, List<EntityPlayerMP> sleepers) {
        for (EntityPlayerMP p : sleepers) {
            world.spawnParticle(EnumParticleTypes.CLOUD, p.posX, p.posY + 0.7, p.posZ, 2, 0.15, 0.05, 0.15, 0.005);
        }
    }

    /** A quiet, unsettling sound for the one player woken by a Nightmare. */
    public static void nightmare(EntityPlayerMP p) {
        p.connection.sendPacket(new SPacketSoundEffect(SoundEvents.ENTITY_ZOMBIE_VILLAGER_CONVERTED,
                SoundCategory.PLAYERS, p.posX, p.posY, p.posZ, 0.5f, 0.6f));
    }

    public static void chime(WorldServer world, float pitch) {
        for (EntityPlayer p : world.playerEntities) {
            if (p instanceof EntityPlayerMP) {
                ((EntityPlayerMP) p).connection.sendPacket(new SPacketSoundEffect(SoundEvents.BLOCK_NOTE_CHIME,
                        SoundCategory.PLAYERS, p.posX, p.posY, p.posZ, 0.6f, pitch));
            }
        }
    }

    /** Harmless lightning (effect only: no fire, no damage) at a spot. */
    public static void lightning(WorldServer world, double x, double y, double z) {
        if (UnrestConfig.lightning) {
            world.addWeatherEffect(new EntityLightningBolt(world, x, y, z, true));
        }
    }

    /** Harmless lightning a few blocks from each of up to three players. */
    public static void lightningNear(WorldServer world, List<EntityPlayerMP> players, Random rand) {
        if (!UnrestConfig.lightning) {
            return;
        }
        int n = 0;
        for (EntityPlayerMP p : players) {
            if (n++ >= 3) {
                break;
            }
            double a = rand.nextDouble() * Math.PI * 2;
            double d = 6 + rand.nextDouble() * 6;
            lightning(world, p.posX + Math.cos(a) * d, p.posY, p.posZ + Math.sin(a) * d);
        }
    }

    /** A puff of smoke where the Reaper fades. */
    public static void fade(WorldServer world, double x, double y, double z) {
        world.spawnParticle(EnumParticleTypes.SMOKE_LARGE, x, y + 1, z, 30, 0.4, 0.8, 0.4, 0.02);
    }
}
