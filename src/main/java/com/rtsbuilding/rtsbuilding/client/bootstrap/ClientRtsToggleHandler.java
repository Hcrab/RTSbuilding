package com.rtsbuilding.rtsbuilding.client.bootstrap;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.infrastructure.di.CompositionRoot;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.input.RtsKeyMappings;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;


@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class ClientRtsToggleHandler {

    private ClientRtsToggleHandler() {}

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        
        if (!RtsKeyMappings.TOGGLE_RTS_KEY.consumeClick()) {
            return;
        }

        RtsClientKernel kernel = CompositionRoot.get().kernel();
        if (!kernel.isInitialized()) return;

        
        CameraModule cam = kernel.module(CameraModule.class);
        boolean currentlyEnabled = cam != null && cam.getState().isEnabled();

        
        RtsClientPacketGateway.sendToggleCamera(!currentlyEnabled);
    }
}
