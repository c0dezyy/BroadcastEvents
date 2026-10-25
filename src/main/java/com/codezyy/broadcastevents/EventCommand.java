package com.codezyy.broadcastevents;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

public final class EventCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBS = Arrays.asList("create", "delete", "list", "info", "start", "stop",
            "settype", "settime", "setdisplay", "setscope", "setspawn", "setkit", "clearkit", "restore", "help");
    private static final List<String> NAMED = Arrays.asList("delete", "info", "start", "stop", "settype", "settime",
            "setdisplay", "setscope", "setspawn", "setkit", "clearkit");
    private static final List<String> TYPES = Arrays.asList("chat", "actionbar", "bossbar", "title");

    private final BroadcastEvents plugin;
    private final EventManager ev;

    public EventCommand(BroadcastEvents plugin) {
        this.plugin = plugin;
        this.ev = plugin.events();
    }

    private void help(CommandSender s) {
        plugin.reply(s, "&6Events");
        s.sendMessage(Text.c("&e/event create <name> <type> <time> [display name] &7- type: bossbar/actionbar/chat/title, time: 10m, 1h30m"));
        s.sendMessage(Text.c("&e/event setspawn <name> [x y z [world]] &7- default: where you stand"));
        s.sendMessage(Text.c("&e/event setkit <name> &7- copy YOUR inventory as the event kit"));
        s.sendMessage(Text.c("&e/event start <name> &7| &e/event stop <name>"));
        s.sendMessage(Text.c("&e/event settype|settime|setdisplay|setscope <name> <value>"));
        s.sendMessage(Text.c("&e/event list &7| &e/event info <name> &7| &e/event delete <name> &7| &e/event clearkit <name>"));
        s.sendMessage(Text.c("&e/event restore <player> &7- force-restore a player's saved inventory"));
    }

    private EventDefinition def(CommandSender s, String[] a) {
        if (a.length < 2) { plugin.reply(s, "&cGive the event name. Example: /event " + a[0] + " myevent"); return null; }
        EventDefinition d = ev.get(a[1]);
        if (d == null) plugin.reply(s, "&cNo event named '" + a[1] + "'. See /event list");
        return d;
    }

    private boolean busy(CommandSender s, EventDefinition d) {
        if (ev.isRunning(d.key())) { plugin.reply(s, "&cStop the event first: /event stop " + d.name); return true; }
        return false;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) { help(s); return true; }

        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "list": {
                if (ev.all().isEmpty()) { plugin.reply(s, "&7No events yet. Use /event create"); return true; }
                for (EventDefinition d : ev.all()) {
                    String state = ev.isRunning(d.key()) ? " &a[RUNNING " + Text.formatTime(ev.remaining(d.key())) + "]" : "";
                    s.sendMessage(Text.c("&e" + d.name + " &7- " + d.type + ", " + Text.formatTime(d.duration) + state));
                }
                return true;
            }
            case "create": {
                if (a.length < 4) { plugin.reply(s, "&cUsage: /event create <name> <type> <time> [display name]"); return true; }
                if (!a[1].matches("[A-Za-z0-9_-]{1,32}")) { plugin.reply(s, "&cName: letters, numbers, - and _ only."); return true; }
                if (ev.get(a[1]) != null) { plugin.reply(s, "&cThat event already exists."); return true; }
                DisplayType t = DisplayType.parse(a[2]);
                if (t == null) { plugin.reply(s, "&cType must be bossbar, actionbar, chat or title."); return true; }
                long dur = Text.parseDuration(a[3]);
                if (dur < 1) { plugin.reply(s, "&cInvalid time. Examples: 90s, 10m, 1h30m."); return true; }
                EventDefinition d = new EventDefinition(a[1]);
                d.type = t;
                d.duration = dur;
                d.scope = ev.defaultScope();
                if (a.length > 4) d.display = String.join(" ", Arrays.copyOfRange(a, 4, a.length));
                ev.add(d);
                plugin.reply(s, "&aCreated &e" + d.name + "&a. Next: /event setspawn " + d.name + " and /event setkit " + d.name);
                return true;
            }
            case "delete": {
                EventDefinition d = def(s, a);
                if (d == null || busy(s, d)) return true;
                ev.remove(d);
                plugin.reply(s, "&aDeleted.");
                return true;
            }
            case "info": {
                EventDefinition d = def(s, a);
                if (d == null) return true;
                plugin.reply(s, "&6" + d.name + " &7(" + d.display + "&7)");
                s.sendMessage(Text.c("&7Type: &f" + d.type + " &7Time: &f" + Text.formatTime(d.duration) + " &7Scope: &f" + d.scope));
                s.sendMessage(Text.c("&7Spawn: &f" + (d.spawn == null ? "not set" : d.spawn.pretty())));
                s.sendMessage(Text.c("&7Kit items: &f" + d.kit.count()));
                if (ev.isRunning(d.key()))
                    s.sendMessage(Text.c("&aRunning: " + Text.formatTime(ev.remaining(d.key())) + " left, " + ev.participantCount(d.key()) + " players"));
                return true;
            }
            case "start": {
                EventDefinition d = def(s, a);
                if (d == null) return true;
                String err = ev.start(d.name);
                plugin.reply(s, err == null ? "&aStarted &e" + d.name + "&a." : "&c" + err);
                return true;
            }
            case "stop": {
                EventDefinition d = def(s, a);
                if (d == null) return true;
                String err = ev.stop(d.name);
                plugin.reply(s, err == null ? "&aStopped. Players were restored." : "&c" + err);
                return true;
            }
            case "settype": {
                EventDefinition d = def(s, a);
                if (d == null || busy(s, d)) return true;
                DisplayType t = a.length > 2 ? DisplayType.parse(a[2]) : null;
                if (t == null) { plugin.reply(s, "&cType must be bossbar, actionbar, chat or title."); return true; }
                d.type = t; ev.save();
                plugin.reply(s, "&aType set to " + t + ".");
                return true;
            }
            case "settime": {
                EventDefinition d = def(s, a);
                if (d == null || busy(s, d)) return true;
                long dur = a.length > 2 ? Text.parseDuration(a[2]) : -1;
                if (dur < 1) { plugin.reply(s, "&cInvalid time. Examples: 90s, 10m, 1h30m."); return true; }
                d.duration = dur; ev.save();
                plugin.reply(s, "&aTime set to " + Text.formatTime(dur) + ".");
                return true;
            }
            case "setdisplay": {
                EventDefinition d = def(s, a);
                if (d == null) return true;
                if (a.length < 3) { plugin.reply(s, "&cUsage: /event setdisplay <name> <text>"); return true; }
                d.display = String.join(" ", Arrays.copyOfRange(a, 2, a.length)); ev.save();
                plugin.reply(s, "&aDisplay name updated.");
                return true;
            }
            case "setscope": {
                EventDefinition d = def(s, a);
                if (d == null || busy(s, d)) return true;
                try {
                    d.scope = EventDefinition.Scope.valueOf(a.length > 2 ? a[2].toUpperCase(Locale.ROOT) : "");
                } catch (IllegalArgumentException e) { plugin.reply(s, "&cScope: all, participants or eventworld."); return true; }
                ev.save();
                plugin.reply(s, "&aRemaining time is shown to: " + d.scope);
                return true;
            }
            case "setspawn": {
                EventDefinition d = def(s, a);
                if (d == null || busy(s, d)) return true;
                Location loc;
                if (a.length >= 5) {
                    try {
                        double x = Double.parseDouble(a[2]), y = Double.parseDouble(a[3]), z = Double.parseDouble(a[4]);
                        World w = a.length >= 6 ? Bukkit.getWorld(a[5])
                                : (s instanceof Player ? ((Player) s).getWorld() : Bukkit.getWorlds().get(0));
                        if (w == null) { plugin.reply(s, "&cUnknown world."); return true; }
                        float yaw = s instanceof Player ? ((Player) s).getLocation().getYaw() : 0f;
                        loc = new Location(w, x, y, z, yaw, 0f);
                    } catch (NumberFormatException e) { plugin.reply(s, "&cCoordinates must be numbers."); return true; }
                } else if (s instanceof Player) {
                    loc = ((Player) s).getLocation();
                } else { plugin.reply(s, "&cConsole must give coordinates: /event setspawn <name> <x> <y> <z> <world>"); return true; }
                d.spawn = StoredLocation.of(loc); ev.save();
                plugin.reply(s, "&aSpawn set to " + d.spawn.pretty() + ".");
                return true;
            }
            case "setkit": {
                EventDefinition d = def(s, a);
                if (d == null) return true;
                if (!(s instanceof Player)) { plugin.reply(s, "&cRun this in game."); return true; }
                d.kit = ItemKit.capture(((Player) s).getInventory()); ev.save();
                plugin.reply(s, "&aKit saved (" + d.kit.count() + " items). Players get exactly this when they /join.");
                return true;
            }
            case "clearkit": {
                EventDefinition d = def(s, a);
                if (d == null) return true;
                d.kit = new ItemKit(); ev.save();
                plugin.reply(s, "&aKit cleared. Players will join with an empty inventory.");
                return true;
            }
            case "restore": {
                if (a.length < 2) { plugin.reply(s, "&cUsage: /event restore <player>"); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { plugin.reply(s, "&cPlayer must be online (offline players are restored automatically when they join)."); return true; }
                plugin.reply(s, ev.restore(t) ? "&aRestored (or nothing to restore)." : "&eCould not restore now (player is dead or file unreadable). It is retried on respawn.");
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
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (a.length == 1) base = SUBS;
        else if (a.length == 2 && NAMED.contains(sub)) { for (EventDefinition d : ev.all()) base.add(d.name); }
        else if (a.length == 2 && sub.equals("restore")) { for (Player p : Bukkit.getOnlinePlayers()) base.add(p.getName()); }
        else if (a.length == 3 && (sub.equals("create") || sub.equals("settype"))) base = TYPES;
        else if (a.length == 3 && sub.equals("setscope")) base = Arrays.asList("all", "participants", "eventworld");
        List<String> out = new ArrayList<>();
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        for (String b : base) if (b.toLowerCase(Locale.ROOT).startsWith(last)) out.add(b);
        return out;
    }
}
