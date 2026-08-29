package dev.anhuar.manager;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import dev.anhuar.SizeChange;
import dev.anhuar.handler.DenyRegionHandler;
import dev.anhuar.util.ColorUtil;
import dev.anhuar.util.MessageUtil;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// PvP combat tag. When two players deal damage to each other (see CombatListener),
// BOTH are tagged for DURATION seconds; every fresh hit refreshes the timer.
// While tagged, the player's size is forced to COMBAT.SIZE — a visual-only
// override wired through DenyRegionHandler's overriddenPlayers set, exactly like
// a region, and given the HIGHEST precedence (combat beats region beats world).
// When the timer runs out, DenyRegionHandler.recomputeOverride restores whatever
// the player's size should be (a region/world they're still in, an active temp,
// or their permanent size).
//
// Exclusions are checked only at tag time: if either player is inside a
// COMBAT.EXCLUDED-REGIONS region when the hit lands, nobody is tagged. A player
// already tagged keeps the tag if they later walk into an excluded region.
public class CombatTagManager {

    private final SizeChange plugin;

    private final Map<UUID, Long> taggedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();

    private BukkitTask ticker;

    private volatile boolean enabled;
    private volatile long durationMillis;
    private volatile double combatSize;
    private volatile boolean showActionBar;
    private volatile boolean showBossBar;
    private volatile boolean showChat;
    private volatile BossBar.Color bossBarColor;
    private volatile BossBar.Overlay bossBarOverlay;
    private volatile Set<String> excludedRegions = Collections.emptySet();

    public CombatTagManager(SizeChange plugin) {
        this.plugin = plugin;
        reload();
        startTicker();
    }

    public void reload() {
        YamlConfiguration c = plugin.getSetting().getConfig();

        boolean wasEnabled = enabled;
        boolean wasBossBar = showBossBar;

        enabled = c.getBoolean("COMBAT.ENABLED", true);
        durationMillis = Math.max(1L, c.getLong("COMBAT.DURATION", 15L)) * 1000L;
        combatSize = c.getDouble("COMBAT.SIZE", 1.0);
        showActionBar = c.getBoolean("COMBAT.DISPLAY.ACTIONBAR", true);
        showBossBar = c.getBoolean("COMBAT.DISPLAY.BOSSBAR", true);
        showChat = c.getBoolean("COMBAT.DISPLAY.CHAT", true);
        bossBarColor = parseEnum(BossBar.Color.class, c.getString("COMBAT.BOSSBAR.COLOR"), BossBar.Color.RED);
        bossBarOverlay = parseEnum(BossBar.Overlay.class, c.getString("COMBAT.BOSSBAR.OVERLAY"), BossBar.Overlay.NOTCHED_10);

        List<String> excluded = c.getStringList("COMBAT.EXCLUDED-REGIONS");
        excludedRegions = excluded.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new HashSet<>(excluded));

        if (wasEnabled && !enabled) {
            clearAll();
        } else if (wasBossBar && !showBossBar) {
            hideAllBossBars();
        }

        plugin.getLogger().info("Combat tag " + (enabled ? "enabled" : "disabled")
                + " (" + (durationMillis / 1000) + "s, size " + combatSize + ", "
                + excludedRegions.size() + " excluded regions)");
    }

    private void startTicker() {
        // 1s cadence — the countdown is shown in whole seconds and a tag lingering
        // at most one extra second before it clears is imperceptible.
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    // ---- queries used by DenyRegionHandler / TemporarySizeManager -------------

    public boolean isTagged(UUID uuid) {
        Long until = taggedUntil.get(uuid);
        return until != null && until > System.currentTimeMillis();
    }

    public double getCombatSize() {
        return combatSize;
    }

    public int getRemainingSeconds(UUID uuid) {
        Long until = taggedUntil.get(uuid);
        if (until == null) {
            return 0;
        }
        return (int) Math.max(0, Math.ceil((until - System.currentTimeMillis()) / 1000.0));
    }

    public int activeCount() {
        return taggedUntil.size();
    }

    // ---- entry points -------------------------------------------------------

    public void onPvpHit(Player attacker, Player victim) {
        if (!enabled) {
            return;
        }
        if (isInExcludedRegion(attacker) || isInExcludedRegion(victim)) {
            return;
        }
        applyTag(attacker);
        applyTag(victim);
    }

    // Staff-triggered / cleanup removal that also restores the real size.
    public void clear(UUID uuid) {
        untag(uuid);
    }

    // Player left the server: drop the state without touching the (now offline)
    // player. DenyRegionHandler.cleanupPlayerCache already clears the override
    // flag, and PlayerListener saves the real permanent size, not the combat one.
    public void handleQuit(UUID uuid) {
        taggedUntil.remove(uuid);
        bossBars.remove(uuid);
    }

    public void shutdown() {
        if (ticker != null) {
            ticker.cancel();
        }
        hideAllBossBars();
        taggedUntil.clear();
    }

    // ---- internals ---------------------------------------------------------

    private void applyTag(Player player) {
        if (player.hasPermission(DenyRegionHandler.BYPASS_PERMISSION)) {
            return;
        }

        UUID uuid = player.getUniqueId();
        boolean alreadyActive = isTagged(uuid);
        taggedUntil.put(uuid, System.currentTimeMillis() + durationMillis);

        if (alreadyActive) {
            return;
        }

        DenyRegionHandler.addVisualOverride(uuid);
        plugin.getManagerHandler().getSizeManager().setSize(player, combatSize);

        if (showChat) {
            MessageUtil.sendMessage(player, "combat-tagged",
                    MessageUtil.placeholder("seconds", String.valueOf(getRemainingSeconds(uuid))));
        }
        if (showBossBar) {
            showBossBarFor(player);
        }
    }

    private void untag(UUID uuid) {
        if (taggedUntil.remove(uuid) == null && !bossBars.containsKey(uuid)) {
            return;
        }

        BossBar bar = bossBars.remove(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player == null || !player.isOnline()) {
            return;
        }

        if (bar != null) {
            player.hideBossBar(bar);
        }

        // Combat no longer tags this player -> resolveOverrideSize now falls
        // through to region / world / temp / permanent and applies the right one.
        DenyRegionHandler.recomputeOverride(player);

        if (showChat) {
            MessageUtil.sendMessage(player, "combat-untagged");
        }
    }

    private void tick() {
        if (taggedUntil.isEmpty()) {
            return;
        }

        long now = System.currentTimeMillis();
        List<UUID> expired = new ArrayList<>();

        taggedUntil.forEach((uuid, until) -> {
            long remaining = until - now;
            if (remaining <= 0) {
                expired.add(uuid);
                return;
            }

            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                return;
            }

            int seconds = (int) Math.ceil(remaining / 1000.0);

            if (showActionBar) {
                player.sendActionBar(render("combat-actionbar", seconds));
            }

            BossBar bar = bossBars.get(uuid);
            if (bar != null) {
                float progress = (float) Math.max(0.0, Math.min(1.0, remaining / (double) durationMillis));
                bar.progress(progress);
                bar.name(render("combat-bossbar", seconds));
            }
        });

        expired.forEach(this::untag);
    }

    private void showBossBarFor(Player player) {
        BossBar previous = bossBars.remove(player.getUniqueId());
        if (previous != null) {
            player.hideBossBar(previous);
        }

        BossBar bar = BossBar.bossBar(render("combat-bossbar", getRemainingSeconds(player.getUniqueId())),
                1.0f, bossBarColor, bossBarOverlay);
        bossBars.put(player.getUniqueId(), bar);
        player.showBossBar(bar);
    }

    private void hideAllBossBars() {
        for (Map.Entry<UUID, BossBar> entry : new ArrayList<>(bossBars.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                player.hideBossBar(entry.getValue());
            }
        }
        bossBars.clear();
    }

    private void clearAll() {
        for (UUID uuid : new ArrayList<>(taggedUntil.keySet())) {
            untag(uuid);
        }
        // Any bars whose tag entry was already gone.
        hideAllBossBars();
    }

    private boolean isInExcludedRegion(Player player) {
        if (excludedRegions.isEmpty()) {
            return false;
        }
        try {
            var container = WorldGuard.getInstance().getPlatform().getRegionContainer()
                    .get(BukkitAdapter.adapt(player.getWorld()));
            if (container == null) {
                return false;
            }
            var regions = container.getApplicableRegions(BlockVector3.at(
                    player.getLocation().getX(),
                    player.getLocation().getY(),
                    player.getLocation().getZ()));
            for (var region : regions.getRegions()) {
                if (excludedRegions.contains(region.getId())) {
                    return true;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Combat exclusion check failed for " + player.getName() + ": " + e.getMessage());
        }
        return false;
    }

    private Component render(String messageKey, int seconds) {
        return ColorUtil.translate(MessageUtil.applyPlaceholders(
                MessageUtil.getMessage(messageKey),
                MessageUtil.placeholder("seconds", String.valueOf(seconds))));
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, E fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            SizeChange.getInstance().getLogger().warning(
                    "Invalid " + type.getSimpleName() + " '" + raw + "' in setting.yml, using " + fallback.name());
            return fallback;
        }
    }
}
