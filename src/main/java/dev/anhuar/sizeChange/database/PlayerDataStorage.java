package dev.anhuar.sizeChange.database;

/*
 * SizeChange | PlayerDataStorage
 *
 * @author 7Str1kes
 * @date 30/07/2025
 *
 * Copyright (c) 2025 7Str1kes. All rights reserved.
 */
public interface PlayerDataStorage {

    void saveData(PlayerData playerData);
    void loadData(PlayerData playerData);

    float getSize(PlayerData playerData);

    void setSize(PlayerData playerData, float size);
}