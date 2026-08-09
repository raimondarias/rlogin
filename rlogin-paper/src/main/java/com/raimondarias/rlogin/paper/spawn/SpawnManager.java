package com.raimondarias.rlogin.paper.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Where players are put at each of the four moments rLogin knows about,
 * backed by a plain {@code spawns.yml} in the plugin's data folder.
 *
 * <p>One spawn per moment, and nothing else. An earlier version had two
 * layers — named points, then a separate step assigning a name to each
 * moment — which meant three commands and two concepts to get one player to
 * one place. The name never carried information the role didn't already
 * have, so it's gone: {@code /rlogin spawn set join} is the whole thing.</p>
 *
 * <p>Paper-only, per-backend state (each server has its own worlds), so
 * unlike accounts this never goes near the shared database. A moment with
 * no spawn set means "don't move them", which is Bukkit's own default: the
 * player appears wherever they last logged out.</p>
 */
public final class SpawnManager {

    public enum Role {
        /** Already-authenticated join: premium auto-login, remembered session, or bypass permission. */
        JOIN,
        /** The player's very first join ever ({@code Player#hasPlayedBefore()} was false). */
        FIRSTJOIN,
        /** Right after a successful {@code /login}. */
        LOGIN,
        /** Right after a successful {@code /register}. */
        REGISTER;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Empty if {@code raw} isn't one of the four; used to reject typos with a helpful message. */
        public static Optional<Role> parse(String raw) {
            for (Role role : values()) {
                if (role.key().equalsIgnoreCase(raw)) {
                    return Optional.of(role);
                }
            }
            return Optional.empty();
        }
    }

    /**
     * A spawn as it sits on disk. The world is kept by name and resolved
     * only when someone is actually sent there — resolving at load time
     * would silently drop spawns in worlds that a world-management plugin
     * loads after rLogin starts.
     */
    public record SpawnPoint(String world, double x, double y, double z, float yaw, float pitch) {

        public Optional<Location> toLocation() {
            World loaded = Bukkit.getWorld(world);
            return loaded == null ? Optional.empty()
                    : Optional.of(new Location(loaded, x, y, z, yaw, pitch));
        }

        public static SpawnPoint of(Location location) {
            return new SpawnPoint(location.getWorld().getName(), location.getX(), location.getY(),
                    location.getZ(), location.getYaw(), location.getPitch());
        }

        /** {@code world 123.5, 64.0, -87.2} — what an admin needs to recognise the place. */
        public String describe() {
            return String.format(Locale.ROOT, "%s  %.1f, %.1f, %.1f", world, x, y, z);
        }
    }

    private final File file;
    private final YamlConfiguration yaml;
    private final Map<Role, SpawnPoint> spawns = new EnumMap<>(Role.class);

    public SpawnManager(File dataFolder) {
        this.file = new File(dataFolder, "spawns.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        load();
    }

    private void load() {
        spawns.clear();
        for (Role role : Role.values()) {
            String path = role.key() + ".";
            String world = yaml.getString(path + "world");
            if (world == null) {
                continue;
            }
            spawns.put(role, new SpawnPoint(world,
                    yaml.getDouble(path + "x"), yaml.getDouble(path + "y"), yaml.getDouble(path + "z"),
                    (float) yaml.getDouble(path + "yaw"), (float) yaml.getDouble(path + "pitch")));
        }
    }

    public void set(Role role, Location location) {
        SpawnPoint point = SpawnPoint.of(location);
        spawns.put(role, point);

        String path = role.key() + ".";
        yaml.set(path + "world", point.world());
        yaml.set(path + "x", point.x());
        yaml.set(path + "y", point.y());
        yaml.set(path + "z", point.z());
        yaml.set(path + "yaw", (double) point.yaw());
        yaml.set(path + "pitch", (double) point.pitch());
        save();
    }

    /** {@code false} if that moment had no spawn to begin with. */
    public boolean remove(Role role) {
        if (spawns.remove(role) == null) {
            return false;
        }
        yaml.set(role.key(), null);
        save();
        return true;
    }

    public Optional<SpawnPoint> get(Role role) {
        return Optional.ofNullable(spawns.get(role));
    }

    /** Sends the player to this moment's spawn, if one is set and its world is loaded. Folia-safe. */
    public void teleportForRole(Player player, Role role) {
        SpawnPoint point = spawns.get(role);
        if (point == null) {
            return;
        }
        point.toLocation().ifPresent(player::teleportAsync);
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            Bukkit.getLogger().warning("[rLogin] Could not save spawns.yml: " + e.getMessage());
        }
    }
}
