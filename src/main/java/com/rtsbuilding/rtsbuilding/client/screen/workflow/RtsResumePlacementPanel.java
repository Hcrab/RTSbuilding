package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsResumePlacementScanPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 挂起放置任务的恢复确认面板。
 *
 * <p>玩家从工作流面板点击挂起任务的 R 后打开这里。面板只展示扫描结果和
 * 恢复策略，不直接修改客户端状态，最终结果必须由服务端确认。</p>
 */
public final class RtsResumePlacementPanel extends RtsWindowPanel {
    private static final int PANEL_W = 244;
    private static final int PANEL_H = 178;
    private static final int PADDING = 8;
    private static final int LINE_H = 12;
    private static final int BUTTON_H = 18;

    private S2CRtsResumePlacementScanPayload scanData;

    public RtsResumePlacementPanel() {
        this.draggable = true;
        this.resizable = false;
        this.closable = true;
    }

    @Override
    public void init(BuilderScreen screen, ClientRtsController controller) {
        super.init(screen, controller);
        setOpen(false);
    }

    public void openWithData(S2CRtsResumePlacementScanPayload data) {
        this.scanData = data;
        setOpen(true);
    }

    @Override
    public void setOpen(boolean open) {
        super.setOpen(open);
        if (!open) {
            this.scanData = null;
        }
    }

    @Override
    protected boolean canShowWindow() {
        return super.canShowWindow() && this.scanData != null;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (this.scanData == null) {
            return;
        }
        Font font = this.screen.font();
        int x = contentX() + PADDING;
        int y = contentY() + PADDING;
        int w = contentWidth() - PADDING * 2;

        ItemStack stack = displayStack(this.scanData.itemId());
        if (!stack.isEmpty()) {
            g.renderItem(stack, x, y);
            g.drawString(font, RtsClientUiUtil.trimToWidth(font, this.scanData.itemLabel(), w - 22),
                    x + 22, y + 4, 0xEAF2FF, false);
        } else {
            g.drawString(font, RtsClientUiUtil.trimToWidth(font, this.scanData.itemLabel(), w),
                    x, y + 4, 0xEAF2FF, false);
        }
        y += 23;
        g.fill(x, y, x + w, y + 1, 0xFF405064);
        y += 7;

        drawStat(g, font, x, y, "剩余位置", String.valueOf(this.scanData.totalRemaining()), 0xEAF2FF);
        y += LINE_H;
        drawStat(g, font, x, y, "已存在同方块", String.valueOf(this.scanData.alreadyPlacedCount()), 0x88BEF4);
        y += LINE_H;
        drawStat(g, font, x, y, "冲突格", String.valueOf(this.scanData.conflictCount()),
                this.scanData.conflictCount() > 0 ? 0xFFE7C46A : 0x88F4BE);
        y += LINE_H;
        drawStat(g, font, x, y, "库存可用", String.valueOf(this.scanData.availableItems()), 0x88F4BE);
        y += LINE_H;
        drawStat(g, font, x, y, "实际需要", String.valueOf(this.scanData.neededItems()), 0xEAF2FF);
        y += LINE_H;
        boolean enough = this.scanData.missingItems() <= 0;
        drawStat(g, font, x, y, "仍缺少", enough ? "0" : String.valueOf(this.scanData.missingItems()),
                enough ? 0x88F4BE : 0xFFFF7070);

        int buttonY = contentY() + contentHeight() - BUTTON_H - PADDING;
        if (this.scanData.conflictCount() > 0) {
            int buttonW = (w - 4) / 2;
            drawButton(g, font, x, buttonY, buttonW, "跳过", enough, mouseX, mouseY);
            drawButton(g, font, x + buttonW + 4, buttonY, buttonW, "覆盖", enough, mouseX, mouseY);
        } else {
            drawButton(g, font, x, buttonY, w, "重启", enough, mouseX, mouseY);
        }
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0 || this.scanData == null || this.scanData.missingItems() > 0) {
            return;
        }
        int x = contentX() + PADDING;
        int w = contentWidth() - PADDING * 2;
        int buttonY = contentY() + contentHeight() - BUTTON_H - PADDING;
        if (this.scanData.conflictCount() > 0) {
            int buttonW = (w - 4) / 2;
            if (inside(mouseX, mouseY, x, buttonY, buttonW, BUTTON_H)) {
                sendAction(0);
            } else if (inside(mouseX, mouseY, x + buttonW + 4, buttonY, buttonW, BUTTON_H)) {
                sendAction(1);
            }
        } else if (inside(mouseX, mouseY, x, buttonY, w, BUTTON_H)) {
            sendAction(0);
        }
    }

    @Override
    protected Component getTitle() {
        return Component.literal("恢复放置");
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
        this.windowX = Math.max(8, (this.screen.width - this.windowWidth) / 2);
        this.windowY = Math.max(24, (this.screen.height - this.windowHeight) / 2);
    }

    private void sendAction(int strategy) {
        RtsClientPacketGateway.sendResumePlacementAction(strategy, this.scanData.workflowEntryId());
        setOpen(false);
    }

    private static ItemStack displayStack(String itemId) {
        ResourceLocation id = itemId == null || itemId.isBlank() ? null : ResourceLocation.tryParse(itemId);
        if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
            return new ItemStack(BuiltInRegistries.ITEM.get(id));
        }
        return ItemStack.EMPTY;
    }

    private static void drawStat(GuiGraphics g, Font font, int x, int y, String label, String value, int color) {
        g.drawString(font, label, x, y, 0xAAB0C0D0, false);
        g.drawString(font, value, x + 104, y, color, false);
    }

    private static void drawButton(
            GuiGraphics g, Font font, int x, int y, int w, String text, boolean enabled, int mouseX, int mouseY) {
        boolean hover = enabled && inside(mouseX, mouseY, x, y, w, BUTTON_H);
        int fill = enabled ? (hover ? 0xCC3AA156 : 0xCC2C873F) : 0xCC444444;
        int border = enabled ? 0xFF74E88C : 0xFF666666;
        RtsClientUiUtil.drawPanelFrame(g, x, y, w, BUTTON_H, fill, border, 0xFF122218);
        RtsClientUiUtil.drawCenteredStringNoShadow(g, font, enabled ? text : "物品不足",
                x + w / 2, y + 4, enabled ? 0xFFFFFF : 0x888888);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}
