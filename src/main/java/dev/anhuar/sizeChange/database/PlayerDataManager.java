package dev.anhuar.sizeChange.database;

import dev.anhuar.sizeChange.SizeChange;
import dev.anhuar.sizeChange.database.type.mongodb.MongoDbManager;
import dev.anhuar.sizeChange.database.type.mysql.MySQLManager;
import dev.anhuar.sizeChange.database.type.sqlite.SQLiteManager;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/*
 * SizeChange | PlayerDataManager
 *
 * @author 7Str1kes
 * @date 30/07/2025
 *
 * Copyright (c) 2025 7Str1kes. All rights reserved.
 */
@Getter
public class PlayerDataManager {

    private final SizeChange plugin;
    private final Map<UUID, PlayerData> playerDataMap = new HashMap<>();

    private PlayerDataStorage playerDataStorage;

    // Types
    private MySQLManager mySQLManager;
    private SQLiteManager sqLiteManager;
    private MongoDbManager mongoDbManager;

    public PlayerDataManager(SizeChange plugin) {
        this.plugin = plugin;
    }

    public boolean enable() {
        if (!plugin.getConfig().getBoolean("DATABASE.ENABLED", false)) {
            plugin.getLogger().warning("Base de datos deshabilitada en la configuración!");
            return false;
        }

        String databaseType = plugin.getConfig().getString("DATABASE.TYPE", "SQLITE").toLowerCase();

        try {
            switch (databaseType) {
                case "mysql" -> {
                    plugin.getLogger().info("Conectando a MySQL...");
                    mySQLManager = new MySQLManager(plugin);
                    mySQLManager.connect();
                    this.playerDataStorage = mySQLManager;
                    plugin.getLogger().info("Conexión a MySQL establecida correctamente!");
                    return true;
                }
                case "sqlite" -> {
                    plugin.getLogger().info("Conectando a SQLite...");
                    sqLiteManager = new SQLiteManager(plugin);
                    sqLiteManager.connect();
                    this.playerDataStorage = sqLiteManager;
                    plugin.getLogger().info("Conexión a SQLite establecida correctamente!");
                    return true;
                }
                case "mongodb" -> {
                    plugin.getLogger().info("Conectando a MongoDB...");
                    mongoDbManager = new MongoDbManager(plugin);
                    mongoDbManager.connect();
                    this.playerDataStorage = mongoDbManager;
                    plugin.getLogger().info("Conexión a MongoDB establecida correctamente!");
                    return true;
                }
                default -> {
                    plugin.getLogger().severe("Tipo de base de datos no válido: " + databaseType);
                    plugin.getLogger().severe("Tipos válidos: MYSQL, SQLITE, MONGODB");
                    return false;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error al conectar con la base de datos: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void disable() {
        try {
            switch (getDataType()) {
                case MYSQL -> {
                    if (mySQLManager != null) {
                        plugin.getLogger().info("Desconectando de MySQL...");
                        mySQLManager.disconnect();
                    }
                }
                case SQLITE -> {
                    if (sqLiteManager != null) {
                        plugin.getLogger().info("Desconectando de SQLite...");
                        sqLiteManager.disconnect();
                    }
                }
                case MONGODB -> {
                    if (mongoDbManager != null) {
                        plugin.getLogger().info("Desconectando de MongoDB...");
                        mongoDbManager.disconnect();
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error al desconectar de la base de datos: " + e.getMessage());
        }

        playerDataMap.clear();
        playerDataStorage = null;
    }

    public PlayerData getOrCreate(UUID uuid) {
        return playerDataMap.computeIfAbsent(uuid, id -> new PlayerData(plugin, id));
    }

    public void removeFromData(UUID uuid) {
        playerDataMap.remove(uuid);
    }

    public DataType getDataType() {
        try {
            return DataType.valueOf(plugin.getConfig().getString("DATABASE.TYPE", "SQLITE").toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Tipo de base de datos no válido, usando SQLite por defecto");
            return DataType.SQLITE;
        }
    }

    public boolean isConnected() {
        return playerDataStorage != null;
    }
}