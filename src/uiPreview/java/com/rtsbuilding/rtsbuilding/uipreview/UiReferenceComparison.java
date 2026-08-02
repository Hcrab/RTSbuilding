package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

/** 只比较顶部、底部和浮窗 UI 区域；Minecraft 世界像素明确排除。 */
public final class UiReferenceComparison {
    private UiReferenceComparison() {
    }

    public static void main(String[] args) throws IOException {
        UiPreviewMain.requireHeadless();
        if (args.length < 3) {
            throw new IllegalArgumentException("usage: reference actual output-directory");
        }
        BufferedImage reference = requireImage(new File(args[0]));
        BufferedImage actual = requireImage(new File(args[1]));
        File output = new File(args[2]);
        if (!output.isDirectory() && !output.mkdirs()) {
            throw new IOException("Cannot create comparison directory: " + output);
        }
        BufferedImage scaledReference = resize(reference, actual.getWidth(), actual.getHeight());
        UiPreviewScenario scenario = UiPreviewScenario.firstBatch().get(0);
        UiPreviewLayout layout = UiPreviewLayout.calculate(scenario);
        List<Zone> zones = zones(layout, actual.getWidth(), actual.getHeight());

        ImageIO.write(scaledReference, "png", new File(output, "reference-scaled.png"));
        ImageIO.write(sideBySide(scaledReference, actual, zones), "png",
                new File(output, "ui-reference-side-by-side.png"));
        ImageIO.write(heatmap(scaledReference, actual, zones), "png",
                new File(output, "ui-reference-diff.png"));
        ImageIO.write(edgeHeatmap(scaledReference, actual, zones), "png",
                new File(output, "ui-reference-edge-diff.png"));
        writeReport(new File(output, "ui-structure-report.txt"), scaledReference, actual, zones);
        System.out.println("Compared UI-only reference zones in " + output);
    }

    private static List<Zone> zones(UiPreviewLayout layout, int width, int height) {
        double scale = layout.renderScale();
        List<Zone> zones = new ArrayList<Zone>();
        zones.add(new Zone("top", new UiRect(0, 0, width,
                Math.min(height, Math.round(layout.topBar().bottom() * scale)))));
        zones.add(new Zone("settings", physical(layout.panels().get(0).bounds(), scale)
                .clampWithin(new UiRect(0, 0, width, height))));
        zones.add(new Zone("bottom", physical(layout.bottomBar(), scale)
                .clampWithin(new UiRect(0, 0, width, height))));
        return zones;
    }

    private static UiRect physical(UiRect logical, double scale) {
        return new UiRect(Math.round(logical.getX() * scale),
                Math.round(logical.getY() * scale),
                Math.round(logical.getWidth() * scale),
                Math.round(logical.getHeight() * scale));
    }

    private static BufferedImage sideBySide(BufferedImage reference, BufferedImage actual,
                                            List<Zone> zones) {
        int gap = 12;
        int width = reference.getWidth() * 2 + gap;
        int height = gap;
        for (Zone zone : zones) height += (int) zone.bounds.getHeight() + gap;
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setColor(new Color(12, 16, 22));
        graphics.fillRect(0, 0, width, height);
        int y = gap;
        for (Zone zone : zones) {
            int sx = (int) zone.bounds.getX();
            int sy = (int) zone.bounds.getY();
            int sw = (int) zone.bounds.getWidth();
            int sh = (int) zone.bounds.getHeight();
            graphics.drawImage(reference, 0, y, sw, y + sh,
                    sx, sy, sx + sw, sy + sh, null);
            graphics.drawImage(actual, reference.getWidth() + gap, y,
                    reference.getWidth() + gap + sw, y + sh,
                    sx, sy, sx + sw, sy + sh, null);
            y += sh + gap;
        }
        graphics.dispose();
        return result;
    }

    private static BufferedImage heatmap(BufferedImage reference, BufferedImage actual,
                                         List<Zone> zones) {
        BufferedImage result = new BufferedImage(actual.getWidth(), actual.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        for (Zone zone : zones) {
            int x0 = (int) zone.bounds.getX();
            int y0 = (int) zone.bounds.getY();
            int x1 = (int) zone.bounds.right();
            int y1 = (int) zone.bounds.bottom();
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    int expected = reference.getRGB(x, y);
                    int observed = actual.getRGB(x, y);
                    int delta = maximumDelta(expected, observed);
                    int alpha = Math.min(255, Math.max(24, delta));
                    result.setRGB(x, y, (alpha << 24) | 0x00FF2200);
                }
            }
        }
        return result;
    }

    /**
     * 颜色热图会把参考图中的半透明世界背景也算作差异；边缘图只比较局部亮度变化，
     * 更适合检查按钮、窗口边框、分栏、网格和文字基线是否发生结构漂移。
     */
    private static BufferedImage edgeHeatmap(BufferedImage reference, BufferedImage actual,
                                             List<Zone> zones) {
        BufferedImage result = new BufferedImage(actual.getWidth(), actual.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        for (Zone zone : zones) {
            int x0 = Math.max(1, (int) zone.bounds.getX());
            int y0 = Math.max(1, (int) zone.bounds.getY());
            int x1 = Math.min(actual.getWidth() - 1, (int) zone.bounds.right());
            int y1 = Math.min(actual.getHeight() - 1, (int) zone.bounds.bottom());
            for (int y = y0; y < y1; y++) {
                for (int x = x0; x < x1; x++) {
                    int delta = Math.abs(edge(reference, x, y) - edge(actual, x, y));
                    if (delta < 10) continue;
                    int alpha = Math.min(255, 32 + delta * 3);
                    result.setRGB(x, y, (alpha << 24) | 0x00FFB000);
                }
            }
        }
        return result;
    }

    private static int edge(BufferedImage image, int x, int y) {
        int center = luminance(image.getRGB(x, y));
        int horizontal = Math.abs(center - luminance(image.getRGB(x - 1, y)));
        int vertical = Math.abs(center - luminance(image.getRGB(x, y - 1)));
        return Math.max(horizontal, vertical);
    }

    private static int luminance(int argb) {
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return (r * 54 + g * 183 + b * 19) >>> 8;
    }

    private static void writeReport(File file, BufferedImage reference, BufferedImage actual,
                                    List<Zone> zones) throws IOException {
        Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
        try {
            writer.write("RTSBuilding headless UI reference comparison\n");
            writer.write("World pixels: excluded\n");
            writer.write("Reference: " + reference.getWidth() + "x" + reference.getHeight() + "\n");
            for (Zone zone : zones) {
                long changed = 0;
                long edgeChanged = 0;
                long total = 0;
                long deltaSum = 0;
                long edgeDeltaSum = 0;
                for (int y = (int) zone.bounds.getY(); y < zone.bounds.bottom(); y++) {
                    for (int x = (int) zone.bounds.getX(); x < zone.bounds.right(); x++) {
                        int delta = maximumDelta(reference.getRGB(x, y), actual.getRGB(x, y));
                        if (delta > 16) changed++;
                        deltaSum += delta;
                        if (x > 0 && y > 0) {
                            int edgeDelta = Math.abs(edge(reference, x, y) - edge(actual, x, y));
                            if (edgeDelta > 10) edgeChanged++;
                            edgeDeltaSum += edgeDelta;
                        }
                        total++;
                    }
                }
                writer.write(zone.name + " " + zone.bounds + " changed>16=" + changed + "/" + total
                        + " meanMaxChannelDelta=" + (total == 0 ? 0 : deltaSum / (double) total) + "\n");
                writer.write("  structuralEdge changed>10=" + edgeChanged + "/" + total
                        + " meanEdgeDelta=" + (total == 0 ? 0 : edgeDeltaSum / (double) total) + "\n");
            }
        } finally {
            writer.close();
        }
    }

    private static int maximumDelta(int left, int right) {
        int maximum = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            maximum = Math.max(maximum,
                    Math.abs(((left >>> shift) & 0xFF) - ((right >>> shift) & 0xFF)));
        }
        return maximum;
    }

    private static BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.drawImage(source, 0, 0, width, height, null);
        graphics.dispose();
        return result;
    }

    private static BufferedImage requireImage(File file) throws IOException {
        BufferedImage image = ImageIO.read(file);
        if (image == null) throw new IOException("Cannot read image: " + file);
        return image;
    }

    private static final class Zone {
        private final String name;
        private final UiRect bounds;

        private Zone(String name, UiRect bounds) {
            this.name = name;
            this.bounds = bounds;
        }
    }
}
