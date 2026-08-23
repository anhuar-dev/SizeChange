package dev.anhuar.manager;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import dev.anhuar.SizeChange;
import dev.anhuar.handler.DenyRegionHandler;
import dev.anhuar.model.TemporarySizeData;
import dev.anhuar.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TemporarySizeManager {

    private final SizeChange plugin;
    private final Map<UUID, TemporarySizeData> temporarySizes;
    private final Map<UUID, BukkitTask> activeTasks;

    public TemporarySizeManager(SizeChange plugin) {
        this.plugin = plugin;
        this.temporarySizes = new ConcurrentHashMap<>();
        this.activeTasks = new ConcurrentHashMap<>();
        startExpirationCheck();
    }

    public void setTemporarySize(Player player, double tempSize, long durationMillis, boolean animate) {
        double originalSize = plugin.getManagerHandler().getSizeManager().getSize(player);
        applyTemporarySize(player, tempSize, originalSize, durationMillis, animate);
    }

    // IMPORTANT: use this overload whenever the player's current visual size does
    // NOT reflect their real original size — e.g. right after joining (visual size
    // is still the default, the real value is only in the DB record being
    // restored) or right after leaving a WorldGuard region (visual size is the
    // region override, not what they had before entering). The other overload
    // above reads the live visual size instead, which is only correct when the
    // player's current size already IS their real size.
    public void setTemporarySize(Player player, double tempSize, double originalSize, long durationMillis, boolean animate) {
        applyTemporarySize(player, tempSize, originalSize, durationMillis, animate);
    }

    // IMPORTANT: animate is true only for the direct /sizechange temporary set
    // command — never for the region re-application in DenyRegionHandler, which
    // can fire far more often than a player types a command.
    private void applyTemporarySize(Player player, double tempSize, double originalSize, long durationMillis, boolean animate) {
        UUID uuid = player.getUniqueId();
        SizeManager sizeManager = plugin.getManagerHandler().getSizeManager();

        cancelTemporarySize(uuid);

        long expirationTime = System.currentTimeMillis() + durationMillis;
        temporarySizes.put(uuid, new TemporarySizeData(uuid, tempSize, originalSize, expirationTime));

        // IMPORTANT: skip the visual change while the player is under a region's
        // visual override. The region must keep controlling what's shown — the temp
        // is still recorded and counting down, it only becomes visible once the
        // player leaves the region (onCrossBoundary's exit branch applies it then).
        if (!DenyRegionHandler.hasVisualOverride(uuid)) {
            if (animate) {
                plugin.getManagerHandler().getSizeAnimator().animateTo(player, tempSize);
            } else {
                sizeManager.setSize(player, tempSize);
            }
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> restoreSize(uuid, false), durationMillis / 50);
        activeTasks.put(uuid, task);
    }

    public void cancelTemporarySize(UUID uuid) {
        BukkitTask task = activeTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
        temporarySizes.remove(uuid);
    }

    // IMPORTANT: animate is true only for the direct /sizechange temporary clear
    // command — never for automatic expiration (the scheduled task or the
    // periodic backup check below), which isn't a player-triggered action.
    public void clearTemporarySize(UUID uuid) {
        restoreSize(uuid, true);
    }

    public void restoreSize(UUID uuid, boolean animate) {
        TemporarySizeData tempData = temporarySizes.remove(uuid);
        activeTasks.remove(uuid);

        if (tempData != null) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                SizeManager sizeManager = plugin.getManagerHandler().getSizeManager();

                // IMPORTANT: check the player's current region BEFORE restoring
                // tempData.getOriginalSize(). If they're standing in a deny/size
                // region when the temp size expires, restoring the original size
                // first would apply it for a single tick before the region logic
                // (which reacts to onCrossBoundary, not to expiration) overwrites
                // it — the player would flicker between sizes. Applying the
                // region's size directly here avoids that.
                ProtectedRegion currentRegion = findApplicableRegion(player);

                if (currentRegion != null) {
                    String regionId = currentRegion.getId();

                    if (plugin.getManagerHandler().getRegionManager().isDenyRegion(regionId)) {
                        applyRestoredSize(player, 1.0, animate);
                        return;
                    }

                    Double regionSize = DenyRegionHandler.getRegionSize(regionId);
                    if (regionSize != null) {
                        applyRestoredSize(player, regionSize, animate);
                        return;
                    }
                }

                applyRestoredSize(player, tempData.getOriginalSize(), animate);

                MessageUtil.sendMessage(player, "temp-size-expired",
                        new MessageUtil.PlaceholderBuilder()
                                .add("size", tempData.getOriginalSize())
                                .build());
            }
        }
    }

    private void applyRestoredSize(Player player, double size, boolean animate) {
        if (animate) {
            plugin.getManagerHandler().getSizeAnimator().animateTo(player, size);
        } else {
            plugin.getManagerHandler().getSizeManager().setSize(player, size);
        }
    }

    // IMPORTANT: returns the highest-priority region this plugin actually cares
    // about (a deny region or a SIZE-REGION), not just whichever region WorldGuard
    // happens to return first. A player can stand inside several overlapping
    // regions at once — filtering to relevant ones and then picking by priority
    // avoids two failures: missing an applicable size region because an unrelated
    // region (e.g. a world-wide spawn area) came first in the set, and applying a
    // different region's size between calls because Sets have no stable order.
    private ProtectedRegion findApplicableRegion(Player player) {
        try {
            Location loc = BukkitAdapter.adapt(player.getLocation());
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionQuery query = container.createQuery();
            ApplicableRegionSet regions = query.getApplicableRegions(loc);

            RegionManager regionManager = plugin.getManagerHandler().getRegionManager();

            return regions.getRegions().stream()
                    .filter(region -> regionManager.isDenyRegion(region.getId())
                            || DenyRegionHandler.getRegionSize(region.getId()) != null)
                    .max(Comparator.comparingInt(ProtectedRegion::getPriority))
                    .orElse(null);
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting current region: " + e.getMessage());
        }
        return null;
    }

    public boolean hasTemporarySize(UUID uuid) {
        return temporarySizes.containsKey(uuid);
    }

    public int activeCount() {
        return temporarySizes.size();
    }

    public TemporarySizeData getTemporarySize(UUID uuid) {
        return temporarySizes.get(uuid);
    }

    // IMPORTANT: this runs off the main thread and only detects expired entries —
    // it never removes them from temporarySizes itself. Removal happens inside
    // restoreSize(), which this schedules back onto the main thread (Bukkit API
    // calls aren't thread-safe). If this method removed entries directly, a
    // concurrent restoreSize() call (e.g. from a manual /sizechange cleartemp)
    // could read a null tempData for a uuid it expected to still be present.
    private void startExpirationCheck() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            temporarySizes.forEach((uuid, data) -> {
                if (data.isExpired()) {
                    Bukkit.getScheduler().runTask(plugin, () -> restoreSize(uuid, false));
                }
            });
        }, 600L, 600L);
    }

    public void shutdown() {
        activeTasks.values().forEach(BukkitTask::cancel);
        activeTasks.clear();
        temporarySizes.clear();
    }
}
