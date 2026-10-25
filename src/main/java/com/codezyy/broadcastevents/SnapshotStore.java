package com.codezyy.broadcastevents;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Persistent per-player snapshot files in plugins/BroadcastEvents/snapshots/.
 * <uuid>.yml is the data, <uuid>.log.txt is a readable item log. Both are deleted after a successful restore.
 */
public final class SnapshotStore {
    private final File dir;
    private final Logger log;

    public SnapshotStore(File dataFolder, Logger log) {
        this.dir = new File(dataFolder, "snapshots");
        this.log = log;
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
    }

    private File yml(UUID id) { return new File(dir, id + ".yml"); }
    private File txt(UUID id) { return new File(dir, id + ".log.txt"); }

    public boolean exists(UUID id) { return yml(id).exists(); }

    public void save(PlayerSnapshot s) throws IOException {
        writeAtomic(yml(s.uuid), s.toYaml().saveToString().getBytes(StandardCharsets.UTF_8));
        writeAtomic(txt(s.uuid), buildLog(s).getBytes(StandardCharsets.UTF_8));
    }

    /** Writes to a temp file, forces it to disk, then moves it into place so a crash can never leave a half file. */
    private void writeAtomic(File target, byte[] data) throws IOException {
        File tmp = new File(target.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            out.write(data);
            out.flush();
            out.getFD().sync();
        }
        try {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ex) {
            Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Returns null if there is no file or it can't be read (the file is kept in that case). */
    public PlayerSnapshot load(UUID id) {
        File f = yml(id);
        if (!f.exists()) return null;
        try {
            YamlConfiguration y = new YamlConfiguration();
            y.load(f);
            return PlayerSnapshot.fromYaml(y);
        } catch (Exception e) {
            log.severe("Could not read snapshot " + f.getName() + ": " + e.getMessage());
            return null;
        }
    }

    public void delete(UUID id) {
        //noinspection ResultOfMethodCallIgnored
        yml(id).delete();
        //noinspection ResultOfMethodCallIgnored
        txt(id).delete();
    }

    private String buildLog(PlayerSnapshot s) {
        StringBuilder sb = new StringBuilder();
        sb.append("Inventory log (deleted automatically after a successful restore)\n");
        sb.append("Player:   ").append(s.playerName).append(" (").append(s.uuid).append(")\n");
        sb.append("Event:    ").append(s.eventName).append('\n');
        sb.append("Saved at: ").append(Instant.ofEpochMilli(s.time)).append('\n');
        sb.append("Return to: ").append(s.origin.pretty()).append('\n');
        sb.append("Gamemode: ").append(s.gameMode).append("  Health: ").append(s.health)
                .append("  Food: ").append(s.food).append("  XP level: ").append(s.level).append("\n\n");
        java.util.List<String> lines = s.inventory.describe();
        if (lines.isEmpty()) sb.append("(inventory was empty)\n");
        for (String l : lines) sb.append(l).append('\n');
        return sb.toString();
    }
}
