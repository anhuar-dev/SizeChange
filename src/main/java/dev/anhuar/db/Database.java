package dev.anhuar.db;

import dev.anhuar.model.PlayerData;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface Database {

    CompletableFuture<Boolean> connect();

    void disconnect();

    CompletableFuture<Void> savePlayer(PlayerData playerData);

    CompletableFuture<PlayerData> loadPlayer(UUID uuid);

    // IMPORTANT: implementations keep an in-memory cache that is only cleared on
    // disconnect(). Without evicting per player on quit, that cache grows for every
    // unique player who ever joins and is never reclaimed until a server restart.
    void evictFromCache(UUID uuid);

    CompletableFuture<Void> saveAll(java.util.Map<UUID, PlayerData> data);
}
