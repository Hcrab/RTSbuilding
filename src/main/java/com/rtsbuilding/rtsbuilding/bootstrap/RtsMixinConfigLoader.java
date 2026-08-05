package com.rtsbuilding.rtsbuilding.bootstrap;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cpw.mods.fml.relauncher.IFMLLoadingPlugin;

import com.gtnewhorizon.gtnhmixins.IEarlyMixinLoader;

/**
 * 把 RTSBuilding 的早期 Mixin 配置交给 GTNH 的 UniMixins 启动链。
 *
 * <p>本类只负责配置发现，不持有玩法状态，也不自行改写字节码。1.12.2 使用的
 * MixinBooter 5 接口与 GTNH 的加载器接口并不兼容，因此这里必须作为明确的
 * 1.7.10 边界；不能仅靠改包名后继续返回配置列表。</p>
 */
@IFMLLoadingPlugin.Name("RTSBuilding GTNH Mixin Loader")
@IFMLLoadingPlugin.MCVersion("1.7.10")
public final class RtsMixinConfigLoader implements IFMLLoadingPlugin, IEarlyMixinLoader {

    @Override
    public String getMixinConfig() {
        return "mixins.rtsbuilding_gtnh_early.json";
    }

    @Override
    public List<String> getMixins(Set<String> loadedCoreMods) {
        // 现有 1.12.2 Mixin 会逐项本地化后再加入；空列表保证首个 GTNH 启动基线安全。
        // IEarlyMixinLoader 会用这里的结果替换配置文件里的 common mixins；
        // client/server 数组仍由 Mixin 按运行侧处理。这里必须显式返回公共入口，
        // 否则远程容器兼容层会看似打包成功、实际从未注入。
        return Arrays.asList(
                "ForgeHooksRemoteContainerMixin",
                "ChestMenuMixin");
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
        // UniMixins 负责配置排队；RTSBuilding 不接管 Forge 注入数据。
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
