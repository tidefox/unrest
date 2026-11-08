package io.github.tidefox.unrest.config;

import io.github.tidefox.unrest.Unrest;
import net.minecraftforge.fml.common.Loader;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads Quark's config file (no Quark classes are used) and warns if its
 * Improved Sleeping tweak would skip nights before Unrest's meter fills.
 */
public final class QuarkCheck {

    private static final Pattern ENABLED = Pattern.compile("B:\\s*\"?Improved Sleeping\"?\\s*=\\s*(true|false)");
    private static final Pattern PERCENT = Pattern.compile("I:\\s*\"?Required Percentage\"?\\s*=\\s*(\\d+)");

    private QuarkCheck() {}

    public static void run() {
        if (!Loader.isModLoaded("quark")) {
            return;
        }
        File file = new File(Loader.instance().getConfigDir(), "quark.cfg");
        boolean enabled = true;
        int percent = 100;
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            for (String line : lines) {
                Matcher m = ENABLED.matcher(line);
                if (m.find()) {
                    enabled = Boolean.parseBoolean(m.group(1));
                }
                m = PERCENT.matcher(line);
                if (m.find()) {
                    percent = Integer.parseInt(m.group(1));
                }
            }
        } catch (IOException | RuntimeException e) {
            Unrest.LOG.info("Quark found, but couldn't read {}; assuming Improved Sleeping is on at 100%.", file);
        }
        if (!enabled) {
            return;
        }
        if (percent < 100) {
            Unrest.LOG.warn("Quark's Improved Sleeping skips the night once {}% of non-AFK players sleep. "
                    + "That bypasses Unrest's sleep meter. Set \"Required Percentage\" to 100 in quark.cfg "
                    + "(Unrest still counts those skips as partial skips).", percent);
        } else {
            Unrest.LOG.info("Quark's Improved Sleeping is on: its skips (everyone non-AFK asleep) are counted by Unrest.");
        }
    }
}
