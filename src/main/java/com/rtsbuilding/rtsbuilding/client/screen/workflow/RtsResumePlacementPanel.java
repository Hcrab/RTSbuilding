package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsResumePlacementActionPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsResumePlacementScanPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * 搁置放置作业重启面板。
 * <p>
 * 显示扫描结果（总剩余、已放置、冲突格、库存可用数），
 * 并提供「跳过冲突格后重启」「覆盖放置」「取消」三项操作。
 */
public final class RtsResumePlacementPanel extends RtsWindowPanel {

    private static final int PANEL_W = 260;
    private static final int PANEL_H = 200;
    private static final int LINE_H = 12;
    private static final int PADDING = 8;
    private static final int BTN_H = 20;

    private S2CRtsResumePlacementScanPayload scanData;
    private int workflowEntryId = -1;

    public RtsResumePlacementPanel() {
    }

    @Override
    protected Component getTitle() {
        return Component.translatable("screen.rtsbuilding.workflow.resume_placement.title");
    }

    @Override
    protected int getDefaultWidth() {
        return PANEL_W;
    }

    @Override
    protected int getDefaultHeight() {
        return PANEL_H;
    }

    @Override
    protected void computeDefaultPosition() {
        if (this.screen == null) return;
        this.windowX = (this.screen.width - PANEL_W) / 2;
        this.windowY = (this.screen.height - PANEL_H) / 2;
    }

    @Override
    protected boolean canShowWindow() {
        return this.scanData != null;
    }

    @Override
    protected boolean shouldClipContent() {
        return false;
    }

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        super.init(screen, controller);
        this.draggable = true;
        this.resizable = false;
        this.closable = true;
        setOpen(false);
    }

    /**
     * Loads scan data and opens the panel.
     */
    public void openWithData(S2CRtsResumePlacementScanPayload data) {
        this.scanData = data;
        this.workflowEntryId = data.workflowEntryId();
        setOpen(true);
    }

    @Override
    public void setOpen(boolean open) {
        super.setOpen(open);
        if (!open) {
            this.scanData = null;
            this.workflowEntryId = -1;
            if (this.controller != null) {
                this.controller.clearResumeScanData();
            }
        }
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (scanData == null) return;

        Font font = this.screen.font();
        int x = contentX() + PADDING;
        int y = contentY() + PADDING;
        int maxW = contentWidth() - PADDING * 2;

        // 物品图标 + 名称
        ItemStack displayStack = ItemStack.EMPTY;
        ResourceLocation id = ResourceLocation.tryParse(scanData.itemId());
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            displayStack = new ItemStack(BuiltInRegistries.ITEM.get(id));
        }
        if (!displayStack.isEmpty()) {
            g.renderItem(displayStack, x, y);
            g.drawString(font, scanData.itemLabel(), x + 20, y + 4, 0xFFFFFF, false);
        } else {
            g.drawString(font, scanData.itemId(), x, y + 4, 0xFFFFFF, false);
        }
        y += 22;

        // 分割线
        g.fill(x, y, x + maxW, y + 1, 0xFF405064);
        y += 6;

        // 统计信息
        int col1X = x;
        int col2X = x + maxW - 80;

        drawStat(g, font, col1X, col2X, y,
                text("screen.rtsbuilding.workflow.resume_placement.remaining"),
                String.valueOf(scanData.totalRemaining()), 0xEAF2FF);
        y += LINE_H;

        drawStat(g, font, col1X, col2X, y,
                text("screen.rtsbuilding.workflow.resume_placement.already_placed"),
                String.valueOf(scanData.alreadyPlacedCount()), 0x88BEF4);
        y += LINE_H;

        if (scanData.conflictCount() > 0) {
            drawStat(g, font, col1X, col2X, y,
                    text("screen.rtsbuilding.workflow.resume_placement.conflicts"),
                    String.valueOf(scanData.conflictCount()), 0xFFC070);
            y += LINE_H;
        }

        drawStat(g, font, col1X, col2X, y,
                text("screen.rtsbuilding.workflow.resume_placement.available"),
                String.valueOf(scanData.availableItems()), 0x88F4BE);
        y += LINE_H;

        drawStat(g, font, col1X, col2X, y,
                text("screen.rtsbuilding.workflow.resume_placement.needed"),
                String.valueOf(scanData.neededItems()), 0xEAF2FF);
        y += LINE_H;

        boolean enough = scanData.missingItems() <= 0;
        if (enough) {
            drawStat(g, font, col1X, col2X, y,
                    text("screen.rtsbuilding.workflow.resume_placement.missing"),
                    text("screen.rtsbuilding.workflow.resume_placement.enough"), 0x88F4BE);
        } else {
            drawStat(g, font, col1X, col2X, y,
                    text("screen.rtsbuilding.workflow.resume_placement.missing"),
                    String.valueOf(scanData.missingItems()), 0xFF7070);
        }
        y += LINE_H + 4;

        // 分隔线
        g.fill(x, y, x + maxW, y + 1, 0xFF405064);
        y += 8;

        // 按钮区域
        boolean hasConflicts = scanData.conflictCount() > 0;
        int btnY = contentY() + contentHeight() - BTN_H - PADDING;

        if (hasConflicts) {
            // 有冲突：跳过 | 覆盖（两个按钮）
            int btnW = (maxW - 2) / 2;

            // 1. 跳过
            boolean skipHovered = enough && isInsideBtn(mouseX, mouseY, x, btnY, btnW, BTN_H);
            int skipBg;
            if (enough) {
                skipBg = skipHovered ? 0xCC3A6A3A : 0xCC2A4A2A;
            } else {
                skipBg = 0xCC444444;
            }
            RtsClientUiUtil.drawPanelFrame(g, x, btnY, btnW, BTN_H,
                    skipBg, enough ? 0xFF74E88C : 0xFF666666, 0xFF1A2A1A);
            RtsClientUiUtil.drawCenteredStringNoShadow(g, font, enough
                            ? text("screen.rtsbuilding.workflow.resume_placement.skip")
                            : text("screen.rtsbuilding.workflow.insufficient_items"),
                    x + btnW / 2, btnY + 4, enough ? 0xFFFFFF : 0x888888);

            // 2. 覆盖
            int overwriteX = x + btnW + 2;
            boolean overwriteHovered = enough && isInsideBtn(mouseX, mouseY, overwriteX, btnY, btnW, BTN_H);
            int overwriteBg;
            if (enough) {
                overwriteBg = overwriteHovered ? 0xCC6A4A2A : 0xCC4A3A1A;
            } else {
                overwriteBg = 0xCC444444;
            }
            RtsClientUiUtil.drawPanelFrame(g, overwriteX, btnY, btnW, BTN_H,
                    overwriteBg, enough ? 0xFFE7C46A : 0xFF666666, enough ? 0xFF2A1A0A : 0xFF1A1A1A);
            RtsClientUiUtil.drawCenteredStringNoShadow(g, font, enough
                            ? text("screen.rtsbuilding.workflow.resume_placement.overwrite")
                            : text("screen.rtsbuilding.workflow.insufficient_items"),
                    overwriteX + btnW / 2, btnY + 4, enough ? 0xFFFFFF : 0x888888);
        } else {
            // 无冲突：重启（单个按钮，满宽）
            boolean resumeHovered = enough && isInsideBtn(mouseX, mouseY, x, btnY, maxW, BTN_H);
            int resumeBg;
            if (enough) {
                resumeBg = resumeHovered ? 0xCC3AA156 : 0xCC2C873F;
            } else {
                resumeBg = 0xCC444444;
            }
            RtsClientUiUtil.drawPanelFrame(g, x, btnY, maxW, BTN_H,
                    resumeBg, enough ? 0xFF74E88C : 0xFF666666, 0xFF1A2A1A);
            RtsClientUiUtil.drawCenteredStringNoShadow(g, font,
                    enough ? text("screen.rtsbuilding.workflow.resume_placement.restart")
                            : text("screen.rtsbuilding.workflow.insufficient_items"),
                    x + maxW / 2, btnY + 4,
                    enough ? 0xFFFFFF : 0x888888);
        }
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0 || scanData == null) return;

        int x = contentX() + PADDING;
        int maxW = contentWidth() - PADDING * 2;
        int btnY = contentY() + contentHeight() - BTN_H - PADDING;

        boolean hasConflicts = scanData.conflictCount() > 0;
        boolean enough = scanData.missingItems() <= 0;

        if (hasConflicts && enough) {
            int btnW = (maxW - 2) / 2;
            // 跳过按钮
            if (isInsideBtn(mouseX, mouseY, x, btnY, btnW, BTN_H)) {
                sendAction(0); // SKIP
                return;
            }
            // 覆盖按钮
            if (isInsideBtn(mouseX, mouseY, x + btnW + 2, btnY, btnW, BTN_H)) {
                sendAction(1); // OVERWRITE
                return;
            }
        }

        if (!hasConflicts && enough) {
            // 重启按钮（满宽，无冲突时）
            if (isInsideBtn(mouseX, mouseY, x, btnY, maxW, BTN_H)) {
                sendAction(0);
            }
        }
    }

    private void sendAction(int strategy) {
        PacketDistributor.sendToServer(new C2SRtsResumePlacementActionPayload(strategy, this.workflowEntryId));
        setOpen(false);
    }

    private static void drawStat(GuiGraphics g, Font font, int col1X, int col2X, int y,
                                  String label, String value, int valueColor) {
        g.drawString(font, label, col1X, y, 0xAAB0C0D0, false);
        g.drawString(font, value, col2X, y, valueColor, false);
    }

    private static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private boolean isInsideBtn(double mx, double my, double bx, double by, double bw, double bh) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    private final List<PersistableProperty> properties = List.of(
            PersistableProperty.bounds("resume_placement", this)
    );

    @Override
    public List<PersistableProperty> persistableProperties() {
        return properties;
    }
}
