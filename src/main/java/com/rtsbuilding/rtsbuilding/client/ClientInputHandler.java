package com.rtsbuilding.rtsbuilding.client;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientInputHandler {
    private static boolean toggleKeyWasDown = false;
    private static int toggleCooldownTicks = 0;
    private static java.lang.reflect.Field inputField;
    private static Object savedInput;

    /** Find the Input-typed field by type name (SRG-safe, no compile-time class ref). */
    private static java.lang.reflect.Field inputField() {
        if (inputField == null) {
            for (final java.lang.reflect.Field f : Minecraft.getInstance().player.getClass().getFields()) {
                if (f.getType().getSimpleName().contains("Input")) {
                    inputField = f;
                    break;
                }
            }
        }
        return inputField;
    }

    private ClientInputHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            final Object player = Minecraft.getInstance().player;
            if (player != null && ClientRtsController.get().isEnabled()) {
                try {
                    final java.lang.reflect.Field f = inputField();
                    if (f != null) {
                        savedInput = f.get(player);
                        f.set(player, RtsFreeCamInput.dummy());
                    }
                } catch (final Exception ignored) {
                }
            }
            ClientRtsController.get().preTick();
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final Object saved = savedInput;
        if (saved != null && minecraft.player != null) {
            try {
                final java.lang.reflect.Field f = inputField();
                if (f != null) f.set(minecraft.player, saved);
            } catch (final Exception ignored) {
            }
            savedInput = null;
        }

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