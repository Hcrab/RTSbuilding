package com.rtsbuilding.rtsbuilding.client.service;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * Interface for camera orbit, pan, dolly, rotation sensitivity, smoothing,
 * and the local render-mirror camera entity on the client side.
 *
 * <p>Provides full camera lifecycle management for the RTS mode, including
 * view capture/restore, local prediction, input accumulation, and server sync.
 */
public interface ICameraOrbitService {

    // ======================== Lifecycle — enable / disable ========================

    /**
     * Captures the player's current camera entity and view settings before RTS mode
     * takes over, so they can be restored on disable.
     */
    void capturePreviousView(Minecraft minecraft);

    /**
     * Applies the server-sent camera pose (anchor, height offset, yaw, pitch)
     * and resets all input accumulation.
     */
    void applyEnabledPose(double anchorX, double anchorY, double anchorZ,
                          double heightOffset, float yawDeg, float pitchDeg);

    /** Clears all camera state (normal disable). */
    void clearState();

    /** Clears camera state on death (partial cleanup). */
    void clearStateOnDeath();

    /** Restores the player's previous camera entity and view settings. */
    void restorePreviousView(Minecraft minecraft, Entity fallbackEntity);

    /** Sets the RTS camera view (FPP, no bobbing, no FOV effect). */
    void applyRtsView(Minecraft minecraft);

    /** Clears the stored restore cursor position. */
    void clearRestoreCursor();

    /** Resets all input accumulation (pan, scroll, rotate, EMA, timestamps). */
    void resetInputAccumulation();

    // ======================== Server entity ID ========================

    void setServerCameraEntityId(int id);

    int getServerCameraEntityId();

    void resetServerCameraEntityId();

    // ======================== Local state ========================

    boolean isLocalStateReady();

    void setLocalStateReady(boolean ready);

    // ======================== Rotate capture ========================

    void beginRotateCapture(double cursorX, double cursorY);

    void endRotateCapture(double fallbackX, double fallbackY);

    boolean isRotateCaptured();

    // ======================== Sensitivity ========================

    float getRotateSensitivity();

    void increaseRotateSensitivity();

    void decreaseRotateSensitivity();

    String getInputSensitivityLabel();

    int getInputSensitivityIndex();

    int getInputSensitivityPresetCount();

    void setInputSensitivityByFraction(double fraction);

    void cycleInputSensitivity();

    // ======================== Smooth camera ========================

    boolean isSmoothCamera();

    void setSmoothCamera(boolean smoothCamera);

    void toggleSmoothCamera();

    // ======================== Invert pan drag ========================

    boolean isInvertPanDragX();

    void setInvertPanDragX(boolean invert);

    void toggleInvertPanDragX();

    boolean isInvertPanDragY();

    void setInvertPanDragY(boolean invert);

    void toggleInvertPanDragY();

    // ======================== Local pose getters ========================

    double getLocalX();

    double getLocalY();

    double getLocalZ();

    double getLocalHeightOffset();

    float getLocalYawDeg();

    float getLocalPitchDeg();

    net.minecraft.world.entity.Entity getLocalMirrorCamera();

    // ======================== Input queueing ========================

    void queuePanDrag(double dragX, double dragY);

    void queueRotateDrag(double dragX, double dragY);

    void queueScroll(double scrollY);

    void queueRotateQuarter(int direction);

    // ======================== Tick ========================

    /**
     * Processes accumulated camera input for this tick and sends camera-move packets.
     */
    void tick(Minecraft minecraft, double anchorX, double anchorY, double anchorZ, double maxRadius);

    // ======================== Mirror camera & visual frame ========================

    /**
     * Ensures the local mirror camera exists and syncs the visual camera frame.
     */
    void syncVisualCameraFrame(Minecraft minecraft, double anchorX, double anchorY, double anchorZ,
                               double maxRadius, boolean rtsEnabled);

    // ======================== Bounds ========================

    /**
     * Updates the bounding anchor used for camera position clamping.
     */
    void setBounds(double anchorX, double anchorY, double anchorZ, double maxRadius);
}
