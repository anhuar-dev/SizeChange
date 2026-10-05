package dev.anhuar.listener;

import dev.anhuar.SizeChange;
import dev.anhuar.handler.DenyRegionHandler;
import dev.anhuar.manager.PlayerDataManager;
import dev.anhuar.manager.SizeManager;
import dev.anhuar.manager.TemporarySizeManager;
import dev.anhuar.model.PlayerData;
import dev.anhuar.model.TemporarySizeData;
import dev.anhuar.util.MessageUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class PlayerListener implements Listener {

    private final SizeChange plugin;

    public PlayerListener(SizeChange plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerDataManager dataManager = plugin.getManagerHandler().getPlayerDataManager();
        SizeManager sizeManager = plugin.getManagerHandler().getSizeManager();
        TemporarySizeManager tempManager = plugin.getManagerHandler().getTemporarySizeManager();

        dataManager.getOrCreatePlayerData(player).thenAccept(playerData -> {
            if (!player.isOnline() || playerData == null) return;

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                if (playerData.hasTemporarySize()) {
                    long remainingTime = playerData.getTemporaryExpiration() - System.currentTimeMillis();

                    if (remainingTime > 0) {
                        tempManager.setTemporarySize(
                                player,
                                playerData.getTemporarySize(),
                                playerData.getSize(),
                                remainingTime,
                                false
                        );
                        MessageUtil.sendMessage(player, "temp-size-restored",
                                new MessageUtil.PlaceholderBuilder()
                                        .add("size", playerData.getTemporarySize())
                                        .build());
                    } else {
                        sizeManager.setSize(player, playerData.getSize());
                        playerData.clearTemporarySize();
                        dataManager.getDatabase().savePlayer(playerData)
                                .thenRun(() -> dataManager.markSaved(player.getUniqueId(), playerData.getSize(), null));
                    }
                } else {
                    sizeManager.setSize(player, playerData.getSize());
                }

                // Independent optional feature — loads and re-applies the player's
                // attribute modifiers from the database (does its own async read +
                // main-thread apply).
                plugin.getManagerHandler().getAttributeModifierManager().handleJoin(player);

                // IMPORTANT: 60L = 3 seconds. This delay is intentional, not
                // arbitrary padding — it gives WorldGuard time to finish setting up
                // the player's region session after join before this queries it.
                // Applying the region size immediately on join was found to
                // sometimes miss the region the player is actually standing in.
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline()) {
                        DenyRegionHandler.applyOverrideIfApplicable(player);
                    }
                }, 60L);
            });
        });
    }

    // A world change fires no WorldGuard boundary event, so DENY-WORLD /
    // SIZE-WORLD enforcement (and the set of applicable regions) has to be
    // recomputed here. One tick's grace lets the teleport's own position/region
    // bookkeeping — and any WorldGuard boundary event for the same move — settle
    // first, so recomputeOverride reads the final destination state.
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                DenyRegionHandler.recomputeOverride(player);
            }
        }, 2L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        PlayerDataManager dataManager = plugin.getManagerHandler().getPlayerDataManager();
        SizeManager sizeManager = plugin.getManagerHandler().getSizeManager();
        TemporarySizeManager tempManager = plugin.getManagerHandler().getTemporarySizeManager();

        UUID uuid = player.getUniqueId();

        plugin.getManagerHandler().getSizeAnimator().cancel(uuid);

        // IMPORTANT: cache eviction is chained onto the save rather than called
        // straight after it. The save is asynchronous and writes the player into the
        // DB cache as part of its work — evicting eagerly could run before that
        // write and leave the stale entry behind, which is exactly what this is
        // meant to prevent.
        if (tempManager.hasTemporarySize(uuid)) {
            // The active temp's own fields are authoritative here — no need to guess
            // at a "current visual" value, which would be the region's forced size
            // if the player is disconnecting while inside one.
            TemporarySizeData tempData = tempManager.getTemporarySize(uuid);

            PlayerData playerData = new PlayerData(uuid, player.getName(), tempData.getOriginalSize());
            playerData.setTemporarySize(tempData.getTemporarySize(), tempData.getExpirationTime());

            dataManager.getDatabase().savePlayer(playerData)
                    .thenRun(() -> dataManager.getDatabase().evictFromCache(uuid));
        } else {
            double sizeToSave = DenyRegionHandler.hasVisualOverride(uuid)
                    ? dataManager.getLastKnownPermanent(uuid, sizeManager.getSize(player))
                    : sizeManager.getSize(player);

            dataManager.savePlayerSize(uuid, player.getName(), sizeToSave)
                    .thenRun(() -> dataManager.getDatabase().evictFromCache(uuid));
        }

        plugin.getManagerHandler().getCombatTagManager().handleQuit(uuid);
        plugin.getManagerHandler().getAttributeModifierManager().handleQuit(uuid);
        DenyRegionHandler.cleanupPlayerCache(uuid);
        dataManager.clearPlayerCache(uuid);
    }
}
