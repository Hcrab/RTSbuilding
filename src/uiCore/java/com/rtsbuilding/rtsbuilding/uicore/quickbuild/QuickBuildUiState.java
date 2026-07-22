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
    public final int chainLimit, chainMinimum, chainMaximum;
    public final int progressCompleted, progressTotal, remainingBlocks;
    public final String progressText;
    public final String costText;
    public final String selectedItemId;
    public final long missingBlocks;
    public final String hintKey;
    public final String confirmKeyLabel;
    public final String dimensions;

    public QuickBuildUiState(boolean open, QuickBuildUiMode mode,
            boolean destroyEnabled, String destroyDisabledReason,
            QuickBuildUiShape buildShape, QuickBuildUiShape destroyShape,
            List<QuickBuildUiShapeOption> shapes, List<QuickBuildUiControl> controls,
            int chainLimit, int chainMinimum, int chainMaximum,
            int progressCompleted, int progressTotal, int remainingBlocks,
            String progressText, String costText, String selectedItemId,
            long missingBlocks, String hintKey, String confirmKeyLabel,
            String dimensions) {
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
    }

    public QuickBuildUiShape activeShape() {
        return mode == QuickBuildUiMode.DESTROY ? destroyShape : buildShape;
    }
    public boolean chainMode() { return mode == QuickBuildUiMode.DESTROY && destroyShape == QuickBuildUiShape.CHAIN; }
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
    public QuickBuildUiState closed() {
        return copy(false, mode, buildShape, destroyShape, shapes, controls, chainLimit);
    }

    private QuickBuildUiState copy(boolean nextOpen, QuickBuildUiMode nextMode,
            QuickBuildUiShape nextBuild, QuickBuildUiShape nextDestroy,
            List<QuickBuildUiShapeOption> nextShapes, List<QuickBuildUiControl> nextControls,
            int nextLimit) {
        return new QuickBuildUiState(nextOpen,nextMode,destroyEnabled,destroyDisabledReason,
                nextBuild,nextDestroy,nextShapes,nextControls,nextLimit,chainMinimum,chainMaximum,
                progressCompleted,progressTotal,remainingBlocks,progressText,costText,selectedItemId,
                missingBlocks,hintKey,confirmKeyLabel,dimensions);
    }
    private static String safe(String v){return v == null ? "" : v;}
    private static int clamp(int v,int min,int max){return Math.max(min,Math.min(max,v));}
    private static <T> List<T> immutable(List<T> v){return Collections.unmodifiableList(
            new ArrayList<T>(v == null ? Collections.<T>emptyList() : v));}
}
