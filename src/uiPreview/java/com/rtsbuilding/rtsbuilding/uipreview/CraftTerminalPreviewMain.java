package com.rtsbuilding.rtsbuilding.uipreview;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.CraftTerminalChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.CraftTerminalLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.CraftTerminalStyle;

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

/**
 * 合成终端专用的 Java 8 离屏验收入口。
 *
 * <p>它直接调用正式 UiKit chrome，裁取美术 {@code terminal.png} 左侧概念区域，输出空态、
 * 填充态、并排图、结构差异图和数值报告。该入口不启动 Minecraft，也不会用另一套预览坐标
 * 伪造正式界面。</p>
 */
public final class CraftTerminalPreviewMain {
    private static final int WIDTH = CraftTerminalLayout.VISIBLE_WIDTH;
    private static final int HEIGHT = CraftTerminalLayout.IMAGE_HEIGHT;

    private CraftTerminalPreviewMain() {
    }

    public static void main(String[] args) throws IOException {
        UiPreviewMain.requireHeadless();
        if (args.length < 2) {
            throw new IllegalArgumentException("usage: output-directory terminal-concept-png");
        }
        File output = new File(args[0]);
        if (!output.isDirectory() && !output.mkdirs()) {
            throw new IOException("Cannot create craft terminal preview directory: " + output);
        }
        BufferedImage source = requireImage(new File(args[1]));
        BufferedImage reference = cropReference(source);
        BufferedImage empty = render(false);
        BufferedImage populated = render(true);

        ImageIO.write(reference, "png", new File(output, "craft-terminal-reference.png"));
        ImageIO.write(empty, "png", new File(output, "craft-terminal-empty.png"));
        ImageIO.write(populated, "png", new File(output, "craft-terminal-populated.png"));
        ImageIO.write(sideBySide(reference, empty, populated), "png",
                new File(output, "craft-terminal-side-by-side.png"));
        ImageIO.write(edgeDiff(reference, empty), "png",
                new File(output, "craft-terminal-edge-diff.png"));
        writeReport(new File(output, "craft-terminal-structure-report.txt"), reference, empty);
        System.out.println("Rendered craft terminal concept comparison to " + output);
    }

    private static BufferedImage render(boolean populated) {
        BufferedImageUiCanvas canvas = new BufferedImageUiCanvas(WIDTH, HEIGHT);
        canvas.clear(new Color(0, 0, 0, 255));
        CraftTerminalLayout.Geometry layout = CraftTerminalLayout.geometry(6);
        CraftTerminalChromeRenderer.render(canvas, layout, null, populated ? 11 : -1,
                populated, populated, true, 2, 1, true, 0.24D, 0.42D);
        if (populated) {
            drawFixtureItems(canvas, layout);
            canvas.text("合成终端", 7, 4, CraftTerminalStyle.TEXT);
            canvas.text("石材", 84, 4, CraftTerminalStyle.MUTED_TEXT);
        }
        canvas.close();
        return canvas.image();
    }

    private static void drawFixtureItems(
            BufferedImageUiCanvas canvas, CraftTerminalLayout.Geometry layout) {
        for (int cell = 0; cell < 28; cell++) {
            UiRect slot = layout.storageCell(cell);
            int tone = cell % 4;
            canvas.fill(new UiRect(slot.getX() + 4, slot.getY() + 4, 10, 10),
                    tone == 0 ? CraftTerminalStyle.BUTTON_ACTIVE
                            : tone == 1 ? CraftTerminalStyle.BORDER_MID
                            : tone == 2 ? CraftTerminalStyle.SLOT_HOVER
                            : CraftTerminalStyle.ICON_MUTED);
        }
        for (int i = 0; i < 3; i++) {
            canvas.fill(new UiRect(CraftTerminalLayout.CRAFT_GRID_X + 4 + i * 18,
                    CraftTerminalLayout.CRAFT_GRID_Y + 4 + 18, 8, 8),
                    CraftTerminalStyle.BORDER_MID);
        }
        canvas.fill(new UiRect(CraftTerminalLayout.RESULT_X + 3,
                CraftTerminalLayout.RESULT_Y + 3, 10, 10), CraftTerminalStyle.MUTED_TEXT);
    }

    private static BufferedImage cropReference(BufferedImage source) {
        BufferedImage result = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setColor(Color.BLACK);
        graphics.fillRect(0, 0, WIDTH, HEIGHT);
        graphics.drawImage(source, 0, 0, WIDTH, HEIGHT, 0, 0, WIDTH, HEIGHT, null);
        graphics.dispose();
        return result;
    }

    private static BufferedImage sideBySide(
            BufferedImage reference, BufferedImage empty, BufferedImage populated) {
        int gap = 8;
        int scale = 3;
        int panelWidth = WIDTH * scale;
        int panelHeight = HEIGHT * scale;
        BufferedImage result = new BufferedImage(panelWidth * 3 + gap * 4,
                panelHeight + gap * 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setColor(new Color(10, 14, 19));
        graphics.fillRect(0, 0, result.getWidth(), result.getHeight());
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        drawScaled(graphics, reference, gap, gap, panelWidth, panelHeight);
        drawScaled(graphics, empty, panelWidth + gap * 2, gap, panelWidth, panelHeight);
        drawScaled(graphics, populated, panelWidth * 2 + gap * 3, gap, panelWidth, panelHeight);
        graphics.dispose();
        return result;
    }

    private static void drawScaled(Graphics2D graphics, BufferedImage image,
                                   int x, int y, int width, int height) {
        graphics.drawImage(image, x, y, x + width, y + height,
                0, 0, image.getWidth(), image.getHeight(), null);
    }

    private static BufferedImage edgeDiff(BufferedImage expected, BufferedImage actual) {
        BufferedImage result = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
        for (int y = 1; y < HEIGHT; y++) {
            for (int x = 1; x < WIDTH; x++) {
                int delta = Math.abs(edge(expected, x, y) - edge(actual, x, y));
                if (delta > 10) {
                    result.setRGB(x, y, CraftTerminalStyle.BUTTON_ACTIVE.toArgb());
                }
            }
        }
        return result;
    }

    private static void writeReport(File file, BufferedImage expected, BufferedImage actual)
            throws IOException {
        long changed = 0;
        long edgeChanged = 0;
        long total = (long) WIDTH * HEIGHT;
        long deltaSum = 0;
        for (int y = 0; y < HEIGHT; y++) {
            for (int x = 0; x < WIDTH; x++) {
                int delta = maximumDelta(expected.getRGB(x, y), actual.getRGB(x, y));
                if (delta > 16) changed++;
                deltaSum += delta;
                if (x > 0 && y > 0
                        && Math.abs(edge(expected, x, y) - edge(actual, x, y)) > 10) {
                    edgeChanged++;
                }
            }
        }
        Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8");
        try {
            writer.write("RTSBuilding craft terminal concept comparison\n");
            writer.write("Reference crop: 0,0 " + WIDTH + "x" + HEIGHT + "\n");
            writer.write("Production source: CraftTerminalLayout + CraftTerminalChromeRenderer\n");
            writer.write("changed>16=" + changed + "/" + total + "\n");
            writer.write("structuralEdge changed>10=" + edgeChanged + "/" + total + "\n");
            writer.write("meanMaxChannelDelta=" + (deltaSum / (double) total) + "\n");
        } finally {
            writer.close();
        }
    }

    private static int edge(BufferedImage image, int x, int y) {
        int center = luminance(image.getRGB(x, y));
        return Math.max(Math.abs(center - luminance(image.getRGB(x - 1, y))),
                Math.abs(center - luminance(image.getRGB(x, y - 1))));
    }

    private static int luminance(int argb) {
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        return (r * 54 + g * 183 + b * 19) >>> 8;
    }

    private static int maximumDelta(int left, int right) {
        int maximum = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            maximum = Math.max(maximum,
                    Math.abs(((left >>> shift) & 0xFF) - ((right >>> shift) & 0xFF)));
        }
        return maximum;
    }

    private static BufferedImage requireImage(File file) throws IOException {
        BufferedImage image = ImageIO.read(file);
        if (image == null) throw new IOException("Cannot read image: " + file);
        return image;
    }
}
