package com.codezyy.broadcastevents;

import org.bukkit.GameMode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Everything needed to put a player back exactly how they were before joining an event. */
@SuppressWarnings("deprecation")
public final class PlayerSnapshot {
    public UUID uuid;
    public String playerName;
    public String eventName;
    public long time;
    public StoredLocation origin;
    public ItemKit inventory;
    public GameMode gameMode = GameMode.SURVIVAL;
    public double health = 20;
    public int food = 20;
    public float saturation = 5;
    public int level;
    public float exp;
    public boolean allowFlight, flying;
    public List<PotionEffect> effects = new ArrayList<>();

    public static PlayerSnapshot capture(Player p, String eventName) {
        PlayerSnapshot s = new PlayerSnapshot();
        s.uuid = p.getUniqueId();
        s.playerName = p.getName();
        s.eventName = eventName;
        s.time = System.currentTimeMillis();
        s.origin = StoredLocation.of(p.getLocation());
        s.inventory = ItemKit.capture(p.getInventory());
        s.gameMode = p.getGameMode();
        s.health = p.getHealth();
        s.food = p.getFoodLevel();
        s.saturation = p.getSaturation();
        s.level = p.getLevel();
        s.exp = p.getExp();
        s.allowFlight = p.getAllowFlight();
        s.flying = p.isFlying();
        s.effects = new ArrayList<>(p.getActivePotionEffects());
        return s;
    }

    public YamlConfiguration toYaml() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("uuid", uuid.toString());
        y.set("player", playerName);
        y.set("event", eventName);
        y.set("time", time);
        origin.save(y.createSection("origin"));
        inventory.save(y.createSection("inventory"));
        y.set("gamemode", gameMode.name());
        y.set("health", health);
        y.set("food", food);
        y.set("saturation", (double) saturation);
        y.set("level", level);
        y.set("exp", (double) exp);
        y.set("allow-flight", allowFlight);
        y.set("flying", flying);
        y.set("effects", effects);
        return y;
    }

    public static PlayerSnapshot fromYaml(YamlConfiguration y) {
        String id = y.getString("uuid");
        if (id == null) return null;
        PlayerSnapshot s = new PlayerSnapshot();
        s.uuid = UUID.fromString(id);
        s.playerName = y.getString("player", "?");
        s.eventName = y.getString("event", "?");
        s.time = y.getLong("time");
        s.origin = StoredLocation.load(y.getConfigurationSection("origin"));
        s.inventory = ItemKit.load(y.getConfigurationSection("inventory"));
        try { s.gameMode = GameMode.valueOf(y.getString("gamemode", "SURVIVAL")); } catch (IllegalArgumentException ignored) { }
        s.health = y.getDouble("health", 20);
        s.food = y.getInt("food", 20);
        s.saturation = (float) y.getDouble("saturation", 5);
        s.level = y.getInt("level");
        s.exp = (float) y.getDouble("exp");
        s.allowFlight = y.getBoolean("allow-flight");
        s.flying = y.getBoolean("flying");
        List<?> raw = y.getList("effects");
        if (raw != null) for (Object o : raw) if (o instanceof PotionEffect) s.effects.add((PotionEffect) o);
        return s;
    }

    public void restoreStats(Player p) {
        p.setGameMode(gameMode);
        for (PotionEffect e : new ArrayList<>(p.getActivePotionEffects())) p.removePotionEffect(e.getType());
        for (PotionEffect e : effects) p.addPotionEffect(e);
        p.setHealth(Math.max(1.0, Math.min(health, p.getMaxHealth())));
        p.setFoodLevel(food);
        p.setSaturation(saturation);
        p.setLevel(level);
        p.setExp(exp);
        p.setAllowFlight(allowFlight || gameMode == GameMode.CREATIVE || gameMode == GameMode.SPECTATOR);
        if (flying && p.getAllowFlight()) p.setFlying(true);
    }
}
