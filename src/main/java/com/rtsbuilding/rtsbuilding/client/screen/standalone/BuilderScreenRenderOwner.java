package com.rtsbuilding.rtsbuilding.client.screen.standalone;


import com.mojang.blaze3d.platform.InputConstants;
import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.pathfinding.RtsClientPathfinding;
import com.rtsbuilding.rtsbuilding.client.record.CraftableEntry;
import com.rtsbuilding.rtsbuilding.client.rendering.builder.BuildGhostBlockStateResolver;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RtsPlacementRayFreeze;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RenderingUtil;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.*;
import com.rtsbuilding.rtsbuilding.client.screen.craft.RtsCraftQuantityWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingManager;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingPanel;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingWorldInput;
import com.rtsbuilding.rtsbuilding.client.screen.funnel.FunnelBufferPanel;
import com.rtsbuilding.rtsbuilding.client.screen.gear.GearMenuPanel;
import com.rtsbuilding.rtsbuilding.client.screen.guide.GuidePanel;
import com.rtsbuilding.rtsbuilding.client.screen.guide.RtsAiChatPanel;
import com.rtsbuilding.rtsbuilding.uicore.guide.GuideUiContext;
import com.rtsbuilding.rtsbuilding.client.screen.handler.RtsUiScaleFrame;
import com.rtsbuilding.rtsbuilding.client.screen.handler.ScreenCursorPicker;
import com.rtsbuilding.rtsbuilding.client.screen.handler.ScreenShapeController;
import com.rtsbuilding.rtsbuilding.client.screen.handler.StorageLinkDetailHandler;
import com.rtsbuilding.rtsbuilding.client.screen.input.CameraInputHandler;
import com.rtsbuilding.rtsbuilding.client.screen.interaction.InteractionTypes;
import com.rtsbuilding.rtsbuilding.client.screen.layout.BottomPanelLayoutTypes;
import com.rtsbuilding.rtsbuilding.client.screen.mode.BuilderModeWheel;
import com.rtsbuilding.rtsbuilding.client.screen.mode.PlacedBlockRotationGesture;
import com.rtsbuilding.rtsbuilding.client.screen.mode.PlacedBlockRotationHandles;
import com.rtsbuilding.rtsbuilding.client.screen.mode.PlacementStateWheel;
import com.rtsbuilding.rtsbuilding.client.screen.overlay.LeftDockedTooltipRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.overlay.PlayerStatusRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.overlay.RtsScreenOverlayRenderer;
import com.rtsbuilding.rtsbuilding.client.screen.panel.BottomPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsFloatingWindowLayer;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.QuickBuildMode;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.QuickBuildPanel;
import com.rtsbuilding.rtsbuilding.client.screen.selection.RtsSelectionNudge;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeGeometryUtil;
import com.rtsbuilding.rtsbuilding.client.screen.storage.LinkedStoragePanel;
import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarPanel;
import com.rtsbuilding.rtsbuilding.client.screen.topbar.TopBarTypes;
import com.rtsbuilding.rtsbuilding.client.screen.workflow.RtsBlueprintResumePanel;
import com.rtsbuilding.rtsbuilding.client.screen.workflow.RtsResumePlacementPanel;
import com.rtsbuilding.rtsbuilding.client.screen.workflow.RtsWorkflowPanel;
import com.rtsbuilding.rtsbuilding.client.service.MiningOperationService;
import com.rtsbuilding.rtsbuilding.client.state.RtsScreenUiStateManager;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.client.widget.WindowTextBox;
import com.rtsbuilding.rtsbuilding.common.RtsUltimineCollector;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import com.rtsbuilding.rtsbuilding.compat.ae2.RtsAe2IconResolver;
import com.rtsbuilding.rtsbuilding.server.plugin.BuiltInRtsPluginCatalog;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelCraftDockStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.BottomPanelCraftStyle;
import com.rtsbuilding.rtsbuilding.uikit.theme.RtsMainlineTheme;
import com.rtsbuilding.rtsbuilding.uikit.theme.TooltipStyle;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.canvas.UiChromeRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * BuilderScreen 的RenderOwner职责所有者。
 *
 * <p>它只处理这一类完整职责，不拥有 Screen 生命周期，也不复制面板状态。</p>
 */
final class BuilderScreenRenderOwner {
    private final BuilderScreen screen;

    BuilderScreenRenderOwner(BuilderScreen screen) {
        this.screen = screen;
    }

    /**
         * Main render entry point. Uses fixed RTS GUI scaling when enabled.
         * Resets hover states, draws the top bar background, renders all panels and overlays
         * in priority order: top bar, bottom panel, quick-build, ultimine, funnel buffer,
         * quest/storage scan popups, blueprint capture/placement HUD,
         * tooltips, cursor preview, damage flash, and modal layers (wheel, gear, guide, dialogs).
         */
        public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            if (!screen.guiScaleCoordinator.isRenderPass()
                    && screen.guiScaleCoordinator.renderScaled(
                            guiGraphics, mouseX, mouseY, partialTick, screen::render)) {
                return;
            }
            screen.lastMouseX = mouseX;
            screen.lastMouseY = mouseY;
            screen.guiScaleCoordinator.recordViewport();
            screen.resetHoverStates();
            guiGraphics.fill(0, 0, screen.uiWidth(), TOP_H, RtsMainlineTheme.TOP_BAR_BACKGROUND.toArgb());
            if (screen.controller.isHomeSelectionMode()) {
                screen.overlayRenderer.renderHomeSelectionOverlay(guiGraphics, mouseX, mouseY);
                screen.overlayRenderer.renderDamageFlash(guiGraphics);
                return;
            }
            screen.topBarPanel.render(guiGraphics, mouseX, mouseY);
            if (screen.controller.isPlayerStatusOverlayEnabled()) {
                screen.playerStatusRenderer.render(guiGraphics);
            }
            screen.storageLinkDetailHandler.updateVisibility(mouseX, mouseY);
            screen.updateRangeCullingHover(mouseX, mouseY);
            screen.bottomPanel.render(guiGraphics, mouseX, mouseY, partialTick);
            screen.funnelBufferPanel.render(guiGraphics, mouseX, mouseY);
            if (screen.bottomPanel.bottomPanelTab == BottomPanelLayoutTypes.BottomPanelTab.BLUEPRINTS && BlueprintPanel.isCaptureModeActive()) {
                BlockHitResult hit = screen.isWorldArea(mouseX, mouseY) ? screen.cursorPicker.pickBlockHit() : null;
                BlueprintPanel.updateCaptureHover(
                        screen.isWorldArea(mouseX, mouseY) ? screen.cursorPicker.currentRayOrigin() : null,
                        screen.isWorldArea(mouseX, mouseY) ? screen.cursorPicker.computeCursorRayDirection() : null,
                        hit == null ? null : hit.getBlockPos());
            }
            screen.blueprintWindowPanel.syncWithBlueprintState();
            screen.blueprintMaterialWindowPanel.syncWithBlueprintState();
            screen.blueprintNameWindowPanel.syncWithBlueprintState();
            screen.floatingWindowLayer.renderFloatingWindows(guiGraphics, mouseX, mouseY);
            screen.floatingWindowLayer.renderFloatingWindowOverlays(guiGraphics, mouseX, mouseY);
            screen.overlayRenderer.renderQuestDetectPopup(guiGraphics);
            screen.overlayRenderer.renderStorageScanPopup(guiGraphics);
            screen.renderHoveredItemTooltips(guiGraphics, mouseX, mouseY);
            screen.overlayRenderer.updateNativeCursor(screen.floatingWindowLayer.resizeCursorAt(mouseX, mouseY));
            screen.bottomPanel.renderCraftFeedback(guiGraphics);
            screen.overlayRenderer.renderDamageFlash(guiGraphics);
            if (screen.placementStateWheel.isOpen()) {
                screen.overlayRenderer.updateNativeCursorVisibility(false);
                screen.placementStateWheel.render(guiGraphics, screen.font(), mouseX, mouseY);
            } else if (screen.modeWheel.isOpen()) {
                screen.overlayRenderer.updateNativeCursorVisibility(false);
                screen.modeWheel.render(guiGraphics, screen.font(), mouseX, mouseY, screen.controller.getMode());
            }
        }

    void resetHoverStates() {
            screen.shapeController.setShapeCursorY(screen.lastMouseY);
            screen.funnelBufferPanel.resetHoveredEntry();
            screen.bottomPanel.hoveredEntry = -1;
            screen.bottomPanel.hoveredRecentEntry = -1;
            screen.bottomPanel.hoveredFluidEntry = -1;
            screen.bottomPanel.hoveredCreativeEntry = -1;
            screen.bottomPanel.hoveredCraftableEntry = -1;
            screen.bottomPanel.hoveredToolSlot = -1;
            screen.bottomPanel.hoveredEmptyHandSlot = false;
            screen.bottomPanel.hoveredPinIndex = -1;
            screen.bottomPanel.hoveredGuiBindingSlot = -1;
            screen.bottomPanel.hoveredPinPageButton = false;
        }

    void renderHoveredItemTooltips(GuiGraphics g, int mouseX, int mouseY) {
            boolean modalOpen = screen.isMouseOverFloatingWindow(mouseX, mouseY);
            boolean placementSelectionActive = screen.controller.hasSelectedItem() || screen.controller.hasSelectedFluid();
            if (!modalOpen) {
                if (!placementSelectionActive
                        && screen.bottomPanel.hoveredCreativeEntry >= 0) {
                    var entry = screen.bottomPanel.getCreativeEntryForTooltip(screen.bottomPanel.hoveredCreativeEntry);
                    if (entry != null) {
                        screen.leftDockedTooltipRenderer.render(g, entry.stack());
                    }
                }
                if (!placementSelectionActive
                        && screen.bottomPanel.hoveredEntry >= 0
                        && screen.bottomPanel.hoveredEntry < screen.controller.getStorageEntries().size()) {
                    var entry = screen.controller.getStorageEntries().get(screen.bottomPanel.hoveredEntry);
                    screen.leftDockedTooltipRenderer.render(g, entry.stack());
                }
                if (!placementSelectionActive
                        && screen.bottomPanel.hoveredRecentEntry >= 0
                        && screen.bottomPanel.hoveredRecentEntry < screen.controller.getRecentEntries().size()) {
                    var entry = screen.controller.getRecentEntries().get(screen.bottomPanel.hoveredRecentEntry);
                    if (!entry.preview().isEmpty()) {
                        screen.leftDockedTooltipRenderer.render(g, entry.preview());
                    } else {
                        screen.leftDockedTooltipRenderer.render(g, Component.literal(entry.label()));
                    }
                }
                if (!placementSelectionActive
                        && screen.bottomPanel.hoveredFluidEntry >= 0
                        && screen.bottomPanel.hoveredFluidEntry < screen.controller.getFluidEntries().size()) {
                    var fluid = screen.controller.getFluidEntries().get(screen.bottomPanel.hoveredFluidEntry);
                    if (!fluid.preview().isEmpty()) {
                        screen.leftDockedTooltipRenderer.render(g, fluid.preview());
                    } else {
                        screen.leftDockedTooltipRenderer.render(g, Component.literal(fluid.label()));
                    }
                }
                if (screen.bottomPanel.hoveredCraftableEntry >= 0 && screen.bottomPanel.hoveredCraftableEntry < screen.controller.getCraftableEntries().size()) {
                    var entry = screen.controller.getCraftableEntries().get(screen.bottomPanel.hoveredCraftableEntry);
                    screen.leftDockedTooltipRenderer.render(g, entry.stack());
                    String detail = entry.craftable()
                            ? screen.text("screen.rtsbuilding.tooltip.craft_choose")
                            : entry.missingSummary();
                    if (detail != null && !detail.isBlank()) {
                        screen.leftDockedTooltipRenderer.renderDetail(
                                g, detail, TooltipStyle.craftChoice(entry.craftable()));
                    }
                }
                if (screen.funnelBufferPanel.getHoveredEntry() >= 0 && screen.funnelBufferPanel.getHoveredEntry() < screen.controller.getFunnelBufferEntries().size()) {
                    var entry = screen.controller.getFunnelBufferEntries().get(screen.funnelBufferPanel.getHoveredEntry());
                    screen.leftDockedTooltipRenderer.render(g, entry.stack());
                    screen.leftDockedTooltipRenderer.renderDetail(
                            g, screen.text("screen.rtsbuilding.tooltip.buffered", entry.count()),
                            TooltipStyle.COUNT);
                }
                if (screen.bottomPanel.hoveredGuiBindingSlot >= 0 && screen.bottomPanel.hoveredGuiBindingSlot < screen.controller.getGuiBindingCount()) {
                    String detail = screen.controller.hasGuiBinding(screen.bottomPanel.hoveredGuiBindingSlot)
                            ? screen.controller.getGuiBindingLabel(screen.bottomPanel.hoveredGuiBindingSlot)
                            : screen.text("screen.rtsbuilding.tooltip.gui_empty");
                    screen.leftDockedTooltipRenderer.render(g, Component.literal(detail));
                    screen.leftDockedTooltipRenderer.renderDetail(
                            g,
                            screen.pendingGuiBindSlot == screen.bottomPanel.hoveredGuiBindingSlot
                                    ? screen.text("screen.rtsbuilding.tooltip.gui_cancel_bind")
                                    : (screen.controller.hasGuiBinding(screen.bottomPanel.hoveredGuiBindingSlot)
                                            ? screen.text("screen.rtsbuilding.tooltip.gui_bound")
                                            : screen.text("screen.rtsbuilding.tooltip.gui_unbound")),
                            TooltipStyle.DETAIL);
                }
                if (screen.bottomPanel.hoveredEmptyHandSlot) {
                    screen.leftDockedTooltipRenderer.render(
                            g, Component.translatable("screen.rtsbuilding.tooltip.empty_hand"));
                    screen.leftDockedTooltipRenderer.renderDetail(
                            g, screen.text("screen.rtsbuilding.tooltip.empty_hand_detail"),
                            TooltipStyle.COUNT);
                }
                boolean funnelCursor = screen.shouldRenderFunnelCursor();
                screen.overlayRenderer.updateNativeCursorVisibility(funnelCursor);
                if (funnelCursor) {
                    g.renderItem(FUNNEL_CURSOR_STACK, mouseX + 8, mouseY + 8);
                } else if (screen.pendingGuiBindSlot >= 0) {
                    screen.drawGuiBindCursor(g, mouseX, mouseY);
                } else {
                    ItemStack cursorPreview = screen.resolveCursorPreview();
                    if (!cursorPreview.isEmpty() && !screen.isSearchFocused()
                            && !screen.isMouseOverFloatingWindow(mouseX, mouseY)) {
                        g.renderItem(cursorPreview, mouseX + 10, mouseY + 10);
                    }
                }
            } else {
                screen.overlayRenderer.updateNativeCursorVisibility(false);
            }
        }

    void renderTopGuideHint(GuiGraphics g, List<TopBarTypes.TopBarButtonLayout> topButtons) {
            screen.guidePanel.renderTopHint(g, topButtons);
        }

    void drawGuiBindCursor(GuiGraphics g, int mouseX, int mouseY) {
            int x = mouseX + 8;
            int y = mouseY + 8;
            UiChromeRenderer.frame(
                    new MinecraftUiCanvas(g, screen.font(), screen),
                    new UiRect(x, y, CRAFT_DOCK_SLOT_SIZE, CRAFT_DOCK_SLOT_SIZE),
                    1.0D,
                    BottomPanelCraftDockStyle.SLOT_PENDING,
                    BottomPanelCraftDockStyle.BIND_CURSOR_BORDER_LIGHT,
                    BottomPanelCraftDockStyle.SLOT_BORDER_DARK);
            int textX = x + (CRAFT_DOCK_SLOT_SIZE - screen.font().width("+")) / 2;
            g.drawString(screen.font(), "+", textX, y + 1,
                    BottomPanelCraftDockStyle.TEXT.toArgb(), false);
        }

}
