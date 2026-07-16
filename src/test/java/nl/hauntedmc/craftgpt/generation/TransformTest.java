package nl.hauntedmc.craftgpt.generation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransformTest {
    @Test
    void appliesMirrorThenRotationThenTranslation() {
        Transform transform = new Transform(true, false, QuarterTurn.DEG_90, new IntVec3(10, 20, 30));

        IntVec3 transformed = transform.apply(new IntVec3(2, 3, 4));

        assertEquals(new IntVec3(14, 23, 32), transformed);
    }

    @Test
    void preservesYAxisAcrossEveryRotation() {
        IntVec3 point = new IntVec3(3, 7, 5);

        assertEquals(7, new Transform(false, false, QuarterTurn.DEG_0, IntVec3.ZERO).apply(point).y());
        assertEquals(7, new Transform(false, false, QuarterTurn.DEG_90, IntVec3.ZERO).apply(point).y());
        assertEquals(7, new Transform(false, false, QuarterTurn.DEG_180, IntVec3.ZERO).apply(point).y());
        assertEquals(7, new Transform(false, false, QuarterTurn.DEG_270, IntVec3.ZERO).apply(point).y());
    }

    @Test
    void surfacesArithmeticOverflowFromMirroring() {
        Transform transform = new Transform(true, false, QuarterTurn.DEG_0, IntVec3.ZERO);

        assertThrows(ArithmeticException.class, () -> transform.apply(new IntVec3(Integer.MIN_VALUE, 0, 0)));
    }
}
