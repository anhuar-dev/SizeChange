package dev.anhuar.menu;

import dev.anhuar.SizeChange;
import dev.anhuar.util.ColorUtil;
import dev.anhuar.util.MessageUtil;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

// Chat-based text input for menus (region names, numeric sizes) — the only way
// to type free-form values into a GUI without a signed-book/anvil hack. Ported
// from the same pattern used in the author's other plugins (elixirmc/Minix,
// elixirmc/EClan ChatInputManager).
public class ChatInputManager implements Listener {

    private final SizeChange plugin;
    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    public ChatInputManager(SizeChange plugin) {
        this.plugin = plugin;
    }

    public void request(Player player, String promptMessageKey, Map<String, String> placeholders, Consumer<String> callback) {
        player.closeInventory();
        MessageUtil.sendMessage(player, promptMessageKey, placeholders);
        player.sendMessage(ColorUtil.translate("<gray>Type <red>cancel</red> to cancel."));
        pending.put(player.getUniqueId(), callback);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Consumer<String> callback = pending.remove(event.getPlayer().getUniqueId());
        if (callback == null) {
            return;
        }

        event.setCancelled(true);
        String input = PlainTextComponentSerializer.plainText().serialize(event.message());

        if (input.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, () ->
                    MessageUtil.sendMessage(event.getPlayer(), "config-input-cancelled"));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> callback.accept(input));
    }
}
