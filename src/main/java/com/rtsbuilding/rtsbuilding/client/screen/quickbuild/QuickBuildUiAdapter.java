package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeGeometryUtil;
import com.rtsbuilding.rtsbuilding.client.screen.ultimine.AreaMineShape;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillLimits;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillPlan;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiAction;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiCatalogPage;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiControl;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceParameter;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceSettings;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceTool;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiMode;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiShape;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiShapeOption;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiState;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiTransition;

import java.util.ArrayList;
import java.util.List;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.ULTIMINE_MAX_LIMIT;
import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.ULTIMINE_MIN_LIMIT;

/**
 * Quick Build 的生产适配边界：把既有控制器和独立 Build/Destroy 偏好投影为 Core 快照，
 * 再把 reducer 命令送回原有的形状、填充、连接和链式上限入口。
 *
 * <p>本类不保存第二份状态，不生成网络包，也不加入距离、冷却或额外权限门槛；
 * 原有客户端到服务端的批量建造、批量破坏链继续由 BuilderScreen 与控制器负责。</p>
 */
final class QuickBuildUiAdapter {
    private QuickBuildUiAdapter() {
    }

    static QuickBuildUiState snapshot(QuickBuildPanel panel) {
        QuickBuildUiMode mode = switch (panel.effectiveMode()) {
            case DESTROY -> QuickBuildUiMode.DESTROY;
            case SMART_FILL -> QuickBuildUiMode.SMART_FILL;
            case BUILD -> QuickBuildUiMode.BUILD;
        };
        QuickBuildUiShape buildShape = toCore(panel.getBuildModeShape());
        QuickBuildUiShape destroyShape = toCore(panel.getRangeDestroyShape());
        List<QuickBuildUiShapeOption> shapes = shapeOptions(panel, mode, buildShape, destroyShape);
        List<QuickBuildUiControl> controls = controls(panel, mode, destroyShape);

        RtsWorkflowStatus workflow = panel.uiController().findActiveDestroyWorkflow();
        int completed = workflow == null ? -1 : workflow.completedBlocks();
        int total = workflow == null ? 0 : workflow.totalBlocks();
        int remaining = workflow == null ? 0 : workflow.remainingBlocks();
        String progress = workflow == null ? "" : workflow.progressText();
        SmartFillPlan smartFillPlan = mode == QuickBuildUiMode.SMART_FILL
                ? panel.smartFillPlan() : null;
        String cost = smartFillPlan == null
                ? panel.uiScreen().currentShapeCostText()
                : Integer.toString(smartFillPlan.targets().size());
        String selectedItemId = panel.uiController().getSelectedItemId();
        long missing = missingBlocks(panel, cost, selectedItemId);
        boolean keyboardFinalConfirm = Config.isKeyboardBatchConfirmEnabled();
        String hint = mode == QuickBuildUiMode.SMART_FILL
                ? smartFillHint(panel, smartFillPlan)
                : mode == QuickBuildUiMode.DESTROY
                ? destroyShape == QuickBuildUiShape.CHAIN
                ? "screen.rtsbuilding.quick_build.chain_hint"
                : panel.isAdvancedShapeMode()
                ? keyboardFinalConfirm
                ? "screen.rtsbuilding.quick_build.destroy_advanced_box_hint"
                : "screen.rtsbuilding.quick_build.destroy_advanced_box_hint_auto"
                : keyboardFinalConfirm
                ? "screen.rtsbuilding.quick_build.destroy_hint"
                : "screen.rtsbuilding.quick_build.destroy_hint_auto"
                : keyboardFinalConfirm
                ? "screen.rtsbuilding.quick_build.build_hint"
                : "screen.rtsbuilding.quick_build.build_hint_auto";

        return new QuickBuildUiState(
                panel.isOpen(), mode, panel.canUseRangeDestroy(),
                panel.canUseRangeDestroy() ? "" : "plugin_required",
                buildShape, destroyShape, shapes, controls,
                mode == QuickBuildUiMode.SMART_FILL
                        ? QuickBuildUiCatalogPage.CONVENIENCE_TOOLS : panel.getCatalogPage(),
                panel.getConvenienceTool(),
                panel.getConvenienceSettings(),
                panel.getChainDestroyLimit(), ULTIMINE_MIN_LIMIT, ULTIMINE_MAX_LIMIT,
                completed, total, remaining, progress, cost, selectedItemId, missing,
                hint, panel.confirmKeyLabel(mode == QuickBuildUiMode.DESTROY),
                mode == QuickBuildUiMode.SMART_FILL
                        ? Integer.toString(panel.getSmartFillDiameter())
                        : panel.uiScreen().currentShapeSizeText(),
                panel.getSmartFillMaxBlocks(),
                SmartFillLimits.MIN_BLOCKS,
                SmartFillLimits.MAX_BLOCKS,
                panel.getSmartFillDiameter(),
                SmartFillLimits.MIN_DIAMETER,
                SmartFillLimits.MAX_DIAMETER,
                smartFillPlan == null ? 0 : smartFillPlan.targets().size(),
                panel.isSmartFillAnchored(),
                smartFillPlan == null ? "" : smartFillPlan.status().name());
    }

    static boolean apply(QuickBuildPanel panel, QuickBuildUiTransition transition) {
        if (transition == null || transition.command == QuickBuildUiTransition.Command.NONE) {
            return false;
        }
        QuickBuildUiAction action = transition.action;
        switch (transition.command) {
            case SELECT_MODE -> {
                if (action.mode == QuickBuildUiMode.DESTROY) {
                    panel.setMode(QuickBuildMode.DESTROY);
                } else if (action.mode == QuickBuildUiMode.SMART_FILL) {
                    panel.setMode(QuickBuildMode.SMART_FILL);
                } else if (action.mode == QuickBuildUiMode.BUILD) {
                    panel.setMode(QuickBuildMode.BUILD);
                } else return false;
            }
            case SELECT_SHAPE -> {
                if (transition.state.mode == QuickBuildUiMode.DESTROY) {
                    panel.setRangeDestroyShape(toArea(action.shape));
                } else if (transition.state.mode == QuickBuildUiMode.BUILD) {
                    panel.setBuildModeShape(toBuild(action.shape));
                } else {
                    return false;
                }
            }
            case ACTIVATE_CONTROL -> activateControl(panel, action.control, transition.state.mode);
            case SET_CHAIN_LIMIT -> panel.setChainDestroyLimit(transition.state.chainLimit);
            case SELECT_CATALOG_PAGE -> {
                if (panel.effectiveMode() == QuickBuildMode.DESTROY) {
                    panel.setCatalogPage(transition.state.catalogPage);
                } else {
                    panel.setMode(transition.state.mode == QuickBuildUiMode.SMART_FILL
                            ? QuickBuildMode.SMART_FILL : QuickBuildMode.BUILD);
                }
            }
            case SELECT_CONVENIENCE_TOOL -> panel.setConvenienceTool(action.convenienceTool);
            case SET_CONVENIENCE_PARAMETER -> panel.setConvenienceParameter(
                    action.convenienceParameter,
                    transition.state.convenienceSettings.value(action.convenienceParameter));
            case SET_SMART_FILL_MAX_BLOCKS -> panel.setSmartFillMaxBlocks(
                    transition.state.smartFillMaxBlocks);
            case SET_SMART_FILL_DIAMETER -> panel.setSmartFillDiameter(
                    transition.state.smartFillDiameter);
            case CLOSE -> panel.setOpen(false);
            default -> {
                return false;
            }
        }
        return true;
    }

    private static List<QuickBuildUiShapeOption> shapeOptions(
            QuickBuildPanel panel,
            QuickBuildUiMode mode,
            QuickBuildUiShape buildShape,
            QuickBuildUiShape destroyShape) {
        List<QuickBuildUiShapeOption> options = new ArrayList<>();
        if (mode == QuickBuildUiMode.BUILD) {
            for (BuildShape shape : BuildShape.values()) {
                QuickBuildUiShape id = toCore(shape);
                options.add(new QuickBuildUiShapeOption(id, id == buildShape, true, ""));
            }
            return options;
        }
        if (mode == QuickBuildUiMode.SMART_FILL) {
            return options;
        }
        AreaMineShape[] order = {
                AreaMineShape.CHAIN, AreaMineShape.BLOCK, AreaMineShape.LINE,
                AreaMineShape.SQUARE, AreaMineShape.WALL, AreaMineShape.CIRCLE,
                AreaMineShape.CYLINDER, AreaMineShape.BALL, AreaMineShape.BOX
        };
        for (AreaMineShape shape : order) {
            boolean enabled = panel.canUseDestroyShape(shape);
            QuickBuildUiShape id = toCore(shape);
            options.add(new QuickBuildUiShapeOption(
                    id, id == destroyShape, enabled, enabled ? "" : "plugin_required"));
        }
        return options;
    }

    private static List<QuickBuildUiControl> controls(
            QuickBuildPanel panel,
            QuickBuildUiMode mode,
            QuickBuildUiShape destroyShape) {
        List<QuickBuildUiControl> controls = new ArrayList<>();
        if (mode == QuickBuildUiMode.SMART_FILL
                || (mode == QuickBuildUiMode.DESTROY && destroyShape == QuickBuildUiShape.CHAIN)
                || (mode == QuickBuildUiMode.DESTROY
                && panel.getCatalogPage() == QuickBuildUiCatalogPage.CONVENIENCE_TOOLS)) {
            return controls;
        }
        ShapeFillMode active = mode == QuickBuildUiMode.DESTROY
                ? panel.uiScreen().getShapeController().getDestroyShapeFillMode()
                : panel.uiScreen().getShapeController().getBuildShapeFillMode();
        for (ShapeFillMode fill : ShapeGeometryUtil.availableFillModes(
                panel.uiController().getBuildShape())) {
            controls.add(new QuickBuildUiControl(
                    QuickBuildUiControl.Id.valueOf(fill.name()),
                    panel.uiScreen().fillModeLabel(fill), fill == active, true));
        }
        BuildShape shape = panel.activeAdvancedShape();
        if (QuickBuildPanel.supportsVerticalToggle(shape)) {
            controls.add(new QuickBuildUiControl(
                    QuickBuildUiControl.Id.VERTICAL,
                    panel.uiScreen().text("screen.rtsbuilding.quick_build.vertical"),
                    panel.isRoundShapeVertical(shape), true));
        }
        if (QuickBuildPanel.supportsAdvancedShape(shape)) {
            controls.add(new QuickBuildUiControl(
                    QuickBuildUiControl.Id.ADVANCED,
                    panel.uiScreen().text("screen.rtsbuilding.quick_build.advanced_box"),
                    panel.isAdvancedShape(shape), true));
        }
        if (shape == BuildShape.LINE || shape == BuildShape.WALL) {
            boolean connected = mode == QuickBuildUiMode.DESTROY
                    ? panel.uiScreen().getShapeController().isDestroyLineConnected()
                    : panel.uiScreen().getShapeController().isBuildLineConnected();
            controls.add(new QuickBuildUiControl(
                    QuickBuildUiControl.Id.CONNECT,
                    panel.uiScreen().text("screen.rtsbuilding.quick_build.connect"),
                    connected, true));
        }
        return controls;
    }

    private static String smartFillHint(QuickBuildPanel panel, SmartFillPlan plan) {
        if (panel.isSmartFillAnchored()) {
            return "screen.rtsbuilding.quick_build.smart_fill.hint_confirm";
        }
        if (plan != null && plan.canSubmit()) {
            return plan.partial()
                    ? "screen.rtsbuilding.quick_build.smart_fill.hint_partial"
                    : "screen.rtsbuilding.quick_build.smart_fill.hint_ready";
        }
        return "screen.rtsbuilding.quick_build.smart_fill.hint_aim";
    }

    private static void activateControl(
            QuickBuildPanel panel,
            QuickBuildUiControl.Id id,
            QuickBuildUiMode mode) {
        if (id == null) {
            return;
        }
        if (id == QuickBuildUiControl.Id.FILL
                || id == QuickBuildUiControl.Id.HOLLOW
                || id == QuickBuildUiControl.Id.SKELETON) {
            ShapeFillMode fill = ShapeFillMode.valueOf(id.name());
            if (mode == QuickBuildUiMode.DESTROY) {
                panel.uiScreen().getShapeController().setDestroyShapeFillMode(fill);
            } else {
                panel.uiScreen().getShapeController().setBuildShapeFillMode(fill);
            }
        } else if (id == QuickBuildUiControl.Id.VERTICAL) {
            BuildShape shape = panel.activeAdvancedShape();
            panel.setRoundShapeVertical(shape, !panel.isRoundShapeVertical(shape));
            panel.uiScreen().clearShapeBuildSession();
        } else if (id == QuickBuildUiControl.Id.ADVANCED) {
            BuildShape shape = panel.activeAdvancedShape();
            panel.setAdvancedShape(shape, !panel.isAdvancedShape(shape));
            panel.uiScreen().clearShapeBuildSession();
        } else if (id == QuickBuildUiControl.Id.CONNECT) {
            if (mode == QuickBuildUiMode.DESTROY) {
                panel.uiScreen().getShapeController().setDestroyLineConnected(
                        !panel.uiScreen().getShapeController().isDestroyLineConnected());
            } else {
                panel.uiScreen().getShapeController().setBuildLineConnected(
                        !panel.uiScreen().getShapeController().isBuildLineConnected());
            }
        } else {
            return;
        }
        panel.uiScreen().persistUiState();
        panel.rebuildFillModeButtons();
    }

    private static long missingBlocks(QuickBuildPanel panel, String cost, String selectedItemId) {
        if (selectedItemId == null || selectedItemId.isBlank()) {
            return 0L;
        }
        try {
            return Math.max(0L, Long.parseLong(cost)
                    - panel.uiController().getStorageTotalCount(selectedItemId));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private static QuickBuildUiShape toCore(BuildShape shape) {
        return QuickBuildUiShape.valueOf((shape == null ? BuildShape.BLOCK : shape).name());
    }

    private static QuickBuildUiShape toCore(AreaMineShape shape) {
        return QuickBuildUiShape.valueOf((shape == null ? AreaMineShape.CHAIN : shape).name());
    }

    private static BuildShape toBuild(QuickBuildUiShape shape) {
        return shape == null || shape == QuickBuildUiShape.CHAIN
                ? BuildShape.BLOCK : BuildShape.valueOf(shape.name());
    }

    private static AreaMineShape toArea(QuickBuildUiShape shape) {
        return shape == null ? AreaMineShape.CHAIN : AreaMineShape.valueOf(shape.name());
    }
}
