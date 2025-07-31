package dev.anhuar.sizeChange.database.type.mysql;

import dev.anhuar.sizeChange.SizeChange;
import dev.anhuar.sizeChange.database.PlayerData;
import dev.anhuar.sizeChange.database.PlayerDataStorage;
import lombok.Getter;

import java.sql.*;

/*
 * SizeChange | MySQLManager
 *
 * @author 7Str1kes
 * @date 30/07/2025
 *
 * Copyright (c) 2025 7Str1kes. All rights reserved.
 */
@Getter
public class MySQLManager implements PlayerDataStorage {

    private final SizeChange plugin;

    private Connection connection;

    public MySQLManager(SizeChange plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        try {
            String host = plugin.getConfig().getString("DATABASE.MYSQL.HOST");
            int port = plugin.getConfig().getInt("DATABASE.MYSQL.PORT");
            String database = plugin.getConfig().getString("DATABASE.MYSQL.DATABASE");
            String username = plugin.getConfig().getString("DATABASE.MYSQL.USERNAME");
            String password = plugin.getConfig().getString("DATABASE.MYSQL.PASSWORD");

            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true";
            connection = DriverManager.getConnection(url, username, password);

            String sql = "CREATE TABLE IF NOT EXISTS player_data (" +
                    "uuid VARCHAR(36) PRIMARY KEY," +
                    "displayName VARCHAR(16)," +
                    "size FLOAT DEFAULT 0.0)";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.executeUpdate();
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
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
        return 0.0f;
    }

    @Override
    public void saveData(PlayerData playerData) {
        try {
            String sql = "INSERT INTO player_data (uuid, displayName, size) VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE displayName=VALUES(displayName), size=VALUES(size)";
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