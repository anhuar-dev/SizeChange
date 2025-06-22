package dev.anhuar.sizeChange.manager;

/*
 * ========================================================
 * SizeChange - SizeManager.java
 *
 * @author Anhuar Ruiz | Anhuar Dev | myclass
 * @web https://anhuar.dev
 * @date 22/06/2025
 *
 * License: MIT License - See LICENSE file for details.
 * Copyright (c) 2025 Anhuar Dev. All rights reserved.
 * ========================================================
 */

import dev.anhuar.sizeChange.SizeChange;
import dev.anhuar.sizeChange.data.DPlayer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class SizeManager {

    private final SizeChange plugin;
    private final float DEFAULT_SIZE = 1.0f;

    public SizeManager(SizeChange plugin) {
        this.plugin = plugin;
    }

    public boolean setSize(UUID uuid, float size) {
        DPlayer playerData = plugin.getManagerHandler().getPlayerDataManager().getPlayerDataMap().get(uuid);

        if (playerData == null) return false;

        playerData.setSize(size);
        plugin.getManagerHandler().getPlayerDataManager().save(uuid);

        applySize(uuid, size);

        return true;
    }

    public boolean resetSize(UUID uuid) {
        return setSize(uuid, DEFAULT_SIZE);
    }

    public float getSize(UUID uuid) {
        DPlayer playerData = plugin.getManagerHandler().getPlayerDataManager().getPlayerDataMap().get(uuid);

        if (playerData == null) return DEFAULT_SIZE;

        return playerData.getSize();
    }

    public void applySize(UUID uuid, float size) {
        Player player = Bukkit.getPlayer(uuid);

        if (player != null && player.isOnline()) {
            AttributeInstance attributeInstance = player.getAttribute(Attribute.SCALE);

            if (attributeInstance != null) {
                attributeInstance.setBaseValue(size);
            }
        }
    }

    public void handleWorldChange(Player player, World toWorld, World fromWorld) {

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        List<String> denyWorlds = plugin.getSetting().getConfig().getStringList("DENY-WORLD");
        float size = plugin.getManagerHandler().getSizeManager().getSize(player.getUniqueId());

        if (denyWorlds.contains(toWorld.getName())) {
            plugin.getManagerHandler().getSizeManager().applySize(player.getUniqueId(), DEFAULT_SIZE);
        } else if (denyWorlds.contains(fromWorld.getName())) {
            plugin.getManagerHandler().getSizeManager().applySize(player.getUniqueId(), size);
        }
    }
}