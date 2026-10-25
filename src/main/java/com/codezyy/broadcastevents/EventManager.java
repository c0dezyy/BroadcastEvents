package com.codezyy.broadcastevents;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

@SuppressWarnings("deprecation")
public final class EventManager {
    private static final class RunningEvent {
        final EventDefinition def;
        final long endMillis;
        final long totalMillis;
        BukkitTask task;
        BossBar bar;
        final Set<UUID> viewers = new HashSet<>();

        RunningEvent(EventDefinition def) {
            this.def = def;
            this.totalMillis = def.duration * 1000L;
            this.endMillis = System.currentTimeMillis() + totalMillis;
        }
    }

    private final BroadcastEvents plugin;
    private final File file;
    private final SnapshotStore store;
    private final Map<String, EventDefinition> defs = new LinkedHashMap<>();
    private final Map<String, RunningEvent> running = new HashMap<>();
    /** player -> event key, only while the player is inside an event */
    private final Map<UUID, String> participants = new HashMap<>();

    public EventManager(BroadcastEvents plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "events.yml");
        this.store = new SnapshotStore(plugin.getDataFolder(), plugin.getLogger());
    }

    // ------------------------------------------------------------------ definitions
    public void load() {
        defs.clear();
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = y.getConfigurationSection("events");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            EventDefinition d = EventDefinition.load(s.getString("name", key), s);
            defs.put(d.key(), d);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (EventDefinition d : defs.values()) d.save(y.createSection("events." + d.key()));
        try { y.save(file); } catch (IOException e) { plugin.getLogger().severe("Could not save events.yml: " + e.getMessage()); }
    }

    public EventDefinition get(String name) { return name == null ? null : defs.get(name.toLowerCase(Locale.ROOT)); }
    public Collection<EventDefinition> all() { return defs.values(); }
    public void add(EventDefinition d) { defs.put(d.key(), d); save(); }
    public void remove(EventDefinition d) { defs.remove(d.key()); save(); }

    public EventDefinition.Scope defaultScope() {
        try { return EventDefinition.Scope.valueOf(plugin.getConfig().getString("event.display-scope", "ALL").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { return EventDefinition.Scope.ALL; }
    }

    public boolean isRunning(String key) { return running.containsKey(key); }
    public List<String> runningNames() {
        List<String> l = new ArrayList<>();
        for (RunningEvent r : running.values()) l.add(r.def.name);
        return l;
    }
    public long remaining(String key) {
        RunningEvent r = running.get(key);
        return r == null ? 0 : (long) Math.ceil((r.endMillis - System.currentTimeMillis()) / 1000.0);
    }
    public int participantCount(String key) {
        int n = 0;
        for (String v : participants.values()) if (v.equals(key)) n++;
        return n;
    }

    // ------------------------------------------------------------------ running events
    private DisplayType msgType(EventDefinition d) { return d.type == DisplayType.TITLE ? DisplayType.TITLE : DisplayType.CHAT; }

    private String fmt(String path, String def, EventDefinition d, long sec) {
        return plugin.getConfig().getString(path, def)
                .replace("{event}", d.display)
                .replace("{time}", Text.formatTime(sec))
                .replace("{players}", String.valueOf(participantCount(d.key())));
    }

    /** Returns an error message, or null on success. */
    public String start(String name) {
        EventDefinition d = get(name);
        if (d == null) return "No event named '" + name + "'.";
        if (running.containsKey(d.key())) return "That event is already running.";
        if (d.spawn == null) return "Set the spawn first: /event setspawn " + d.name;
        if (d.spawn.toLocation() == null) return "The world '" + d.spawn.world + "' is not loaded.";
        if (d.duration <= 0) return "Set a time first: /event settime " + d.name + " <time>";

        RunningEvent r = new RunningEvent(d);
        running.put(d.key(), r);
        plugin.announcements().show(Bukkit.getOnlinePlayers(), msgType(d),
                fmt("event.start-message", "&a{event} started! /join (ends in {time})", d, d.duration));
        r.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(r), 0L, 20L);
        return null;
    }

    /** Returns an error message, or null on success. */
    public String stop(String name) {
        EventDefinition d = get(name);
        if (d == null) return "No event named '" + name + "'.";
        RunningEvent r = running.get(d.key());
        if (r == null) return "That event is not running.";
        end(r);
        return null;
    }

    private Collection<Player> audience(RunningEvent r) {
        List<Player> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            switch (r.def.scope) {
                case PARTICIPANTS:
                    if (r.def.key().equals(participants.get(p.getUniqueId()))) out.add(p);
                    break;
                case EVENTWORLD:
                    if (r.def.spawn != null && p.getWorld().getName().equals(r.def.spawn.world)) out.add(p);
                    break;
                default:
                    out.add(p);
            }
        }
        return out;
    }

    private void tick(RunningEvent r) {
        long now = System.currentTimeMillis();
        long remaining = (long) Math.ceil((r.endMillis - now) / 1000.0);
        if (remaining <= 0) { end(r); return; }
        EventDefinition d = r.def;
        Collection<Player> aud = audience(r);

        switch (d.type) {
            case BOSSBAR: {
                if (r.bar == null) {
                    r.bar = BossBar.bossBar(Component.empty(), 1f,
                            plugin.announcements().bossColor("event.bossbar-color", "RED"),
                            plugin.announcements().bossOverlay("event.bossbar-overlay"));
                }
                r.bar.name(Text.c(fmt("event.format", "&6{event} &f{time}", d, remaining)));
                r.bar.progress(Math.max(0f, Math.min(1f, (float) (r.endMillis - now) / r.totalMillis)));
                Set<UUID> want = new HashSet<>();
                for (Player p : aud) want.add(p.getUniqueId());
                for (UUID id : new ArrayList<>(r.viewers)) {
                    if (!want.contains(id)) {
                        Player p = Bukkit.getPlayer(id);
                        if (p != null) p.hideBossBar(r.bar);
                        r.viewers.remove(id);
                    }
                }
                for (Player p : aud) if (r.viewers.add(p.getUniqueId())) p.showBossBar(r.bar);
                break;
            }
            case ACTIONBAR: {
                Component c = Text.c(fmt("event.format", "&6{event} &f{time}", d, remaining));
                for (Player p : aud) p.sendActionBar(c);
                break;
            }
            case TITLE:
            case CHAT: {
                if (remaining < d.duration && plugin.getConfig().getIntegerList("event.milestones").contains((int) remaining)) {
                    if (d.type == DisplayType.TITLE) {
                        plugin.announcements().show(aud, DisplayType.TITLE, fmt("event.title-format", "&6{event}||&f{time}", d, remaining));
                    } else {
                        plugin.announcements().show(aud, DisplayType.CHAT, fmt("event.chat-format", "&6{event} ends in {time}", d, remaining));
                    }
                }
                break;
            }
        }
    }

    private void hideBar(RunningEvent r) {
        if (r.bar == null) return;
        for (UUID id : r.viewers) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) p.hideBossBar(r.bar);
        }
        r.viewers.clear();
    }

    /** Ends the event (timer finished or stopped by an admin) and restores everyone. */
    private void end(RunningEvent r) {
        if (r.task != null) r.task.cancel();
        hideBar(r);
        running.remove(r.def.key());
        plugin.announcements().show(Bukkit.getOnlinePlayers(), msgType(r.def),
                fmt("event.end-message", "&c{event} has ended!", r.def, 0));
        String key = r.def.key();
        for (Map.Entry<UUID, String> e : new ArrayList<>(participants.entrySet())) {
            if (!e.getValue().equals(key)) continue;
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) participants.remove(e.getKey());   // offline: snapshot file stays, restored on next join
            else restore(p);
        }
    }

    /** Called from onDisable: restore everyone who is online right now, without scheduling anything. */
    public void shutdown() {
        for (RunningEvent r : new ArrayList<>(running.values())) {
            if (r.task != null) r.task.cancel();
            hideBar(r);
        }
        running.clear();
        for (Player p : new ArrayList<>(Bukkit.getOnlinePlayers())) {
            if (participants.containsKey(p.getUniqueId()) || store.exists(p.getUniqueId())) restore(p);
        }
        participants.clear();
    }

    // ------------------------------------------------------------------ players
    public boolean isParticipant(UUID id) { return participants.containsKey(id); }
    public boolean hasSnapshot(UUID id) { return store.exists(id); }

    /** The definition of the running event this player is in, or null. */
    public EventDefinition activeDefinition(UUID id) {
        String key = participants.get(id);
        if (key == null) return null;
        RunningEvent r = running.get(key);
        return r == null ? null : r.def;
    }

    public Location originOf(UUID id) {
        PlayerSnapshot s = store.load(id);
        return (s == null || s.origin == null) ? null : s.origin.toLocation();
    }

    /** Returns an error message, or null on success. */
    public String join(Player p, String eventName) {
        UUID id = p.getUniqueId();
        if (participants.containsKey(id)) return "You are already in an event. Use /leave to go back.";

        RunningEvent r;
        if (eventName == null) {
            if (running.isEmpty()) return "There is no running event right now.";
            if (running.size() > 1) return "Several events are running: " + String.join(", ", runningNames()) + ". Use /join <name>.";
            r = running.values().iterator().next();
        } else {
            r = running.get(eventName.toLowerCase(Locale.ROOT));
            if (r == null) return "That event is not running.";
        }

        // Leftover snapshot from an earlier crash/disconnect: restore it first so it is never overwritten.
        if (store.exists(id) && !restore(p)) return "Your saved inventory could not be restored yet. Please tell an admin.";

        Location dest = r.def.spawn == null ? null : r.def.spawn.toLocation();
        if (dest == null) return "The event world is not available.";

        p.closeInventory();
        PlayerSnapshot snap = PlayerSnapshot.capture(p, r.def.name);
        try {
            store.save(snap);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not write snapshot for " + p.getName() + ": " + ex.getMessage());
            return "Could not save your inventory, so joining was cancelled to keep your items safe.";
        }
        if (store.load(id) == null) {          // verify the file really can be read back
            store.delete(id);
            return "Could not verify your saved inventory, so joining was cancelled.";
        }

        participants.put(id, r.def.key());
        if (!p.teleport(dest)) {               // nothing has been touched yet, so just undo the bookkeeping
            participants.remove(id);
            store.delete(id);
            return "Teleport failed. Nothing was changed.";
        }
        giveKit(p, r.def);
        plugin.tellPlayer(p, "joined", "&aYou joined &e{event}&a.", "{event}", r.def.display);
        return null;
    }

    /** Returns an error message, or null on success. */
    public String leave(Player p) {
        if (!participants.containsKey(p.getUniqueId())) return "You are not in an event.";
        return restore(p) ? null : "Could not restore you right now.";
    }

    public void giveKit(Player p, EventDefinition d) {
        p.closeInventory();
        d.kit.applyTo(p);
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
        String gm = plugin.getConfig().getString("event.gamemode", "SURVIVAL");
        if (!gm.equalsIgnoreCase("KEEP")) {
            try { p.setGameMode(GameMode.valueOf(gm.toUpperCase(Locale.ROOT))); } catch (IllegalArgumentException ignored) { }
        }
        p.setHealth(p.getMaxHealth());
        p.setFoodLevel(20);
        p.setSaturation(20f);
        p.setLevel(0);
        p.setExp(0f);
        p.setFireTicks(0);
        p.setFallDistance(0f);
    }

    /**
     * Puts the player back where they were with their original inventory, then deletes the snapshot + log.
     * Returns true when there is nothing left to restore. If the player is dead the snapshot is kept and
     * restored on respawn.
     */
    public boolean restore(Player p) {
        UUID id = p.getUniqueId();
        if (!store.exists(id)) { participants.remove(id); return true; }

        PlayerSnapshot s = store.load(id);
        if (s == null) {
            plugin.getLogger().severe("Snapshot for " + p.getName() + " is unreadable. File kept in snapshots/.");
            return false;
        }
        participants.remove(id);
        if (p.isDead()) return false;          // handled in the respawn listener, file stays on disk

        p.closeInventory();
        Location dest = s.origin == null ? null : s.origin.toLocation();
        boolean fallback = false;
        if (dest == null) {
            dest = Bukkit.getWorlds().get(0).getSpawnLocation();
            fallback = true;
            plugin.getLogger().warning("Original world of " + p.getName() + " is missing ("
                    + (s.origin == null ? "?" : s.origin.pretty()) + "); using main spawn.");
        }
        p.setFallDistance(0f);
        if (!p.teleport(dest)) plugin.getLogger().warning("Teleport back failed for " + p.getName() + ".");
        s.inventory.applyTo(p);
        s.restoreStats(p);
        store.delete(id);

        if (fallback) plugin.tellPlayer(p, "restored-fallback", "&eRestored at the main spawn.");
        else plugin.tellPlayer(p, "restored", "&aYour inventory and location were restored.");
        return true;
    }
}
