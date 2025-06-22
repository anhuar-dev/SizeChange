package dev.anhuar.sizeChange.util;

/*
 * ========================================================
 * SizeChange - ConfigUtil.java
 *
 * @author Anhuar Ruiz | Anhuar Dev | myclass
 * @web https://anhuar.dev
 * @date 22/06/2025
 *
 * License: MIT License - See LICENSE file for details.
 * Copyright (c) 2025 Anhuar Dev. All rights reserved.
 * ========================================================
 */

import lombok.Getter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ConfigUtil {

    private final JavaPlugin plugin;
    private final String fileName;
    private final File configFile;

    @Getter
    private YamlConfiguration config;

    public ConfigUtil(JavaPlugin plugin, String filePath) {
        this.plugin = plugin;
        this.fileName = filePath;

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        File parentDir = null;
        if (filePath.contains("/")) {
            String dirPath = filePath.substring(0, filePath.lastIndexOf("/"));
            parentDir = new File(plugin.getDataFolder(), dirPath);
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }
        }

        configFile = new File(plugin.getDataFolder(), filePath);

        if (!configFile.exists()) {
            try {
                if (plugin.getResource(filePath) != null) {
                    plugin.saveResource(filePath, false);
                } else {
                    configFile.createNewFile();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Carga la configuración
        reload();
    }

    public void reload() {
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public void save() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Object get(String path) {
        return config.get(path);
    }

    public String getString(String path) {
        return config.getString(path);
    }

    public int getInt(String path) {
        return config.getInt(path);
    }

    public boolean getBoolean(String path) {
        return config.getBoolean(path);
    }

    public List<?> getList(String path) {
        return config.getList(path);
    }

    public void set(String path, Object value) {
        config.set(path, value);
    }

    public void setAndSave(String path, Object value) {
        set(path, value);
        save();
    }

    public boolean contains(String path) {
        return config.contains(path);
    }
}