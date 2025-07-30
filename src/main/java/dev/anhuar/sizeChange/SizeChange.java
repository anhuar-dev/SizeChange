package dev.anhuar.sizeChange;

import dev.anhuar.sizeChange.database.PlayerDataManager;
import dev.anhuar.sizeChange.database.PlayerDataStorage;
import dev.anhuar.sizeChange.handler.CommandHandler;
import dev.anhuar.sizeChange.handler.ListenerHandler;
import dev.anhuar.sizeChange.handler.ManagerHandler;
import dev.anhuar.sizeChange.util.ConfigUtil;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public final class SizeChange extends JavaPlugin {

    @Getter
    @Setter
    private static SizeChange instance;

    ConfigUtil setting, message;

    private CommandHandler commandHandler;
    private ListenerHandler listenerHandler;
    private ManagerHandler managerHandler;

    // Database
    private PlayerDataManager playerDataManager;
    private PlayerDataStorage playerDataStorage;

    @Override
    public void onEnable() {
        instance = this;

        setting = new ConfigUtil(this, "setting.yml");
        message = new ConfigUtil(this, "message.yml");

        this.playerDataManager = new PlayerDataManager(this);
        playerDataManager.enable();
        this.playerDataStorage = playerDataManager.getPlayerDataStorage();

        commandHandler = new CommandHandler(this);
        listenerHandler = new ListenerHandler(this);
        managerHandler = new ManagerHandler(this);
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) {
            playerDataManager.disable();
        }
    }
}
