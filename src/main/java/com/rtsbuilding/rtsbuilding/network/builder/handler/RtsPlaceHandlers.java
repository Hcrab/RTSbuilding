package com.rtsbuilding.rtsbuilding.network.builder.handler;

import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.network.builder.*;
import com.rtsbuilding.rtsbuilding.server.service.RtsBindingService;
import com.rtsbuilding.rtsbuilding.server.service.RtsFluidService;
import com.rtsbuilding.rtsbuilding.server.service.RtsPlacementService;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import com.rtsbuilding.rtsbuilding.forgecompat.network.IPayloadContext;

/**
 * Server-side C2S adapter for RTS placement and fluid actions.
 *
 * <p>Keep placement validation, item extraction, and undo recording in
 * RtsPlacementService; this layer should only unwrap payloads and enqueue work
 * on the server thread.
 */
public final class RtsPlaceHandlers {
    private RtsPlaceHandlers() {
    }

    public static void handleSetMode(C2SRtsSetModePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                int modeId = payload.mode();
                var modes = BuilderMode.values();
                if (modeId < 0 || modeId >= modes.length) {
                    return;
                }
                RtsBindingService.setMode(serverPlayer, modes[modeId]);
            }
        });
    }

    public static void handleRotateBlock(C2SRtsRotateBlockPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsPlacementService.rotateBlock(serverPlayer, payload.pos());
            }
        });
    }

    public static void handlePlace(C2SRtsPlacePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                Direction face = Direction.from3DDataValue(payload.face());
                RtsPlacementService.placeSelected(
                        serverPlayer,
                        payload.clickedPos(),
                        face,
                        payload.hitX(),
                        payload.hitY(),
                        payload.hitZ(),
                        payload.rotateSteps(),
                        payload.forcePlace(),
                        payload.skipIfOccupied(),
                        payload.itemId(),
                        payload.itemPrototype(),
                        payload.rayOriginX(),
                        payload.rayOriginY(),
                        payload.rayOriginZ(),
                        payload.rayDirX(),
                        payload.rayDirY(),
                        payload.rayDirZ(),
                        payload.quickBuild(),
                        payload.forceEmptyHand());
            }
        });
    }

    public static void handlePlaceBatch(C2SRtsPlaceBatchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                Direction face = Direction.from3DDataValue(payload.face());
                RtsPlacementService.enqueuePlaceBatch(
                        serverPlayer,
                        payload.clickedPositions(),
                        face,
                        payload.hitOffsetX(),
                        payload.hitOffsetY(),
                        payload.hitOffsetZ(),
                        payload.rotateSteps(),
                        payload.forcePlace(),
                        payload.skipIfOccupied(),
                        payload.itemId(),
                        payload.itemPrototype(),
                        payload.rayOriginX(),
                        payload.rayOriginY(),
                        payload.rayOriginZ(),
                        payload.rayDirX(),
                        payload.rayDirY(),
                        payload.rayDirZ());
            }
        });
    }

    public static void handlePlaceFluid(C2SRtsPlaceFluidPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                Direction face = Direction.from3DDataValue(payload.face());
                RtsFluidService.placeFluid(
                        serverPlayer,
                        payload.clickedPos(),
                        face,
                        payload.hitX(),
                        payload.hitY(),
                        payload.hitZ(),
                        payload.forcePlace(),
                        payload.fluidId(),
                        payload.rayOriginX(),
                        payload.rayOriginY(),
                        payload.rayOriginZ(),
                        payload.rayDirX(),
                        payload.rayDirY(),
                        payload.rayDirZ());
            }
        });
    }

    public static void handleStoreFluid(C2SRtsStoreFluidPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsFluidService.storeFluidFromContainer(
                        serverPlayer,
                        payload.sourceType(),
                        payload.toolSlot(),
                        payload.itemId());
            }
        });
    }
}
