package com.rtsbuilding.rtsbuilding.bootstrap;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.gtnewhorizon.gtnhmixins.ILateMixinLoader;
import com.gtnewhorizon.gtnhmixins.LateMixin;

/**
 * GTNH 第三方模组已经进入 classpath 后加载的兼容 Mixin 入口。
 *
 * <p>1.12.2 的 JEI/Jade 配置不能在 1.7.10 直接启用。后续 NEI、Waila、AE2 与
 * GregTech 兼容会逐项加入专用配置；在此之前保持空配置比误判目标类更安全。</p>
 */
@LateMixin
public final class RtsLateMixinConfigLoader implements ILateMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.rtsbuilding_gtnh_late.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedMods) {
        return Collections.emptyList();
    }
}
