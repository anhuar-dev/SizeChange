package dev.anhuar.handler;

import dev.anhuar.SizeChange;
import dev.anhuar.manager.PlayerDataManager;
import dev.anhuar.manager.RegionManager;
import dev.anhuar.manager.SizeManager;
import dev.anhuar.manager.TemporarySizeManager;
import lombok.Getter;

@Getter
public class ManagerHandler {

    private final SizeChange plugin;

    private RegionManager regionManager;
    private SizeManager sizeManager;
    private PlayerDataManager playerDataManager;
    private TemporarySizeManager temporarySizeManager;

    public ManagerHandler(SizeChange plugin) {
        this.plugin = plugin;
        registerManager();
    }

    public void registerManager() {
        this.regionManager = new RegionManager();
        this.sizeManager = new SizeManager();
        this.playerDataManager = new PlayerDataManager(plugin);
        this.temporarySizeManager = new TemporarySizeManager(plugin);
    }
}

