package com.rtsbuilding.rtsbuilding.client.state;


import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.ScreenShapeController;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.QuickBuildPanel;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeBuildTypes;
import com.rtsbuilding.rtsbuilding.client.screen.ultimine.UltiminePanel;
import net.minecraft.util.Mth;

import java.util.Locale;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.DEFAULT_RTS_GUI_SCALE;
import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.MAX_RTS_GUI_SCALE;
import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.MIN_RTS_GUI_SCALE;
import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.RTS_GUI_SCALE_STEP;

/**
 * Owns persisted RTS screen UI preferences for {@link BuilderScreen}.
 *
 * <p>This manager applies and saves client-only UI state: quick-build and
 * ultimine window state, shape settings, camera preferences, debug visibility,
 * and the fixed RTS GUI scale. It explicitly does not own rendering order,
 * input routing, storage/container overlay behavior, or gameplay mutation.
 * Keeping those lines separate lets the PR #71 window/state direction land
 * without moving the newer mainline overlay and UI flows back to the old fork.
 */
public final class RtsScreenUiStateManager {
    private final ClientRtsController controller;
    private final ScreenShapeController shapeController;
    private final QuickBuildPanel quickBuildPanel;
    private final UltiminePanel ultiminePanel;

    private boolean debugButtonVisible = false;
    private double fixedRtsGuiScale = DEFAULT_RTS_GUI_SCALE;

    public RtsScreenUiStateManager(
            ClientRtsController controller,
            ScreenShapeController shapeController,
            QuickBuildPanel quickBuildPanel,
            UltiminePanel ultiminePanel) {
        this.controller = controller;
        this.shapeController = shapeController;
        this.quickBuildPanel = quickBuildPanel;
        this.ultiminePanel = ultiminePanel;
    }

    public boolean isDebugButtonVisible() {
        return this.debugButtonVisible;
    }

    public void toggleDebugButton() {
        this.debugButtonVisible = !this.debugButtonVisible;
    }

    public double fixedRtsGuiScale() {
        return this.fixedRtsGuiScale;
    }

    public void applyStoredUiState() {
        RtsClientUiStateStore.UiState state = RtsClientUiStateStore.load();
        this.quickBuildPanel.setQuickBuildOpen(state.quickBuildOpen);
        if (state.quickBuildX >= 0 && state.quickBuildY >= 0) {
            this.quickBuildPanel.setPosition(state.quickBuildX, state.quickBuildY);
        }
        this.ultiminePanel.applyOpenState(state.ultimineOpen);
        if (state.ultimineX >= 0 && state.ultimineY >= 0) {
            this.ultiminePanel.setPosition(state.ultimineX, state.ultimineY);
        }
        this.ultiminePanel.setLimit(state.ultimineLimit);
        this.fixedRtsGuiScale = sanitizeRtsGuiScale(state.rtsGuiScale);
        this.controller.setStartCameraAtPlayerHead(state.startCameraAtPlayerHead);
        this.controller.setAllowPlacedBlockRecovery(state.allowPlacedBlockRecovery);
        this.controller.setInvertPanDragX(state.invertPanDragX);
        this.controller.setInvertPanDragY(state.invertPanDragY);
        this.controller.setSmoothCamera(state.smoothCamera);
        this.controller.setDamageSoundEnabled(state.damageSoundEnabled);
        this.controller.setDamageAutoReturnEnabled(state.damageAutoReturnEnabled);
        this.debugButtonVisible = state.debugButtonVisible;
        int sensitivityPresetCount = Math.max(1, this.controller.getInputSensitivityPresetCount());
        double sensitivityFraction = sensitivityPresetCount <= 1
                ? 0.0D
                : Mth.clamp(state.inputSensitivityIndex, 0, sensitivityPresetCount - 1) / (double) (sensitivityPresetCount - 1);
        this.controller.setInputSensitivityByFraction(sensitivityFraction);
        this.controller.setChunkCurtainVisible(state.chunkCurtainVisible);
        try {
            this.controller.setBuildShape(ClientRtsController.BuildShape.valueOf(state.buildShape));
        } catch (IllegalArgumentException ignored) {
            this.controller.setBuildShape(ClientRtsController.BuildShape.BLOCK);
        }
        try {
            this.shapeController.setShapeFillMode(ShapeBuildTypes.ShapeFillMode.valueOf(state.fillMode));
        } catch (IllegalArgumentException ignored) {
            this.shapeController.setShapeFillMode(ShapeBuildTypes.ShapeFillMode.FILL);
        }
        this.shapeController.rotateToDegrees(Math.floorMod(state.rotationDegrees, 360));
        this.shapeController.ensureFillModeForShape(this.controller.getBuildShape());
    }

    public void persistUiState() {
        RtsClientUiStateStore.UiState state = RtsClientUiStateStore.load();
        state.buildShape = this.controller.getBuildShape().name();
        state.fillMode = this.shapeController.getShapeFillMode().name();
        state.rotationDegrees = this.shapeController.getShapeRotateDegrees();
        state.quickBuildOpen = this.quickBuildPanel.isQuickBuildOpen();
        if (this.quickBuildPanel.hasInitializedBounds()) {
            state.quickBuildX = this.quickBuildPanel.getWindowX();
            state.quickBuildY = this.quickBuildPanel.getWindowY();
        }
        state.ultimineOpen = this.ultiminePanel.isOpen();
        if (this.ultiminePanel.hasInitializedBounds()) {
            state.ultimineX = this.ultiminePanel.getWindowX();
            state.ultimineY = this.ultiminePanel.getWindowY();
        }
        state.ultimineLimit = this.ultiminePanel.getLimit();
        state.chunkCurtainVisible = this.controller.isChunkCurtainVisible();
        state.rtsGuiScale = sanitizeRtsGuiScale(this.fixedRtsGuiScale);
        state.inputSensitivityIndex = this.controller.getInputSensitivityIndex();
        state.startCameraAtPlayerHead = this.controller.isStartCameraAtPlayerHead();
        state.allowPlacedBlockRecovery = this.controller.isAllowPlacedBlockRecovery();
        state.invertPanDragX = this.controller.isInvertPanDragX();
        state.invertPanDragY = this.controller.isInvertPanDragY();
        state.smoothCamera = this.controller.isSmoothCamera();
        state.damageSoundEnabled = this.controller.isDamageSoundEnabled();
        state.damageAutoReturnEnabled = this.controller.isDamageAutoReturnEnabled();
        state.debugButtonVisible = this.debugButtonVisible;
        RtsClientUiStateStore.save(state);
    }

    public void adjustRtsGuiScale(double delta) {
        this.fixedRtsGuiScale = sanitizeRtsGuiScale(this.fixedRtsGuiScale + delta);
        persistUiState();
    }

    public String rtsGuiScaleLabel() {
        double scale = sanitizeRtsGuiScale(this.fixedRtsGuiScale);
        if (Math.abs(scale - Math.rint(scale)) < 0.001D) {
            return String.format(Locale.ROOT, "%.0fx", scale);
        }
        return String.format(Locale.ROOT, "%.1fx", scale);
    }

    private static double sanitizeRtsGuiScale(double scale) {
        if (!Double.isFinite(scale)) {
            return DEFAULT_RTS_GUI_SCALE;
        }
        double snapped = Math.round(scale / RTS_GUI_SCALE_STEP) * RTS_GUI_SCALE_STEP;
        return Math.max(MIN_RTS_GUI_SCALE, Math.min(MAX_RTS_GUI_SCALE, snapped));
    }
}
