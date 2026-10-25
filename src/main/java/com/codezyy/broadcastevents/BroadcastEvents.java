package com.codezyy.broadcastevents;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class BroadcastEvents extends JavaPlugin {
    private AnnouncementManager announcements;
    private EventManager events;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        if (!new File(getDataFolder(), "announcements.yml").exists()) saveResource("announcements.yml", false);

        announcements = new AnnouncementManager(this);
        announcements.load();
        announcements.schedule();

        events = new EventManager(this);
        events.load();

        AnnounceCommand ann = new AnnounceCommand(this);
        getCommand("announce").setExecutor(ann);
        getCommand("announce").setTabCompleter(ann);
        EventCommand ev = new EventCommand(this);
        getCommand("event").setExecutor(ev);
        getCommand("event").setTabCompleter(ev);
        JoinCommand jc = new JoinCommand(this);
        getCommand("join").setExecutor(jc);
        getCommand("join").setTabCompleter(jc);
        getCommand("leave").setExecutor(jc);

        getServer().getPluginManager().registerEvents(new EventListener(this), this);
    }

    @Override
    public void onDisable() {
        if (events != null) events.shutdown();
        if (announcements != null) announcements.stop();
    }

    public void reload() {
        reloadConfig();
        announcements.load();
        announcements.schedule();
    }

    public AnnouncementManager announcements() { return announcements; }
    public EventManager events() { return events; }

    /** Admin-facing reply with prefix. */
    public void reply(CommandSender s, String text) {
        s.sendMessage(Text.c(getConfig().getString("messages.prefix", "&8[&6Events&8] &r") + text));
    }

    /** Player-facing configurable message (messages.<key> in config.yml). */
    public void tellPlayer(Player p, String key, String def, String... kv) {
        String m = getConfig().getString("messages." + key, def);
        for (int i = 0; i + 1 < kv.length; i += 2) m = m.replace(kv[i], kv[i + 1]);
        reply(p, m);
    }
}
