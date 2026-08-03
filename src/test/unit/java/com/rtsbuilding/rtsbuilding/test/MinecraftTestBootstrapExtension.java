package com.rtsbuilding.rtsbuilding.test;

import net.minecraft.SharedConstants;
import net.minecraft.commands.arguments.selector.options.EntitySelectorOptions;
import net.minecraft.core.Registry;
import net.minecraft.core.cauldron.CauldronInteraction;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.alchemy.PotionBrewing;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraftforge.registries.GameData;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

import java.lang.reflect.Field;

/**
 * 为纯 JVM 测试初始化原版注册表，但不启动 Forge 的网络层。
 *
 * <p>Forge 1.19.2 在 {@link Bootstrap#bootStrap()} 尾部初始化旧网络事件总线；
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

        // 1.19.2 没有 BuiltInRegistries.bootStrap()，因此按该版本 Bootstrap 的
        // 原版初始化顺序执行到注册表冻结为止。反射只跳过最后的 Forge 网络层，
        // 不进入生产代码；GameTest 与真实服务端仍调用完整 Bootstrap。
        Field field = Bootstrap.class.getDeclaredField("isBootstrapped");
        field.setAccessible(true);
        field.setBoolean(null, true);
        if (Registry.REGISTRY.keySet().isEmpty()) {
            throw new IllegalStateException("Unable to load registries");
        }
        FireBlock.bootStrap();
        ComposterBlock.bootStrap();
        if (EntityType.getKey(EntityType.PLAYER) == null) {
            throw new IllegalStateException("Failed loading EntityTypes");
        }
        PotionBrewing.bootStrap();
        EntitySelectorOptions.bootStrap();
        DispenseItemBehavior.bootStrap();
        CauldronInteraction.bootStrap();
        Registry.freezeBuiltins();
        GameData.vanillaSnapshot();
        bootstrapped = true;
    }
}
