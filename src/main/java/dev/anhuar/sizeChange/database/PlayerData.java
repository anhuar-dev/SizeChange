package dev.anhuar.sizeChange.database;

import dev.anhuar.sizeChange.SizeChange;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/*
 * SizeChange | PlayerData
 *
 * @author 7Str1kes
 * @date 30/07/2025
 *
 * Copyright (c) 2025 7Str1kes. All rights reserved.
 */
@Setter
@Getter
public class PlayerData {

    private final SizeChange plugin;

    private final UUID uuid;
    private String displayName;
    private float size = 1.0f;

    public PlayerData(SizeChange plugin, UUID uuid) {
        this.plugin = plugin;
        this.uuid = uuid;
    }

    // Getters
    public float getSize() {
        PlayerDataStorage storage = SizeChange.getInstance().getPlayerDataManager().getPlayerDataStorage();
        if (storage != null) {
            return storage.getSize(this);
        }
        return this.size;
    }

    // Setters
    public void setSize(float amount) {
        PlayerDataStorage storage = SizeChange.getInstance().getPlayerDataManager().getPlayerDataStorage();
        if (storage != null) {
            storage.setSize(this, amount);
        }
        this.size = amount;
    }
}