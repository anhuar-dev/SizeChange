package dev.anhuar.listener;

import dev.anhuar.SizeChange;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

// Turns real player-vs-player damage into a combat tag. MONITOR + ignoreCancelled
// so we only react to a hit that actually landed — if WorldGuard's PvP flag,
// another protection plugin, or a totem/absorb cancelled it, no tag is applied.
public class CombatListener implements Listener {

    private final SizeChange plugin;

    public CombatListener(SizeChange plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        plugin.getManagerHandler().getCombatTagManager().onPvpHit(attacker, victim);
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
