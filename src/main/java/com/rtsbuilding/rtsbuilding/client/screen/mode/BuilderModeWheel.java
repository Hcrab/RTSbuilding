package com.rtsbuilding.rtsbuilding.client.screen.mode;

import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarIconRenderer;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiTextureState;
import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarTypes;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.util.RtsGuiVectorRenderer;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.uikit.theme.ModeWheelStyle;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 长按 Alt 唤出的四向 RTS 鼠标模式轮盘。
 *
 * <p>本类只管理临时显示状态、命中检测和绘制，不负责真正切换模式。模式提交仍由
 * {@code BuilderScreen} 统一处理，因此顶部栏、相机、漏斗和服务端同步只读取一套状态。</p>
 */
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

    public boolean isOpen() {
        return this.open;
    }

    public void open(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        this.centerX = clampCenter(mouseX, screenWidth);
        this.centerY = clampCenter(mouseY, screenHeight);
        this.open = true;
        this.transitionStartedAtMs = Util.getMillis();
        this.lastRenderAtMs = this.transitionStartedAtMs;
        for (int i = 0; i < this.hoverProgress.length; i++) {
            this.hoverProgress[i] = 0.0F;
        }
    }

    static int clampCenter(double mouseCoordinate, int screenSize) {
        if (screenSize <= EDGE_PADDING * 2) {
            return Math.max(0, screenSize / 2);
        }
        return Mth.clamp(
                (int) Math.round(mouseCoordinate),
                EDGE_PADDING,
                screenSize - EDGE_PADDING);
    }

    public void close() {
        this.open = false;
        for (int i = 0; i < this.hoverProgress.length; i++) {
            this.hoverProgress[i] = 0.0F;
        }
    }

    public BuilderMode hoveredMode(double mouseX, double mouseY) {
        if (!this.open) {
            return null;
        }
        double dx = mouseX - this.centerX;
        double dy = mouseY - this.centerY;
        double radiusSquared = dx * dx + dy * dy;
        if (radiusSquared < INNER_RADIUS * INNER_RADIUS
                || radiusSquared > OUTER_RADIUS * OUTER_RADIUS) {
            return null;
        }
        if (Math.abs(dx) > Math.abs(dy)) {
            return dx > 0.0D ? BuilderMode.LINK_STORAGE : BuilderMode.ROTATE;
        }
        return dy > 0.0D ? BuilderMode.FUNNEL : BuilderMode.INTERACT;
    }

    public void render(
            GuiGraphics graphics,
            Font font,
            int mouseX,
            int mouseY,
            BuilderMode currentMode) {
        if (!this.open) {
            return;
        }

        long now = Util.getMillis();
        float progress = animationProgress(now);
        float deltaSeconds = Math.min(
                0.05F,
                Math.max(0L, now - this.lastRenderAtMs) / 1000.0F);
        this.lastRenderAtMs = now;
        BuilderMode hovered = hoveredMode(mouseX, mouseY);
        updateHoverAnimations(hovered, deltaSeconds);
        float alpha = Mth.clamp(progress, 0.0F, 1.0F);
        float distance = Mth.lerp(progress, OPTION_START_DISTANCE, OPTION_DISTANCE);
        float ringRadius = Mth.lerp(progress, 15.0F, 41.0F);

        // 中心保持透明，只留下轻量轨道和定位点，避免轮盘遮住玩家正在观察的世界。
        RtsGuiVectorRenderer.drawRing(
                graphics,
                this.centerX,
                this.centerY,
                ringRadius,
                8.0F,
                ModeWheelStyle.multiplyAlpha(
                        ModeWheelStyle.TRACK_BACKGROUND, alpha).toArgb());
        RtsGuiVectorRenderer.drawRing(
                graphics,
                this.centerX,
                this.centerY,
                ringRadius,
                1.25F,
                ModeWheelStyle.multiplyAlpha(
                        ModeWheelStyle.TRACK_BORDER, alpha).toArgb());
        RtsGuiVectorRenderer.fillDisc(
                graphics,
                this.centerX,
                this.centerY,
                2.0F + progress,
                ModeWheelStyle.multiplyAlpha(
                        ModeWheelStyle.CENTER_DOT, alpha * 0.78F).toArgb());

        drawOption(graphics, BuilderMode.INTERACT, 0, -1, 0,
                distance, currentMode, hovered, alpha, progress);
        drawOption(graphics, BuilderMode.LINK_STORAGE, 1, 0, 1,
                distance, currentMode, hovered, alpha, progress);
        drawOption(graphics, BuilderMode.FUNNEL, 0, 1, 2,
                distance, currentMode, hovered, alpha, progress);
        drawOption(graphics, BuilderMode.ROTATE, -1, 0, 3,
                distance, currentMode, hovered, alpha, progress);

        Component label = Component.translatable(modeTranslationKey(
                hovered == null ? currentMode : hovered));
        drawLabelPill(
                graphics,
                font,
                label.getString(),
                this.centerX,
                this.centerY + 80,
                alpha);
        RtsClientUiUtil.drawCenteredStringNoShadow(
                graphics,
                font,
                Component.translatable("screen.rtsbuilding.mode_wheel.hint").getString(),
                this.centerX,
                this.centerY + 97,
                ModeWheelStyle.multiplyAlpha(
                        ModeWheelStyle.HINT_TEXT, alpha * 0.86F).toArgb());
    }

    private void drawOption(
            GuiGraphics graphics,
            BuilderMode mode,
            int dx,
            int dy,
            int optionIndex,
            float distance,
            BuilderMode currentMode,
            BuilderMode hoveredMode,
            float alpha,
            float openingProgress) {
        int cx = this.centerX + Math.round(dx * distance);
        int cy = this.centerY + Math.round(dy * distance);
        boolean current = mode == currentMode;
        boolean hovered = mode == hoveredMode;
        float hover = this.hoverProgress[optionIndex];
        float scale = (0.72F + openingProgress * 0.28F) * (1.0F + hover * 0.12F);
        float radius = OPTION_RADIUS * scale;
        int border = ModeWheelStyle.multiplyAlpha(
                ModeWheelStyle.optionBorder(current, hover), alpha).toArgb();
        int background = ModeWheelStyle.multiplyAlpha(
                ModeWheelStyle.optionBackground(current, hover), alpha).toArgb();
        RtsGuiVectorRenderer.fillDisc(
                graphics, cx, cy, radius + 1.25F, border);
        RtsGuiVectorRenderer.fillDisc(
                graphics, cx, cy, Math.max(4.0F, radius - 1.25F),
                background);

        TopBarTypes.TopBarButtonId iconId = modeButtonId(mode);
        UiTextureState textureState = current
                ? UiTextureState.ACTIVE
                : hovered ? UiTextureState.HOVER
                : UiTextureState.INACTIVE;
        ResourceLocation texture = TopBarIconRenderer.texture(iconId, textureState);
        if (texture != null) {
            int iconSize = Math.max(12, Math.round(ICON_SIZE * scale));
            int iconX = cx - iconSize / 2;
            int iconY = cy - iconSize / 2;
            graphics.blit(texture, iconX, iconY, 0, 0,
                    iconSize, iconSize, iconSize, iconSize);
        }
    }

    private void updateHoverAnimations(BuilderMode hoveredMode, float deltaSeconds) {
        float amount = Mth.clamp(deltaSeconds * HOVER_SPEED_PER_SECOND, 0.0F, 1.0F);
        BuilderMode[] modes = {
                BuilderMode.INTERACT,
                BuilderMode.LINK_STORAGE,
                BuilderMode.FUNNEL,
                BuilderMode.ROTATE
        };
        for (int i = 0; i < this.hoverProgress.length; i++) {
            float target = modes[i] == hoveredMode ? 1.0F : 0.0F;
            this.hoverProgress[i] = Mth.lerp(amount, this.hoverProgress[i], target);
        }
    }

    private float animationProgress(long now) {
        float raw = Mth.clamp(
                (now - this.transitionStartedAtMs) / (float) OPEN_DURATION_MS,
                0.0F,
                1.0F);
        float remaining = 1.0F - raw;
        return 1.0F - remaining * remaining * remaining;
    }

    private static void drawLabelPill(
            GuiGraphics graphics,
            Font font,
            String text,
            int centerX,
            int centerY,
            float alpha) {
        int width = font.width(text) + 16;
        RtsGuiVectorRenderer.fillCapsule(
                graphics,
                centerX - width / 2,
                centerX + (width + 1) / 2,
                centerY,
                15.0F,
                ModeWheelStyle.multiplyAlpha(
                        ModeWheelStyle.LABEL_BACKGROUND, alpha * 0.88F).toArgb());
        RtsClientUiUtil.drawCenteredStringNoShadow(
                graphics,
                font,
                text,
                centerX,
                centerY - 4,
                ModeWheelStyle.multiplyAlpha(
                        ModeWheelStyle.LABEL_TEXT, alpha).toArgb());
    }

    private static TopBarTypes.TopBarButtonId modeButtonId(BuilderMode mode) {
        return switch (mode) {
            case LINK_STORAGE -> TopBarTypes.TopBarButtonId.LINK;
            case FUNNEL -> TopBarTypes.TopBarButtonId.FUNNEL;
            case ROTATE -> TopBarTypes.TopBarButtonId.ROTATE;
            default -> TopBarTypes.TopBarButtonId.INTERACT;
        };
    }

    private static String modeTranslationKey(BuilderMode mode) {
        return switch (mode) {
            case LINK_STORAGE -> "screen.rtsbuilding.mode.link_storage";
            case FUNNEL -> "screen.rtsbuilding.mode.funnel";
            case ROTATE -> "screen.rtsbuilding.mode.rotate";
            default -> "screen.rtsbuilding.mode.interact";
        };
    }

}
