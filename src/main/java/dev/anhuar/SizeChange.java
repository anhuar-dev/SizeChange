package dev.anhuar;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.session.SessionManager;
import dev.anhuar.handler.CommandHandler;
import dev.anhuar.handler.DenyRegionHandler;
import dev.anhuar.handler.ListenerHandler;
import dev.anhuar.handler.ManagerHandler;
import dev.anhuar.hook.PlaceholderHook;
import dev.anhuar.util.ConfigUtil;
import lombok.Getter;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
public class SizeChange extends JavaPlugin {

    // IMPORTANT: getter only, no setter. The instance is assigned once in
    // onEnable(); exposing a public setter would let any caller swap or null out
    // the singleton that every manager and the WorldGuard handler resolve through.
    @Getter
    private static SizeChange instance;

    private ConfigUtil setting, message, menu;

    private CommandHandler commandHandler;
    private ListenerHandler listenerHandler;
    private ManagerHandler managerHandler;

    private PlaceholderHook placeholderHook;

    // bStats plugin id. Developer-only: register the plugin once at
    // https://bstats.org and paste the id here. 0 keeps metrics off entirely.
    // Server owners opt out through the global plugins/bStats/config.yml.
    private static final int BSTATS_PLUGIN_ID = 0;

    @Override
    public void onEnable() {
        instance = this;

        if (!checkWorldGuard()) {
            getLogger().severe("WorldGuard is not installed. The plugin will be disabled");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        setting = new ConfigUtil(this, "setting.yml", "DENY-REGION", "SIZE-REGION", "DENY-WORLD", "SIZE-WORLD");
        message = new ConfigUtil(this, "message.yml");
        menu = new ConfigUtil(this, "menu.yml");

        // IMPORTANT: not a ConfigUtil — SizeChange never reads this file itself.
        // It's a reference DeluxeMenus menu (a separate, optional plugin) that the
        // admin can copy into plugins/DeluxeMenus/menus/ if they want a self-service
        // size picker. saveResource(..., false) is a no-op if it already exists, so
        // this is safe to call on every startup without touching admin edits.
        saveResource("deluxemenu_template.yml", false);

        managerHandler = new ManagerHandler(this);
        commandHandler = new CommandHandler(this);
        listenerHandler = new ListenerHandler(this);

        managerHandler.getRegionManager().reloadDenyRegions();
        managerHandler.getWorldManager().reload();

        DenyRegionHandler.initialize(this);

        if (!verifyDatabaseConnection()) {
            getLogger().severe("Could not connect to the database. The plugin will be disabled");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        SessionManager sessionManager = WorldGuard.getInstance().getPlatform().getSessionManager();
        sessionManager.registerHandler(DenyRegionHandler.FACTORY, null);

        setupMetrics();
        setupPlaceholders();

        // Re-apply attribute modifiers to anyone already online (matters only when
        // the plugin is hot-loaded with players connected; a no-op on a normal
        // startup). Regular joins go through PlayerListener.
        getServer().getOnlinePlayers().forEach(player ->
                managerHandler.getAttributeModifierManager().handleJoin(player));

        getLogger().info("Size plugin enabled successfully");
    }

    // bStats is shaded + relocated (see build.gradle.kts). Fully off unless a
    // real id is compiled into BSTATS_PLUGIN_ID; see BSTATS.md.
    private void setupMetrics() {
        if (BSTATS_PLUGIN_ID <= 0) {
            return;
        }

        Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);
        metrics.addCustomChart(new SimplePie("database_type",
                () -> setting.getString("DATABASE.TYPE")));
        metrics.addCustomChart(new SimplePie("animation_enabled",
                () -> String.valueOf(setting.getConfig().getBoolean("ANIMATION.ENABLED", true))));
        metrics.addCustomChart(new SingleLineChart("active_temporary_sizes",
                () -> managerHandler.getTemporarySizeManager().activeCount()));
        metrics.addCustomChart(new SimplePie("attributes_enabled",
                () -> String.valueOf(setting.getConfig().getBoolean("ATTRIBUTES.ENABLED", true))));
        metrics.addCustomChart(new SingleLineChart("active_attribute_modifiers",
                () -> managerHandler.getAttributeModifierManager().activeCount()));
    }

    private void setupPlaceholders() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }
        placeholderHook = new PlaceholderHook(this);
        if (placeholderHook.register()) {
            getLogger().info("Registered the PlaceholderAPI expansion (%sizechange_...%)");
        }
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
            if (managerHandler.getCombatTagManager() != null) {
                managerHandler.getCombatTagManager().shutdown();
            }
            if (managerHandler.getAttributeModifierManager() != null) {
                managerHandler.getAttributeModifierManager().shutdown();
            }
            if (managerHandler.getSizeAnimator() != null) {
                managerHandler.getSizeAnimator().shutdown();
            }
        }

        if (placeholderHook != null) {
            placeholderHook.unregister();
        }

        DenyRegionHandler.clearAllCache();

        getLogger().info("Size plugin disabled successfully");
    }
}
