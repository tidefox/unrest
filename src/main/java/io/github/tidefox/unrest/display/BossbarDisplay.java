package io.github.tidefox.unrest.display;

import io.github.tidefox.unrest.config.UnrestConfig;
import io.github.tidefox.unrest.core.NightType;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.play.server.SPacketUpdateBossInfo;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.BossInfo;
import net.minecraft.world.BossInfoServer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The sleep meter bossbar (DESIGN.md §7). */
public final class BossbarDisplay {

    private final Bar bar = new Bar();
    private String lastText = "";

    public void show(List<EntityPlayerMP> viewers, NightType type, List<EntityPlayerMP> sleepers, float progress) {
        show(viewers, type, sleepers, progress, UnrestConfig.moon(), "", false);
    }

    /**
     * Shows the bar with the moon symbol, any momentum extras ("⚡3 ⛓×2") and,
     * on a Fever Dream, a colour that flashes each second.
     */
    public void show(List<EntityPlayerMP> viewers, NightType type, List<EntityPlayerMP> sleepers, float progress,
                     String moon, String extras, boolean flash) {
        float pct = Math.max(0f, Math.min(1f, progress));
        String text = text(moon, type, sleepers, pct, extras);
        if (!text.equals(lastText)) {
            lastText = text;
            bar.setName(new TextComponentString(text));
        }
        BossInfo.Color color = type == NightType.FEVER && flash ? BossInfo.Color.PINK : color(type);
        if (bar.getColor() != color) {
            bar.recolor(color);
        }
        bar.setPercent(pct);

        Set<EntityPlayerMP> wanted = new HashSet<>(viewers);
        for (EntityPlayerMP p : new ArrayList<>(bar.getPlayers())) {
            if (!wanted.contains(p)) {
                bar.removePlayer(p);
            }
        }
        for (EntityPlayerMP p : viewers) {
            if (!bar.getPlayers().contains(p)) {
                bar.addPlayer(p);
            }
        }
    }

    public void hide() {
        for (EntityPlayerMP p : new ArrayList<>(bar.getPlayers())) {
            bar.removePlayer(p);
        }
    }

    /**
     * {@code BossInfo.setColor} is client-only: it is missing from the dedicated
     * server jar, so calling it there throws {@code NoSuchMethodError}. The
     * colour field is protected and the style packet is in the server jar, so
     * the bar sets the field and sends the update itself, as vanilla's
     * {@code BossInfoServer} does for its other setters.
     */
    private static final class Bar extends BossInfoServer {
        Bar() {
            super(new TextComponentString(""), BossInfo.Color.BLUE, BossInfo.Overlay.NOTCHED_10);
        }

        void recolor(BossInfo.Color c) {
            // The bar is never hidden with setVisible(false) (hide() removes the
            // players instead), so unlike vanilla there's no visibility check.
            color = c;
            SPacketUpdateBossInfo packet = new SPacketUpdateBossInfo(SPacketUpdateBossInfo.Operation.UPDATE_STYLE, this);
            for (EntityPlayerMP p : getPlayers()) {
                p.connection.sendPacket(packet);
            }
        }
    }

    static String text(String moon, NightType type, List<EntityPlayerMP> sleepers, float pct, String extras) {
        StringBuilder sb = new StringBuilder();
        sb.append(moon).append(' ').append(type.displayName());
        if (!sleepers.isEmpty()) {
            sb.append(" — ").append(ChatDisplay.names(sleepers, UnrestConfig.bossbarMaxNames)).append(" asleep");
        }
        sb.append(" — ").append(Math.round(pct * 100)).append('%');
        if (extras != null && !extras.isEmpty()) {
            sb.append("  ").append(extras);
        }
        return sb.toString();
    }

    static BossInfo.Color color(NightType type) {
        switch (type) {
            case CALM:
                return BossInfo.Color.GREEN;
            case RESTLESS:
                return BossInfo.Color.YELLOW;
            case HAUNTED:
                return BossInfo.Color.RED;
            case FEVER:
                return BossInfo.Color.PURPLE;
            default:
                return BossInfo.Color.BLUE;
        }
    }
}
