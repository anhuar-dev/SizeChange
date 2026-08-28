package dev.anhuar.menu;

import dev.anhuar.SizeChange;
import dev.anhuar.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static dev.anhuar.menu.MenuItemFactory.buildConfiguredItem;

// Landing page opened by /sizechange config: pick which override collection to
// manage (DENY-REGION / SIZE-REGION / DENY-WORLD / SIZE-WORLD) before
// RegionConfigMenu opens, scoped to just that one collection.
public class RegionTypeMenu {

    private final SizeChange plugin;

    public RegionTypeMenu(SizeChange plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer) {
        ConfigurationSection cfg = plugin.getMenu().getConfig().getConfigurationSection("region-config-select");
        if (cfg == null) {
            viewer.sendMessage(ColorUtil.translate("<red>menu.yml is misconfigured (missing the 'region-config-select' section)."));
            return;
        }

        int rows = Math.max(1, Math.min(6, cfg.getInt("rows", 3)));
        String title = cfg.getString("title", "Configure Overrides");

        // menu.yml button key -> which collection it opens. LinkedHashMap only for
        // stable placement order; a missing button section is simply skipped.
        Map<String, RegionConfigMenu.ConfigType> buttonTypes = new LinkedHashMap<>();
        buttonTypes.put("deny-button", RegionConfigMenu.ConfigType.DENY_REGION);
        buttonTypes.put("world-deny-button", RegionConfigMenu.ConfigType.DENY_WORLD);
        buttonTypes.put("size-button", RegionConfigMenu.ConfigType.SIZE_REGION);
        buttonTypes.put("world-size-button", RegionConfigMenu.ConfigType.SIZE_WORLD);

        Map<Integer, RegionConfigMenu.ConfigType> slotTypes = new HashMap<>();
        for (Map.Entry<String, RegionConfigMenu.ConfigType> entry : buttonTypes.entrySet()) {
            ConfigurationSection section = cfg.getConfigurationSection(entry.getKey());
            if (section == null) {
                continue;
            }
            int slot = section.getInt("slot", -1);
            if (slot >= 0) {
                slotTypes.put(slot, entry.getValue());
            }
        }

        MenuHolder holder = new MenuHolder((player, slot) -> {
            RegionConfigMenu.ConfigType picked = slotTypes.get(slot);
            if (picked != null) {
                new RegionConfigMenu(plugin, picked).open(player, 0);
            }
        });

        Inventory inventory = Bukkit.createInventory(holder, rows * 9, ColorUtil.translate(title));
        holder.setInventory(inventory);

        ItemStack fillItem = buildConfiguredItem(plugin.getLogger(), cfg.getConfigurationSection("fill"), Map.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, fillItem);
        }

        for (String buttonKey : buttonTypes.keySet()) {
            placeButton(inventory, cfg.getConfigurationSection(buttonKey));
        }

        viewer.openInventory(inventory);
    }

    private void placeButton(Inventory inventory, ConfigurationSection section) {
        if (section == null) {
            return;
        }
        int slot = section.getInt("slot", -1);
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, buildConfiguredItem(plugin.getLogger(), section, Map.of()));
    }
}
