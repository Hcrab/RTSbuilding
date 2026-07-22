package com.rtsbuilding.rtsbuilding.client.screen.culling;

import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.culling.CullingUiAction;
import com.rtsbuilding.rtsbuilding.uicore.culling.CullingUiState;
import com.rtsbuilding.rtsbuilding.uikit.layout.CullingWindowLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 范围剔除的紧凑状态窗口。
 *
 * <p>窗口只显示当前步骤、选中盒尺寸和删除入口；主要编辑交互放在世界空间的轴向箭头上。
 * 这样玩家看着盒子就能调整剔除范围，面板不会再用大面积空白打断视线。</p>
 */
public final class RtsCullingPanel extends RtsWindowPanel {
    private static final int TEXT = 0xFFE7F2FF;
    private static final int MUTED = 0xFF9FB2C4;
    private static final int ACCENT = 0xFF8EC8FF;
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
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        CullingUiState state = CullingUiAdapter.snapshot(manager);
        int x = CullingWindowLayout.contentLeft(contentX());
        int w = CullingWindowLayout.contentInnerWidth(contentWidth());
        drawLine(g, text("screen.rtsbuilding.culling.count", state.boxCount),
                x, CullingWindowLayout.countRowY(contentY()), TEXT, w);
        drawLine(g, phaseText(state), x, CullingWindowLayout.phaseRowY(contentY()), ACCENT, w);

        if (!state.hasSelection()) {
            drawLine(g, text("screen.rtsbuilding.culling.no_selection"),
                    x, CullingWindowLayout.selectedRowY(contentY()), MUTED, w);
            return;
        }

        int deleteX = CullingWindowLayout.deleteButtonX(x, w);
        drawLine(g, text("screen.rtsbuilding.culling.selected", state.selectedId),
                x, CullingWindowLayout.selectedRowY(contentY()), TEXT,
                CullingWindowLayout.selectedTextWidth(w));
        drawWideButton(g, deleteX, CullingWindowLayout.deleteButtonRowY(contentY()),
                text("screen.rtsbuilding.culling.delete_button"),
                isDeleteButtonHovered(mouseX, mouseY));
        drawLine(g, text("screen.rtsbuilding.culling.dimensions",
                        state.width, state.height, state.depth),
                x, CullingWindowLayout.dimensionRowY(contentY()), TEXT, w);
        drawLine(g, text("screen.rtsbuilding.culling.delete_hint"),
                x, CullingWindowLayout.hintRowY(contentY()), MUTED, w);
    }

    private void drawWideButton(GuiGraphics g, int x, int y, String label, boolean hovered) {
        RtsClientUiUtil.drawPanelFrame(g, x, CullingWindowLayout.buttonTop(y),
                CullingWindowLayout.DELETE_BUTTON_WIDTH, CullingWindowLayout.buttonHeight(),
                hovered ? 0xFF9A3340 : 0xFF742833,
                hovered ? 0xFFFFD1D7 : 0xFFFFA2AE,
                0xFF0B1017);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(),
                screen.trimToWidth(label, CullingWindowLayout.deleteButtonTextWidth()),
                x + CullingWindowLayout.DELETE_BUTTON_WIDTH / 2,
                CullingWindowLayout.buttonTextY(y), TEXT);
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
    protected Component getTitle() {
        return Component.translatable("screen.rtsbuilding.culling.title");
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

    private void drawLine(GuiGraphics g, String label, int x, int y, int color, int width) {
        g.drawString(screen.font(), screen.trimToWidth(label, width), x, y, color, false);
    }

    private String phaseText(CullingUiState state) {
        return switch (state.phase) {
            case IDLE -> text("screen.rtsbuilding.culling.phase.idle");
            case NEED_SECOND -> text("screen.rtsbuilding.culling.phase.second");
            case NEED_HEIGHT -> text("screen.rtsbuilding.culling.phase.height", state.previewHeight);
        };
    }

    private String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
