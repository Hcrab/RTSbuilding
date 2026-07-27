package com.rtsbuilding.rtsbuilding.client.input;


import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.pathfinding.RtsClientPathfinding;
import com.rtsbuilding.rtsbuilding.client.rendering.animation.ClientFakeAirBlocks;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;

@Mod.EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Side.CLIENT)
public final class ClientInputHandler {
    private static boolean toggleKeyWasDown = false;
    private static int toggleCooldownTicks = 0;

    private ClientInputHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            ClientRtsController.get().preTick();
            RtsClientPathfinding.tickPre();
            return;
        }
        ClientFakeAirBlocks.tick();
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null) {
            toggleKeyWasDown = false;
            toggleCooldownTicks = 0;
            ClientRtsController.get().tick();
            return;
        }

        if (toggleCooldownTicks > 0) {
            toggleCooldownTicks--;
        }

        boolean toggleKeyDown = ClientKeyMappings.TOGGLE_RTS.isKeyDown();
        if (!toggleKeyDown && toggleKeyWasDown && toggleCooldownTicks == 0) {
            RtsClientPacketGateway.sendToggleCamera(ClientRtsController.get().isStartCameraAtPlayerHead());
            toggleCooldownTicks = 6;
        }
        toggleKeyWasDown = toggleKeyDown;

        ClientRtsController.get().tick();
    }
}
