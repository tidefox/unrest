package io.github.tidefox.unrest.display;

import io.github.tidefox.unrest.Unrest;
import io.github.tidefox.unrest.config.UnrestConfig;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketPlayerListItem;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.io.IOException;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;

/**
 * The [Zz] tag before sleepers' names in the tab list (DESIGN.md §7).
 *
 * <p>Forge 14.23.5 has no tab-list name event, so this sends the vanilla
 * "update display name" player-list packet directly. Players without the tag
 * are sent no display name, so the client falls back to the normal name.
 */
public final class TabListDisplay {

    private TabListDisplay() {}

    /** Sends the tab-list name of each of {@code players} to everyone online. */
    public static void update(MinecraftServer server, Collection<EntityPlayerMP> players, Set<UUID> sleepers) {
        if (players.isEmpty()) {
            return;
        }
        PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
        buf.writeEnumValue(SPacketPlayerListItem.Action.UPDATE_DISPLAY_NAME);
        buf.writeVarInt(players.size());
        for (EntityPlayerMP p : players) {
            buf.writeUniqueId(p.getUniqueID());
            boolean tagged = UnrestConfig.tabList && sleepers.contains(p.getUniqueID());
            buf.writeBoolean(tagged);
            if (tagged) {
                String prefix = UnrestConfig.tabPrefix.isEmpty() ? "" : UnrestConfig.tabPrefix + " ";
                buf.writeTextComponent(new TextComponentString(
                        prefix + ScorePlayerTeam.formatPlayerName(p.getTeam(), p.getName())));
            }
        }
        SPacketPlayerListItem packet = new SPacketPlayerListItem();
        try {
            packet.readPacketData(buf);
        } catch (IOException e) {
            Unrest.LOG.warn("Couldn't build the tab list packet", e);
            return;
        } finally {
            buf.release();
        }
        server.getPlayerList().sendPacketToAllPlayers(packet);
    }
}
