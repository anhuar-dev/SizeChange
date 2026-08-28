package dev.anhuar.manager;

import dev.anhuar.SizeChange;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Smoothly interpolates Attribute.SCALE over a short, fixed duration (with an
// ease-out curve — fast start, gentle settle at the end) instead of snapping
// instantly. Deliberately wired ONLY into size changes a player explicitly
// triggers via a /sizechange command (permanent set/reset, temporary set/clear) —
// never into WorldGuard region crossings, automatic temporary-size expiration, or
// the join-time restore, since those can fire far more often (or on a hot path)
// and would turn a rare, bounded cost into a recurring one. Everywhere else keeps
// calling SizeManager.setSize() directly for an instant change. Can be turned off
// entirely via ANIMATION.ENABLED in setting.yml — when disabled, this just applies
// the target size instantly, same as SizeManager.setSize() would. Also plays a
// configurable sound and/or particle on every real change it handles (EFFECTS.*
// in setting.yml — SOUND and PARTICLE each with their own ENABLED/NAME) — same
// scope as the animation itself, so region/world crossings and automatic
// expiration never trigger it either.
public class SizeAnimator {

    private static final int DURATION_TICKS = 16;

    private final SizeChange plugin;
    private final SizeManager sizeManager;
    private final Map<UUID, BukkitTask> activeAnimations = new ConcurrentHashMap<>();

    public SizeAnimator(SizeChange plugin, SizeManager sizeManager) {
        this.plugin = plugin;
        this.sizeManager = sizeManager;
    }

    public void animateTo(Player player, double targetSize) {
        if (player == null || !player.isOnline()) {
            return;
        }

        double start = sizeManager.getSize(player);
        boolean noChange = Math.abs(targetSize - start) < 0.0001;

        if (!noChange) {
            playEffects(player);
        }

        if (noChange || !plugin.getSetting().getConfig().getBoolean("ANIMATION.ENABLED", true)) {
            sizeManager.setSize(player, targetSize);
            return;
        }

        UUID uuid = player.getUniqueId();
        cancel(uuid);

        BukkitTask task = new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    activeAnimations.remove(uuid);
                    cancel();
                    return;
                }

                elapsed++;
                if (elapsed >= DURATION_TICKS) {
                    sizeManager.setSize(player, targetSize);
                    activeAnimations.remove(uuid);
                    cancel();
                    return;
                }

                // Quadratic ease-out: fast at first, settling gently into the
                // target instead of a constant-speed linear ramp.
                double t = (double) elapsed / DURATION_TICKS;
                double eased = 1 - Math.pow(1 - t, 2);
                sizeManager.setSize(player, start + (targetSize - start) * eased);
            }
        }.runTaskTimer(plugin, 0L, 1L);

        activeAnimations.put(uuid, task);
    }

    // IMPORTANT: independent of ANIMATION.ENABLED on purpose — a server might want
    // the instant snap but keep the feedback sound/particle, or the smooth motion
    // without the noise. Sound and particle are also independent of each other,
    // each with its own EFFECTS.<x>.ENABLED toggle. Fires once per real change, at
    // the moment it starts (not once at the start and again at the end — a single
    // cue reads cleaner than an echo 0.8s later).
    private void playEffects(Player player) {
        if (plugin.getSetting().getConfig().getBoolean("EFFECTS.SOUND.ENABLED", true)) {
            String soundName = plugin.getSetting().getString("EFFECTS.SOUND.NAME");
            if (soundName != null && !soundName.isBlank()) {
                try {
                    Sound sound = Sound.valueOf(soundName.trim().toUpperCase());
                    player.getWorld().playSound(player.getLocation(), sound, 1.0f, 1.0f);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid EFFECTS.SOUND.NAME in setting.yml: '" + soundName + "'");
                }
            }
        }

        if (plugin.getSetting().getConfig().getBoolean("EFFECTS.PARTICLE.ENABLED", true)) {
            String particleName = plugin.getSetting().getString("EFFECTS.PARTICLE.NAME");
            if (particleName != null && !particleName.isBlank()) {
                try {
                    Particle particle = Particle.valueOf(particleName.trim().toUpperCase());
                    player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid EFFECTS.PARTICLE.NAME in setting.yml: '" + particleName + "'");
                }
            }
        }
    }

    public void cancel(UUID uuid) {
        BukkitTask task = activeAnimations.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    public void shutdown() {
        activeAnimations.values().forEach(BukkitTask::cancel);
        activeAnimations.clear();
    }
}
