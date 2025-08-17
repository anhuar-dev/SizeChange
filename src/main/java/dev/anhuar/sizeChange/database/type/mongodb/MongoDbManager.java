package dev.anhuar.sizeChange.database.type.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import dev.anhuar.sizeChange.SizeChange;
import dev.anhuar.sizeChange.database.PlayerData;
import dev.anhuar.sizeChange.database.PlayerDataStorage;
import lombok.Getter;
import org.bson.Document;

/*
 * SizeChange | MongoDbManager
 *
 * @author 7Str1kes
 * @date 30/07/2025
 *
 * Copyright (c) 2025 7Str1kes. All rights reserved.
 */
@Getter
public class MongoDbManager implements PlayerDataStorage {

    private final SizeChange plugin;

    private MongoClient mongoClient;
    private MongoDatabase database;
    private MongoCollection<Document> collection;

    public MongoDbManager(SizeChange plugin) {
        this.plugin = plugin;
    }

    public void connect() {
        try {
            String uri = plugin.getConfig().getString("DATABASE.MONGO.URI");
            String databaseName = plugin.getConfig().getString("DATABASE.MONGO.DATABASE");
            String collectionName = plugin.getConfig().getString("DATABASE.MONGO.COLLECTION");

            mongoClient = MongoClients.create(uri);
            database = mongoClient.getDatabase(databaseName);
            collection = database.getCollection(collectionName);

            // Create index on uuid field for better performance
            collection.createIndex(new Document("uuid", 1));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void disconnect() {
        try {
            if (mongoClient != null) {
                mongoClient.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean exists(PlayerData data) {
        try {
            Document query = new Document("uuid", data.getUuid().toString());
            return collection.countDocuments(query) > 0;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public void saveData(PlayerData playerData) {
        try {
            Document document = new Document()
                    .append("uuid", playerData.getUuid().toString())
                    .append("displayName", plugin.getServer().getOfflinePlayer(playerData.getUuid()).getName())
                    .append("size", getSize(playerData));

            Document filter = new Document("uuid", playerData.getUuid().toString());
            collection.replaceOne(filter, document, new ReplaceOptions().upsert(true));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void loadData(PlayerData playerData) {
        try {
            Document query = new Document("uuid", playerData.getUuid().toString());
            Document result = collection.find(query).first();

            if (result != null) {
                String displayName = result.getString("displayName");
                if (displayName != null) {
                    playerData.setDisplayName(displayName);
                }

                Double size = result.getDouble("size");
                if (size != null) {
                    playerData.setSize(size.floatValue());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public float getSize(PlayerData playerData) {
        try {
            Document query = new Document("uuid", playerData.getUuid().toString());
            Document result = collection.find(query).first();

            if (result != null) {
                Double size = result.getDouble("size");
                if (size != null) {
                    return size.floatValue();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0.0f;
    }

    @Override
    public void setSize(PlayerData playerData, float size) {
        try {
            if (!exists(playerData)) {
                saveData(playerData);
            }

            Document filter = new Document("uuid", playerData.getUuid().toString());
            Document update = new Document("$set", new Document("size", size));
            collection.updateOne(filter, update);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}