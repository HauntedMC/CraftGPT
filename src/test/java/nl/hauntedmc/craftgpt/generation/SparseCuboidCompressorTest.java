package nl.hauntedmc.craftgpt.generation;

import nl.hauntedmc.craftgpt.generation.compiled.ResolvedBlock;
import nl.hauntedmc.craftgpt.generation.compiled.ResolvedPaletteEntry;
import nl.hauntedmc.craftgpt.generation.compiled.SparseCuboid;
import nl.hauntedmc.craftgpt.generation.compiled.compiler.SparseCuboidCompressor;
import nl.hauntedmc.craftgpt.generation.compiled.VoxelModel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparseCuboidCompressorTest {
    private final SparseCuboidCompressor compressor = new SparseCuboidCompressor();
    private final ResolvedPaletteEntry stone = new ResolvedPaletteEntry("1", "minecraft:stone",
            new ResolvedBlock("minecraft:stone", "minecraft:stone", null));
    private final ResolvedPaletteEntry dirt = new ResolvedPaletteEntry("2", "minecraft:dirt",
            new ResolvedBlock("minecraft:dirt", "minecraft:dirt", null));

    @Test
    void roundTripsSingleCellsAndBoxes() {
        VoxelModel model = new VoxelModel();
        model.put(new IntVec3(0, 0, 0), stone);
        model.put(new IntVec3(1, 0, 0), stone);
        model.put(new IntVec3(2, 0, 0), stone);
        model.put(new IntVec3(2, 1, 0), dirt);

        List<SparseCuboid> cuboids = compressor.compress(model);

        assertEquals(model, compressor.decompress(cuboids));
    }

    @Test
    void preservesHollowAndAlternatingPatterns() {
        VoxelModel model = new VoxelModel();
        for (int y = 0; y <= 2; y++) {
            for (int z = 0; z <= 2; z++) {
                for (int x = 0; x <= 2; x++) {
                    if (x == 1 && y == 1 && z == 1) {
                        continue;
                    }
                    model.put(new IntVec3(x, y, z), ((x + y + z) & 1) == 0 ? stone : dirt);
                }
            }
        }

        List<SparseCuboid> cuboids = compressor.compress(model);
        assertEquals(model, compressor.decompress(cuboids));
    }

    @Test
    void emitsDeterministicOrdering() {
        VoxelModel model = new VoxelModel();
        model.put(new IntVec3(2, 0, 0), stone);
        model.put(new IntVec3(0, 1, 0), stone);
        model.put(new IntVec3(0, 0, 1), stone);

        List<SparseCuboid> first = compressor.compress(model);
        List<SparseCuboid> second = compressor.compress(model.copy());

        assertIterableEquals(first, second);
        assertTrue(first.get(0).from().y() <= first.get(1).from().y());
    }

    @Test
    void randomizedRoundTrips() {
        Random random = new Random(12345L);
        for (int iteration = 0; iteration < 20; iteration++) {
            VoxelModel model = new VoxelModel();
            for (int y = 0; y < 6; y++) {
                for (int z = 0; z < 6; z++) {
                    for (int x = 0; x < 6; x++) {
                        if (random.nextBoolean()) {
                            model.put(new IntVec3(x, y, z), random.nextBoolean() ? stone : dirt);
                        }
                    }
                }
            }
            assertEquals(model, compressor.decompress(compressor.compress(model)));
        }
    }
}
