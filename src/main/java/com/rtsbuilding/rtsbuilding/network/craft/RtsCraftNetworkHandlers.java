package com.rtsbuilding.rtsbuilding.network.craft;

import com.rtsbuilding.rtsbuilding.server.service.RtsCraftingService;
import net.minecraft.server.level.ServerPlayer;
import com.rtsbuilding.rtsbuilding.forgecompat.network.IPayloadContext;

/**
 * Server-side C2S adapter for RTS crafting actions.
 *
 * Keep recipe scanning, grid refill, JEI transfer, and output insertion in
 * RtsStorageManager; this layer should only unwrap payloads and enqueue work on
 * the server thread.
 */
public final class RtsCraftNetworkHandlers {
    private RtsCraftNetworkHandlers() {
    }

    public static void handleRequestCraftables(C2SRtsRequestCraftablesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsCraftingService.requestCraftables(
                        serverPlayer,
                        payload.search(),
                        payload.showUnavailable(),
                        payload.offset(),
                        payload.limit(),
                        payload.pinyinSearchEnabled(),
                        payload.localizedSearchMatches());
            }
        });
    }

    public static void handleOpenCraftTerminal(C2SRtsOpenCraftTerminalPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsCraftingService.openCraftTerminal(serverPlayer);
            }
        });
    }

    public static void handleCraftRefill(C2SRtsCraftRefillPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsCraftingService.refillCurrentCraftGridFromBlueprintStacks(
                        serverPlayer,
                        payload.blueprintStacks(),
                        payload.craftedItemId(),
                        payload.craftedCount());
            }
        });
    }

    public static void handleCraftRecipe(C2SRtsCraftRecipePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsCraftingService.craftRecipeToLinked(serverPlayer, payload.recipeId(), payload.craftCount());
            }
        });
    }

    public static void handleJeiTransfer(C2SRtsJeiTransferPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsCraftingService.applyJeiTransfer(
                        serverPlayer,
                        payload.recipeId(),
                        payload.ingredientPrototypes(),
                        payload.maxTransfer(),
                        payload.clearGridFirst());
            }
        });
    }
}
