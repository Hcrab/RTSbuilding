package com.rtsbuilding.rtsbuilding.client.input;

import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.diagnostic.RtsClientOperationDiagnostics;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.pathfinding.RtsClientPathfinding;
import com.rtsbuilding.rtsbuilding.client.rendering.animation.ClientFakeAirBlocks;
import net.minecraft.client.Minecraft;

public final class ClientInputHandler {
  private static boolean toggleKeyWasDown = false;
  private static int toggleCooldownTicks = 0;

  private ClientInputHandler() {}

  public static void onClientTickPre() {
    ClientRtsController.get().preTick();
    RtsClientPathfinding.tickPre();
  }

  public static void onClientTickPost() {
    ClientFakeAirBlocks.tick();
    RtsClientOperationDiagnostics.expireTimedOut();
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
      RtsClientPacketGateway.sendToggleCamera(
          ClientRtsController.get().isStartCameraAtPlayerHead());
      toggleCooldownTicks = 6;
    }
    toggleKeyWasDown = toggleKeyDown;

    ClientRtsController.get().tick();
  }
}
