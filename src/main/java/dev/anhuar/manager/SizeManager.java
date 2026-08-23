package dev.anhuar.manager;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

public class SizeManager {

    public void setSize(Player player, double size) {
        if (player == null || !player.isOnline()) {
            return;
        }

        size = Math.max(0.0625, Math.min(16.0, size));

        AttributeInstance attribute = player.getAttribute(Attribute.SCALE);
        if (attribute != null) {
            attribute.setBaseValue(size);
        }
    }

    public double getSize(Player player) {
        if (player == null || !player.isOnline()) {
            return 1.0;
        }

        AttributeInstance attribute = player.getAttribute(Attribute.SCALE);
        if (attribute != null) {
            return attribute.getBaseValue();
        }
        return 1.0;
    }
}
