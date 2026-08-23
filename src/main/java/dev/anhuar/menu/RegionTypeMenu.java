package dev.anhuar.menu;

import dev.anhuar.SizeChange;
import dev.anhuar.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

import static dev.anhuar.menu.MenuItemFactory.buildConfiguredItem;

// Landing page opened by /sizechange config: pick DENY-REGION or SIZE-REGION
// before RegionConfigMenu opens, scoped to just that one collection.
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
        String title = cfg.getString("title", "Configure Regions");

        ConfigurationSection denySection = cfg.getConfigurationSection("deny-button");
        ConfigurationSection sizeSection = cfg.getConfigurationSection("size-button");

        MenuHolder holder = new MenuHolder((player, slot) -> {
            if (denySection != null && slot == denySection.getInt("slot", -1)) {
                new RegionConfigMenu(plugin, RegionConfigMenu.RegionType.DENY).open(player, 0);
                return;
            }
            if (sizeSection != null && slot == sizeSection.getInt("slot", -1)) {
                new RegionConfigMenu(plugin, RegionConfigMenu.RegionType.SIZE).open(player, 0);
            }
        });

        Inventory inventory = Bukkit.createInventory(holder, rows * 9, ColorUtil.translate(title));
        holder.setInventory(inventory);

        ItemStack fillItem = buildConfiguredItem(plugin.getLogger(), cfg.getConfigurationSection("fill"), Map.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, fillItem);
        }

        placeButton(inventory, denySection);
        placeButton(inventory, sizeSection);

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
