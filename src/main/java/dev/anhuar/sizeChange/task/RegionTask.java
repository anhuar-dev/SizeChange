package dev.anhuar.sizeChange.task;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import dev.anhuar.sizeChange.SizeChange;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RegionTask extends BukkitRunnable {

    private final SizeChange plugin;
    private final Map<UUID, Boolean> playerInDenyRegion = new ConcurrentHashMap<>();

    private final Set<String> relevantWorlds = new HashSet<>();
    private long lastCacheUpdate = 0;
    private static final long CACHE_UPDATE_INTERVAL = 30000;

    private List<String> denyRegions;

    public RegionTask(SizeChange plugin) {
        this.plugin = plugin;
        updateRegionCache();
    }

    private void updateRegionCache() {
        denyRegions = plugin.getSetting().getConfig().getStringList("DENY-REGION");

        if (denyRegions.isEmpty()) {
            relevantWorlds.clear();
            return;
        }

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        relevantWorlds.clear();

        for (World world : Bukkit.getWorlds()) {
            RegionManager regionManager = container.get(BukkitAdapter.adapt(world));
            if (regionManager == null) continue;

            for (String regionId : denyRegions) {
                if (regionManager.hasRegion(regionId)) {
                    relevantWorlds.add(world.getName());
                    break;
                }
            }
        }

        lastCacheUpdate = System.currentTimeMillis();
    }

    @Override
    public void run() {
        if (System.currentTimeMillis() - lastCacheUpdate > CACHE_UPDATE_INTERVAL) {
            updateRegionCache();
        }

        if (denyRegions.isEmpty()) return;

        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionQuery query = container.createQuery();

        for (Player player : Bukkit.getOnlinePlayers()) {

            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }

            if (!relevantWorlds.contains(player.getWorld().getName())) continue;

            UUID uuid = player.getUniqueId();

            ApplicableRegionSet regions = query.getApplicableRegions(BukkitAdapter.adapt(player.getLocation()));

            boolean inDenyRegion = false;
            for (ProtectedRegion region : regions) {
                if (denyRegions.contains(region.getId())) {
                    inDenyRegion = true;
                    break;
                }
            }

            Boolean wasInDenyRegion = playerInDenyRegion.get(uuid);

            if (inDenyRegion && (wasInDenyRegion == null || !wasInDenyRegion)) {
                playerInDenyRegion.put(uuid, true);
                float DEFAULT_SIZE = 1.0f;
                plugin.getManagerHandler().getSizeManager().applySize(uuid, DEFAULT_SIZE);
            } else if (!inDenyRegion && Boolean.TRUE.equals(wasInDenyRegion)) {
                playerInDenyRegion.put(uuid, false);
                float originalSize = plugin.getManagerHandler().getSizeManager().getSize(uuid);
                plugin.getManagerHandler().getSizeManager().applySize(uuid, originalSize);
            }
        }
    }

    public void removePlayer(UUID uuid) {
        playerInDenyRegion.remove(uuid);
    }

    public void forceUpdateCache() {
        updateRegionCache();
    }
}