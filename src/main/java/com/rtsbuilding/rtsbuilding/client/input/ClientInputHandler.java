package com.rtsbuilding.rtsbuilding.client.input;


import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.GAME)
public final class ClientInputHandler {
    private static boolean toggleKeyWasDown = false;
    private static int toggleCooldownTicks = 0;

    private ClientInputHandler() {
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        ClientRtsController.get().preTick();
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            toggleKeyWasDown = false;
            toggleCooldownTicks = 0;
            ClientRtsController.get().tick();
            return;
        }

        if (toggleCooldownTicks > 0) {
            toggleCooldownTicks--;
        }

        boolean toggleKeyDown = ClientKeyMappings.TOGGLE_RTS.isDown();
        if (!toggleKeyDown && toggleKeyWasDown && toggleCooldownTicks == 0) {
            RtsClientPacketGateway.sendToggleCamera(ClientRtsController.get().isStartCameraAtPlayerHead());
            toggleCooldownTicks = 6;
        }
        toggleKeyWasDown = toggleKeyDown;

        ClientRtsController.get().tick();
    }
}
