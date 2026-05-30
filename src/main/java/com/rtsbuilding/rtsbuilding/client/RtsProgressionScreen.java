package com.rtsbuilding.rtsbuilding.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import com.rtsbuilding.rtsbuilding.progression.RtsIngredientCost;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNode;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNodes;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

public final class RtsProgressionScreen extends Screen {
    private static final int BASE_CARD_W = 96;
    private static final int BASE_CARD_H = 42;
    private static final int BADGE_W = 50;
    private static final int BADGE_H = 14;

    private final Screen parent;
    private final ClientRtsController controller = ClientRtsController.get();
    private final Map<ResourceLocation, NodeRect> nodeRects = new HashMap<>();
    private double zoom = 1.18D;
    private boolean draggingZoom;
    private boolean draggingPan;
    private double panX;
    private double panY;
    private int viewportX;
    private int viewportY;
    private int viewportW;
    private int viewportH;
    private int backX;
    private int backY;
    private int backW;
    private int backH;
    private int zoomTrackX;
    private int zoomTrackY;
    private int zoomTrackW;
    private int zoomTrackH;
    private ResourceLocation detailNodeId;
    private int detailPanelX;
    private int detailPanelY;
    private int detailPanelW;
    private int detailPanelH;
    private int detailCloseX;
    private int detailCloseY;
    private int detailCloseW;
    private int detailCloseH;
    private int detailUnlockX;
    private int detailUnlockY;
    private int detailUnlockW;
    private int detailUnlockH;
    private int detailViewH;
    private int detailContentH;
    private int detailScrollY;

    public RtsProgressionScreen(Screen parent) {
        super(Component.translatable("screen.rtsbuilding.progression"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.controller.requestProgressionState();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.nodeRects.clear();
        int panelW = Math.min(640, this.width - 16);
        int panelH = Math.min(390, this.height - 16);
        int x = (this.width - panelW) / 2;
        int y = (this.height - panelH) / 2;

        drawPanel(g, x, y, panelW, panelH);
        g.drawCenteredString(this.font, Component.translatable("screen.rtsbuilding.progression"), x + panelW / 2, y + 12, 0xF4F7FF);
        g.drawString(this.font,
                Component.translatable(this.controller.isProgressionEnabled()
                        ? "screen.rtsbuilding.progression.survival_on"
                        : "screen.rtsbuilding.progression.survival_off"),
                x + 12, y + 30, 0xCFE3F7);

        this.viewportX = x + 12;
        this.viewportY = y + 48;
        this.viewportW = panelW - 24;
        this.viewportH = panelH - 98;
        g.fill(this.viewportX, this.viewportY, this.viewportX + this.viewportW, this.viewportY + this.viewportH, 0xAA0A0F16);
        g.hLine(this.viewportX, this.viewportX + this.viewportW, this.viewportY, 0xFF344251);
        g.hLine(this.viewportX, this.viewportX + this.viewportW, this.viewportY + this.viewportH, 0xFF05070A);
        g.vLine(this.viewportX, this.viewportY, this.viewportY + this.viewportH, 0xFF344251);
        g.vLine(this.viewportX + this.viewportW, this.viewportY, this.viewportY + this.viewportH, 0xFF05070A);
        layoutNodes(this.viewportX + 16, this.viewportY + 14, this.viewportW - 32, this.viewportH - 28);

        g.enableScissor(this.viewportX, this.viewportY, this.viewportX + this.viewportW, this.viewportY + this.viewportH);
        for (RtsProgressionNode node : RtsProgressionNodes.all()) {
            NodeRect to = this.nodeRects.get(node.id());
            if (to == null) {
                continue;
            }
            for (ResourceLocation dependency : node.dependencies()) {
                NodeRect from = this.nodeRects.get(dependency);
                if (from != null) {
                    drawDependencyLine(g, from, to, stateFor(node).lineColor());
                }
            }
        }

        for (RtsProgressionNode node : RtsProgressionNodes.all()) {
            NodeRect rect = this.nodeRects.get(node.id());
            if (rect != null) {
                drawNode(g, node, rect, mouseX, mouseY);
            }
        }
        g.disableScissor();

        this.backW = 54;
        this.backH = 18;
        this.backX = x + panelW - this.backW - 12;
        this.backY = y + panelH - this.backH - 10;
        drawButton(g, this.backX, this.backY, this.backW, this.backH, Component.translatable("gui.rtsbuilding.back"), inside(mouseX, mouseY, this.backX, this.backY, this.backW, this.backH), true);
        drawZoomControl(g, x + 12, y + panelH - 28, panelW - 92, mouseX, mouseY);
        if (this.detailNodeId != null) {
            renderDetailsModal(g, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.detailNodeId != null) {
            return handleDetailsClick(mouseX, mouseY);
        }
        if (inside(mouseX, mouseY, this.zoomTrackX, this.zoomTrackY - 5, this.zoomTrackW, this.zoomTrackH + 10)) {
            this.draggingZoom = true;
            updateZoomFromMouse(mouseX);
            return true;
        }
        if (inside(mouseX, mouseY, this.backX, this.backY, this.backW, this.backH)) {
            this.minecraft.setScreen(this.parent);
            return true;
        }
        boolean insideViewport = inside(mouseX, mouseY, this.viewportX, this.viewportY, this.viewportW, this.viewportH);
        for (RtsProgressionNode node : RtsProgressionNodes.all()) {
            NodeRect rect = this.nodeRects.get(node.id());
            if (rect == null || !insideViewport) {
                continue;
            }
            int bx = rect.x() + rect.w() - BADGE_W - 4;
            int by = rect.y() + rect.h() - BADGE_H - 4;
            if (inside(mouseX, mouseY, bx, by, BADGE_W, BADGE_H)) {
                this.detailNodeId = node.id();
                this.detailScrollY = 0;
                this.controller.requestProgressionState();
                return true;
            }
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && insideViewport) {
            this.draggingPan = true;
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.detailNodeId != null) {
            return true;
        }
        if (this.draggingZoom) {
            updateZoomFromMouse(mouseX);
            return true;
        }
        if (this.draggingPan) {
            this.panX += dragX;
            this.panY += dragY;
            clampPan();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.detailNodeId != null) {
            return true;
        }
        if (this.draggingZoom) {
            this.draggingZoom = false;
            return true;
        }
        if (this.draggingPan) {
            this.draggingPan = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.detailNodeId != null) {
            scrollDetails(scrollY);
            return true;
        }
        this.zoom = Mth.clamp(this.zoom + scrollY * 0.12D, 0.45D, 2.35D);
        clampPan();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (this.detailNodeId != null) {
                this.detailNodeId = null;
                return true;
            }
            this.minecraft.setScreen(this.parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void layoutNodes(int x, int y, int w, int h) {
        int minX = 0;
        int maxX = 5;
        int minY = -1;
        int maxY = 3;
        int cardW = cardW();
        int cardH = cardH();
        double colGap = ((double) Math.max(1, w - cardW) / Math.max(1, maxX - minX)) * this.zoom;
        double rowGap = ((double) Math.max(1, h - cardH) / Math.max(1, maxY - minY)) * this.zoom;
        int fullW = (int) Math.round((maxX - minX) * colGap + cardW);
        int fullH = (int) Math.round((maxY - minY) * rowGap + cardH);
        clampPan(w, h, fullW, fullH);
        int offsetX = (w - fullW) / 2;
        int offsetY = (h - fullH) / 2;
        for (RtsProgressionNode node : RtsProgressionNodes.all()) {
            int nx = x + offsetX + (int) Math.round(this.panX) + (int) Math.round((node.x() - minX) * colGap);
            int ny = y + offsetY + (int) Math.round(this.panY) + (int) Math.round((node.y() - minY) * rowGap);
            this.nodeRects.put(node.id(), new NodeRect(nx, ny, cardW, cardH));
        }
    }

    private void clampPan() {
        int cardW = cardW();
        int cardH = cardH();
        int contentW = Math.max(1, this.viewportW - 32);
        int contentH = Math.max(1, this.viewportH - 28);
        double colGap = ((double) Math.max(1, contentW - cardW) / 5.0D) * this.zoom;
        double rowGap = ((double) Math.max(1, contentH - cardH) / 4.0D) * this.zoom;
        int fullW = (int) Math.round(5 * colGap + cardW);
        int fullH = (int) Math.round(4 * rowGap + cardH);
        clampPan(contentW, contentH, fullW, fullH);
    }

    private void clampPan(int viewportContentW, int viewportContentH, int fullW, int fullH) {
        double marginX = Math.min(180.0D, Math.max(80.0D, viewportContentW / 3.0D));
        double marginY = Math.min(140.0D, Math.max(60.0D, viewportContentH / 3.0D));
        double maxX = Math.max(0.0D, (fullW - viewportContentW) / 2.0D) + marginX;
        double maxY = Math.max(0.0D, (fullH - viewportContentH) / 2.0D) + marginY;
        this.panX = Mth.clamp(this.panX, -maxX, maxX);
        this.panY = Mth.clamp(this.panY, -maxY, maxY);
    }

    private void drawPanel(GuiGraphics g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xF0101720);
        g.fill(x + 3, y + 3, x + w - 3, y + 24, 0xFF2A303A);
        g.hLine(x, x + w, y, 0xFF7B8FA4);
        g.hLine(x, x + w, y + h, 0xFF070A0E);
        g.vLine(x, y, y + h, 0xFF7B8FA4);
        g.vLine(x + w, y, y + h, 0xFF070A0E);
    }

    private void drawDependencyLine(GuiGraphics g, NodeRect from, NodeRect to, int color) {
        int x1 = from.x() + from.w() / 2;
        int y1 = from.y() + from.h() / 2;
        int x2 = to.x() + to.w() / 2;
        int y2 = to.y() + to.h() / 2;
        int midY = (y1 + y2) / 2;
        drawThickVLine(g, x1, y1, midY, color);
        drawThickHLine(g, x1, x2, midY, color);
        drawThickVLine(g, x2, midY, y2, color);
    }

    private void drawNode(GuiGraphics g, RtsProgressionNode node, NodeRect rect, int mouseX, int mouseY) {
        NodeState state = stateFor(node);
        boolean hover = inside(mouseX, mouseY, rect.x(), rect.y(), rect.w(), rect.h());
        int bg = state.bg();
        int border = state.border();
        if (hover) {
            border = 0xFFF0F7FF;
        }
        g.fill(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), bg);
        g.hLine(rect.x(), rect.x() + rect.w(), rect.y(), border);
        g.hLine(rect.x(), rect.x() + rect.w(), rect.y() + rect.h(), 0xFF0A0E13);
        g.vLine(rect.x(), rect.y(), rect.y() + rect.h(), border);
        g.vLine(rect.x() + rect.w(), rect.y(), rect.y() + rect.h(), 0xFF0A0E13);

        g.fill(rect.x() + 4, rect.y() + 4, rect.x() + 9, rect.y() + rect.h() - 4, state.accent());
        g.drawString(this.font, trim(Component.translatable(node.titleKey()).getString(), rect.w() - 18), rect.x() + 14, rect.y() + 5, state.text());
        g.drawString(this.font, Component.translatable(state.labelKey()), rect.x() + 14, rect.y() + 16, state.subtext());
        renderCostIcons(g, RtsProgressionNodes.syncedCostsFor(node), rect.x() + 10, rect.y() + rect.h() - 18, rect.w() - 66);

        int bx = rect.x() + rect.w() - BADGE_W - 4;
        int by = rect.y() + rect.h() - BADGE_H - 4;
        drawButton(g, bx, by, BADGE_W, BADGE_H, Component.translatable(state.actionKey()), hover, state == NodeState.UNLOCKABLE);
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h, Component text, boolean hover, boolean active) {
        int bg = active ? (hover ? 0xDD3F8B58 : 0xDD2C6B47) : 0xDD2A3340;
        int border = active ? 0xFF8BE19A : 0xFF657384;
        g.fill(x, y, x + w, y + h, bg);
        g.hLine(x, x + w, y, border);
        g.hLine(x, x + w, y + h, 0xFF0A0E13);
        g.vLine(x, y, y + h, border);
        g.vLine(x + w, y, y + h, 0xFF0A0E13);
        g.drawCenteredString(this.font, trim(text.getString(), w - 4), x + w / 2, y + 4, active ? 0xF7FFF8 : 0xAEB9C6);
    }

    private boolean isUnlocked(ResourceLocation id) {
        return this.controller.getUnlockedProgressionNodes().contains(id.toString());
    }

    private boolean isUnlockable(ResourceLocation id) {
        return this.controller.getUnlockableProgressionNodes().contains(id.toString());
    }

    private NodeState stateFor(RtsProgressionNode node) {
        if (isUnlocked(node.id())) {
            return NodeState.UNLOCKED;
        }
        if (isUnlockable(node.id())) {
            return NodeState.UNLOCKABLE;
        }
        for (ResourceLocation dependency : node.dependencies()) {
            if (!isUnlocked(dependency)) {
                return NodeState.PREREQ_LOCKED;
            }
        }
        return NodeState.MISSING_MATERIALS;
    }

    private void renderCostIcons(GuiGraphics g, List<RtsIngredientCost> costs, int x, int y, int width) {
        if (costs.isEmpty()) {
            g.drawString(this.font, Component.translatable("screen.rtsbuilding.progression.free"), x, y + 4, 0xBFD2E6);
            return;
        }
        int cursor = x;
        for (RtsIngredientCost cost : costs) {
            if (cursor + 26 > x + width) {
                g.drawString(this.font, "...", cursor, y + 5, 0xBFD2E6);
                return;
            }
            ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(cost.itemId()));
            g.renderItem(stack, cursor, y);
            g.drawString(this.font, "x" + cost.count(), cursor + 15, y + 6, 0xF4F7FF);
            cursor += 34;
        }
    }

    private void renderDetailsModal(GuiGraphics g, int mouseX, int mouseY) {
        RtsProgressionNode node = detailNode();
        if (node == null) {
            this.detailNodeId = null;
            return;
        }

        this.detailPanelW = Math.min(380, Math.max(220, this.width - 48));
        this.detailPanelH = Math.min(292, Math.max(190, this.height - 48));
        this.detailPanelX = (this.width - this.detailPanelW) / 2;
        this.detailPanelY = (this.height - this.detailPanelH) / 2;
        this.detailCloseW = 20;
        this.detailCloseH = 18;
        this.detailCloseX = this.detailPanelX + this.detailPanelW - this.detailCloseW - 8;
        this.detailCloseY = this.detailPanelY + 8;
        this.detailUnlockW = 84;
        this.detailUnlockH = 20;
        this.detailUnlockX = this.detailPanelX + this.detailPanelW - this.detailUnlockW - 12;
        this.detailUnlockY = this.detailPanelY + this.detailPanelH - this.detailUnlockH - 10;
        int contentX = this.detailPanelX + 14;
        int contentY = this.detailPanelY + 72;
        int contentW = this.detailPanelW - 28;
        this.detailViewH = Math.max(40, this.detailUnlockY - contentY - 8);

        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 720.0F);
        g.fill(0, 0, this.width, this.height, 0x99000000);
        drawPanel(g, this.detailPanelX, this.detailPanelY, this.detailPanelW, this.detailPanelH);
        g.drawCenteredString(this.font, Component.translatable("screen.rtsbuilding.progression.details"),
                this.detailPanelX + this.detailPanelW / 2, this.detailPanelY + 12, 0xF4F7FF);
        drawButton(g, this.detailCloseX, this.detailCloseY, this.detailCloseW, this.detailCloseH,
                Component.literal("x"), inside(mouseX, mouseY, this.detailCloseX, this.detailCloseY, this.detailCloseW, this.detailCloseH), false);

        NodeState state = stateFor(node);
        g.drawString(this.font, trim(Component.translatable(node.titleKey()).getString(), this.detailPanelW - 32),
                this.detailPanelX + 14, this.detailPanelY + 34, state.text());
        g.drawString(this.font, Component.translatable(state.labelKey()), this.detailPanelX + 14, this.detailPanelY + 46, state.subtext());
        g.drawString(this.font, trim(Component.translatable(node.descriptionKey()).getString(), this.detailPanelW - 32),
                this.detailPanelX + 14, this.detailPanelY + 58, 0xCFE3F7);

        g.enableScissor(contentX, contentY, contentX + contentW, contentY + this.detailViewH);
        int cursorY = contentY - this.detailScrollY;
        cursorY = drawDetailsMaterials(g, node, contentX, cursorY, contentW);
        cursorY += 6;
        cursorY = drawDetailsPrerequisites(g, node, contentX, cursorY, contentW);
        this.detailContentH = Math.max(0, cursorY - contentY + this.detailScrollY);
        this.detailScrollY = Mth.clamp(this.detailScrollY, 0, Math.max(0, this.detailContentH - this.detailViewH));
        g.disableScissor();
        drawDetailsScrollbar(g, contentX + contentW - 3, contentY);

        boolean canUnlock = canUnlockFromDetails(node);
        drawButton(g, this.detailUnlockX, this.detailUnlockY, this.detailUnlockW, this.detailUnlockH,
                Component.translatable("screen.rtsbuilding.progression.unlock"),
                inside(mouseX, mouseY, this.detailUnlockX, this.detailUnlockY, this.detailUnlockW, this.detailUnlockH),
                canUnlock);
        g.pose().popPose();
    }

    private int drawDetailsMaterials(GuiGraphics g, RtsProgressionNode node, int x, int y, int width) {
        g.drawString(this.font, Component.translatable("screen.rtsbuilding.progression.details.materials"), x, y, 0xFFFFD47A);
        y += 14;
        List<RtsIngredientCost> costs = RtsProgressionNodes.syncedCostsFor(node);
        if (costs.isEmpty()) {
            g.drawString(this.font, Component.translatable("screen.rtsbuilding.progression.details.no_materials"), x + 8, y, 0xAEE8AE);
            return y + 14;
        }
        for (RtsIngredientCost cost : costs) {
            Item item = BuiltInRegistries.ITEM.get(cost.itemId());
            ItemStack stack = new ItemStack(item);
            long have = countPlayerInventory(item);
            boolean enough = have >= cost.count();
            int rowColor = enough ? 0xAEE8AE : 0xFFB0B0;
            g.renderItem(stack, x + 6, y - 2);
            int nameX = x + 28;
            int countW = 80;
            g.drawString(this.font, trim(stack.getHoverName().getString(), width - countW - 36), nameX, y + 2, 0xE6EDF7);
            String countText = Component.translatable("screen.rtsbuilding.progression.details.have_count", have, cost.count()).getString();
            g.drawString(this.font, trim(countText, countW), x + width - countW, y + 2, rowColor);
            String statusKey = enough ? "screen.rtsbuilding.progression.details.available" : "screen.rtsbuilding.progression.details.missing";
            g.drawString(this.font, Component.translatable(statusKey), x + width - countW, y + 12, rowColor);
            y += 24;
        }
        return y;
    }

    private int drawDetailsPrerequisites(GuiGraphics g, RtsProgressionNode node, int x, int y, int width) {
        g.drawString(this.font, Component.translatable("screen.rtsbuilding.progression.details.prerequisites"), x, y, 0xFFFFD47A);
        y += 14;
        List<RtsProgressionNode> missing = missingPrerequisites(node);
        if (missing.isEmpty()) {
            g.drawString(this.font, Component.translatable("screen.rtsbuilding.progression.details.no_prerequisites"), x + 8, y, 0xAEE8AE);
            return y + 14;
        }
        for (RtsProgressionNode dependency : missing) {
            NodeState state = stateFor(dependency);
            String title = Component.translatable(dependency.titleKey()).getString();
            String stateLabel = Component.translatable(state.labelKey()).getString();
            g.drawString(this.font, trim("- " + title + " / " + stateLabel, width - 12), x + 8, y, state.subtext());
            y += 12;
        }
        return y;
    }

    private void drawDetailsScrollbar(GuiGraphics g, int x, int y) {
        int maxScroll = Math.max(0, this.detailContentH - this.detailViewH);
        if (maxScroll <= 0) {
            return;
        }
        g.fill(x, y, x + 2, y + this.detailViewH, 0x77000000);
        int knobH = Math.max(12, this.detailViewH * this.detailViewH / Math.max(this.detailViewH, this.detailContentH));
        int knobY = y + (int) Math.round((this.detailViewH - knobH) * (this.detailScrollY / (double) maxScroll));
        g.fill(x, knobY, x + 2, knobY + knobH, 0xFF8FA5BC);
    }

    private boolean handleDetailsClick(double mouseX, double mouseY) {
        if (inside(mouseX, mouseY, this.detailCloseX, this.detailCloseY, this.detailCloseW, this.detailCloseH)
                || !inside(mouseX, mouseY, this.detailPanelX, this.detailPanelY, this.detailPanelW, this.detailPanelH)) {
            this.detailNodeId = null;
            return true;
        }
        RtsProgressionNode node = detailNode();
        if (node != null
                && inside(mouseX, mouseY, this.detailUnlockX, this.detailUnlockY, this.detailUnlockW, this.detailUnlockH)
                && canUnlockFromDetails(node)) {
            this.controller.unlockProgressionNode(node.id());
            this.controller.requestProgressionState();
            this.detailNodeId = null;
            return true;
        }
        return true;
    }

    private void scrollDetails(double scrollY) {
        int maxScroll = Math.max(0, this.detailContentH - this.detailViewH);
        this.detailScrollY = Mth.clamp(this.detailScrollY - (int) Math.round(scrollY * 18.0D), 0, maxScroll);
    }

    private boolean canUnlockFromDetails(RtsProgressionNode node) {
        return this.controller.isProgressionEnabled()
                && node != null
                && !isUnlocked(node.id())
                && missingPrerequisites(node).isEmpty()
                && hasAllCosts(node);
    }

    private boolean hasAllCosts(RtsProgressionNode node) {
        for (RtsIngredientCost cost : RtsProgressionNodes.syncedCostsFor(node)) {
            if (countPlayerInventory(BuiltInRegistries.ITEM.get(cost.itemId())) < cost.count()) {
                return false;
            }
        }
        return true;
    }

    private long countPlayerInventory(Item item) {
        if (item == null || this.minecraft == null || this.minecraft.player == null) {
            return 0L;
        }
        long count = 0L;
        for (ItemStack stack : this.minecraft.player.getInventory().items) {
            if (!stack.isEmpty() && stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private List<RtsProgressionNode> missingPrerequisites(RtsProgressionNode node) {
        LinkedHashSet<ResourceLocation> missingIds = new LinkedHashSet<>();
        collectMissingPrerequisites(node, missingIds);
        ArrayList<RtsProgressionNode> missing = new ArrayList<>(missingIds.size());
        for (ResourceLocation id : missingIds) {
            RtsProgressionNode dependency = RtsProgressionNodes.get(id);
            if (dependency != null) {
                missing.add(dependency);
            }
        }
        return missing;
    }

    private void collectMissingPrerequisites(RtsProgressionNode node, LinkedHashSet<ResourceLocation> missingIds) {
        if (node == null) {
            return;
        }
        for (ResourceLocation dependency : node.dependencies()) {
            if (!isUnlocked(dependency)) {
                missingIds.add(dependency);
                collectMissingPrerequisites(RtsProgressionNodes.get(dependency), missingIds);
            }
        }
    }

    private RtsProgressionNode detailNode() {
        return this.detailNodeId == null ? null : RtsProgressionNodes.get(this.detailNodeId);
    }

    private boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void drawThickHLine(GuiGraphics g, int x1, int x2, int y, int color) {
        int from = Math.min(x1, x2);
        int to = Math.max(x1, x2);
        g.hLine(from, to, y - 1, color);
        g.hLine(from, to, y, color);
        g.hLine(from, to, y + 1, color);
    }

    private void drawThickVLine(GuiGraphics g, int x, int y1, int y2, int color) {
        int from = Math.min(y1, y2);
        int to = Math.max(y1, y2);
        g.vLine(x - 1, from, to, color);
        g.vLine(x, from, to, color);
        g.vLine(x + 1, from, to, color);
    }

    private void drawZoomControl(GuiGraphics g, int x, int y, int w, int mouseX, int mouseY) {
        this.zoomTrackX = x + 48;
        this.zoomTrackY = y + 7;
        this.zoomTrackW = Math.max(80, w - 86);
        this.zoomTrackH = 4;
        g.drawString(this.font, Component.translatable("screen.rtsbuilding.progression.zoom"), x, y + 3, 0xCFE3F7);
        g.fill(this.zoomTrackX, this.zoomTrackY, this.zoomTrackX + this.zoomTrackW, this.zoomTrackY + this.zoomTrackH, 0xFF0A0E13);
        g.fill(this.zoomTrackX + 1, this.zoomTrackY + 1, this.zoomTrackX + this.zoomTrackW - 1, this.zoomTrackY + this.zoomTrackH - 1, 0xFF4D5D70);
        int knobX = this.zoomTrackX + (int) Math.round(((this.zoom - 0.45D) / (2.35D - 0.45D)) * this.zoomTrackW);
        int knobColor = inside(mouseX, mouseY, knobX - 5, y + 1, 10, 16) || this.draggingZoom ? 0xFFFFD47A : 0xFF79D98D;
        g.fill(knobX - 4, y + 2, knobX + 5, y + 16, knobColor);
        g.drawString(this.font, (int) Math.round(this.zoom * 100.0D) + "%", this.zoomTrackX + this.zoomTrackW + 8, y + 3, 0xCFE3F7);
    }

    private void updateZoomFromMouse(double mouseX) {
        double t = (mouseX - this.zoomTrackX) / Math.max(1.0D, this.zoomTrackW);
        this.zoom = Mth.clamp(0.45D + t * (2.35D - 0.45D), 0.45D, 2.35D);
    }

    private int cardW() {
        return (int) Math.round(BASE_CARD_W * Mth.clamp(this.zoom, 0.86D, 1.10D));
    }

    private int cardH() {
        return (int) Math.round(BASE_CARD_H * Mth.clamp(this.zoom, 0.86D, 1.06D));
    }

    private String trim(String text, int width) {
        return this.font.plainSubstrByWidth(text, width);
    }

    private record NodeRect(int x, int y, int w, int h) {
    }

    private enum NodeState {
        UNLOCKED(0xE01B442A, 0xFF79D98D, 0xFF5FEA72, 0xFFE6FFE9, 0xFFAEE8AE, 0xCC79D98D,
                "screen.rtsbuilding.progression.state_unlocked", "screen.rtsbuilding.progression.unlocked"),
        UNLOCKABLE(0xE03B3018, 0xFFE0B864, 0xFFFFC96B, 0xFFFFE3A2, 0xFFFFCE72, 0xCCE0B864,
                "screen.rtsbuilding.progression.state_unlockable", "screen.rtsbuilding.progression.unlock"),
        MISSING_MATERIALS(0xE02A2532, 0xFFB58A58, 0xFFD89D5C, 0xFFE4C9A6, 0xFFD0A577, 0x88B58A58,
                "screen.rtsbuilding.progression.state_missing_materials", "screen.rtsbuilding.progression.need_items"),
        PREREQ_LOCKED(0xE0172131, 0xFF536477, 0xFF6F8196, 0xFFB7C2CF, 0xFF8895A4, 0x665D7288,
                "screen.rtsbuilding.progression.state_prereq_locked", "screen.rtsbuilding.progression.locked");

        private final int bg;
        private final int border;
        private final int accent;
        private final int text;
        private final int subtext;
        private final int lineColor;
        private final String labelKey;
        private final String actionKey;

        NodeState(int bg, int border, int accent, int text, int subtext, int lineColor, String labelKey, String actionKey) {
            this.bg = bg;
            this.border = border;
            this.accent = accent;
            this.text = text;
            this.subtext = subtext;
            this.lineColor = lineColor;
            this.labelKey = labelKey;
            this.actionKey = actionKey;
        }

        int bg() { return this.bg; }
        int border() { return this.border; }
        int accent() { return this.accent; }
        int text() { return this.text; }
        int subtext() { return this.subtext; }
        int lineColor() { return this.lineColor; }
        String labelKey() { return this.labelKey; }
        String actionKey() { return this.actionKey; }
    }
}
