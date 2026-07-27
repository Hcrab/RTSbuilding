package com.rtsbuilding.rtsbuilding.client.service;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;

/** 保存并恢复进入 RTS 前的原版视角选项；不拥有 RTS 实体或运动姿态。 */
final class CameraViewRestoration {
    private Entity entity;
    private int thirdPersonView;
    private boolean bob = true;

    void capture(Minecraft minecraft) {
        entity = minecraft.getRenderViewEntity();
        thirdPersonView = minecraft.gameSettings.thirdPersonView;
        bob = minecraft.gameSettings.viewBobbing;
    }

    void restore(Minecraft minecraft, Entity fallback) {
        minecraft.setRenderViewEntity(entity != null ? entity : fallback);
        minecraft.gameSettings.thirdPersonView = thirdPersonView;
        minecraft.gameSettings.viewBobbing = bob;
    }

    void applyRts(Minecraft minecraft) {
        minecraft.gameSettings.thirdPersonView = 0;
        minecraft.gameSettings.viewBobbing = false;
    }

    void clear() { entity = null; }
}
