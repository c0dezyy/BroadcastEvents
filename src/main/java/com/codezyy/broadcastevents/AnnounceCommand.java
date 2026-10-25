package com.codezyy.broadcastevents;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.*;

public final class AnnounceCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = Arrays.asList("send", "add", "remove", "list", "interval", "mode", "toggle", "reload", "help");
    private static final List<String> TYPES = Arrays.asList("chat", "actionbar", "bossbar", "title");
    private final BroadcastEvents plugin;

    public AnnounceCommand(BroadcastEvents plugin) { this.plugin = plugin; }

    private void help(CommandSender s) {
        plugin.reply(s, "&6Announcements &7(types: chat, actionbar, bossbar, title)");
        s.sendMessage(Text.c("&e/announce send <type> <text> &7- broadcast now"));
        s.sendMessage(Text.c("&e/announce add <type> <text> &7- add to the rotation"));
        s.sendMessage(Text.c("&e/announce remove <#> &7| &e/announce list"));
        s.sendMessage(Text.c("&e/announce interval <time> [max] &7- e.g. 2m, or 1m 5m for a random delay"));
        s.sendMessage(Text.c("&e/announce mode <sequential|random> &7| &e/announce toggle &7| &e/announce reload"));
        s.sendMessage(Text.c("&7Use &#rrggbb or &a codes. Title: text||subtitle. New line: \\n"));
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        AnnouncementManager m = plugin.announcements();
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) { help(s); return true; }

        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "send":
            case "add": {
                if (a.length < 3) { plugin.reply(s, "&cUsage: /announce " + a[0].toLowerCase(Locale.ROOT) + " <type> <text>"); return true; }
                DisplayType t = DisplayType.parse(a[1]);
                if (t == null) { plugin.reply(s, "&cType must be chat, actionbar, bossbar or title."); return true; }
                String text = String.join(" ", Arrays.copyOfRange(a, 2, a.length));
                if (a[0].equalsIgnoreCase("send")) {
                    m.broadcast(t, text);
                    plugin.reply(s, "&aSent.");
                } else {
                    m.add(t, text);
                    plugin.reply(s, "&aAdded as #" + m.entries().size() + ".");
                }
                return true;
            }
            case "remove": {
                if (a.length < 2) { plugin.reply(s, "&cUsage: /announce remove <#>"); return true; }
                try {
                    plugin.reply(s, m.remove(Integer.parseInt(a[1]) - 1) ? "&aRemoved." : "&cNo announcement with that number.");
                } catch (NumberFormatException e) { plugin.reply(s, "&cThat is not a number."); }
                return true;
            }
            case "list": {
                plugin.reply(s, "&6" + m.entries().size() + " announcements &7(" + (m.isEnabled() ? "on" : "off") + ", "
                        + (m.isRandom() ? "random" : "in order") + ", every " + Text.formatTime(m.minSec())
                        + (m.maxSec() != m.minSec() ? " to " + Text.formatTime(m.maxSec()) : "") + ")");
                int i = 1;
                for (AnnouncementManager.Entry e : m.entries())
                    s.sendMessage(Text.c("&7#" + (i++) + " &e" + e.type + " &f" + e.text));
                return true;
            }
            case "interval": {
                if (a.length < 2) { plugin.reply(s, "&cUsage: /announce interval <time> [maxtime]"); return true; }
                long min = Text.parseDuration(a[1]);
                long max = a.length > 2 ? Text.parseDuration(a[2]) : min;
                if (min < 1 || max < min) { plugin.reply(s, "&cInvalid time. Examples: 30s, 5m, 1h30m. Max must be >= min."); return true; }
                m.setInterval(min, max);
                plugin.reply(s, "&aInterval set to " + Text.formatTime(min) + (max != min ? " - " + Text.formatTime(max) : "") + ".");
                return true;
            }
            case "mode": {
                if (a.length < 2 || !(a[1].equalsIgnoreCase("random") || a[1].equalsIgnoreCase("sequential"))) {
                    plugin.reply(s, "&cUsage: /announce mode <sequential|random>"); return true;
                }
                m.setRandom(a[1].equalsIgnoreCase("random"));
                plugin.reply(s, "&aMode: " + (m.isRandom() ? "random" : "sequential"));
                return true;
            }
            case "toggle": {
                m.setEnabled(!m.isEnabled());
                plugin.reply(s, "&aAutomatic announcements " + (m.isEnabled() ? "enabled" : "disabled") + ".");
                return true;
            }
            case "reload": {
                plugin.reload();
                plugin.reply(s, "&aReloaded config.yml and announcements.yml.");
                return true;
            }
            default:
                help(s);
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> base = new ArrayList<>();
        if (a.length == 1) base = SUBS;
        else if (a.length == 2 && (a[0].equalsIgnoreCase("send") || a[0].equalsIgnoreCase("add"))) base = TYPES;
        else if (a.length == 2 && a[0].equalsIgnoreCase("mode")) base = Arrays.asList("sequential", "random");
        List<String> out = new ArrayList<>();
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        for (String b : base) if (b.startsWith(last)) out.add(b);
        return out;
    }
}
