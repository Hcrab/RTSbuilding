package com.rtsbuilding.rtsbuilding.platform.event;

import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraftforge.common.MinecraftForge;

import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.Set;

/**
 * 1.7.10 对现代自动事件订阅器的显式生命周期替代。
 *
 * <p>本类只负责创建订阅对象并把它同时挂到 Forge 与 FML 总线；它不引用任何客户端类。
 * 客户端调用方必须用字符串或只在客户端加载的调用点传入类型，避免专用服务器解析
 * {@code net.minecraft.client}。同时注册两条总线是必要的：方块/渲染事件在 Forge 总线，
 * tick/登录事件在 FML 总线；不属于该总线的事件不会被重复触发。</p>
 */
public final class LegacyEventRegistrar {
    private static final Set<String> REGISTERED_TYPES = new HashSet<String>();

    private LegacyEventRegistrar() {
    }

    public static synchronized void registerClass(Class<?> subscriberType) {
        if (subscriberType == null || REGISTERED_TYPES.contains(subscriberType.getName())) return;
        try {
            Constructor<?> constructor = subscriberType.getDeclaredConstructor();
            constructor.setAccessible(true);
            registerInstanceInternal(constructor.newInstance());
            REGISTERED_TYPES.add(subscriberType.getName());
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("无法注册旧版事件订阅器: " + subscriberType.getName(), failure);
        }
    }

    public static synchronized void registerInstance(Object subscriber) {
        if (subscriber == null || REGISTERED_TYPES.contains(subscriber.getClass().getName())) return;
        registerInstanceInternal(subscriber);
        REGISTERED_TYPES.add(subscriber.getClass().getName());
    }

    public static void registerByName(String className) {
        try {
            registerClass(Class.forName(className));
        } catch (ClassNotFoundException failure) {
            throw new IllegalStateException("找不到旧版事件订阅器: " + className, failure);
        }
    }

    private static void registerInstanceInternal(Object subscriber) {
        MinecraftForge.EVENT_BUS.register(subscriber);
        FMLCommonHandler.instance().bus().register(subscriber);
    }
}
