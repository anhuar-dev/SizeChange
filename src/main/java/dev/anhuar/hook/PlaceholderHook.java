package dev.anhuar.hook;

import dev.anhuar.SizeChange;
import dev.anhuar.handler.DenyRegionHandler;
import dev.anhuar.manager.SizeManager;
import dev.anhuar.manager.TemporarySizeManager;
import dev.anhuar.model.TemporarySizeData;
import dev.anhuar.util.DurationUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.UUID;

// PlaceholderAPI expansion (%sizechange_<x>%). Only instantiated/registered when
// PlaceholderAPI is actually installed — see SizeChange.onEnable — so the
// me.clip.* classes are never touched otherwise.
//
// All data is read from the in-memory managers, so the size placeholders resolve
// only for ONLINE players; an offline player yields an empty string (booleans
// still answer "false"/"0").
public class PlaceholderHook extends PlaceholderExpansion {

    private final SizeChange plugin;

    public PlaceholderHook(SizeChange plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "sizechange";
    }

    @Override
    public @NotNull String getAuthor() {
        return "anhuar";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    // Keep the expansion registered across PlaceholderAPI reloads — it holds no
    // state of its own and its plugin outlives a /papi reload.
    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        if (offlinePlayer == null) {
            return "";
        }

        UUID uuid = offlinePlayer.getUniqueId();
        Player player = offlinePlayer.getPlayer();

        SizeManager sizeManager = plugin.getManagerHandler().getSizeManager();
        TemporarySizeManager tempManager = plugin.getManagerHandler().getTemporarySizeManager();
        boolean hasTemp = tempManager.hasTemporarySize(uuid);
        TemporarySizeData temp = hasTemp ? tempManager.getTemporarySize(uuid) : null;

        switch (params.toLowerCase(Locale.ROOT)) {
            case "size", "size_formatted" -> {
                return player == null ? "" : format(sizeManager.getSize(player));
            }
            case "size_raw" -> {
                return player == null ? "" : String.valueOf(sizeManager.getSize(player));
            }
            case "permanent_size" -> {
                if (player == null) {
                    return "";
                }
                if (hasTemp) {
                    return format(temp.getOriginalSize());
                }
                double live = sizeManager.getSize(player);
                double permanent = DenyRegionHandler.hasVisualOverride(uuid)
                        ? plugin.getManagerHandler().getPlayerDataManager().getLastKnownPermanent(uuid, live)
                        : live;
                return format(permanent);
            }
            case "temporary_active" -> {
                return String.valueOf(hasTemp);
            }
            case "temporary_size" -> {
                return hasTemp ? format(temp.getTemporarySize()) : "";
            }
            case "temporary_remaining" -> {
                return hasTemp ? DurationUtil.format(temp.getRemainingTime()) : "";
            }
            case "temporary_remaining_seconds" -> {
                return hasTemp ? String.valueOf(temp.getRemainingTime() / 1000) : "0";
            }
            case "override_active" -> {
                return String.valueOf(DenyRegionHandler.hasVisualOverride(uuid));
            }
            case "combat_tagged" -> {
                return String.valueOf(plugin.getManagerHandler().getCombatTagManager().isTagged(uuid));
            }
            case "combat_remaining" -> {
                return String.valueOf(plugin.getManagerHandler().getCombatTagManager().getRemainingSeconds(uuid));
            }
            case "attribute_count" -> {
                return String.valueOf(plugin.getManagerHandler().getAttributeModifierManager().count(uuid));
            }
            default -> {
                // %sizechange_attribute_<name>% -> the player's current effective
                // value for that attribute (base + every modifier, ours included).
                if (params.toLowerCase(Locale.ROOT).startsWith("attribute_")) {
                    if (player == null) {
                        return "";
                    }
                    var manager = plugin.getManagerHandler().getAttributeModifierManager();
                    var attribute = manager.resolveAttribute(params.substring("attribute_".length()));
                    if (attribute == null) {
                        return "";
                    }
                    var instance = player.getAttribute(attribute);
                    return instance == null ? "" : format(instance.getValue());
                }
                return null;
            }
        }
    }

    private String format(double value) {
        return String.format(Locale.US, "%.2f", value);
    }
}
