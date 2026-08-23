package dev.anhuar.menu;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import dev.anhuar.SizeChange;
import dev.anhuar.handler.DenyRegionHandler;
import dev.anhuar.util.ColorUtil;
import dev.anhuar.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dev.anhuar.menu.MenuItemFactory.buildConfiguredItem;
import static dev.anhuar.menu.MenuItemFactory.buildItem;

// In-game editor for ONE of DENY-REGION / SIZE-REGION in setting.yml, scoped by
// `type` for the lifetime of this instance — reached via RegionTypeMenu, which
// picks the type first. Unlike PlayerListMenu (read-only), this menu writes back
// to disk: clicking an existing entry asks for confirmation before removing it,
// the "add" button walks the player through a chat-input flow (region name, then
// size if this is the SIZE-REGION menu) via ChatInputManager. Fully driven by
// menu.yml's "region-config" section.
public class RegionConfigMenu {

    public enum RegionType {
        DENY, SIZE
    }

    private record RegionEntry(String name, Double size) {
    }

    private static final double MIN_SIZE = 0.0625;
    private static final double MAX_SIZE = 16.0;

    private final SizeChange plugin;
    private final RegionType type;

    public RegionConfigMenu(SizeChange plugin, RegionType type) {
        this.plugin = plugin;
        this.type = type;
    }

    public void open(Player viewer, int page) {
        ConfigurationSection cfg = plugin.getMenu().getConfig().getConfigurationSection("region-config");
        if (cfg == null) {
            viewer.sendMessage(ColorUtil.translate("<red>menu.yml is misconfigured (missing the 'region-config' section)."));
            return;
        }

        List<RegionEntry> entries = loadEntries();

        int startSlot = cfg.getInt("content.start-slot", 0);
        int endSlot = cfg.getInt("content.end-slot", 44);
        int pageSize = Math.max(1, endSlot - startSlot + 1);

        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) pageSize));
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * pageSize;
        List<RegionEntry> pageEntries = entries.subList(start, Math.min(start + pageSize, entries.size()));

        int rows = Math.max(1, Math.min(6, cfg.getInt("rows", 6)));
        String title = MessageUtil.applyPlaceholders(cfg.getString("title", "Regions"),
                new MessageUtil.PlaceholderBuilder()
                        .add("type", typeLabel())
                        .add("page", currentPage + 1)
                        .add("total_pages", totalPages)
                        .build());

        MenuHolder holder = new MenuHolder((player, slot) ->
                handleClick(player, cfg, slot, currentPage, totalPages, startSlot, pageEntries));
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, ColorUtil.translate(title));
        holder.setInventory(inventory);

        ItemStack fillItem = buildConfiguredItem(plugin.getLogger(), cfg.getConfigurationSection("fill"), Map.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, fillItem);
        }

        int slot = startSlot;
        for (RegionEntry entry : pageEntries) {
            if (slot > endSlot || slot >= inventory.getSize()) {
                break;
            }
            inventory.setItem(slot, buildEntryItem(cfg, entry));
            slot++;
        }

        Map<String, String> navPlaceholders = new MessageUtil.PlaceholderBuilder()
                .add("type", typeLabel())
                .add("page", currentPage + 1)
                .add("total_pages", totalPages)
                .build();

        placeNavItem(inventory, cfg.getConfigurationSection("navigation.previous"), currentPage > 0, navPlaceholders);
        placeNavItem(inventory, cfg.getConfigurationSection("navigation.next"), currentPage < totalPages - 1, navPlaceholders);

        placeConfiguredSlot(inventory, cfg.getConfigurationSection("add"), navPlaceholders);
        placeConfiguredSlot(inventory, cfg.getConfigurationSection("back"), navPlaceholders);

        viewer.openInventory(inventory);
    }

    private String typeLabel() {
        return type == RegionType.DENY ? "DENY-REGION" : "SIZE-REGION";
    }

    private List<RegionEntry> loadEntries() {
        List<RegionEntry> entries = new ArrayList<>();

        if (type == RegionType.DENY) {
            for (String region : plugin.getSetting().getConfig().getStringList("DENY-REGION")) {
                entries.add(new RegionEntry(region, null));
            }
        } else {
            ConfigurationSection sizeSection = plugin.getSetting().getConfig().getConfigurationSection("SIZE-REGION");
            if (sizeSection != null) {
                for (String region : sizeSection.getKeys(false)) {
                    entries.add(new RegionEntry(region, sizeSection.getDouble(region)));
                }
            }
        }

        return entries;
    }

    private ItemStack buildEntryItem(ConfigurationSection cfg, RegionEntry entry) {
        String sectionKey = type == RegionType.DENY ? "deny-entry" : "size-entry";
        return buildConfiguredItem(plugin.getLogger(), cfg.getConfigurationSection(sectionKey), entryPlaceholders(entry));
    }

    private Map<String, String> entryPlaceholders(RegionEntry entry) {
        return type == RegionType.DENY
                ? new MessageUtil.PlaceholderBuilder().add("region", entry.name()).build()
                : new MessageUtil.PlaceholderBuilder().add("region", entry.name()).add("size", entry.size()).build();
    }

    private void placeNavItem(Inventory inventory, ConfigurationSection section, boolean enabled, Map<String, String> placeholders) {
        if (section == null) {
            return;
        }
        int slot = section.getInt("slot", -1);
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }

        String materialKey = enabled
                ? section.getString("material", "ARROW")
                : section.getString("disabled-material", section.getString("material", "ARROW"));

        inventory.setItem(slot, buildItem(plugin.getLogger(), materialKey, section.getString("name"), section.getStringList("lore"), placeholders));
    }

    private void placeConfiguredSlot(Inventory inventory, ConfigurationSection section, Map<String, String> placeholders) {
        if (section == null) {
            return;
        }
        int slot = section.getInt("slot", -1);
        if (slot < 0 || slot >= inventory.getSize()) {
            return;
        }
        inventory.setItem(slot, buildConfiguredItem(plugin.getLogger(), section, placeholders));
    }

    private void handleClick(Player player, ConfigurationSection cfg, int slot, int currentPage, int totalPages,
                              int startSlot, List<RegionEntry> pageEntries) {

        ConfigurationSection previous = cfg.getConfigurationSection("navigation.previous");
        ConfigurationSection next = cfg.getConfigurationSection("navigation.next");
        ConfigurationSection add = cfg.getConfigurationSection("add");
        ConfigurationSection back = cfg.getConfigurationSection("back");

        if (previous != null && slot == previous.getInt("slot", -1) && currentPage > 0) {
            open(player, currentPage - 1);
            return;
        }
        if (next != null && slot == next.getInt("slot", -1) && currentPage < totalPages - 1) {
            open(player, currentPage + 1);
            return;
        }
        if (add != null && slot == add.getInt("slot", -1)) {
            startAddFlow(player, currentPage);
            return;
        }
        if (back != null && slot == back.getInt("slot", -1)) {
            new RegionTypeMenu(plugin).open(player);
            return;
        }

        int index = slot - startSlot;
        if (index < 0 || index >= pageEntries.size()) {
            return;
        }

        openConfirmDelete(player, cfg, pageEntries.get(index), currentPage);
    }

    // IMPORTANT: deleting a region entry is a destructive, one-click-away action
    // in a menu where every other slot is a single click — a confirmation step
    // was added at the user's request to avoid an accidental misclick removing a
    // configured region.
    private void openConfirmDelete(Player player, ConfigurationSection cfg, RegionEntry entry, int returnPage) {
        ConfigurationSection confirmCfg = cfg.getConfigurationSection("confirm-delete");
        if (confirmCfg == null) {
            // menu.yml is missing this section (e.g. an old menu.yml from before this
            // feature existed) — fail safe by skipping the delete rather than either
            // silently deleting without confirmation or crashing the click handler.
            player.sendMessage(ColorUtil.translate("<red>menu.yml is misconfigured (missing 'region-config.confirm-delete')."));
            open(player, returnPage);
            return;
        }

        Map<String, String> placeholders = entryPlaceholders(entry);

        int rows = Math.max(1, Math.min(6, confirmCfg.getInt("rows", 3)));
        String title = MessageUtil.applyPlaceholders(confirmCfg.getString("title", "Confirm delete"), placeholders);

        ConfigurationSection cancelSection = confirmCfg.getConfigurationSection("cancel");
        ConfigurationSection confirmSection = confirmCfg.getConfigurationSection("confirm");

        MenuHolder holder = new MenuHolder((clicker, slot) -> {
            if (cancelSection != null && slot == cancelSection.getInt("slot", -1)) {
                open(clicker, returnPage);
                return;
            }
            if (confirmSection != null && slot == confirmSection.getInt("slot", -1)) {
                removeEntry(clicker, entry);
                open(clicker, returnPage);
            }
        });

        Inventory inventory = Bukkit.createInventory(holder, rows * 9, ColorUtil.translate(title));
        holder.setInventory(inventory);

        ItemStack fillItem = buildConfiguredItem(plugin.getLogger(), cfg.getConfigurationSection("fill"), Map.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, fillItem);
        }

        placeConfiguredSlot(inventory, confirmCfg.getConfigurationSection("info"), placeholders);
        placeConfiguredSlot(inventory, cancelSection, placeholders);
        placeConfiguredSlot(inventory, confirmSection, placeholders);

        player.openInventory(inventory);
    }

    private void removeEntry(Player player, RegionEntry entry) {
        if (type == RegionType.DENY) {
            List<String> regions = new ArrayList<>(plugin.getSetting().getConfig().getStringList("DENY-REGION"));
            regions.remove(entry.name());
            plugin.getSetting().set("DENY-REGION", regions);
            plugin.getSetting().save();
            plugin.getManagerHandler().getRegionManager().reloadDenyRegions();
            MessageUtil.sendMessage(player, "config-deny-removed", MessageUtil.placeholder("region", entry.name()));
        } else {
            plugin.getSetting().set("SIZE-REGION." + entry.name(), null);
            plugin.getSetting().save();
            DenyRegionHandler.reloadSizeRegions();
            MessageUtil.sendMessage(player, "config-size-removed", MessageUtil.placeholder("region", entry.name()));
        }
    }

    private void startAddFlow(Player player, int returnPage) {
        ChatInputManager chatInput = plugin.getManagerHandler().getChatInputManager();

        chatInput.request(player, "config-add-prompt-name", Map.of(), nameInput ->
                onRegionNameProvided(player, returnPage, nameInput));
    }

    private void onRegionNameProvided(Player player, int returnPage, String nameInput) {
        String region = nameInput.trim();

        // IMPORTANT: reject '.' outright instead of trying to support it. Bukkit's
        // ConfigurationSection ALWAYS treats a key string as a dotted path, on every
        // get/set/contains call, regardless of which section you call it through —
        // there is no "raw key" API. A region literally named e.g. "spawn.arena"
        // would silently nest as SIZE-REGION -> spawn -> arena instead of being
        // stored as one flat entry, and loadEntries() would then fail to list it
        // correctly. WorldGuard region IDs are not expected to contain dots anyway.
        if (region.isEmpty() || region.contains(".")) {
            MessageUtil.sendMessage(player, "config-add-invalid-name");
            return;
        }

        // IMPORTANT: checks BOTH collections regardless of which menu (deny/size)
        // this is — a region can't usefully live in both DENY-REGION and
        // SIZE-REGION at once (DenyRegionHandler checks DENY-REGION first, so a
        // SIZE-REGION entry for the same id would just be dead config), so adding
        // it while it already exists in the other collection is rejected too.
        boolean existsInDeny = plugin.getSetting().getConfig().getStringList("DENY-REGION").contains(region);
        boolean existsInSize = plugin.getSetting().getConfig().contains("SIZE-REGION." + region);

        if (existsInDeny || existsInSize) {
            MessageUtil.sendMessage(player, "config-add-duplicate", MessageUtil.placeholder("region", region));
            return;
        }

        if (!regionExistsInAnyWorld(region)) {
            MessageUtil.sendMessage(player, "config-add-region-not-found-warning", MessageUtil.placeholder("region", region));
        }

        if (type == RegionType.DENY) {
            List<String> regions = new ArrayList<>(plugin.getSetting().getConfig().getStringList("DENY-REGION"));
            regions.add(region);
            plugin.getSetting().set("DENY-REGION", regions);
            plugin.getSetting().save();
            plugin.getManagerHandler().getRegionManager().reloadDenyRegions();
            MessageUtil.sendMessage(player, "config-deny-added", MessageUtil.placeholder("region", region));
            open(player, returnPage);
            return;
        }

        ChatInputManager chatInput = plugin.getManagerHandler().getChatInputManager();
        chatInput.request(player, "config-add-prompt-size", Map.of(), sizeInput ->
                onSizeProvided(player, returnPage, region, sizeInput));
    }

    private void onSizeProvided(Player player, int returnPage, String region, String sizeInput) {
        double size;
        try {
            size = Double.parseDouble(sizeInput.trim());
        } catch (NumberFormatException e) {
            MessageUtil.sendMessage(player, "config-add-invalid-size");
            return;
        }

        // IMPORTANT: written as a positive range check, not "< MIN || > MAX". Every
        // comparison against NaN returns false in Java, so the negated form would
        // let "NaN" (a valid double literal Double.parseDouble accepts without
        // throwing) slip through unrejected. This form correctly rejects it, since
        // NaN >= MIN_SIZE is also false.
        if (!(size >= MIN_SIZE && size <= MAX_SIZE)) {
            MessageUtil.sendMessage(player, "config-add-invalid-size");
            return;
        }

        plugin.getSetting().set("SIZE-REGION." + region, size);
        plugin.getSetting().save();
        DenyRegionHandler.reloadSizeRegions();
        MessageUtil.sendMessage(player, "config-size-added",
                new MessageUtil.PlaceholderBuilder().add("region", region).add("size", size).build());
        open(player, returnPage);
    }

    // IMPORTANT: this is advisory only — a region typed here might belong to a
    // world that isn't loaded yet, or the admin might be pre-configuring it before
    // creating the region. It warns on a likely typo without blocking a legitimate
    // save.
    private boolean regionExistsInAnyWorld(String regionId) {
        for (World world : Bukkit.getWorlds()) {
            var regionManager = WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
            if (regionManager != null && regionManager.getRegion(regionId) != null) {
                return true;
            }
        }
        return false;
    }
}
