package com.rtsbuilding.rtsbuilding.client.screen.mode;

import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarIconRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarTypes;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * 长按 Alt 唤出的四向 RTS 鼠标模式轮盘。
 *
 * <p>本类只管理临时显示状态、命中检测和绘制，不负责真正切换模式。
 * 模式提交仍由 {@code BuilderScreen} 统一处理，因此相机、漏斗、蓝图锁定和
 * 服务端同步不会形成第二套状态。</p>
 */
public final class BuilderModeWheel {
    private static final int OPTION_DISTANCE = 48;
    private static final int OPTION_SIZE = 28;
    private static final int ICON_SIZE = 24;
    private static final int INNER_RADIUS = 16;
    private static final int OUTER_RADIUS = 82;
    private static final int EDGE_PADDING = 94;

    private boolean open;
    private int centerX;
    private int centerY;

    public boolean isOpen() {
        return this.open;
    }

    public void open(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        int maxX = Math.max(EDGE_PADDING, screenWidth - EDGE_PADDING);
        int maxY = Math.max(EDGE_PADDING, screenHeight - EDGE_PADDING);
        this.centerX = Mth.clamp((int) Math.round(mouseX), EDGE_PADDING, maxX);
        this.centerY = Mth.clamp((int) Math.round(mouseY), EDGE_PADDING, maxY);
        this.open = true;
    }

    public void close() {
        this.open = false;
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

        BuilderMode hovered = hoveredMode(mouseX, mouseY);
        fillCircle(graphics, this.centerX, this.centerY, 72, 0xC0161B22);
        fillCircle(graphics, this.centerX, this.centerY, 25, 0xE0242C36);

        drawConnector(graphics, 0, -1);
        drawConnector(graphics, 1, 0);
        drawConnector(graphics, 0, 1);
        drawConnector(graphics, -1, 0);

        drawOption(graphics, BuilderMode.INTERACT, 0, -1, currentMode, hovered);
        drawOption(graphics, BuilderMode.LINK_STORAGE, 1, 0, currentMode, hovered);
        drawOption(graphics, BuilderMode.FUNNEL, 0, 1, currentMode, hovered);
        drawOption(graphics, BuilderMode.ROTATE, -1, 0, currentMode, hovered);

        Component centerLabel = Component.translatable(modeTranslationKey(
                hovered == null ? currentMode : hovered));
        RtsClientUiUtil.drawCenteredStringNoShadow(
                graphics,
                font,
                centerLabel.getString(),
                this.centerX,
                this.centerY - 4,
                hovered == null ? 0xFFD6DFEA : 0xFFFFFFFF);
        RtsClientUiUtil.drawCenteredStringNoShadow(
                graphics,
                font,
                Component.translatable("screen.rtsbuilding.mode_wheel.hint").getString(),
                this.centerX,
                this.centerY + 78,
                0xFFD6DFEA);
    }

    private void drawConnector(GuiGraphics graphics, int dx, int dy) {
        int start = 23;
        int end = OPTION_DISTANCE - OPTION_SIZE / 2;
        int x1 = this.centerX + Math.min(dx * start, dx * end) - (dy == 0 ? 0 : 2);
        int x2 = this.centerX + Math.max(dx * start, dx * end) + (dy == 0 ? 1 : 2);
        int y1 = this.centerY + Math.min(dy * start, dy * end) - (dx == 0 ? 0 : 2);
        int y2 = this.centerY + Math.max(dy * start, dy * end) + (dx == 0 ? 1 : 2);
        graphics.fill(x1, y1, x2, y2, 0xFF4E5D6B);
    }

    private void drawOption(
            GuiGraphics graphics,
            BuilderMode mode,
            int dx,
            int dy,
            BuilderMode currentMode,
            BuilderMode hoveredMode) {
        int cx = this.centerX + dx * OPTION_DISTANCE;
        int cy = this.centerY + dy * OPTION_DISTANCE;
        int x = cx - OPTION_SIZE / 2;
        int y = cy - OPTION_SIZE / 2;
        boolean current = mode == currentMode;
        boolean hovered = mode == hoveredMode;
        int border = hovered ? 0xFFFFD878 : current ? 0xFF8FD4A8 : 0xFF657587;
        int background = hovered ? 0xFF5A4720 : current ? 0xFF244E38 : 0xEE1D242C;
        graphics.fill(x, y, x + OPTION_SIZE, y + OPTION_SIZE, border);
        graphics.fill(x + 2, y + 2, x + OPTION_SIZE - 2, y + OPTION_SIZE - 2, background);

        TopBarTypes.TopBarButtonId iconId = modeButtonId(mode);
        ResourceLocation texture = TopBarIconRenderer.topbarModeTexture(
                iconId, current, hovered, false);
        if (texture != null) {
            int iconX = cx - ICON_SIZE / 2;
            int iconY = cy - ICON_SIZE / 2;
            graphics.blit(texture, iconX, iconY, 0, 0,
                    ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        }
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

    private static void fillCircle(
            GuiGraphics graphics,
            int centerX,
            int centerY,
            int radius,
            int color) {
        for (int y = -radius; y <= radius; y++) {
            int halfWidth = (int) Math.sqrt(radius * radius - y * y);
            graphics.fill(centerX - halfWidth, centerY + y,
                    centerX + halfWidth + 1, centerY + y + 1, color);
        }
    }
}
