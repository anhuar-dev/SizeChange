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
import java.util.function.Consumer;

import static dev.anhuar.menu.MenuItemFactory.buildConfiguredItem;

// Landing page opened by /sizechange config: pick what to manage — one of the
// four region/world size collections, the PvP combat settings, or the size-change
// effects settings — then the matching editor opens.
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

        // menu.yml button key -> what clicking it opens. LinkedHashMap only for a
        // stable placement order; a missing button section is simply skipped.
        Map<String, Consumer<Player>> buttons = new LinkedHashMap<>();
        buttons.put("deny-button", p -> new RegionConfigMenu(plugin, RegionConfigMenu.ConfigType.DENY_REGION).open(p, 0));
        buttons.put("world-deny-button", p -> new RegionConfigMenu(plugin, RegionConfigMenu.ConfigType.DENY_WORLD).open(p, 0));
        buttons.put("size-button", p -> new RegionConfigMenu(plugin, RegionConfigMenu.ConfigType.SIZE_REGION).open(p, 0));
        buttons.put("world-size-button", p -> new RegionConfigMenu(plugin, RegionConfigMenu.ConfigType.SIZE_WORLD).open(p, 0));
        buttons.put("combat-button", p -> SettingsMenu.combat(plugin).open(p));
        buttons.put("effects-button", p -> SettingsMenu.effects(plugin).open(p));

        Map<Integer, Consumer<Player>> slotActions = new HashMap<>();
        for (Map.Entry<String, Consumer<Player>> entry : buttons.entrySet()) {
            ConfigurationSection section = cfg.getConfigurationSection(entry.getKey());
            if (section == null) {
                continue;
            }
            int slot = section.getInt("slot", -1);
            if (slot >= 0) {
                slotActions.put(slot, entry.getValue());
            }
        }

        MenuHolder holder = new MenuHolder((player, slot) -> {
            Consumer<Player> action = slotActions.get(slot);
            if (action != null) {
                action.accept(player);
            }
        });

        Inventory inventory = Bukkit.createInventory(holder, rows * 9, ColorUtil.translate(title));
        holder.setInventory(inventory);

        ItemStack fillItem = buildConfiguredItem(plugin.getLogger(), cfg.getConfigurationSection("fill"), Map.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, fillItem);
        }

        for (String buttonKey : buttons.keySet()) {
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
