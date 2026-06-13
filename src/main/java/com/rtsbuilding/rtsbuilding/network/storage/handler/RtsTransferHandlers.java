package com.rtsbuilding.rtsbuilding.network.storage.handler;

import com.rtsbuilding.rtsbuilding.server.service.RtsTransferService;
import net.minecraft.server.level.ServerPlayer;
import com.rtsbuilding.rtsbuilding.forgecompat.network.IPayloadContext;

/**
 * Server-side C2S adapter for linked-storage item transfer actions.
 *
 * <p>Keep inventory mutation and compatibility behavior in
 * RtsTransferService; this layer should only unwrap payloads and enqueue work on
 * the server thread.
 */
public final class RtsTransferHandlers {
    private RtsTransferHandlers() {
    }

    public static void handleFillInventory(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsFillInventoryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsTransferService.fillPlayerInventoryFromLinked(serverPlayer);
            }
        });
    }

    public static void handleLinkedPickup(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkedPickupPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsTransferService.pickupLinkedToCarried(serverPlayer, payload.prototype(), payload.amount());
            }
        });
    }

    public static void handleLinkedQuickMove(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkedQuickMovePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsTransferService.quickMoveLinkedItem(serverPlayer, payload.prototype());
            }
        });
    }

    public static void handleReturnCarried(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsReturnCarriedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsTransferService.returnCarriedToLinked(serverPlayer, payload.itemId(), payload.amount());
            }
        });
    }

    public static void handleImportMenuSlot(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsImportMenuSlotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsTransferService.importMenuSlotToLinked(serverPlayer, payload.menuSlot());
            }
        });
    }
}
