package nl.hauntedmc.craftgpt.selection;

import nl.hauntedmc.craftgpt.CraftGPT;
import nl.hauntedmc.craftgpt.util.PluginConfig;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.scheduler.BukkitTask;

public class FancySelectionBox {
    private BukkitTask particleTask;
    private final Location pos1;
    private final Location pos2;
    private boolean generationMode;

    public FancySelectionBox(Location pos1, Location pos2) {
        this.pos1 = pos1;
        this.pos2 = pos2;
    }

    public void startDrawingBox(boolean generating) {
        PluginConfig config = CraftGPT.getInstance().getPluginConfig();
        int particlesPerBlock = config.getParticlesPerBlock();
        if (particlesPerBlock <= 0) {
            generationMode = generating;
            return;
        }
        double stepAmount = 1D / particlesPerBlock;

        Color color = generating ? Color.PURPLE : Color.AQUA;
        generationMode = generating;
        Particle.DustOptions dustOptions = new Particle.DustOptions(color, 1.0f);

        double minX = Math.min(pos1.getX(), pos2.getX());
        double maxX = Math.max(pos1.getX(), pos2.getX()) + 1;
        double minY = Math.min(pos1.getY(), pos2.getY());
        double maxY = Math.max(pos1.getY(), pos2.getY()) + 1;
        double minZ = Math.min(pos1.getZ(), pos2.getZ());
        double maxZ = Math.max(pos1.getZ(), pos2.getZ()) + 1;
        long updateTicks = config.getSelectionBoxUpdateTicks();

        particleTask = Bukkit.getScheduler().runTaskTimer(CraftGPT.getInstance(), () -> {
            for (double x = minX; x <= maxX; x += stepAmount) {
                pos1.getWorld().spawnParticle(Particle.DUST, x, minY, minZ, 0, dustOptions);
                pos1.getWorld().spawnParticle(Particle.DUST, x, minY, maxZ, 0, dustOptions);
                pos1.getWorld().spawnParticle(Particle.DUST, x, maxY, minZ, 0, dustOptions);
                pos1.getWorld().spawnParticle(Particle.DUST, x, maxY, maxZ, 0, dustOptions);
            }

            for (double z = minZ; z <= maxZ; z += stepAmount) {
                pos1.getWorld().spawnParticle(Particle.DUST, minX, minY, z, 0, dustOptions);
                pos1.getWorld().spawnParticle(Particle.DUST, maxX, minY, z, 0, dustOptions);
                pos1.getWorld().spawnParticle(Particle.DUST, minX, maxY, z, 0, dustOptions);
                pos1.getWorld().spawnParticle(Particle.DUST, maxX, maxY, z, 0, dustOptions);
            }

            for (double y = minY; y <= maxY; y += stepAmount) {
                pos1.getWorld().spawnParticle(Particle.DUST, minX, y, minZ, 0, dustOptions);
                pos1.getWorld().spawnParticle(Particle.DUST, minX, y, maxZ, 0, dustOptions);
                pos1.getWorld().spawnParticle(Particle.DUST, maxX, y, minZ, 0, dustOptions);
                pos1.getWorld().spawnParticle(Particle.DUST, maxX, y, maxZ, 0, dustOptions);
            }
        }, 0L, updateTicks);
    }

    public void switchToGenerating() {
        stop();
        startDrawingBox(true);
    }

    public void stop() {
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
    }

    public boolean isGenerationMode() {
        return generationMode;
    }
}
