package dev.anhuar.manager;

import dev.anhuar.SizeChange;
import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// World-level counterpart of RegionManager + DenyRegionHandler's SIZE-REGION
// cache, kept in one place because — unlike regions — there is no WorldGuard
// session Handler involved, so nothing forces the split.
//
//   DENY-WORLD  -> list of world names; size forced to 1.0 while inside
//   SIZE-WORLD  -> world name -> forced size
//
// Both behave exactly like their region equivalents: visual-only while the
// player is in the world, the real permanent/temporary size is never touched or
// written to the database, and it is restored on leaving. A DENY-REGION /
// SIZE-REGION the player is standing in always takes precedence over the world.
public class WorldManager {

    @Getter
    private volatile Set<String> denyWorlds = Collections.emptySet();

    @Getter
    private volatile Map<String, Double> sizeWorlds = Collections.emptyMap();

    public void reload() {
        YamlConfiguration config = SizeChange.getInstance().getSetting().getConfig();

        List<String> deny = config.getStringList("DENY-WORLD");
        if (deny.isEmpty()) {
            denyWorlds = Collections.emptySet();
        } else {
            Set<String> newDeny = ConcurrentHashMap.newKeySet(deny.size());
            newDeny.addAll(deny);
            denyWorlds = Collections.unmodifiableSet(newDeny);
        }

        ConfigurationSection section = config.getConfigurationSection("SIZE-WORLD");
        if (section == null) {
            sizeWorlds = Collections.emptyMap();
        } else {
            Map<String, Double> newSizes = new ConcurrentHashMap<>();
            for (String key : section.getKeys(false)) {
                newSizes.put(key, section.getDouble(key, 1.0));
            }
            sizeWorlds = Collections.unmodifiableMap(newSizes);
        }

        SizeChange.getInstance().getLogger().info(
                "Loaded " + denyWorlds.size() + " deny worlds and " + sizeWorlds.size() + " size worlds");
    }

    public boolean isDenyWorld(String worldName) {
        return denyWorlds.contains(worldName);
    }

    public boolean isSizeWorld(String worldName) {
        return sizeWorlds.containsKey(worldName);
    }

    // IMPORTANT: mirrors the resolution order used for regions in
    // DenyRegionHandler — a deny entry forces 1.0, a size entry forces its
    // value, anything else returns null ("no world override applies"). Callers
    // rely on null vs non-null to decide whether to keep the player under a
    // visual override or restore their real size.
    public Double getOverrideSize(String worldName) {
        if (worldName == null) {
            return null;
        }
        if (denyWorlds.contains(worldName)) {
            return 1.0;
        }
        return sizeWorlds.get(worldName);
    }
}
