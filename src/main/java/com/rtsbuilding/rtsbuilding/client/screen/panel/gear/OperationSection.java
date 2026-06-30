package com.rtsbuilding.rtsbuilding.client.screen.panel.gear;

import com.rtsbuilding.rtsbuilding.client.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.render.util.CursorRaycaster;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.util.SettingsSection;
import com.rtsbuilding.rtsbuilding.client.screen.panel.util.ScaleSliderComponent;
import com.rtsbuilding.rtsbuilding.client.screen.panel.util.ThemeSwitchComponent;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * 操作设置折叠分区——在设置面板中管理"操作设置"分区的渲染和交互。
 */
public class OperationSection extends SettingsSection {

    private static final double SENS_MIN = 0.1;
    private static final double SENS_MAX = 2.0;

    private final ScaleSliderComponent slider = new ScaleSliderComponent();
    private final ThemeSwitchComponent orbitToggle = new ThemeSwitchComponent();
    /** 灵敏度滑条轨道位置缓存 */
    private final SliderTrack sensTrack = new SliderTrack();

    /** 缓存的翻译文本 */
    private String cachedSensitivityLabel;
    private String cachedOrbitLabel;

    @Nullable
    private CameraModule cameraModule;

    public OperationSection() {
        super("screen.rtsbuilding.settings.category.controls");
    }

    public void setCameraModule(@Nullable CameraModule module) {
        this.cameraModule = module;
    }

    @Override
    protected int getContentRowCount() {
        return 2;
    }

    @Override
    protected void renderContent(GuiGraphics g, int mouseX, int mouseY, int x, int y, int w, int lineCount) {
        double sens = getSensitivity();

        // 灵敏度标签 + 滑条（第一行，合并到同一行）
        String labelText = buildSensitivityLabel(sens);
        renderSlider(g, mouseX, mouseY, x, y, w, 0, labelText, slider, sensTrack,
                SENS_MIN, SENS_MAX, sens);

        // 环绕模式开关（第二行）
        renderLabel(g, getOrbitLabel(), x, y, 1);
        renderToggle(g, mouseX, mouseY, x, y, w, 1, orbitToggle, cameraModule != null && cameraModule.isOrbitMode());
    }

    private String buildSensitivityLabel(double sens) {
        if (cachedSensitivityLabel == null) {
            cachedSensitivityLabel = Component.translatable("screen.rtsbuilding.settings.sensitivity").getString();
        }
        return cachedSensitivityLabel + String.format(java.util.Locale.ROOT, "：x%.2f", sens);
    }

    private String getOrbitLabel() {
        if (cachedOrbitLabel == null) cachedOrbitLabel = Component.translatable("screen.rtsbuilding.settings.orbit_mode").getString();
        return cachedOrbitLabel;
    }

    // ======================== 点击 ========================

    @Override
    protected boolean onContentLineClick(int lineIndex, double mouseX, double mouseY,
                                         int contentX, int contentY, int contentW) {
        Double newVal = slider.handleClick(mouseX, mouseY,
                sensTrack.trackX, sensTrack.trackY, sensTrack.trackW, SENS_MIN, SENS_MAX);
        if (newVal != null) {
            setSensitivity(newVal);
            return true;
        }
        if (cameraModule != null && orbitToggle.handleClick(mouseX, mouseY)) {
            if (cameraModule.isOrbitMode()) {
                cameraModule.disableOrbitMode();
            } else {
                BlockPos target = computeOrbitTargetFromCamera();
                if (target != null) cameraModule.enableOrbitMode(target);
                else cameraModule.toggleOrbitMode();
            }
            return true;
        }
        return false;
    }

    // ======================== 滑条拖拽 / 滚轮 ========================

    public boolean isSliderDragging() { return slider.isDragging(); }

    public void handleSliderDrag(double mouseX) {
        if (slider.isDragging() && sensTrack.trackW > 0) {
            double val = slider.handleDrag(mouseX, sensTrack.trackX, sensTrack.trackW, SENS_MIN, SENS_MAX);
            setSensitivity(val);
        }
    }

    public void endSliderDrag() { slider.endDrag(); }

    public boolean handleSliderScroll(double mouseX, double mouseY, double scrollY) {
        Double newVal = slider.handleScroll(mouseX, mouseY, scrollY,
                sensTrack.trackX, sensTrack.trackY, sensTrack.trackW, SENS_MIN, SENS_MAX);
        if (newVal != null) { setSensitivity(newVal); return true; }
        return false;
    }

    // ======================== 灵敏度 ========================

    private double getSensitivity() {
        return cameraModule != null ? cameraModule.getInputSensitivity() : 1.0;
    }

    private void setSensitivity(double val) {
        if (cameraModule != null) cameraModule.setInputSensitivity((float) val);
    }

    @Nullable
    private BlockPos computeOrbitTargetFromCamera() {
        Minecraft mc = Minecraft.getInstance();
        var ray = CursorRaycaster.computeCameraCenterRay(mc);
        if (ray == null) return null;
        var hit = ray.raycastBlock(mc);
        return hit != null ? hit.getBlockPos() : null;
    }
}
