package dev.anhuar.menu;

import dev.anhuar.util.ColorUtil;
import dev.anhuar.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

// Shared item-building helpers for menu.yml-driven GUIs (PlayerListMenu,
// RegionConfigMenu). Kept here instead of duplicated per menu class.
public final class MenuItemFactory {

    private MenuItemFactory() {
    }

    public static ItemStack buildConfiguredItem(Logger logger, ConfigurationSection section, Map<String, String> placeholders) {
        if (section == null) {
            return new ItemStack(Material.STONE);
        }
        return buildItem(logger, section.getString("material", "STONE"), section.getString("name"),
                section.getStringList("lore"), placeholders);
    }

    public static ItemStack buildItem(Logger logger, String materialKey, String name, List<String> lore, Map<String, String> placeholders) {
        Material material = parseMaterial(logger, materialKey);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            applyNameAndLore(meta, name, lore, placeholders);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static void applyNameAndLore(ItemMeta meta, String name, List<String> lore, Map<String, String> placeholders) {
        if (name != null) {
            meta.displayName(ColorUtil.translate(MessageUtil.applyPlaceholders(name, placeholders)));
        }
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore.stream()
                    .map(line -> MessageUtil.applyPlaceholders(line, placeholders))
                    .map(ColorUtil::translate)
                    .toList());
        }
    }

    public static Material parseMaterial(Logger logger, String key) {
        if (key == null) {
            return Material.STONE;
        }
        Material material = Material.matchMaterial(key);
        if (material == null) {
            logger.warning("Invalid material in menu.yml: '" + key + "', defaulting to STONE");
            return Material.STONE;
        }
        return material;
    }
}
