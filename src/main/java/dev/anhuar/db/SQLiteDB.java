package dev.anhuar.db;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import dev.anhuar.model.AttributeModifierData;
import dev.anhuar.model.PlayerData;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Type;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SQLiteDB implements Database {

    private static final Gson GSON = new Gson();
    private static final Type MODIFIER_LIST_TYPE = new TypeToken<List<AttributeModifierData>>() {
    }.getType();

    private final JavaPlugin plugin;
    private final File dbFile;
    private Connection connection;
    private final Map<UUID, PlayerData> cache;
    private final Map<UUID, List<AttributeModifierData>> attributeCache;

    // IMPORTANT: single-threaded on purpose. A single JDBC Connection is not
    // thread-safe, and every DB call in this class runs on this executor. Do not
    // switch this to a pooled/multi-thread executor without also introducing a
    // connection pool — concurrent access to one Connection can corrupt query state.
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SizePlugin-SQLite");
        t.setDaemon(true);
        return t;
    });

    public SQLiteDB(JavaPlugin plugin, String fileName) {
        this.plugin = plugin;
        this.dbFile = new File(plugin.getDataFolder(), fileName);
        this.cache = new ConcurrentHashMap<>();
        this.attributeCache = new ConcurrentHashMap<>();
    }

    @Override
    public CompletableFuture<Boolean> connect() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                if (!dbFile.getParentFile().exists() && !dbFile.getParentFile().mkdirs()) {
                    plugin.getLogger().warning("Could not create folder for SQLite");
                }

                String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
                connection = DriverManager.getConnection(url);

                try (Statement stmt = connection.createStatement()) {
                    stmt.execute("PRAGMA journal_mode=WAL");
                    stmt.execute("PRAGMA synchronous=NORMAL");
                    stmt.execute("PRAGMA cache_size=10000");
                    stmt.execute("PRAGMA temp_store=MEMORY");
                    stmt.execute("PRAGMA page_size=4096");
                }

                String createTable = """
                        CREATE TABLE IF NOT EXISTS players (
                            uuid TEXT PRIMARY KEY NOT NULL,
                            name TEXT NOT NULL,
                            size REAL NOT NULL,
                            lastUpdate INTEGER NOT NULL,
                            temporarySize REAL,
                            temporaryExpiration INTEGER
                        )
                        """;

                try (Statement stmt = connection.createStatement()) {
                    stmt.execute(createTable);

                    try {
                        stmt.execute("ALTER TABLE players ADD COLUMN temporarySize REAL");
                        plugin.getLogger().info("Column 'temporarySize' added to the database");
                    } catch (SQLException e) {
                    }

                    try {
                        stmt.execute("ALTER TABLE players ADD COLUMN temporaryExpiration INTEGER");
                        plugin.getLogger().info("Column 'temporaryExpiration' added to the database");
                    } catch (SQLException e) {
                    }

                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_uuid ON players(uuid)");

                    stmt.execute("CREATE INDEX IF NOT EXISTS idx_name ON players(name)");

                    // ATTRIBUTES feature — one row per player, the modifier list
                    // held as a compact JSON array in `data`. Independent of the
                    // `players` table above.
                    stmt.execute("""
                            CREATE TABLE IF NOT EXISTS player_attributes (
                                uuid TEXT PRIMARY KEY NOT NULL,
                                name TEXT NOT NULL,
                                data TEXT NOT NULL,
                                lastUpdate INTEGER NOT NULL
                            )
                            """);
                }

                plugin.getLogger().info("SQLite database connected: " + dbFile.getName());
                plugin.getLogger().info("Location: " + dbFile.getAbsolutePath());
                return true;
            } catch (SQLException e) {
                plugin.getLogger().severe("Error connecting to SQLite: " + e.getMessage());
                return false;
            }
        }, dbExecutor);
    }

    @Override
    public void disconnect() {
        dbExecutor.shutdown();
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("SQLite database disconnected");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Error closing SQLite: " + e.getMessage());
        }
        cache.clear();
        attributeCache.clear();
    }

    @Override
    public CompletableFuture<Void> savePlayer(PlayerData playerData) {
        cache.put(playerData.getUuid(), playerData);

        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO players (uuid, name, size, lastUpdate, temporarySize, temporaryExpiration)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET
                        name = excluded.name,
                        size = excluded.size,
                        lastUpdate = excluded.lastUpdate,
                        temporarySize = excluded.temporarySize,
                        temporaryExpiration = excluded.temporaryExpiration
                    """;

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerData.getUuid().toString());
                pstmt.setString(2, playerData.getName());
                pstmt.setDouble(3, playerData.getSize());
                pstmt.setLong(4, playerData.getLastUpdate());

                if (playerData.getTemporarySize() != null) {
                    pstmt.setDouble(5, playerData.getTemporarySize());
                } else {
                    pstmt.setNull(5, java.sql.Types.REAL);
                }

                if (playerData.getTemporaryExpiration() != null) {
                    pstmt.setLong(6, playerData.getTemporaryExpiration());
                } else {
                    pstmt.setNull(6, java.sql.Types.INTEGER);
                }

                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Error saving player to SQLite: " + e.getMessage());
            }
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<PlayerData> loadPlayer(UUID uuid) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT uuid, name, size, lastUpdate, temporarySize, temporaryExpiration FROM players WHERE uuid = ?";

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                ResultSet rs = pstmt.executeQuery();

                if (rs.next()) {
                    PlayerData data = new PlayerData(
                            UUID.fromString(rs.getString("uuid")),
                            rs.getString("name"),
                            rs.getDouble("size"),
                            rs.getLong("lastUpdate"),
                            rs.getObject("temporarySize") != null ? rs.getDouble("temporarySize") : null,
                            rs.getObject("temporaryExpiration") != null ? rs.getLong("temporaryExpiration") : null
                    );

                    cache.put(uuid, data);
                    return data;
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Error loading player from SQLite: " + e.getMessage());
            }

            return null;
        }, dbExecutor);
    }

    @Override
    public void evictFromCache(UUID uuid) {
        cache.remove(uuid);
    }

    @Override
    public CompletableFuture<Void> saveAll(Map<UUID, PlayerData> data) {
        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO players (uuid, name, size, lastUpdate, temporarySize, temporaryExpiration)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET
                        name = excluded.name,
                        size = excluded.size,
                        lastUpdate = excluded.lastUpdate,
                        temporarySize = excluded.temporarySize,
                        temporaryExpiration = excluded.temporaryExpiration
                    """;

            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                for (Map.Entry<UUID, PlayerData> entry : data.entrySet()) {
                    PlayerData playerData = entry.getValue();
                    cache.put(playerData.getUuid(), playerData);
                    stmt.setString(1, playerData.getUuid().toString());
                    stmt.setString(2, playerData.getName());
                    stmt.setDouble(3, playerData.getSize());
                    stmt.setLong(4, playerData.getLastUpdate());
                    if (playerData.hasTemporarySize()) {
                        stmt.setDouble(5, playerData.getTemporarySize());
                        stmt.setLong(6, playerData.getTemporaryExpiration());
                    } else {
                        stmt.setNull(5, java.sql.Types.REAL);
                        stmt.setNull(6, java.sql.Types.INTEGER);
                    }
                    stmt.addBatch();
                }
                stmt.executeBatch();
                plugin.getLogger().info("Periodic save completed: " + data.size() + " players");
            } catch (SQLException e) {
                plugin.getLogger().severe("Error saving all data to SQLite: " + e.getMessage());
            }
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<List<AttributeModifierData>> loadAttributes(UUID uuid) {
        List<AttributeModifierData> cached = attributeCache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(new ArrayList<>(cached));
        }

        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT data FROM player_attributes WHERE uuid = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                ResultSet rs = pstmt.executeQuery();
                if (rs.next()) {
                    List<AttributeModifierData> parsed = GSON.fromJson(rs.getString("data"), MODIFIER_LIST_TYPE);
                    List<AttributeModifierData> result = parsed != null ? parsed : new ArrayList<>();
                    attributeCache.put(uuid, new ArrayList<>(result));
                    return result;
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Error loading attributes from SQLite: " + e.getMessage());
            }
            attributeCache.put(uuid, new ArrayList<>());
            return new ArrayList<>();
        }, dbExecutor);
    }

    @Override
    public CompletableFuture<Void> saveAttributes(UUID uuid, String name, List<AttributeModifierData> modifiers) {
        List<AttributeModifierData> snapshot = new ArrayList<>(modifiers);
        attributeCache.put(uuid, new ArrayList<>(snapshot));

        return CompletableFuture.runAsync(() -> {
            String sql = """
                    INSERT INTO player_attributes (uuid, name, data, lastUpdate)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET
                        name = excluded.name,
                        data = excluded.data,
                        lastUpdate = excluded.lastUpdate
                    """;

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, name);
                pstmt.setString(3, GSON.toJson(snapshot, MODIFIER_LIST_TYPE));
                pstmt.setLong(4, System.currentTimeMillis());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Error saving attributes to SQLite: " + e.getMessage());
            }
        }, dbExecutor);
    }

    @Override
    public void evictAttributesFromCache(UUID uuid) {
        attributeCache.remove(uuid);
    }
}
