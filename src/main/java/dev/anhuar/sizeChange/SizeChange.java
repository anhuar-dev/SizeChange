package dev.anhuar.sizeChange;

import dev.anhuar.sizeChange.handler.CommandHandler;
import dev.anhuar.sizeChange.handler.ListenerHandler;
import dev.anhuar.sizeChange.handler.ManagerHandler;
import dev.anhuar.sizeChange.handler.MongoHandler;
import dev.anhuar.sizeChange.util.ConfigUtil;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.event.Listener;
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
    private MongoHandler mongoHandler;

    @Override
    public void onEnable() {

        instance = this;

        setting = new ConfigUtil(this, "setting.yml");
        message = new ConfigUtil(this, "message.yml");

        commandHandler = new CommandHandler(this);
        listenerHandler = new ListenerHandler(this);
        managerHandler = new ManagerHandler(this);
        mongoHandler = new MongoHandler(this);

    }


    @Override
    public void onDisable() {
        if (this.mongoHandler != null) {
            this.mongoHandler.close();
        }
    }
}
