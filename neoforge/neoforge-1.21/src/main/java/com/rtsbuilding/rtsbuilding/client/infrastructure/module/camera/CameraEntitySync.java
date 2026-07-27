package com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera;

import com.rtsbuilding.rtsbuilding.common.entity.RtsCameraEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EntityType;

final class CameraEntitySync {

    
    RtsCameraEntity localMirrorCamera;
    
    private EntityType<RtsCameraEntity> cachedCameraEntityType;

    
    void ensureMirrorCamera(Minecraft mc) {
        if (mc.level == null) return;
        if (localMirrorCamera != null && localMirrorCamera.level() == mc.level) return;
        if (cachedCameraEntityType == null) {
            cachedCameraEntityType = (EntityType<RtsCameraEntity>) com.rtsbuilding.rtsbuilding.common.RtsEntities.RTS_CAMERA_ENTITY.get();
        }
        localMirrorCamera = new RtsCameraEntity(cachedCameraEntityType, mc.level);
    }

    
    void setAsCameraEntity(Minecraft mc) {
        if (localMirrorCamera != null && mc.getCameraEntity() != localMirrorCamera) {
            mc.setCameraEntity(localMirrorCamera);
        }
    }

    
    void snapToState(CameraState state) {
        if (localMirrorCamera != null) {
            localMirrorCamera.snapTo(state.localX, state.localY, state.localZ,
                    state.localYaw, state.localPitch);
        }
    }

    
    void clear() {
        localMirrorCamera = null;
    }
}
