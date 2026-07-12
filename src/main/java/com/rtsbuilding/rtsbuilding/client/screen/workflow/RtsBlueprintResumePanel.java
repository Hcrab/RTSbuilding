package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsResumePlacementActionPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsBlueprintResumeScanPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * 蓝图搁置作业的材料扫描结果面板。
 * <p>
 * 显示蓝图剩余方块所需的材料清单（每种材料的需求/可用/缺少），
 * 并提供「重启」按钮以手动恢复蓝图放置。
 */
public final class RtsBlueprintResumePanel extends RtsWindowPanel {

    private static final int PANEL_W = 280;
    private static final int PANEL_H = 240;
    private static final int PADDING = 8;
    private static final int ROW_H = 18;
    private static final int BTN_H = 20;
    private static final int MAX_VISIBLE_ROWS = 8;

    private S2CRtsBlueprintResumeScanPayload scanData;
    private int workflowEntryId = -1;
    private int scrollOffset;

    public RtsBlueprintResumePanel() {
    }

    @Override
    protected Component getTitle() {
        return Component.translatable("screen.rtsbuilding.workflow.blueprint_resume.title");
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
    public void openWithData(S2CRtsBlueprintResumeScanPayload data) {
        this.scanData = data;
        this.workflowEntryId = data.workflowEntryId();
        this.scrollOffset = 0;
        setOpen(true);
    }

    @Override
    public void setOpen(boolean open) {
        super.setOpen(open);
        if (!open) {
            this.scanData = null;
            this.workflowEntryId = -1;
            this.scrollOffset = 0;
        }
    }

    @Override
    protected boolean handleContentScroll(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scanData == null) return false;
        int maxScroll = Math.max(0, scanData.itemIds().size() - MAX_VISIBLE_ROWS);
        if (scrollY < 0) {
            scrollOffset = Math.max(0, scrollOffset - 1);
        } else if (scrollY > 0) {
            scrollOffset = Math.min(maxScroll, scrollOffset + 1);
        }
        return true;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (scanData == null) return;

        Font font = this.screen.font();
        int x = contentX() + PADDING;
        int y = contentY() + PADDING;
        int maxW = contentWidth() - PADDING * 2;

        // ── 标题：进度统计 ──
        String progress = text("screen.rtsbuilding.workflow.blueprint_resume.progress",
                scanData.completedCount(), scanData.totalCount(),
                scanData.totalCount() - scanData.completedCount());
        g.drawString(font, progress, x, y, 0xFFE7C46A, false);
        y += ROW_H;

        // ── 分隔线 ──
        g.fill(x, y, x + maxW, y + 1, 0xFF405064);
        y += 4;

        // ── 列标题 ──
        g.drawString(font, text("screen.rtsbuilding.workflow.blueprint_resume.material"),
                x, y, 0xAAB0C0D0, false);
        int col2X = x + maxW - 130;
        int col3X = x + maxW - 70;
        g.drawString(font, text("screen.rtsbuilding.workflow.blueprint_resume.required"),
                col2X, y, 0xAAB0C0D0, false);
        g.drawString(font, text("screen.rtsbuilding.workflow.blueprint_resume.available"),
                col3X, y, 0xAAB0C0D0, false);
        y += ROW_H;

        // ── 材料列表（按 scrollOffset 偏移） ──
        int listEnd = Math.min(scanData.itemIds().size(), scrollOffset + MAX_VISIBLE_ROWS);
        for (int i = scrollOffset; i < listEnd; i++) {
            String itemId = scanData.itemIds().get(i);
            String itemLabel = scanData.itemLabels().get(i);
            int req = scanData.required().get(i);
            long avail = scanData.available().get(i);
            long missing = Math.max(0, req - avail);
            boolean enough = avail >= req;

            // 物品图标
            ItemStack displayStack = ItemStack.EMPTY;
            ResourceLocation id = ResourceLocation.tryParse(itemId);
            if (id != null && BuiltInRegistries.ITEM.containsKey(id)) {
                displayStack = new ItemStack(BuiltInRegistries.ITEM.get(id));
            }
            if (!displayStack.isEmpty()) {
                g.renderItem(displayStack, x, y);
                g.drawString(font, truncateLabel(itemLabel, font, 100), x + 18, y + 4, 0xFFFFFF, false);
            } else {
                g.drawString(font, itemLabel, x, y + 4, 0xFFFFFF, false);
            }

            g.drawString(font, String.valueOf(req), col2X, y + 4, 0xEAF2FF, false);
            int color = enough ? 0x88F4BE : 0xFF7070;
            g.drawString(font, enough ? String.valueOf(avail)
                            : text("screen.rtsbuilding.workflow.blueprint_resume.missing", missing),
                    col3X, y + 4, color, false);

            y += ROW_H;
        }

        // ── 分隔线 ──
        y = contentY() + contentHeight() - BTN_H - PADDING - 4;
        g.fill(x, y, x + maxW, y + 1, 0xFF405064);
        y += 6;

        // ── 重启按钮 ──
        boolean canResume = allMaterialsEnough();
        int btnY = contentY() + contentHeight() - BTN_H - PADDING;
        boolean resumeHovered = canResume && isInsideBtn(mouseX, mouseY, x, btnY, maxW, BTN_H);
        int resumeBg = canResume
                ? (resumeHovered ? 0xCC3AA156 : 0xCC2C873F)
                : 0xCC444444;
        int resumeBorder = canResume ? 0xFF74E88C : 0xFF666666;
        RtsClientUiUtil.drawPanelFrame(g, x, btnY, maxW, BTN_H,
                resumeBg, resumeBorder, 0xFF1A2A1A);
        int resumeColor = canResume ? 0xFFFFFF : 0xFF888888;
        String btnText = canResume
                ? text("screen.rtsbuilding.workflow.blueprint_resume.restart")
                : text("screen.rtsbuilding.workflow.blueprint_resume.insufficient_materials");
        RtsClientUiUtil.drawCenteredStringNoShadow(g, font, btnText,
                x + maxW / 2, btnY + 4, resumeColor);
    }

    @Override
    protected void handleContentClick(double mouseX, double mouseY, int button) {
        if (button != 0 || scanData == null) return;

        int x = contentX() + PADDING;
        int maxW = contentWidth() - PADDING * 2;
        int btnY = contentY() + contentHeight() - BTN_H - PADDING;

        // 重启按钮（材料不足时不可点击）
        if (allMaterialsEnough() && isInsideBtn(mouseX, mouseY, x, btnY, maxW, BTN_H)) {
            PacketDistributor.sendToServer(new C2SRtsResumePlacementActionPayload(0, this.workflowEntryId));
            setOpen(false);
        }
    }

    private static String truncateLabel(String label, Font font, int maxPx) {
        if (font.width(label) <= maxPx) return label;
        while (!label.isEmpty() && font.width(label + "…") > maxPx) {
            label = label.substring(0, label.length() - 1);
        }
        return label + "…";
    }

    private static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    private boolean isInsideBtn(double mx, double my, double bx, double by, double bw, double bh) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    /**
     * 检查扫描数据中所有材料是否都满足需求。
     */
    private boolean allMaterialsEnough() {
        if (scanData == null) return false;
        List<Integer> required = scanData.required();
        List<Long> available = scanData.available();
        for (int i = 0; i < required.size(); i++) {
            if (available.get(i) < required.get(i)) return false;
        }
        return true;
    }

    private final List<PersistableProperty> properties = List.of(
            PersistableProperty.bounds("blueprint_resume", this)
    );

    @Override
    public List<PersistableProperty> persistableProperties() {
        return properties;
    }
}
