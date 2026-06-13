package com.rtsbuilding.rtsbuilding.client.input;


import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.rendering.animation.ClientFakeAirBlocks;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientInputHandler {
    private static boolean toggleKeyWasDown = false;
    private static int toggleCooldownTicks = 0;

    private ClientInputHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            ClientRtsController.get().preTick();
            return;
        }

        ClientFakeAirBlocks.tick();
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
