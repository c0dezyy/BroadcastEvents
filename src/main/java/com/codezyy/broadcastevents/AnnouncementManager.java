package com.codezyy.broadcastevents;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class AnnouncementManager {
    public static final class Entry {
        public final DisplayType type;
        public final String text;
        public Entry(DisplayType type, String text) { this.type = type; this.text = text; }
    }

    private final BroadcastEvents plugin;
    private final File file;
    private final List<Entry> entries = new ArrayList<>();
    private boolean enabled = true, random = false;
    private long minSec = 60, maxSec = 60;
    private int index = 0;
    private BukkitTask task;

    public AnnouncementManager(BroadcastEvents plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "announcements.yml");
    }

    public void load() {
        entries.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        enabled = y.getBoolean("enabled", true);
        random = "RANDOM".equalsIgnoreCase(y.getString("mode", "SEQUENTIAL"));
        minSec = Math.max(1, y.getLong("interval-min", 60));
        maxSec = Math.max(minSec, y.getLong("interval-max", minSec));
        for (Map<?, ?> m : y.getMapList("list")) {
            DisplayType t = DisplayType.parse(String.valueOf(m.get("type")));
            Object tx = m.get("text");
            if (t != null && tx != null) entries.add(new Entry(t, String.valueOf(tx)));
        }
        index = 0;
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("enabled", enabled);
        y.set("mode", random ? "RANDOM" : "SEQUENTIAL");
        y.set("interval-min", minSec);
        y.set("interval-max", maxSec);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Entry e : entries) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", e.type.name());
            m.put("text", e.text);
            list.add(m);
        }
        y.set("list", list);
        try { y.save(file); } catch (IOException ex) { plugin.getLogger().warning("Could not save announcements.yml: " + ex.getMessage()); }
    }

    // ---- scheduling ----
    public void schedule() {
        stop();
        if (!enabled || entries.isEmpty()) return;
        long sec = minSec == maxSec ? minSec : ThreadLocalRandom.current().nextLong(minSec, maxSec + 1);
        task = Bukkit.getScheduler().runTaskLater(plugin, () -> { next(); schedule(); }, sec * 20L);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    private void next() {
        if (entries.isEmpty()) return;
        Entry e = random
                ? entries.get(ThreadLocalRandom.current().nextInt(entries.size()))
                : entries.get(index++ % entries.size());
        broadcast(e.type, e.text);
    }

    // ---- accessors ----
    public List<Entry> entries() { return entries; }
    public boolean isEnabled() { return enabled; }
    public boolean isRandom() { return random; }
    public long minSec() { return minSec; }
    public long maxSec() { return maxSec; }
    public void setEnabled(boolean v) { enabled = v; save(); schedule(); }
    public void setRandom(boolean v) { random = v; save(); }
    public void setInterval(long min, long max) { minSec = min; maxSec = max; save(); schedule(); }
    public void add(DisplayType t, String text) { entries.add(new Entry(t, text)); save(); schedule(); }
    public boolean remove(int i) {
        if (i < 0 || i >= entries.size()) return false;
        entries.remove(i); save(); return true;
    }

    // ---- display ----
    public void broadcast(DisplayType type, String text) {
        show(Bukkit.getOnlinePlayers(), type, text);
    }

    public BossBar.Color bossColor(String path, String def) {
        try { return BossBar.Color.valueOf(plugin.getConfig().getString(path, def).toUpperCase(Locale.ROOT)); }
        catch (Exception e) { return BossBar.Color.valueOf(def); }
    }

    public BossBar.Overlay bossOverlay(String path) {
        try { return BossBar.Overlay.valueOf(plugin.getConfig().getString(path, "PROGRESS").toUpperCase(Locale.ROOT)); }
        catch (Exception e) { return BossBar.Overlay.PROGRESS; }
    }

    /** Shows a message to the given players in the given style. Supports &colors, &#hex, {online}, {max}. */
    public void show(Collection<? extends Player> targets, DisplayType type, String raw) {
        final List<Player> players = new ArrayList<>(targets);
        if (players.isEmpty()) return;
        final String text = raw
                .replace("{online}", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("{max}", String.valueOf(Bukkit.getMaxPlayers()));
        final FileConfiguration cfg = plugin.getConfig();

        switch (type) {
            case CHAT: {
                String prefix = cfg.getString("chat-prefix", "");
                for (String line : text.split("\\\\n")) {
                    Component c = Text.c(prefix + line);
                    for (Player p : players) p.sendMessage(c);
                }
                break;
            }
            case ACTIONBAR: {
                final int secs = Math.max(1, cfg.getInt("announce.actionbar-seconds", 5));
                final Component c = Text.c(text);
                new BukkitRunnable() {
                    int left = secs;
                    @Override public void run() {
                        if (left-- <= 0) { cancel(); return; }
                        for (Player p : players) if (p.isOnline()) p.sendActionBar(c);
                    }
                }.runTaskTimer(plugin, 0L, 20L);
                break;
            }
            case TITLE: {
                String[] parts = text.split("\\|\\|", 2);
                Title t = Title.title(Text.c(parts[0]), Text.c(parts.length > 1 ? parts[1] : ""),
                        Title.Times.times(Duration.ofMillis(500),
                                Duration.ofSeconds(Math.max(1, cfg.getInt("announce.title-stay-seconds", 4))),
                                Duration.ofMillis(500)));
                for (Player p : players) p.showTitle(t);
                break;
            }
            case BOSSBAR: {
                final int secs = Math.max(2, cfg.getInt("announce.bossbar-seconds", 10));
                final BossBar bar = BossBar.bossBar(Text.c(text), 1f,
                        bossColor("announce.bossbar-color", "YELLOW"), bossOverlay("announce.bossbar-overlay"));
                for (Player p : players) p.showBossBar(bar);
                new BukkitRunnable() {
                    int left = secs;
                    @Override public void run() {
                        left--;
                        if (left <= 0) {
                            for (Player p : players) if (p.isOnline()) p.hideBossBar(bar);
                            cancel();
                            return;
                        }
                        bar.progress(Math.max(0f, (float) left / secs));
                    }
                }.runTaskTimer(plugin, 20L, 20L);
                break;
            }
        }
    }
}
