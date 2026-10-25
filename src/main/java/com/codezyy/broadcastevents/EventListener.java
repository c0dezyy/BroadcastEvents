package com.codezyy.broadcastevents;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.UUID;

public final class EventListener implements Listener {
    private final BroadcastEvents plugin;

    public EventListener(BroadcastEvents plugin) { this.plugin = plugin; }

    private EventManager ev() { return plugin.events(); }

    /** Player logged in with a leftover snapshot (disconnect, crash, died + quit...) -> restore. */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        if (ev().hasSnapshot(id) && !ev().isParticipant(id)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline() && ev().hasSnapshot(id) && !ev().isParticipant(id)) ev().restore(p);
            }, 10L);
        }
    }

    /** Leaving (quit, kick, timeout) while inside an event: give everything back right now. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        if (ev().isParticipant(p.getUniqueId())) ev().restore(p);
        // if the player is dead the snapshot file stays and is restored on respawn / next join
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        if (!ev().isParticipant(e.getEntity().getUniqueId())) return;
        if (plugin.getConfig().getBoolean("event.clear-drops-on-death", true)) {
            e.getDrops().clear();
            e.setKeepInventory(false);
            e.setKeepLevel(false);
            e.setDroppedExp(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        UUID id = p.getUniqueId();
        EventDefinition d = ev().activeDefinition(id);

        if (d != null) {
            if (plugin.getConfig().getBoolean("event.respawn-in-event", true) && d.spawn != null) {
                Location l = d.spawn.toLocation();
                if (l != null) {
                    e.setRespawnLocation(l);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (p.isOnline() && ev().isParticipant(id)) ev().giveKit(p, d);
                    }, 2L);
                    return;
                }
            }
            // respawn-in-event disabled: dying means leaving the event
            Location o = ev().originOf(id);
            if (o != null) e.setRespawnLocation(o);
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) ev().restore(p); }, 2L);
            return;
        }

        // Event ended / player quit while dead: snapshot still on disk -> restore on respawn
        if (ev().hasSnapshot(id)) {
            Location o = ev().originOf(id);
            if (o != null) e.setRespawnLocation(o);
            Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) ev().restore(p); }, 2L);
        }
    }
}
