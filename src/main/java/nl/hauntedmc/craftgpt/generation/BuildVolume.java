package nl.hauntedmc.craftgpt.generation;

public record BuildVolume(int width, int height, int depth) {
    public BuildVolume {
        if (width <= 0 || height <= 0 || depth <= 0) {
            throw new IllegalArgumentException("Selection dimensions must be positive.");
        }
    }

    public int maxX() {
        return width - 1;
    }

    public int maxY() {
        return height - 1;
    }

    public int maxZ() {
        return depth - 1;
    }

    public long footprint() {
        return (long) width * depth;
    }

    public long volume() {
        return (long) width * height * depth;
    }

    public boolean contains(IntVec3 point) {
        return point.x() >= 0 && point.x() < width
                && point.y() >= 0 && point.y() < height
                && point.z() >= 0 && point.z() < depth;
    }
}
