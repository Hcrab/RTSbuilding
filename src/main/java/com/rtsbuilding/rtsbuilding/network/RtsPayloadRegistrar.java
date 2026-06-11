package com.rtsbuilding.rtsbuilding.network;


import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsAreaDestroyPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsBreakPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsInteractPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsMinePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPlaceBatchPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPlaceFluidPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPlacePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsQuickDropPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsRotateBlockPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsSetModePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsStoreFluidPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsUltiminePayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsBreakAnimationPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsMineProgressPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsPlaceAnimationPayload;
import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsUltimineProgressPayload;
import com.rtsbuilding.rtsbuilding.network.camera.C2SRtsCameraMovePayload;
import com.rtsbuilding.rtsbuilding.network.camera.C2SRtsToggleCameraPayload;
import com.rtsbuilding.rtsbuilding.network.camera.S2CRtsCameraStatePayload;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsCraftRecipePayload;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsCraftRefillPayload;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsJeiTransferPayload;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsOpenCraftTerminalPayload;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsRequestCraftablesPayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftablesPayload;
import com.rtsbuilding.rtsbuilding.network.craft.S2CRtsCraftFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.feedback.S2CRtsDamageFeedbackPayload;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsBeginHomeSelectionPayload;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsQuestDetectPayload;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsRequestProgressionStatePayload;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsSetHomePayload;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsSetProgressionCostPayload;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsSetSurvivalProgressionPayload;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsUnlockProgressionNodePayload;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsProgressionStatePayload;
import com.rtsbuilding.rtsbuilding.network.progression.S2CRtsQuestDetectStatusPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsCloseRemoteMenuPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsFillInventoryPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsFunnelTargetPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsImportMenuSlotPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkedPickupPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkedQuickMovePayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStoragePayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsOpenGuiBindingPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsRequestStoragePagePayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsReturnCarriedPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetAutoStorePayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetFunnelPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetGuiBindingPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetQuickSlotPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsStoreHotbarSlotPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsRemoteMenuHintPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStorageDirtyPayload;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.blueprint.network.BlueprintClientPayloadBridge;
import com.rtsbuilding.rtsbuilding.blueprint.network.BlueprintNetworkHandlers;
import com.rtsbuilding.rtsbuilding.blueprint.network.C2SBlueprintPlacePayload;
import com.rtsbuilding.rtsbuilding.blueprint.network.S2CBlueprintStatusPayload;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import com.rtsbuilding.rtsbuilding.forgecompat.network.IPayloadContext;

public final class RtsPayloadRegistrar {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RtsbuildingMod.MODID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static boolean registered;

    private RtsPayloadRegistrar() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        int id = 0;
        registerMessage(id++, C2SRtsToggleCameraPayload.class, C2SRtsToggleCameraPayload.STREAM_CODEC, RtsNetworkHandlers::handleToggle);
        registerMessage(id++, C2SRtsCameraMovePayload.class, C2SRtsCameraMovePayload.STREAM_CODEC, RtsNetworkHandlers::handleMove);
        registerMessage(id++, C2SRtsSetModePayload.class, C2SRtsSetModePayload.STREAM_CODEC, RtsNetworkHandlers::handleSetMode);
        registerMessage(id++, C2SRtsSetFunnelPayload.class, C2SRtsSetFunnelPayload.STREAM_CODEC, RtsNetworkHandlers::handleSetFunnel);
        registerMessage(id++, C2SRtsSetAutoStorePayload.class, C2SRtsSetAutoStorePayload.STREAM_CODEC, RtsNetworkHandlers::handleSetAutoStore);
        registerMessage(id++, C2SRtsLinkStoragePayload.class, C2SRtsLinkStoragePayload.STREAM_CODEC, RtsNetworkHandlers::handleLinkStorage);
        registerMessage(id++, C2SRtsRotateBlockPayload.class, C2SRtsRotateBlockPayload.STREAM_CODEC, RtsNetworkHandlers::handleRotateBlock);
        registerMessage(id++, C2SRtsStoreHotbarSlotPayload.class, C2SRtsStoreHotbarSlotPayload.STREAM_CODEC, RtsNetworkHandlers::handleStoreHotbarSlot);
        registerMessage(id++, C2SRtsSetQuickSlotPayload.class, C2SRtsSetQuickSlotPayload.STREAM_CODEC, RtsNetworkHandlers::handleSetQuickSlot);
        registerMessage(id++, C2SRtsSetGuiBindingPayload.class, C2SRtsSetGuiBindingPayload.STREAM_CODEC, RtsNetworkHandlers::handleSetGuiBinding);
        registerMessage(id++, C2SRtsOpenGuiBindingPayload.class, C2SRtsOpenGuiBindingPayload.STREAM_CODEC, RtsNetworkHandlers::handleOpenGuiBinding);
        registerMessage(id++, C2SRtsRequestStoragePagePayload.class, C2SRtsRequestStoragePagePayload.STREAM_CODEC, RtsNetworkHandlers::handleRequestStoragePage);
        registerMessage(id++, C2SRtsRequestCraftablesPayload.class, C2SRtsRequestCraftablesPayload.STREAM_CODEC, RtsNetworkHandlers::handleRequestCraftables);
        registerMessage(id++, C2SRtsPlacePayload.class, C2SRtsPlacePayload.STREAM_CODEC, RtsNetworkHandlers::handlePlace);
        registerMessage(id++, C2SRtsPlaceBatchPayload.class, C2SRtsPlaceBatchPayload.STREAM_CODEC, RtsNetworkHandlers::handlePlaceBatch);
        registerMessage(id++, C2SRtsPlaceFluidPayload.class, C2SRtsPlaceFluidPayload.STREAM_CODEC, RtsNetworkHandlers::handlePlaceFluid);
        registerMessage(id++, C2SRtsStoreFluidPayload.class, C2SRtsStoreFluidPayload.STREAM_CODEC, RtsNetworkHandlers::handleStoreFluid);
        registerMessage(id++, C2SRtsInteractPayload.class, C2SRtsInteractPayload.STREAM_CODEC, RtsNetworkHandlers::handleInteract);
        registerMessage(id++, C2SRtsQuickDropPayload.class, C2SRtsQuickDropPayload.STREAM_CODEC, RtsNetworkHandlers::handleQuickDrop);
        registerMessage(id++, C2SRtsBreakPayload.class, C2SRtsBreakPayload.STREAM_CODEC, RtsNetworkHandlers::handleBreak);
        registerMessage(id++, C2SRtsMinePayload.class, C2SRtsMinePayload.STREAM_CODEC, RtsNetworkHandlers::handleMine);
        registerMessage(id++, C2SRtsUltiminePayload.class, C2SRtsUltiminePayload.STREAM_CODEC, RtsNetworkHandlers::handleUltimine);
        registerMessage(id++, C2SRtsAreaDestroyPayload.class, C2SRtsAreaDestroyPayload.STREAM_CODEC, RtsNetworkHandlers::handleAreaDestroy);
        registerMessage(id++, C2SRtsFunnelTargetPayload.class, C2SRtsFunnelTargetPayload.STREAM_CODEC, RtsNetworkHandlers::handleFunnelTarget);
        registerMessage(id++, C2SRtsFillInventoryPayload.class, C2SRtsFillInventoryPayload.STREAM_CODEC, RtsNetworkHandlers::handleFillInventory);
        registerMessage(id++, C2SRtsLinkedPickupPayload.class, C2SRtsLinkedPickupPayload.STREAM_CODEC, RtsNetworkHandlers::handleLinkedPickup);
        registerMessage(id++, C2SRtsLinkedQuickMovePayload.class, C2SRtsLinkedQuickMovePayload.STREAM_CODEC, RtsNetworkHandlers::handleLinkedQuickMove);
        registerMessage(id++, C2SRtsReturnCarriedPayload.class, C2SRtsReturnCarriedPayload.STREAM_CODEC, RtsNetworkHandlers::handleReturnCarried);
        registerMessage(id++, C2SRtsOpenCraftTerminalPayload.class, C2SRtsOpenCraftTerminalPayload.STREAM_CODEC, RtsNetworkHandlers::handleOpenCraftTerminal);
        registerMessage(id++, C2SRtsImportMenuSlotPayload.class, C2SRtsImportMenuSlotPayload.STREAM_CODEC, RtsNetworkHandlers::handleImportMenuSlot);
        registerMessage(id++, C2SRtsCloseRemoteMenuPayload.class, C2SRtsCloseRemoteMenuPayload.STREAM_CODEC, RtsNetworkHandlers::handleCloseRemoteMenu);
        registerMessage(id++, C2SRtsCraftRefillPayload.class, C2SRtsCraftRefillPayload.STREAM_CODEC, RtsNetworkHandlers::handleCraftRefill);
        registerMessage(id++, C2SRtsCraftRecipePayload.class, C2SRtsCraftRecipePayload.STREAM_CODEC, RtsNetworkHandlers::handleCraftRecipe);
        registerMessage(id++, C2SRtsJeiTransferPayload.class, C2SRtsJeiTransferPayload.STREAM_CODEC, RtsNetworkHandlers::handleJeiTransfer);
        registerMessage(id++, C2SRtsQuestDetectPayload.class, C2SRtsQuestDetectPayload.STREAM_CODEC, RtsNetworkHandlers::handleQuestDetect);
        registerMessage(id++, C2SRtsUnlockProgressionNodePayload.class, C2SRtsUnlockProgressionNodePayload.STREAM_CODEC, RtsNetworkHandlers::handleUnlockProgressionNode);
        registerMessage(id++, C2SRtsSetSurvivalProgressionPayload.class, C2SRtsSetSurvivalProgressionPayload.STREAM_CODEC, RtsNetworkHandlers::handleSetSurvivalProgression);
        registerMessage(id++, C2SRtsSetProgressionCostPayload.class, C2SRtsSetProgressionCostPayload.STREAM_CODEC, RtsNetworkHandlers::handleSetProgressionCost);
        registerMessage(id++, C2SRtsSetHomePayload.class, C2SRtsSetHomePayload.STREAM_CODEC, RtsNetworkHandlers::handleSetHome);
        registerMessage(id++, C2SRtsBeginHomeSelectionPayload.class, C2SRtsBeginHomeSelectionPayload.STREAM_CODEC, RtsNetworkHandlers::handleBeginHomeSelection);
        registerMessage(id++, C2SRtsRequestProgressionStatePayload.class, C2SRtsRequestProgressionStatePayload.STREAM_CODEC, RtsNetworkHandlers::handleRequestProgressionState);
        registerMessage(id++, C2SBlueprintPlacePayload.class, C2SBlueprintPlacePayload.STREAM_CODEC, BlueprintNetworkHandlers::handlePlace);

        registerMessage(id++, S2CRtsCameraStatePayload.class, S2CRtsCameraStatePayload.STREAM_CODEC, RtsClientPayloadBridge::handleCameraState);
        registerMessage(id++, S2CRtsStoragePagePayload.class, S2CRtsStoragePagePayload.STREAM_CODEC, RtsClientPayloadBridge::handleStoragePage);
        registerMessage(id++, S2CRtsStorageDirtyPayload.class, S2CRtsStorageDirtyPayload.STREAM_CODEC, RtsClientPayloadBridge::handleStorageDirty);
        registerMessage(id++, S2CRtsRemoteMenuHintPayload.class, S2CRtsRemoteMenuHintPayload.STREAM_CODEC, RtsClientPayloadBridge::handleRemoteMenuHint);
        registerMessage(id++, S2CRtsCraftablesPayload.class, S2CRtsCraftablesPayload.STREAM_CODEC, RtsClientPayloadBridge::handleCraftables);
        registerMessage(id++, S2CRtsCraftFeedbackPayload.class, S2CRtsCraftFeedbackPayload.STREAM_CODEC, RtsClientPayloadBridge::handleCraftFeedback);
        registerMessage(id++, S2CRtsDamageFeedbackPayload.class, S2CRtsDamageFeedbackPayload.STREAM_CODEC, RtsClientPayloadBridge::handleDamageFeedback);
        registerMessage(id++, S2CRtsQuestDetectStatusPayload.class, S2CRtsQuestDetectStatusPayload.STREAM_CODEC, RtsClientPayloadBridge::handleQuestDetectStatus);
        registerMessage(id++, S2CRtsMineProgressPayload.class, S2CRtsMineProgressPayload.STREAM_CODEC, RtsClientPayloadBridge::handleMineProgress);
        registerMessage(id++, S2CRtsPlaceAnimationPayload.class, S2CRtsPlaceAnimationPayload.STREAM_CODEC, RtsClientPayloadBridge::handlePlaceAnimation);
        registerMessage(id++, S2CRtsBreakAnimationPayload.class, S2CRtsBreakAnimationPayload.STREAM_CODEC, RtsClientPayloadBridge::handleBreakAnimation);
        registerMessage(id++, S2CRtsUltimineProgressPayload.class, S2CRtsUltimineProgressPayload.STREAM_CODEC, RtsClientPayloadBridge::handleUltimineProgress);
        registerMessage(id++, S2CRtsProgressionStatePayload.class, S2CRtsProgressionStatePayload.STREAM_CODEC, RtsClientPayloadBridge::handleProgressionState);
        registerMessage(id++, S2CBlueprintStatusPayload.class, S2CBlueprintStatusPayload.STREAM_CODEC, BlueprintClientPayloadBridge::handleStatus);
    }

    public static void sendToPlayer(final ServerPlayer player, final Object message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    private static <T extends CustomPacketPayload> void registerMessage(
            final int id,
            final Class<T> messageType,
            final com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec<RegistryFriendlyByteBuf, T> codec,
            final BiConsumer<T, IPayloadContext> handler) {
        CHANNEL.registerMessage(
                id,
                messageType,
                (message, buffer) -> codec.encode(new RegistryFriendlyByteBuf(buffer), message),
                buffer -> codec.decode(new RegistryFriendlyByteBuf(buffer)),
                (message, contextSupplier) -> {
                    handler.accept(message, new PayloadContextAdapter(contextSupplier.get()));
                    contextSupplier.get().setPacketHandled(true);
                });
    }

    private record PayloadContextAdapter(NetworkEvent.Context context) implements IPayloadContext {
        @Override
        public net.minecraft.world.entity.player.Player player() {
            return this.context.getSender();
        }

        @Override
        public void enqueueWork(final Runnable runnable) {
            this.context.enqueueWork(runnable);
        }
    }
}
