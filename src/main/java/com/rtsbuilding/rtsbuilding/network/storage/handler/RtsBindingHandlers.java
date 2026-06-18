package com.rtsbuilding.rtsbuilding.network.storage.handler;

import com.rtsbuilding.rtsbuilding.server.service.ServiceRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

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

    public static void handleSetFunnel(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetFunnelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().binding().setFunnelEnabled(serverPlayer, payload.enabled());
            }
        });
    }

    public static void handleSetAutoStore(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetAutoStorePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().binding().setAutoStoreMinedDrops(serverPlayer, payload.enabled());
            }
        });
    }

    public static void handleSetBdNetwork(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetBdNetworkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().binding().setBdNetworkEnabled(serverPlayer, payload.enabled());
            }
        });
    }

    public static void handleLinkStorage(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStoragePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().binding().linkStorage(serverPlayer, payload.pos(), payload.linkMode());
            }
        });
    }

    public static void handleUnlinkStorage(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsUnlinkStoragePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().binding().unlinkStorage(serverPlayer, payload.pos());
            }
        });
    }

    public static void handleUpdateLinkedStorage(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsUpdateLinkedStoragePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().binding().updateLinkedStorageSettings(
                        serverPlayer,
                        payload.pos(),
                        payload.linkMode(),
                        payload.priority());
            }
        });
    }

    public static void handleStoreHotbarSlot(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsStoreHotbarSlotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().binding().storeHotbarSlot(serverPlayer, payload.slot());
            }
        });
    }

    public static void handleSetQuickSlot(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetQuickSlotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().binding().setQuickSlot(serverPlayer, payload.slot(), payload.itemId(), payload.previewStack());
            }
        });
    }

    public static void handleSetGuiBinding(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetGuiBindingPayload payload, IPayloadContext context) {
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

    public static void handleOpenGuiBinding(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsOpenGuiBindingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().binding().openGuiBinding(serverPlayer, payload.slot());
            }
        });
    }

    public static void handleFunnelTarget(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsFunnelTargetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().binding().updateFunnelTarget(serverPlayer, payload.target());
            }
        });
    }

    public static void handleCloseRemoteMenu(com.rtsbuilding.rtsbuilding.network.storage.C2SRtsCloseRemoteMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ServiceRegistry.getInstance().binding().closeRemoteMenu(serverPlayer);
            }
        });
    }
}
