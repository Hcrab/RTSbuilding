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
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceSettings;
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
 * Forge 1.12.2 Quick Build 平台适配器。
 *
 * <p>它把真实形状控制器、Destroy 插件门禁、目录偏好、工作流和 Smart Fill/便利工具预览投影为
 * Core 快照；reducer 命令再在这里回到已有客户端业务入口。布局、绘制和服务端实现均不归本类管理。</p>
 */
final class QuickBuildUiAdapter {
    private QuickBuildUiAdapter() {
    }

    static QuickBuildUiState snapshot(QuickBuildPanel panel) {
        QuickBuildUiMode mode;
        if (panel.effectiveMode() == QuickBuildMode.DESTROY) {
            mode = QuickBuildUiMode.DESTROY;
        } else if (panel.effectiveMode() == QuickBuildMode.SMART_FILL) {
            mode = QuickBuildUiMode.SMART_FILL;
        } else {
            mode = QuickBuildUiMode.BUILD;
        }

        QuickBuildUiShape buildShape = toCore(panel.getBuildModeShape());
        QuickBuildUiShape destroyShape = toCore(panel.getRangeDestroyShape());
        List<QuickBuildUiShapeOption> shapes = new ArrayList<QuickBuildUiShapeOption>();
        if (mode == QuickBuildUiMode.BUILD) {
            for (BuildShape shape : BuildShape.values()) {
                QuickBuildUiShape id = toCore(shape);
                shapes.add(new QuickBuildUiShapeOption(id, id == buildShape, true, ""));
            }
        } else if (mode == QuickBuildUiMode.DESTROY) {
            AreaMineShape[] order = { AreaMineShape.CHAIN, AreaMineShape.BLOCK,
                    AreaMineShape.LINE, AreaMineShape.SQUARE, AreaMineShape.WALL,
                    AreaMineShape.CIRCLE, AreaMineShape.CYLINDER, AreaMineShape.BALL,
                    AreaMineShape.BOX };
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
        List<QuickBuildUiControl> controls = new ArrayList<QuickBuildUiControl>();
        boolean convenience = mode == QuickBuildUiMode.DESTROY
                && panel.getCatalogPage() == QuickBuildUiCatalogPage.CONVENIENCE_TOOLS;
        if (mode != QuickBuildUiMode.SMART_FILL && !convenience
                && !(mode == QuickBuildUiMode.DESTROY
                && destroyShape == QuickBuildUiShape.CHAIN)) {
            for (ShapeFillMode option : ShapeGeometryUtil.availableFillModes(
                    panel.uiController().getBuildShape())) {
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
                        panel.uiScreen().text("screen.rtsbuilding.quick_build.connect"),
                        connected, true));
            }
            if (mode == QuickBuildUiMode.BUILD && panel.hasCreativePlayer()) {
                controls.add(new QuickBuildUiControl(QuickBuildUiControl.Id.OVERWRITE,
                        panel.uiScreen().text("screen.rtsbuilding.quick_build.overwrite"),
                        panel.isOverwriteSelected(), true));
            }
        }

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
        String selectedId = panel.uiController().getSelectedItemId();
        long missing = 0L;
        if ((mode == QuickBuildUiMode.BUILD || mode == QuickBuildUiMode.SMART_FILL)
                && selectedId != null && !selectedId.isEmpty()) {
            try {
                missing = Math.max(0L, Long.parseLong(cost)
                        - panel.uiController().getStorageTotalCount(selectedId));
            } catch (NumberFormatException ignored) {
                // 显示层遇到非数字成本时只省略缺料数，不能阻断既有建造流程。
            }
        }

        boolean keyboardFinalConfirm = Config.isKeyboardBatchConfirmEnabled();
        String hint = mode == QuickBuildUiMode.SMART_FILL
                ? smartFillHint(panel, smartFillPlan)
                : convenience
                ? panel.convenienceHintKey()
                : mode == QuickBuildUiMode.BUILD
                ? (keyboardFinalConfirm ? "screen.rtsbuilding.quick_build.build_hint"
                : "screen.rtsbuilding.quick_build.build_hint_auto")
                : destroyShape == QuickBuildUiShape.CHAIN
                ? "screen.rtsbuilding.quick_build.chain_hint"
                : panel.isAdvancedShapeMode()
                ? (keyboardFinalConfirm
                ? "screen.rtsbuilding.quick_build.destroy_advanced_box_hint"
                : "screen.rtsbuilding.quick_build.destroy_advanced_box_hint_auto")
                : (keyboardFinalConfirm ? "screen.rtsbuilding.quick_build.destroy_hint"
                : "screen.rtsbuilding.quick_build.destroy_hint_auto");

        QuickBuildUiConvenienceSettings settings = panel.getConvenienceSettings();
        return new QuickBuildUiState(panel.isOpen(), mode, panel.canUseRangeDestroy(),
                panel.canUseRangeDestroy() ? "" : "plugin_required",
                buildShape, destroyShape, shapes, controls,
                mode == QuickBuildUiMode.SMART_FILL
                        ? QuickBuildUiCatalogPage.CONVENIENCE_TOOLS
                        : panel.getCatalogPage(),
                panel.getConvenienceTool(), settings,
                panel.getChainDestroyLimit(), ULTIMINE_MIN_LIMIT, ULTIMINE_MAX_LIMIT,
                completed, total, remaining, progress, cost, selectedId, missing,
                hint, panel.confirmKeyLabel(mode == QuickBuildUiMode.DESTROY),
                mode == QuickBuildUiMode.SMART_FILL
                        ? Integer.toString(panel.getSmartFillDiameter())
                        : (convenience ? panel.convenienceDimensionLabel()
                        : panel.uiScreen().currentShapeSizeText()),
                panel.getSmartFillMaxBlocks(), SmartFillLimits.MIN_BLOCKS,
                SmartFillLimits.MAX_BLOCKS, panel.getSmartFillDiameter(),
                SmartFillLimits.MIN_DIAMETER, SmartFillLimits.MAX_DIAMETER,
                smartFillPlan == null ? 0 : smartFillPlan.targets().size(),
                panel.isSmartFillAnchored(),
                smartFillPlan == null ? "" : smartFillPlan.status().name());
    }

    static void apply(QuickBuildPanel panel, QuickBuildUiTransition transition) {
        if (transition == null || transition.command == QuickBuildUiTransition.Command.NONE) {
            return;
        }
        QuickBuildUiAction action = transition.action;
        switch (transition.command) {
            case SELECT_MODE:
                if (action.mode == QuickBuildUiMode.DESTROY) {
                    panel.setMode(QuickBuildMode.DESTROY);
                } else if (action.mode == QuickBuildUiMode.SMART_FILL) {
                    panel.setMode(QuickBuildMode.SMART_FILL);
                } else {
                    panel.setMode(QuickBuildMode.BUILD);
                }
                break;
            case SELECT_SHAPE:
                if (transition.state.mode == QuickBuildUiMode.DESTROY) {
                    panel.setRangeDestroyShape(toArea(action.shape));
                } else {
                    panel.setBuildModeShape(toBuild(action.shape));
                }
                break;
            case ACTIVATE_CONTROL:
                activateControl(panel, action.control, transition.state.mode);
                break;
            case SET_CHAIN_LIMIT:
                panel.setChainDestroyLimit(transition.state.chainLimit);
                break;
            case SELECT_CATALOG_PAGE:
                if (panel.effectiveMode() == QuickBuildMode.DESTROY) {
                    panel.setCatalogPage(transition.state.catalogPage);
                } else {
                    panel.setMode(transition.state.mode == QuickBuildUiMode.SMART_FILL
                            ? QuickBuildMode.SMART_FILL : QuickBuildMode.BUILD);
                }
                break;
            case SELECT_CONVENIENCE_TOOL:
                panel.setConvenienceTool(transition.state.convenienceTool);
                break;
            case SET_CONVENIENCE_PARAMETER:
                panel.setConvenienceParameter(action.convenienceParameter,
                        transition.state.convenienceSettings.value(action.convenienceParameter));
                break;
            case SET_SMART_FILL_MAX_BLOCKS:
                panel.setSmartFillMaxBlocks(transition.state.smartFillMaxBlocks);
                break;
            case SET_SMART_FILL_DIAMETER:
                panel.setSmartFillDiameter(transition.state.smartFillDiameter);
                break;
            case CLOSE:
                panel.setOpen(false);
                break;
            default:
                break;
        }
    }

    private static String smartFillHint(QuickBuildPanel panel, SmartFillPlan plan) {
        if (panel.isSmartFillAnchored()) {
            return "screen.rtsbuilding.quick_build.smart_fill.hint_confirm";
        }
        if (plan != null && plan.canSubmit()) {
            return plan.partial() ? "screen.rtsbuilding.quick_build.smart_fill.hint_partial"
                    : "screen.rtsbuilding.quick_build.smart_fill.hint_ready";
        }
        return "screen.rtsbuilding.quick_build.smart_fill.hint_aim";
    }

    private static void activateControl(QuickBuildPanel panel, QuickBuildUiControl.Id id,
            QuickBuildUiMode mode) {
        if (id == QuickBuildUiControl.Id.FILL || id == QuickBuildUiControl.Id.HOLLOW
                || id == QuickBuildUiControl.Id.SKELETON) {
            ShapeFillMode value = ShapeFillMode.valueOf(id.name());
            if (mode == QuickBuildUiMode.DESTROY) {
                panel.uiScreen().getShapeController().setDestroyShapeFillMode(value);
            } else {
                panel.uiScreen().getShapeController().setBuildShapeFillMode(value);
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
        } else if (id == QuickBuildUiControl.Id.OVERWRITE && mode == QuickBuildUiMode.BUILD
                && panel.hasCreativePlayer()) {
            panel.setOverwriteSelected(!panel.isOverwriteSelected());
            panel.uiScreen().clearShapeBuildSession();
        }
        panel.uiScreen().persistUiState();
        panel.rebuildFillModeButtons();
    }

    private static QuickBuildUiControl.Id control(ShapeFillMode mode) {
        return QuickBuildUiControl.Id.valueOf(mode.name());
    }

    private static QuickBuildUiShape toCore(BuildShape shape) {
        return QuickBuildUiShape.valueOf(shape.name());
    }

    private static QuickBuildUiShape toCore(AreaMineShape shape) {
        return QuickBuildUiShape.valueOf(shape.name());
    }

    private static BuildShape toBuild(QuickBuildUiShape shape) {
        return shape == QuickBuildUiShape.CHAIN
                ? BuildShape.BLOCK : BuildShape.valueOf(shape.name());
    }

    private static AreaMineShape toArea(QuickBuildUiShape shape) {
        return AreaMineShape.valueOf(shape.name());
    }
}
