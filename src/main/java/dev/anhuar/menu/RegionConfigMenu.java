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
import java.util.function.Consumer;

import static dev.anhuar.menu.MenuItemFactory.buildConfiguredItem;
import static dev.anhuar.menu.MenuItemFactory.buildItem;

// In-game editor for ONE name/size collection in setting.yml
// (DENY-REGION / SIZE-REGION / DENY-WORLD / SIZE-WORLD / COMBAT.EXCLUDED-REGIONS),
// scoped by `type` for the lifetime of this instance — reached via RegionTypeMenu
// or the Combat settings screen. Unlike PlayerListMenu (read-only), it writes to disk:
// clicking an existing entry asks for confirmation before removing it, the "add"
// button walks the player through a chat-input flow (region/world name, then size
// for the SIZE-* collections) via ChatInputManager. Fully driven by menu.yml's
// "region-config" section; the region and world collections reuse the same
// deny-entry / size-entry item templates, with {type} telling them apart.
public class RegionConfigMenu {

    // path      -> the setting.yml key this collection lives under
    // sized     -> true when each entry carries a forced size (SIZE-*), false
    //              when the entry is just a name and the forced size is 1.0
    // world     -> true for the world collections, false for the region ones —
    //              switches name validation (Bukkit world vs WorldGuard region)
    //              and which cache is reloaded after a write
    public enum ConfigType {
        DENY_REGION("DENY-REGION", false, false, "config-deny",
                "config-add-prompt-name", "config-add-region-not-found-warning"),
        SIZE_REGION("SIZE-REGION", true, false, "config-size",
                "config-add-prompt-name", "config-add-region-not-found-warning"),
        DENY_WORLD("DENY-WORLD", false, true, "config-world-deny",
                "config-add-prompt-world", "config-add-world-not-found-warning"),
        SIZE_WORLD("SIZE-WORLD", true, true, "config-world-size",
                "config-add-prompt-world", "config-add-world-not-found-warning"),
        // Region-id list at a nested path — the PvP combat tag's exclusions.
        COMBAT_EXCLUDED("COMBAT.EXCLUDED-REGIONS", false, false, "config-combat-excluded",
                "config-add-prompt-name", "config-add-region-not-found-warning");

        private final String path;
        private final boolean sized;
        private final boolean world;
        private final String msgPrefix;
        private final String promptNameKey;
        private final String notFoundKey;

        ConfigType(String path, boolean sized, boolean world, String msgPrefix,
                   String promptNameKey, String notFoundKey) {
            this.path = path;
            this.sized = sized;
            this.world = world;
            this.msgPrefix = msgPrefix;
            this.promptNameKey = promptNameKey;
            this.notFoundKey = notFoundKey;
        }

        String addedKey() {
            return msgPrefix + "-added";
        }

        String removedKey() {
            return msgPrefix + "-removed";
        }
    }

    private record Entry(String name, Double size) {
    }

    private static final double MIN_SIZE = 0.0625;
    private static final double MAX_SIZE = 16.0;

    private final SizeChange plugin;
    private final ConfigType type;
    // Where the "back" button goes. null -> the RegionTypeMenu hub (default).
    private final Consumer<Player> backTarget;

    public RegionConfigMenu(SizeChange plugin, ConfigType type) {
        this(plugin, type, null);
    }

    public RegionConfigMenu(SizeChange plugin, ConfigType type, Consumer<Player> backTarget) {
        this.plugin = plugin;
        this.type = type;
        this.backTarget = backTarget;
    }

    private void goBack(Player player) {
        if (backTarget != null) {
            backTarget.accept(player);
        } else {
            new RegionTypeMenu(plugin).open(player);
        }
    }

    public void open(Player viewer, int page) {
        ConfigurationSection cfg = plugin.getMenu().getConfig().getConfigurationSection("region-config");
        if (cfg == null) {
            viewer.sendMessage(ColorUtil.translate("<red>menu.yml is misconfigured (missing the 'region-config' section)."));
            return;
        }

        List<Entry> entries = loadEntries();

        int startSlot = cfg.getInt("content.start-slot", 0);
        int endSlot = cfg.getInt("content.end-slot", 44);
        int pageSize = Math.max(1, endSlot - startSlot + 1);

        int totalPages = Math.max(1, (int) Math.ceil(entries.size() / (double) pageSize));
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * pageSize;
        List<Entry> pageEntries = entries.subList(start, Math.min(start + pageSize, entries.size()));

        int rows = Math.max(1, Math.min(6, cfg.getInt("rows", 6)));
        String title = MessageUtil.applyPlaceholders(cfg.getString("title", "Regions"),
                new MessageUtil.PlaceholderBuilder()
                        .add("type", type.path)
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
        for (Entry entry : pageEntries) {
            if (slot > endSlot || slot >= inventory.getSize()) {
                break;
            }
            inventory.setItem(slot, buildEntryItem(cfg, entry));
            slot++;
        }

        Map<String, String> navPlaceholders = new MessageUtil.PlaceholderBuilder()
                .add("type", type.path)
                .add("page", currentPage + 1)
                .add("total_pages", totalPages)
                .build();

        placeNavItem(inventory, cfg.getConfigurationSection("navigation.previous"), currentPage > 0, navPlaceholders);
        placeNavItem(inventory, cfg.getConfigurationSection("navigation.next"), currentPage < totalPages - 1, navPlaceholders);

        placeConfiguredSlot(inventory, cfg.getConfigurationSection("add"), navPlaceholders);
        placeConfiguredSlot(inventory, cfg.getConfigurationSection("back"), navPlaceholders);

        viewer.openInventory(inventory);
    }

    private List<Entry> loadEntries() {
        List<Entry> entries = new ArrayList<>();

        if (type.sized) {
            ConfigurationSection section = plugin.getSetting().getConfig().getConfigurationSection(type.path);
            if (section != null) {
                for (String key : section.getKeys(false)) {
                    entries.add(new Entry(key, section.getDouble(key)));
                }
            }
        } else {
            for (String name : plugin.getSetting().getConfig().getStringList(type.path)) {
                entries.add(new Entry(name, null));
            }
        }

        return entries;
    }

    private ItemStack buildEntryItem(ConfigurationSection cfg, Entry entry) {
        String sectionKey = type.sized ? "size-entry" : "deny-entry";
        return buildConfiguredItem(plugin.getLogger(), cfg.getConfigurationSection(sectionKey), entryPlaceholders(entry));
    }

    // {region} and {world} are both filled with the entry id (aliases) so a
    // custom menu.yml written for either scope keeps working; {name} is the
    // scope-neutral one, {type} is the setting.yml key ("SIZE-WORLD", ...).
    private Map<String, String> entryPlaceholders(Entry entry) {
        MessageUtil.PlaceholderBuilder builder = new MessageUtil.PlaceholderBuilder()
                .add("name", entry.name())
                .add("region", entry.name())
                .add("world", entry.name())
                .add("type", type.path);
        if (entry.size() != null) {
            builder.add("size", entry.size());
        }
        return builder.build();
    }

    private Map<String, String> namePlaceholders(String name) {
        return new MessageUtil.PlaceholderBuilder()
                .add("name", name)
                .add("region", name)
                .add("world", name)
                .build();
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
                              int startSlot, List<Entry> pageEntries) {

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
            goBack(player);
            return;
        }

        int index = slot - startSlot;
        if (index < 0 || index >= pageEntries.size()) {
            return;
        }

        openConfirmDelete(player, cfg, pageEntries.get(index), currentPage);
    }

    // IMPORTANT: deleting an entry is a destructive, one-click-away action in a
    // menu where every other slot is a single click — a confirmation step was
    // added at the user's request to avoid an accidental misclick removing a
    // configured region or world.
    private void openConfirmDelete(Player player, ConfigurationSection cfg, Entry entry, int returnPage) {
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

    private void removeEntry(Player player, Entry entry) {
        if (type.sized) {
            plugin.getSetting().set(type.path + "." + entry.name(), null);
        } else {
            List<String> names = new ArrayList<>(plugin.getSetting().getConfig().getStringList(type.path));
            names.remove(entry.name());
            plugin.getSetting().set(type.path, names);
        }
        plugin.getSetting().save();
        refreshCaches();
        MessageUtil.sendMessage(player, type.removedKey(), namePlaceholders(entry.name()));
    }

    private void startAddFlow(Player player, int returnPage) {
        ChatInputManager chatInput = plugin.getManagerHandler().getChatInputManager();

        chatInput.request(player, type.promptNameKey, Map.of(), nameInput ->
                onNameProvided(player, returnPage, nameInput));
    }

    private void onNameProvided(Player player, int returnPage, String nameInput) {
        String name = nameInput.trim();

        // IMPORTANT: reject '.' outright instead of trying to support it. Bukkit's
        // ConfigurationSection ALWAYS treats a key string as a dotted path, on every
        // get/set/contains call, regardless of which section you call it through —
        // there is no "raw key" API. A name like "spawn.arena" would silently nest
        // instead of being stored as one flat entry, and loadEntries() would then
        // fail to list it correctly. WorldGuard region IDs and Bukkit world names
        // are not expected to contain dots anyway.
        if (name.isEmpty() || name.contains(".")) {
            MessageUtil.sendMessage(player, "config-add-invalid-name");
            return;
        }

        // IMPORTANT: checks BOTH collections in this scope regardless of which menu
        // (deny/size) this is — an entry can't usefully live in both the DENY and
        // the SIZE collection at once (the deny one is checked first everywhere, so
        // a size entry for the same id would just be dead config), so adding it
        // while it already exists in the sibling collection is rejected too.
        if (isDuplicate(name)) {
            MessageUtil.sendMessage(player, "config-add-duplicate", namePlaceholders(name));
            return;
        }

        if (!targetExists(name)) {
            MessageUtil.sendMessage(player, type.notFoundKey, namePlaceholders(name));
        }

        if (!type.sized) {
            saveUnsized(player, returnPage, name);
            return;
        }

        ChatInputManager chatInput = plugin.getManagerHandler().getChatInputManager();
        chatInput.request(player, "config-add-prompt-size", Map.of(), sizeInput ->
                onSizeProvided(player, returnPage, name, sizeInput));
    }

    private void saveUnsized(Player player, int returnPage, String name) {
        List<String> names = new ArrayList<>(plugin.getSetting().getConfig().getStringList(type.path));
        names.add(name);
        plugin.getSetting().set(type.path, names);
        plugin.getSetting().save();
        refreshCaches();
        MessageUtil.sendMessage(player, type.addedKey(), namePlaceholders(name));
        open(player, returnPage);
    }

    private void onSizeProvided(Player player, int returnPage, String name, String sizeInput) {
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

        plugin.getSetting().set(type.path + "." + name, size);
        plugin.getSetting().save();
        refreshCaches();
        MessageUtil.sendMessage(player, type.addedKey(),
                new MessageUtil.PlaceholderBuilder()
                        .add("name", name).add("region", name).add("world", name)
                        .add("size", size).build());
        open(player, returnPage);
    }

    private boolean isDuplicate(String name) {
        var config = plugin.getSetting().getConfig();
        if (type == ConfigType.COMBAT_EXCLUDED) {
            return config.getStringList(type.path).contains(name);
        }
        if (type.world) {
            return config.getStringList("DENY-WORLD").contains(name)
                    || config.contains("SIZE-WORLD." + name);
        }
        return config.getStringList("DENY-REGION").contains(name)
                || config.contains("SIZE-REGION." + name);
    }

    private boolean targetExists(String name) {
        if (type.world) {
            return Bukkit.getWorld(name) != null;
        }
        return regionExistsInAnyWorld(name);
    }

    private void refreshCaches() {
        switch (type) {
            case DENY_REGION -> plugin.getManagerHandler().getRegionManager().reloadDenyRegions();
            case SIZE_REGION -> DenyRegionHandler.reloadSizeRegions();
            case DENY_WORLD, SIZE_WORLD -> plugin.getManagerHandler().getWorldManager().reload();
            case COMBAT_EXCLUDED -> plugin.getManagerHandler().getCombatTagManager().reload();
        }
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
