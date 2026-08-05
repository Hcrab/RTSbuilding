package com.rtsbuilding.rtsbuilding.client.screen.mode;

import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.client.util.RtsGuiVectorRenderer;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.uikit.theme.ModeWheelStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import com.rtsbuilding.rtsbuilding.platform.render.GlStateManager;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.ResourceLocation;

/** 长按 Alt 唤出的四向 RTS 鼠标模式轮盘。 */
public final class BuilderModeWheel {
    private static final int OPTION_DISTANCE = 54;
    private static final int OPTION_START_DISTANCE = 20;
    private static final int OPTION_RADIUS = 17;
    private static final int ICON_SIZE = 22;
    private static final int INNER_RADIUS = 16;
    private static final int OUTER_RADIUS = 82;
    private static final int EDGE_PADDING = 106;
    private static final long OPEN_DURATION_MS = 175L;
    private static final float HOVER_SPEED_PER_SECOND = 15.0F;

    private boolean open;
    private int centerX;
    private int centerY;
    private long transitionStartedAtMs;
    private long lastRenderAtMs;
    private final float[] hoverProgress = new float[4];

    public boolean isOpen() { return this.open; }

    public void open(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        this.centerX = clampCenter(mouseX, screenWidth);
        this.centerY = clampCenter(mouseY, screenHeight);
        this.open = true;
        this.transitionStartedAtMs = now();
        this.lastRenderAtMs = this.transitionStartedAtMs;
        for (int i = 0; i < this.hoverProgress.length; i++) this.hoverProgress[i] = 0.0F;
    }

    static int clampCenter(double coordinate, int screenSize) {
        if (screenSize <= EDGE_PADDING * 2) return Math.max(0, screenSize / 2);
        return clamp((int) Math.round(coordinate), EDGE_PADDING, screenSize - EDGE_PADDING);
    }

    public void close() {
        this.open = false;
        for (int i = 0; i < this.hoverProgress.length; i++) this.hoverProgress[i] = 0.0F;
    }

    public BuilderMode hoveredMode(double mouseX, double mouseY) {
        if (!this.open) return null;
        double dx = mouseX - this.centerX;
        double dy = mouseY - this.centerY;
        double radiusSquared = dx * dx + dy * dy;
        if (radiusSquared < INNER_RADIUS * INNER_RADIUS || radiusSquared > OUTER_RADIUS * OUTER_RADIUS) {
            return null;
        }
        if (Math.abs(dx) > Math.abs(dy)) return dx > 0.0D ? BuilderMode.LINK_STORAGE : BuilderMode.ROTATE;
        return dy > 0.0D ? BuilderMode.FUNNEL : BuilderMode.INTERACT;
    }

    public void render(LegacyGuiGraphics graphics, FontRenderer font, int mouseX, int mouseY,
            BuilderMode currentMode) {
        if (!this.open) return;
        long timestamp = now();
        float progress = animationProgress(timestamp);
        float deltaSeconds = Math.min(0.05F, Math.max(0L, timestamp - this.lastRenderAtMs) / 1000.0F);
        this.lastRenderAtMs = timestamp;
        BuilderMode hovered = hoveredMode(mouseX, mouseY);
        updateHoverAnimations(hovered, deltaSeconds);
        float alpha = clamp(progress, 0.0F, 1.0F);
        float distance = lerp(progress, OPTION_START_DISTANCE, OPTION_DISTANCE);
        float ringRadius = lerp(progress, 15.0F, 41.0F);

        RtsGuiVectorRenderer.drawRing(graphics, this.centerX, this.centerY, ringRadius, 8.0F,
                color(ModeWheelStyle.TRACK_BACKGROUND.toArgb(), alpha));
        RtsGuiVectorRenderer.drawRing(graphics, this.centerX, this.centerY, ringRadius, 1.25F,
                color(ModeWheelStyle.TRACK_BORDER.toArgb(), alpha));
        RtsGuiVectorRenderer.fillDisc(graphics, this.centerX, this.centerY, 2.0F + progress,
                color(ModeWheelStyle.CENTER_DOT.toArgb(), alpha * 0.78F));

        drawOption(graphics, BuilderMode.INTERACT, 0, -1, 0, distance, currentMode, hovered, alpha, progress);
        drawOption(graphics, BuilderMode.LINK_STORAGE, 1, 0, 1, distance, currentMode, hovered, alpha, progress);
        drawOption(graphics, BuilderMode.FUNNEL, 0, 1, 2, distance, currentMode, hovered, alpha, progress);
        drawOption(graphics, BuilderMode.ROTATE, -1, 0, 3, distance, currentMode, hovered, alpha, progress);

        drawLabelPill(graphics, font, tr(modeTranslationKey(hovered == null ? currentMode : hovered)),
                this.centerX, this.centerY + 80, alpha);
        graphics.drawCenteredString(font, tr("screen.rtsbuilding.mode_wheel.hint"), this.centerX,
                this.centerY + 97, color(ModeWheelStyle.HINT_TEXT.toArgb(), alpha * 0.86F));
    }

    private void drawOption(LegacyGuiGraphics graphics, BuilderMode mode, int dx, int dy,
            int optionIndex, float distance, BuilderMode currentMode, BuilderMode hoveredMode,
            float alpha, float openingProgress) {
        int cx = this.centerX + Math.round(dx * distance);
        int cy = this.centerY + Math.round(dy * distance);
        boolean current = mode == currentMode;
        boolean hovered = mode == hoveredMode;
        float hover = this.hoverProgress[optionIndex];
        float scale = (0.72F + openingProgress * 0.28F) * (1.0F + hover * 0.12F);
        float radius = OPTION_RADIUS * scale;
        RtsGuiVectorRenderer.fillDisc(graphics, cx, cy, radius + 1.25F,
                color(ModeWheelStyle.optionBorder(current, hover).toArgb(), alpha));
        RtsGuiVectorRenderer.fillDisc(graphics, cx, cy, Math.max(4.0F, radius - 1.25F),
                color(ModeWheelStyle.optionBackground(current, hover).toArgb(), alpha));

        String visual = current ? "active" : hovered ? "hover" : "inactive";
        ResourceLocation texture = new ResourceLocation("rtsbuilding", "textures/gui/topbar/"
                + modeTextureName(mode) + "_" + visual + ".png");
        int size = Math.max(12, Math.round(ICON_SIZE * scale));
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture);
        GlStateManager.enableBlend();
        GlStateManager.color(1.0F, 1.0F, 1.0F, alpha);
        com.rtsbuilding.rtsbuilding.platform.client.GuiCompat.drawModalRectWithCustomSizedTexture(cx - size / 2, cy - size / 2,
                0.0F, 0.0F, size, size, size, size);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private void updateHoverAnimations(BuilderMode hoveredMode, float deltaSeconds) {
        float amount = clamp(deltaSeconds * HOVER_SPEED_PER_SECOND, 0.0F, 1.0F);
        BuilderMode[] modes = {BuilderMode.INTERACT, BuilderMode.LINK_STORAGE,
                BuilderMode.FUNNEL, BuilderMode.ROTATE};
        for (int i = 0; i < modes.length; i++) {
            this.hoverProgress[i] = lerp(amount, this.hoverProgress[i], modes[i] == hoveredMode ? 1.0F : 0.0F);
        }
    }

    private float animationProgress(long timestamp) {
        float raw = clamp((timestamp - this.transitionStartedAtMs) / (float) OPEN_DURATION_MS, 0.0F, 1.0F);
        float remaining = 1.0F - raw;
        return 1.0F - remaining * remaining * remaining;
    }

    private static void drawLabelPill(LegacyGuiGraphics graphics, FontRenderer font, String text,
            int centerX, int centerY, float alpha) {
        int width = font.getStringWidth(text) + 16;
        RtsGuiVectorRenderer.fillCapsule(graphics, centerX - width / 2, centerX + (width + 1) / 2,
                centerY, 15.0F, color(ModeWheelStyle.LABEL_BACKGROUND.toArgb(), alpha * 0.88F));
        graphics.drawCenteredString(font, text, centerX, centerY - 4,
                color(ModeWheelStyle.LABEL_TEXT.toArgb(), alpha));
    }

    private static String modeTextureName(BuilderMode mode) {
        if (mode == BuilderMode.LINK_STORAGE) return "mode_link";
        if (mode == BuilderMode.FUNNEL) return "mode_funnel";
        if (mode == BuilderMode.ROTATE) return "mode_rotate";
        return "mode_interact";
    }

    private static String modeTranslationKey(BuilderMode mode) {
        if (mode == BuilderMode.LINK_STORAGE) return "screen.rtsbuilding.mode.link_storage";
        if (mode == BuilderMode.FUNNEL) return "screen.rtsbuilding.mode.funnel";
        if (mode == BuilderMode.ROTATE) return "screen.rtsbuilding.mode.rotate";
        return "screen.rtsbuilding.mode.interact";
    }

    private static String tr(String key) { return I18n.format(key); }
    private static long now() { return System.currentTimeMillis(); }
    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
    private static float lerp(float amount, float from, float to) { return from + (to - from) * amount; }
    private static int color(int argb, float multiplier) {
        int alpha = Math.round(((argb >>> 24) & 255) * clamp(multiplier, 0.0F, 1.0F));
        return new com.rtsbuilding.rtsbuilding.uikit.theme.UiColor(argb).withAlpha(alpha).toArgb();
    }

}
