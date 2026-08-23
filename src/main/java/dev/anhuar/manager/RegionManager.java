package dev.anhuar.manager;

import dev.anhuar.SizeChange;
import lombok.Getter;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class RegionManager {

    @Getter
    private volatile Set<String> denyRegions = Collections.emptySet();

    public void loadDenyRegions(List<String> regions) {
        if (regions == null || regions.isEmpty()) {
            denyRegions = Collections.emptySet();
            return;
        }

        Set<String> newRegions = ConcurrentHashMap.newKeySet(regions.size());
        newRegions.addAll(regions);
        denyRegions = Collections.unmodifiableSet(newRegions);
    }

    public void reloadDenyRegions() {
        List<String> regions = SizeChange.getInstance().getSetting().getConfig().getStringList("DENY-REGION");
        loadDenyRegions(regions);
        SizeChange.getInstance().getLogger().info("Loaded " + denyRegions.size() + " deny regions");
    }

    public boolean isDenyRegion(String regionName) {
        return denyRegions.contains(regionName);
    }
}