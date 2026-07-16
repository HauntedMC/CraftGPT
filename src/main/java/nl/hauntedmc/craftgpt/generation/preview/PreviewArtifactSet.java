package nl.hauntedmc.craftgpt.generation.preview;

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

public final class PreviewArtifactSet implements AutoCloseable {
    private final EnumMap<PreviewPerspective, Path> files;

    public PreviewArtifactSet(Map<PreviewPerspective, Path> files) {
        this.files = new EnumMap<>(files);
    }

    public Path file(PreviewPerspective perspective) {
        return files.get(perspective);
    }

    public Map<PreviewPerspective, Path> files() {
        return Map.copyOf(files);
    }

    @Override
    public void close() {
        for (Path path : files.values()) {
            if (path == null) {
                continue;
            }
            try {
                java.nio.file.Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
        }
    }
}
