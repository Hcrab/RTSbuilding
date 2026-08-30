package com.rtsbuilding.rtsbuilding.client.controller;

import com.rtsbuilding.rtsbuilding.client.service.CameraOrbitService;

/**
 * 客户端 RTS 相机与辅助偏好的完整 owner。
 *
 * <p>它拥有偏好状态和相机设置 API，但不拥有 RTS 生命周期、服务端状态、储存、
 * 工作流或网络命令。控制器继承这组稳定调用面，避免为了兼容旧调用而保留一百多行
 * 无业务价值的转发方法。</p>
 */
abstract class ClientRtsPreferenceFacade {
    protected final CameraOrbitService cameraOrbitService = new CameraOrbitService();

    private boolean startCameraAtPlayerHead;
    private boolean allowPlacedBlockRecovery;
    private boolean toolProtectionEnabled = true;
    private boolean playerStatusOverlayEnabled = true;
    private boolean damageSoundEnabled = true;
    private boolean damageAutoReturnEnabled = true;

    public float getRotateSensitivity() {
        return this.cameraOrbitService.getRotateSensitivity();
    }

    public String getInputSensitivityLabel() {
        return this.cameraOrbitService.getInputSensitivityLabel();
    }

    public int getInputSensitivityIndex() {
        return this.cameraOrbitService.getInputSensitivityIndex();
    }

    public int getInputSensitivityPresetCount() {
        return this.cameraOrbitService.getInputSensitivityPresetCount();
    }

    public void setInputSensitivityByFraction(double fraction) {
        this.cameraOrbitService.setInputSensitivityByFraction(fraction);
    }

    public void cycleInputSensitivity() {
        this.cameraOrbitService.cycleInputSensitivity();
    }

    public String getPanDragSensitivityLabel() {
        return this.cameraOrbitService.getPanDragSensitivityLabel();
    }

    public int getPanDragSensitivityIndex() {
        return this.cameraOrbitService.getPanDragSensitivityIndex();
    }

    public void setPanDragSensitivityByFraction(double fraction) {
        this.cameraOrbitService.setPanDragSensitivityByFraction(fraction);
    }

    public String getRotateViewSensitivityLabel() {
        return this.cameraOrbitService.getRotateViewSensitivityLabel();
    }

    public int getRotateViewSensitivityIndex() {
        return this.cameraOrbitService.getRotateViewSensitivityIndex();
    }

    public void setRotateViewSensitivityByFraction(double fraction) {
        this.cameraOrbitService.setRotateViewSensitivityByFraction(fraction);
    }

    public String getKeyboardMoveSensitivityLabel() {
        return this.cameraOrbitService.getKeyboardMoveSensitivityLabel();
    }

    public int getKeyboardMoveSensitivityIndex() {
        return this.cameraOrbitService.getKeyboardMoveSensitivityIndex();
    }

    public void setKeyboardMoveSensitivityByFraction(double fraction) {
        this.cameraOrbitService.setKeyboardMoveSensitivityByFraction(fraction);
    }

    public String getWheelZoomSensitivityLabel() {
        return this.cameraOrbitService.getWheelZoomSensitivityLabel();
    }

    public int getWheelZoomSensitivityIndex() {
        return this.cameraOrbitService.getWheelZoomSensitivityIndex();
    }

    public void setWheelZoomSensitivityByFraction(double fraction) {
        this.cameraOrbitService.setWheelZoomSensitivityByFraction(fraction);
    }

    public void increaseRotateSensitivity() {
        this.cameraOrbitService.increaseRotateSensitivity();
    }

    public void decreaseRotateSensitivity() {
        this.cameraOrbitService.decreaseRotateSensitivity();
    }

    public void beginRotateCapture(double cursorX, double cursorY) {
        this.cameraOrbitService.beginRotateCapture(cursorX, cursorY);
    }

    public void endRotateCapture(double fallbackX, double fallbackY) {
        this.cameraOrbitService.endRotateCapture(fallbackX, fallbackY);
    }

    public boolean isRotateCaptured() {
        return this.cameraOrbitService.isRotateCaptured();
    }

    public boolean isStartCameraAtPlayerHead() { return this.startCameraAtPlayerHead; }
    public void setStartCameraAtPlayerHead(boolean value) { this.startCameraAtPlayerHead = value; }
    public void toggleStartCameraAtPlayerHead() { this.startCameraAtPlayerHead = !this.startCameraAtPlayerHead; }
    public boolean isAllowPlacedBlockRecovery() { return this.allowPlacedBlockRecovery; }
    public void setAllowPlacedBlockRecovery(boolean value) { this.allowPlacedBlockRecovery = value; }
    public void toggleAllowPlacedBlockRecovery() { this.allowPlacedBlockRecovery = !this.allowPlacedBlockRecovery; }
    public boolean isToolProtectionEnabled() { return this.toolProtectionEnabled; }
    public void setToolProtectionEnabled(boolean value) { this.toolProtectionEnabled = value; }
    public void toggleToolProtectionEnabled() { this.toolProtectionEnabled = !this.toolProtectionEnabled; }
    public boolean isPlayerStatusOverlayEnabled() { return this.playerStatusOverlayEnabled; }
    public void setPlayerStatusOverlayEnabled(boolean value) { this.playerStatusOverlayEnabled = value; }
    public void togglePlayerStatusOverlayEnabled() { this.playerStatusOverlayEnabled = !this.playerStatusOverlayEnabled; }

    public boolean isInvertPanDragX() { return this.cameraOrbitService.isInvertPanDragX(); }
    public void setInvertPanDragX(boolean value) { this.cameraOrbitService.setInvertPanDragX(value); }
    public void toggleInvertPanDragX() { this.cameraOrbitService.toggleInvertPanDragX(); }
    public boolean isInvertPanDragY() { return this.cameraOrbitService.isInvertPanDragY(); }
    public void setInvertPanDragY(boolean value) { this.cameraOrbitService.setInvertPanDragY(value); }
    public void toggleInvertPanDragY() { this.cameraOrbitService.toggleInvertPanDragY(); }
    public boolean isSmoothCamera() { return this.cameraOrbitService.isSmoothCamera(); }
    public void setSmoothCamera(boolean value) { this.cameraOrbitService.setSmoothCamera(value); }
    public void toggleSmoothCamera() { this.cameraOrbitService.toggleSmoothCamera(); }

    public boolean isDamageSoundEnabled() { return this.damageSoundEnabled; }
    public void setDamageSoundEnabled(boolean value) { this.damageSoundEnabled = value; }
    public void toggleDamageSoundEnabled() { this.damageSoundEnabled = !this.damageSoundEnabled; }
    public boolean isDamageAutoReturnEnabled() { return this.damageAutoReturnEnabled; }
    public void setDamageAutoReturnEnabled(boolean value) { this.damageAutoReturnEnabled = value; }
    public void toggleDamageAutoReturnEnabled() { this.damageAutoReturnEnabled = !this.damageAutoReturnEnabled; }
}
