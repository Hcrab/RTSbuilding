package com.rtsbuilding.rtsbuilding.uicore.quickbuild;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 快速建造/破坏窗全部玩家可见状态的纯 Java 快照。 */
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
    public final int chainLimit, chainMinimum, chainMaximum;
    public final int progressCompleted, progressTotal, remainingBlocks;
    public final String progressText;
    public final String costText;
    public final String selectedItemId;
    public final long missingBlocks;
    public final String hintKey;
    public final String confirmKeyLabel;
    public final String dimensions;
    public final int smartFillMaxBlocks, smartFillMinBlocks, smartFillMaxBlocksLimit;
    public final int smartFillDiameter, smartFillMinDiameter, smartFillMaxDiameter;
    public final int smartFillTargetCount;
    public final boolean smartFillAnchored;
    public final String smartFillStatus;

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
        this.open=open;
        this.destroyEnabled=destroyEnabled;
        this.mode=mode == QuickBuildUiMode.DESTROY && !destroyEnabled
                ? QuickBuildUiMode.BUILD : (mode == null ? QuickBuildUiMode.BUILD : mode);
        this.destroyDisabledReason=safe(destroyDisabledReason);
        this.buildShape=buildShape == null || buildShape == QuickBuildUiShape.CHAIN
                ? QuickBuildUiShape.BLOCK : buildShape;
        this.destroyShape=destroyShape == null ? QuickBuildUiShape.CHAIN : destroyShape;
        this.shapes=immutable(shapes);
        this.controls=immutable(controls);
        this.catalogPage=this.mode == QuickBuildUiMode.SMART_FILL
                ? QuickBuildUiCatalogPage.CONVENIENCE_TOOLS
                : (this.mode == QuickBuildUiMode.DESTROY && catalogPage != null
                ? catalogPage : QuickBuildUiCatalogPage.SHAPES);
        this.convenienceTool=convenienceTool == null
                ? QuickBuildUiConvenienceTool.REPEAT_BOX : convenienceTool;
        this.convenienceSettings=convenienceSettings == null
                ? QuickBuildUiConvenienceSettings.DEFAULT : convenienceSettings;
        this.chainMinimum=Math.max(1, chainMinimum);
        this.chainMaximum=Math.max(this.chainMinimum, chainMaximum);
        this.chainLimit=clamp(chainLimit, this.chainMinimum, this.chainMaximum);
        this.progressCompleted=Math.max(-1, progressCompleted);
        this.progressTotal=Math.max(0, progressTotal);
        this.remainingBlocks=Math.max(0, remainingBlocks);
        this.progressText=safe(progressText); this.costText=safe(costText);
        this.selectedItemId=safe(selectedItemId); this.missingBlocks=Math.max(0L, missingBlocks);
        this.hintKey=safe(hintKey); this.confirmKeyLabel=safe(confirmKeyLabel);
        this.dimensions=safe(dimensions);
        this.smartFillMinBlocks=Math.max(1, smartFillMinBlocks);
        this.smartFillMaxBlocksLimit=Math.max(this.smartFillMinBlocks, smartFillMaxBlocksLimit);
        this.smartFillMaxBlocks=clamp(smartFillMaxBlocks,
                this.smartFillMinBlocks, this.smartFillMaxBlocksLimit);
        this.smartFillMinDiameter=Math.max(1, smartFillMinDiameter);
        this.smartFillMaxDiameter=Math.max(this.smartFillMinDiameter, smartFillMaxDiameter);
        this.smartFillDiameter=clamp(smartFillDiameter,
                this.smartFillMinDiameter, this.smartFillMaxDiameter);
        this.smartFillTargetCount=Math.max(0, smartFillTargetCount);
        this.smartFillAnchored=smartFillAnchored;
        this.smartFillStatus=safe(smartFillStatus);
    }

    public QuickBuildUiShape activeShape() {
        return mode == QuickBuildUiMode.DESTROY ? destroyShape : buildShape;
    }
    public boolean chainMode() { return mode == QuickBuildUiMode.DESTROY && destroyShape == QuickBuildUiShape.CHAIN; }
    public boolean convenienceMode() {
        return mode == QuickBuildUiMode.DESTROY
                && catalogPage == QuickBuildUiCatalogPage.CONVENIENCE_TOOLS;
    }
    public QuickBuildUiControl control(QuickBuildUiControl.Id id) {
        for (QuickBuildUiControl control : controls) if (control.id == id) return control;
        return null;
    }
    public QuickBuildUiState withMode(QuickBuildUiMode value) {
        return copy(open, value, buildShape, destroyShape, shapes, controls, chainLimit);
    }
    public QuickBuildUiState withShape(QuickBuildUiShape value) {
        QuickBuildUiShape nextBuild = mode == QuickBuildUiMode.BUILD ? value : buildShape;
        QuickBuildUiShape nextDestroy = mode == QuickBuildUiMode.DESTROY ? value : destroyShape;
        List<QuickBuildUiShapeOption> next = new ArrayList<QuickBuildUiShapeOption>();
        for (QuickBuildUiShapeOption option : shapes) next.add(new QuickBuildUiShapeOption(
                option.shape, option.shape == value, option.enabled, option.disabledReason));
        return copy(open, mode, nextBuild, nextDestroy, next, controls, chainLimit);
    }
    public QuickBuildUiState withControl(QuickBuildUiControl.Id id) {
        List<QuickBuildUiControl> next = new ArrayList<QuickBuildUiControl>();
        boolean exclusiveFill = id == QuickBuildUiControl.Id.FILL
                || id == QuickBuildUiControl.Id.HOLLOW || id == QuickBuildUiControl.Id.SKELETON;
        for (QuickBuildUiControl control : controls) {
            boolean selected = exclusiveFill
                    ? ((control.id == QuickBuildUiControl.Id.FILL || control.id == QuickBuildUiControl.Id.HOLLOW
                    || control.id == QuickBuildUiControl.Id.SKELETON) ? control.id == id : control.selected)
                    : (control.id == id ? !control.selected : control.selected);
            next.add(control.withSelected(selected));
        }
        return copy(open, mode, buildShape, destroyShape, shapes, next, chainLimit);
    }
    public QuickBuildUiState withChainLimit(int value) {
        return copy(open, mode, buildShape, destroyShape, shapes, controls, value);
    }
    public QuickBuildUiState withCatalogPage(QuickBuildUiCatalogPage value) {
        return new QuickBuildUiState(open, mode, destroyEnabled, destroyDisabledReason,
                buildShape, destroyShape, shapes, controls,
                value, convenienceTool, convenienceSettings,
                chainLimit, chainMinimum, chainMaximum,
                progressCompleted, progressTotal, remainingBlocks, progressText, costText,
                selectedItemId, missingBlocks, hintKey, confirmKeyLabel, dimensions);
    }
    public QuickBuildUiState withConvenienceTool(QuickBuildUiConvenienceTool value) {
        return new QuickBuildUiState(open, mode, destroyEnabled, destroyDisabledReason,
                buildShape, destroyShape, shapes, controls,
                QuickBuildUiCatalogPage.CONVENIENCE_TOOLS, value, convenienceSettings,
                chainLimit, chainMinimum, chainMaximum,
                progressCompleted, progressTotal, remainingBlocks, progressText, costText,
                selectedItemId, missingBlocks, hintKey, confirmKeyLabel, dimensions);
    }
    public QuickBuildUiState withConvenienceParameter(
            QuickBuildUiConvenienceParameter parameter, int value) {
        return new QuickBuildUiState(open, mode, destroyEnabled, destroyDisabledReason,
                buildShape, destroyShape, shapes, controls,
                catalogPage, convenienceTool, convenienceSettings.with(parameter, value),
                chainLimit, chainMinimum, chainMaximum,
                progressCompleted, progressTotal, remainingBlocks, progressText, costText,
                selectedItemId, missingBlocks, hintKey, confirmKeyLabel, dimensions);
    }
    public QuickBuildUiState withSmartFillMaxBlocks(int value) {
        return smartFillCopy(value, smartFillDiameter);
    }
    public QuickBuildUiState withSmartFillDiameter(int value) {
        return smartFillCopy(smartFillMaxBlocks, value);
    }
    public QuickBuildUiState closed() {
        return copy(false, mode, buildShape, destroyShape, shapes, controls, chainLimit);
    }

    private QuickBuildUiState copy(boolean nextOpen, QuickBuildUiMode nextMode,
            QuickBuildUiShape nextBuild, QuickBuildUiShape nextDestroy,
            List<QuickBuildUiShapeOption> nextShapes, List<QuickBuildUiControl> nextControls,
            int nextLimit) {
        return new QuickBuildUiState(nextOpen,nextMode,destroyEnabled,destroyDisabledReason,
                nextBuild,nextDestroy,nextShapes,nextControls,
                nextMode == QuickBuildUiMode.DESTROY ? catalogPage
                        : (nextMode == QuickBuildUiMode.SMART_FILL
                        ? QuickBuildUiCatalogPage.CONVENIENCE_TOOLS
                        : QuickBuildUiCatalogPage.SHAPES),
                convenienceTool, convenienceSettings,
                nextLimit,chainMinimum,chainMaximum,
                progressCompleted,progressTotal,remainingBlocks,progressText,costText,selectedItemId,
                missingBlocks,hintKey,confirmKeyLabel,dimensions,
                smartFillMaxBlocks,smartFillMinBlocks,smartFillMaxBlocksLimit,
                smartFillDiameter,smartFillMinDiameter,smartFillMaxDiameter,
                smartFillTargetCount,smartFillAnchored,smartFillStatus);
    }
    private QuickBuildUiState smartFillCopy(int nextMaxBlocks, int nextDiameter) {
        return new QuickBuildUiState(open,mode,destroyEnabled,destroyDisabledReason,
                buildShape,destroyShape,shapes,controls,
                catalogPage,convenienceTool,convenienceSettings,
                chainLimit,chainMinimum,chainMaximum,
                progressCompleted,progressTotal,remainingBlocks,progressText,costText,
                selectedItemId,missingBlocks,hintKey,confirmKeyLabel,dimensions,
                nextMaxBlocks,smartFillMinBlocks,smartFillMaxBlocksLimit,
                nextDiameter,smartFillMinDiameter,smartFillMaxDiameter,
                smartFillTargetCount,smartFillAnchored,smartFillStatus);
    }
    private static String safe(String v){return v == null ? "" : v;}
    private static int clamp(int v,int min,int max){return Math.max(min,Math.min(max,v));}
    private static <T> List<T> immutable(List<T> v){return Collections.unmodifiableList(
            new ArrayList<T>(v == null ? Collections.<T>emptyList() : v));}
}
