package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsResumePlacementScanPayload;
import com.rtsbuilding.rtsbuilding.uikit.canvas.WorkflowResumeChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.WorkflowResumeWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.WorkflowResumeStyle;
import net.minecraft.client.gui.Font;
import com.rtsbuilding.rtsbuilding.client.screen.canvas.RtsGuiContext;
import net.minecraft.world.item.ItemStack;

/**
 * 普通放置恢复窗口的 Minecraft 薄绘制适配。
 *
 * <p>共享 Kit 负责几何与 chrome；本类只把放置扫描字段翻译为无阴影文字和真实物品预览。
 * 它不持有窗口生命周期或恢复命令。</p>
 */
final class PlacementResumePanelRenderer {
    private PlacementResumePanelRenderer() {
    }

    static void render(
            RtsGuiContext graphics,
            Font font,
            WorkflowResumeWindowLayout.PlacementGeometry geometry,
            S2CRtsResumePlacementScanPayload data,
            int mouseX,
            int mouseY) {
        boolean enough = data.missingItems() <= 0;
        WorkflowResumeChromeRenderer.renderPlacement(
                new MinecraftUiCanvas(graphics, font),
                geometry,
                enough,
                mouseX,
                mouseY);
        renderItem(graphics, font, geometry, data);
        renderStats(graphics, font, geometry, data, enough);
        renderActions(
                graphics,
                font,
                geometry,
                enough,
                mouseX,
                mouseY);
    }

    private static void renderItem(
            RtsGuiContext graphics,
            Font font,
            WorkflowResumeWindowLayout.PlacementGeometry geometry,
            S2CRtsResumePlacementScanPayload data) {
        ItemStack displayStack =
                WorkflowResumeRenderSupport.item(data.itemId());
        if (!displayStack.isEmpty()) {
            graphics.renderItem(
                    displayStack,
                    (int) geometry.itemIcon.getX(),
                    (int) geometry.itemIcon.getY());
            WorkflowResumeRenderSupport.draw(
                    graphics,
                    font,
                    data.itemLabel(),
                    geometry.x + WorkflowResumeWindowLayout.PLACEMENT_ITEM_TEXT_X,
                    geometry.y + WorkflowResumeWindowLayout.PLACEMENT_ITEM_TEXT_TOP,
                    WorkflowResumeStyle.ITEM_TEXT.toArgb());
            return;
        }
        WorkflowResumeRenderSupport.draw(
                graphics,
                font,
                data.itemId(),
                geometry.x,
                geometry.y + WorkflowResumeWindowLayout.PLACEMENT_ITEM_TEXT_TOP,
                WorkflowResumeStyle.ITEM_TEXT.toArgb());
    }

    private static void renderStats(
            RtsGuiContext graphics,
            Font font,
            WorkflowResumeWindowLayout.PlacementGeometry geometry,
            S2CRtsResumePlacementScanPayload data,
            boolean enough) {
        int row = 0;
        drawStat(
                graphics,
                font,
                geometry,
                row++,
                "screen.rtsbuilding.workflow.resume_placement.remaining",
                String.valueOf(data.totalRemaining()),
                WorkflowResumeStyle.PRIMARY_TEXT.toArgb());
        drawStat(
                graphics,
                font,
                geometry,
                row++,
                "screen.rtsbuilding.workflow.resume_placement.already_placed",
                String.valueOf(data.alreadyPlacedCount()),
                WorkflowResumeStyle.SECONDARY_TEXT.toArgb());
        if (geometry.hasConflicts) {
            drawStat(
                    graphics,
                    font,
                    geometry,
                    row++,
                    "screen.rtsbuilding.workflow.resume_placement.conflicts",
                    String.valueOf(data.conflictCount()),
                    WorkflowResumeStyle.WARNING_TEXT.toArgb());
        }
        drawStat(
                graphics,
                font,
                geometry,
                row++,
                "screen.rtsbuilding.workflow.resume_placement.available",
                String.valueOf(data.availableItems()),
                WorkflowResumeStyle.SUCCESS_TEXT.toArgb());
        drawStat(
                graphics,
                font,
                geometry,
                row++,
                "screen.rtsbuilding.workflow.resume_placement.needed",
                String.valueOf(data.neededItems()),
                WorkflowResumeStyle.PRIMARY_TEXT.toArgb());
        drawStat(
                graphics,
                font,
                geometry,
                row,
                "screen.rtsbuilding.workflow.resume_placement.missing",
                enough
                        ? WorkflowResumeRenderSupport.text(
                                "screen.rtsbuilding.workflow.resume_placement.enough")
                        : String.valueOf(data.missingItems()),
                enough
                        ? WorkflowResumeStyle.SUCCESS_TEXT.toArgb()
                        : WorkflowResumeStyle.ERROR_TEXT.toArgb());
    }

    private static void renderActions(
            RtsGuiContext graphics,
            Font font,
            WorkflowResumeWindowLayout.PlacementGeometry geometry,
            boolean enough,
            int mouseX,
            int mouseY) {
        if (geometry.hasConflicts) {
            drawAction(
                    graphics,
                    font,
                    geometry.primaryAction,
                    enough
                            ? "screen.rtsbuilding.workflow.resume_placement.skip"
                            : "screen.rtsbuilding.workflow.insufficient_items",
                    WorkflowResumeStyle.ActionKind.SKIP,
                    enough,
                    mouseX,
                    mouseY);
            drawAction(
                    graphics,
                    font,
                    geometry.secondaryAction,
                    enough
                            ? "screen.rtsbuilding.workflow.resume_placement.overwrite"
                            : "screen.rtsbuilding.workflow.insufficient_items",
                    WorkflowResumeStyle.ActionKind.OVERWRITE,
                    enough,
                    mouseX,
                    mouseY);
            return;
        }
        drawAction(
                graphics,
                font,
                geometry.primaryAction,
                enough
                        ? "screen.rtsbuilding.workflow.resume_placement.restart"
                        : "screen.rtsbuilding.workflow.insufficient_items",
                WorkflowResumeStyle.ActionKind.RESUME,
                enough,
                mouseX,
                mouseY);
    }

    private static void drawAction(
            RtsGuiContext graphics,
            Font font,
            com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect action,
            String key,
            WorkflowResumeStyle.ActionKind kind,
            boolean enabled,
            int mouseX,
            int mouseY) {
        WorkflowResumeRenderSupport.drawActionText(
                graphics,
                font,
                action,
                key,
                WorkflowResumeStyle.action(
                        kind,
                        enabled,
                        action.contains(mouseX, mouseY)).text.toArgb());
    }

    private static void drawStat(
            RtsGuiContext graphics,
            Font font,
            WorkflowResumeWindowLayout.PlacementGeometry geometry,
            int row,
            String labelKey,
            String value,
            int valueColor) {
        int y = geometry.statY(row);
        WorkflowResumeRenderSupport.draw(
                graphics,
                font,
                WorkflowResumeRenderSupport.text(labelKey),
                geometry.x,
                y,
                WorkflowResumeStyle.LABEL_TEXT.toArgb());
        WorkflowResumeRenderSupport.draw(
                graphics,
                font,
                value,
                geometry.valueX,
                y,
                valueColor);
    }
}
