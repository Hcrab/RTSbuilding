package com.rtsbuilding.rtsbuilding.network.storage.handler;

import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import com.rtsbuilding.rtsbuilding.network.RtsPayloadContext;

/**
 * Server-side C2S adapter for linked-storage binding and GUI overlay actions.
 *
 * <p>Keep inventory mutation, storage lookup, and compatibility behavior in
 * RtsBindingService; this layer should only unwrap payloads and enqueue work on
 * the server thread.
 */
public final class RtsBindingHandlers {
    private RtsBindingHandlers() {
    }

    public static void handleSetFunnel(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetFunnelPayload payload, RtsPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().binding().setFunnelEnabled(serverPlayer, payload.enabled());
            }
        });
    }

    public static void handleSetAutoStore(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetAutoStorePayload payload, RtsPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().binding().setAutoStoreMinedDrops(serverPlayer, payload.enabled());
            }
        });
    }

    public static void handleSetBdNetwork(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetBdNetworkPayload payload, RtsPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().binding().setBdNetworkEnabled(serverPlayer, payload.enabled());
            }
        });
    }

    public static void handleLinkStorage(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStoragePayload payload, RtsPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().binding().linkStorage(serverPlayer, payload.pos(), payload.linkMode());
            }
        });
    }

    public static void handleBatchLinkStorage(
            com.rtsbuilding.rtsbuilding.network.storage.C2SRtsBatchLinkStoragePayload payload,
            RtsPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().binding().linkStoragesInSelection(
                        serverPlayer, payload.first(), payload.second(), payload.linkMode());
            }
        });
    }

    public static void handleUnlinkStorage(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsUnlinkStoragePayload payload, RtsPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().binding().unlinkStorage(
                        serverPlayer,
                        ResourceKey.create(Registries.DIMENSION, payload.dimension()),
                        payload.pos());
            }
        });
    }

    public static void handleUpdateLinkedStorage(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsUpdateLinkedStoragePayload payload, RtsPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().binding().updateLinkedStorageSettings(
                        serverPlayer,
                        ResourceKey.create(Registries.DIMENSION, payload.dimension()),
                        payload.pos(),
                        payload.linkMode(),
                        payload.priority());
            }
        });
    }

    public static void handleStoreHotbarSlot(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsStoreHotbarSlotPayload payload, RtsPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().binding().storeHotbarSlot(serverPlayer, payload.slot());
            }
        });
    }

    public static void handleSetQuickSlot(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetQuickSlotPayload payload, RtsPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().binding().setQuickSlot(serverPlayer, payload.slot(), payload.itemId(), payload.previewStack());
            }
        });
    }

    public static void handleSetGuiBinding(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetGuiBindingPayload payload, RtsPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().binding().setGuiBinding(
                        serverPlayer,
                        payload.slot(),
                        payload.clear(),
                        payload.pos(),
                        payload.face(),
                        payload.itemIdHint());
            }
        });
    }

    public static void handleOpenGuiBinding(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsOpenGuiBindingPayload payload, RtsPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().binding().openGuiBinding(serverPlayer, payload.slot());
            }
        });
    }

    public static void handleFunnelTarget(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsFunnelTargetPayload payload, RtsPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().binding().updateFunnelTarget(serverPlayer, payload.target());
            }
        });
    }

    public static void handleCloseRemoteMenu(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsCloseRemoteMenuPayload payload, RtsPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().binding().closeRemoteMenu(serverPlayer);
            }
        });
    }
}
