package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server-side C2S adapter for linked storage and container-overlay actions.
 *
 * Keep inventory mutation, storage lookup, and compatibility behavior in
 * RtsStorageManager; this layer should only unwrap payloads and enqueue work on
 * the server thread.
 */
public final class RtsStorageNetworkHandlers {
    private RtsStorageNetworkHandlers() {
    }

    public static void handleSetFunnel(C2SRtsSetFunnelPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageManager.setFunnelEnabled(serverPlayer, payload.enabled());
            }
        });
    }

    public static void handleSetAutoStore(C2SRtsSetAutoStorePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageManager.setAutoStoreMinedDrops(serverPlayer, payload.enabled());
            }
        });
    }

    public static void handleSetBdNetwork(C2SRtsSetBdNetworkPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageManager.setBdNetworkEnabled(serverPlayer, payload.enabled());
            }
        });
    }

    public static void handleLinkStorage(C2SRtsLinkStoragePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageManager.linkStorage(serverPlayer, payload.pos(), payload.linkMode());
            }
        });
    }

    public static void handleUnlinkStorage(C2SRtsUnlinkStoragePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageManager.unlinkStorage(serverPlayer, payload.pos());
            }
        });
    }

    public static void handleUpdateLinkedStorage(C2SRtsUpdateLinkedStoragePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageManager.updateLinkedStorageSettings(
                        serverPlayer,
                        payload.pos(),
                        payload.linkMode(),
                        payload.priority());
            }
        });
    }

    public static void handleStoreHotbarSlot(C2SRtsStoreHotbarSlotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageManager.storeHotbarSlotToLinked(serverPlayer, payload.slot());
            }
        });
    }

    public static void handleSetQuickSlot(C2SRtsSetQuickSlotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageManager.setQuickSlot(serverPlayer, payload.slot(), payload.itemId());
            }
        });
    }

    public static void handleSetGuiBinding(C2SRtsSetGuiBindingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageManager.setGuiBinding(
                        serverPlayer,
                        payload.slot(),
                        payload.clear(),
                        payload.pos(),
                        payload.face(),
                        payload.itemIdHint());
            }
        });
    }

    public static void handleOpenGuiBinding(C2SRtsOpenGuiBindingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageManager.openGuiBinding(serverPlayer, payload.slot());
            }
        });
    }

    public static void handleRequestStoragePage(C2SRtsRequestStoragePagePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageManager.requestPage(
                        serverPlayer,
                        payload.page(),
                        payload.search(),
                        payload.category(),
                        RtsStorageSort.byId(payload.sort()),
                        payload.ascending(),
                        payload.pageSize(),
                        payload.pinyinSearchEnabled(),
                        payload.localizedSearchMatches());
            }
        });
    }

    public static void handleFunnelTarget(C2SRtsFunnelTargetPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageManager.updateFunnelTarget(serverPlayer, payload.target());
            }
        });
    }

    public static void handleFillInventory(C2SRtsFillInventoryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageManager.fillPlayerInventoryFromLinked(serverPlayer);
            }
        });
    }

    public static void handleLinkedPickup(C2SRtsLinkedPickupPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageManager.pickupLinkedToCarried(serverPlayer, payload.prototype(), payload.amount());
            }
        });
    }

    public static void handleLinkedQuickMove(C2SRtsLinkedQuickMovePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageManager.quickMoveLinkedItem(serverPlayer, payload.prototype());
            }
        });
    }

    public static void handleReturnCarried(C2SRtsReturnCarriedPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageManager.returnCarriedToLinked(serverPlayer, payload.itemId(), payload.amount());
            }
        });
    }

    public static void handleImportMenuSlot(C2SRtsImportMenuSlotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageManager.importMenuSlotToLinked(serverPlayer, payload.menuSlot());
            }
        });
    }

    public static void handleCloseRemoteMenu(C2SRtsCloseRemoteMenuPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageManager.closeRemoteMenuFromClient(serverPlayer);
            }
        });
    }
}
