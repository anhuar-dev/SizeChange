package dev.anhuar.sizeChange.listener;

import dev.anhuar.sizeChange.SizeChange;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/*
 * ========================================================
 * SizeChange - PlayerListener.java
 *
 * @author Anhuar Ruiz | Anhuar Dev | myclass
 * @web https://anhuar.dev
 * @date 22/06/2025
 *
 * License: MIT License - See LICENSE file for details.
 * Copyright (c) 2025 Anhuar Dev. All rights reserved.
 * ========================================================
 */

public class PlayerListener implements Listener {

    private final SizeChange plugin;

    public PlayerListener(SizeChange plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getManagerHandler().getPlayerDataManager().load(player.getUniqueId());
        });

        float size = plugin.getManagerHandler().getSizeManager().getSize(player.getUniqueId());
        plugin.getManagerHandler().getSizeManager().applySize(player.getUniqueId(), size);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getManagerHandler().getPlayerDataManager().save(player.getUniqueId());
    }

}