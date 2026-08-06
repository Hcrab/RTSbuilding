package com.rtsbuilding.rtsbuilding.uicore.quickbuild;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 快速建造/破坏窗口全部玩家可见状态的纯 Java 快照。
 *
 * <p>Smart Fill 仍是 Build 工具页的内部状态，而不是第三种顶栏主模式；便利工具只在 Destroy
 * 的工具目录生效。该约束在 Core 中统一维护，生产与离屏输入不会产生不同的页面投影。</p>
 */
public final class QuickBuildUiState {
    public final boolean open;
    public final QuickBuildUiMode mode;
    public final boolean destroyEnabled;
    public final String destroyDisabledReason;
    public final QuickBuildUiShape buildShape;
    public final QuickBuildUiShape destroyShape;
    public final List<QuickBuildUiShapeOption> shapes;
    public final List<QuickBuildUiControl> controls;
    public final QuickBuildUiCatalogPage catalogPage;
    public final QuickBuildUiConvenienceTool convenienceTool;
    public final QuickBuildUiConvenienceSettings convenienceSettings;
    public final int chainLimit;
    public final int chainMinimum;
    public final int chainMaximum;
    public final int progressCompleted;
    public final int progressTotal;
    public final int remainingBlocks;
    public final String progressText;
    public final String costText;
    public final String selectedItemId;
    public final long missingBlocks;
    public final String hintKey;
    public final String confirmKeyLabel;
    public final String dimensions;
    public final int smartFillMaxBlocks;
    public final int smartFillMinBlocks;
    public final int smartFillMaxBlocksLimit;
    public final int smartFillDiameter;
    public final int smartFillMinDiameter;
    public final int smartFillMaxDiameter;
    public final int smartFillTargetCount;
    public final boolean smartFillAnchored;
    public final String smartFillStatus;

    /** 保留旧构造入口，未使用新目录时默认投影到形状页。 */
    public QuickBuildUiState(boolean open, QuickBuildUiMode mode,
            boolean destroyEnabled, String destroyDisabledReason,
            QuickBuildUiShape buildShape, QuickBuildUiShape destroyShape,
            List<QuickBuildUiShapeOption> shapes, List<QuickBuildUiControl> controls,
            int chainLimit, int chainMinimum, int chainMaximum,
            int progressCompleted, int progressTotal, int remainingBlocks,
            String progressText, String costText, String selectedItemId,
            long missingBlocks, String hintKey, String confirmKeyLabel,
            String dimensions) {
        this(open, mode, destroyEnabled, destroyDisabledReason,
                buildShape, destroyShape, shapes, controls,
                QuickBuildUiCatalogPage.SHAPES,
                QuickBuildUiConvenienceTool.REPEAT_BOX,
                QuickBuildUiConvenienceSettings.DEFAULT,
                chainLimit, chainMinimum, chainMaximum,
                progressCompleted, progressTotal, remainingBlocks,
                progressText, costText, selectedItemId, missingBlocks,
                hintKey, confirmKeyLabel, dimensions,
                512, 1, 1024, 16, 3, 32, 0, false, "");
    }

    public QuickBuildUiState(boolean open, QuickBuildUiMode mode,
            boolean destroyEnabled, String destroyDisabledReason,
            QuickBuildUiShape buildShape, QuickBuildUiShape destroyShape,
            List<QuickBuildUiShapeOption> shapes, List<QuickBuildUiControl> controls,
            QuickBuildUiCatalogPage catalogPage,
            QuickBuildUiConvenienceTool convenienceTool,
            QuickBuildUiConvenienceSettings convenienceSettings,
            int chainLimit, int chainMinimum, int chainMaximum,
            int progressCompleted, int progressTotal, int remainingBlocks,
            String progressText, String costText, String selectedItemId,
            long missingBlocks, String hintKey, String confirmKeyLabel,
            String dimensions) {
        this(open, mode, destroyEnabled, destroyDisabledReason,
                buildShape, destroyShape, shapes, controls,
                catalogPage, convenienceTool, convenienceSettings,
                chainLimit, chainMinimum, chainMaximum,
                progressCompleted, progressTotal, remainingBlocks,
                progressText, costText, selectedItemId, missingBlocks,
                hintKey, confirmKeyLabel, dimensions,
                512, 1, 1024, 16, 3, 32, 0, false, "");
    }

    public QuickBuildUiState(boolean open, QuickBuildUiMode mode,
            boolean destroyEnabled, String destroyDisabledReason,
            QuickBuildUiShape buildShape, QuickBuildUiShape destroyShape,
            List<QuickBuildUiShapeOption> shapes, List<QuickBuildUiControl> controls,
            QuickBuildUiCatalogPage catalogPage,
            QuickBuildUiConvenienceTool convenienceTool,
            QuickBuildUiConvenienceSettings convenienceSettings,
            int chainLimit, int chainMinimum, int chainMaximum,
            int progressCompleted, int progressTotal, int remainingBlocks,
            String progressText, String costText, String selectedItemId,
            long missingBlocks, String hintKey, String confirmKeyLabel,
            String dimensions,
            int smartFillMaxBlocks, int smartFillMinBlocks, int smartFillMaxBlocksLimit,
            int smartFillDiameter, int smartFillMinDiameter, int smartFillMaxDiameter,
            int smartFillTargetCount, boolean smartFillAnchored, String smartFillStatus) {
        this.open = open;
        this.destroyEnabled = destroyEnabled;
        this.mode = mode == QuickBuildUiMode.DESTROY && !destroyEnabled
                ? QuickBuildUiMode.BUILD : (mode == null ? QuickBuildUiMode.BUILD : mode);
        this.destroyDisabledReason = safe(destroyDisabledReason);
        this.buildShape = buildShape == null || buildShape == QuickBuildUiShape.CHAIN
                ? QuickBuildUiShape.BLOCK : buildShape;
        this.destroyShape = destroyShape == null ? QuickBuildUiShape.CHAIN : destroyShape;
        this.shapes = immutable(shapes);
        this.controls = immutable(controls);
        this.catalogPage = this.mode == QuickBuildUiMode.SMART_FILL
                ? QuickBuildUiCatalogPage.CONVENIENCE_TOOLS
                : (this.mode == QuickBuildUiMode.DESTROY && catalogPage != null
                ? catalogPage : QuickBuildUiCatalogPage.SHAPES);
        this.convenienceTool = convenienceTool == null
                ? QuickBuildUiConvenienceTool.REPEAT_BOX : convenienceTool;
        this.convenienceSettings = convenienceSettings == null
                ? QuickBuildUiConvenienceSettings.DEFAULT : convenienceSettings;
        this.chainMinimum = Math.max(1, chainMinimum);
        this.chainMaximum = Math.max(this.chainMinimum, chainMaximum);
        this.chainLimit = clamp(chainLimit, this.chainMinimum, this.chainMaximum);
        this.progressCompleted = Math.max(-1, progressCompleted);
        this.progressTotal = Math.max(0, progressTotal);
        this.remainingBlocks = Math.max(0, remainingBlocks);
        this.progressText = safe(progressText);
        this.costText = safe(costText);
        this.selectedItemId = safe(selectedItemId);
        this.missingBlocks = Math.max(0L, missingBlocks);
        this.hintKey = safe(hintKey);
        this.confirmKeyLabel = safe(confirmKeyLabel);
        this.dimensions = safe(dimensions);
        this.smartFillMinBlocks = Math.max(1, smartFillMinBlocks);
        this.smartFillMaxBlocksLimit = Math.max(this.smartFillMinBlocks, smartFillMaxBlocksLimit);
        this.smartFillMaxBlocks = clamp(smartFillMaxBlocks,
                this.smartFillMinBlocks, this.smartFillMaxBlocksLimit);
        this.smartFillMinDiameter = Math.max(1, smartFillMinDiameter);
        this.smartFillMaxDiameter = Math.max(this.smartFillMinDiameter, smartFillMaxDiameter);
        this.smartFillDiameter = clamp(smartFillDiameter,
                this.smartFillMinDiameter, this.smartFillMaxDiameter);
        this.smartFillTargetCount = Math.max(0, smartFillTargetCount);
        this.smartFillAnchored = smartFillAnchored;
        this.smartFillStatus = safe(smartFillStatus);
    }

    public QuickBuildUiShape activeShape() {
        return mode == QuickBuildUiMode.DESTROY ? destroyShape : buildShape;
    }

    public boolean chainMode() {
        return mode == QuickBuildUiMode.DESTROY && destroyShape == QuickBuildUiShape.CHAIN;
    }

    public boolean convenienceMode() {
        return mode == QuickBuildUiMode.DESTROY
                && catalogPage == QuickBuildUiCatalogPage.CONVENIENCE_TOOLS;
    }

    public QuickBuildUiControl control(QuickBuildUiControl.Id id) {
        for (QuickBuildUiControl control : controls) {
            if (control.id == id) {
                return control;
            }
        }
        return null;
    }

    public QuickBuildUiState withMode(QuickBuildUiMode value) {
        return copy(open, value, buildShape, destroyShape, shapes, controls, chainLimit);
    }

    public QuickBuildUiState withShape(QuickBuildUiShape value) {
        QuickBuildUiShape nextBuild = mode == QuickBuildUiMode.BUILD ? value : buildShape;
        QuickBuildUiShape nextDestroy = mode == QuickBuildUiMode.DESTROY ? value : destroyShape;
        List<QuickBuildUiShapeOption> next = new ArrayList<QuickBuildUiShapeOption>();
        for (QuickBuildUiShapeOption option : shapes) {
            next.add(new QuickBuildUiShapeOption(
                    option.shape, option.shape == value, option.enabled, option.disabledReason));
        }
        return copy(open, mode, nextBuild, nextDestroy, next, controls, chainLimit);
    }

    public QuickBuildUiState withControl(QuickBuildUiControl.Id id) {
        List<QuickBuildUiControl> next = new ArrayList<QuickBuildUiControl>();
        boolean exclusiveFill = id == QuickBuildUiControl.Id.FILL
                || id == QuickBuildUiControl.Id.HOLLOW || id == QuickBuildUiControl.Id.SKELETON;
        for (QuickBuildUiControl control : controls) {
            boolean selected = exclusiveFill
                    ? ((control.id == QuickBuildUiControl.Id.FILL
                    || control.id == QuickBuildUiControl.Id.HOLLOW
                    || control.id == QuickBuildUiControl.Id.SKELETON)
                    ? control.id == id : control.selected)
                    : (control.id == id ? !control.selected : control.selected);
            next.add(control.withSelected(selected));
        }
        return copy(open, mode, buildShape, destroyShape, shapes, next, chainLimit);
    }

    public QuickBuildUiState withChainLimit(int value) {
        return copy(open, mode, buildShape, destroyShape, shapes, controls, value);
    }

    public QuickBuildUiState withCatalogPage(QuickBuildUiCatalogPage value) {
        return newState(open, mode, buildShape, destroyShape, shapes, controls,
                value, convenienceTool, convenienceSettings, chainLimit,
                smartFillMaxBlocks, smartFillDiameter);
    }

    public QuickBuildUiState withConvenienceTool(QuickBuildUiConvenienceTool value) {
        return newState(open, mode, buildShape, destroyShape, shapes, controls,
                QuickBuildUiCatalogPage.CONVENIENCE_TOOLS, value, convenienceSettings, chainLimit,
                smartFillMaxBlocks, smartFillDiameter);
    }

    public QuickBuildUiState withConvenienceParameter(
            QuickBuildUiConvenienceParameter parameter, int value) {
        return newState(open, mode, buildShape, destroyShape, shapes, controls,
                catalogPage, convenienceTool, convenienceSettings.with(parameter, value), chainLimit,
                smartFillMaxBlocks, smartFillDiameter);
    }

    public QuickBuildUiState withSmartFillMaxBlocks(int value) {
        return newState(open, mode, buildShape, destroyShape, shapes, controls,
                catalogPage, convenienceTool, convenienceSettings, chainLimit,
                value, smartFillDiameter);
    }

    public QuickBuildUiState withSmartFillDiameter(int value) {
        return newState(open, mode, buildShape, destroyShape, shapes, controls,
                catalogPage, convenienceTool, convenienceSettings, chainLimit,
                smartFillMaxBlocks, value);
    }

    public QuickBuildUiState closed() {
        return copy(false, mode, buildShape, destroyShape, shapes, controls, chainLimit);
    }

    private QuickBuildUiState copy(boolean nextOpen, QuickBuildUiMode nextMode,
            QuickBuildUiShape nextBuild, QuickBuildUiShape nextDestroy,
            List<QuickBuildUiShapeOption> nextShapes, List<QuickBuildUiControl> nextControls,
            int nextLimit) {
        return newState(nextOpen, nextMode, nextBuild, nextDestroy, nextShapes, nextControls,
                nextMode == QuickBuildUiMode.DESTROY ? catalogPage
                        : (nextMode == QuickBuildUiMode.SMART_FILL
                        ? QuickBuildUiCatalogPage.CONVENIENCE_TOOLS
                        : QuickBuildUiCatalogPage.SHAPES),
                convenienceTool, convenienceSettings, nextLimit,
                smartFillMaxBlocks, smartFillDiameter);
    }

    private QuickBuildUiState newState(boolean nextOpen, QuickBuildUiMode nextMode,
            QuickBuildUiShape nextBuild, QuickBuildUiShape nextDestroy,
            List<QuickBuildUiShapeOption> nextShapes, List<QuickBuildUiControl> nextControls,
            QuickBuildUiCatalogPage nextPage, QuickBuildUiConvenienceTool nextTool,
            QuickBuildUiConvenienceSettings nextSettings, int nextLimit,
            int nextSmartFillMaxBlocks, int nextSmartFillDiameter) {
        return new QuickBuildUiState(nextOpen, nextMode, destroyEnabled, destroyDisabledReason,
                nextBuild, nextDestroy, nextShapes, nextControls,
                nextPage, nextTool, nextSettings,
                nextLimit, chainMinimum, chainMaximum,
                progressCompleted, progressTotal, remainingBlocks, progressText, costText,
                selectedItemId, missingBlocks, hintKey, confirmKeyLabel, dimensions,
                nextSmartFillMaxBlocks, smartFillMinBlocks, smartFillMaxBlocksLimit,
                nextSmartFillDiameter, smartFillMinDiameter, smartFillMaxDiameter,
                smartFillTargetCount, smartFillAnchored, smartFillStatus);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static <T> List<T> immutable(List<T> value) {
        return Collections.unmodifiableList(new ArrayList<T>(
                value == null ? Collections.<T>emptyList() : value));
    }
}
