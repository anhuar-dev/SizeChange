package dev.anhuar.manager;

import dev.anhuar.SizeChange;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

// Draws the particle effect that accompanies a command-triggered size change.
//
// IMPORTANT: every point is spawned with count=1 and zero offset/speed
// (spawnParticle(..., 1, 0, 0, 0, 0)). That is the whole difference between a
// shape and a puff: the count+offset form Bukkit defaults to scatters particles
// randomly inside a box and the extra/speed argument launches them, which is why
// the original one-shot BURST reads as "sprayed everywhere". With count=1 and no
// speed, a particle stays exactly where it is placed, so a task placing points
// along a curve over several ticks actually draws that curve.
//
// The animation is a repeating task rather than a single spawnParticle call, is
// keyed per player so it can be cancelled on quit, and re-reads the player's
// location every tick so it follows them if they move.
public class ParticleAnimator {

    public enum Style {
        // Legacy one-shot puff — kept so an existing setting.yml keeps working.
        BURST,
        // Two strands spiralling up around the player, widest at chest height.
        HELIX,
        // Stacked rings rising and pulsing outward, collapsing in at the end.
        AURA
    }

    private static final int DEFAULT_DURATION_TICKS = 50;

    // HELIX: full double spiral redrawn every tick, rotating + pulsing.
    private static final int HELIX_STRANDS = 2;
    private static final int HELIX_POINTS = 34;      // points per strand, bottom -> top
    private static final double HELIX_TURNS = 2.5;
    private static final double HELIX_SPIN_PER_TICK = 0.30;

    // AURA: several full rings redrawn every tick at staggered heights.
    private static final int AURA_RINGS = 4;
    private static final int AURA_RING_POINTS = 22;
    private static final double AURA_SPIN_PER_TICK = 0.16;

    private final SizeChange plugin;
    private final Map<UUID, BukkitTask> active = new ConcurrentHashMap<>();

    public ParticleAnimator(SizeChange plugin) {
        this.plugin = plugin;
    }

    public void play(Player player, Particle particle, double targetSize) {
        Style style = resolveStyle();

        // The particle's own data requirement (DUST needs DustOptions, BLOCK needs
        // BlockData, ...). Resolved once here instead of per point.
        Object data = resolveData(particle);

        if (style == Style.BURST) {
            spawnBurst(player, particle, data);
            return;
        }

        UUID uuid = player.getUniqueId();
        cancel(uuid);

        int duration = Math.max(10, plugin.getSetting().getConfig()
                .getInt("EFFECTS.PARTICLE.DURATION", DEFAULT_DURATION_TICKS));

        // Clamped so a 16x giant doesn't paint a 30-block-tall helix (and a 0.06x
        // player still gets something visible), while still tracking their size.
        double scale = Math.max(0.35, Math.min(4.0, targetSize));

        BukkitTask task = new BukkitRunnable() {
            int elapsed = 0;
            boolean warned = false;

            @Override
            public void run() {
                if (!player.isOnline() || elapsed >= duration) {
                    active.remove(uuid);
                    cancel();
                    return;
                }

                Location base = player.getLocation();
                World world = base.getWorld();
                double progress = elapsed / (double) duration;
                // 0 -> 1 -> 0 over the whole animation: the shape grows out of
                // the player, holds, then draws back in instead of vanishing.
                double envelope = Math.sin(Math.PI * Math.min(1.0, progress));

                try {
                    if (style == Style.HELIX) {
                        drawHelix(world, base, particle, data, scale, envelope, elapsed);
                    } else {
                        drawAura(world, base, particle, data, scale, envelope, progress, elapsed);
                    }
                } catch (IllegalArgumentException e) {
                    // A particle whose data requirement we couldn't satisfy. Warn
                    // once and stop rather than throwing every tick.
                    if (!warned) {
                        warned = true;
                        plugin.getLogger().warning("Particle '" + particle.name()
                                + "' could not be drawn: " + e.getMessage());
                    }
                    active.remove(uuid);
                    cancel();
                    return;
                }

                elapsed++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        active.put(uuid, task);
    }

    // The WHOLE double spiral, redrawn every tick (~68 points), rotating a little
    // each tick and with its radius driven by `envelope` (grows out, holds,
    // draws back in). The per-point sin term pinches the radius at the feet and
    // head so it reads as a cocoon rather than a straight cylinder.
    private void drawHelix(World world, Location base, Particle particle, Object data,
                           double scale, double envelope, int elapsed) {
        if (envelope <= 0.02) {
            return;
        }

        double height = 2.4 * scale;
        double maxRadius = 0.85 * scale;
        double spin = elapsed * HELIX_SPIN_PER_TICK;

        for (int i = 0; i < HELIX_POINTS; i++) {
            double f = i / (double) (HELIX_POINTS - 1);
            double y = f * height;
            double radius = maxRadius * envelope * (0.25 + 0.75 * Math.sin(Math.PI * f));
            double angle = spin + f * HELIX_TURNS * 2 * Math.PI;

            for (int strand = 0; strand < HELIX_STRANDS; strand++) {
                double a = angle + strand * (2 * Math.PI / HELIX_STRANDS);
                spawn(world, particle, data,
                        base.getX() + Math.cos(a) * radius,
                        base.getY() + y + 0.1,
                        base.getZ() + Math.sin(a) * radius);
            }
        }
    }

    // Several full rings, redrawn every tick (~88 points) at staggered heights.
    // Each ring loops feet -> head on its own phase, widest at chest height; the
    // global `envelope` scales every radius so the whole aura blooms and closes.
    private void drawAura(World world, Location base, Particle particle, Object data,
                          double scale, double envelope, double progress, int elapsed) {
        if (envelope <= 0.02) {
            return;
        }

        double height = 2.4 * scale;
        double maxRadius = 1.1 * scale;

        for (int ring = 0; ring < AURA_RINGS; ring++) {
            double phase = (progress * 1.3 + ring / (double) AURA_RINGS) % 1.0;
            double radius = maxRadius * envelope * Math.sin(Math.PI * phase);
            if (radius <= 0.03) {
                continue;
            }

            double y = phase * height;
            double spin = elapsed * AURA_SPIN_PER_TICK + ring * 0.7;

            for (int i = 0; i < AURA_RING_POINTS; i++) {
                double a = (i / (double) AURA_RING_POINTS) * 2 * Math.PI + spin;
                spawn(world, particle, data,
                        base.getX() + Math.cos(a) * radius,
                        base.getY() + y + 0.1,
                        base.getZ() + Math.sin(a) * radius);
            }
        }
    }

    private void spawnBurst(Player player, Particle particle, Object data) {
        Location loc = player.getLocation().add(0, 1, 0);
        if (data == null) {
            player.getWorld().spawnParticle(particle, loc, 15, 0.3, 0.5, 0.3);
        } else {
            player.getWorld().spawnParticle(particle, loc, 15, 0.3, 0.5, 0.3, 0, data);
        }
    }

    private void spawn(World world, Particle particle, Object data, double x, double y, double z) {
        if (data == null) {
            world.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0);
        } else {
            world.spawnParticle(particle, x, y, z, 1, 0, 0, 0, 0, data);
        }
    }

    // Some particles refuse to spawn without an extra data object. Supplying a
    // sensible default keeps every entry in the picker menu usable instead of
    // silently drawing nothing for DUST and friends.
    private Object resolveData(Particle particle) {
        Class<?> type = particle.getDataType();
        if (type == Void.class) {
            return null;
        }
        if (type == Particle.DustOptions.class) {
            return new Particle.DustOptions(Color.fromRGB(0x7FD8FF), 1.0f);
        }
        if (type == Particle.DustTransition.class) {
            return new Particle.DustTransition(Color.fromRGB(0x7FD8FF), Color.WHITE, 1.0f);
        }
        if (org.bukkit.block.data.BlockData.class.isAssignableFrom(type)) {
            return Material.WHITE_CONCRETE.createBlockData();
        }
        if (type == ItemStack.class) {
            return new ItemStack(Material.SNOWBALL);
        }
        return null;
    }

    private Style resolveStyle() {
        String raw = plugin.getSetting().getString("EFFECTS.PARTICLE.STYLE");
        if (raw == null || raw.isBlank()) {
            return Style.HELIX;
        }
        try {
            return Style.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid EFFECTS.PARTICLE.STYLE in setting.yml: '"
                    + raw + "', using HELIX");
            return Style.HELIX;
        }
    }

    public void cancel(UUID uuid) {
        BukkitTask task = active.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    public void shutdown() {
        active.values().forEach(BukkitTask::cancel);
        active.clear();
    }
}
