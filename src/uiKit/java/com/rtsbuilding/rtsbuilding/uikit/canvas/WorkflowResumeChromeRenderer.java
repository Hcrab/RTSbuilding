package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.layout.WorkflowResumeWindowLayout;
import com.rtsbuilding.rtsbuilding.uikit.theme.WorkflowResumeStyle;

/**
 * 两类恢复窗口的纯 Canvas 分隔线、动作框与缺失物品占位。
 *
 * <p>本类不绘制文本或真实 ItemStack，也不发送恢复命令。</p>
 */
public final class WorkflowResumeChromeRenderer {
    private WorkflowResumeChromeRenderer() {
    }

    public static void renderPlacement(
            UiCanvas2D canvas,
            WorkflowResumeWindowLayout.PlacementGeometry geometry,
            boolean enabled,
            double mouseX,
            double mouseY) {
        require(canvas, geometry);
        canvas.fill(geometry.topDivider, WorkflowResumeStyle.DIVIDER);
        canvas.fill(geometry.summaryDivider, WorkflowResumeStyle.DIVIDER);
        WorkflowResumeStyle.ActionKind primaryKind =
                geometry.hasConflicts
                        ? WorkflowResumeStyle.ActionKind.SKIP
                        : WorkflowResumeStyle.ActionKind.RESUME;
        frame(
                canvas,
                geometry.primaryAction,
                WorkflowResumeStyle.action(
                        primaryKind,
                        enabled,
                        geometry.primaryAction.contains(mouseX, mouseY)));
        if (geometry.secondaryAction != null) {
            frame(
                    canvas,
                    geometry.secondaryAction,
                    WorkflowResumeStyle.action(
                            WorkflowResumeStyle.ActionKind.OVERWRITE,
                            enabled,
                            geometry.secondaryAction.contains(
                                    mouseX,
                                    mouseY)));
        }
    }

    public static void renderBlueprint(
            UiCanvas2D canvas,
            WorkflowResumeWindowLayout.BlueprintGeometry geometry,
            boolean enabled,
            double mouseX,
            double mouseY) {
        require(canvas, geometry);
        canvas.fill(geometry.headerDivider, WorkflowResumeStyle.DIVIDER);
        canvas.fill(geometry.actionDivider, WorkflowResumeStyle.DIVIDER);
        frame(
                canvas,
                geometry.action,
                WorkflowResumeStyle.action(
                        WorkflowResumeStyle.ActionKind.RESUME,
                        enabled,
                        geometry.action.contains(mouseX, mouseY)));
    }

    public static void renderMissingItem(
            UiCanvas2D canvas,
            UiRect icon) {
        if (canvas == null || icon == null) {
            throw new IllegalArgumentException(
                    "canvas and icon must not be null");
        }
        canvas.fill(icon, WorkflowResumeStyle.PLACEHOLDER_BACKGROUND);
        canvas.fill(
                icon.getX(),
                icon.getY(),
                icon.getWidth() + 1.0D,
                1.0D,
                WorkflowResumeStyle.PLACEHOLDER_BORDER);
        canvas.fill(
                icon.getX(),
                icon.getY(),
                1.0D,
                icon.getHeight() + 1.0D,
                WorkflowResumeStyle.PLACEHOLDER_BORDER);
    }

    private static void frame(
            UiCanvas2D canvas,
            UiRect bounds,
            WorkflowResumeStyle.ActionVisual visual) {
        UiCompactFrameRenderer.frame(
                canvas,
                bounds,
                visual.background,
                visual.border,
                visual.darkBorder);
    }

    private static void require(
            UiCanvas2D canvas,
            Object geometry) {
        if (canvas == null || geometry == null) {
            throw new IllegalArgumentException(
                    "canvas and geometry must not be null");
        }
    }
}
