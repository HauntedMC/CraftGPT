package nl.hauntedmc.craftgpt.generation.compiled.compiler;

import nl.hauntedmc.craftgpt.generation.Axis;
import nl.hauntedmc.craftgpt.generation.IntVec3;
import nl.hauntedmc.craftgpt.generation.compiled.*;

import java.util.ArrayList;
import java.util.List;

public final class PrimitiveVoxelizer {
    @FunctionalInterface
    public interface VoxelConsumer {
        void accept(IntVec3 point);
    }

    public void voxelize(PrimitiveOperation operation, VoxelConsumer consumer) {
        switch (operation) {
            case BoxOperation box -> voxelizeBox(box, consumer);
            case CylinderOperation cylinder -> voxelizeCylinder(cylinder, consumer);
            case EllipsoidOperation ellipsoid -> voxelizeEllipsoid(ellipsoid, consumer);
            case LineOperation line -> voxelizeLine(line, consumer);
            case PointOperation point -> voxelizePoint(point, consumer);
            case PointSetOperation points -> voxelizePointSet(points, consumer);
            case ProfileOperation profile -> voxelizeProfile(profile, consumer);
            case RawCuboidOperation rawCuboid -> voxelizeRawCuboid(rawCuboid, consumer);
        }
    }

    private void voxelizeBox(BoxOperation box, VoxelConsumer consumer) {
        IntVec3 min = IntVec3.min(box.from(), box.to());
        IntVec3 max = IntVec3.max(box.from(), box.to());
        for (int y = min.y(); y <= max.y(); y++) {
            for (int z = min.z(); z <= max.z(); z++) {
                for (int x = min.x(); x <= max.x(); x++) {
                    if (box.hollow() && !isOnHollowBoundary(x, y, z, min, max, box.wallThickness())) {
                        continue;
                    }
                    consumer.accept(new IntVec3(x, y, z));
                }
            }
        }
    }

    private void voxelizeCylinder(CylinderOperation cylinder, VoxelConsumer consumer) {
        int minA = axisStart(cylinder.center(), cylinder.axis());
        int maxA = minA + cylinder.height() - 1;
        for (int axisCoord = minA; axisCoord <= maxA; axisCoord++) {
            int outerSquared = cylinder.radius() * cylinder.radius();
            int innerRadius = Math.max(0, cylinder.radius() - cylinder.wallThickness());
            int innerSquared = innerRadius * innerRadius;

            for (int first = -cylinder.radius(); first <= cylinder.radius(); first++) {
                for (int second = -cylinder.radius(); second <= cylinder.radius(); second++) {
                    int distanceSquared = first * first + second * second;
                    if (distanceSquared > outerSquared) {
                        continue;
                    }
                    if (cylinder.hollow() && distanceSquared < innerSquared) {
                        continue;
                    }
                    consumer.accept(axisPoint(cylinder.axis(), axisCoord, cylinder.center(), first, second));
                }
            }
        }
    }

    private void voxelizeEllipsoid(EllipsoidOperation ellipsoid, VoxelConsumer consumer) {
        int outerRxSq = ellipsoid.radiusX() * ellipsoid.radiusX();
        int outerRySq = ellipsoid.radiusY() * ellipsoid.radiusY();
        int outerRzSq = ellipsoid.radiusZ() * ellipsoid.radiusZ();
        int innerRx = Math.max(0, ellipsoid.radiusX() - ellipsoid.wallThickness());
        int innerRy = Math.max(0, ellipsoid.radiusY() - ellipsoid.wallThickness());
        int innerRz = Math.max(0, ellipsoid.radiusZ() - ellipsoid.wallThickness());

        for (int x = ellipsoid.center().x() - ellipsoid.radiusX(); x <= ellipsoid.center().x() + ellipsoid.radiusX(); x++) {
            for (int y = ellipsoid.center().y() - ellipsoid.radiusY(); y <= ellipsoid.center().y() + ellipsoid.radiusY(); y++) {
                for (int z = ellipsoid.center().z() - ellipsoid.radiusZ(); z <= ellipsoid.center().z() + ellipsoid.radiusZ(); z++) {
                    if (!isInsideEllipsoid(x, y, z, ellipsoid.center(), ellipsoid.radiusX(), ellipsoid.radiusY(), ellipsoid.radiusZ(),
                            outerRxSq, outerRySq, outerRzSq)) {
                        continue;
                    }
                    if (ellipsoid.hollow() && innerRx > 0 && innerRy > 0 && innerRz > 0
                            && isInsideEllipsoid(x, y, z, ellipsoid.center(), innerRx, innerRy, innerRz,
                            innerRx * innerRx, innerRy * innerRy, innerRz * innerRz)) {
                        continue;
                    }
                    consumer.accept(new IntVec3(x, y, z));
                }
            }
        }
    }

    private void voxelizeLine(LineOperation line, VoxelConsumer consumer) {
        for (IntVec3 point : bresenham3d(line.from(), line.to())) {
            for (int x = point.x() - line.width(); x <= point.x() + line.width(); x++) {
                for (int y = point.y() - line.width(); y <= point.y() + line.width(); y++) {
                    for (int z = point.z() - line.width(); z <= point.z() + line.width(); z++) {
                        consumer.accept(new IntVec3(x, y, z));
                    }
                }
            }
        }
    }

    private void voxelizePoint(PointOperation point, VoxelConsumer consumer) {
        consumer.accept(point.at());
    }

    private void voxelizePointSet(PointSetOperation points, VoxelConsumer consumer) {
        for (IntVec3 point : points.points()) {
            consumer.accept(point);
        }
    }

    private void voxelizeProfile(ProfileOperation profile, VoxelConsumer consumer) {
        IntVec3 start = profile.from();
        IntVec3 end = profile.to();
        int thickness = Math.max(1, profile.wallThickness());
        int depth = Math.max(1, profile.depth());

        if (profile.axis() == Axis.X && start.x() != end.x()) {
            throw new IllegalArgumentException("Profile rectangles perpendicular to X must keep x fixed.");
        }
        if (profile.axis() == Axis.Y && start.y() != end.y()) {
            throw new IllegalArgumentException("Profile rectangles perpendicular to Y must keep y fixed.");
        }
        if (profile.axis() == Axis.Z && start.z() != end.z()) {
            throw new IllegalArgumentException("Profile rectangles perpendicular to Z must keep z fixed.");
        }

        IntVec3 min = IntVec3.min(start, end);
        IntVec3 max = IntVec3.max(start, end);
        for (int extrude = 0; extrude < depth; extrude++) {
            switch (profile.axis()) {
                case X -> {
                    int x = start.x() + extrude;
                    for (int y = min.y(); y <= max.y(); y++) {
                        for (int z = min.z(); z <= max.z(); z++) {
                            if (isOnRectangleBorder(y, z, min.y(), max.y(), min.z(), max.z(), thickness)) {
                                consumer.accept(new IntVec3(x, y, z));
                            }
                        }
                    }
                }
                case Y -> {
                    int y = start.y() + extrude;
                    for (int x = min.x(); x <= max.x(); x++) {
                        for (int z = min.z(); z <= max.z(); z++) {
                            if (isOnRectangleBorder(x, z, min.x(), max.x(), min.z(), max.z(), thickness)) {
                                consumer.accept(new IntVec3(x, y, z));
                            }
                        }
                    }
                }
                case Z -> {
                    int z = start.z() + extrude;
                    for (int x = min.x(); x <= max.x(); x++) {
                        for (int y = min.y(); y <= max.y(); y++) {
                            if (isOnRectangleBorder(x, y, min.x(), max.x(), min.y(), max.y(), thickness)) {
                                consumer.accept(new IntVec3(x, y, z));
                            }
                        }
                    }
                }
            }
        }
    }

    private void voxelizeRawCuboid(RawCuboidOperation operation, VoxelConsumer consumer) {
        IntVec3 min = IntVec3.min(operation.from(), operation.to());
        IntVec3 max = IntVec3.max(operation.from(), operation.to());
        for (int y = min.y(); y <= max.y(); y++) {
            for (int z = min.z(); z <= max.z(); z++) {
                for (int x = min.x(); x <= max.x(); x++) {
                    consumer.accept(new IntVec3(x, y, z));
                }
            }
        }
    }

    private boolean isOnHollowBoundary(int x, int y, int z, IntVec3 min, IntVec3 max, int thickness) {
        return x < min.x() + thickness || x > max.x() - thickness
                || y < min.y() + thickness || y > max.y() - thickness
                || z < min.z() + thickness || z > max.z() - thickness;
    }

    private boolean isOnRectangleBorder(int first, int second, int minFirst, int maxFirst, int minSecond, int maxSecond, int thickness) {
        return first < minFirst + thickness || first > maxFirst - thickness
                || second < minSecond + thickness || second > maxSecond - thickness;
    }

    private int axisStart(IntVec3 center, Axis axis) {
        return switch (axis) {
            case X -> center.x();
            case Y -> center.y();
            case Z -> center.z();
        };
    }

    private IntVec3 axisPoint(Axis axis, int axisCoord, IntVec3 center, int first, int second) {
        return switch (axis) {
            case X -> new IntVec3(axisCoord, center.y() + first, center.z() + second);
            case Y -> new IntVec3(center.x() + first, axisCoord, center.z() + second);
            case Z -> new IntVec3(center.x() + first, center.y() + second, axisCoord);
        };
    }

    private boolean isInsideEllipsoid(int x, int y, int z, IntVec3 center, int rx, int ry, int rz,
                                      int rxSq, int rySq, int rzSq) {
        double dx = x - center.x();
        double dy = y - center.y();
        double dz = z - center.z();
        double normalized = (dx * dx) / rxSq + (dy * dy) / rySq + (dz * dz) / rzSq;
        return normalized <= 1.0d;
    }

    /**
     * Deterministic 3D Bresenham traversal used for thick-line rasterization.
     */
    private List<IntVec3> bresenham3d(IntVec3 from, IntVec3 to) {
        List<IntVec3> points = new ArrayList<>();
        int x1 = from.x();
        int y1 = from.y();
        int z1 = from.z();
        int x2 = to.x();
        int y2 = to.y();
        int z2 = to.z();

        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int dz = Math.abs(z2 - z1);

        int xs = Integer.compare(x2, x1);
        int ys = Integer.compare(y2, y1);
        int zs = Integer.compare(z2, z1);

        points.add(new IntVec3(x1, y1, z1));
        if (dx >= dy && dx >= dz) {
            int p1 = 2 * dy - dx;
            int p2 = 2 * dz - dx;
            while (x1 != x2) {
                x1 += xs;
                if (p1 >= 0) {
                    y1 += ys;
                    p1 -= 2 * dx;
                }
                if (p2 >= 0) {
                    z1 += zs;
                    p2 -= 2 * dx;
                }
                p1 += 2 * dy;
                p2 += 2 * dz;
                points.add(new IntVec3(x1, y1, z1));
            }
            return points;
        }
        if (dy >= dx && dy >= dz) {
            int p1 = 2 * dx - dy;
            int p2 = 2 * dz - dy;
            while (y1 != y2) {
                y1 += ys;
                if (p1 >= 0) {
                    x1 += xs;
                    p1 -= 2 * dy;
                }
                if (p2 >= 0) {
                    z1 += zs;
                    p2 -= 2 * dy;
                }
                p1 += 2 * dx;
                p2 += 2 * dz;
                points.add(new IntVec3(x1, y1, z1));
            }
            return points;
        }

        int p1 = 2 * dy - dz;
        int p2 = 2 * dx - dz;
        while (z1 != z2) {
            z1 += zs;
            if (p1 >= 0) {
                y1 += ys;
                p1 -= 2 * dz;
            }
            if (p2 >= 0) {
                x1 += xs;
                p2 -= 2 * dz;
            }
            p1 += 2 * dy;
            p2 += 2 * dx;
            points.add(new IntVec3(x1, y1, z1));
        }
        return points;
    }
}
