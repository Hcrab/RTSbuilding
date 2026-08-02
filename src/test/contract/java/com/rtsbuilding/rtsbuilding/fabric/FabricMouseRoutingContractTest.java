package com.rtsbuilding.rtsbuilding.fabric;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 锁定 Fabric 上下文鼠标路由和连接边界，防止原版交互再次被 RTS 全局绑定吞掉。 */
class FabricMouseRoutingContractTest {
    @Test
    void vanillaMouseOwnersAreRestoredBeforeClientInputSampling() throws IOException {
        String routing = Files.readString(Path.of(
                "src/client/java/com/rtsbuilding/rtsbuilding/fabric/client/FabricVanillaMouseRouting.java"));
        String entry = Files.readString(Path.of(
                "src/client/java/com/rtsbuilding/rtsbuilding/fabric/client/RtsbuildingFabricClient.java"));
        String accessor = Files.readString(Path.of(
                "src/client/java/com/rtsbuilding/rtsbuilding/mixin/KeyMappingAccessor.java"));

        assertTrue(routing.contains("minecraft.options.keyUse"));
        assertTrue(routing.contains("minecraft.options.keyAttack"));
        assertTrue(routing.contains("minecraft.options.keyPickItem"));
        assertTrue(routing.contains("ClientRtsController.get().isEnabled()"));
        assertTrue(accessor.contains("@Accessor(\"MAP\")"));
        assertTrue(entry.indexOf("FabricVanillaMouseRouting.restoreOutsideRts()")
                < entry.indexOf("ClientInputHandler.onClientTickPre()"));
    }

    @Test
    void connectionChangesFailOpenAndLogoutFlushReturnsToServerThread() throws IOException {
        String gate = Files.readString(Path.of(
                "src/client/java/com/rtsbuilding/rtsbuilding/client/input/RtsClientInputGate.java"));
        String lifecycle = Files.readString(Path.of(
                "src/client/java/com/rtsbuilding/rtsbuilding/client/controller/ClientRtsLifecycleOwner.java"));
        String mod = Files.readString(Path.of(
                "src/main/java/com/rtsbuilding/rtsbuilding/RtsbuildingMod.java"));

        assertTrue(gate.contains("onClientLoggingIn()") && gate.contains("resetForConnectionChange()"));
        assertTrue(gate.contains("onClientLoggingOut()"));
        assertTrue(lifecycle.contains("controller.enabled = false"));
        assertTrue(mod.contains("server.execute(() -> onPlayerLogout(handler.player))"));
    }
}
