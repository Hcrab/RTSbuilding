package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.client.screen.ultimine.AreaMineShape;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeGeometryUtil;
import com.rtsbuilding.rtsbuilding.common.shape.model.ShapeFillMode;
import com.rtsbuilding.rtsbuilding.server.workflow.model.RtsWorkflowStatus;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiAction;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiControl;
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
 * 1.21.1 Quick Build 平台适配器。
 *
 * <p>负责把真实形状控制器、插件权限、工作流、按键文本和储存数量投影成 Core；
 * 也负责执行 reducer 命令。它不拥有布局或绘制，且必须保持 BUILD/DESTROY 两套
 * 独立填充、连接和形状状态不互相覆盖。</p>
 */
final class QuickBuildUiAdapter {
    private QuickBuildUiAdapter() {}

    static QuickBuildUiState snapshot(QuickBuildPanel panel) {
        QuickBuildUiMode mode = panel.effectiveMode() == QuickBuildMode.DESTROY
                ? QuickBuildUiMode.DESTROY : QuickBuildUiMode.BUILD;
        QuickBuildUiShape buildShape = toCore(panel.getBuildModeShape());
        QuickBuildUiShape destroyShape = toCore(panel.getRangeDestroyShape());
        List<QuickBuildUiShapeOption> shapes = new ArrayList<>();
        if (mode == QuickBuildUiMode.BUILD) {
            for (BuildShape shape : BuildShape.values()) {
                QuickBuildUiShape id = toCore(shape);
                shapes.add(new QuickBuildUiShapeOption(id, id == buildShape, true, ""));
            }
        } else {
            AreaMineShape[] order = { AreaMineShape.CHAIN, AreaMineShape.BLOCK, AreaMineShape.LINE,
                    AreaMineShape.SQUARE, AreaMineShape.WALL, AreaMineShape.CIRCLE,
                    AreaMineShape.CYLINDER, AreaMineShape.BALL, AreaMineShape.BOX };
            for (AreaMineShape shape : order) {
                boolean enabled = panel.canUseDestroyShape(shape);
                QuickBuildUiShape id = toCore(shape);
                shapes.add(new QuickBuildUiShapeOption(id, id == destroyShape, enabled,
                        enabled ? "" : "plugin_required"));
            }
        }

        BuildShape activeShape = panel.activeAdvancedShape();
        ShapeFillMode fill = mode == QuickBuildUiMode.DESTROY
                ? panel.uiScreen().getShapeController().getDestroyShapeFillMode()
                : panel.uiScreen().getShapeController().getBuildShapeFillMode();
        List<QuickBuildUiControl> controls = new ArrayList<>();
        if (!(mode == QuickBuildUiMode.DESTROY && destroyShape == QuickBuildUiShape.CHAIN)) {
            for (ShapeFillMode option : ShapeGeometryUtil.availableFillModes(panel.uiController().getBuildShape())) {
                controls.add(new QuickBuildUiControl(control(option),
                        panel.uiScreen().fillModeLabel(option), option == fill, true));
            }
            if (QuickBuildPanel.supportsVerticalToggle(activeShape)) {
                controls.add(new QuickBuildUiControl(QuickBuildUiControl.Id.VERTICAL,
                        panel.uiScreen().text("screen.rtsbuilding.quick_build.vertical"),
                        panel.isRoundShapeVertical(activeShape), true));
            }
            if (QuickBuildPanel.supportsAdvancedShape(activeShape)) {
                controls.add(new QuickBuildUiControl(QuickBuildUiControl.Id.ADVANCED,
                        panel.uiScreen().text("screen.rtsbuilding.quick_build.advanced_box"),
                        panel.isAdvancedShape(activeShape), true));
            }
            if (activeShape == BuildShape.LINE || activeShape == BuildShape.WALL) {
                boolean connected = mode == QuickBuildUiMode.DESTROY
                        ? panel.uiScreen().getShapeController().isDestroyLineConnected()
                        : panel.uiScreen().getShapeController().isBuildLineConnected();
                controls.add(new QuickBuildUiControl(QuickBuildUiControl.Id.CONNECT,
                        panel.uiScreen().text("screen.rtsbuilding.quick_build.connect"), connected, true));
            }
        }

        RtsWorkflowStatus workflow = panel.uiController().findActiveDestroyWorkflow();
        int completed = workflow == null ? -1 : workflow.completedBlocks();
        int total = workflow == null ? 0 : workflow.totalBlocks();
        int remaining = workflow == null ? 0 : workflow.remainingBlocks();
        String progress = workflow == null ? "" : workflow.progressText();
        String cost = panel.uiScreen().currentShapeCostText();
        String selectedId = panel.uiController().getSelectedItemId();
        long missing = 0L;
        if (mode == QuickBuildUiMode.BUILD && !selectedId.isBlank()) {
            try {
                missing = Math.max(0L, Long.parseLong(cost)
                        - panel.uiController().getStorageTotalCount(selectedId));
            } catch (NumberFormatException ignored) { }
        }
        String hint = mode == QuickBuildUiMode.BUILD
                ? "screen.rtsbuilding.quick_build.build_hint"
                : destroyShape == QuickBuildUiShape.CHAIN
                ? "screen.rtsbuilding.quick_build.chain_hint"
                : panel.isAdvancedShapeMode()
                ? "screen.rtsbuilding.quick_build.destroy_advanced_box_hint"
                : "screen.rtsbuilding.quick_build.destroy_hint";
        return new QuickBuildUiState(panel.isOpen(), mode, panel.canUseRangeDestroy(),
                panel.canUseRangeDestroy() ? "" : "plugin_required",
                buildShape, destroyShape, shapes, controls,
                panel.getChainDestroyLimit(), ULTIMINE_MIN_LIMIT, ULTIMINE_MAX_LIMIT,
                completed, total, remaining, progress, cost, selectedId, missing,
                hint, panel.confirmKeyLabel(mode == QuickBuildUiMode.DESTROY),
                panel.uiScreen().currentShapeSizeText());
    }

    static void apply(QuickBuildPanel panel, QuickBuildUiTransition transition) {
        if (transition == null || transition.command == QuickBuildUiTransition.Command.NONE) return;
        QuickBuildUiAction action = transition.action;
        switch (transition.command) {
            case SELECT_MODE -> panel.setMode(action.mode == QuickBuildUiMode.DESTROY
                    ? QuickBuildMode.DESTROY : QuickBuildMode.BUILD);
            case SELECT_SHAPE -> {
                if (transition.state.mode == QuickBuildUiMode.DESTROY) {
                    panel.setRangeDestroyShape(toArea(action.shape));
                } else {
                    panel.setBuildModeShape(toBuild(action.shape));
                }
            }
            case ACTIVATE_CONTROL -> activateControl(panel, action.control, transition.state.mode);
            case SET_CHAIN_LIMIT -> panel.setChainDestroyLimit(transition.state.chainLimit);
            case CLOSE -> panel.setOpen(false);
            default -> { }
        }
    }

    private static void activateControl(QuickBuildPanel panel, QuickBuildUiControl.Id id,
                                        QuickBuildUiMode mode) {
        if (id == QuickBuildUiControl.Id.FILL || id == QuickBuildUiControl.Id.HOLLOW
                || id == QuickBuildUiControl.Id.SKELETON) {
            ShapeFillMode value = ShapeFillMode.valueOf(id.name());
            if (mode == QuickBuildUiMode.DESTROY) panel.uiScreen().getShapeController().setDestroyShapeFillMode(value);
            else panel.uiScreen().getShapeController().setBuildShapeFillMode(value);
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
        }
        panel.uiScreen().persistUiState();
        panel.rebuildFillModeButtons();
    }

    private static QuickBuildUiControl.Id control(ShapeFillMode mode) {
        return QuickBuildUiControl.Id.valueOf(mode.name());
    }
    private static QuickBuildUiShape toCore(BuildShape shape){return QuickBuildUiShape.valueOf(shape.name());}
    private static QuickBuildUiShape toCore(AreaMineShape shape){return QuickBuildUiShape.valueOf(shape.name());}
    private static BuildShape toBuild(QuickBuildUiShape shape){return shape == QuickBuildUiShape.CHAIN
            ? BuildShape.BLOCK : BuildShape.valueOf(shape.name());}
    private static AreaMineShape toArea(QuickBuildUiShape shape){return AreaMineShape.valueOf(shape.name());}
}
