package nl.hauntedmc.craftgpt.generation.preview;

import nl.hauntedmc.craftgpt.generation.IntVec3;
import nl.hauntedmc.craftgpt.generation.compiled.ResolvedPaletteEntry;
import nl.hauntedmc.craftgpt.generation.compiled.VoxelModel;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class PreviewRenderer {
    private final BlockColorResolver colorResolver = new BlockColorResolver();

    public PreviewArtifactSet render(VoxelModel voxelModel, int imageSize, boolean debugArtifactsEnabled, String debugDirectory) throws IOException {
        return render(voxelModel, imageSize,
                List.of(PreviewPerspective.FRONT, PreviewPerspective.RIGHT, PreviewPerspective.TOP, PreviewPerspective.ISOMETRIC),
                debugArtifactsEnabled, debugDirectory);
    }

    public PreviewArtifactSet render(VoxelModel voxelModel, int imageSize, Collection<PreviewPerspective> perspectives,
                                     boolean debugArtifactsEnabled, String debugDirectory) throws IOException {
        EnumMap<PreviewPerspective, BufferedImage> images = new EnumMap<>(PreviewPerspective.class);
        for (PreviewPerspective perspective : perspectives) {
            images.put(perspective, switch (perspective) {
                case FRONT -> renderFront(voxelModel, imageSize);
                case BACK -> renderBack(voxelModel, imageSize);
                case LEFT -> renderLeft(voxelModel, imageSize);
                case RIGHT -> renderRight(voxelModel, imageSize);
                case TOP -> renderTop(voxelModel, imageSize);
                case ISOMETRIC -> renderIsometric(voxelModel, imageSize);
            });
        }

        EnumMap<PreviewPerspective, Path> files = new EnumMap<>(PreviewPerspective.class);
        for (Map.Entry<PreviewPerspective, BufferedImage> entry : images.entrySet()) {
            Path file = Files.createTempFile("craftgpt-preview-" + entry.getKey().name().toLowerCase(), ".png");
            ImageIO.write(entry.getValue(), "png", file.toFile());
            files.put(entry.getKey(), file);

            if (debugArtifactsEnabled && debugDirectory != null && !debugDirectory.isBlank()) {
                Path debugDir = Path.of(debugDirectory);
                Files.createDirectories(debugDir);
                ImageIO.write(entry.getValue(), "png", debugDir.resolve(file.getFileName().toString()).toFile());
            }
        }
        return new PreviewArtifactSet(files);
    }

    private BufferedImage renderFront(VoxelModel voxelModel, int imageSize) {
        IntVec3 min = voxelModel.min();
        IntVec3 max = voxelModel.max();
        BufferedImage image = createImage(imageSize);
        Graphics2D graphics = image.createGraphics();
        configure(graphics);
        double scale = computeScale(imageSize, max.x() - min.x() + 1, max.y() - min.y() + 1);
        double xOffset = (imageSize - ((max.x() - min.x() + 1) * scale)) / 2.0d;
        double yOffset = (imageSize - ((max.y() - min.y() + 1) * scale)) / 2.0d;

        for (int x = min.x(); x <= max.x(); x++) {
            for (int y = min.y(); y <= max.y(); y++) {
                ResolvedPaletteEntry visible = null;
                for (int z = min.z(); z <= max.z(); z++) {
                    ResolvedPaletteEntry candidate = voxelModel.get(new IntVec3(x, y, z));
                    if (candidate != null) {
                        visible = candidate;
                    }
                }
                if (visible != null) {
                    fillCell(graphics, xOffset, yOffset, scale, x - min.x(), max.y() - y, color(visible, 1.0d));
                }
            }
        }
        graphics.dispose();
        return image;
    }

    private BufferedImage renderBack(VoxelModel voxelModel, int imageSize) {
        IntVec3 min = voxelModel.min();
        IntVec3 max = voxelModel.max();
        BufferedImage image = createImage(imageSize);
        Graphics2D graphics = image.createGraphics();
        configure(graphics);
        double scale = computeScale(imageSize, max.x() - min.x() + 1, max.y() - min.y() + 1);
        double xOffset = (imageSize - ((max.x() - min.x() + 1) * scale)) / 2.0d;
        double yOffset = (imageSize - ((max.y() - min.y() + 1) * scale)) / 2.0d;

        for (int x = max.x(); x >= min.x(); x--) {
            for (int y = min.y(); y <= max.y(); y++) {
                ResolvedPaletteEntry visible = null;
                for (int z = max.z(); z >= min.z(); z--) {
                    ResolvedPaletteEntry candidate = voxelModel.get(new IntVec3(x, y, z));
                    if (candidate != null) {
                        visible = candidate;
                        break;
                    }
                }
                if (visible != null) {
                    fillCell(graphics, xOffset, yOffset, scale, max.x() - x, max.y() - y, color(visible, 0.94d));
                }
            }
        }
        graphics.dispose();
        return image;
    }

    private BufferedImage renderRight(VoxelModel voxelModel, int imageSize) {
        IntVec3 min = voxelModel.min();
        IntVec3 max = voxelModel.max();
        BufferedImage image = createImage(imageSize);
        Graphics2D graphics = image.createGraphics();
        configure(graphics);
        double scale = computeScale(imageSize, max.z() - min.z() + 1, max.y() - min.y() + 1);
        double xOffset = (imageSize - ((max.z() - min.z() + 1) * scale)) / 2.0d;
        double yOffset = (imageSize - ((max.y() - min.y() + 1) * scale)) / 2.0d;

        for (int z = min.z(); z <= max.z(); z++) {
            for (int y = min.y(); y <= max.y(); y++) {
                ResolvedPaletteEntry visible = null;
                for (int x = max.x(); x >= min.x(); x--) {
                    ResolvedPaletteEntry candidate = voxelModel.get(new IntVec3(x, y, z));
                    if (candidate != null) {
                        visible = candidate;
                        break;
                    }
                }
                if (visible != null) {
                    fillCell(graphics, xOffset, yOffset, scale, z - min.z(), max.y() - y, color(visible, 0.88d));
                }
            }
        }
        graphics.dispose();
        return image;
    }

    private BufferedImage renderLeft(VoxelModel voxelModel, int imageSize) {
        IntVec3 min = voxelModel.min();
        IntVec3 max = voxelModel.max();
        BufferedImage image = createImage(imageSize);
        Graphics2D graphics = image.createGraphics();
        configure(graphics);
        double scale = computeScale(imageSize, max.z() - min.z() + 1, max.y() - min.y() + 1);
        double xOffset = (imageSize - ((max.z() - min.z() + 1) * scale)) / 2.0d;
        double yOffset = (imageSize - ((max.y() - min.y() + 1) * scale)) / 2.0d;

        for (int z = max.z(); z >= min.z(); z--) {
            for (int y = min.y(); y <= max.y(); y++) {
                ResolvedPaletteEntry visible = null;
                for (int x = min.x(); x <= max.x(); x++) {
                    ResolvedPaletteEntry candidate = voxelModel.get(new IntVec3(x, y, z));
                    if (candidate != null) {
                        visible = candidate;
                        break;
                    }
                }
                if (visible != null) {
                    fillCell(graphics, xOffset, yOffset, scale, max.z() - z, max.y() - y, color(visible, 0.82d));
                }
            }
        }
        graphics.dispose();
        return image;
    }

    private BufferedImage renderTop(VoxelModel voxelModel, int imageSize) {
        IntVec3 min = voxelModel.min();
        IntVec3 max = voxelModel.max();
        BufferedImage image = createImage(imageSize);
        Graphics2D graphics = image.createGraphics();
        configure(graphics);
        double scale = computeScale(imageSize, max.x() - min.x() + 1, max.z() - min.z() + 1);
        double xOffset = (imageSize - ((max.x() - min.x() + 1) * scale)) / 2.0d;
        double yOffset = (imageSize - ((max.z() - min.z() + 1) * scale)) / 2.0d;

        for (int x = min.x(); x <= max.x(); x++) {
            for (int z = min.z(); z <= max.z(); z++) {
                ResolvedPaletteEntry visible = null;
                for (int y = max.y(); y >= min.y(); y--) {
                    ResolvedPaletteEntry candidate = voxelModel.get(new IntVec3(x, y, z));
                    if (candidate != null) {
                        visible = candidate;
                        break;
                    }
                }
                if (visible != null) {
                    fillCell(graphics, xOffset, yOffset, scale, x - min.x(), z - min.z(), color(visible, 1.12d));
                }
            }
        }
        graphics.dispose();
        return image;
    }

    private BufferedImage renderIsometric(VoxelModel voxelModel, int imageSize) {
        IntVec3 min = voxelModel.min();
        IntVec3 max = voxelModel.max();
        BufferedImage image = createImage(imageSize);
        Graphics2D graphics = image.createGraphics();
        configure(graphics);
        double tileWidth = Math.max(4.0d, imageSize / (double) ((max.x() - min.x() + 1) + (max.z() - min.z() + 1) + 4));
        double tileHeight = tileWidth / 2.0d;
        double blockHeight = tileWidth / 2.0d;
        double originX = imageSize / 2.0d;
        double originY = imageSize - tileWidth * 2.0d;

        voxelModel.entries().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new IsoCell(VoxelModel.unpack(entry.getKey()), entry.getValue()))
                .sorted((left, right) -> {
                    int leftDepth = left.point.x() + left.point.z() + left.point.y();
                    int rightDepth = right.point.x() + right.point.z() + right.point.y();
                    return Integer.compare(leftDepth, rightDepth);
                })
                .forEach(cell -> drawIsoBlock(graphics, cell.point, cell.entry, min, originX, originY, tileWidth, tileHeight, blockHeight));

        graphics.dispose();
        return image;
    }

    private void drawIsoBlock(Graphics2D graphics, IntVec3 point, ResolvedPaletteEntry entry, IntVec3 min,
                              double originX, double originY, double tileWidth, double tileHeight, double blockHeight) {
        double x = originX + ((point.x() - min.x()) - (point.z() - min.z())) * (tileWidth / 2.0d);
        double y = originY - (point.y() - min.y()) * blockHeight - ((point.x() - min.x()) + (point.z() - min.z())) * (tileHeight / 2.0d);

        Color base = color(entry, 1.0d);
        Polygon top = polygon(
                x, y - tileHeight,
                x + tileWidth / 2.0d, y - tileHeight / 2.0d,
                x, y,
                x - tileWidth / 2.0d, y - tileHeight / 2.0d
        );
        Polygon left = polygon(
                x - tileWidth / 2.0d, y - tileHeight / 2.0d,
                x, y,
                x, y + blockHeight,
                x - tileWidth / 2.0d, y + blockHeight - tileHeight / 2.0d
        );
        Polygon right = polygon(
                x + tileWidth / 2.0d, y - tileHeight / 2.0d,
                x, y,
                x, y + blockHeight,
                x + tileWidth / 2.0d, y + blockHeight - tileHeight / 2.0d
        );

        graphics.setColor(adjust(base, 1.1d));
        graphics.fillPolygon(top);
        graphics.setColor(adjust(base, 0.86d));
        graphics.fillPolygon(left);
        graphics.setColor(adjust(base, 0.72d));
        graphics.fillPolygon(right);
        graphics.setColor(new Color(0, 0, 0, 40));
        graphics.setStroke(new BasicStroke(1f));
        graphics.drawPolygon(top);
        graphics.drawPolygon(left);
        graphics.drawPolygon(right);
    }

    private Polygon polygon(double... coordinates) {
        int[] xs = new int[coordinates.length / 2];
        int[] ys = new int[coordinates.length / 2];
        for (int i = 0; i < coordinates.length; i += 2) {
            xs[i / 2] = (int) Math.round(coordinates[i]);
            ys[i / 2] = (int) Math.round(coordinates[i + 1]);
        }
        return new Polygon(xs, ys, xs.length);
    }

    private BufferedImage createImage(int imageSize) {
        return new BufferedImage(imageSize, imageSize, BufferedImage.TYPE_INT_ARGB);
    }

    private void configure(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_SPEED);
    }

    private double computeScale(int imageSize, int width, int height) {
        return Math.max(2.0d, Math.floor((imageSize - 32.0d) / Math.max(width, height)));
    }

    private void fillCell(Graphics2D graphics, double xOffset, double yOffset, double scale, int x, int y, Color color) {
        graphics.setColor(color);
        graphics.fillRect(
                (int) Math.round(xOffset + x * scale),
                (int) Math.round(yOffset + y * scale),
                Math.max(1, (int) Math.ceil(scale)),
                Math.max(1, (int) Math.ceil(scale))
        );
    }

    private Color color(ResolvedPaletteEntry entry, double shading) {
        return adjust(colorResolver.resolve(entry.block().canonicalBlockState()), shading);
    }

    private Color adjust(Color color, double factor) {
        int red = clamp((int) Math.round(color.getRed() * factor));
        int green = clamp((int) Math.round(color.getGreen() * factor));
        int blue = clamp((int) Math.round(color.getBlue() * factor));
        return new Color(red, green, blue, 255);
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private record IsoCell(IntVec3 point, ResolvedPaletteEntry entry) {
    }
}
