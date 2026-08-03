package com.rtsbuilding.rtsbuilding.bootstrap;

import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.Arrays;
import java.util.List;

/**
 * 等 Forge 把普通模组 JAR 注入 classpath 后，再排队第三方模组兼容 Mixin。
 *
 * <p>本类只负责 JEI/HEI、Jade 一类可选目标。它绝不能包含 Minecraft 基类 Mixin，
 * 否则大型整合包会在晚期准备配置时遇到目标类已经加载。反过来，可选目标也不能放进
 * 早期 coremod 配置，否则 Mixin 会在第三方 JAR 可见前把目标误判为不存在。</p>
 */
public final class RtsLateMixinConfigLoader implements ILateMixinLoader {
    private static final List<String> CONFIGS = Arrays.asList(
            "mixins.rtsbuilding_jei.json",
            "mixins.rtsbuilding_jade.json");

    @Override
    public List<String> getMixinConfigs() {
        return CONFIGS;
    }
}
