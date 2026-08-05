package com.rtsbuilding.rtsbuilding.client.screen.workflow;

import com.rtsbuilding.rtsbuilding.client.screen.canvas.MinecraftUiCanvas;
import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsBlueprintResumeScanPayload;
import com.rtsbuilding.rtsbuilding.uikit.canvas.WorkflowResumeChromeRenderer;
import com.rtsbuilding.rtsbuilding.uikit.layout.WorkflowResumeWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.WorkflowResumeStyle;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.item.ItemStack;

/**
 * 蓝图材料恢复窗口的 Minecraft 薄绘制适配。
 *
 * <p>共享 Kit 负责行、列与动作框；本类只恢复真实物品图标、材料统计和翻译文字。
 * 它不持有滚动位置、窗口生命周期或网络命令。</p>
 */
final class BlueprintResumePanelRenderer {
    private BlueprintResumePanelRenderer() {
    }

    static void render(
            LegacyGuiGraphics graphics,
            FontRenderer font,
            WorkflowResumeWindowLayout.BlueprintGeometry geometry,
            S2CRtsBlueprintResumeScanPayload data,
            int scrollOffset,
            boolean canResume,
            int mouseX,
            int mouseY) {
        WorkflowResumeChromeRenderer.renderBlueprint(
                new MinecraftUiCanvas(graphics, font),
                geometry,
                canResume,
                mouseX,
                mouseY);
        renderHeader(graphics, font, geometry, data);
        renderRows(
                graphics,
                font,
                geometry,
                data,
                scrollOffset);
        WorkflowResumeRenderSupport.drawActionText(
                graphics,
                font,
                geometry.action,
                canResume
                        ? "screen.rtsbuilding.workflow.blueprint_resume.restart"
                        : "screen.rtsbuilding.workflow.blueprint_resume.insufficient_materials",
                WorkflowResumeStyle.action(
                        WorkflowResumeStyle.ActionKind.RESUME,
                        canResume,
                        geometry.action.contains(mouseX, mouseY))
                        .text.toArgb());
    }

    static boolean allMaterialsEnough(
            S2CRtsBlueprintResumeScanPayload data) {
        if (data == null) {
            return false;
        }
        for (int index = 0; index < data.required().size(); index++) {
            if (data.available().get(index) < data.required().get(index)) {
                return false;
            }
        }
        return true;
    }

    private static void renderHeader(
            LegacyGuiGraphics graphics,
            FontRenderer font,
            WorkflowResumeWindowLayout.BlueprintGeometry geometry,
            S2CRtsBlueprintResumeScanPayload data) {
        WorkflowResumeRenderSupport.draw(
                graphics,
                font,
                WorkflowResumeRenderSupport.text(
                        "screen.rtsbuilding.workflow.blueprint_resume.progress",
                        data.completedCount(),
                        data.totalCount(),
                        data.totalCount() - data.completedCount()),
                geometry.x,
                geometry.y,
                WorkflowResumeStyle.PROGRESS_TEXT.toArgb());
        int headerY = geometry.y + WorkflowResumeWindowLayout.BLUEPRINT_HEADER_TOP;
        WorkflowResumeRenderSupport.draw(
                graphics,
                font,
                WorkflowResumeRenderSupport.text(
                        "screen.rtsbuilding.workflow.blueprint_resume.material"),
                geometry.x,
                headerY,
                WorkflowResumeStyle.LABEL_TEXT.toArgb());
        WorkflowResumeRenderSupport.draw(
                graphics,
                font,
                WorkflowResumeRenderSupport.text(
                        "screen.rtsbuilding.workflow.blueprint_resume.required"),
                geometry.requiredColumnX,
                headerY,
                WorkflowResumeStyle.LABEL_TEXT.toArgb());
        WorkflowResumeRenderSupport.draw(
                graphics,
                font,
                WorkflowResumeRenderSupport.text(
                        "screen.rtsbuilding.workflow.blueprint_resume.available"),
                geometry.availableColumnX,
                headerY,
                WorkflowResumeStyle.LABEL_TEXT.toArgb());
    }

    private static void renderRows(
            LegacyGuiGraphics graphics,
            FontRenderer font,
            WorkflowResumeWindowLayout.BlueprintGeometry geometry,
            S2CRtsBlueprintResumeScanPayload data,
            int scrollOffset) {
        for (WorkflowResumeWindowLayout.BlueprintRowGeometry row
                : geometry.rows) {
            int sourceIndex = scrollOffset + row.visibleIndex;
            renderRow(
                    graphics,
                    font,
                    geometry,
                    row,
                    data.itemIds().get(sourceIndex),
                    data.itemLabels().get(sourceIndex),
                    data.required().get(sourceIndex),
                    data.available().get(sourceIndex));
        }
    }

    private static void renderRow(
            LegacyGuiGraphics graphics,
            FontRenderer font,
            WorkflowResumeWindowLayout.BlueprintGeometry geometry,
            WorkflowResumeWindowLayout.BlueprintRowGeometry row,
            String itemId,
            String itemLabel,
            int required,
            long available) {
        long missing = Math.max(0L, required - available);
        boolean enough = available >= required;
        ItemStack displayStack =
                WorkflowResumeRenderSupport.item(itemId);
        if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(displayStack)) {
            graphics.renderItem(
                    displayStack,
                    (int) row.itemIcon.getX(),
                    (int) row.itemIcon.getY());
            WorkflowResumeRenderSupport.draw(
                    graphics,
                    font,
                    WorkflowResumeRenderSupport.truncate(
                            itemLabel,
                            font,
                            100),
                    (int) row.itemIcon.getX() + 18,
                    (int) row.itemIcon.getY() + 4,
                    WorkflowResumeStyle.ITEM_TEXT.toArgb());
        } else {
            WorkflowResumeRenderSupport.draw(
                    graphics,
                    font,
                    itemLabel,
                    (int) row.itemIcon.getX(),
                    (int) row.itemIcon.getY() + 4,
                    WorkflowResumeStyle.ITEM_TEXT.toArgb());
        }
        WorkflowResumeRenderSupport.draw(
                graphics,
                font,
                String.valueOf(required),
                geometry.requiredColumnX,
                (int) row.row.getY() + 4,
                WorkflowResumeStyle.PRIMARY_TEXT.toArgb());
        WorkflowResumeRenderSupport.draw(
                graphics,
                font,
                enough
                        ? String.valueOf(available)
                        : WorkflowResumeRenderSupport.text(
                                "screen.rtsbuilding.workflow.blueprint_resume.missing",
                                missing),
                geometry.availableColumnX,
                (int) row.row.getY() + 4,
                enough
                        ? WorkflowResumeStyle.SUCCESS_TEXT.toArgb()
                        : WorkflowResumeStyle.ERROR_TEXT.toArgb());
    }
}
