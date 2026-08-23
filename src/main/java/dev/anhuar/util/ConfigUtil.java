package dev.anhuar.util;

import lombok.Getter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class ConfigUtil {

    private final JavaPlugin plugin;
    private final File configFile;
    private final String filePath;

    @Getter
    private YamlConfiguration config;

    public ConfigUtil(JavaPlugin plugin, String filePath) {
        this.plugin = plugin;
        this.filePath = filePath;

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

            // IMPORTANT: only merges top-level keys that are entirely missing from
            // the user's file — it never descends into a key/section the user
            // already has. This is intentional: for a section like SIZE-REGION,
            // whose entries are user data rather than fixed schema, a deep merge
            // can't tell a "new key added by a plugin update" apart from "an
            // example entry the user deliberately deleted" — it would silently
            // resurrect deleted entries. Only add new top-level keys, and copy
            // them in full via copyDefaultKey() below.
            boolean updated = false;
            for (String topKey : defaults.getKeys(false)) {
                if (config.contains(topKey)) continue;

                copyDefaultKey(defaults, topKey);
                updated = true;
            }

            if (updated) {
                save();
                plugin.getLogger().info("Added new keys to " + filePath + " (plugin update).");
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not merge default keys for " + filePath);
        }
    }

    private void copyDefaultKey(YamlConfiguration defaults, String path) {
        if (defaults.isConfigurationSection(path)) {
            for (String leaf : defaults.getConfigurationSection(path).getKeys(true)) {
                String fullPath = path + "." + leaf;
                if (!defaults.isConfigurationSection(fullPath)) {
                    config.set(fullPath, defaults.get(fullPath));
                }
            }
        } else {
            config.set(path, defaults.get(path));
        }
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