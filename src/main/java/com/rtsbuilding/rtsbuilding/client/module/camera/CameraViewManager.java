package com.rtsbuilding.rtsbuilding.client.module.camera;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

/**
 * 摄像机视图状态管理器——捕获/恢复/切换 RTS 模式下的摄像机设置。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>RTS 开启前保存玩家的摄像机实体、视角类型、摇晃、FOV 效果等设置</li>
 *   <li>RTS 激活时应用固定视角（第一人称、无摇晃、无 FOV 效果）</li>
 *   <li>RTS 关闭时恢复玩家原本的视角设置</li>
 * </ul>
 */
final class CameraViewManager {

    // Previous view restoration state
    private Entity prevCameraEntity;
    private CameraType prevCameraType = CameraType.FIRST_PERSON;
    private boolean prevBobView = true;
    private double prevFovScale = 1.0D;

    /** 保存当前玩家视角设置。 */
    void capture(Minecraft mc) {
        prevCameraEntity = mc.getCameraEntity();
        prevCameraType = mc.options.getCameraType();
        prevBobView = mc.options.bobView().get();
        prevFovScale = mc.options.fovEffectScale().get();
    }

    /** 应用 RTS 固定视角（第一人称、无摇晃、无 FOV 效果）。 */
    void applyRtsView(Minecraft mc) {
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.bobView().set(false);
        mc.options.fovEffectScale().set(0.0D);
    }

    /** 恢复之前保存的玩家视角设置。 */
    void restore(Minecraft mc) {
        Entity restore = prevCameraEntity != null ? prevCameraEntity : mc.player;
        mc.setCameraEntity(restore);
        mc.options.setCameraType(prevCameraType);
        mc.options.bobView().set(prevBobView);
        mc.options.fovEffectScale().set(prevFovScale);
    }

    /** 清理保存的视角状态。 */
    void clear() {
        prevCameraEntity = null;
    }
}
