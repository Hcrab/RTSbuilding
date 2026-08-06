package com.rtsbuilding.rtsbuilding.network.builder.handler;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.network.builder.*;
import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import com.rtsbuilding.rtsbuilding.server.service.placement.RtsSmartFillService;
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
                ServiceRegistry.getInstance().binding().setMode(serverPlayer, modes[modeId]);
            }
        });
    }

    public static void handleRotateBlock(C2SRtsRotateBlockPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().placement().rotateBlock(serverPlayer, payload.pos());
            }
        });
    }

    public static void handleOrientBlock(C2SRtsOrientBlockPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer
                    && payload.axisDirection() >= 0
                    && payload.axisDirection() < Direction.values().length
                    && Math.abs(payload.quarterTurns()) == 1) {
                ServiceRegistry.getInstance().placement().rotateBlockStep(
                        serverPlayer,
                        payload.pos(),
                        Direction.from3DDataValue(payload.axisDirection()),
                        payload.quarterTurns());
            }
        });
    }

    public static void handlePlace(C2SRtsPlacePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                Direction face = Direction.from3DDataValue(payload.face());
                if (!payload.statePreset().isBlank()) {
                    RtsbuildingMod.LOGGER.debug(
                            "R placement preset receive: player={}, item={}, preset={}, quickBuild={}, clicked={}",
                            serverPlayer.getGameProfile().getName(),
                            payload.itemId(),
                            payload.statePreset(),
                            payload.quickBuild(),
                            payload.clickedPos());
                }
                ServiceRegistry.getInstance().placement().placeSelected(
                        serverPlayer,
                        payload.clickedPos(),
                        face,
                        payload.hitX(),
                        payload.hitY(),
                        payload.hitZ(),
                        payload.rotateSteps(),
                        payload.statePreset(),
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
                ServiceRegistry.getInstance().placement().enqueuePlaceBatch(
                        serverPlayer,
                        payload.clickedPositions(),
                        face,
                        payload.hitOffsetX(),
                        payload.hitOffsetY(),
                        payload.hitOffsetZ(),
                        payload.rotateSteps(),
                        payload.statePreset(),
                        payload.forcePlace(),
                        payload.skipIfOccupied(),
                        payload.overwriteExisting(),
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

    public static void handleConfirmSmartFill(
            C2SRtsConfirmSmartFillPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsSmartFillService.confirm(serverPlayer, payload);
            }
        });
    }

    public static void handlePlaceFluid(C2SRtsPlaceFluidPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                Direction face = Direction.from3DDataValue(payload.face());
                ServiceRegistry.getInstance().fluid().placeFluid(
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
                ServiceRegistry.getInstance().fluid().storeFluidFromContainer(
                        serverPlayer,
                        payload.sourceType(),
                        payload.toolSlot(),
                        payload.itemId());
            }
        });
    }
}
