package com.rtsbuilding.rtsbuilding.client.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
    void serverAcceptsTheSameBoundedSmoothRotationTotalAsTheClient() throws IOException {
        String serverSource = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/server/camera/RtsCameraManager.java"));

        assertTrue(serverSource.contains("ROT_INPUT_CLAMP = 160.0F"),
                "服务端旋转上限必须接纳客户端一个 tick 内的有界汇总，否则仍会出现姿态分叉。");
    }
}
