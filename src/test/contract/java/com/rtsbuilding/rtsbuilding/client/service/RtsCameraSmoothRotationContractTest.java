package com.rtsbuilding.rtsbuilding.client.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsCameraSmoothRotationContractTest {
    @Test
    void smoothRotationKeepsHighFrequencyInputInsteadOfStoppingAtTheLegacyTickCap() throws IOException {
        String clientSource = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/service/CameraOrbitService.java"));

        assertTrue(clientSource.contains("MAX_SMOOTH_ROTATE_ACCUMULATION = 160.0F"),
                "平滑旋转必须允许一个 tick 汇总多次高频鼠标事件。");
        assertTrue(clientSource.contains("rotateXForTick = this.pendingSmoothRotateX"),
                "网络应发送本地实际应用的平滑旋转汇总，不能再次截回旧的 20 上限。");
        assertTrue(clientSource.contains("rotateYForTick = this.pendingSmoothRotateY"),
                "俯仰旋转也必须沿用同一份本地汇总。");
    }

    @Test
    void mouseRotationUsesDirectTargetsWithoutReleaseInertia() throws IOException {
        String clientSource = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/service/CameraOrbitService.java"));

        assertTrue(clientSource.contains("applyImmediateRotation((float) dragX, (float) dragY);"),
                "每个鼠标拖拽事件都应立即更新目标朝向。");
        assertFalse(clientSource.contains("ROT_EMA_ALPHA"),
                "旋转不能再通过速度 EMA 产生松手后的惯性尾巴。");
        assertFalse(clientSource.contains("ROT_EMA_DECAY"),
                "旋转不能在没有新鼠标输入时继续衰减滑动。");
    }

    @Test
    void visualCameraAdvancesOnceBeforeEachRenderedFrame() throws IOException {
        String renderSyncSource = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/camera/RtsCameraRenderSync.java"));
        String controllerSource = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/controller/ClientRtsController.java"));

        assertTrue(renderSyncSource.contains("RenderFrameEvent.Pre"),
                "视觉镜头必须在 GameRenderer 开始本帧之前更新。");
        assertFalse(renderSyncSource.contains("RenderLevelStageEvent"),
                "世界渲染阶段事件已经太晚，不能用来驱动本帧镜头。");
        assertFalse(controllerSource.contains(
                        "this.cameraOrbitService.syncVisualCameraFrame(minecraft, this.anchorX, this.anchorY, this.anchorZ, this.maxRadius, this.enabled);"
                                + System.lineSeparator() + "    }"
                                + System.lineSeparator()
                                + System.lineSeparator() + "    private boolean handleDeathScreenHandoff"),
                "客户端 tick 不能再重置按帧平滑的时间基，否则会周期性卡顿。");
    }

    @Test
    void serverAcceptsTheSameBoundedSmoothRotationTotalAsTheClient() throws IOException {
        String serverSource = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/camera/RtsCameraManager.java"));

        assertTrue(serverSource.contains("ROT_INPUT_CLAMP = 160.0F"),
                "服务端旋转上限必须接纳客户端一个 tick 内的有界汇总，否则仍会出现姿态分叉。");
    }
}
