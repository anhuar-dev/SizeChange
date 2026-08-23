package dev.anhuar.util;

import lombok.Getter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ConfigUtil {

    private final JavaPlugin plugin;
    private final File configFile;
    private final String filePath;
    private final Set<String> freeformSections;

    @Getter
    private YamlConfiguration config;

    // IMPORTANT: freeformSections marks top-level keys whose *entries* are user
    // data, not a fixed schema known ahead of time (e.g. setting.yml's
    // DENY-REGION/SIZE-REGION — arbitrary WorldGuard region names the admin
    // chose). Those are only ever added wholesale if missing entirely; see the
    // comment in mergeMissingDefaults() for why they can never be deep-merged.
    // Every other top-level key is treated as fixed schema and gets deep-merged
    // on update — new sub-keys a later plugin version introduces (e.g. adding
    // ANIMATION.DURATION-TICKS to an ANIMATION section the admin already has)
    // get added automatically without touching anything already set.
    public ConfigUtil(JavaPlugin plugin, String filePath, String... freeformSections) {
        this.plugin = plugin;
        this.filePath = filePath;
        this.freeformSections = freeformSections.length == 0
                ? Collections.emptySet()
                : new HashSet<>(Arrays.asList(freeformSections));

        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create the plugin's data folder");
        }

        if (filePath.contains("/")) {
            String dirPath = filePath.substring(0, filePath.lastIndexOf("/"));
            File parentDir = new File(plugin.getDataFolder(), dirPath);
            if (!parentDir.exists() && !parentDir.mkdirs()) {
                plugin.getLogger().warning("Could not create directory: " + dirPath);
            }
        }

        configFile = new File(plugin.getDataFolder(), filePath);

        if (!configFile.exists()) {
            try {
                if (plugin.getResource(filePath) != null) {
                    plugin.saveResource(filePath, false);
                    plugin.getLogger().info("Configuration file created: " + filePath);
                } else {
                    if (configFile.createNewFile()) {
                        plugin.getLogger().info("Empty file created: " + filePath);
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Error creating configuration file: " + filePath);
                plugin.getLogger().severe("Reason: " + e.getMessage());
            }
        }

        reload();

        mergeMissingDefaults();
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    private void mergeMissingDefaults() {
        try (InputStream resource = plugin.getResource(filePath)) {
            if (resource == null) return;

            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(resource, StandardCharsets.UTF_8));

            boolean updated = false;
            for (String topKey : defaults.getKeys(false)) {
                if (freeformSections.contains(topKey)) {
                    // User-authored content — only add the whole section if it's
                    // missing entirely. Never descend into it once it exists: a
                    // deep merge can't tell "a new key this update introduced"
                    // apart from "an example entry the user deliberately
                    // deleted" — it would silently resurrect deleted entries.
                    if (!config.contains(topKey)) {
                        updated |= mergeLeaves(defaults, topKey);
                    }
                    continue;
                }

                // Fixed-schema key — safe to deep-merge regardless of whether
                // the user already has it: only ever adds a leaf the update
                // introduced that's genuinely missing, never touches a value
                // the user already set.
                updated |= mergeLeaves(defaults, topKey);
            }

            if (updated) {
                save();
                plugin.getLogger().info("Added new keys to " + filePath + " (plugin update).");
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not merge default keys for " + filePath);
        }
    }

    // Copies every leaf under `path` (or `path` itself, if it isn't a section)
    // from defaults into config, skipping any that config already has. Used
    // both for a path missing entirely and for one that exists but is missing
    // some of the keys a plugin update introduced — same rule either way.
    private boolean mergeLeaves(YamlConfiguration defaults, String path) {
        if (!defaults.isConfigurationSection(path)) {
            if (config.contains(path)) return false;
            config.set(path, defaults.get(path));
            return true;
        }

        boolean updated = false;
        for (String leaf : defaults.getConfigurationSection(path).getKeys(true)) {
            String fullPath = path + "." + leaf;
            if (defaults.isConfigurationSection(fullPath) || config.contains(fullPath)) continue;

            config.set(fullPath, defaults.get(fullPath));
            updated = true;
        }
        return updated;
    }

    public void save() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Error saving configuration file: " + configFile.getName());
            plugin.getLogger().severe("Reason: " + e.getMessage());
        }
    }

    public String getString(String path) {
        return config.getString(path);
    }

    public void set(String path, Object value) {
        config.set(path, value);
    }

    public boolean contains(String path) {
        return config.contains(path);
    }
}