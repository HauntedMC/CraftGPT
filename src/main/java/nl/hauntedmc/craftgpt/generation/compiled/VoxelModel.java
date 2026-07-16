package nl.hauntedmc.craftgpt.generation.compiled;

import nl.hauntedmc.craftgpt.generation.IntVec3;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class VoxelModel {
    private static final int COORD_BITS = 21;
    private static final long COORD_MASK = (1L << COORD_BITS) - 1L;

    private final Map<Long, ResolvedPaletteEntry> occupied = new LinkedHashMap<>();

    public Set<Map.Entry<Long, ResolvedPaletteEntry>> entries() {
        return Collections.unmodifiableSet(occupied.entrySet());
    }

    public Collection<ResolvedPaletteEntry> materials() {
        return Collections.unmodifiableCollection(occupied.values());
    }

    public int size() {
        return occupied.size();
    }

    public ResolvedPaletteEntry put(IntVec3 point, ResolvedPaletteEntry paletteEntry) {
        return occupied.put(pack(point), paletteEntry);
    }

    public ResolvedPaletteEntry remove(IntVec3 point) {
        return occupied.remove(pack(point));
    }

    public boolean contains(IntVec3 point) {
        return occupied.containsKey(pack(point));
    }

    public ResolvedPaletteEntry get(IntVec3 point) {
        return occupied.get(pack(point));
    }

    public ResolvedPaletteEntry get(long packedCoordinate) {
        return occupied.get(packedCoordinate);
    }

    public static long pack(IntVec3 point) {
        return pack(point.x(), point.y(), point.z());
    }

    public static long pack(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x > COORD_MASK || y > COORD_MASK || z > COORD_MASK) {
            throw new IllegalArgumentException("Local coordinates are outside the packed range: [" + x + "," + y + "," + z + "]");
        }
        return ((long) x << (COORD_BITS * 2)) | ((long) y << COORD_BITS) | (z & COORD_MASK);
    }

    public static IntVec3 unpack(long packed) {
        int z = (int) (packed & COORD_MASK);
        int y = (int) ((packed >> COORD_BITS) & COORD_MASK);
        int x = (int) ((packed >> (COORD_BITS * 2)) & COORD_MASK);
        return new IntVec3(x, y, z);
    }

    public Map<Long, ResolvedPaletteEntry> asMap() {
        return Collections.unmodifiableMap(occupied);
    }

    public IntVec3 min() {
        IntVec3 min = null;
        for (long key : occupied.keySet()) {
            IntVec3 point = unpack(key);
            min = min == null ? point : IntVec3.min(min, point);
        }
        return min;
    }

    public IntVec3 max() {
        IntVec3 max = null;
        for (long key : occupied.keySet()) {
            IntVec3 point = unpack(key);
            max = max == null ? point : IntVec3.max(max, point);
        }
        return max;
    }

    public VoxelModel copy() {
        VoxelModel copy = new VoxelModel();
        copy.occupied.putAll(occupied);
        return copy;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof VoxelModel model)) {
            return false;
        }
        return Objects.equals(occupied, model.occupied);
    }

    @Override
    public int hashCode() {
        return occupied.hashCode();
    }
}
