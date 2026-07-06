package com.rtsbuilding.rtsbuilding.client.screen.panel.handler;

import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsUiScaleFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * RTS GUI 缩放管理器——管理固定缩放倍率的渲染与输入坐标适配。
 *
 * <p>从 {@link BuilderScreen} 提取，职责包括：</p>
 * <ul>
 *   <li>存储用户偏好的固定缩放倍率</li>
 *   <li>管理渲染通道和输入通道的缩放状态（防止递归重入）</li>
 *   <li>提供缩放入口：递归调用 Screen.render() 提交缩放后的坐标</li>
 *   <li>提供缩放输入入口：递归调用输入方法，坐标自动适配</li>
 *   <li>提供裁剪区域适配（需缩放倍率补偿）</li>
 * </ul>
 */
public final class BuilderScreenScaleManager {

    // ======================== 状态 ========================

    /** 用户设定的偏好 RTS GUI 缩放值（默认 2.0x） */
    private double fixedRtsGuiScale = BuilderScreenConstants.DEFAULT_RTS_GUI_SCALE;

    /** 当前是否处于固定缩放渲染通道中（防止递归重入） */
    private boolean fixedRtsScaleRenderPass = false;
    /** 当前是否处于固定缩放输入通道中（防止递归重入） */
    private boolean fixedRtsScaleInputPass = false;
    /** 活跃的渲染缩放倍率（仅在 fixedRtsScaleRenderPass 期间有效） */
    private double activeRtsGuiRenderScale = 1.0D;

    // ======================== 缩放值查询与修改 ========================

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

    /** 当前是否处于缩放渲染通道中（防止递归重入） */
    public boolean isInRenderPass() {
        return this.fixedRtsScaleRenderPass;
    }

    // ======================== 渲染缩放 ========================

    /**
     * 启用裁剪区域，自动适配当前活跃的渲染缩放倍率。
     * <p>在固定缩放渲染通道中，Minecraft 的裁剪坐标是缩放后的实际像素坐标，
     * 需将虚拟坐标乘以缩放倍率后再提交。</p>
     */
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

    /**
     * 以用户配置的固定 RTS GUI 缩放倍率渲染画面。
     *
     * @return true 表示已以非单位缩放处理（调用方应 return）
     */
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

    /**
     * 进入固定 RTS GUI 缩放帧：临时调整 screen 的 width/height 为虚拟尺寸，
     * 使 Minecraft 的 Screen 尺寸匹配用户偏好的固定缩放倍率。
     *
     * @return 缩放帧（调用方需在完成后 {@link RtsUiScaleFrame#close()} 恢复），
     *         或在不可缩放时返回 {@code null}
     */
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

    // ======================== 输入缩放 ========================

    /**
     * 进入固定缩放输入帧。若需要缩放，调用 {@code handler} 递归处理并返回结果。
     *
     * @return 非 null 表示缩放已递归处理（调用方应直接 return）；
     *         null 表示无需缩放或已在缩放通道中，调用方用原始坐标继续处理
     */
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

    /**
     * 进入固定缩放输入帧（void 版本）。
     */
    public boolean scaleMouseEventVoid(BuilderScreen screen, double mouseX, double mouseY,
                                        BiConsumer<Double, Double> handler) {
        Boolean result = scaleMouseEvent(screen, mouseX, mouseY, (x, y) -> {
            handler.accept(x, y);
            return true;
        });
        return result != null;
    }

    /**
     * 四参数输入缩放（用于 mouseDragged 等）。
     * <p>mouseDragged 有 4 个坐标参数（mouseX, mouseY, dragX, dragY），
     * 需要同时缩放全部四个变量。</p>
     */
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

    /**
     * 四参数回调接口，用于 {@link #scaleMouseEventQuad}。
     */
    @FunctionalInterface
    public interface QuadHandler {
        boolean apply(double mouseX, double mouseY, int button, double dragX, double dragY);
    }

    // ======================== 工具 ========================

    /**
     * 将缩放值限制到合法范围并按配置步长取整。
     */
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
