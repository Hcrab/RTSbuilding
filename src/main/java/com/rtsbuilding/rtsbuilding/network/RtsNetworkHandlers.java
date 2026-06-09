package com.rtsbuilding.rtsbuilding.network;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.progression.RtsProgressionNodes;
import com.rtsbuilding.rtsbuilding.server.RtsCameraManager;
import com.rtsbuilding.rtsbuilding.server.RtsProgressionManager;
import com.rtsbuilding.rtsbuilding.server.RtsStorageManager;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import com.rtsbuilding.rtsbuilding.forgecompat.network.IPayloadContext;

public final class RtsNetworkHandlers {
    private RtsNetworkHandlers() {
    }

    public static void handleToggle(C2SRtsToggleCameraPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsCameraManager.toggle(serverPlayer, payload.startAtPlayerHead());
            }
        });
    }

    public static void handleMove(C2SRtsCameraMovePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsCameraManager.move(
                        serverPlayer,
                        payload.forward(),
                        payload.strafe(),
                        payload.vertical(),
                        payload.panX(),
                        payload.panY(),
                        payload.rotateX(),
                        payload.rotateY(),
                        payload.scroll(),
                        payload.rotateSteps(),
                        payload.fast());
            }
        });
    }

    public static void handleSetMode(C2SRtsSetModePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                int modeId = payload.mode();
                var modes = BuilderMode.values();
                if (modeId < 0 || modeId >= modes.length) {
                    return;
                }
                RtsStorageManager.setMode(serverPlayer, modes[modeId]);
            }
        });
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

    public static void handleLinkStorage(C2SRtsLinkStoragePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageManager.linkStorage(serverPlayer, payload.pos(), payload.linkMode());
            }
        });
    }

    public static void handleRotateBlock(C2SRtsRotateBlockPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageManager.rotateBlock(serverPlayer, payload.pos());
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

    public static void handleRequestCraftables(C2SRtsRequestCraftablesPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageManager.requestCraftables(
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

    public static void handlePlace(C2SRtsPlacePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                Direction face = Direction.from3DDataValue(payload.face());
                RtsStorageManager.placeSelected(
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
                RtsStorageManager.enqueuePlaceBatch(
                        serverPlayer,
                        payload.clickedPositions(),
                        face,
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
                RtsStorageManager.placeFluid(
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
                RtsStorageManager.storeFluidFromContainer(
                        serverPlayer,
                        payload.sourceType(),
                        payload.toolSlot(),
                        payload.itemId());
            }
        });
    }

    public static void handleInteract(C2SRtsInteractPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                Direction face = Direction.from3DDataValue(payload.face());
                RtsStorageManager.interactTarget(
                        serverPlayer,
                        payload.entityId(),
                        payload.clickedPos(),
                        face,
                        payload.hitX(),
                        payload.hitY(),
                        payload.hitZ(),
                        payload.sourceType(),
                        payload.toolSlot(),
                        payload.itemId(),
                        payload.rayOriginX(),
                        payload.rayOriginY(),
                        payload.rayOriginZ(),
                        payload.rayDirX(),
                        payload.rayDirY(),
                        payload.rayDirZ());
            }
        });
    }

    public static void handleQuickDrop(C2SRtsQuickDropPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageManager.quickDropLinkedItem(
                        serverPlayer,
                        payload.itemId(),
                        payload.amount(),
                        payload.dropX(),
                        payload.dropY(),
                        payload.dropZ());
            }
        });
    }

    public static void handleBreak(C2SRtsBreakPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                Direction face = Direction.from3DDataValue(payload.face());
                RtsStorageManager.breakPlaced(serverPlayer, payload.pos(), face, payload.allowAdjacentFallback());
            }
        });
    }

    public static void handleMine(C2SRtsMinePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                Direction face = Direction.from3DDataValue(payload.face());
                RtsStorageManager.mine(
                        serverPlayer,
                        payload.pos(),
                        face,
                        payload.start(),
                        payload.toolSlot(),
                        payload.toolItemId(),
                        payload.toolPrototype(),
                        payload.allowPlacedBlockRecovery());
            }
        });
    }

    public static void handleUltimine(C2SRtsUltiminePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                Direction face = Direction.from3DDataValue(payload.face());
                RtsStorageManager.startUltimine(
                        serverPlayer,
                        payload.pos(),
                        face,
                        payload.toolSlot(),
                        payload.toolItemId(),
                        payload.toolPrototype(),
                        payload.limit(),
                        payload.mode());
            }
        });
    }

    public static void handleAreaDestroy(C2SRtsAreaDestroyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageManager.areaDestroy(
                        serverPlayer,
                        payload.positions(),
                        payload.toolSlot(),
                        payload.toolItemId(),
                        payload.toolPrototype());
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

    public static void handleOpenCraftTerminal(C2SRtsOpenCraftTerminalPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageManager.openCraftTerminal(serverPlayer);
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

    public static void handleCraftRefill(C2SRtsCraftRefillPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageManager.refillCurrentCraftGridFromBlueprintIds(
                        serverPlayer,
                        payload.blueprintItemIds(),
                        payload.craftedItemId(),
                        payload.craftedCount());
            }
        });
    }

    public static void handleCraftRecipe(C2SRtsCraftRecipePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageManager.craftRecipeToLinked(serverPlayer, payload.recipeId(), payload.craftCount());
            }
        });
    }

    public static void handleJeiTransfer(C2SRtsJeiTransferPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageManager.applyJeiTransfer(
                        serverPlayer,
                        payload.recipeId(),
                        payload.ingredientPrototypes(),
                        payload.maxTransfer(),
                        payload.clearGridFirst());
            }
        });
    }

    public static void handleQuestDetect(C2SRtsQuestDetectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsStorageManager.detectQuests(serverPlayer, payload.mode());
            }
        });
    }

    public static void handleUnlockProgressionNode(C2SRtsUnlockProgressionNodePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsProgressionManager.unlockNode(serverPlayer, payload.nodeId()).notifyPlayer(serverPlayer);
            }
        });
    }

    public static void handleSetSurvivalProgression(C2SRtsSetSurvivalProgressionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer && serverPlayer.hasPermissions(2)) {
                Config.setSurvivalProgressionEnabled(payload.enabled());
                serverPlayer.server.getPlayerList().getPlayers().forEach(RtsProgressionManager::syncToPlayer);
            }
        });
    }

    public static void handleSetProgressionCost(C2SRtsSetProgressionCostPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer
                    && serverPlayer.hasPermissions(2)
                    && RtsProgressionNodes.contains(payload.nodeId())) {
                Config.setProgressionCostOverride(payload.nodeId().getPath(), payload.costsText());
                serverPlayer.server.getPlayerList().getPlayers().forEach(RtsProgressionManager::syncToPlayer);
            }
        });
    }

    public static void handleSetHome(C2SRtsSetHomePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                if (RtsProgressionManager.commitHome(serverPlayer, payload.pos())) {
                    RtsCameraManager.restartNormalFromHomeSelection(serverPlayer);
                }
            }
        });
    }

    public static void handleBeginHomeSelection(C2SRtsBeginHomeSelectionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsCameraManager.startHomeSelectionFromPanel(serverPlayer);
            }
        });
    }

    public static void handleRequestProgressionState(C2SRtsRequestProgressionStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                RtsProgressionManager.syncToPlayer(serverPlayer);
            }
        });
    }

}
