package nl.hauntedmc.craftgpt.generation.compiled;

import nl.hauntedmc.craftgpt.generation.IntVec3;
import nl.hauntedmc.craftgpt.generation.QuarterTurn;

public record ComponentInstance(
        String componentName,
        IntVec3 translation,
        QuarterTurn rotation,
        boolean mirrorX,
        boolean mirrorZ,
        IntVec3 repeatStep,
        int repeatCount,
        IntVec3 repeatStepSecondary,
        int repeatCountSecondary
) {
    public ComponentInstance {
        repeatStep = repeatStep == null ? IntVec3.ZERO : repeatStep;
        repeatStepSecondary = repeatStepSecondary == null ? IntVec3.ZERO : repeatStepSecondary;
    }

    public int expandedPlacementCount() {
        return Math.multiplyExact(repeatCount, repeatCountSecondary);
    }
}
