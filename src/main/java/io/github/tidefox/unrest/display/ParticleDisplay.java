package io.github.tidefox.unrest.display;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.SoundEvents;
import net.minecraft.network.play.server.SPacketSoundEffect;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.SoundCategory;
import net.minecraft.world.WorldServer;

import java.util.List;

/** Cloud puffs above sleepers and the meter chimes (DESIGN.md §7). */
public final class ParticleDisplay {

    private ParticleDisplay() {}

    public static void puffs(WorldServer world, List<EntityPlayerMP> sleepers) {
        for (EntityPlayerMP p : sleepers) {
            world.spawnParticle(EnumParticleTypes.CLOUD, p.posX, p.posY + 0.7, p.posZ, 2, 0.15, 0.05, 0.15, 0.005);
        }
    }

    public static void chime(WorldServer world, float pitch) {
        for (EntityPlayer p : world.playerEntities) {
            if (p instanceof EntityPlayerMP) {
                ((EntityPlayerMP) p).connection.sendPacket(new SPacketSoundEffect(SoundEvents.BLOCK_NOTE_CHIME,
                        SoundCategory.PLAYERS, p.posX, p.posY, p.posZ, 0.6f, pitch));
            }
        }
    }
}
