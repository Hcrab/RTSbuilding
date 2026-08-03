package com.rtsbuilding.rtsbuilding.release;

import com.rtsbuilding.rtsbuilding.bootstrap.RtsLateMixinConfigLoader;
import com.rtsbuilding.rtsbuilding.bootstrap.RtsMixinConfigLoader;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.junit.jupiter.api.Test;
import zone.rong.mixinbooter.IEarlyMixinLoader;
import zone.rong.mixinbooter.ILateMixinLoader;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/** 锁定 MM 所用 MixinBooter 5 的两阶段配置发现，避免基础类过晚或第三方类过早。 */
class LegacyMixinLoaderContractTest {
    @Test
    void minecraftAndOptionalModMixinsUseDifferentDiscoveryPhases() {
        RtsMixinConfigLoader earlyLoader = new RtsMixinConfigLoader();
        RtsLateMixinConfigLoader lateLoader = new RtsLateMixinConfigLoader();

        assertInstanceOf(IEarlyMixinLoader.class, earlyLoader);
        assertInstanceOf(IFMLLoadingPlugin.class, earlyLoader);
        assertEquals(Arrays.asList("mixins.rtsbuilding.json"), earlyLoader.getMixinConfigs());
        assertInstanceOf(ILateMixinLoader.class, lateLoader);
        assertEquals(Arrays.asList("mixins.rtsbuilding_jei.json", "mixins.rtsbuilding_jade.json"),
                lateLoader.getMixinConfigs());
    }
}
