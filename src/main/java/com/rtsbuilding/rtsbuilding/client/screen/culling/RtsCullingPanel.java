package com.rtsbuilding.rtsbuilding.client.screen.culling;

import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    private static final RtsCullingPanelLayout LAYOUT = new RtsCullingPanelLayout();

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
    protected void renderContent(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int x = LAYOUT.contentLeft(contentX());
        int w = LAYOUT.contentInnerWidth(contentWidth());
        drawLine(g, text("screen.rtsbuilding.culling.count", manager.boxes().size()),
                x, LAYOUT.countRowY(contentY()), TEXT, w);
        drawLine(g, phaseText(), x, LAYOUT.phaseRowY(contentY()), ACCENT, w);

        RtsCullingBox selected = manager.selectedBox().orElse(null);
        if (selected == null) {
            drawLine(g, text("screen.rtsbuilding.culling.no_selection"),
                    x, LAYOUT.selectedRowY(contentY()), MUTED, w);
            return;
        }

        int deleteX = LAYOUT.deleteButtonX(x, w);
        drawLine(g, text("screen.rtsbuilding.culling.selected", selected.id()),
                x, LAYOUT.selectedRowY(contentY()), TEXT, LAYOUT.selectedTextWidth(w));
        drawWideButton(g, deleteX, LAYOUT.deleteButtonRowY(contentY()),
                text("screen.rtsbuilding.culling.delete_button"),
                isDeleteButtonHovered(mouseX, mouseY));
        drawLine(g, text("screen.rtsbuilding.culling.dimensions",
                        selected.width(), selected.height(), selected.depth()),
                x, LAYOUT.dimensionRowY(contentY()), TEXT, w);
        drawLine(g, text("screen.rtsbuilding.culling.delete_hint"),
                x, LAYOUT.hintRowY(contentY()), MUTED, w);
    }

    private void drawWideButton(GuiGraphicsExtractor g, int x, int y, String label, boolean hovered) {
        RtsClientUiUtil.drawPanelFrame(g, x, LAYOUT.buttonTop(y),
                RtsCullingPanelLayout.DELETE_BUTTON_WIDTH, LAYOUT.buttonHeight(),
                hovered ? 0xFF9A3340 : 0xFF742833,
                hovered ? 0xFFFFD1D7 : 0xFFFFA2AE,
                0xFF0B1017);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, screen.font(),
                screen.trimToWidth(label, LAYOUT.deleteButtonTextWidth()),
                x + RtsCullingPanelLayout.DELETE_BUTTON_WIDTH / 2, LAYOUT.buttonTextY(y), TEXT);
    }

    private boolean isDeleteButtonHovered(int mouseX, int mouseY) {
        if (!this.mouseHovering || manager.selectedId() < 0) {
            return false;
        }
        int x = LAYOUT.deleteButtonX(LAYOUT.contentLeft(contentX()), LAYOUT.contentInnerWidth(contentWidth()));
        return LAYOUT.containsButton(mouseX, mouseY, x,
                LAYOUT.deleteButtonRowY(contentY()), RtsCullingPanelLayout.DELETE_BUTTON_WIDTH);
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (deleteButtonAt(mouseX, mouseY)) {
            manager.deleteSelected();
        }
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        return false;
    }

    @Override
    protected boolean handleWindowKeyPressed(int keyCode, int scanCode, int modifiers) {
        boolean handled = manager.handleKey(keyCode, scanCode, modifiers);
        if (handled && !manager.isManagementMode()) {
            setOpen(false);
        }
        return handled;
    }

    @Override
    protected void onClose() {
        manager.closeManagementMode();
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
        return RtsCullingPanelLayout.DEFAULT_WIDTH;
    }

    @Override
    protected int getDefaultHeight() {
        return RtsCullingPanelLayout.DEFAULT_HEIGHT;
    }

    @Override
    protected void computeDefaultPosition() {
        this.windowX = LAYOUT.defaultWindowX();
        this.windowY = screen == null ? LAYOUT.fallbackWindowY() : LAYOUT.defaultWindowY(screen.topBarBottomY());
    }

    @Override
    protected boolean canShowWindow() {
        return manager.isManagementMode();
    }

    private boolean deleteButtonAt(double mouseX, double mouseY) {
        if (manager.selectedId() < 0) {
            return false;
        }
        int x = LAYOUT.deleteButtonX(LAYOUT.contentLeft(contentX()), LAYOUT.contentInnerWidth(contentWidth()));
        return LAYOUT.containsButton(mouseX, mouseY, x,
                LAYOUT.deleteButtonRowY(contentY()), RtsCullingPanelLayout.DELETE_BUTTON_WIDTH);
    }

    private void drawLine(GuiGraphicsExtractor g, String label, int x, int y, int color, int width) {
        g .text(screen.font(), screen.trimToWidth(label, width), x, y, color, false);
    }

    private String phaseText() {
        return switch (manager.phase()) {
            case IDLE -> text("screen.rtsbuilding.culling.phase.idle");
            case NEED_SECOND -> text("screen.rtsbuilding.culling.phase.second");
            case NEED_HEIGHT -> text("screen.rtsbuilding.culling.phase.height", manager.previewHeight());
        };
    }

    private String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
