package nl.hauntedmc.craftgpt.generation.compiled;

import nl.hauntedmc.craftgpt.generation.IntVec3;

public record SparseCuboid(ResolvedPaletteEntry paletteEntry, IntVec3 from, IntVec3 to) {
    public long volume() {
        return (long) (to.x() - from.x() + 1)
                * (to.y() - from.y() + 1)
                * (to.z() - from.z() + 1);
    }
}
