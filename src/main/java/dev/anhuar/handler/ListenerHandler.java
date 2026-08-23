package dev.anhuar.handler;

import dev.anhuar.SizeChange;
import dev.anhuar.listener.PlayerListener;
import org.bukkit.Bukkit;

public class ListenerHandler {

    private final SizeChange plugin;

    public ListenerHandler(SizeChange plugin) {
        this.plugin = plugin;
        registerListeners();
    }

    private void registerListeners() {
        Bukkit.getPluginManager().registerEvents(new PlayerListener(plugin), plugin);
    }
}
