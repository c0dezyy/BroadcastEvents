package com.codezyy.broadcastevents;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;

/** A location that stores the world by name so it survives world reloads. */
public final class StoredLocation {
    public final String world;
    public final double x, y, z;
    public final float yaw, pitch;

    public StoredLocation(String world, double x, double y, double z, float yaw, float pitch) {
        this.world = world; this.x = x; this.y = y; this.z = z; this.yaw = yaw; this.pitch = pitch;
    }

    public static StoredLocation of(Location l) {
        return new StoredLocation(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
    }

    /** Returns null if the world can't be found/loaded. */
    public Location toLocation() {
        if (world == null) return null;
        World w = Bukkit.getWorld(world);
        if (w == null && new File(Bukkit.getWorldContainer(), world).isDirectory()) {
            try { w = Bukkit.createWorld(new WorldCreator(world)); } catch (Exception ignored) { }
        }
        return w == null ? null : new Location(w, x, y, z, yaw, pitch);
    }

    public void save(ConfigurationSection s) {
        s.set("world", world); s.set("x", x); s.set("y", y); s.set("z", z);
        s.set("yaw", (double) yaw); s.set("pitch", (double) pitch);
    }

    public static StoredLocation load(ConfigurationSection s) {
        if (s == null || s.getString("world") == null) return null;
        return new StoredLocation(s.getString("world"), s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    public String pretty() {
        return world + " " + Math.round(x) + " " + Math.round(y) + " " + Math.round(z);
    }
}
