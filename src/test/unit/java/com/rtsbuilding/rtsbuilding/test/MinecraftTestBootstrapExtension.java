package com.rtsbuilding.rtsbuilding.test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraftforge.fml.loading.LoadingModList;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.util.List;

/**
 * 为需要真实 Minecraft 类型的纯 JVM 测试统一建立 Forge 1.20.1 运行时前置条件。
 *
 * <p>1.20.1 的 {@code ResourceKey}、{@code Item} 与 {@code ItemStack} 在首次加载时
 * 会访问内建注册表；若测试进程没有先执行 Minecraft bootstrap，它们会把相关类永久
 * 标记为初始化失败，并连带污染同一进程中随后执行的任务编解码、存储和工作流测试。
 * 这里在每个测试类初始化之前完成一次进程级引导，不替代 GameTest，也不伪造注册表内容。
 *
 * <p>Forge 对 {@link Bootstrap#bootStrap()} 增加了网络初始化，因此必须先提供一个空的
 * {@link LoadingModList}。正常游戏已有真实加载列表；这个空列表只存在于 JUnit 进程中。
 */
public final class MinecraftTestBootstrapExtension implements BeforeAllCallback {
    private static boolean bootstrapped;

    @Override
    public void beforeAll(ExtensionContext context) {
        ensureBootstrapped();
    }

    private static synchronized void ensureBootstrapped() {
        if (bootstrapped) {
            return;
        }
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), null);
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        bootstrapped = true;
    }
}
