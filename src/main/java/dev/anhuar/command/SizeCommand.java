package dev.anhuar.command;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.anhuar.SizeChange;
import dev.anhuar.handler.DenyRegionHandler;
import dev.anhuar.manager.SizeManager;
import dev.anhuar.menu.PlayerListMenu;
import dev.anhuar.menu.RegionTypeMenu;
import dev.anhuar.util.DurationUtil;
import dev.anhuar.util.MessageUtil;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

public class SizeCommand {

    private static final SuggestionProvider<CommandSourceStack> PLAYER_SUGGESTIONS = (context, builder) -> {
        Bukkit.getOnlinePlayers().forEach(p -> builder.suggest(p.getName()));
        return builder.buildFuture();
    };

    private final SizeChange plugin;

    public SizeCommand(SizeChange plugin) {
        this.plugin = plugin;
    }

    public void register() {
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            Commands commands = event.registrar();

            commands.register(
                    literal("sizechange")
                            .requires(source -> source.getSender().hasPermission("sizechange.help"))
                            .executes(this::executeHelp)
                            .then(
                                    literal("permanent")
                                            .then(
                                                    literal("set")
                                                            .requires(source -> source.getSender().hasPermission("sizechange.permanent.set"))
                                                            .then(
                                                                    argument("player", StringArgumentType.word())
                                                                            .suggests(PLAYER_SUGGESTIONS)
                                                                            .then(
                                                                                    argument("size", DoubleArgumentType.doubleArg(0.0625, 16.0))
                                                                                            .executes(this::executePermanentSet)
                                                                            )
                                                            )
                                            )
                                            .then(
                                                    literal("reset")
                                                            .requires(source -> source.getSender().hasPermission("sizechange.permanent.reset"))
                                                            .then(
                                                                    argument("player", StringArgumentType.word())
                                                                            .suggests(PLAYER_SUGGESTIONS)
                                                                            .executes(this::executePermanentReset)
                                                            )
                                            )
                            )
                            .then(
                                    literal("temporary")
                                            .then(
                                                    literal("set")
                                                            .requires(source -> source.getSender().hasPermission("sizechange.temporary.set"))
                                                            .then(
                                                                    argument("player", StringArgumentType.word())
                                                                            .suggests(PLAYER_SUGGESTIONS)
                                                                            .then(
                                                                                    argument("size", DoubleArgumentType.doubleArg(0.0625, 16.0))
                                                                                            .then(
                                                                                                    argument("duration", StringArgumentType.word())
                                                                                                            .suggests((context, builder) -> {
                                                                                                                builder.suggest("30s");
                                                                                                                builder.suggest("1m");
                                                                                                                builder.suggest("5m");
                                                                                                                builder.suggest("10m");
                                                                                                                builder.suggest("1h");
                                                                                                                return builder.buildFuture();
                                                                                                            })
                                                                                                            .executes(this::executeTemporarySet)
                                                                                            )
                                                                            )
                                                            )
                                            )
                                            .then(
                                                    literal("clear")
                                                            .requires(source -> source.getSender().hasPermission("sizechange.temporary.clear"))
                                                            .then(
                                                                    argument("player", StringArgumentType.word())
                                                                            .suggests(PLAYER_SUGGESTIONS)
                                                                            .executes(this::executeTemporaryClear)
                                                            )
                                            )
                            )
                            .then(
                                    literal("view")
                                            .requires(source -> source.getSender().hasPermission("sizechange.view"))
                                            .then(
                                                    argument("player", StringArgumentType.word())
                                                            .suggests(PLAYER_SUGGESTIONS)
                                                            .executes(this::executeView)
                                            )
                            )
                            .then(
                                    literal("list")
                                            .requires(source -> source.getSender().hasPermission("sizechange.list"))
                                            .executes(this::executeList)
                            )
                            .then(
                                    literal("info")
                                            .requires(source -> source.getSender().hasPermission("sizechange.info"))
                                            .executes(this::executeInfo)
                            )
                            .then(
                                    literal("config")
                                            .requires(source -> source.getSender().hasPermission("sizechange.config"))
                                            .executes(this::executeConfig)
                            )
                            .then(
                                    literal("reload")
                                            .requires(source -> source.getSender().hasPermission("sizechange.reload"))
                                            .executes(this::executeReload)
                            )
                            .build(),
                    "Comando principal del plugin Size"
            );
        });
    }

    private int executeHelp(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        MessageUtil.sendMessage(sender, "help");
        return 1;
    }

    private Player resolveTarget(CommandContext<CommandSourceStack> context, CommandSender sender) {
        String targetName = context.getArgument("player", String.class);
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            MessageUtil.sendMessage(sender, "player-not-found",
                    MessageUtil.placeholder("player", targetName));
        }
        return target;
    }

    private int executePermanentSet(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        double size = context.getArgument("size", Double.class);

        Player target = resolveTarget(context, sender);
        if (target == null) {
            return 0;
        }

        UUID targetId = target.getUniqueId();
        plugin.getManagerHandler().getTemporarySizeManager().cancelTemporarySize(targetId);

        // IMPORTANT: while the player is under a region's visual override, the
        // region must keep controlling what's shown — the new permanent size is
        // still recorded (setSize below persists it, which also keeps
        // PlayerDataManager's dirty-tracking cache current), it just doesn't become
        // visible until they leave.
        if (!DenyRegionHandler.hasVisualOverride(targetId)) {
            plugin.getManagerHandler().getSizeAnimator().animateTo(target, size);
        }
        plugin.getManagerHandler().getPlayerDataManager().setSize(targetId, size);

        MessageUtil.sendMessage(sender, "size-set",
                new MessageUtil.PlaceholderBuilder()
                        .add("player", target.getName())
                        .add("size", size)
                        .build());

        if (!sender.equals(target)) {
            MessageUtil.sendMessage(target, "size-changed",
                    MessageUtil.placeholder("size", String.valueOf(size)));
        }

        return 1;
    }

    private int executePermanentReset(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();

        Player target = resolveTarget(context, sender);
        if (target == null) {
            return 0;
        }

        UUID targetId = target.getUniqueId();
        plugin.getManagerHandler().getTemporarySizeManager().cancelTemporarySize(targetId);

        if (!DenyRegionHandler.hasVisualOverride(targetId)) {
            plugin.getManagerHandler().getSizeAnimator().animateTo(target, 1.0);
        }
        plugin.getManagerHandler().getPlayerDataManager().setSize(targetId, 1.0);

        MessageUtil.sendMessage(sender, "size-reset",
                MessageUtil.placeholder("player", target.getName()));

        if (!sender.equals(target)) {
            MessageUtil.sendMessage(target, "size-changed",
                    MessageUtil.placeholder("size", "1.0"));
        }

        return 1;
    }

    private int executeView(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();

        Player target = resolveTarget(context, sender);
        if (target == null) {
            return 0;
        }

        plugin.getManagerHandler().getPlayerDataManager()
                .getDatabase()
                .loadPlayer(target.getUniqueId())
                .thenAccept(data -> {
                    double currentSize = data != null ? data.getSize() : 1.0;

                    var tempManager = plugin.getManagerHandler().getTemporarySizeManager();
                    if (tempManager.hasTemporarySize(target.getUniqueId())) {
                        var tempData = tempManager.getTemporarySize(target.getUniqueId());
                        String remainingTime = DurationUtil.format(tempData.getRemainingTime());

                        MessageUtil.sendMessage(sender, "size-view-temp",
                                new MessageUtil.PlaceholderBuilder()
                                        .add("player", target.getName())
                                        .add("size", currentSize)
                                        .add("temp_size", tempData.getTemporarySize())
                                        .add("remaining", remainingTime)
                                        .build());
                    } else {
                        MessageUtil.sendMessage(sender, "size-view",
                                new MessageUtil.PlaceholderBuilder()
                                        .add("player", target.getName())
                                        .add("size", currentSize)
                                        .build());
                    }
                })
                .exceptionally(throwable -> {
                    MessageUtil.sendMessage(sender, "error-loading-data");
                    plugin.getLogger().warning("Error loading data for " + target.getName() + ": " + throwable.getMessage());
                    return null;
                });

        return 1;
    }

    private int executeTemporarySet(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        double size = context.getArgument("size", Double.class);
        String duration = context.getArgument("duration", String.class);

        Player target = resolveTarget(context, sender);
        if (target == null) {
            return 0;
        }

        long durationMs = DurationUtil.parse(duration);
        if (durationMs <= 0) {
            MessageUtil.sendMessage(sender, "invalid-duration");
            return 0;
        }

        // IMPORTANT: resolve the true original size explicitly instead of letting
        // setTemporarySize() read the player's live visual size. If the target is
        // currently inside a WorldGuard region override, their visual size IS the
        // region's value, not their real permanent size — using it as "original"
        // would corrupt the temp's restore target with the region's size.
        UUID targetId = target.getUniqueId();
        if (DenyRegionHandler.hasVisualOverride(targetId)) {
            SizeManager sizeManager = plugin.getManagerHandler().getSizeManager();
            double trueOriginal = plugin.getManagerHandler().getPlayerDataManager()
                    .getLastKnownPermanent(targetId, sizeManager.getSize(target));
            plugin.getManagerHandler().getTemporarySizeManager()
                    .setTemporarySize(target, size, trueOriginal, durationMs, true);
        } else {
            plugin.getManagerHandler().getTemporarySizeManager().setTemporarySize(target, size, durationMs, true);
        }

        MessageUtil.sendMessage(sender, "temp-size-set",
                new MessageUtil.PlaceholderBuilder()
                        .add("player", target.getName())
                        .add("size", size)
                        .add("duration", duration)
                        .build());

        if (!sender.equals(target)) {
            MessageUtil.sendMessage(target, "temp-size-applied",
                    new MessageUtil.PlaceholderBuilder()
                            .add("size", size)
                            .add("duration", duration)
                            .build());
        }

        return 1;
    }

    private int executeTemporaryClear(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();

        Player target = resolveTarget(context, sender);
        if (target == null) {
            return 0;
        }

        plugin.getManagerHandler().getTemporarySizeManager().clearTemporarySize(target.getUniqueId());

        MessageUtil.sendMessage(sender, "temp-size-cleared", MessageUtil.placeholder("player", target.getName()));

        return 1;
    }

    private int executeList(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();

        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, "gui-console-only");
            return 0;
        }

        new PlayerListMenu(plugin).open(player, 0);
        return 1;
    }

    private int executeConfig(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();

        if (!(sender instanceof Player player)) {
            MessageUtil.sendMessage(sender, "gui-console-only");
            return 0;
        }

        new RegionTypeMenu(plugin).open(player);
        return 1;
    }

    private int executeInfo(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();

        String version = plugin.getPluginMeta().getVersion();
        String database = plugin.getSetting().getString("DATABASE.TYPE");
        int activeTemp = plugin.getManagerHandler().getTemporarySizeManager().activeCount();

        int denyRegions = plugin.getManagerHandler().getRegionManager().getDenyRegions().size();
        int sizeRegions = 0;
        var sizeRegionSection = plugin.getSetting().getConfig().getConfigurationSection("SIZE-REGION");
        if (sizeRegionSection != null) {
            sizeRegions = sizeRegionSection.getKeys(false).size();
        }

        var worldManager = plugin.getManagerHandler().getWorldManager();
        int worlds = worldManager.getDenyWorlds().size() + worldManager.getSizeWorlds().size();

        MessageUtil.sendMessage(sender, "info",
                new MessageUtil.PlaceholderBuilder()
                        .add("version", version)
                        .add("database", database)
                        .add("active_temp", activeTemp)
                        .add("regions", denyRegions + sizeRegions)
                        .add("worlds", worlds)
                        .build());

        return 1;
    }

    private int executeReload(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();

        plugin.getSetting().reload();
        plugin.getMessage().reload();
        plugin.getMenu().reload();

        plugin.getManagerHandler().getRegionManager().reloadDenyRegions();
        plugin.getManagerHandler().getWorldManager().reload();
        DenyRegionHandler.reloadSizeRegions();

        MessageUtil.sendMessage(sender, "reload");

        return 1;
    }

}
