package dev.anhuar.manager;

import dev.anhuar.SizeChange;
import dev.anhuar.db.Database;
import dev.anhuar.model.AttributeModifierData;
import dev.anhuar.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// Optional, self-contained feature: extra vanilla attribute modifiers (speed,
// damage, max health, ...) applied to players, permanently or for a limited time.
// Structurally a sibling of TemporarySizeManager — it owns the in-memory state for
// online players, all Bukkit application, and the expiry scheduling.
//
// It is deliberately orthogonal to the size system: it never touches
// Attribute.SCALE (always blocked) and the size system never touches anything
// else, so there is no interaction with regions, worlds, or the combat tag.
//
// Persistence is a separate per-player row/document (Database.loadAttributes /
// saveAttributes); every mutation is write-through. Bukkit-added modifiers on a
// player persist in player.dat across relogs, so handleJoin() always strips our
// modifiers first and then re-applies from the database — the DB is the source
// of truth, exactly like the size system.
public class AttributeModifierManager {

    // Curated set used for tab-completion and for the "ALLOWED is empty = allow
    // everything supported" case. Every entry is an attribute a player entity has
    // on 1.21.4.
    private static final List<String> SUPPORTED = List.of(
            "minecraft:max_health",
            "minecraft:movement_speed",
            "minecraft:attack_damage",
            "minecraft:attack_speed",
            "minecraft:attack_knockback",
            "minecraft:armor",
            "minecraft:armor_toughness",
            "minecraft:knockback_resistance",
            "minecraft:luck",
            "minecraft:max_absorption",
            "minecraft:jump_strength",
            "minecraft:safe_fall_distance",
            "minecraft:fall_damage_multiplier",
            "minecraft:gravity",
            "minecraft:movement_efficiency",
            "minecraft:water_movement_efficiency",
            "minecraft:step_height",
            "minecraft:block_interaction_range",
            "minecraft:entity_interaction_range",
            "minecraft:block_break_speed",
            "minecraft:mining_efficiency",
            "minecraft:sneaking_speed",
            "minecraft:submerged_mining_speed",
            "minecraft:sweeping_damage_ratio",
            "minecraft:burning_time",
            "minecraft:explosion_knockback_resistance",
            "minecraft:oxygen_bonus"
    );

    private static final String BLOCKED = "minecraft:scale";

    private final SizeChange plugin;
    private final String namespace;

    // uuid -> live modifiers (CopyOnWriteArrayList: mutated only on the main
    // thread, but read from PlaceholderAPI which some plugins call async).
    private final Map<UUID, List<AttributeModifierData>> live = new ConcurrentHashMap<>();

    // "uuid|minecraft:attr" -> precise expiry task (temporary modifiers only).
    private final Map<String, BukkitTask> expiryTasks = new ConcurrentHashMap<>();

    private volatile boolean enabled;
    private volatile double minAmount;
    private volatile double maxAmount;
    private volatile Set<String> allowed = Collections.emptySet();

    private BukkitTask sweepTask;

    public AttributeModifierManager(SizeChange plugin) {
        this.plugin = plugin;
        this.namespace = new NamespacedKey(plugin, "probe").getNamespace();
        readSettings();
        startExpirySweep();
    }

    // ---- configuration ----------------------------------------------------

    private void readSettings() {
        YamlConfiguration c = plugin.getSetting().getConfig();
        enabled = c.getBoolean("ATTRIBUTES.ENABLED", true);
        minAmount = c.getDouble("ATTRIBUTES.MIN-AMOUNT", -1024.0);
        maxAmount = c.getDouble("ATTRIBUTES.MAX-AMOUNT", 1024.0);

        Set<String> newAllowed = ConcurrentHashMap.newKeySet();
        for (String raw : c.getStringList("ATTRIBUTES.ALLOWED")) {
            Attribute attr = resolveAttribute(raw);
            if (attr != null) {
                newAllowed.add(attr.getKey().toString());
            } else {
                plugin.getLogger().warning("Unknown attribute in ATTRIBUTES.ALLOWED: '" + raw + "'");
            }
        }
        allowed = newAllowed;
    }

    public void reload() {
        boolean wasEnabled = enabled;
        readSettings();

        if (wasEnabled && !enabled) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                stripAll(player);
            }
            expiryTasks.values().forEach(BukkitTask::cancel);
            expiryTasks.clear();
            live.clear();
        } else if (!wasEnabled && enabled) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                handleJoin(player);
            }
        }

        plugin.getLogger().info("Attributes " + (enabled ? "enabled" : "disabled")
                + " (" + allowed.size() + " allowed" + (allowed.isEmpty() ? ", empty = all supported)" : ")"));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public double clampAmount(double amount) {
        return Math.max(minAmount, Math.min(maxAmount, amount));
    }

    // ---- attribute resolution / whitelist -------------------------------

    // Accepts "movement_speed", "minecraft:movement_speed" and the legacy dotted
    // form "generic.movement_speed". Returns null for anything unresolvable.
    @SuppressWarnings("deprecation")
    public Attribute resolveAttribute(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String s = input.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("minecraft:")) {
            s = s.substring("minecraft:".length());
        }
        int dot = s.indexOf('.');
        if (dot >= 0) {
            s = s.substring(dot + 1);
        }
        try {
            return Registry.ATTRIBUTE.get(NamespacedKey.minecraft(s));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isAllowed(Attribute attr) {
        if (attr == null) {
            return false;
        }
        String key = attr.getKey().toString();
        if (key.equals(BLOCKED)) {
            return false;
        }
        if (allowed.isEmpty()) {
            return SUPPORTED.contains(key);
        }
        return allowed.contains(key);
    }

    public List<String> allowedShortNames() {
        List<String> source = allowed.isEmpty() ? SUPPORTED : new ArrayList<>(allowed);
        List<String> out = new ArrayList<>(source.size());
        for (String key : source) {
            out.add(shortName(key));
        }
        Collections.sort(out);
        return out;
    }

    // ---- mutations (main thread) ---------------------------------------

    // durationMillis == null -> permanent modifier; otherwise temporary.
    public void setModifier(Player player, Attribute attr, double amount, String operation, Long durationMillis) {
        UUID uuid = player.getUniqueId();
        boolean temporary = durationMillis != null;
        String attrKey = attr.getKey().toString();
        Long expiration = temporary ? System.currentTimeMillis() + durationMillis : null;

        List<AttributeModifierData> list = live.computeIfAbsent(uuid, k -> new CopyOnWriteArrayList<>());
        list.removeIf(d -> d.getAttribute().equals(attrKey) && d.isTemporary() == temporary);

        AttributeModifierData data = new AttributeModifierData(attrKey, amount, operation, expiration);
        list.add(data);

        applyModifier(player, data);
        scheduleExpiry(uuid, data);
        persist(uuid);
    }

    public boolean clearModifier(Player player, Attribute attr, boolean temporary) {
        UUID uuid = player.getUniqueId();
        List<AttributeModifierData> list = live.get(uuid);
        if (list == null) {
            return false;
        }
        String attrKey = attr.getKey().toString();
        if (!list.removeIf(d -> d.getAttribute().equals(attrKey) && d.isTemporary() == temporary)) {
            return false;
        }

        AttributeInstance inst = player.getAttribute(attr);
        if (inst != null) {
            removeBukkitModifier(inst, modifierKey(attr, temporary));
        }
        cancelTask(taskKey(uuid, attrKey));
        persist(uuid);
        return true;
    }

    public int clearAll(Player player) {
        UUID uuid = player.getUniqueId();
        List<AttributeModifierData> removed = live.remove(uuid);
        int count = removed == null ? 0 : removed.size();

        stripAll(player);
        for (String key : new ArrayList<>(expiryTasks.keySet())) {
            if (key.startsWith(uuid + "|")) {
                cancelTask(key);
            }
        }
        database().saveAttributes(uuid, player.getName(), List.of());
        return count;
    }

    public boolean extendTemporary(Player player, Attribute attr, long extraMillis) {
        List<AttributeModifierData> list = live.get(player.getUniqueId());
        if (list == null) {
            return false;
        }
        String attrKey = attr.getKey().toString();

        AttributeModifierData target = null;
        for (AttributeModifierData d : list) {
            if (d.getAttribute().equals(attrKey) && d.isTemporary()) {
                target = d;
                break;
            }
        }
        if (target == null) {
            return false;
        }

        long base = Math.max(target.getExpiration(), System.currentTimeMillis());
        target.setExpiration(base + extraMillis);
        scheduleExpiry(player.getUniqueId(), target);
        persist(player.getUniqueId());
        return true;
    }

    // ---- queries ------------------------------------------------------

    public List<AttributeModifierData> modifiersOf(UUID uuid) {
        List<AttributeModifierData> list = live.get(uuid);
        return list == null ? List.of() : new ArrayList<>(list);
    }

    public int count(UUID uuid) {
        List<AttributeModifierData> list = live.get(uuid);
        return list == null ? 0 : list.size();
    }

    public int activeCount() {
        int total = 0;
        for (List<AttributeModifierData> list : live.values()) {
            for (AttributeModifierData d : list) {
                if (d.isTemporary()) {
                    total++;
                }
            }
        }
        return total;
    }

    // Online players are served from memory; offline players hit the database.
    public CompletableFuture<List<AttributeModifierData>> loadForView(UUID uuid) {
        List<AttributeModifierData> list = live.get(uuid);
        if (list != null) {
            return CompletableFuture.completedFuture(new ArrayList<>(list));
        }
        return database().loadAttributes(uuid);
    }

    public String shortName(String attributeKey) {
        int colon = attributeKey.indexOf(':');
        return colon >= 0 ? attributeKey.substring(colon + 1) : attributeKey;
    }

    // ---- lifecycle --------------------------------------------------

    public void handleJoin(Player player) {
        if (!enabled) {
            return;
        }
        UUID uuid = player.getUniqueId();

        database().loadAttributes(uuid).thenAccept(stored -> {
            if (!player.isOnline()) {
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) {
                    return;
                }

                // Clear anything a previous session left in player.dat before
                // re-applying from the database.
                stripAll(player);

                List<AttributeModifierData> keep = new CopyOnWriteArrayList<>();
                boolean changed = false;
                for (AttributeModifierData d : stored) {
                    if (d.isExpired() || resolveAttribute(d.getAttribute()) == null) {
                        changed = true;
                        continue;
                    }
                    keep.add(d);
                    applyModifier(player, d);
                    scheduleExpiry(uuid, d);
                }

                if (keep.isEmpty()) {
                    live.remove(uuid);
                } else {
                    live.put(uuid, keep);
                }
                if (changed) {
                    persist(uuid);
                }

                long temps = keep.stream().filter(AttributeModifierData::isTemporary).count();
                if (temps > 0) {
                    MessageUtil.sendMessage(player, "attribute-restored",
                            MessageUtil.placeholder("count", String.valueOf(temps)));
                }
            });
        }).exceptionally(throwable -> {
            plugin.getLogger().warning("Error loading attributes for " + player.getName() + ": " + throwable.getMessage());
            return null;
        });
    }

    public void handleQuit(UUID uuid) {
        for (String key : new ArrayList<>(expiryTasks.keySet())) {
            if (key.startsWith(uuid + "|")) {
                cancelTask(key);
            }
        }
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            stripAll(player);
        }
        live.remove(uuid);
        database().evictAttributesFromCache(uuid);
    }

    public void shutdown() {
        if (sweepTask != null) {
            sweepTask.cancel();
        }
        expiryTasks.values().forEach(BukkitTask::cancel);
        expiryTasks.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            stripAll(player);
        }
        live.clear();
    }

    // ---- internals ------------------------------------------------

    private Database database() {
        return plugin.getManagerHandler().getPlayerDataManager().getDatabase();
    }

    private void persist(UUID uuid) {
        List<AttributeModifierData> list = live.get(uuid);
        String name = Bukkit.getPlayer(uuid) != null ? Bukkit.getPlayer(uuid).getName() : uuid.toString();
        database().saveAttributes(uuid, name, list == null ? List.of() : new ArrayList<>(list));
        if (list != null && list.isEmpty()) {
            live.remove(uuid);
        }
    }

    private void applyModifier(Player player, AttributeModifierData data) {
        Attribute attr = resolveAttribute(data.getAttribute());
        if (attr == null) {
            return;
        }
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) {
            return;
        }

        AttributeModifier.Operation op;
        try {
            op = AttributeModifier.Operation.valueOf(data.getOperation());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid attribute operation stored: '" + data.getOperation() + "'");
            return;
        }

        NamespacedKey key = modifierKey(attr, data.isTemporary());
        removeBukkitModifier(inst, key);
        inst.addModifier(new AttributeModifier(key, data.getAmount(), op, EquipmentSlotGroup.ANY));
    }

    private void removeBukkitModifier(AttributeInstance inst, NamespacedKey key) {
        for (AttributeModifier modifier : new ArrayList<>(inst.getModifiers())) {
            if (key.equals(modifier.getKey())) {
                inst.removeModifier(modifier);
            }
        }
    }

    // Remove every modifier this plugin ever added, across all attributes.
    private void stripAll(Player player) {
        for (Attribute attr : Registry.ATTRIBUTE) {
            AttributeInstance inst = player.getAttribute(attr);
            if (inst == null) {
                continue;
            }
            for (AttributeModifier modifier : new ArrayList<>(inst.getModifiers())) {
                NamespacedKey key = modifier.getKey();
                if (key != null && namespace.equals(key.getNamespace())) {
                    inst.removeModifier(modifier);
                }
            }
        }
    }

    private NamespacedKey modifierKey(Attribute attr, boolean temporary) {
        return new NamespacedKey(plugin, (temporary ? "temp_" : "perm_") + attr.getKey().getKey());
    }

    private void scheduleExpiry(UUID uuid, AttributeModifierData data) {
        if (!data.isTemporary()) {
            return;
        }
        String key = taskKey(uuid, data.getAttribute());
        cancelTask(key);

        long ticks = Math.max(1L, data.getRemainingTime() / 50);
        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin,
                () -> expire(uuid, data.getAttribute()), ticks);
        expiryTasks.put(key, task);
    }

    private void expire(UUID uuid, String attributeKey) {
        expiryTasks.remove(taskKey(uuid, attributeKey));

        List<AttributeModifierData> list = live.get(uuid);
        if (list == null) {
            return;
        }
        if (!list.removeIf(d -> d.getAttribute().equals(attributeKey) && d.isTemporary())) {
            return;
        }

        Player player = Bukkit.getPlayer(uuid);
        Attribute attr = resolveAttribute(attributeKey);
        if (player != null && player.isOnline() && attr != null) {
            AttributeInstance inst = player.getAttribute(attr);
            if (inst != null) {
                removeBukkitModifier(inst, modifierKey(attr, true));
            }
            MessageUtil.sendMessage(player, "attribute-temp-expired",
                    MessageUtil.placeholder("attribute", shortName(attributeKey)));
        }
        persist(uuid);
    }

    private void cancelTask(String key) {
        BukkitTask task = expiryTasks.remove(key);
        if (task != null) {
            task.cancel();
        }
    }

    private String taskKey(UUID uuid, String attributeKey) {
        return uuid + "|" + attributeKey;
    }

    // Backup sweep — detects a temporary modifier whose precise task somehow did
    // not fire and hops back to the main thread to expire it. Mirrors
    // TemporarySizeManager.startExpirationCheck (detect async, mutate on main).
    private void startExpirySweep() {
        sweepTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (Map.Entry<UUID, List<AttributeModifierData>> entry : live.entrySet()) {
                for (AttributeModifierData d : entry.getValue()) {
                    if (d.isExpired()) {
                        Bukkit.getScheduler().runTask(plugin, () -> expire(entry.getKey(), d.getAttribute()));
                    }
                }
            }
        }, 600L, 600L);
    }
}
