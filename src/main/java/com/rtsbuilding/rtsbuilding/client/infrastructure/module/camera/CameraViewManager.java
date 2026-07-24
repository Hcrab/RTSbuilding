package com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;


final class CameraViewManager {

    
    private Entity prevCameraEntity;
    private CameraType prevCameraType = CameraType.FIRST_PERSON;
    private boolean prevBobView = true;
    private double prevFovScale = 1.0D;

    
    void capture(Minecraft mc) {
        prevCameraEntity = mc.getCameraEntity();
        prevCameraType = mc.options.getCameraType();
        prevBobView = mc.options.bobView().get();
        prevFovScale = mc.options.fovEffectScale().get();
    }

    
    void applyRtsView(Minecraft mc) {
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.bobView().set(false);
        mc.options.fovEffectScale().set(0.0D);
    }

    
    void restore(Minecraft mc) {
        Entity restore = prevCameraEntity != null ? prevCameraEntity : mc.player;
        mc.setCameraEntity(restore);
        mc.options.setCameraType(prevCameraType);
        mc.options.bobView().set(prevBobView);
        mc.options.fovEffectScale().set(prevFovScale);
    }

    
    void clear() {
        prevCameraEntity = null;
    }
}
