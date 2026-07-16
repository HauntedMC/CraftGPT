package nl.hauntedmc.craftgpt.generation;

import java.util.Locale;

public final class RequestClassifier {
    private RequestClassifier() {
    }

    public static RequestClassification classify(String request) {
        String normalized = request == null ? "" : request.toLowerCase(Locale.ROOT);
        if (normalized.contains("pixel art")
                || normalized.contains("floor art")
                || normalized.contains("relief")
                || normalized.contains("flat design")
                || normalized.contains("outline")) {
            return RequestClassification.FLAT_ART;
        }
        return RequestClassification.VOLUMETRIC;
    }
}
