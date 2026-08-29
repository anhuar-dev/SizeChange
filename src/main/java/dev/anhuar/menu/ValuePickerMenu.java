package dev.anhuar.menu;

import dev.anhuar.SizeChange;
import dev.anhuar.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static dev.anhuar.menu.MenuItemFactory.buildConfiguredItem;
import static dev.anhuar.menu.MenuItemFactory.buildItem;

// Paginated "click one of these strings" menu — used by SettingsMenu to browse
// every Sound / Particle instead of typing the name. 45 values per page on rows
// 1-5, a nav row on row 6. Clicking a value runs onPick and returns to `back`.
public class ValuePickerMenu {

    private static final int PAGE_SIZE = 45;
    private static final int PREVIOUS_SLOT = 45;
    private static final int BACK_SLOT = 49;
    private static final int NEXT_SLOT = 53;

    private final SizeChange plugin;
    private final String title;
    private final List<String> values;
    private final String iconMaterial;
    private final String current;
    private final Consumer<String> onPick;
    private final Consumer<Player> back;

    public ValuePickerMenu(SizeChange plugin, String title, List<String> values, String iconMaterial,
                           String current, Consumer<String> onPick, Consumer<Player> back) {
        this.plugin = plugin;
        this.title = title;
        this.values = values;
        this.iconMaterial = iconMaterial;
        this.current = current == null ? "" : current;
        this.onPick = onPick;
        this.back = back;
    }

    public void open(Player viewer, int page) {
        ConfigurationSection cfg = plugin.getMenu().getConfig().getConfigurationSection("value-picker");

        int totalPages = Math.max(1, (int) Math.ceil(values.size() / (double) PAGE_SIZE));
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * PAGE_SIZE;
        List<String> pageValues = values.subList(start, Math.min(start + PAGE_SIZE, values.size()));

        String heading = title + " <gray>(" + (currentPage + 1) + "/" + totalPages + ")";

        MenuHolder holder = new MenuHolder((player, slot) ->
                handleClick(player, slot, currentPage, totalPages, pageValues));
        Inventory inventory = Bukkit.createInventory(holder, 54, ColorUtil.translate(heading));
        holder.setInventory(inventory);

        ItemStack fill = cfg != null
                ? buildConfiguredItem(plugin.getLogger(), cfg.getConfigurationSection("fill"), Map.of())
                : new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = PAGE_SIZE; i < 54; i++) {
            inventory.setItem(i, fill);
        }

        for (int i = 0; i < pageValues.size(); i++) {
            String value = pageValues.get(i);
            String name = value.equals(current) ? "<green>✔ " + value : "<yellow>" + value;
            inventory.setItem(i, buildItem(plugin.getLogger(), iconMaterial, name,
                    List.of("<gray>Click to select"), Map.of()));
        }

        if (currentPage > 0) {
            inventory.setItem(PREVIOUS_SLOT, buildItem(plugin.getLogger(), "ARROW", "<gray>« Previous", List.of(), Map.of()));
        }
        inventory.setItem(BACK_SLOT, buildItem(plugin.getLogger(), "BARRIER", "<red>Back", List.of(), Map.of()));
        if (currentPage < totalPages - 1) {
            inventory.setItem(NEXT_SLOT, buildItem(plugin.getLogger(), "ARROW", "<gray>Next »", List.of(), Map.of()));
        }

        viewer.openInventory(inventory);
    }

    private void handleClick(Player player, int slot, int currentPage, int totalPages, List<String> pageValues) {
        if (slot == PREVIOUS_SLOT && currentPage > 0) {
            open(player, currentPage - 1);
            return;
        }
        if (slot == NEXT_SLOT && currentPage < totalPages - 1) {
            open(player, currentPage + 1);
            return;
        }
        if (slot == BACK_SLOT) {
            back.accept(player);
            return;
        }
        if (slot >= 0 && slot < pageValues.size()) {
            onPick.accept(pageValues.get(slot));
            back.accept(player);
        }
    }
}
