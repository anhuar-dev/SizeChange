package dev.anhuar.util;

import dev.anhuar.SizeChange;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageUtil {

    private static YamlConfiguration getMessageConfig() {
        return SizeChange.getInstance().getMessage().getConfig();
    }

    public static void sendMessage(CommandSender sender, String path, Map<String, String> placeholders) {
        if (getMessageConfig().isList(path)) {
            sendList(sender, path, placeholders);
            return;
        }

        String message = getMessage(path);
        if (message.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            message = message.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        Component component = ColorUtil.translate(message);
        sender.sendMessage(component);
    }

    public static void sendMessage(CommandSender sender, String path) {
        sendMessage(sender, path, new HashMap<>());
    }

    public static void sendList(CommandSender sender, String path) {
        sendList(sender, path, new HashMap<>());
    }

    public static void sendList(CommandSender sender, String path, Map<String, String> placeholders) {
        List<String> messages = getMessageConfig().getStringList(path);

        for (String message : messages) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                message = message.replace("{" + entry.getKey() + "}", entry.getValue());
            }

            Component component = ColorUtil.translate(message);
            sender.sendMessage(component);
        }
    }

    public static String getMessage(String path) {
        return getMessageConfig().getString(path, "");
    }

    public static Map<String, String> placeholder(String key, String value) {
        Map<String, String> map = new HashMap<>();
        map.put(key, value);
        return map;
    }

    public static class PlaceholderBuilder {
        private final Map<String, String> placeholders = new HashMap<>();

        public PlaceholderBuilder add(String key, String value) {
            placeholders.put(key, value);
            return this;
        }

        public PlaceholderBuilder add(String key, double value) {
            placeholders.put(key, String.valueOf(value));
            return this;
        }

        public PlaceholderBuilder add(String key, int value) {
            placeholders.put(key, String.valueOf(value));
            return this;
        }

        public Map<String, String> build() {
            return placeholders;
        }
    }
}
