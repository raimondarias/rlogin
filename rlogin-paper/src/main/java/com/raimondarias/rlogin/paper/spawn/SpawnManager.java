package com.raimondarias.rlogin.paper.spawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Named spawn points plus per-purpose role assignments
 * ({@code join}/{@code firstjoin}/{@code login}/{@code register}), backed by
 * a simple {@code spawns.yml} in the plugin's data folder.
 *
 * <p>This is Paper-only, per-backend state — each server has its own
 * worlds — so unlike accounts it does not go through the shared
 * {@code Storage}/database layer. If a role has no spawn assigned (or the
 * assigned spawn no longer exists), nothing happens: the player is simply
 * left wherever Bukkit would put them by default, i.e. where they last
 * disconnected.</p>
 */
public final class SpawnManager {

    public enum Role {
        /** Already-authenticated join (premium auto-login, remembered session, bypass permission). */
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
    }

    private final File file;
    private final YamlConfiguration yaml;
    private final Map<String, Location> points = new LinkedHashMap<>();
    private final Map<Role, String> roles = new LinkedHashMap<>();

    public SpawnManager(File dataFolder) {
        this.file = new File(dataFolder, "spawns.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        load();
    }

    private void load() {
        points.clear();
        roles.clear();

        ConfigurationSection pointsSection = yaml.getConfigurationSection("points");
        if (pointsSection != null) {
            for (String name : pointsSection.getKeys(false)) {
                String path = "points." + name + ".";
                String worldName = yaml.getString(path + "world");
                World world = worldName != null ? Bukkit.getWorld(worldName) : null;
                if (world == null) {
                    continue; // World not loaded (yet) — it'll just be unavailable until it is.
                }
                Location location = new Location(world,
                        yaml.getDouble(path + "x"), yaml.getDouble(path + "y"), yaml.getDouble(path + "z"),
                        (float) yaml.getDouble(path + "yaw"), (float) yaml.getDouble(path + "pitch"));
                points.put(name.toLowerCase(Locale.ROOT), location);
            }
        }

        for (Role role : Role.values()) {
            String assigned = yaml.getString("roles." + role.key());
            if (assigned != null) {
                roles.put(role, assigned.toLowerCase(Locale.ROOT));
            }
        }
    }

    public void set(String name, Location location) {
        String key = name.toLowerCase(Locale.ROOT);
        points.put(key, location.clone());

        String path = "points." + key + ".";
        yaml.set(path + "world", location.getWorld().getName());
        yaml.set(path + "x", location.getX());
        yaml.set(path + "y", location.getY());
        yaml.set(path + "z", location.getZ());
        yaml.set(path + "yaw", (double) location.getYaw());
        yaml.set(path + "pitch", (double) location.getPitch());
        save();
    }

    public boolean remove(String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (!points.containsKey(key)) {
            return false;
        }
        points.remove(key);
        yaml.set("points." + key, null);

        for (Role role : Role.values()) {
            if (key.equals(roles.get(role))) {
                roles.remove(role);
                yaml.set("roles." + role.key(), null);
            }
        }
        save();
        return true;
    }

    public Set<String> names() {
        return points.keySet();
    }

    public Optional<Location> get(String name) {
        return Optional.ofNullable(points.get(name.toLowerCase(Locale.ROOT)));
    }

    /** {@code false} if no spawn with that name exists. */
    public boolean assignRole(Role role, String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (!points.containsKey(key)) {
            return false;
        }
        roles.put(role, key);
        yaml.set("roles." + role.key(), key);
        save();
        return true;
    }

    public void clearRole(Role role) {
        roles.remove(role);
        yaml.set("roles." + role.key(), null);
        save();
    }

    public Optional<String> roleAssignment(Role role) {
        return Optional.ofNullable(roles.get(role));
    }

    /** Teleports the player to this role's assigned spawn, if any and if it still exists. Folia-safe. */
    public void teleportForRole(Player player, Role role) {
        String name = roles.get(role);
        if (name == null) {
            return;
        }
        Location location = points.get(name);
        if (location != null) {
            player.teleportAsync(location);
        }
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException e) {
            Bukkit.getLogger().warning("[rLogin] Could not save spawns.yml: " + e.getMessage());
        }
    }
}
