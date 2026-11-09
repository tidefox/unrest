package io.github.tidefox.unrest.log;

import io.github.tidefox.unrest.Unrest;
import io.github.tidefox.unrest.config.UnrestConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Appends one CSV row per night to {@code logs/unrest-nights.csv} (DESIGN.md §9.2). */
public final class NightLog {

    public static final String HEADER = "timestamp,mode,day,nightType,debtBefore,debtAfter,outcome,secondsToSkip,"
            + "playersOnline,countedN,sleepersAtSkip,ratioAtSkip,afkCount,peakSleepers,nightmares,hungerLostTotal,"
            + "watchTotal";

    /** One finished night. */
    public static final class Entry {
        public String mode;
        public long day;
        public String nightType;
        public double debtBefore;
        public double debtAfter;
        public String outcome;
        /** Seconds from the start of the night to the skip, or -1 for a night with no skip. */
        public int secondsToSkip = -1;
        public int playersOnline;
        public int countedN;
        public int sleepersAtSkip;
        public double ratioAtSkip;
        public int afkCount;
        public int peakSleepers;
        public int nightmares;
        public int hungerLostTotal;
        /** Meter added by awake players keeping watch (§4.2). */
        public double watchTotal;

        public String toCsv() {
            return String.join(",",
                    OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                    mode,
                    Long.toString(day),
                    nightType,
                    fmt(debtBefore),
                    fmt(debtAfter),
                    outcome,
                    secondsToSkip < 0 ? "" : Integer.toString(secondsToSkip),
                    Integer.toString(playersOnline),
                    Integer.toString(countedN),
                    Integer.toString(sleepersAtSkip),
                    fmt(ratioAtSkip),
                    Integer.toString(afkCount),
                    Integer.toString(peakSleepers),
                    Integer.toString(nightmares),
                    Integer.toString(hungerLostTotal),
                    fmt(watchTotal));
        }

        private static String fmt(double v) {
            return String.format(Locale.ROOT, "%.3f", v);
        }
    }

    private final File file;

    public NightLog(File file) {
        this.file = file;
    }

    public void write(Entry entry) {
        if (!UnrestConfig.nightLog) {
            return;
        }
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
                throw new IOException("can't create " + dir);
            }
            boolean fresh = !file.exists() || file.length() == 0;
            try (Writer w = new OutputStreamWriter(new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
                if (fresh) {
                    w.write(HEADER);
                    w.write('\n');
                }
                w.write(entry.toCsv());
                w.write('\n');
            }
        } catch (IOException e) {
            Unrest.LOG.warn("Couldn't write the night log {}", file, e);
        }
    }
}
