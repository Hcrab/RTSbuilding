package com.rtsbuilding.rtsbuilding.client.input;


import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.pathfinding.RtsClientPathfinding;
import com.rtsbuilding.rtsbuilding.client.rendering.animation.ClientFakeAirBlocks;
import net.minecraft.client.Minecraft;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;

public final class ClientInputHandler {
    private static boolean toggleKeyWasDown = false;
    private static int toggleCooldownTicks = 0;

    private ClientInputHandler() {
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            ClientRtsController.get().preTick();
            RtsClientPathfinding.tickPre();
            return;
        }
        ClientFakeAirBlocks.tick();
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.thePlayer == null) {
            toggleKeyWasDown = false;
            toggleCooldownTicks = 0;
            ClientRtsController.get().tick();
            return;
        }

        if (toggleCooldownTicks > 0) {
            toggleCooldownTicks--;
        }

        boolean toggleKeyDown = ClientKeyMappings.TOGGLE_RTS.getIsKeyPressed();
        if (!toggleKeyDown && toggleKeyWasDown && toggleCooldownTicks == 0) {
            RtsClientPacketGateway.sendToggleCamera(ClientRtsController.get().isStartCameraAtPlayerHead());
            toggleCooldownTicks = 6;
        }
        toggleKeyWasDown = toggleKeyDown;

        ClientRtsController.get().tick();
    }
}
