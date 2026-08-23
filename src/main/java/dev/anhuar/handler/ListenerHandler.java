package dev.anhuar.handler;

import dev.anhuar.SizeChange;
import dev.anhuar.listener.PlayerListener;
import dev.anhuar.menu.MenuHolder;
import org.bukkit.Bukkit;

public class ListenerHandler {

    private final SizeChange plugin;

    public ListenerHandler(SizeChange plugin) {
        this.plugin = plugin;
        registerListeners();
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new PlayerListener(plugin), plugin);
        Bukkit.getPluginManager().registerEvents(new MenuHolder.MenuListener(), plugin);
        Bukkit.getPluginManager().registerEvents(plugin.getManagerHandler().getChatInputManager(), plugin);
    }
}
