package com.rtsbuilding.rtsbuilding.energy.client;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.energy.RtsEnergyBlocks;
import com.rtsbuilding.rtsbuilding.energy.RtsEnergyMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * Client-side registration for the built-in energy addon.
 * <p>
 * This class is picked up by {@code @EventBusSubscriber} unconditionally, so it
 * must guard against the addon being disabled via config — in that case the
 * energy blocks were never registered and their holders are unbound.
 */
@EventBusSubscriber(modid = RtsEnergyMod.MODID, value = Dist.CLIENT)
public final class RtsEnergyClient {

    private RtsEnergyClient() {
    }

    @SubscribeEvent
    public static void onRegisterClientExtensions(RegisterClientExtensionsEvent event) {
        if (!Config.isTechnologizedEnabled()) {
            return;
        }
        // Custom break particles: consolidate multi-element collision shapes into
        // a single bounding-box particle burst (see RtsBlockRenderProperties).
        event.registerBlock(RtsBlockRenderProperties.INSTANCE, RtsEnergyBlocks.ENERGY_BANK.get());
        event.registerBlock(RtsBlockRenderProperties.INSTANCE, RtsEnergyBlocks.THERMAL_GENERATOR.get());
    }
}
