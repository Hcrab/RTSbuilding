package com.rtsbuilding.rtsbuilding.client;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 防止 RTS 镜头重构时再次漏掉玩家位置包同步入口。
 */
class LocalPlayerCameraSyncContractTest {
    @Test
    void localMirrorCameraKeepsVanillaPlayerMovementPacketsEnabled() throws Exception {
        String mixin = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/mixin/LocalPlayerMixin.java"));
        String config = Files.readString(Path.of(
                "src/main/resources/rtsbuilding.mixins.json"));
        String controller = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/client/controller/ClientRtsController.java"));

        assertTrue(mixin.contains("@Mixin(LocalPlayer.class)")
                        && mixin.contains("method = { \"isControlledCamera\", \"m_108636_\" }")
                        && mixin.contains("remap = false")
                        && mixin.contains("ClientRtsController.get().isEnabled()")
                        && config.contains("\"LocalPlayerMixin\""),
                "Forge 必须注册 LocalPlayerMixin，保证 RTS 镜头期间继续发送玩家移动包");
        assertTrue(controller.contains("setCameraEntity(this.localMirrorCamera)"),
                "契约只适用于实际切换到本地镜像相机的 RTS 控制器");
    }
}
