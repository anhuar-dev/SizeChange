package dev.anhuar.sizeChange.manager;

/*
 * ========================================================
 * SizeChange - PlayerDataManager.java
 *
 * @author Anhuar Ruiz | Anhuar Dev | myclass
 * @web https://anhuar.dev
 * @date 22/06/2025
 *
 * License: MIT License - See LICENSE file for details.
 * Copyright (c) 2025 Anhuar Dev. All rights reserved.
 * ========================================================
 */

import com.google.common.collect.Maps;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import dev.anhuar.sizeChange.SizeChange;
import dev.anhuar.sizeChange.data.DPlayer;
import dev.anhuar.sizeChange.util.GsonUtil;
import lombok.Getter;
import org.bson.Document;
import org.bukkit.Bukkit;

import java.util.Map;
import java.util.UUID;

@Getter
public class PlayerDataManager {

    private final Map<UUID, DPlayer> playerDataMap;

    public PlayerDataManager() {
        this.playerDataMap = Maps.newConcurrentMap();
    }

    public void load(UUID uuid) {
        Document document = SizeChange.getInstance().getMongoHandler().getPlayers().find(Filters.eq("_id", uuid.toString())).first();

        if (document == null) {
            DPlayer playerData = new DPlayer();
            playerData.setName(getPlayerName(uuid));
            playerDataMap.put(uuid, playerData);
            save(uuid);
            return;
        }

        Document data = document.get("data", Document.class);
        Object object = GsonUtil.parseJsonString(data.toJson(GsonUtil.getWriterSettings()), DPlayer.class);
        DPlayer playerData = (DPlayer) object;

        String currentName = getPlayerName(uuid);
        if (!currentName.equals(playerData.getName())) {
            playerData.setName(currentName);
        }

        playerDataMap.put(uuid, playerData);
    }

    public void save(UUID uuid) {

        Bukkit.getScheduler().runTaskAsynchronously(SizeChange.getInstance(), () -> {

            DPlayer playerData = playerDataMap.get(uuid);

            String jsonData = GsonUtil.getGson().toJson(playerDataMap.get(uuid));
            Document document = Document.parse(jsonData);
            Document newDocument = new Document();

            playerData.setName(getPlayerName(uuid));
            newDocument.put("_id", uuid.toString());
            newDocument.put("data", document);
            SizeChange.getInstance().getMongoHandler().getPlayers().replaceOne(Filters.eq("_id", uuid.toString()), newDocument, new ReplaceOptions().upsert(true));
        });

    }

    public DPlayer getPlayerData(UUID uuid) {
        return playerDataMap.get(uuid);
    }

    private String getPlayerName(UUID uuid) {
        return Bukkit.getOfflinePlayer(uuid).getName();
    }
}