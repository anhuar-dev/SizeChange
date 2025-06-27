package dev.anhuar.sizeChange.listener;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import dev.anhuar.sizeChange.SizeChange;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;

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

            Bukkit.getScheduler().runTask(plugin, () -> {
                List<String> denyWorlds = plugin.getSetting().getConfig().getStringList("DENY-WORLD");
                float size;

                if (denyWorlds.contains(player.getWorld().getName())) {
                    size = 1.0f;
                } else {
                    size = plugin.getManagerHandler().getSizeManager().getSize(player.getUniqueId());
                }
                plugin.getManagerHandler().getSizeManager().applySize(player.getUniqueId(), size);
            });
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.getManagerHandler().getPlayerDataManager().save(player.getUniqueId());

        if (plugin.getListenerHandler().getRegionTask() != null) {
            plugin.getListenerHandler().getRegionTask().removePlayer(player.getUniqueId());
        }
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) return;

        Player player = event.getPlayer();

        if (player.hasPermission("sizechange.admin")) return;

        List<String> denyWorlds = plugin.getSetting().getConfig().getStringList("DENY-WORLD");
        if (denyWorlds.contains(player.getWorld().getName())) {
            checkCommand(event);
            return;
        }

        List<String> denyRegions = plugin.getSetting().getConfig().getStringList("DENY-REGION");
        if (denyRegions.isEmpty()) return;

        RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(player.getLocation()));

        for (ProtectedRegion region : regions) {
            if (denyRegions.contains(region.getId())) {
                checkCommand(event);
                return;
            }
        }
    }

    private void checkCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage().toLowerCase();
        String command = message.split(" ")[0].substring(1);

        List<String> denyCommands = plugin.getSetting().getConfig().getStringList("DENY-COMMAND");

        for (String denyCommand : denyCommands) {
            if (command.equals(denyCommand) || command.startsWith(denyCommand + " ")) {
                event.setCancelled(true);
                event.getPlayer().sendRichMessage(plugin.getMessage().getString("ERROR.DENY-COMMAND"));
                return;
            }
        }
    }
}