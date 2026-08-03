package com.rtsbuilding.rtsbuilding.bootstrap;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import zone.rong.mixinbooter.IEarlyMixinLoader;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 在 Minecraft 基类加载前，把 RTSBuilding 的基础 Mixin 配置交给 MixinBooter。
 *
 * <p>MixinBooter 5 只从 Forge 的 coremod 列表中寻找 {@link IEarlyMixinLoader}，因此本类
 * 同时是一个无 ASM transformer 的最小 {@link IFMLLoadingPlugin}。它只负责配置发现，
 * 不持有兼容业务状态，也不自行改写字节码。</p>
 *
 * <p>引导类必须位于 Mixin 配置声明的包之外。Mixin 会保护其专用包，配置生效后直接加载
 * 其中的普通类会触发 {@code IllegalClassLoadError}。</p>
 */
@IFMLLoadingPlugin.Name("RTSBuilding Mixin Loader")
@IFMLLoadingPlugin.MCVersion("1.12.2")
public final class RtsMixinConfigLoader implements IFMLLoadingPlugin, IEarlyMixinLoader {
    private static final List<String> CONFIGS =
            Collections.singletonList("mixins.rtsbuilding.json");

    @Override
    public List<String> getMixinConfigs() {
        return CONFIGS;
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
        // 配置由 MixinBooter 的早期加载阶段统一排队，这里不接管 Forge 注入数据。
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
