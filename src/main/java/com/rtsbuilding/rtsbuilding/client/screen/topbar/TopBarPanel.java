package com.rtsbuilding.rtsbuilding.client.screen.topbar;

import com.rtsbuilding.rtsbuilding.client.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.ClientRtsController;
import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNodes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.*;

public final class TopBarPanel {
    private BuilderScreen screen;
    private ClientRtsController controller;

    public void init(BuilderScreen screen, ClientRtsController controller) {
        this.screen = screen;
        this.controller = controller;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY) {
        screen.ensureFillModeForShape(this.controller.getBuildShape());
        List<TopBarButtonLayout> topButtons = buildTopBarButtonLayouts();
        for (TopBarButtonLayout button : topButtons) {
            drawTopButton(g, mouseX, mouseY, button);
        }
        renderTopGuideHint(g, topButtons);

        String modeText = switch (this.controller.getMode()) {
            case INTERACT -> screen.text("screen.rtsbuilding.status.mode", screen.text("screen.rtsbuilding.mode.interact"));
            case LINK_STORAGE -> screen.text("screen.rtsbuilding.status.mode", screen.text("screen.rtsbuilding.mode.link_storage"));
            case FUNNEL -> screen.text("screen.rtsbuilding.status.mode", screen.text("screen.rtsbuilding.mode.funnel"));
            case SELECT_PAN -> screen.text("screen.rtsbuilding.status.mode", screen.text("screen.rtsbuilding.mode.camera"));
            case ROTATE -> screen.text("screen.rtsbuilding.status.mode", screen.text("screen.rtsbuilding.mode.rotate"));
            default -> screen.text("screen.rtsbuilding.status.mode", screen.text("screen.rtsbuilding.mode.idle"));
        };

        String linked = this.controller.isStorageLinked()
                ? screen.text("screen.rtsbuilding.status.storage_linked", this.controller.getLinkedStorageName())
                : screen.text("screen.rtsbuilding.status.storage_not_linked");
        String selected;
        if (this.controller.hasSelectedFluid()) {
            selected = screen.text("screen.rtsbuilding.status.selected_fluid", this.controller.getSelectedFluidLabel());
        } else if (!this.controller.getSelectedItemLabel().isEmpty()) {
            selected = screen.text("screen.rtsbuilding.status.selected_item", screen.selectedItemStatusLabel());
        } else if (this.controller.isEmptyHandSelected()) {
            selected = screen.text("screen.rtsbuilding.status.selected_empty_hand");
        } else {
            selected = screen.text("screen.rtsbuilding.status.selected_none");
        }
        String row1 = modeText + "    " + selected;
        String row2 = linked + (this.controller.isAutoStoreMinedDrops()
                ? "    " + screen.text("screen.rtsbuilding.status.auto_store_on")
                : "    " + screen.text("screen.rtsbuilding.status.auto_store_off"))
                + (screen.hasProgressionNode(RtsProgressionNodes.FUNNEL) ? "    " + screen.text("screen.rtsbuilding.status.funnel", screen.text(this.controller.isFunnelEnabled() ? "gui.rtsbuilding.on" : "gui.rtsbuilding.off")) : "")
                + (screen.hasProgressionNode(RtsProgressionNodes.REMOTE_PLACE) ? "    " + screen.text("screen.rtsbuilding.status.shape", screen.shapeLabel(this.controller.getBuildShape())) : "")
                + (screen.hasProgressionNode(RtsProgressionNodes.ULTIMINE) && screen.isUltimineOpen() ? "    " + screen.text("screen.rtsbuilding.status.ultimine", screen.getUltimineLimit()) : "")
                + "    " + screen.text("screen.rtsbuilding.status.fill", screen.fillModeLabel(screen.getShapeFillMode()))
                + "    " + screen.text("screen.rtsbuilding.status.rotation", screen.getShapeRotateDegrees())
                + "    " + screen.text("screen.rtsbuilding.status.undo_redo", screen.getShapeUndoSize(), screen.getShapeRedoSize())
                + "    " + screen.pendingShapeStatusText()
                + (screen.getPendingGuiBindSlot() >= 0 ? "    " + screen.text("screen.rtsbuilding.status.gui_bind_armed", screen.getPendingGuiBindSlot() + 1) : "");

        int statusX = 8;
        int statusW = Math.max(40, screen.width - 16);
        g.drawString(screen.font(), screen.trimToWidth(row1, statusW), statusX, 33, 0xF0F0F0);
        g.drawString(screen.font(), screen.trimToWidth(row2, statusW), statusX, 44,
                this.controller.isStorageLinked() ? 0xB8FFB8 : 0xFFD8AE);
    }

    // ======================== 点击处理 ========================

    public boolean handleClick(double mouseX, double mouseY) {
        if (mouseY < 4 || mouseY > 4 + TOP_BUTTON_H) {
            return false;
        }

        for (TopBarButtonLayout button : buildTopBarButtonLayouts()) {
            if (!inside(mouseX, mouseY, button.x(), 4, button.width(), TOP_BUTTON_H)) {
                continue;
            }
            switch (button.id()) {
                case INTERACT -> {
                    this.controller.setMode(BuilderMode.INTERACT);
                    this.controller.setFunnelEnabled(false);
                    screen.clearShapeBuildSession();
                }
                case LINK -> {
                    this.controller.setMode(BuilderMode.LINK_STORAGE);
                    this.controller.setFunnelEnabled(false);
                    screen.clearShapeBuildSession();
                }
                case FUNNEL -> {
                    this.controller.setMode(BuilderMode.FUNNEL);
                    this.controller.setFunnelEnabled(true);
                    screen.clearShapeBuildSession();
                }
                case ROTATE -> {
                    this.controller.setMode(BuilderMode.ROTATE);
                    this.controller.setFunnelEnabled(false);
                    screen.clearShapeBuildSession();
                }
                case QUICK_BUILD -> {
                    screen.toggleQuickBuild();
                    screen.closeGearMenu();
                    screen.persistUiState();
                }
                case ULTIMINE -> {
                    screen.toggleUltimine();
                    screen.closeGearMenu();
                    screen.persistUiState();
                }
                case QUEST_DETECT -> {
                    screen.closeGearMenu();
                    this.controller.detectQuestsNow();
                }
                case CHUNK_VIEW -> {
                    this.controller.setChunkCurtainVisible(!this.controller.isChunkCurtainVisible());
                    screen.persistUiState();
                }
                case GUIDE -> {
                    screen.toggleTopGuide(button.x() + button.width() / 2, 4 + TOP_BUTTON_H);
                    screen.closeGearMenu();
                }
                case DEBUG -> {
                    screen.closeGearMenu();
                    screen.copyDebugSnapshotToClipboard();
                }
                case GEAR -> {
                    screen.toggleGearMenu();
                }
                default -> {
                }
            }
            return true;
        }
        screen.closeGearMenu();
        return false;
    }

    // ======================== 布局构建 ========================

    public List<TopBarButtonLayout> buildTopBarButtonLayouts() {
        List<TopBarButtonLayout> layouts = new ArrayList<>();
        int x = 8;
        layouts.add(new TopBarButtonLayout(TopBarButtonId.INTERACT, x, TOP_MODE_BUTTON_W, "", true, topActionForMode() == TopAction.INTERACT));
        x += TOP_MODE_BUTTON_W + TOP_BUTTON_GAP;
        if (screen.hasProgressionNode(RtsProgressionNodes.STORAGE_LINK)) {
            layouts.add(new TopBarButtonLayout(TopBarButtonId.LINK, x, TOP_MODE_BUTTON_W, "", true, topActionForMode() == TopAction.LINK));
            x += TOP_MODE_BUTTON_W + TOP_BUTTON_GAP;
        }
        if (screen.hasProgressionNode(RtsProgressionNodes.FUNNEL)) {
            layouts.add(new TopBarButtonLayout(TopBarButtonId.FUNNEL, x, TOP_MODE_BUTTON_W, "", true, topActionForMode() == TopAction.FUNNEL));
            x += TOP_MODE_BUTTON_W + TOP_BUTTON_GAP;
        }
        if (screen.hasProgressionNode(RtsProgressionNodes.ROTATE_BLOCK)) {
            layouts.add(new TopBarButtonLayout(TopBarButtonId.ROTATE, x, TOP_MODE_BUTTON_W, "", true, topActionForMode() == TopAction.ROTATE));
            x += TOP_MODE_BUTTON_W + TOP_BUTTON_GAP;
        }
        x += 8;
        if (screen.hasProgressionNode(RtsProgressionNodes.REMOTE_PLACE)) {
            layouts.add(new TopBarButtonLayout(TopBarButtonId.QUICK_BUILD, x, TOP_ICON_BUTTON_W, "", true, screen.isQuickBuildOpen()));
            x += TOP_ICON_BUTTON_W + TOP_BUTTON_GAP;
        }
        if (screen.hasProgressionNode(RtsProgressionNodes.ULTIMINE)) {
            layouts.add(new TopBarButtonLayout(TopBarButtonId.ULTIMINE, x, TOP_ICON_BUTTON_W, "", true, screen.isUltimineOpen()));
            x += TOP_ICON_BUTTON_W + TOP_BUTTON_GAP;
        }
        if (isFtbQuestIntegrationLoaded()) {
            layouts.add(new TopBarButtonLayout(TopBarButtonId.QUEST_DETECT, x, TOP_ICON_BUTTON_W, "", true, this.controller.isQuestDetectPopupVisible()));
            x += TOP_ICON_BUTTON_W + TOP_BUTTON_GAP;
        }
        layouts.add(new TopBarButtonLayout(TopBarButtonId.CHUNK_VIEW, x, TOP_ICON_BUTTON_W, "", true, this.controller.isChunkCurtainVisible()));
        x += TOP_ICON_BUTTON_W + TOP_BUTTON_GAP;
        layouts.add(new TopBarButtonLayout(TopBarButtonId.GUIDE, x, TOP_ICON_BUTTON_W, "", true, screen.isGuideOpen()));
        int gearX = Math.max(x + TOP_BUTTON_GAP, screen.width - TOP_ICON_BUTTON_W - 8);
        if (screen.isDebugButtonVisible()) {
            int debugX = gearX - TOP_ICON_BUTTON_W - TOP_BUTTON_GAP;
            layouts.add(new TopBarButtonLayout(TopBarButtonId.DEBUG, debugX, TOP_ICON_BUTTON_W, "", true, false));
        }
        layouts.add(new TopBarButtonLayout(TopBarButtonId.GEAR, gearX, TOP_ICON_BUTTON_W, "", true, screen.isGearMenuOpen()));
        return layouts;
    }

    public boolean isPrimaryMouseDown() {
        return GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
    }

    // ======================== 按钮渲染 ========================

    private void drawTopButton(GuiGraphics g, int mouseX, int mouseY, TopBarButtonLayout button) {
        if (button.iconOnly()) {
            drawTopIconButton(g, mouseX, mouseY, button);
            return;
        }
        drawTopButtonSized(g, button.x(), button.label(), button.active(), button.width());
    }

    private void drawTopButtonSized(GuiGraphics g, int x, String label, boolean active, int w) {
        int y = 4;
        int h = TOP_BUTTON_H;
        int bg = active ? 0xFF2E6A50 : 0xAA1F2329;
        g.fill(x, y, x + w, y + h, bg);
        g.hLine(x, x + w, y, 0xFF5B6673);
        g.hLine(x, x + w, y + h, 0xFF0D0E10);
        g.vLine(x, y, y + h, 0xFF5B6673);
        g.vLine(x + w, y, y + h, 0xFF0D0E10);
        g.drawCenteredString(screen.font(), screen.trimToWidth(label, Math.max(6, w - 8)), x + w / 2, y + 8, 0xFFFFFF);
    }

    private void drawTopIconButton(GuiGraphics g, int mouseX, int mouseY, TopBarButtonLayout button) {
        int x = button.x();
        int y = 4;
        int w = button.width();
        int h = TOP_BUTTON_H;
        boolean hovered = inside(mouseX, mouseY, x, y, w, h);
        boolean pressed = hovered && isPrimaryMouseDown();

        int bg = 0xAA1F2329;
        int light = 0xFF5B6673;
        int dark = 0xFF0D0E10;
        int icon = 0xFFBDC9D6;
        if (button.active()) {
            bg = 0xFF2D6B47;
            light = 0xFF9AD2AE;
            icon = 0xFFF4FBF5;
        } else if (pressed) {
            bg = 0xFF1F5037;
            light = 0xFF6AA784;
            icon = 0xFFD9E3EF;
        } else if (hovered) {
            bg = 0xFF1D2530;
            light = 0xFF7A90AA;
            icon = 0xFFD9E3EF;
        }

        ResourceLocation textureIcon = TopBarIconRenderer.topbarModeTexture(button.id(), button.active(), hovered, pressed);
        if (textureIcon != null) {
            g.blit(textureIcon, x + (w - TOP_BUTTON_H) / 2, y, 0, 0, TOP_BUTTON_H, TOP_BUTTON_H, TOP_BUTTON_H, TOP_BUTTON_H);
            return;
        }

        g.fill(x, y, x + w, y + h, bg);
        g.hLine(x, x + w, y, light);
        g.hLine(x, x + w, y + h, dark);
        g.vLine(x, y, y + h, light);
        g.vLine(x + w, y, y + h, dark);
        if (pressed) {
            g.hLine(x + 1, x + w - 1, y + 1, dark);
            g.vLine(x + 1, y + 1, y + h - 1, dark);
        }

        int cx = x + (w / 2);
        int cy = y + (h / 2);
        switch (button.id()) {
            case INTERACT -> TopBarIconRenderer.drawInteractModeIcon(g, cx, cy, icon);
            case LINK -> TopBarIconRenderer.drawLinkModeIcon(g, cx, cy, icon, button.active());
            case FUNNEL -> TopBarIconRenderer.drawFunnelModeIcon(g, cx, cy, icon, button.active());
            case ROTATE -> TopBarIconRenderer.drawRotateModeIcon(g, cx, cy, icon);
            case QUICK_BUILD -> TopBarIconRenderer.drawQuickBuildIcon(g, cx, cy, icon, button.active());
            case ULTIMINE -> TopBarIconRenderer.drawUltimineIcon(g, cx, cy, icon, button.active());
            case QUEST_DETECT -> TopBarIconRenderer.drawQuestCheckIcon(g, cx, cy, icon);
            case CHUNK_VIEW -> TopBarIconRenderer.drawChunkCurtainIcon(g, cx, cy, icon, button.active());
            case DEBUG -> TopBarIconRenderer.drawDebugIcon(g, cx, cy, icon, screen.font());
            case GEAR -> TopBarIconRenderer.drawGearIcon(g, cx, cy, icon);
            case GUIDE -> g.drawCenteredString(screen.font(), "i", cx, y + 7, icon);
            default -> g.drawCenteredString(screen.font(), "?", cx, y + 6, icon);
        }
    }

    private void renderTopGuideHint(GuiGraphics g, List<TopBarButtonLayout> topButtons) {
        screen.renderTopGuideHint(g, topButtons);
    }

    // ======================== 辅助方法 ========================

    public TopAction topActionForMode() {
        return switch (this.controller.getMode()) {
            case INTERACT -> TopAction.INTERACT;
            case LINK_STORAGE -> TopAction.LINK;
            case FUNNEL -> TopAction.FUNNEL;
            case ROTATE -> TopAction.ROTATE;
            default -> TopAction.INTERACT;
        };
    }

    private static boolean isFtbQuestIntegrationLoaded() {
        return ModList.get().isLoaded("ftbquests")
                || ModList.get().isLoaded("ftb_quests")
                || ModList.get().isLoaded("ftblibrary");
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}
