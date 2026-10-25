package com.codezyy.broadcastevents;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

public final class EventDefinition {
    public enum Scope { ALL, PARTICIPANTS, EVENTWORLD }

    public final String name;
    public String display;
    public DisplayType type = DisplayType.BOSSBAR;
    public long duration;
    public Scope scope = Scope.ALL;
    public StoredLocation spawn;
    public ItemKit kit = new ItemKit();

    public EventDefinition(String name) {
        this.name = name;
        this.display = name;
    }

    public String key() { return name.toLowerCase(Locale.ROOT); }

    public void save(ConfigurationSection s) {
        s.set("name", name);
        s.set("display", display);
        s.set("type", type.name());
        s.set("duration", duration);
        s.set("scope", scope.name());
        if (spawn != null) spawn.save(s.createSection("spawn"));
        kit.save(s.createSection("kit"));
    }

    public static EventDefinition load(String name, ConfigurationSection s) {
        EventDefinition d = new EventDefinition(name);
        d.display = s.getString("display", name);
        DisplayType t = DisplayType.parse(s.getString("type"));
        d.type = t == null ? DisplayType.BOSSBAR : t;
        d.duration = s.getLong("duration");
        try { d.scope = Scope.valueOf(s.getString("scope", "ALL")); } catch (IllegalArgumentException e) { d.scope = Scope.ALL; }
        d.spawn = StoredLocation.load(s.getConfigurationSection("spawn"));
        d.kit = ItemKit.load(s.getConfigurationSection("kit"));
        return d;
    }
}
