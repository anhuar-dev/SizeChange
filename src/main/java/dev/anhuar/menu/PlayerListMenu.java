package dev.anhuar.menu;

import dev.anhuar.SizeChange;
import dev.anhuar.manager.TemporarySizeManager;
import dev.anhuar.model.TemporarySizeData;
import dev.anhuar.util.ColorUtil;
import dev.anhuar.util.DurationUtil;
import dev.anhuar.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import static dev.anhuar.menu.MenuItemFactory.applyNameAndLore;
import static dev.anhuar.menu.MenuItemFactory.buildConfiguredItem;
import static dev.anhuar.menu.MenuItemFactory.buildItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

// IMPORTANT: fully driven by menu.yml (section "list") so server owners can
// reshape the layout (title, rows, slots, materials, lore) without touching code.
// Only online players are listed — no new database "list all" query, see the
// plan discussed with the user. The menu is read-only: clicking a head does
// nothing, only the configured previous/next slots react (see MenuListener).
public class PlayerListMenu {

    private final SizeChange plugin;

    public PlayerListMenu(SizeChange plugin) {
        this.plugin = plugin;
    }

    public void open(Player viewer, int page) {
        ConfigurationSection cfg = plugin.getMenu().getConfig().getConfigurationSection("list");
        if (cfg == null) {
            viewer.sendMessage(ColorUtil.translate("<red>menu.yml is misconfigured (missing the 'list' section)."));
            return;
        }

        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        int startSlot = cfg.getInt("content.start-slot", 0);
        int endSlot = cfg.getInt("content.end-slot", 44);
        int pageSize = Math.max(1, endSlot - startSlot + 1);

        int totalPages = Math.max(1, (int) Math.ceil(online.size() / (double) pageSize));
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * pageSize;
        List<Player> pagePlayers = online.subList(start, Math.min(start + pageSize, online.size()));

        TemporarySizeManager tempManager = plugin.getManagerHandler().getTemporarySizeManager();

        List<CompletableFuture<ItemStack>> headFutures = new ArrayList<>();
        for (Player target : pagePlayers) {
            headFutures.add(loadHead(target, tempManager, cfg));
        }

        int totalPlayers = online.size();
        CompletableFuture.allOf(headFutures.toArray(new CompletableFuture[0]))
                .thenApply(v -> headFutures.stream().map(CompletableFuture::join).toList())
                .thenAccept(heads -> plugin.getServer().getScheduler().runTask(plugin, () ->
                        build(viewer, cfg, currentPage, totalPages, totalPlayers, startSlot, endSlot, heads)))
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("Error building the player list menu: " + throwable.getMessage());
                    return null;
                });
    }

    private CompletableFuture<ItemStack> loadHead(Player target, TemporarySizeManager tempManager, ConfigurationSection cfg) {
        return plugin.getManagerHandler().getPlayerDataManager()
                .getDatabase()
                .loadPlayer(target.getUniqueId())
                .thenApply(data -> {
                    double size = data != null ? data.getSize() : 1.0;

                    boolean hasTemp = tempManager.hasTemporarySize(target.getUniqueId());
                    String tempSizeStr = "-";
                    String remainingStr = "-";
                    if (hasTemp) {
                        TemporarySizeData tempData = tempManager.getTemporarySize(target.getUniqueId());
                        tempSizeStr = String.valueOf(tempData.getTemporarySize());
                        remainingStr = DurationUtil.format(tempData.getRemainingTime());
                    }

                    Map<String, String> placeholders = new MessageUtil.PlaceholderBuilder()
                            .add("player", target.getName())
                            .add("size", size)
                            .add("has_temp", hasTemp ? "Sí" : "No")
                            .add("temp_size", tempSizeStr)
                            .add("remaining", remainingStr)
                            .build();

                    return buildHeadItem(target, cfg, placeholders);
                })
                .exceptionally(throwable -> {
                    plugin.getLogger().warning("Error loading data for " + target.getName() + " (list menu): " + throwable.getMessage());
                    return buildHeadItem(target, cfg, new MessageUtil.PlaceholderBuilder()
                            .add("player", target.getName())
                            .add("size", 1.0)
                            .add("has_temp", "No")
                            .add("temp_size", "-")
                            .add("remaining", "-")
                            .build());
                });
    }

    private ItemStack buildHeadItem(Player target, ConfigurationSection cfg, Map<String, String> placeholders) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(target);
        }
        if (meta != null) {
            applyNameAndLore(meta, cfg.getString("player-head.name"), cfg.getStringList("player-head.lore"), placeholders);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void build(Player viewer, ConfigurationSection cfg, int currentPage, int totalPages,
                        int totalPlayers, int startSlot, int endSlot, List<ItemStack> heads) {

        if (!viewer.isOnline()) {
            return;
        }

        int rows = Math.max(1, Math.min(6, cfg.getInt("rows", 6)));
        String title = MessageUtil.applyPlaceholders(cfg.getString("title", "Players"),
                new MessageUtil.PlaceholderBuilder()
                        .add("page", currentPage + 1)
                        .add("total_pages", totalPages)
                        .build());

        MenuHolder holder = new MenuHolder((player, slot) -> handleClick(player, cfg, slot, currentPage, totalPages));
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, ColorUtil.translate(title));
        holder.setInventory(inventory);

        ItemStack fillItem = buildConfiguredItem(plugin.getLogger(), cfg.getConfigurationSection("fill"), Map.of());
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, fillItem);
        }

        if (heads.isEmpty()) {
            ConfigurationSection empty = cfg.getConfigurationSection("empty");
            if (empty != null) {
                inventory.setItem(empty.getInt("slot", 22), buildConfiguredItem(plugin.getLogger(), empty, Map.of()));
            }
        } else {
            int slot = startSlot;
            for (ItemStack head : heads) {
                if (slot > endSlot || slot >= inventory.getSize()) {
                    break;
                }
                inventory.setItem(slot, head);
                slot++;
            }
        }

        Map<String, String> navPlaceholders = new MessageUtil.PlaceholderBuilder()
                .add("page", currentPage + 1)
                .add("total_pages", totalPages)
                .add("total_players", totalPlayers)
                .build();

        placeNavItem(inventory, cfg.getConfigurationSection("navigation.previous"), currentPage > 0, navPlaceholders);
        placeNavItem(inventory, cfg.getConfigurationSection("navigation.next"), currentPage < totalPages - 1, navPlaceholders);

        ConfigurationSection infoSection = cfg.getConfigurationSection("navigation.info");
        if (infoSection != null) {
            int slot = infoSection.getInt("slot", -1);
            if (slot >= 0 && slot < inventory.getSize()) {
                inventory.setItem(slot, buildConfiguredItem(plugin.getLogger(), infoSection, navPlaceholders));
            }
        }

        viewer.openInventory(inventory);
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

        ItemStack item = buildItem(plugin.getLogger(), materialKey, section.getString("name"), section.getStringList("lore"), placeholders);
        inventory.setItem(slot, item);
    }

    // IMPORTANT: the only reactive slots in this read-only menu. Anything else the
    // player clicks is already cancelled by MenuListener and does nothing here.
    private void handleClick(Player player, ConfigurationSection cfg, int slot, int currentPage, int totalPages) {
        ConfigurationSection previous = cfg.getConfigurationSection("navigation.previous");
        ConfigurationSection next = cfg.getConfigurationSection("navigation.next");

        if (previous != null && slot == previous.getInt("slot", -1) && currentPage > 0) {
            open(player, currentPage - 1);
            return;
        }
        if (next != null && slot == next.getInt("slot", -1) && currentPage < totalPages - 1) {
            open(player, currentPage + 1);
        }
    }
}
