package com.rtsbuilding.rtsbuilding.test;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraftforge.registries.GameData;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Field;

/**
 * 为纯 JVM 测试初始化原版注册表，但不启动 Forge 的网络层。
 *
 * <p>Forge 1.20.1 在 {@link Bootstrap#bootStrap()} 尾部初始化旧网络事件总线；
 * 该路径依赖完整游戏启动环境，JUnit 类路径并不具备这些条件。这里仅执行
 * ItemStack、Items 等纯逻辑测试真正需要的原版注册表阶段，避免测试基础设施
 * 把网络生命周期误当成业务前置条件。GameTest 和真实服务端仍走完整引导。</p>
 */
public final class MinecraftTestBootstrapExtension implements BeforeAllCallback {
    private static boolean bootstrapped;

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        ensureBootstrapped();
    }

    private static synchronized void ensureBootstrapped() throws Exception {
        if (bootstrapped) {
            return;
        }
        SharedConstants.tryDetectVersion();

        // BuiltInRegistries 构造 MappedRegistry 时会检查此标志；完整 Bootstrap
        // 也是在首次接触注册表之前设置它。反射仅限测试进程，不进入生产代码。
        Field field = Bootstrap.class.getDeclaredField("isBootstrapped");
        field.setAccessible(true);
        field.setBoolean(null, true);

        BuiltInRegistries.bootStrap();
        // Forge 的包装注册表要在原版内容冻结后建立快照，否则 getKey 会把
        // 已存在的原版物品回退成默认值（通常是 minecraft:air）。
        GameData.vanillaSnapshot();
        bootstrapped = true;
    }
}
