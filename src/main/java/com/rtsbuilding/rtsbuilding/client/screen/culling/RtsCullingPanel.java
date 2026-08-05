package com.rtsbuilding.rtsbuilding.client.screen.culling;

import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.uicore.culling.CullingUiAction;
import com.rtsbuilding.rtsbuilding.uicore.culling.CullingUiState;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.CullingWindowChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.CullingWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.CullingWindowStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiCanvas2D;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import com.rtsbuilding.rtsbuilding.platform.render.GlStateManager;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.client.resources.I18n;
import org.lwjgl.opengl.GL11;

/**
 * 范围剔除的紧凑状态窗口。
 *
 * <p>窗口只显示当前步骤、选中盒尺寸和删除入口；主要编辑交互放在世界空间的轴向箭头上。
 * 这样玩家看着盒子就能调整剔除范围，面板不会再用大面积空白打断视线。</p>
 */
public final class RtsCullingPanel extends RtsWindowPanel {
    private final RtsCullingManager manager;

    public RtsCullingPanel(RtsCullingManager manager) {
        this.manager = manager;
        this.closable = true;
        this.resizable = false;
    }

    public void open() {
        setOpen(true);
    }

    @Override
    protected void renderContent(LegacyGuiGraphics g, int mouseX, int mouseY, float partialTick) {
        CullingUiState state = CullingUiAdapter.snapshot(manager);
        int x = CullingWindowLayout.contentLeft(contentX());
        int w = CullingWindowLayout.contentInnerWidth(contentWidth());
        drawLine(g, text("screen.rtsbuilding.culling.count", state.boxCount),
                x, CullingWindowLayout.countRowY(contentY()), CullingWindowStyle.PRIMARY_TEXT, w);
        drawLine(g, phaseText(state), x, CullingWindowLayout.phaseRowY(contentY()),
                CullingWindowStyle.PHASE_TEXT, w);

        if (!state.hasSelection()) {
            drawLine(g, text("screen.rtsbuilding.culling.no_selection"),
                    x, CullingWindowLayout.selectedRowY(contentY()),
                    CullingWindowStyle.MUTED_TEXT, w);
            return;
        }

        int deleteX = CullingWindowLayout.deleteButtonX(x, w);
        drawLine(g, text("screen.rtsbuilding.culling.selected", state.selectedId),
                x, CullingWindowLayout.selectedRowY(contentY()), CullingWindowStyle.PRIMARY_TEXT,
                CullingWindowLayout.selectedTextWidth(w));
        drawWideButton(g, deleteX, CullingWindowLayout.deleteButtonRowY(contentY()),
                text("screen.rtsbuilding.culling.delete_button"),
                isDeleteButtonHovered(mouseX, mouseY));
        drawLine(g, text("screen.rtsbuilding.culling.dimensions",
                        state.width, state.height, state.depth),
                x, CullingWindowLayout.dimensionRowY(contentY()),
                CullingWindowStyle.PRIMARY_TEXT, w);
        drawLine(g, text("screen.rtsbuilding.culling.delete_hint"),
                x, CullingWindowLayout.hintRowY(contentY()), CullingWindowStyle.MUTED_TEXT, w);
    }

    private void drawWideButton(LegacyGuiGraphics g, int x, int y, String label, boolean hovered) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        CullingWindowChromeRenderer.renderDeleteButton(
                new LegacyCanvas(g, font),
                new UiRect(x, CullingWindowLayout.buttonTop(y),
                        CullingWindowLayout.DELETE_BUTTON_WIDTH,
                        CullingWindowLayout.buttonHeight()),
                hovered);
        g.drawCenteredString(font,
                font.trimStringToWidth(label, CullingWindowLayout.deleteButtonTextWidth()),
                x + CullingWindowLayout.DELETE_BUTTON_WIDTH / 2,
                CullingWindowLayout.buttonTextY(y), CullingWindowStyle.PRIMARY_TEXT.toArgb());
    }

    private boolean isDeleteButtonHovered(int mouseX, int mouseY) {
        if (!this.mouseHovering || manager.selectedId() < 0) {
            return false;
        }
        int x = CullingWindowLayout.deleteButtonX(CullingWindowLayout.contentLeft(contentX()),
                CullingWindowLayout.contentInnerWidth(contentWidth()));
        return CullingWindowLayout.containsDelete(mouseX, mouseY, x,
                CullingWindowLayout.deleteButtonRowY(contentY()));
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (deleteButtonAt(mouseX, mouseY)) {
            CullingUiAdapter.dispatch(manager,
                    CullingUiAction.simple(CullingUiAction.Type.DELETE_SELECTED));
        }
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    @Override
    protected boolean handleWindowKeyPressed(int keyCode, int scanCode, int modifiers) {
        boolean handled = CullingUiAdapter.handleKey(manager, keyCode);
        if (handled && !manager.isManagementMode()) {
            setOpen(false);
        }
        return handled;
    }

    @Override
    protected void onClose() {
        CullingUiAdapter.dispatch(manager, CullingUiAction.simple(CullingUiAction.Type.CLOSE));
        if (screen != null) {
            screen.persistUiState();
        }
    }

    @Override
    protected IChatComponent getTitle() {
        return new ChatComponentTranslation("screen.rtsbuilding.culling.title");
    }

    @Override
    protected int getDefaultWidth() {
        return CullingWindowLayout.DEFAULT_WIDTH;
    }

    @Override
    protected int getDefaultHeight() {
        return CullingWindowLayout.DEFAULT_HEIGHT;
    }

    @Override
    protected void computeDefaultPosition() {
        this.windowX = CullingWindowLayout.defaultWindowX();
        this.windowY = screen == null ? CullingWindowLayout.fallbackWindowY()
                : CullingWindowLayout.defaultWindowY(screen.topBarBottomY());
    }

    @Override
    protected boolean canShowWindow() {
        return manager.isManagementMode();
    }

    private boolean deleteButtonAt(double mouseX, double mouseY) {
        if (manager.selectedId() < 0) {
            return false;
        }
        int x = CullingWindowLayout.deleteButtonX(CullingWindowLayout.contentLeft(contentX()),
                CullingWindowLayout.contentInnerWidth(contentWidth()));
        return CullingWindowLayout.containsDelete(mouseX, mouseY, x,
                CullingWindowLayout.deleteButtonRowY(contentY()));
    }

    private void drawLine(LegacyGuiGraphics g, String label, int x, int y, UiColor color, int width) {
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        g.drawString(font, font.trimStringToWidth(label, width), x, y, color.toArgb(), false);
    }

    private String phaseText(CullingUiState state) {
        switch (state.phase) {
            case IDLE: return text("screen.rtsbuilding.culling.phase.idle");
            case NEED_SECOND: return text("screen.rtsbuilding.culling.phase.second");
            case NEED_HEIGHT: return text("screen.rtsbuilding.culling.phase.height", state.previewHeight);
            default: throw new AssertionError(state.phase);
        }
    }

    private String text(String key, Object... args) {
        return I18n.format(key, args);
    }

    /** 只供纯 UI Kit chrome 使用的 1.12 立即绘制画布。 */
    private static final class LegacyCanvas implements UiCanvas2D {
        private final LegacyGuiGraphics graphics;
        private final FontRenderer font;
        private LegacyCanvas(LegacyGuiGraphics graphics, FontRenderer font) {
            this.graphics = graphics;
            this.font = font;
        }
        @Override public void fill(UiRect rect, UiColor color) {
            graphics.fill(round(rect.getX()), round(rect.getY()), round(rect.right()), round(rect.bottom()), color.toArgb());
        }
        @Override public void text(String text, double x, double y, UiColor color) {
            graphics.drawString(font, text, round(x), round(y), color.toArgb(), false);
        }
        @Override public void pushClip(UiRect clip) {
            Minecraft mc = Minecraft.getMinecraft();
            ScaledResolution scaled = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
            int factor = scaled.getScaleFactor();
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
            GL11.glScissor(round(clip.getX()) * factor,
                    mc.displayHeight - round(clip.bottom()) * factor,
                    Math.max(0, round(clip.getWidth()) * factor),
                    Math.max(0, round(clip.getHeight()) * factor));
        }
        @Override public void popClip() { GL11.glDisable(GL11.GL_SCISSOR_TEST); }
        @Override public void pushTransform() { GlStateManager.pushMatrix(); }
        @Override public void popTransform() { GlStateManager.popMatrix(); }
        @Override public void translate(double x, double y) { GlStateManager.translate(x, y, 0.0D); }
        @Override public void scale(double x, double y) { GlStateManager.scale(x, y, 1.0D); }
        private static int round(double value) { return (int) Math.round(value); }
    }
}
