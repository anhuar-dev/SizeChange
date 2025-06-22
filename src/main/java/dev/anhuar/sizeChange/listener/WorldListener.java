package dev.anhuar.sizeChange.listener;

import dev.anhuar.sizeChange.SizeChange;
import jdk.jfr.Enabled;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.scheduler.BukkitRunnable;

/*
 * ========================================================
 * SizeChange - WorldListener.java
 *
 * @author Anhuar Ruiz | Anhuar Dev | myclass
 * @web https://anhuar.dev
 * @date 22/06/2025
 *
 * License: MIT License - See LICENSE file for details.
 * Copyright (c) 2025 Anhuar Dev. All rights reserved.
 * ========================================================
 */
public class WorldListener implements Listener {

    private final SizeChange plugin;

    public WorldListener(SizeChange plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        World fromWorld = event.getFrom();
        World toWorld = player.getWorld();

        new BukkitRunnable() {
            @Override
            public void run() {
                plugin.getManagerHandler().getSizeManager().handleWorldChange(player, toWorld, fromWorld);
            }
        }.runTaskLater(plugin, 20);

    }

}