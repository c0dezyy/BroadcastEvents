package com.codezyy.broadcastevents;

import java.util.Locale;

public enum DisplayType {
    CHAT, ACTIONBAR, BOSSBAR, TITLE;

    public static DisplayType parse(String s) {
        if (s == null) return null;
        String u = s.toUpperCase(Locale.ROOT).replace("-", "").replace("_", "");
        switch (u) {
            case "CHAT": case "MESSAGE": case "MSG": return CHAT;
            case "ACTIONBAR": case "ACTION": return ACTIONBAR;
            case "BOSSBAR": case "BAR": return BOSSBAR;
            case "TITLE": return TITLE;
            default: return null;
        }
    }
}
