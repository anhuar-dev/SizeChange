package dev.anhuar.menu;

import dev.anhuar.SizeChange;
import dev.anhuar.util.ColorUtil;
import dev.anhuar.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static dev.anhuar.menu.MenuItemFactory.buildConfiguredItem;
import static dev.anhuar.menu.MenuItemFactory.buildItem;

// Generic "edit a handful of setting.yml keys" GUI, used for the Combat and
// Effects screens opened from /sizechange config. Each Setting is one slot:
//   TOGGLE   - left click flips the boolean
//   INT / DECIMAL / TEXT - opens a chat prompt for the new value
//   ENUM     - click cycles through a fixed list
//   SUBMENU  - click opens another menu
// After any write the config is saved and onChange runs (a manager reload where
// one is needed). menu.yml only supplies the optional title / rows / fill for the
// "settings-combat" and "settings-effects" sections; the items are built here.
public class SettingsMenu {

    public enum Kind { TOGGLE, INT, DECIMAL, TEXT, ENUM, SUBMENU }

    public static final class Setting {
        private final int slot;
        private final Kind kind;
        private final String path;
        private final String label;
        private final String material;

        private double min = Double.NEGATIVE_INFINITY;
        private double max = Double.POSITIVE_INFINITY;
        private List<String> options = List.of();
        private Predicate<String> textValidator = s -> true;
        private Consumer<Player> submenu;
        private String promptKey;
        private Supplier<List<String>> pickerValues;
        private String pickerIcon;

        public Setting(int slot, Kind kind, String path, String label, String material) {
            this.slot = slot;
            this.kind = kind;
            this.path = path;
            this.label = label;
            this.material = material;
        }

        public Setting range(double min, double max) {
            this.min = min;
            this.max = max;
            return this;
        }

        public Setting options(String... values) {
            this.options = List.of(values);
            return this;
        }

        public Setting validator(Predicate<String> validator) {
            this.textValidator = validator;
            return this;
        }

        public Setting submenu(Consumer<Player> opener) {
            this.submenu = opener;
            return this;
        }

        public Setting prompt(String messageKey) {
            this.promptKey = messageKey;
            return this;
        }

        // Adds a "browse the full list" option alongside the chat prompt for a
        // TEXT setting (clicking it opens a chooser first).
        public Setting picker(Supplier<List<String>> values, String icon) {
            this.pickerValues = values;
            this.pickerIcon = icon;
            return this;
        }
    }

    private final SizeChange plugin;
    private final String menuKey;        // "combat" | "effects"
    private final String defaultTitle;
    private final Runnable onChange;     // nullable
    private final List<Setting> settings;
    private final Consumer<Player> back;

    public SettingsMenu(SizeChange plugin, String menuKey, String defaultTitle,
                        Runnable onChange, List<Setting> settings, Consumer<Player> back) {
        this.plugin = plugin;
        this.menuKey = menuKey;
        this.defaultTitle = defaultTitle;
        this.onChange = onChange;
        this.settings = settings;
        this.back = back;
    }

    public void open(Player viewer) {
        ConfigurationSection cfg = plugin.getMenu().getConfig().getConfigurationSection("settings-" + menuKey);

        int rows = cfg != null ? Math.max(1, Math.min(6, cfg.getInt("rows", 4))) : 4;
        String title = cfg != null ? cfg.getString("title", defaultTitle) : defaultTitle;
        int backSlot = cfg != null ? cfg.getInt("back-slot", rows * 9 - 1) : rows * 9 - 1;

        MenuHolder holder = new MenuHolder((player, slot) -> handleClick(player, slot, backSlot));
        Inventory inventory = Bukkit.createInventory(holder, rows * 9, ColorUtil.translate(title));
        holder.setInventory(inventory);

        ItemStack fill = cfg != null
                ? buildConfiguredItem(plugin.getLogger(), cfg.getConfigurationSection("fill"), Map.of())
                : new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, fill);
        }

        for (Setting setting : settings) {
            if (setting.slot >= 0 && setting.slot < inventory.getSize()) {
                inventory.setItem(setting.slot, renderItem(setting));
            }
        }
        if (backSlot >= 0 && backSlot < inventory.getSize()) {
            inventory.setItem(backSlot, buildItem(plugin.getLogger(), "ARROW", "<gray>← Back", List.of(), Map.of()));
        }

        viewer.openInventory(inventory);
    }

    private ItemStack renderItem(Setting setting) {
        List<String> lore = new ArrayList<>();
        lore.add("<gray>Current: <white>" + currentValue(setting) + "</white>");
        lore.add("");
        lore.add(switch (setting.kind) {
            case TOGGLE -> "<yellow>Click to toggle";
            case ENUM -> "<yellow>Click to cycle";
            case SUBMENU -> "<yellow>Click to open";
            default -> "<yellow>Click to edit";
        });
        return buildItem(plugin.getLogger(), setting.material, setting.label, lore, Map.of());
    }

    private String currentValue(Setting setting) {
        ConfigurationSection c = plugin.getSetting().getConfig();
        return switch (setting.kind) {
            case TOGGLE -> c.getBoolean(setting.path) ? "enabled" : "disabled";
            case INT -> String.valueOf(c.getInt(setting.path));
            case DECIMAL -> String.valueOf(c.getDouble(setting.path));
            case ENUM, TEXT -> String.valueOf(c.getString(setting.path, ""));
            case SUBMENU -> "…";
        };
    }

    private void handleClick(Player player, int slot, int backSlot) {
        if (slot == backSlot) {
            back.accept(player);
            return;
        }
        for (Setting setting : settings) {
            if (setting.slot == slot) {
                applyClick(player, setting);
                return;
            }
        }
    }

    private void applyClick(Player player, Setting setting) {
        ConfigurationSection c = plugin.getSetting().getConfig();
        switch (setting.kind) {
            case TOGGLE -> {
                c.set(setting.path, !c.getBoolean(setting.path));
                persist();
                open(player);
            }
            case ENUM -> {
                if (!setting.options.isEmpty()) {
                    String current = c.getString(setting.path, setting.options.get(0));
                    int index = Math.max(0, setting.options.indexOf(current));
                    c.set(setting.path, setting.options.get((index + 1) % setting.options.size()));
                    persist();
                }
                open(player);
            }
            case SUBMENU -> setting.submenu.accept(player);
            case INT, DECIMAL -> promptValue(player, setting);
            case TEXT -> {
                if (setting.pickerValues != null) {
                    openValueMethod(player, setting);
                } else {
                    promptValue(player, setting);
                }
            }
        }
    }

    // Small chooser shown before editing a TEXT setting that has a picker:
    // type it, or browse every value in a paginated menu.
    private void openValueMethod(Player player, Setting setting) {
        MenuHolder holder = new MenuHolder((clicker, slot) -> {
            switch (slot) {
                case 11 -> promptValue(clicker, setting);
                case 15 -> {
                    String currentValue = plugin.getSetting().getConfig().getString(setting.path, "");
                    new ValuePickerMenu(plugin, setting.label, setting.pickerValues.get(), setting.pickerIcon,
                            currentValue,
                            value -> {
                                plugin.getSetting().getConfig().set(setting.path, value);
                                persist();
                                MessageUtil.sendMessage(clicker, "config-value-saved",
                                        MessageUtil.placeholder("value", value));
                            },
                            this::open).open(clicker, 0);
                }
                case 22 -> open(clicker);
                default -> { }
            }
        });

        Inventory inventory = Bukkit.createInventory(holder, 27, ColorUtil.translate("<dark_gray>How do you want to set it?"));
        holder.setInventory(inventory);

        ItemStack fill = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, fill);
        }
        inventory.setItem(11, buildItem(plugin.getLogger(), "WRITABLE_BOOK", "<yellow>Type the name",
                List.of("<gray>Enter it in chat yourself"), Map.of()));
        inventory.setItem(15, buildItem(plugin.getLogger(), "CHEST", "<yellow>Browse the list",
                List.of("<gray>Pick from every available value"), Map.of()));
        inventory.setItem(22, buildItem(plugin.getLogger(), "ARROW", "<gray>← Back", List.of(), Map.of()));

        player.openInventory(inventory);
    }

    private void promptValue(Player player, Setting setting) {
        plugin.getManagerHandler().getChatInputManager().request(player, setting.promptKey, Map.of(), input -> {
            String value = input.trim();
            ConfigurationSection c = plugin.getSetting().getConfig();
            boolean ok;

            switch (setting.kind) {
                case INT -> {
                    try {
                        long parsed = Long.parseLong(value);
                        ok = parsed >= setting.min && parsed <= setting.max;
                        if (ok) {
                            c.set(setting.path, (int) parsed);
                        }
                    } catch (NumberFormatException e) {
                        ok = false;
                    }
                }
                case DECIMAL -> {
                    try {
                        double parsed = Double.parseDouble(value);
                        // positive-form range check also rejects NaN
                        ok = parsed >= setting.min && parsed <= setting.max;
                        if (ok) {
                            c.set(setting.path, parsed);
                        }
                    } catch (NumberFormatException e) {
                        ok = false;
                    }
                }
                default -> {
                    ok = setting.textValidator.test(value);
                    if (ok) {
                        c.set(setting.path, value);
                    }
                }
            }

            if (ok) {
                persist();
                MessageUtil.sendMessage(player, "config-value-saved",
                        MessageUtil.placeholder("value", value));
            } else {
                MessageUtil.sendMessage(player, "config-value-invalid");
            }
            open(player);
        });
    }

    private void persist() {
        plugin.getSetting().save();
        if (onChange != null) {
            onChange.run();
        }
    }

    // ---- the two concrete screens ----------------------------------------

    public static SettingsMenu combat(SizeChange plugin) {
        List<Setting> settings = List.of(
                new Setting(10, Kind.TOGGLE, "COMBAT.ENABLED", "<red>Combat tag", "REDSTONE"),
                new Setting(12, Kind.INT, "COMBAT.DURATION", "<yellow>Duration (seconds)", "CLOCK")
                        .range(1, 3600).prompt("config-combat-duration-prompt"),
                new Setting(14, Kind.DECIMAL, "COMBAT.SIZE", "<yellow>Forced size", "SLIME_BALL")
                        .range(0.0625, 16.0).prompt("config-combat-size-prompt"),
                new Setting(19, Kind.TOGGLE, "COMBAT.DISPLAY.ACTIONBAR", "<aqua>Action bar", "OAK_SIGN"),
                new Setting(21, Kind.TOGGLE, "COMBAT.DISPLAY.BOSSBAR", "<aqua>Boss bar", "DRAGON_HEAD"),
                new Setting(23, Kind.TOGGLE, "COMBAT.DISPLAY.CHAT", "<aqua>Chat message", "PAPER"),
                new Setting(29, Kind.ENUM, "COMBAT.BOSSBAR.COLOR", "<light_purple>Boss bar colour", "RED_DYE")
                        .options("PINK", "BLUE", "RED", "GREEN", "YELLOW", "PURPLE", "WHITE"),
                new Setting(31, Kind.ENUM, "COMBAT.BOSSBAR.OVERLAY", "<light_purple>Boss bar overlay", "ITEM_FRAME")
                        .options("PROGRESS", "NOTCHED_6", "NOTCHED_10", "NOTCHED_12", "NOTCHED_20"),
                new Setting(33, Kind.SUBMENU, null, "<gold>Excluded regions", "BARRIER")
                        .submenu(player -> new RegionConfigMenu(plugin,
                                RegionConfigMenu.ConfigType.COMBAT_EXCLUDED,
                                viewer -> combat(plugin).open(viewer)).open(player, 0))
        );

        return new SettingsMenu(plugin, "combat", "<dark_gray>Combat Settings",
                () -> plugin.getManagerHandler().getCombatTagManager().reload(),
                settings, player -> new RegionTypeMenu(plugin).open(player));
    }

    public static SettingsMenu effects(SizeChange plugin) {
        List<Setting> settings = List.of(
                new Setting(10, Kind.TOGGLE, "ANIMATION.ENABLED", "<yellow>Smooth animation", "FEATHER"),
                new Setting(12, Kind.TOGGLE, "EFFECTS.SOUND.ENABLED", "<gold>Sound", "NOTE_BLOCK"),
                new Setting(13, Kind.TEXT, "EFFECTS.SOUND.NAME", "<gold>Sound name", "JUKEBOX")
                        .validator(SettingsMenu::isValidSound).prompt("config-effects-sound-prompt")
                        .picker(SettingsMenu::allSoundNames, "NOTE_BLOCK"),
                new Setting(15, Kind.TOGGLE, "EFFECTS.PARTICLE.ENABLED", "<gold>Particle", "BLAZE_POWDER"),
                new Setting(16, Kind.TEXT, "EFFECTS.PARTICLE.NAME", "<gold>Particle name", "FIREWORK_ROCKET")
                        .validator(SettingsMenu::isValidParticle).prompt("config-effects-particle-prompt")
                        .picker(SettingsMenu::allParticleNames, "FIREWORK_STAR"),
                new Setting(21, Kind.ENUM, "EFFECTS.PARTICLE.STYLE", "<light_purple>Particle animation", "END_CRYSTAL")
                        .options("HELIX", "AURA", "BURST"),
                new Setting(23, Kind.INT, "EFFECTS.PARTICLE.DURATION", "<light_purple>Animation length (ticks)", "CLOCK")
                        .range(10, 200).prompt("config-effects-duration-prompt")
        );

        // SizeAnimator reads these keys live, so nothing to reload.
        return new SettingsMenu(plugin, "effects", "<dark_gray>Effects Settings",
                null, settings, player -> new RegionTypeMenu(plugin).open(player));
    }

    private static boolean isValidSound(String value) {
        try {
            Sound.valueOf(value.trim().toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isValidParticle(String value) {
        try {
            Particle.valueOf(value.trim().toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // Lazily built once — a few thousand names, sorted; menu opens are main-thread.
    private static List<String> soundNames;
    private static List<String> particleNames;

    // Enum-name based to match how SizeAnimator resolves the value
    // (Sound.valueOf / Particle.valueOf) and how it's stored in setting.yml.
    @SuppressWarnings({"deprecation", "removal"})
    private static List<String> allSoundNames() {
        if (soundNames == null) {
            soundNames = Arrays.stream(Sound.values()).map(Sound::name).sorted().toList();
        }
        return soundNames;
    }

    @SuppressWarnings({"deprecation", "removal"})
    private static List<String> allParticleNames() {
        if (particleNames == null) {
            particleNames = Arrays.stream(Particle.values()).map(Particle::name).sorted().toList();
        }
        return particleNames;
    }
}
