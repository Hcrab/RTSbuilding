package com.rtsbuilding.rtsbuilding.client.compat;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RtsRemoteMenuClientSafetyContractTest {
    @Test
    void lifecycleKeepsThrowableFallbackAndWritesClientDiagnostics() throws Exception {
        String source = read("src/main/java/com/rtsbuilding/rtsbuilding/client/controller/ClientRtsLifecycleOwner.java");

        assertTrue(source.contains("catch (Throwable throwable)"),
                "第三方 Menu 的构造、链接或反射异常必须由客户端总 fallback 接住。");
        assertTrue(source.contains("RtsRemoteMenuClientDiagnostics.validationApplied"),
                "成功放宽 stillValid 后应记录低噪声客户端诊断摘要。");
        assertTrue(source.contains("RtsRemoteMenuClientDiagnostics.screenlessRecovery"),
                "Menu 存在但 Screen 丢失时必须记录恢复原因。");
        assertTrue(source.contains("RtsRemoteMenuClientDiagnostics.observe"),
                "远程 GUI 生命周期应持续经过状态压缩器观察。");
    }

    @Test
    void failurePathClosesMenuInsteadOfCrashingClient() throws Exception {
        String source = read("src/main/java/com/rtsbuilding/rtsbuilding/client/controller/ClientRtsCommandOwner.java");

        assertTrue(source.contains("RtsRemoteMenuClientDiagnostics.compatFailure"));
        assertTrue(source.contains("RtsClientPacketGateway.sendCloseRemoteMenu()"));
        assertTrue(source.contains("minecraft.player.closeContainer()"));
        assertTrue(source.contains("minecraft.setScreen(null)"));
    }

    @Test
    void optionalCompatIsNonInitializingAndMixinTargetsAreOptional() throws Exception {
        String compat = read("src/main/java/com/rtsbuilding/rtsbuilding/client/compat/RtsClientRemoteMenuCompat.java");
        String mixin = read("src/main/java/com/rtsbuilding/rtsbuilding/mixin/ModdedRemoteStillValidMixin.java");

        assertTrue(compat.contains("Class.forName(className, false, loader)"),
                "探测可选模组类型时不得触发其静态初始化。");
        assertTrue(compat.contains("InaccessibleObjectException"),
                "反射不可访问应当退回原始校验，不能炸掉客户端。");
        assertTrue(mixin.contains("require = 0"),
                "可选模组目标缺失或签名变化时，Mixin 注入必须允许安全跳过。");
    }

    private static String read(String relativePath) throws Exception {
        return Files.readString(Path.of(relativePath));
    }
}
