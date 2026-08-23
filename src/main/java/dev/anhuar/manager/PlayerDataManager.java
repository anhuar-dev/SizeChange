package dev.anhuar.manager;

import dev.anhuar.SizeChange;
import dev.anhuar.db.Database;
import dev.anhuar.db.MongoDB;
import dev.anhuar.db.SQLiteDB;
import dev.anhuar.handler.DenyRegionHandler;
import dev.anhuar.model.PlayerData;
import dev.anhuar.model.TemporarySizeData;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager {

    private final SizeChange plugin;
    private Database database;
    private CompletableFuture<Boolean> connectFuture;

    private final Map<UUID, Double> lastSavedPermanentSize = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSavedTempExpiry = new ConcurrentHashMap<>();

    public PlayerDataManager(SizeChange plugin) {
        this.plugin = plugin;
        initializeDatabase();
    }

    private void initializeDatabase() {
        String type = plugin.getSetting().getString("DATABASE.TYPE").toUpperCase();

        switch (type) {
            case "MONGODB" -> {
                String uri = plugin.getSetting().getString("DATABASE.MONGODB.URI");
                String dbName = plugin.getSetting().getString("DATABASE.MONGODB.DATABASE");
                String collection = plugin.getSetting().getString("DATABASE.MONGODB.COLLECTION");
                database = new MongoDB(plugin, uri, dbName, collection);
                plugin.getLogger().info("Using MongoDB as the database");
            }
            case "SQLITE" -> {
                String fileName = plugin.getSetting().getString("DATABASE.SQLITE.FILE");
                database = new SQLiteDB(plugin, fileName);
                plugin.getLogger().info("Using SQLite as the database");
            }
            default -> {
                plugin.getLogger().warning("Unrecognized database type: '" + type + "'. Defaulting to SQLite.");
                String fileName = plugin.getSetting().getString("DATABASE.SQLITE.FILE");
                database = new SQLiteDB(plugin, fileName);
                plugin.getLogger().info("Using SQLite as the database");
            }
        }

        connectFuture = database.connect();
        connectFuture.thenAccept(success -> {
            if (success) {
                plugin.getLogger().info("Database initialized successfully");
                plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin,
                        () -> saveAllActiveData().join(), 6000L, 6000L);
                plugin.getLogger().info("Periodic save enabled (every 5 minutes)");
            } else {
                plugin.getLogger().severe("Error initializing the database");
            }
        });
    }

    public CompletableFuture<PlayerData> getOrCreatePlayerData(Player player) {
        return database.loadPlayer(player.getUniqueId()).thenApply(data -> {
            if (data == null) {
                PlayerData newData = new PlayerData(player.getUniqueId(), player.getName(), 1.0);
                database.savePlayer(newData);
                markSaved(player.getUniqueId(), 1.0, null);
                return newData;
            }
            Long tempExpiry = data.hasTemporarySize() ? data.getTemporaryExpiration() : null;
            markSaved(player.getUniqueId(), data.getSize(), tempExpiry);
            return data;
        });
    }

    public CompletableFuture<Void> savePlayerSize(UUID uuid, String name, double size) {
        PlayerData data = new PlayerData(uuid, name, size);
        return database.savePlayer(data).thenRun(() -> markSaved(uuid, size, null));
    }

    public CompletableFuture<Double> getPlayerSize(UUID uuid) {
        return database.loadPlayer(uuid).thenApply(data -> data != null ? data.getSize() : 1.0);
    }

    public void shutdown() {
        if (database != null) {
            database.disconnect();
        }
    }

    public Database getDatabase() {
        return database;
    }

    public CompletableFuture<Boolean> getConnectFuture() {
        return connectFuture;
    }

    public void setSize(UUID uuid, double size) {
        database.loadPlayer(uuid).thenAccept(data -> {
            if (data != null) {
                data.setSize(size);
                database.savePlayer(data).thenRun(() -> markSaved(uuid, size, null));
            }
        });
    }

    // IMPORTANT: exposes the last permanent size actually confirmed in the DB, for
    // callers that need the player's true permanent while their visual size is a
    // stand-in (a WorldGuard region override) and can't do an async DB read at that
    // point. Falls back to the live visual size only if we somehow have no record
    // yet (shouldn't happen for an online player past getOrCreatePlayerData()).
    public double getLastKnownPermanent(UUID uuid, double fallback) {
        Double known = lastSavedPermanentSize.get(uuid);
        return known != null ? known : fallback;
    }

    // IMPORTANT: the player's "real" permanent size is not always what's currently
    // visually applied to them (sizeManager.getSize()). While a temporary size or a
    // WorldGuard region override is active, the visual size is a stand-in, not the
    // value that should be persisted. Resolution order below:
    //   1. Active temporary size     -> use its stored originalSize (pre-temp value)
    //   2. Inside a size/deny region -> use the last confirmed DB permanent, since
    //      the visual is the region's forced value, not the real one
    //   3. Neither                   -> the visual size IS the real permanent size
    // Getting this wrong means a player's permanent size silently drifts to whatever
    // temporary/region override happened to be active at save time.
    private Map<UUID, PlayerData> computeDirtyData() {
        Map<UUID, PlayerData> dirtyData = new HashMap<>();
        SizeManager sizeManager = plugin.getManagerHandler().getSizeManager();
        TemporarySizeManager tempManager = plugin.getManagerHandler().getTemporarySizeManager();

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            double permanentSize;
            Long tempExpiry = null;

            if (tempManager.hasTemporarySize(uuid)) {
                TemporarySizeData tempData = tempManager.getTemporarySize(uuid);
                permanentSize = tempData.getOriginalSize();
                tempExpiry = tempData.getExpirationTime();
            } else if (DenyRegionHandler.hasVisualOverride(uuid)) {
                permanentSize = getLastKnownPermanent(uuid, sizeManager.getSize(player));
            } else {
                permanentSize = sizeManager.getSize(player);
            }

            if (!isDirty(uuid, permanentSize, tempExpiry)) continue;

            PlayerData data = new PlayerData(uuid, player.getName(), permanentSize);
            if (tempExpiry != null) {
                TemporarySizeData tempData = tempManager.getTemporarySize(uuid);
                data.setTemporarySize(tempData.getTemporarySize(), tempExpiry);
            }

            dirtyData.put(uuid, data);
        }
        return dirtyData;
    }

    private void persistDirtyData(Map<UUID, PlayerData> dirtyData, String logContext) throws Exception {
        if (dirtyData == null || dirtyData.isEmpty()) return;

        database.saveAll(dirtyData).get(5, java.util.concurrent.TimeUnit.SECONDS);
        dirtyData.forEach((uuid, data) ->
                markSaved(uuid, data.getSize(), data.hasTemporarySize() ? data.getTemporaryExpiration() : null));
        plugin.getLogger().info(logContext + ": " + dirtyData.size() + " players updated");
    }

    public CompletableFuture<Void> saveAllActiveData() {
        java.util.concurrent.Future<Map<UUID, PlayerData>> syncFuture;
        try {
            syncFuture = plugin.getServer().getScheduler().callSyncMethod(plugin, this::computeDirtyData);
        } catch (Exception e) {
            plugin.getLogger().warning("Error scheduling periodic save: " + e.getMessage());
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            try {
                Map<UUID, PlayerData> dirtyData = syncFuture.get(5, java.util.concurrent.TimeUnit.SECONDS);
                persistDirtyData(dirtyData, "Periodic save");
            } catch (Exception e) {
                plugin.getLogger().warning("Error during periodic save: " + e.getMessage());
            }
        });
    }

    // IMPORTANT: do not refactor this to reuse saveAllActiveData()'s callSyncMethod
    // path. This runs during onDisable(), which is already executing on the main
    // thread. callSyncMethod schedules work to run on the main thread and blocks
    // waiting for it — calling it here would make the main thread wait on itself,
    // deadlocking the server shutdown. That's why this computes dirty data directly
    // instead of going through the scheduler.
    public void flushOnDisable() {
        try {
            persistDirtyData(computeDirtyData(), "Final save on disable");
        } catch (Exception e) {
            plugin.getLogger().warning("Error during final save on disable: " + e.getMessage());
        }
    }

    private boolean isDirty(UUID uuid, double permanentSize, Long tempExpiry) {
        Double lastPerm = lastSavedPermanentSize.get(uuid);
        if (lastPerm == null) return true;
        if (Double.compare(lastPerm, permanentSize) != 0) return true;

        Long lastTemp = lastSavedTempExpiry.get(uuid);
        if (tempExpiry == null) return lastTemp != null;
        return !tempExpiry.equals(lastTemp);
    }

    public void markSaved(UUID uuid, double permanentSize, Long tempExpiry) {
        lastSavedPermanentSize.put(uuid, permanentSize);
        if (tempExpiry == null) {
            lastSavedTempExpiry.remove(uuid);
        } else {
            lastSavedTempExpiry.put(uuid, tempExpiry);
        }
    }

    public void clearPlayerCache(UUID uuid) {
        lastSavedPermanentSize.remove(uuid);
        lastSavedTempExpiry.remove(uuid);
    }
}
