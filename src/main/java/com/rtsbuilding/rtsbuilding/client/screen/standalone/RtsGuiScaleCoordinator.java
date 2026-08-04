package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.client.screen.handler.RtsUiScaleFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * 统一管理 RTS 固定 UI 缩放使用的虚拟视口、输入坐标和裁剪坐标。
 *
 * <p>本类只处理坐标系生命周期，不绘制具体面板、不解释鼠标动作，也不持久化
 * 缩放设置。{@link BuilderScreen} 仍然拥有屏幕生命周期和输入优先级；这里保证
 * 渲染递归、输入递归和 scissor 始终使用同一套缩放帧，避免三条链路各自维护
 * 标志位。</p>
 */
final class RtsGuiScaleCoordinator {
    private static final double UNIT_SCALE_EPSILON = 0.001D;

    private final Supplier<Minecraft> minecraft;
    private final IntSupplier width;
    private final IntSupplier height;
    private final IntConsumer setWidth;
    private final IntConsumer setHeight;
    private final DoubleSupplier configuredScale;
    private final IntSupplier minimumViewportWidth;
    private final IntSupplier minimumViewportHeight;

    private boolean renderPass;
    private boolean inputPass;
    private double activeRenderScale = 1.0D;
    private int lastUiWidth;
    private int lastUiHeight;

    RtsGuiScaleCoordinator(
            Supplier<Minecraft> minecraft,
            IntSupplier width,
            IntSupplier height,
            IntConsumer setWidth,
            IntConsumer setHeight,
            DoubleSupplier configuredScale,
            IntSupplier minimumViewportWidth,
            IntSupplier minimumViewportHeight) {
        this.minecraft = minecraft;
        this.width = width;
        this.height = height;
        this.setWidth = setWidth;
        this.setHeight = setHeight;
        this.configuredScale = configuredScale;
        this.minimumViewportWidth = minimumViewportWidth;
        this.minimumViewportHeight = minimumViewportHeight;
    }

    boolean isRenderPass() {
        return this.renderPass;
    }

    /**
     * 在非 1:1 缩放时开启虚拟渲染帧，并回调一次已经换算过的鼠标坐标。
     */
    boolean renderScaled(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick,
            RenderPass renderer) {
        RtsUiScaleFrame frame = enterFrame();
        if (frame == null || isUnitScale(frame.scale())) {
            close(frame);
            return false;
        }
        this.renderPass = true;
        double previousScale = this.activeRenderScale;
        this.activeRenderScale = frame.scale();
        graphics.pose().pushPose();
        graphics.pose().scale((float) frame.scale(), (float) frame.scale(), 1.0F);
        try {
            renderer.render(
                    graphics,
                    (int) Math.round(mouseX / frame.scale()),
                    (int) Math.round(mouseY / frame.scale()),
                    partialTick);
        } finally {
            graphics.pose().popPose();
            this.activeRenderScale = previousScale;
            this.renderPass = false;
            frame.close();
        }
        return true;
    }

    /**
     * 开启一次输入坐标帧。返回对象必须关闭；递归分发期间会自动阻止重复换算。
     */
    InputFrame beginInput() {
        if (this.inputPass) {
            return InputFrame.noop();
        }
        RtsUiScaleFrame frame = enterFrame();
        boolean remap = frame != null && !isUnitScale(frame.scale());
        if (remap) {
            this.inputPass = true;
        }
        return new InputFrame(this, frame, remap);
    }

    /**
     * 初始化持久化布局时临时进入虚拟视口，调用方负责关闭返回帧。
     */
    RtsUiScaleFrame enterLayoutFrame() {
        return enterFrame();
    }

    void recordViewport() {
        this.lastUiWidth = this.width.getAsInt();
        this.lastUiHeight = this.height.getAsInt();
    }

    int viewportWidth() {
        return this.lastUiWidth > 0 ? this.lastUiWidth : this.width.getAsInt();
    }

    int viewportHeight() {
        return this.lastUiHeight > 0 ? this.lastUiHeight : this.height.getAsInt();
    }

    void enableScissor(GuiGraphics graphics, int x1, int y1, int x2, int y2) {
        double scale = this.renderPass ? this.activeRenderScale : 1.0D;
        if (Double.isFinite(scale) && scale > 0.0D && !isUnitScale(scale)) {
            graphics.enableScissor(
                    (int) Math.floor(x1 * scale),
                    (int) Math.floor(y1 * scale),
                    (int) Math.ceil(x2 * scale),
                    (int) Math.ceil(y2 * scale));
            return;
        }
        graphics.enableScissor(x1, y1, x2, y2);
    }

    private RtsUiScaleFrame enterFrame() {
        Minecraft client = this.minecraft.get();
        int currentWidth = this.width.getAsInt();
        int currentHeight = this.height.getAsInt();
        if (client == null || client.getWindow() == null
                || currentWidth <= 0 || currentHeight <= 0) {
            return null;
        }
        double currentScale = client.getWindow().getScreenWidth()
                / (double) Math.max(1, currentWidth);
        if (!Double.isFinite(currentScale) || currentScale <= 0.0D) {
            return null;
        }
        double renderScale = this.configuredScale.getAsDouble() / currentScale;
        // 信息密度高的完整设置页可以声明一个临时最小虚拟视口。这里仅收紧本帧
        // 渲染倍率，不修改玩家保存的 RTS UI Scale；关闭页面后会自然恢复。
        int requiredWidth = this.minimumViewportWidth.getAsInt();
        int requiredHeight = this.minimumViewportHeight.getAsInt();
        if (requiredWidth > 0) {
            renderScale = Math.min(renderScale, currentWidth / (double) requiredWidth);
        }
        if (requiredHeight > 0) {
            renderScale = Math.min(renderScale, currentHeight / (double) requiredHeight);
        }
        if (!Double.isFinite(renderScale) || renderScale <= 0.0D) {
            return null;
        }
        int virtualWidth = Math.max(
                1, (int) Math.round(currentWidth / renderScale));
        int virtualHeight = Math.max(
                1, (int) Math.round(currentHeight / renderScale));
        this.setWidth.accept(virtualWidth);
        this.setHeight.accept(virtualHeight);
        return new RtsUiScaleFrame(
                currentWidth,
                currentHeight,
                renderScale,
                () -> {
                    this.setWidth.accept(currentWidth);
                    this.setHeight.accept(currentHeight);
                });
    }

    private void endInput(RtsUiScaleFrame frame, boolean ownsPass) {
        if (ownsPass) {
            this.inputPass = false;
        }
        close(frame);
    }

    private static boolean isUnitScale(double scale) {
        return Math.abs(scale - 1.0D) < UNIT_SCALE_EPSILON;
    }

    private static void close(RtsUiScaleFrame frame) {
        if (frame != null) {
            frame.close();
        }
    }

    @FunctionalInterface
    interface RenderPass {
        void render(
                GuiGraphics graphics,
                int mouseX,
                int mouseY,
                float partialTick);
    }

    /**
     * 一次输入换算的作用域。无缩放或递归调用时仍可安全关闭。
     */
    static final class InputFrame implements AutoCloseable {
        private final RtsGuiScaleCoordinator owner;
        private final RtsUiScaleFrame frame;
        private final boolean remap;
        private boolean closed;

        private InputFrame(
                RtsGuiScaleCoordinator owner,
                RtsUiScaleFrame frame,
                boolean remap) {
            this.owner = owner;
            this.frame = frame;
            this.remap = remap;
        }

        static InputFrame noop() {
            return new InputFrame(null, null, false);
        }

        boolean requiresRemap() {
            return this.remap;
        }

        double scale() {
            return this.frame == null ? 1.0D : this.frame.scale();
        }

        @Override
        public void close() {
            if (this.closed) {
                return;
            }
            this.closed = true;
            if (this.owner != null) {
                this.owner.endInput(this.frame, this.remap);
            }
        }
    }
}
