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

        assertTrue(renderSyncSource.contains("TickEvent.RenderTickEvent"),
                "1.12.2 视觉镜头必须挂接 Forge 的渲染 tick 入口。");
        assertTrue(renderSyncSource.contains("event.phase != TickEvent.Phase.START"),
                "视觉镜头必须在 1.12.2 GameRenderer 使用视角前、START 阶段更新。");
        assertFalse(renderSyncSource.contains("TickEvent.Phase.END"),
                "END 阶段已经太晚，不能用来驱动本帧镜头。");
        assertFalse(controllerSource.contains(
                        "this.cameraOrbitService.syncVisualCameraFrame(minecraft, this.anchorX, this.anchorY, this.anchorZ, this.maxRadius, this.enabled);"
                                + System.lineSeparator() + "    }"
                                + System.lineSeparator()
                                + System.lineSeparator() + "    private boolean handleDeathScreenHandoff"),
                "客户端 tick 不能再重置按帧平滑的时间基，否则会周期性卡顿。");
    }

    @Test
    void mirrorCameraSnapsEveryInterpolationBaselineTogether() throws IOException {
        String entitySource = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/common/entity/RtsCameraEntity.java"));

        assertTrue(entitySource.contains("prevPosX = x")
                        && entitySource.contains("prevPosY = y")
                        && entitySource.contains("prevPosZ = z"),
                "本帧视觉镜头必须同步 prevPos，避免位置插值在旧坐标和新坐标之间闪烁。");
        assertTrue(entitySource.contains("lastTickPosX = x")
                        && entitySource.contains("lastTickPosY = y")
                        && entitySource.contains("lastTickPosZ = z"),
                "1.12.2 还必须同步 lastTickPos，避免 GameRenderer 使用过期 tick 基线。");
        assertTrue(entitySource.contains("prevRotationYaw = yaw")
                        && entitySource.contains("prevRotationPitch = pitch"),
                "镜头旋转的上一帧基线必须与当前姿态一起推进。");
    }

    @Test
    void serverAcceptsTheSameBoundedSmoothRotationTotalAsTheClient() throws IOException {
        String serverSource = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/camera/RtsCameraManager.java"));

        assertTrue(serverSource.contains("ROT_INPUT_CLAMP = 160.0F"),
                "服务端旋转上限必须接纳客户端一个 tick 内的有界汇总，否则仍会出现姿态分叉。");
    }
}
