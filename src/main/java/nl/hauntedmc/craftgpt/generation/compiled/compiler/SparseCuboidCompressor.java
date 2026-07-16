package nl.hauntedmc.craftgpt.generation.compiled.compiler;

import nl.hauntedmc.craftgpt.generation.IntVec3;
import nl.hauntedmc.craftgpt.generation.compiled.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SparseCuboidCompressor {
    public List<SparseCuboid> compress(VoxelModel voxelModel) {
        List<SparseCuboid> cuboids = new ArrayList<>();
        if (voxelModel.size() == 0) {
            return cuboids;
        }
        IntVec3 min = voxelModel.min();
        IntVec3 max = voxelModel.max();
        Set<Long> processed = new HashSet<>();

        for (int y = min.y(); y <= max.y(); y++) {
            for (int z = min.z(); z <= max.z(); z++) {
                for (int x = min.x(); x <= max.x(); x++) {
                    IntVec3 seed = new IntVec3(x, y, z);
                    long packed = VoxelModel.pack(seed);
                    if (processed.contains(packed)) {
                        continue;
                    }
                    ResolvedPaletteEntry material = voxelModel.get(packed);
                    if (material == null) {
                        continue;
                    }

                    int maxX = x;
                    while (isEligible(voxelModel, processed, material, maxX + 1, y, z)) {
                        maxX++;
                    }

                    int maxZ = z;
                    zLoop:
                    while (true) {
                        int candidateZ = maxZ + 1;
                        for (int scanX = x; scanX <= maxX; scanX++) {
                            if (!isEligible(voxelModel, processed, material, scanX, y, candidateZ)) {
                                break zLoop;
                            }
                        }
                        maxZ = candidateZ;
                    }

                    int maxY = y;
                    yLoop:
                    while (true) {
                        int candidateY = maxY + 1;
                        for (int scanZ = z; scanZ <= maxZ; scanZ++) {
                            for (int scanX = x; scanX <= maxX; scanX++) {
                                if (!isEligible(voxelModel, processed, material, scanX, candidateY, scanZ)) {
                                    break yLoop;
                                }
                            }
                        }
                        maxY = candidateY;
                    }

                    for (int markY = y; markY <= maxY; markY++) {
                        for (int markZ = z; markZ <= maxZ; markZ++) {
                            for (int markX = x; markX <= maxX; markX++) {
                                processed.add(VoxelModel.pack(markX, markY, markZ));
                            }
                        }
                    }
                    cuboids.add(new SparseCuboid(material, seed, new IntVec3(maxX, maxY, maxZ)));
                }
            }
        }
        return cuboids;
    }

    public VoxelModel decompress(List<SparseCuboid> cuboids) {
        VoxelModel voxelModel = new VoxelModel();
        for (SparseCuboid cuboid : cuboids) {
            for (int y = cuboid.from().y(); y <= cuboid.to().y(); y++) {
                for (int z = cuboid.from().z(); z <= cuboid.to().z(); z++) {
                    for (int x = cuboid.from().x(); x <= cuboid.to().x(); x++) {
                        voxelModel.put(new IntVec3(x, y, z), cuboid.paletteEntry());
                    }
                }
            }
        }
        return voxelModel;
    }

    private boolean isEligible(VoxelModel model, Set<Long> processed, ResolvedPaletteEntry material, int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0) {
            return false;
        }
        long packed;
        try {
            packed = VoxelModel.pack(x, y, z);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
        return !processed.contains(packed) && material.equals(model.get(packed));
    }
}
