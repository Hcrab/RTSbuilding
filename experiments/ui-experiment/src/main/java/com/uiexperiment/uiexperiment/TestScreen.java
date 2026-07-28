package com.uiexperiment.uiexperiment;

import com.uiexperiment.uiexperiment.client.SdfRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FastColor;

public class TestScreen extends Screen {

    private static final int BG_COLOR = 0xFF1a1a2e;
    private static final int PANEL_COLOR = 0xFF16213e;
    private static final int ACCENT_COLOR = 0xFF0f3460;
    private static final int TEXT_COLOR = 0xFFe0e0e0;
    private static final int HIGHLIGHT = 0xFFe94560;

    private float time;

    public TestScreen() {
        super(Component.literal("SDF + SVG Texture Test"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g, mouseX, mouseY, partialTick);
        time = (time + partialTick * 0.02f) % 360;

        int centerX = width / 2;
        int centerY = height / 2;

        // Title
        drawTitle(g);

        // Column 1: fixed radius, varying sizes
        int col1X = 20;
        int y = 40;
        drawLabel(g, "Same radius (12px), varying sizes", col1X, y - 10);
        drawSizedRects(g, col1X, y);

        // Column 2: same size, varying radii
        int col2X = col1X + 220;
        y = 40;
        drawLabel(g, "Same size (120x80), varying radii", col2X, y - 10);
        drawRadiusVariants(g, col2X, y);

        // Column 3: extreme small sizes (stress test)
        int col3X = col2X + 200;
        y = 40;
        drawLabel(g, "Small sizes (stress test)", col3X, y - 10);
        drawSmallVariants(g, col3X, y);

        // Animated radius demo
        int animY = height - 140;
        drawLabel(g, "Animated radius (4-40px cycling)", 20, animY - 10);
        drawAnimated(g, 20, animY, centerX - 20, 80);

        // Scale stress test - batch of tiny rects
        int gridY = height - 60;
        drawLabel(g, "Grid stress test (tiny rects at 0.5px-4px radius)", 20, gridY - 10);
        drawGridStress(g, 20, gridY);

        // Column 4: Triangle SDF test
        int col4X = col3X + 180;
        y = 40;
        drawLabel(g, "Triangle SDF (various sizes)", col4X, y - 10);
        drawTriangleVariants(g, col4X, y);

        // Animated triangle
        int triAnimY = height - 140;
        drawLabel(g, "Animated triangle (rotating)", 20, triAnimY - 10);
        drawAnimatedTriangle(g, 20, triAnimY, 60, 100);

        // Triangle grid stress test
        int triGridY = height - 60;
        drawLabel(g, "Triangle grid stress test (tiny)", 20, triGridY - 10);
        drawTriangleGridStress(g, 300, triGridY);
    }

    private void drawTitle(GuiGraphics g) {
        String title = "SDF Rounded Rectangle Test";
        int tw = font.width(title);
        font.drawInBatch(title, (width - tw) / 2f, 8, TEXT_COLOR, true,
                g.pose().last().pose(), g.bufferSource(), net.minecraft.client.gui.Font.DisplayMode.NORMAL,
                0, 0xF000F0);
    }

    private void drawLabel(GuiGraphics g, String text, int x, int y) {
        font.drawInBatch(text, x, y, TEXT_COLOR, true,
                g.pose().last().pose(), g.bufferSource(), net.minecraft.client.gui.Font.DisplayMode.NORMAL,
                0, 0xF000F0);
    }

    private void drawSizedRects(GuiGraphics g, int x, int y) {
        int[] sizes = {20, 40, 80, 160, 240};
        int[] colors = {0x88e94560, 0x880f3460, 0x8816213e, 0x88533a71, 0x884a7c59};
        for (int i = 0; i < sizes.length; i++) {
            int s = sizes[i];
            SdfRenderer.drawRoundedRect(g, x, y, s, s, 12, colors[i], 1f);
            g.flush();
            font.drawInBatch(String.valueOf(s) + "x" + s, x, y + s + 2, TEXT_COLOR, true,
                    g.pose().last().pose(), g.bufferSource(),
                    net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 0xF000F0);
            g.flush();
            y += s + 16;
        }
    }

    private void drawRadiusVariants(GuiGraphics g, int x, int y) {
        int[] radii = {2, 6, 12, 24, 40};
        for (int r : radii) {
            SdfRenderer.drawRoundedRect(g, x, y, 120, 80, r, 0x88533a71, 1f);
            g.flush();
            font.drawInBatch("radius=" + r, x, y + 84, TEXT_COLOR, true,
                    g.pose().last().pose(), g.bufferSource(),
                    net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 0xF000F0);
            g.flush();
            y += 100;
        }
    }

    private void drawSmallVariants(GuiGraphics g, int x, int y) {
        int[][] cases = {
                {8, 8, 2},    // tiny square
                {16, 8, 3},   // thin rect
                {8, 24, 4},   // narrow tall
                {6, 6, 1},    // very tiny
                {4, 4, 1},    // minimum
        };
        for (int[] c : cases) {
            int cw = c[0], ch = c[1], cr = c[2];
            SdfRenderer.drawRoundedRect(g, x, y, cw, ch, cr, 0x88e94560, 1f);
            g.flush();
            font.drawInBatch(cw + "x" + ch + " r=" + cr, x + cw + 6, y + Math.max(0, (ch - 9) / 2),
                    TEXT_COLOR, true, g.pose().last().pose(), g.bufferSource(),
                    net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 0xF000F0);
            g.flush();
            y += Math.max(ch + 16, 20);
        }
    }

    private void drawAnimated(GuiGraphics g, int x, int y, int w, int h) {
        float t = (float) Math.toRadians(time * 3);
        float animatedRadius = 4 + Math.abs((float) Math.sin(t)) * 36;
        int color = FastColor.ARGB32.color(180, 233, 69, 96);

        SdfRenderer.drawRoundedRect(g, x, y, w, h, animatedRadius, color);
        g.flush();

        String info = "w=" + w + " h=" + h + " radius=" + String.format("%.1f", animatedRadius);
        font.drawInBatch(info, x, y + h + 4, TEXT_COLOR, true,
                g.pose().last().pose(), g.bufferSource(),
                net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 0xF000F0);
        g.flush();
    }

    private void drawTriangleVariants(GuiGraphics g, int x, int y) {
        int[] sizes = {16, 32, 64, 96, 128};
        int[] colors = {0x88e94560, 0x88533a71, 0x884a7c59, 0x880f3460, 0x8816213e};
        for (int i = 0; i < sizes.length; i++) {
            int s = sizes[i];
            SdfRenderer.drawTriangle(g, x, y, s, (int)(s * 1.2f), colors[i], 1f);
            g.flush();
            font.drawInBatch(s + "x" + (int)(s*1.2), x, y + (int)(s*1.2) + 2, TEXT_COLOR, true,
                    g.pose().last().pose(), g.bufferSource(),
                    net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 0xF000F0);
            g.flush();
            y += (int)(s * 1.2) + 16;
        }
    }

    private void drawAnimatedTriangle(GuiGraphics g, int x, int y, int w, int h) {
        float t = (float) Math.toRadians(time * 2);
        // oscillate size and position
        int cw = (int)(w * (0.7f + 0.3f * ((float)Math.sin(t) * 0.5f + 0.5f)));
        int ch = (int)(h * (0.7f + 0.3f * ((float)Math.cos(t) * 0.5f + 0.5f)));
        int px = x + (w - cw) / 2;
        int py = y + (h - ch) / 2;
        int color = FastColor.ARGB32.color(200, 233, 69, 96);
        SdfRenderer.drawTriangle(g, px, py, cw, ch, color);
        g.flush();
        font.drawInBatch("tri " + cw + "x" + ch, x, y + h + 4, TEXT_COLOR, true,
                g.pose().last().pose(), g.bufferSource(),
                net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 0xF000F0);
        g.flush();
    }

    private void drawTriangleGridStress(GuiGraphics g, int startX, int startY) {
        int cols = 15;
        int rows = 3;
        int cellW = 18;
        int cellH = 14;
        int gap = 3;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int x = startX + col * (cellW + gap);
                int y = startY + row * (cellH + gap);
                int color = (row == 0) ? 0x88e94560 : (row == 1) ? 0x88533a71 : 0x884a7c59;
                SdfRenderer.drawTriangle(g, x, y, cellW, cellH, color);
            }
        }
        g.flush();
    }

    private void drawGridStress(GuiGraphics g, int startX, int startY) {
        int cols = 18;
        int rows = 3;
        int cellW = 22;
        int cellH = 16;
        int gap = 4;
        int[] radii = {1, 2, 4};
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int x = startX + col * (cellW + gap);
                int y = startY + row * (cellH + gap);
                int color = (row == 0) ? 0x88e94560 : (row == 1) ? 0x88533a71 : 0x884a7c59;
                SdfRenderer.drawRoundedRect(g, x, y, cellW, cellH, radii[row], color);
            }
        }
        g.flush();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
