package dev.anhuar.sizeChange.database.type.sqlite;

import dev.anhuar.sizeChange.SizeChange;
import dev.anhuar.sizeChange.database.PlayerData;
import dev.anhuar.sizeChange.database.PlayerDataStorage;
import lombok.Getter;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/*
 * SizeChange | SQLiteManager
 *
 * @author 7Str1kes
 * @date 30/07/2025
 *
 * Copyright (c) 2025 7Str1kes. All rights reserved.
 */
@Getter
public class SQLiteManager implements PlayerDataStorage {

    private final SizeChange plugin;

    private Connection connection;

    public SQLiteManager(SizeChange plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        try {
            String fileName = plugin.getConfig().getString("DATABASE.SQLITE.FILE");

            File dbFile = new File(plugin.getDataFolder(), fileName);
            if (!dbFile.getParentFile().exists()) {
                dbFile.getParentFile().mkdirs();
            }

            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);

            String sql = "CREATE TABLE IF NOT EXISTS player_data (" +
                    "uuid TEXT PRIMARY KEY," +
                    "displayName TEXT," +
                    "size FLOAT DEFAULT 0)";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.executeUpdate();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed())
                connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private boolean exists(PlayerData data) {
        try (PreparedStatement stmt = connection.prepareStatement("SELECT uuid FROM player_data WHERE uuid=?")) {
            stmt.setString(1, data.getUuid().toString());
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void upsert(PlayerData data, String column, float value) {
        try {
            if (!exists(data)) {
                saveData(data);
            }
            String sql = "UPDATE player_data SET " + column + "=? WHERE uuid=?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setFloat(1, value);
                stmt.setString(2, data.getUuid().toString());
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private float getStat(PlayerData data, String column) {
        try {
            String sql = "SELECT " + column + " FROM player_data WHERE uuid=?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, data.getUuid().toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getFloat(column);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public void saveData(PlayerData playerData) {
        try {
            String sql = "REPLACE INTO player_data (uuid, displayName, size) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerData.getUuid().toString());
                stmt.setString(2, plugin.getServer().getOfflinePlayer(playerData.getUuid()).getName());
                stmt.setFloat(3, getSize(playerData));
                stmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void loadData(PlayerData playerData) {
        try {
            String sql = "SELECT * FROM player_data WHERE uuid=?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, playerData.getUuid().toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    playerData.setDisplayName(rs.getString("displayName"));
                    playerData.setSize(rs.getFloat("size"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public float getSize(PlayerData playerData) {
        return getStat(playerData, "size");
    }

    @Override
    public void setSize(PlayerData playerData, float size) {
        upsert(playerData, "size", size);
    }
}