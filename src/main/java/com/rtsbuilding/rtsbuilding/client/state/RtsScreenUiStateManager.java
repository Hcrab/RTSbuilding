package com.rtsbuilding.rtsbuilding.client.state;


import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.handler.ScreenShapeController;
import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.QuickBuildMode;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.QuickBuildPanel;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.ultimine.AreaMineShape;
import com.rtsbuilding.rtsbuilding.common.shape.ShapeFillMode;
import net.minecraft.util.Mth;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreenConstants.*;

/**
 * Manages persistent UI preferences for {@link BuilderScreen} — the business management layer.
 *
 * <p>This class bridges the persistent {@link RtsClientUiStateStore.UiState} with runtime UI
 * components (panels, shape controller, camera controller, etc.) via bi-directional sync.
 *
 * <h3>Architecture</h3>
 * <ul>
 *   <li><b>Manager</b> — bridges the {@link RtsClientUiStateStore Store} with UI components</li>
 *   <li><b>Batch load</b> — {@link #applyStoredUiState()} reads from the Store and dispatches to components</li>
 *   <li><b>Batch persist</b> — {@link #persistUiState()} collects state from components and writes to the Store</li>
 *   <li><b>Window panel bounds</b> — registers/unregisters draggable panels and persists their boundaries</li>
 *   <li><b>Scale management</b> — GUI scale adjustment and formatted label generation</li>
 * </ul>
 *
 * <p>This class does not perform I/O directly; all persistence is delegated to {@link RtsClientUiStateStore}.
 *
 * @see RtsClientUiStateStore
 * @see BuilderScreen
 */
public final class RtsScreenUiStateManager {
    /** Client RTS controller, used to read/write camera, input, and visual preferences. */
    private final ClientRtsController controller;
    /** Shape controller, used to read/write shape mode, fill mode, and rotation. */
    private final ScreenShapeController shapeController;
    /** Quick-build panel, persisted for its open state. */
    private final QuickBuildPanel quickBuildPanel;
    /** Ultimine panel, persisted for its open state and limit value. */

    /** Registered persistable window panels (key → panel), managed indirectly in applyStoredUiState / persistUiState. */
    private final Map<String, RtsWindowPanel> persistablePanels = new LinkedHashMap<>();

    /** Debug button visibility (runtime state, managed only by this class). */
    private boolean debugButtonVisible = false;
    /** Cached fixed RTS GUI scale (synced to the Store on persist). */
    private double fixedRtsGuiScale = DEFAULT_RTS_GUI_SCALE;

    /**
     * Constructs a UI state manager.
     *
     * @param controller     the client RTS controller
     * @param shapeController the shape controller
     * @param quickBuildPanel the quick-build panel (auto-registered as "quick_build")
     */
    public RtsScreenUiStateManager(
            ClientRtsController controller,
            ScreenShapeController shapeController,
            QuickBuildPanel quickBuildPanel) {
        this.controller = controller;
        this.shapeController = shapeController;
        this.quickBuildPanel = quickBuildPanel;

        // Register persistable-position window panels
        registerWindowPanel("quick_build", quickBuildPanel);
    }

    /**
     * Registers a window panel so its position/size can be persisted.
     * The key must be stable and unique; it is used as the JSON storage key.
     * Re-registering overwrites the old value.
     */
    public void registerWindowPanel(String key, RtsWindowPanel panel) {
        this.persistablePanels.put(key, panel);
    }

    /** Unregisters a window panel, stopping position persistence for it. */
    public void unregisterWindowPanel(String key) {
        this.persistablePanels.remove(key);
    }

    // ======================== Debug button ========================

    /** Whether the debug button is visible. */
    public boolean isDebugButtonVisible() {
        return this.debugButtonVisible;
    }

    /** Toggles debug button visibility. */
    public void toggleDebugButton() {
        this.debugButtonVisible = !this.debugButtonVisible;
    }

    // ======================== GUI Scale ========================

    /** Returns the current fixed RTS GUI scale. */
    public double fixedRtsGuiScale() {
        return this.fixedRtsGuiScale;
    }

    /**
     * Adjusts the GUI scale by the given delta and persists immediately.
     *
     * @param delta the scale adjustment (e.g. +0.5 or -0.5)
     */
    public void adjustRtsGuiScale(double delta) {
        this.fixedRtsGuiScale = sanitizeRtsGuiScale(this.fixedRtsGuiScale + delta);
        persistUiState();
    }

    /**
     * Returns a formatted scale label.
     * <p>Displays as "2x" for integer values and "2.5x" for half-values.
     */
    public String rtsGuiScaleLabel() {
        double scale = sanitizeRtsGuiScale(this.fixedRtsGuiScale);
        if (Math.abs(scale - Math.rint(scale)) < 0.001D) {
            return String.format(Locale.ROOT, "%.0fx", scale);
        }
        return String.format(Locale.ROOT, "%.1fx", scale);
    }

    // ======================== Load / Persist ========================

    /**
     * Reads all UI state from persistent storage and applies it to the corresponding controllers/panels.
     */
    public void applyStoredUiState() {
        RtsClientUiStateStore.UiState state = RtsClientUiStateStore.load();
        applyPanelState(state);
        applyCameraState(state);
        applyInputState(state);
        applyShapeState(state);
        applyMiscState(state);
        applyWindowPanelBounds(state);
    }

    /**
     * Collects all UI preferences from current runtime state and writes them back to persistent storage.
     * <p>Should be called whenever state changes (e.g. panel toggle, shape switch, scale adjustment).
     */
    public void persistUiState() {
        RtsClientUiStateStore.UiState state = RtsClientUiStateStore.load();
        state.buildShape = this.quickBuildPanel.getBuildModeShape().name();
        state.fillMode = this.shapeController.getShapeFillMode().name();
        state.lineConnected = this.shapeController.isLineConnected();
        state.rotationDegrees = this.shapeController.getShapeRotateDegrees();
        state.quickBuildOpen = this.quickBuildPanel.isQuickBuildOpen();
        state.quickBuildMode = this.quickBuildPanel.getMode().name();
        state.ultimineOpen = false;
        state.ultimineLimit = this.quickBuildPanel.getChainDestroyLimit();
        state.ultimineMode = "CHAIN";
        state.areaMineShape = this.quickBuildPanel.getRangeDestroyShape().name();
        state.chunkCurtainVisible = this.controller.isChunkCurtainVisible();
        state.rtsGuiScale = sanitizeRtsGuiScale(this.fixedRtsGuiScale);
        state.inputSensitivityIndex = this.controller.getInputSensitivityIndex();
        state.startCameraAtPlayerHead = this.controller.isStartCameraAtPlayerHead();
        state.allowPlacedBlockRecovery = this.controller.isAllowPlacedBlockRecovery();
        state.toolProtectionEnabled = this.controller.isToolProtectionEnabled();
        state.invertPanDragX = this.controller.isInvertPanDragX();
        state.invertPanDragY = this.controller.isInvertPanDragY();
        state.smoothCamera = this.controller.isSmoothCamera();
        state.damageSoundEnabled = this.controller.isDamageSoundEnabled();
        state.damageAutoReturnEnabled = this.controller.isDamageAutoReturnEnabled();
        state.debugButtonVisible = this.debugButtonVisible;
        persistWindowPanelBounds(state);
        RtsClientUiStateStore.save(state);
    }

    // ====== apply* breakdown ======

    /** Restores panel open state and limit values. */
    private void applyPanelState(RtsClientUiStateStore.UiState state) {
        this.quickBuildPanel.setQuickBuildOpen(state.quickBuildOpen);
        try {
            this.quickBuildPanel.setMode(QuickBuildMode.valueOf(state.quickBuildMode));
        } catch (IllegalArgumentException ignored) {
            this.quickBuildPanel.setMode(QuickBuildMode.BUILD);
        }
        this.quickBuildPanel.loadChainDestroyLimit(state.ultimineLimit);
        try {
            this.controller.setAreaMineShape(AreaMineShape.valueOf(state.areaMineShape));
        } catch (IllegalArgumentException ignored) {
            this.controller.setAreaMineShape(AreaMineShape.CHAIN);
        }
    }

    /** Restores camera, chunk curtain, and other visual preferences. */
    private void applyCameraState(RtsClientUiStateStore.UiState state) {
        this.controller.setStartCameraAtPlayerHead(state.startCameraAtPlayerHead);
        this.controller.setAllowPlacedBlockRecovery(state.allowPlacedBlockRecovery);
        this.controller.setToolProtectionEnabled(state.toolProtectionEnabled);
        this.controller.setSmoothCamera(state.smoothCamera);
        this.controller.setDamageSoundEnabled(state.damageSoundEnabled);
        this.controller.setDamageAutoReturnEnabled(state.damageAutoReturnEnabled);
        this.controller.setChunkCurtainVisible(state.chunkCurtainVisible);
    }

    /** Restores input sensitivity and drag inversion. */
    private void applyInputState(RtsClientUiStateStore.UiState state) {
        this.controller.setInvertPanDragX(state.invertPanDragX);
        this.controller.setInvertPanDragY(state.invertPanDragY);
        applyInputSensitivity(state.inputSensitivityIndex);
    }

    /**
     * Converts the stored sensitivity index to a [0, 1] fraction and applies it to the controller.
     * <p>The number of presets in the controller determines the granularity of the index-to-fraction mapping.
     *
     * @param index the stored sensitivity index
     */
    private void applyInputSensitivity(int index) {
        int presetCount = Math.max(1, this.controller.getInputSensitivityPresetCount());
        if (presetCount <= 1) {
            this.controller.setInputSensitivityByFraction(0.0D);
            return;
        }
        int clamped = Mth.clamp(index, 0, presetCount - 1);
        double fraction = (double) clamped / (double) (presetCount - 1);
        this.controller.setInputSensitivityByFraction(fraction);
    }

    /** Restores shape mode, fill mode, and rotation angle. */
    private void applyShapeState(RtsClientUiStateStore.UiState state) {
        parseAndSetBuildShape(state.buildShape);
        this.quickBuildPanel.loadStoredShapes(this.controller.getBuildShape(), this.controller.getAreaMineShape());
        parseAndSetFillMode(state.fillMode);
        this.shapeController.setLineConnected(state.lineConnected);
        this.shapeController.rotateToDegrees(Math.floorMod(state.rotationDegrees, 360));
        this.shapeController.ensureFillModeForShape(this.controller.getBuildShape());
    }

    /**
     * Attempts to parse and set the build shape string.
     *
     * @param name the enum name of the build shape
     */
    private void parseAndSetBuildShape(String name) {
        try {
            this.controller.setBuildShape(BuildShape.valueOf(name));
        } catch (IllegalArgumentException ignored) {
            this.controller.setBuildShape(BuildShape.BLOCK);
        }
    }

    /**
     * Attempts to parse and set the fill mode string.
     *
     * @param name the enum name of the fill mode
     */
    private void parseAndSetFillMode(String name) {
        try {
            this.shapeController.setShapeFillMode(ShapeFillMode.valueOf(name));
        } catch (IllegalArgumentException ignored) {
            this.shapeController.setShapeFillMode(ShapeFillMode.FILL);
        }
    }

    /** Restores debug button visibility and GUI scale. */
    private void applyMiscState(RtsClientUiStateStore.UiState state) {
        this.debugButtonVisible = state.debugButtonVisible;
        this.fixedRtsGuiScale = sanitizeRtsGuiScale(state.rtsGuiScale);
    }

    // ====== Window panel bounds persistence ======

    /** Writes registered window panel bounds into the state. */
    private void persistWindowPanelBounds(RtsClientUiStateStore.UiState state) {
        state.windowPanelBounds.clear();
        for (Map.Entry<String, RtsWindowPanel> entry : this.persistablePanels.entrySet()) {
            RtsWindowPanel panel = entry.getValue();
            if (panel.hasUserBoundsPreference()) {
                state.windowPanelBounds.put(entry.getKey(),
                        new RtsClientUiStateStore.UiState.PanelBounds(
                                panel.getWindowX(), panel.getWindowY(),
                                panel.getWindowWidth(), panel.getWindowHeight()));
            }
        }
    }

    /** Restores registered window panel bounds from the state (via setBounds to avoid intermediate clamp side effects). */
    private void applyWindowPanelBounds(RtsClientUiStateStore.UiState state) {
        for (Map.Entry<String, RtsWindowPanel> entry : this.persistablePanels.entrySet()) {
            RtsClientUiStateStore.UiState.PanelBounds bounds = state.windowPanelBounds.get(entry.getKey());
            if (bounds != null) {
                RtsWindowPanel panel = entry.getValue();
                panel.setBounds(bounds.x, bounds.y, bounds.width, bounds.height);
            }
        }
    }

    // ====== Scale utility ======

    /** Snaps the scale to the valid range and rounds to the configured step. */
    private static double sanitizeRtsGuiScale(double scale) {
        if (!Double.isFinite(scale)) {
            return DEFAULT_RTS_GUI_SCALE;
        }
        double snapped = Math.round(scale / RTS_GUI_SCALE_STEP) * RTS_GUI_SCALE_STEP;
        return Math.max(MIN_RTS_GUI_SCALE, Math.min(MAX_RTS_GUI_SCALE, snapped));
    }
}
