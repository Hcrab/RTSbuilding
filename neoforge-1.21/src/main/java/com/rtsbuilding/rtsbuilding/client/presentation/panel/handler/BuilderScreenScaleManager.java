package com.rtsbuilding.rtsbuilding.client.presentation.panel.handler;

import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreenConstants;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.RtsUiScaleFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public final class BuilderScreenScaleManager {

    

    
    private double fixedRtsGuiScale = BuilderScreenConstants.DEFAULT_RTS_GUI_SCALE;

    
    private boolean fixedRtsScaleRenderPass = false;
    
    private boolean fixedRtsScaleInputPass = false;
    
    private double activeRtsGuiRenderScale = 1.0D;

    

    public double getRtsGuiScale() {
        return this.fixedRtsGuiScale;
    }

    public String rtsGuiScaleLabel() {
        double scale = sanitizeRtsGuiScale(this.fixedRtsGuiScale);
        if (Math.abs(scale - Math.rint(scale)) < 0.001D) {
            return String.format(Locale.ROOT, "%.0fx", scale);
        }
        return String.format(Locale.ROOT, "%.1fx", scale);
    }

    public void adjustRtsGuiScale(double delta) {
        this.fixedRtsGuiScale = sanitizeRtsGuiScale(this.fixedRtsGuiScale + delta);
    }

    public void setRtsGuiScale(double scale) {
        this.fixedRtsGuiScale = sanitizeRtsGuiScale(scale);
    }

    
    public boolean isInRenderPass() {
        return this.fixedRtsScaleRenderPass;
    }

    

    
    public void enableRtsScissor(GuiGraphics g, int x1, int y1, int x2, int y2) {
        double scale = this.fixedRtsScaleRenderPass ? this.activeRtsGuiRenderScale : 1.0D;
        if (scale > 0.0D && Double.isFinite(scale) && Math.abs(scale - 1.0D) >= 0.001D) {
            g.enableScissor(
                    (int) Math.floor(x1 * scale),
                    (int) Math.floor(y1 * scale),
                    (int) Math.ceil(x2 * scale),
                    (int) Math.ceil(y2 * scale));
            return;
        }
        g.enableScissor(x1, y1, x2, y2);
    }

    
    public boolean renderWithFixedRtsGuiScale(BuilderScreen screen, GuiGraphics g,
                                               int mouseX, int mouseY, float partialTick) {
        RtsUiScaleFrame frame = enterFixedRtsGuiScale(screen);
        if (frame == null || Math.abs(frame.scale() - 1.0D) < 0.001D) {
            if (frame != null) frame.close();
            return false;
        }
        this.fixedRtsScaleRenderPass = true;
        double previousActiveRenderScale = this.activeRtsGuiRenderScale;
        this.activeRtsGuiRenderScale = frame.scale();
        g.pose().pushPose();
        g.pose().scale((float) frame.scale(), (float) frame.scale(), 1.0F);
        try {
            screen.render(g,
                    (int) Math.round(mouseX / frame.scale()),
                    (int) Math.round(mouseY / frame.scale()),
                    partialTick);
        } finally {
            g.pose().popPose();
            this.activeRtsGuiRenderScale = previousActiveRenderScale;
            this.fixedRtsScaleRenderPass = false;
            frame.close();
        }
        return true;
    }

    
    public RtsUiScaleFrame enterFixedRtsGuiScale(BuilderScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (screen == null || mc == null || mc.getWindow() == null
                || screen.width <= 0 || screen.height <= 0) {
            return null;
        }
        double currentScale = mc.getWindow().getScreenWidth()
                / (double) Math.max(1, screen.width);
        if (currentScale <= 0.0D || !Double.isFinite(currentScale)) {
            return null;
        }
        double renderScale = this.fixedRtsGuiScale / currentScale;
        if (renderScale <= 0.0D || !Double.isFinite(renderScale)) {
            return null;
        }
        int oldW = screen.width;
        int oldH = screen.height;
        int virtualW = Math.max(1, (int) Math.round(oldW / renderScale));
        int virtualH = Math.max(1, (int) Math.round(oldH / renderScale));
        screen.width = virtualW;
        screen.height = virtualH;
        return new RtsUiScaleFrame(oldW, oldH, renderScale, () -> {
            screen.width = oldW;
            screen.height = oldH;
        });
    }

    

    
    @javax.annotation.Nullable
    public Boolean scaleMouseEvent(BuilderScreen screen, double mouseX, double mouseY,
                                    BiFunction<Double, Double, Boolean> handler) {
        if (this.fixedRtsScaleInputPass) return null;
        RtsUiScaleFrame frame = enterFixedRtsGuiScale(screen);
        if (frame == null) return false;
        if (Math.abs(frame.scale() - 1.0D) >= 0.001D) {
            this.fixedRtsScaleInputPass = true;
            try {
                return handler.apply(mouseX / frame.scale(), mouseY / frame.scale());
            } finally {
                this.fixedRtsScaleInputPass = false;
                frame.close();
            }
        }
        frame.close();
        return null;
    }

    
    public boolean scaleMouseEventVoid(BuilderScreen screen, double mouseX, double mouseY,
                                        BiConsumer<Double, Double> handler) {
        Boolean result = scaleMouseEvent(screen, mouseX, mouseY, (x, y) -> {
            handler.accept(x, y);
            return true;
        });
        return result != null;
    }

    
    public boolean scaleMouseEventQuad(BuilderScreen screen, double mouseX, double mouseY,
                                        int button, double dragX, double dragY,
                                        QuadHandler handler) {
        if (this.fixedRtsScaleInputPass) return false;
        RtsUiScaleFrame frame = enterFixedRtsGuiScale(screen);
        if (frame == null) return true;
        if (Math.abs(frame.scale() - 1.0D) >= 0.001D) {
            this.fixedRtsScaleInputPass = true;
            try {
                double s = frame.scale();
                return handler.apply(mouseX / s, mouseY / s, button, dragX / s, dragY / s);
            } finally {
                this.fixedRtsScaleInputPass = false;
                frame.close();
            }
        }
        frame.close();
        return false;
    }

    
    @FunctionalInterface
    public interface QuadHandler {
        boolean apply(double mouseX, double mouseY, int button, double dragX, double dragY);
    }

    

    
    private static double sanitizeRtsGuiScale(double scale) {
        if (!Double.isFinite(scale)) {
            return BuilderScreenConstants.DEFAULT_RTS_GUI_SCALE;
        }
        double snapped = Math.round(scale / BuilderScreenConstants.RTS_GUI_SCALE_STEP)
                * BuilderScreenConstants.RTS_GUI_SCALE_STEP;
        return Math.max(BuilderScreenConstants.MIN_RTS_GUI_SCALE,
                Math.min(BuilderScreenConstants.MAX_RTS_GUI_SCALE, snapped));
    }
}
