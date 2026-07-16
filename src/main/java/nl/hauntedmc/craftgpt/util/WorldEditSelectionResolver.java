package nl.hauntedmc.craftgpt.util;

import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.entity.Player;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import nl.hauntedmc.craftgpt.CraftGPT;
import org.bukkit.Location;
import org.bukkit.World;

public final class WorldEditSelectionResolver {
    private WorldEditSelectionResolver() {
    }

    public static SelectionResult resolve(org.bukkit.entity.Player bukkitPlayer) {
        if (bukkitPlayer == null || bukkitPlayer.getWorld() == null) {
            CraftGPT.getInstance().getLogger().warning("WorldEdit selection resolution failed because the Bukkit player or world was null.");
            return SelectionResult.error("error.selection");
        }

        Player actor = BukkitAdapter.adapt(bukkitPlayer);
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
        com.sk89q.worldedit.world.World selectionWorld = session.getSelectionWorld();
        World currentWorld = bukkitPlayer.getWorld();

        if (selectionWorld == null) {
            CraftGPT.getInstance().getLogger().warning("WorldEdit selection resolution failed for player '" + bukkitPlayer.getName() + "' because no active selection world exists.");
            return SelectionResult.error("error.selection");
        }

        World bukkitSelectionWorld = BukkitAdapter.adapt(selectionWorld);
        if (bukkitSelectionWorld == null || !currentWorld.equals(bukkitSelectionWorld)) {
            CraftGPT.getInstance().getLogger().warning("WorldEdit selection world mismatch for player '" + bukkitPlayer.getName()
                    + "': current='" + currentWorld.getName() + "', selection='" + (bukkitSelectionWorld == null ? "null" : bukkitSelectionWorld.getName()) + "'.");
            return SelectionResult.error("error.selection.world");
        }

        Region region;
        try {
            region = session.getSelection(selectionWorld);
        } catch (IncompleteRegionException e) {
            CraftGPT.getInstance().getLogger().warning("WorldEdit selection resolution failed for player '" + bukkitPlayer.getName() + "' because the cuboid selection is incomplete.");
            return SelectionResult.error("error.selection");
        }
        if (!(region instanceof CuboidRegion)) {
            CraftGPT.getInstance().getLogger().warning("WorldEdit selection resolution rejected a non-cuboid region for player '"
                    + bukkitPlayer.getName() + "': " + region.getClass().getSimpleName() + ".");
            return SelectionResult.error("error.selection");
        }

        BlockVector3 min = region.getMinimumPoint();
        BlockVector3 max = region.getMaximumPoint();

        return SelectionResult.success(
                new Location(currentWorld, min.x(), min.y(), min.z()),
                new Location(currentWorld, max.x(), max.y(), max.z()));
    }

    public static final class SelectionResult {
        private final boolean success;
        private final String errorKey;
        private final Location pos1;
        private final Location pos2;

        private SelectionResult(boolean success, String errorKey, Location pos1, Location pos2) {
            this.success = success;
            this.errorKey = errorKey;
            this.pos1 = pos1;
            this.pos2 = pos2;
        }

        public static SelectionResult success(Location pos1, Location pos2) {
            return new SelectionResult(true, "", pos1, pos2);
        }

        public static SelectionResult error(String errorKey) {
            return new SelectionResult(false, errorKey, null, null);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getErrorKey() {
            return errorKey;
        }

        public Location getPos1() {
            return pos1;
        }

        public Location getPos2() {
            return pos2;
        }
    }
}
