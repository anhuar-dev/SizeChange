package dev.anhuar;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.session.SessionManager;
import dev.anhuar.handler.CommandHandler;
import dev.anhuar.handler.DenyRegionHandler;
import dev.anhuar.handler.ListenerHandler;
import dev.anhuar.handler.ManagerHandler;
import dev.anhuar.util.ConfigUtil;
import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public class SizeChange extends JavaPlugin {

    // IMPORTANT: getter only, no setter. The instance is assigned once in
    // onEnable(); exposing a public setter would let any caller swap or null out
    // the singleton that every manager and the WorldGuard handler resolve through.
    @Getter
    private static SizeChange instance;

    private ConfigUtil setting, message;

    private CommandHandler commandHandler;
    private ListenerHandler listenerHandler;
    private ManagerHandler managerHandler;

    @Override
    public void onEnable() {
        instance = this;

        if (!checkWorldGuard()) {
            getLogger().severe("WorldGuard is not installed. The plugin will be disabled");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        setting = new ConfigUtil(this, "setting.yml");
        message = new ConfigUtil(this, "message.yml");

        managerHandler = new ManagerHandler(this);
        commandHandler = new CommandHandler(this);
        listenerHandler = new ListenerHandler(this);

        managerHandler.getRegionManager().reloadDenyRegions();

        DenyRegionHandler.initialize(this);

        if (!verifyDatabaseConnection()) {
            getLogger().severe("Could not connect to the database. The plugin will be disabled");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        SessionManager sessionManager = WorldGuard.getInstance().getPlatform().getSessionManager();
        sessionManager.registerHandler(DenyRegionHandler.FACTORY, null);

        getLogger().info("Size plugin enabled successfully");
    }

    private boolean checkWorldGuard() {
        try {
            Class.forName("com.sk89q.worldguard.WorldGuard");
            return getServer().getPluginManager().getPlugin("WorldGuard") != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private boolean verifyDatabaseConnection() {
        try {
            Boolean connected = managerHandler.getPlayerDataManager()
                    .getConnectFuture()
                    .get(10, java.util.concurrent.TimeUnit.SECONDS);

            return connected != null && connected;
        } catch (Exception e) {
            getLogger().severe("Error connecting to the database: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void onDisable() {
        if (managerHandler != null) {
            // IMPORTANT: order matters here — do not swap these two blocks.
            // flushOnDisable() reads TemporarySizeManager's data to resolve each
            // player's real permanent size. If temporarySizeManager.shutdown() runs
            // first, that data is already cleared and any player with an active
            // temporary size gets their real permanent size silently overwritten.
            if (managerHandler.getPlayerDataManager() != null) {
                managerHandler.getPlayerDataManager().flushOnDisable();
                managerHandler.getPlayerDataManager().shutdown();
            }
            if (managerHandler.getTemporarySizeManager() != null) {
                managerHandler.getTemporarySizeManager().shutdown();
            }
        }

        DenyRegionHandler.clearAllCache();

        getLogger().info("Size plugin disabled successfully");
    }
}
