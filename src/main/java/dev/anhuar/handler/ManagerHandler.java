package dev.anhuar.handler;

import dev.anhuar.SizeChange;
import dev.anhuar.manager.CombatTagManager;
import dev.anhuar.manager.PlayerDataManager;
import dev.anhuar.manager.RegionManager;
import dev.anhuar.manager.SizeAnimator;
import dev.anhuar.manager.SizeManager;
import dev.anhuar.manager.TemporarySizeManager;
import dev.anhuar.manager.WorldManager;
import dev.anhuar.menu.ChatInputManager;
import lombok.Getter;

@Getter
public class ManagerHandler {

    private final SizeChange plugin;

    private RegionManager regionManager;
    private WorldManager worldManager;
    private SizeManager sizeManager;
    private SizeAnimator sizeAnimator;
    private PlayerDataManager playerDataManager;
    private TemporarySizeManager temporarySizeManager;
    private CombatTagManager combatTagManager;
    private ChatInputManager chatInputManager;

    public ManagerHandler(SizeChange plugin) {
        this.plugin = plugin;
        registerManager();
    }

    public void registerManager() {
        this.regionManager = new RegionManager();
        this.worldManager = new WorldManager();
        this.sizeManager = new SizeManager();
        this.sizeAnimator = new SizeAnimator(plugin, sizeManager);
        this.playerDataManager = new PlayerDataManager(plugin);
        this.temporarySizeManager = new TemporarySizeManager(plugin);
        this.combatTagManager = new CombatTagManager(plugin);
        this.chatInputManager = new ChatInputManager(plugin);
    }
}

