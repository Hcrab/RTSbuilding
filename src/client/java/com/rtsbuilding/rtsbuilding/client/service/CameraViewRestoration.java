package com.rtsbuilding.rtsbuilding.client.service;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/** 保存并恢复进入 RTS 前的原版视角选项；不拥有 RTS 实体或运动姿态。 */
final class CameraViewRestoration {
    private Entity entity;
    private CameraType type = CameraType.FIRST_PERSON;
    private boolean bob = true;
    private double fovScale = 1.0D;

    void capture(Minecraft minecraft) {
        entity = minecraft.getCameraEntity();
        type = minecraft.options.getCameraType();
        bob = minecraft.options.bobView().get();
        fovScale = minecraft.options.fovEffectScale().get();
    }

    void restore(Minecraft minecraft, Entity fallback) {
        minecraft.setCameraEntity(entity != null ? entity : fallback);
        minecraft.options.setCameraType(type);
        minecraft.options.bobView().set(bob);
        minecraft.options.fovEffectScale().set(fovScale);
    }

    void applyRts(Minecraft minecraft) {
        minecraft.options.setCameraType(CameraType.FIRST_PERSON);
        minecraft.options.bobView().set(false);
        minecraft.options.fovEffectScale().set(0.0D);
    }

    void clear() { entity = null; }
}
