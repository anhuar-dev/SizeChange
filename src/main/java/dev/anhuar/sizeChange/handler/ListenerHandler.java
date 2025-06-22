package dev.anhuar.sizeChange.handler;

/*
 * ========================================================
 * SizeChange - ListenerHandler.java
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
import dev.anhuar.sizeChange.listener.PlayerListener;
import dev.anhuar.sizeChange.listener.WorldListener;
import dev.anhuar.sizeChange.task.RegionTask;
import lombok.Getter;
import org.bukkit.event.Listener;

import java.util.Arrays;
import java.util.List;

public class ListenerHandler {

    private final SizeChange plugin;

    @Getter
    private RegionTask regionTask;

    public ListenerHandler(SizeChange plugin) {
        this.plugin = plugin;
        registerListeners();
        startTasks();
    }

    private void registerListeners() {
        List<Listener> listeners = Arrays.asList(
                new PlayerListener(plugin),
                new WorldListener(plugin)
        );

        for (Listener listener : listeners) {
            plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        }
    }

    private void startTasks() {
        this.regionTask = new RegionTask(plugin);
        regionTask.runTaskTimer(plugin, 20, 10);
    }
}