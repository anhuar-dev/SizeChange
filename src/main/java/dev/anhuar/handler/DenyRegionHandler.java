package dev.anhuar.handler;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.session.MoveType;
import com.sk89q.worldguard.session.Session;
import com.sk89q.worldguard.session.handler.Handler;
import dev.anhuar.SizeChange;
import dev.anhuar.manager.PlayerDataManager;
import dev.anhuar.manager.RegionManager;
import dev.anhuar.manager.SizeManager;
import dev.anhuar.manager.TemporarySizeManager;
import dev.anhuar.manager.WorldManager;
import dev.anhuar.model.TemporarySizeData;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// IMPORTANT: this class deliberately mixes static state with an instance
// (cachedPlayer/playerUUID below). WorldGuard's session Handler.Factory
// constructs a new DenyRegionHandler per player session with no way to inject
// the plugin instance through the constructor, so plugin/managers must be
// stashed in static fields via initialize() before FACTORY is registered.
public class DenyRegionHandler extends Handler {

    public static final Factory FACTORY = new Factory();

    public static final String BYPASS_PERMISSION = "sizechange.bypass";

    private static SizeChange plugin;
    private static RegionManager regionManager;
    private static WorldManager worldManager;
    private static SizeManager sizeManager;
    private static PlayerDataManager dataManager;

    private static Map<String, Double> sizeRegionsCache = new ConcurrentHashMap<>();

    // IMPORTANT: tracks membership only — no value. The player's true permanent
    // size while under an override is read live from PlayerDataManager's own
    // dirty-tracking cache (already kept correct by every command that changes a
    // permanent size), instead of duplicating it here and having to remember to
    // keep two caches in sync.
    private static final Set<UUID> overriddenPlayers = ConcurrentHashMap.newKeySet();

    private Player cachedPlayer;
    private UUID playerUUID;

    public static class Factory extends Handler.Factory<DenyRegionHandler> {
        @Override
        public DenyRegionHandler create(Session session) {
            return new DenyRegionHandler(session);
        }
    }

    protected DenyRegionHandler(Session session) {
        super(session);
    }

    public static void initialize(SizeChange pluginInstance) {
        plugin = pluginInstance;
        regionManager = plugin.getManagerHandler().getRegionManager();
        worldManager = plugin.getManagerHandler().getWorldManager();
        sizeManager = plugin.getManagerHandler().getSizeManager();
        dataManager = plugin.getManagerHandler().getPlayerDataManager();
        reloadSizeRegions();
        plugin.getLogger().info("DenyRegionHandler initialized with manager cache");
    }

    public static void reloadSizeRegions() {
        ConfigurationSection section = plugin.getSetting().getConfig()
                .getConfigurationSection("SIZE-REGION");

        sizeRegionsCache.clear();

        if (section != null) {
            for (String key : section.getKeys(false)) {
                sizeRegionsCache.put(key, section.getDouble(key, 1.0));
            }
            plugin.getLogger().info("SIZE-REGION cache reloaded: " + sizeRegionsCache.size() + " regions");
        }
    }

    public static void cleanupPlayerCache(UUID uuid) {
        overriddenPlayers.remove(uuid);
    }

    public static void clearAllCache() {
        overriddenPlayers.clear();
        sizeRegionsCache.clear();
    }

    public static Double getRegionSize(String regionId) {
        return sizeRegionsCache.get(regionId);
    }

    // IMPORTANT: callers use this to decide whether a size change should be shown
    // immediately or applied silently until the player leaves the region — the
    // region always keeps controlling what's visible while this is true.
    public static boolean hasVisualOverride(UUID uuid) {
        return overriddenPlayers.contains(uuid);
    }

    // IMPORTANT: the size is applied inside runTask() because this callback may run
    // on a database pool thread (any cache miss). Bukkit's API is not thread-safe —
    // setting an entity attribute off the main thread is undefined behaviour and
    // Paper can throw for it. Always hop back to the main thread before touching
    // the player, even when the DB read looks instant.
    private static void restorePermanentSizeAsync(UUID uuid, Player player) {
        dataManager.getPlayerSize(uuid).thenAccept(size -> {
            if (player == null || !player.isOnline()) return;

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    sizeManager.setSize(player, size);
                }
            });
        });
    }

    // IMPORTANT: WorldGuard hands `entered`/`exited` over as unordered Sets, and
    // nested or overlapping regions are common (e.g. an arena inside a world-wide
    // region). Taking the first match in Set order would make the applied size
    // non-deterministic between crossings. Sorting by WorldGuard's own priority
    // (highest first) makes the most specific region win, matching how the rest of
    // WorldGuard resolves conflicts.
    private static List<ProtectedRegion> byPriorityDesc(Collection<ProtectedRegion> regions) {
        List<ProtectedRegion> sorted = new ArrayList<>(regions);
        sorted.sort(Comparator.comparingInt(ProtectedRegion::getPriority).reversed());
        return sorted;
    }

    // IMPORTANT: resolution order is region-first, then world. A DENY-REGION /
    // SIZE-REGION the player is physically standing in is always more specific
    // than the world they're in, so it wins — the world override only applies
    // when no relevant region does. Returns null when nothing overrides the
    // player's real size. The region lookup is a direct spatial query on the
    // container (not the player's WG session), so this is safe to call the
    // moment a player joins or changes world, before the session is rebuilt.
    private static Double resolveOverrideSize(Player player) {
        World adaptWorld = BukkitAdapter.adapt(player.getWorld());
        var container = WorldGuard.getInstance().getPlatform().getRegionContainer().get(adaptWorld);

        if (container != null) {
            ApplicableRegionSet regions = container.getApplicableRegions(BlockVector3.at(
                    player.getLocation().getX(),
                    player.getLocation().getY(),
                    player.getLocation().getZ()));

            for (ProtectedRegion region : byPriorityDesc(regions.getRegions())) {
                String regionId = region.getId();

                if (regionManager.isDenyRegion(regionId)) {
                    return 1.0;
                }

                Double regionSize = sizeRegionsCache.get(regionId);
                if (regionSize != null) {
                    return regionSize;
                }
            }
        }

        return worldManager.getOverrideSize(player.getWorld().getName());
    }

    // Called ~3s after join (see PlayerListener): if the player logged in already
    // inside a DENY/SIZE region or a DENY/SIZE world, apply that override now.
    // Only ever applies — never restores — because join has already set the
    // player's real size at this point.
    public static void applyOverrideIfApplicable(Player player) {
        if (plugin == null || regionManager == null || sizeManager == null) return;

        if (player.hasPermission(BYPASS_PERMISSION)) return;

        Double target = resolveOverrideSize(player);
        if (target != null) {
            overriddenPlayers.add(player.getUniqueId());
            sizeManager.setSize(player, target);
        }
    }

    // Recompute the player's override from scratch and either apply it or, if
    // nothing overrides them any more, restore their real (temporary/permanent)
    // size. Used on world change, where no WorldGuard boundary event fires but
    // the applicable DENY/SIZE world — and the set of regions — can both change.
    public static void recomputeOverride(Player player) {
        if (plugin == null || regionManager == null || sizeManager == null) return;

        UUID uuid = player.getUniqueId();

        if (player.hasPermission(BYPASS_PERMISSION)) {
            overriddenPlayers.remove(uuid);
            return;
        }

        Double target = resolveOverrideSize(player);
        if (target != null) {
            overriddenPlayers.add(uuid);
            sizeManager.setSize(player, target);
            return;
        }

        if (overriddenPlayers.remove(uuid)) {
            restoreNonOverrideSize(player);
        }
    }

    // Restore whatever the player's size should be once no override applies:
    // their active temporary size (re-armed with the correct time remaining) if
    // one is still running, otherwise their permanent size from the DB. Extracted
    // from onCrossBoundary's exit branch so world changes restore identically.
    private static void restoreNonOverrideSize(Player player) {
        UUID uuid = player.getUniqueId();
        TemporarySizeManager tempManager = plugin.getManagerHandler().getTemporarySizeManager();

        if (tempManager.hasTemporarySize(uuid)) {
            TemporarySizeData tempData = tempManager.getTemporarySize(uuid);
            long remainingTime = tempData.getRemainingTime();

            if (remainingTime > 0) {
                double activeTempSize = tempData.getTemporarySize();
                double trueOriginalSize = tempData.getOriginalSize();

                sizeManager.setSize(player, activeTempSize);

                tempManager.cancelTemporarySize(uuid);
                tempManager.setTemporarySize(player, activeTempSize, trueOriginalSize, remainingTime, false);
            } else {
                restorePermanentSizeAsync(uuid, player);
            }
        } else {
            restorePermanentSizeAsync(uuid, player);
        }
    }

    @Override
    public boolean onCrossBoundary(LocalPlayer player, Location from, Location to,
                                   ApplicableRegionSet toSet, Set<ProtectedRegion> entered,
                                   Set<ProtectedRegion> exited, MoveType moveType) {

        if ((entered == null || entered.isEmpty()) && (exited == null || exited.isEmpty())) {
            return true;
        }

        if (playerUUID == null) {
            playerUUID = player.getUniqueId();
        }

        cachedPlayer = Bukkit.getPlayer(playerUUID);
        if (cachedPlayer == null || !cachedPlayer.isOnline()) {
            return true;
        }

        if (cachedPlayer.hasPermission(BYPASS_PERMISSION)) {
            return true;
        }

        // IMPORTANT: WorldGuard reports a direct crossing between two adjacent
        // regions (e.g. walking straight from a SIZE-REGION into a DENY-REGION) as
        // both an "entered" and an "exited" region in the SAME call. If the entered
        // block below already applied a new override, the player is still under an
        // override — just a different one — so the exited block further down must
        // not run at all: it would otherwise restore the permanent size right after
        // the entered block just set the new region's size, undoing it.
        boolean enteredNewOverride = false;

        if (entered != null && !entered.isEmpty()) {
            for (ProtectedRegion region : byPriorityDesc(entered)) {
                String regionId = region.getId();

                if (regionManager.isDenyRegion(regionId)) {
                    overriddenPlayers.add(playerUUID);
                    sizeManager.setSize(cachedPlayer, 1.0);
                    enteredNewOverride = true;
                    break;
                }

                Double regionSize = sizeRegionsCache.get(regionId);
                if (regionSize != null) {
                    overriddenPlayers.add(playerUUID);
                    sizeManager.setSize(cachedPlayer, regionSize);
                    enteredNewOverride = true;
                    break;
                }
            }
        }

        if (!enteredNewOverride && exited != null && !exited.isEmpty()) {
            for (ProtectedRegion region : exited) {
                String regionId = region.getId();

                boolean isDenyRegion = regionManager.isDenyRegion(regionId);
                boolean isSizeRegion = sizeRegionsCache.containsKey(regionId);

                if (isDenyRegion || isSizeRegion) {
                    overriddenPlayers.remove(playerUUID);

                    // IMPORTANT: a DENY/SIZE world for the player's current world
                    // takes over the moment the region stops applying — same
                    // region-beats-world precedence used everywhere else. Only
                    // when no world override applies either do we restore the
                    // player's real temporary/permanent size.
                    Double worldSize = worldManager.getOverrideSize(cachedPlayer.getWorld().getName());
                    if (worldSize != null) {
                        overriddenPlayers.add(playerUUID);
                        sizeManager.setSize(cachedPlayer, worldSize);
                    } else {
                        restoreNonOverrideSize(cachedPlayer);
                    }
                    break;
                }
            }
        }

        return true;
    }
}
