package com.codezyy.broadcastevents;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Text {
    private static final LegacyComponentSerializer SER =
            LegacyComponentSerializer.builder().character('&').hexColors().build();
    private static final Pattern DURATION = Pattern.compile("(\\d+)([hms]?)");

    private Text() {}

    /** Converts &a color codes and &#rrggbb hex codes into a Component. */
    public static Component c(String s) {
        return SER.deserialize(s == null ? "" : s);
    }

    public static String formatTime(long sec) {
        if (sec < 0) sec = 0;
        long h = sec / 3600, m = (sec % 3600) / 60, s = sec % 60;
        if (h > 0) return String.format("%dh %02dm %02ds", h, m, s);
        if (m > 0) return String.format("%dm %02ds", m, s);
        return s + "s";
    }

    /** Parses 90, 90s, 5m, 1h30m ... Returns -1 when invalid. */
    public static long parseDuration(String input) {
        if (input == null) return -1;
        String s = input.toLowerCase();
        Matcher m = DURATION.matcher(s);
        long total = 0;
        int pos = 0;
        while (m.find()) {
            if (m.start() != pos) return -1;
            pos = m.end();
            long n;
            try { n = Long.parseLong(m.group(1)); } catch (NumberFormatException e) { return -1; }
            switch (m.group(2)) {
                case "h": total += n * 3600; break;
                case "m": total += n * 60; break;
                default: total += n;
            }
        }
        return (pos == s.length() && total > 0) ? total : -1;
    }
}
