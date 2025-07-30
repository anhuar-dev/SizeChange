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
        if (plugin.getConfig().getBoolean("DATABASE.ENABLED")) {
            switch (plugin.getConfig().getString("DATABASE.TYPE").toLowerCase()) {
                case "mysql" -> {
                    mySQLManager = new MySQLManager(plugin);
                    mySQLManager.connect();

                    this.playerDataStorage = mySQLManager;
                    return true;
                }
                case "sqlite" -> {
                    sqLiteManager = new SQLiteManager(plugin);
                    sqLiteManager.connect();

                    this.playerDataStorage = sqLiteManager;
                    return true;
                }
                case "mongodb" -> {
                    mongoDbManager = new MongoDbManager(plugin);
                    mongoDbManager.connect();

                    this.playerDataStorage = mongoDbManager;
                    return true;
                }
            }
        }

        return false;
    }

    public void disable() {
        switch (getDataType()) {
            case MYSQL -> {
                if (mySQLManager != null) {
                    mySQLManager.disconnect();
                }
            }
            case SQLITE -> {
                if (sqLiteManager != null) {
                    sqLiteManager.disconnect();
                }
            }
        }
    }

    public PlayerData getOrCreate(UUID uuid) {
        return playerDataMap.computeIfAbsent(uuid, id -> new PlayerData(plugin, id));
    }

    public void removeFromData(UUID uuid) {
        playerDataMap.remove(uuid);
    }

    public DataType getDataType() {
        return DataType.valueOf(plugin.getConfig().getString("DATABASE.TYPE").toUpperCase());
    }
}