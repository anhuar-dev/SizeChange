package dev.anhuar.db;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOneModel;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.WriteModel;
import dev.anhuar.model.PlayerData;
import org.bson.Document;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class MongoDB implements Database {

    private final JavaPlugin plugin;
    private final String uri;
    private final String databaseName;
    private final String collectionName;

    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> collection;
    private final Map<UUID, PlayerData> cache;

    public MongoDB(JavaPlugin plugin, String uri, String databaseName, String collectionName) {
        this.plugin = plugin;
        this.uri = uri;
        this.databaseName = databaseName;
        this.collectionName = collectionName;
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public CompletableFuture<Boolean> connect() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                ConnectionString connectionString = new ConnectionString(uri);
                MongoClientSettings settings = MongoClientSettings.builder()
                        .applyConnectionString(connectionString)
                        .build();

                mongoClient = MongoClients.create(settings);
                database = mongoClient.getDatabase(databaseName);
                collection = database.getCollection(collectionName);

                database.listCollectionNames().first();

                plugin.getLogger().info("MongoDB database connected successfully");
                plugin.getLogger().info("Database: " + databaseName + " | Collection: " + collectionName);
                return true;
            } catch (Exception e) {
                plugin.getLogger().severe("Error connecting to MongoDB: " + e.getMessage());
                return false;
            }
        });
    }

    @Override
    public void disconnect() {
        if (mongoClient != null) {
            mongoClient.close();
            plugin.getLogger().info("MongoDB database disconnected");
        }
        cache.clear();
    }

    @Override
    public CompletableFuture<Void> savePlayer(PlayerData playerData) {
        return CompletableFuture.runAsync(() -> {
            try {
                cache.put(playerData.getUuid(), playerData);

                Document doc = new Document("_id", playerData.getUuid().toString())
                        .append("name", playerData.getName())
                        .append("size", playerData.getSize())
                        .append("lastUpdate", playerData.getLastUpdate());

                if (playerData.getTemporarySize() != null) {
                    doc.append("temporarySize", playerData.getTemporarySize());
                }
                if (playerData.getTemporaryExpiration() != null) {
                    doc.append("temporaryExpiration", playerData.getTemporaryExpiration());
                }

                collection.replaceOne(
                        Filters.eq("_id", playerData.getUuid().toString()),
                        doc,
                        new ReplaceOptions().upsert(true)
                );
            } catch (Exception e) {
                plugin.getLogger().warning("Error saving player to MongoDB: " + e.getMessage());
            }
        });
    }

    @Override
    public CompletableFuture<PlayerData> loadPlayer(UUID uuid) {
        // IMPORTANT: the cache lookup must stay OUTSIDE supplyAsync. When it was
        // inside, even a cache hit returned a future completed on a pool thread, so
        // every caller's .thenAccept() ran off the main thread — silently pushing
        // Bukkit API calls (DenyRegionHandler) into unsafe territory on every single
        // region crossing. Completing synchronously on a hit also avoids queuing a
        // pool task for what is just a map lookup.
        PlayerData cached = cache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                Document doc = collection.find(Filters.eq("_id", uuid.toString())).first();

                if (doc != null) {
                    PlayerData playerData = new PlayerData(
                            UUID.fromString(doc.getString("_id")),
                            doc.getString("name"),
                            doc.getDouble("size"),
                            doc.getLong("lastUpdate"),
                            doc.get("temporarySize") != null ? doc.getDouble("temporarySize") : null,
                            doc.get("temporaryExpiration") != null ? doc.getLong("temporaryExpiration") : null
                    );

                    cache.put(uuid, playerData);
                    return playerData;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error loading player from MongoDB: " + e.getMessage());
            }

            return null;
        });
    }

    @Override
    public void evictFromCache(UUID uuid) {
        cache.remove(uuid);
    }

    @Override
    public CompletableFuture<Void> saveAll(Map<UUID, PlayerData> data) {
        return CompletableFuture.runAsync(() -> {
            try {
                List<WriteModel<Document>> operations = new ArrayList<>();
                for (Map.Entry<UUID, PlayerData> entry : data.entrySet()) {
                    PlayerData playerData = entry.getValue();
                    cache.put(playerData.getUuid(), playerData);

                    Document doc = new Document("_id", playerData.getUuid().toString())
                            .append("name", playerData.getName())
                            .append("size", playerData.getSize())
                            .append("lastUpdate", playerData.getLastUpdate());

                    if (playerData.hasTemporarySize()) {
                        doc.append("temporarySize", playerData.getTemporarySize())
                                .append("temporaryExpiration", playerData.getTemporaryExpiration());
                    }

                    operations.add(new ReplaceOneModel<>(
                            Filters.eq("_id", playerData.getUuid().toString()),
                            doc,
                            new ReplaceOptions().upsert(true)
                    ));
                }

                if (!operations.isEmpty()) {
                    collection.bulkWrite(operations);
                    plugin.getLogger().info("Periodic save completed: " + data.size() + " players");
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error saving all data to MongoDB: " + e.getMessage());
            }
        });
    }
}
