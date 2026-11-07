package io.github.tidefox.unrest.display;

import io.github.tidefox.unrest.config.UnrestConfig;
import io.github.tidefox.unrest.core.NightType;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.BossInfo;
import net.minecraft.world.BossInfoServer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The sleep meter bossbar (DESIGN.md §7). */
public final class BossbarDisplay {

    private final BossInfoServer bar = new BossInfoServer(new TextComponentString(""),
            BossInfo.Color.BLUE, BossInfo.Overlay.NOTCHED_10);
    private String lastText = "";

    public void show(List<EntityPlayerMP> viewers, NightType type, List<EntityPlayerMP> sleepers, float progress) {
        float pct = Math.max(0f, Math.min(1f, progress));
        String text = text(type, sleepers, pct);
        if (!text.equals(lastText)) {
            lastText = text;
            bar.setName(new TextComponentString(text));
        }
        BossInfo.Color color = color(type);
        if (bar.getColor() != color) {
            bar.setColor(color);
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
        if (!bar.getPlayers().isEmpty()) {
            bar.removeAllPlayers();
        }
    }

    static String text(NightType type, List<EntityPlayerMP> sleepers, float pct) {
        StringBuilder sb = new StringBuilder();
        sb.append(UnrestConfig.moon()).append(' ').append(type.displayName());
        if (!sleepers.isEmpty()) {
            sb.append(" — ").append(ChatDisplay.names(sleepers, UnrestConfig.bossbarMaxNames)).append(" asleep");
        }
        sb.append(" — ").append(Math.round(pct * 100)).append('%');
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
            default:
                return BossInfo.Color.BLUE;
        }
    }
}
