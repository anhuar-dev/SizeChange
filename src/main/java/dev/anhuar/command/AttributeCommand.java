package dev.anhuar.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.anhuar.SizeChange;
import dev.anhuar.manager.AttributeModifierManager;
import dev.anhuar.model.AttributeModifierData;
import dev.anhuar.util.AttributeValueParser;
import dev.anhuar.util.DurationUtil;
import dev.anhuar.util.MessageUtil;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

// The /sizechange attribute ... branch. SizeCommand.register() attaches node()
// onto the root command. Structurally mirrors the temporary-size subcommands:
// a permanent and a temporary variant of set/clear, plus extend, clearall and
// view. Every executor first checks the feature is enabled (ATTRIBUTES.ENABLED)
// and refuses with "attribute-disabled" otherwise.
public class AttributeCommand {

    private static final String[] ALL_PERMS = {
            "sizechange.attribute.permanent.set",
            "sizechange.attribute.permanent.clear",
            "sizechange.attribute.temporary.set",
            "sizechange.attribute.temporary.clear",
            "sizechange.attribute.temporary.extend",
            "sizechange.attribute.view"
    };

    private final SizeChange plugin;

    private final SuggestionProvider<CommandSourceStack> playerSuggestions = (context, builder) -> {
        Bukkit.getOnlinePlayers().forEach(p -> builder.suggest(p.getName()));
        return builder.buildFuture();
    };

    private final SuggestionProvider<CommandSourceStack> attributeSuggestions = (context, builder) -> {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String name : manager().allowedShortNames()) {
            if (name.startsWith(remaining)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    };

    private final SuggestionProvider<CommandSourceStack> amountSuggestions = (context, builder) -> {
        builder.suggest("4");
        builder.suggest("-2");
        builder.suggest("20%");
        builder.suggest("1.5x");
        return builder.buildFuture();
    };

    private final SuggestionProvider<CommandSourceStack> durationSuggestions = (context, builder) -> {
        builder.suggest("30s");
        builder.suggest("1m");
        builder.suggest("5m");
        builder.suggest("10m");
        builder.suggest("1h");
        return builder.buildFuture();
    };

    public AttributeCommand(SizeChange plugin) {
        this.plugin = plugin;
    }

    public LiteralArgumentBuilder<CommandSourceStack> node() {
        return literal("attribute")
                .requires(source -> {
                    CommandSender sender = source.getSender();
                    for (String perm : ALL_PERMS) {
                        if (sender.hasPermission(perm)) {
                            return true;
                        }
                    }
                    return false;
                })
                .executes(this::executeUsage)
                .then(literal("permanent")
                        .then(literal("set")
                                .requires(perm("sizechange.attribute.permanent.set"))
                                .then(argument("player", StringArgumentType.word()).suggests(playerSuggestions)
                                        .then(argument("attribute", StringArgumentType.word()).suggests(attributeSuggestions)
                                                .then(argument("amount", StringArgumentType.word()).suggests(amountSuggestions)
                                                        .executes(context -> executeSet(context, null))))))
                        .then(literal("clear")
                                .requires(perm("sizechange.attribute.permanent.clear"))
                                .then(argument("player", StringArgumentType.word()).suggests(playerSuggestions)
                                        .then(argument("attribute", StringArgumentType.word()).suggests(attributeSuggestions)
                                                .executes(context -> executeClear(context, false))))))
                .then(literal("temporary")
                        .then(literal("set")
                                .requires(perm("sizechange.attribute.temporary.set"))
                                .then(argument("player", StringArgumentType.word()).suggests(playerSuggestions)
                                        .then(argument("attribute", StringArgumentType.word()).suggests(attributeSuggestions)
                                                .then(argument("amount", StringArgumentType.word()).suggests(amountSuggestions)
                                                        .then(argument("duration", StringArgumentType.word()).suggests(durationSuggestions)
                                                                .executes(context -> executeSet(context, requireDuration(context))))))))
                        .then(literal("clear")
                                .requires(perm("sizechange.attribute.temporary.clear"))
                                .then(argument("player", StringArgumentType.word()).suggests(playerSuggestions)
                                        .then(argument("attribute", StringArgumentType.word()).suggests(attributeSuggestions)
                                                .executes(context -> executeClear(context, true)))))
                        .then(literal("extend")
                                .requires(perm("sizechange.attribute.temporary.extend"))
                                .then(argument("player", StringArgumentType.word()).suggests(playerSuggestions)
                                        .then(argument("attribute", StringArgumentType.word()).suggests(attributeSuggestions)
                                                .then(argument("duration", StringArgumentType.word()).suggests(durationSuggestions)
                                                        .executes(this::executeExtend))))))
                .then(literal("clearall")
                        .requires(perm("sizechange.attribute.permanent.clear"))
                        .then(argument("player", StringArgumentType.word()).suggests(playerSuggestions)
                                .executes(this::executeClearAll)))
                .then(literal("view")
                        .requires(perm("sizechange.attribute.view"))
                        .then(argument("player", StringArgumentType.word()).suggests(playerSuggestions)
                                .executes(this::executeView)));
    }

    // ---- executors ------------------------------------------------------

    private int executeUsage(CommandContext<CommandSourceStack> context) {
        MessageUtil.sendMessage(context.getSource().getSender(), "attribute-help");
        return 1;
    }

    // durationMillis == null -> permanent; a positive value -> temporary; a
    // non-positive value means the duration argument was present but invalid.
    private int executeSet(CommandContext<CommandSourceStack> context, Long durationMillis) {
        CommandSender sender = context.getSource().getSender();
        if (!checkEnabled(sender)) {
            return 0;
        }
        if (durationMillis != null && durationMillis <= 0) {
            MessageUtil.sendMessage(sender, "invalid-duration");
            return 0;
        }

        Player target = resolveTarget(context, sender);
        if (target == null) {
            return 0;
        }
        Attribute attribute = resolveAttribute(context, sender);
        if (attribute == null) {
            return 0;
        }

        AttributeValueParser.Result parsed = AttributeValueParser.parse(context.getArgument("amount", String.class));
        if (parsed == null) {
            MessageUtil.sendMessage(sender, "attribute-amount-invalid");
            return 0;
        }

        double amount = manager().clampAmount(parsed.amount());
        manager().setModifier(target, attribute, amount, parsed.operation(), durationMillis);

        String shortName = manager().shortName(attribute.getKey().toString());
        String display = AttributeValueParser.format(amount, parsed.operation());
        boolean temporary = durationMillis != null;

        if (temporary) {
            String duration = context.getArgument("duration", String.class);
            MessageUtil.sendMessage(sender, "attribute-temp-set", new MessageUtil.PlaceholderBuilder()
                    .add("player", target.getName())
                    .add("attribute", shortName)
                    .add("amount", display)
                    .add("duration", duration)
                    .build());
            if (!sender.equals(target)) {
                MessageUtil.sendMessage(target, "attribute-applied-target", new MessageUtil.PlaceholderBuilder()
                        .add("attribute", shortName)
                        .add("amount", display)
                        .add("duration_suffix", " (" + duration + ")")
                        .build());
            }
        } else {
            MessageUtil.sendMessage(sender, "attribute-set", new MessageUtil.PlaceholderBuilder()
                    .add("player", target.getName())
                    .add("attribute", shortName)
                    .add("amount", display)
                    .add("operation", parsed.operation())
                    .build());
            if (!sender.equals(target)) {
                MessageUtil.sendMessage(target, "attribute-applied-target", new MessageUtil.PlaceholderBuilder()
                        .add("attribute", shortName)
                        .add("amount", display)
                        .add("duration_suffix", "")
                        .build());
            }
        }
        return 1;
    }

    private int executeClear(CommandContext<CommandSourceStack> context, boolean temporary) {
        CommandSender sender = context.getSource().getSender();
        if (!checkEnabled(sender)) {
            return 0;
        }
        Player target = resolveTarget(context, sender);
        if (target == null) {
            return 0;
        }
        Attribute attribute = resolveAttribute(context, sender);
        if (attribute == null) {
            return 0;
        }

        String shortName = manager().shortName(attribute.getKey().toString());
        if (!manager().clearModifier(target, attribute, temporary)) {
            MessageUtil.sendMessage(sender, "attribute-none", new MessageUtil.PlaceholderBuilder()
                    .add("player", target.getName())
                    .add("attribute", shortName)
                    .build());
            return 0;
        }

        MessageUtil.sendMessage(sender, "attribute-cleared", new MessageUtil.PlaceholderBuilder()
                .add("player", target.getName())
                .add("attribute", shortName)
                .build());
        return 1;
    }

    private int executeClearAll(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!checkEnabled(sender)) {
            return 0;
        }
        Player target = resolveTarget(context, sender);
        if (target == null) {
            return 0;
        }

        int removed = manager().clearAll(target);
        MessageUtil.sendMessage(sender, "attribute-clearall", new MessageUtil.PlaceholderBuilder()
                .add("player", target.getName())
                .add("count", removed)
                .build());
        return 1;
    }

    private int executeExtend(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!checkEnabled(sender)) {
            return 0;
        }
        Player target = resolveTarget(context, sender);
        if (target == null) {
            return 0;
        }
        Attribute attribute = resolveAttribute(context, sender);
        if (attribute == null) {
            return 0;
        }

        String duration = context.getArgument("duration", String.class);
        long durationMillis = DurationUtil.parse(duration);
        if (durationMillis <= 0) {
            MessageUtil.sendMessage(sender, "invalid-duration");
            return 0;
        }

        if (!manager().extendTemporary(target, attribute, durationMillis)) {
            MessageUtil.sendMessage(sender, "attribute-none", new MessageUtil.PlaceholderBuilder()
                    .add("player", target.getName())
                    .add("attribute", manager().shortName(attribute.getKey().toString()))
                    .build());
            return 0;
        }

        String remaining = remainingOf(target, attribute);
        MessageUtil.sendMessage(sender, "attribute-temp-extended", new MessageUtil.PlaceholderBuilder()
                .add("player", target.getName())
                .add("attribute", manager().shortName(attribute.getKey().toString()))
                .add("duration", duration)
                .add("remaining", remaining)
                .build());
        return 1;
    }

    private int executeView(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        if (!checkEnabled(sender)) {
            return 0;
        }
        Player target = resolveTarget(context, sender);
        if (target == null) {
            return 0;
        }

        List<AttributeModifierData> modifiers = manager().modifiersOf(target.getUniqueId());
        MessageUtil.sendMessage(sender, "attribute-view-header",
                MessageUtil.placeholder("player", target.getName()));

        if (modifiers.isEmpty()) {
            MessageUtil.sendMessage(sender, "attribute-view-empty");
            return 1;
        }

        for (AttributeModifierData data : modifiers) {
            String remaining = data.isTemporary() ? " · " + DurationUtil.format(data.getRemainingTime()) : "";
            MessageUtil.sendMessage(sender, "attribute-view-line", new MessageUtil.PlaceholderBuilder()
                    .add("attribute", manager().shortName(data.getAttribute()))
                    .add("amount", AttributeValueParser.format(data.getAmount(), data.getOperation()))
                    .add("operation", data.isTemporary() ? "temp" : "perm")
                    .add("remaining", remaining)
                    .build());
        }
        return 1;
    }

    // ---- helpers -------------------------------------------------------

    private AttributeModifierManager manager() {
        return plugin.getManagerHandler().getAttributeModifierManager();
    }

    private static Predicate<CommandSourceStack> perm(String permission) {
        return source -> source.getSender().hasPermission(permission);
    }

    private boolean checkEnabled(CommandSender sender) {
        if (!manager().isEnabled()) {
            MessageUtil.sendMessage(sender, "attribute-disabled");
            return false;
        }
        return true;
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

    private Attribute resolveAttribute(CommandContext<CommandSourceStack> context, CommandSender sender) {
        String raw = context.getArgument("attribute", String.class);
        Attribute attribute = manager().resolveAttribute(raw);
        if (attribute == null) {
            MessageUtil.sendMessage(sender, "attribute-invalid",
                    MessageUtil.placeholder("attribute", raw));
            return null;
        }
        if (!manager().isAllowed(attribute)) {
            MessageUtil.sendMessage(sender, "attribute-not-allowed",
                    MessageUtil.placeholder("attribute", manager().shortName(attribute.getKey().toString())));
            return null;
        }
        return attribute;
    }

    // Parses the "duration" argument for the temporary set path. Returns a
    // positive millis value, or 0 when the token is invalid (executeSet turns
    // that into the "invalid-duration" message).
    private Long requireDuration(CommandContext<CommandSourceStack> context) {
        long millis = DurationUtil.parse(context.getArgument("duration", String.class));
        return millis <= 0 ? 0L : millis;
    }

    private String remainingOf(Player target, Attribute attribute) {
        String key = attribute.getKey().toString();
        for (AttributeModifierData data : manager().modifiersOf(target.getUniqueId())) {
            if (data.getAttribute().equals(key) && data.isTemporary()) {
                return DurationUtil.format(data.getRemainingTime());
            }
        }
        return "-";
    }
}
