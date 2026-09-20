package com.rtsbuilding.rtsbuilding.test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.neoforged.fml.loading.LoadingModList;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.List;
import java.util.Map;

/**
 * 在任何测试类接触 ItemStack 前初始化真实原版注册表，避免测试顺序毒化静态类。
 * 这里只补纯 JVM 测试所需的注册表环境，不启动客户端、世界或服务端；GameTest 仍走完整 loader。
 */
public final class MinecraftTestBootstrapExtension implements BeforeAllCallback {
    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        if (net.neoforged.fml.loading.FMLPaths.GAMEDIR.get() == null) {
            net.neoforged.fml.loading.FMLPaths.loadAbsolutePaths(
                    java.nio.file.Files.createTempDirectory("rts-junit-neoforge-"));
        }
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }
}
