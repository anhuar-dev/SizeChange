package dev.anhuar.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class MenuHolder implements InventoryHolder {

    @FunctionalInterface
    public interface ClickHandler {
        void handle(Player player, int slot);
    }

    private final ClickHandler handler;
    private Inventory inventory;

    public MenuHolder(ClickHandler handler) {
        this.handler = handler;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public ClickHandler getHandler() {
        return handler;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    // IMPORTANT: shared by every menu.yml-driven GUI (PlayerListMenu, RegionTypeMenu,
    // RegionConfigMenu). Every click inside a MenuHolder's own inventory is cancelled
    // unconditionally — nothing can ever be picked up, moved, or dropped from these
    // synthetic inventories — and only whichever slots that menu's ClickHandler
    // recognizes actually do something.
    public static class MenuListener implements Listener {

        @EventHandler
        public void onClick(InventoryClickEvent event) {
            if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) {
                return;
            }
            if (!(event.getWhoClicked() instanceof Player player)) {
                return;
            }

            event.setCancelled(true);

            if (event.getClickedInventory() == null) {
                return;
            }
            if (!event.getClickedInventory().equals(event.getView().getTopInventory())) {
                return;
            }

            holder.getHandler().handle(player, event.getSlot());
        }

        @EventHandler
        public void onDrag(InventoryDragEvent event) {
            if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) {
                event.setCancelled(true);
            }
        }
    }
}
