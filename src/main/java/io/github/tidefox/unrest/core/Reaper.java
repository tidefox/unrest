package io.github.tidefox.unrest.core;

import io.github.tidefox.unrest.config.UnrestConfig;
import io.github.tidefox.unrest.display.ParticleDisplay;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.monster.EntityWitherSkeleton;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.init.MobEffects;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldServer;

import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * The Reaper (DESIGN.md §4.4): a Nightmare sends a wither skeleton with a hoe
 * after an awake player. Killing it in time brings the dawn closer; if it
 * fades unkilled, the dawn slips back. Only one hunts at a time.
 */
public final class Reaper {

    /** Scoreboard tag on the entity, so a leftover from a restart can be removed. */
    public static final String TAG = "unrest_reaper";

    private EntityWitherSkeleton entity;
    private UUID id;
    private EntityPlayerMP target;
    private int secondsLeft;

    public boolean active() {
        return entity != null;
    }

    public String targetName() {
        return target == null ? "" : target.getName();
    }

    public int secondsLeft() {
        return secondsLeft;
    }

    /** Whether {@code e} is the Reaper that is hunting right now. */
    public boolean isOurs(Entity e) {
        return id != null && id.equals(e.getUniqueID());
    }

    /**
     * Sends the Reaper after a random one of {@code awake}. Returns the player
     * it hunts, or null if it couldn't spawn (peaceful, no room near anyone).
     */
    public EntityPlayerMP spawn(WorldServer world, List<EntityPlayerMP> awake, Random rand) {
        if (active() || awake.isEmpty() || world.getDifficulty() == EnumDifficulty.PEACEFUL) {
            return null;
        }
        EntityPlayerMP p = awake.get(rand.nextInt(awake.size()));
        EntityWitherSkeleton e = new EntityWitherSkeleton(world);
        BlockPos pos = findSpot(world, p, e, rand);
        if (pos == null) {
            return null;
        }
        int ticks = UnrestConfig.reaperSeconds * 20;
        e.onInitialSpawn(world.getDifficultyForLocation(pos), null);
        e.setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.IRON_HOE));
        e.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(Items.SKULL, 1, 1));
        e.setDropChance(EntityEquipmentSlot.MAINHAND, 0f);
        e.setDropChance(EntityEquipmentSlot.HEAD, 0f);
        e.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(UnrestConfig.reaperHealth);
        e.setHealth((float) UnrestConfig.reaperHealth);
        e.setCustomNameTag("§4☠ The Reaper");
        e.setAlwaysRenderNameTag(true);
        e.addPotionEffect(new PotionEffect(MobEffects.GLOWING, ticks, 0, false, false));
        e.addPotionEffect(new PotionEffect(MobEffects.SPEED, ticks, 0, false, false));
        e.addTag(TAG);
        entity = e;
        id = e.getUniqueID();
        target = p;
        secondsLeft = UnrestConfig.reaperSeconds;
        if (!world.spawnEntity(e)) {
            clear();
            return null;
        }
        e.setAttackTarget(p);
        ParticleDisplay.lightning(world, e.posX, e.posY, e.posZ);
        return p;
    }

    /** Somewhere 8–14 blocks from the player with room for a wither skeleton, near their height. */
    private static BlockPos findSpot(WorldServer world, EntityPlayerMP p, EntityWitherSkeleton e, Random rand) {
        int py = MathHelper.floor(p.posY);
        for (int i = 0; i < 24; i++) {
            double a = rand.nextDouble() * Math.PI * 2;
            double d = 8 + rand.nextDouble() * 6;
            int x = MathHelper.floor(p.posX + Math.cos(a) * d);
            int z = MathHelper.floor(p.posZ + Math.sin(a) * d);
            for (int k = 0; k <= 8; k++) {
                int y = py + (k % 2 == 0 ? k / 2 : -(k + 1) / 2);
                BlockPos pos = new BlockPos(x, y, z);
                if (!world.isBlockLoaded(pos)) {
                    continue;
                }
                IBlockState below = world.getBlockState(pos.down());
                if (!below.isSideSolid(world, pos.down(), EnumFacing.UP)) {
                    continue;
                }
                e.setLocationAndAngles(x + 0.5, y, z + 0.5, rand.nextFloat() * 360f, 0f);
                if (world.getCollisionBoxes(e, e.getEntityBoundingBox()).isEmpty()
                        && !world.containsAnyLiquid(e.getEntityBoundingBox())) {
                    return pos;
                }
            }
        }
        return null;
    }

    /**
     * Called once a second. Keeps the Reaper on its target. Returns true the
     * second it fades unkilled.
     */
    public boolean tick() {
        if (entity == null) {
            return false;
        }
        if (entity.isDead || !entity.isEntityAlive()) {
            clear();
            return false;
        }
        if (--secondsLeft <= 0) {
            remove();
            return true;
        }
        if (target != null && !target.hasDisconnected() && target.isEntityAlive()
                && target.world == entity.world && entity.getAttackTarget() == null) {
            entity.setAttackTarget(target);
        }
        return false;
    }

    /** Removes the Reaper in a puff of smoke, e.g. at dawn. */
    public void remove() {
        if (entity != null && !entity.isDead && entity.world instanceof WorldServer) {
            ParticleDisplay.fade((WorldServer) entity.world, entity.posX, entity.posY, entity.posZ);
            entity.setDead();
        }
        clear();
    }

    /** The Reaper died. */
    public void killed() {
        clear();
    }

    private void clear() {
        entity = null;
        id = null;
        target = null;
        secondsLeft = 0;
    }
}
