package nl.hauntedmc.craftgpt.generation.worldedit;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.WorldEditException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.world.block.BlockState;
import nl.hauntedmc.craftgpt.CraftGPT;
import nl.hauntedmc.craftgpt.generation.IntVec3;
import nl.hauntedmc.craftgpt.generation.compiled.ResolvedPaletteEntry;
import nl.hauntedmc.craftgpt.generation.compiled.SparseCuboid;
import nl.hauntedmc.craftgpt.util.PluginConfig;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.function.Consumer;

public final class WorldEditBuildApplicationService {
    public BukkitTask apply(Player player, World world, IntVec3 selectionMinWorld, List<SparseCuboid> cuboids,
                            PluginConfig config, Runnable onSuccess, Consumer<String> onFailure) {
        if (config.getBuildDelayTicks() <= 0) {
            return Bukkit.getScheduler().runTask(CraftGPT.getInstance(), () -> {
                if (applyBatch(world, selectionMinWorld, cuboids, 0, cuboids.size(), onFailure)) {
                    onSuccess.run();
                }
            });
        }

        return new BukkitRunnable() {
            private int index;

            @Override
            public void run() {
                if (index >= cuboids.size()) {
                    cancel();
                    onSuccess.run();
                    return;
                }
                int end = Math.min(index + config.getWorldEditBatchSize(), cuboids.size());
                if (!applyBatch(world, selectionMinWorld, cuboids, index, end, onFailure)) {
                    cancel();
                    return;
                }
                index = end;
                if (index >= cuboids.size()) {
                    cancel();
                    onSuccess.run();
                }
            }
        }.runTaskTimer(CraftGPT.getInstance(), 0L, config.getBuildDelayTicks());
    }

    private boolean applyBatch(World world, IntVec3 selectionMinWorld, List<SparseCuboid> cuboids, int start, int end,
                               Consumer<String> onFailure) {
        try {
            int batchBlocks = 0;
            for (int i = start; i < end; i++) {
                long volume = cuboids.get(i).volume();
                if (volume > Integer.MAX_VALUE) {
                    onFailure.accept("Batch volume exceeded integer limits.");
                    return false;
                }
                batchBlocks = Math.addExact(batchBlocks, (int) volume);
            }

            try (EditSession editSession = WorldEdit.getInstance().newEditSessionBuilder()
                    .world(BukkitAdapter.adapt(world))
                    .maxBlocks(batchBlocks)
                    .build()) {
                for (int i = start; i < end; i++) {
                    SparseCuboid cuboid = cuboids.get(i);
                    BlockState blockState = (BlockState) cuboid.paletteEntry().block().platformState();
                    IntVec3 worldFrom = selectionMinWorld.add(cuboid.from());
                    IntVec3 worldTo = selectionMinWorld.add(cuboid.to());
                    if (worldFrom.equals(worldTo)) {
                        editSession.setBlock(BlockVector3.at(worldFrom.x(), worldFrom.y(), worldFrom.z()), blockState);
                        continue;
                    }
                    editSession.setBlocks(
                            new CuboidRegion(
                                    BlockVector3.at(worldFrom.x(), worldFrom.y(), worldFrom.z()),
                                    BlockVector3.at(worldTo.x(), worldTo.y(), worldTo.z())
                            ),
                            blockState
                    );
                }
                return true;
            }
        } catch (ArithmeticException e) {
            onFailure.accept("Numeric overflow while translating local build coordinates into world coordinates.");
            return false;
        } catch (WorldEditException | ClassCastException e) {
            onFailure.accept(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            return false;
        }
    }
}
