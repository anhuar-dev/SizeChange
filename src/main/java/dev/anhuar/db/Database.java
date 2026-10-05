package dev.anhuar.db;

import dev.anhuar.model.AttributeModifierData;
import dev.anhuar.model.PlayerData;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface Database {

    CompletableFuture<Boolean> connect();

    void disconnect();

    CompletableFuture<Void> savePlayer(PlayerData playerData);

    CompletableFuture<PlayerData> loadPlayer(UUID uuid);

    // ---- ATTRIBUTES feature -------------------------------------------------
    // Stored separately from PlayerData on purpose: PlayerData carries fragile
    // permanent-size dirty-tracking and is reconstructed by hand in several
    // places, so bolting the modifier list onto it invites silent data loss.
    // Every mutation is write-through, same as PlayerDataManager.setSize.

    CompletableFuture<List<AttributeModifierData>> loadAttributes(UUID uuid);

    CompletableFuture<Void> saveAttributes(UUID uuid, String name, List<AttributeModifierData> modifiers);

    void evictAttributesFromCache(UUID uuid);

    // IMPORTANT: implementations keep an in-memory cache that is only cleared on
    // disconnect(). Without evicting per player on quit, that cache grows for every
    // unique player who ever joins and is never reclaimed until a server restart.
    void evictFromCache(UUID uuid);

    CompletableFuture<Void> saveAll(java.util.Map<UUID, PlayerData> data);
}
