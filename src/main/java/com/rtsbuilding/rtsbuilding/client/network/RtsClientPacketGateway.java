package com.rtsbuilding.rtsbuilding.client.network;


import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsBreakPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsAreaDestroyPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsDeleteWorkflowPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsResumePlacementActionPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsScanResumePlacementPayload;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsBeginHomeSelectionPayload;
import com.rtsbuilding.rtsbuilding.network.camera.C2SRtsCameraMovePayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsCloseRemoteMenuPayload;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsCraftRecipePayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsFillInventoryPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsFunnelTargetPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsInteractPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsLinkStoragePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsMinePayload;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsOpenCraftTerminalPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsOpenGuiBindingPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPlaceBatchPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPlaceFluidPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPlacePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsPauseWorkflowPayload;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsQuestDetectPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsQuickDropPayload;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsRequestProgressionStatePayload;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsRequestCraftablesPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsRequestStoragePagePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsRotateBlockPayload;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsSetHomePayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetAutoStorePayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetFunnelPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetGuiBindingPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsSetModePayload;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsSetProgressionCostPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsSetQuickSlotPayload;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsSetSurvivalProgressionPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsStoreFluidPayload;
import com.rtsbuilding.rtsbuilding.network.storage.C2SRtsStoreHotbarSlotPayload;
import com.rtsbuilding.rtsbuilding.network.camera.C2SRtsToggleCameraPayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsUltiminePayload;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsUndoPayload;
import com.rtsbuilding.rtsbuilding.network.progression.C2SRtsUnlockProgressionNodePayload;
import com.rtsbuilding.rtsbuilding.network.storage.RtsStorageSort;
import com.rtsbuilding.rtsbuilding.util.RtsPinyinSearch;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import com.rtsbuilding.rtsbuilding.forgecompat.network.PacketDistributor;

public final class RtsClientPacketGateway {
    private RtsClientPacketGateway() {
    }

    public static void sendSetMode(BuilderMode mode) {
        PacketDistributor.sendToServer(new C2SRtsSetModePayload((byte) mode.ordinal()));
    }

    public static void sendRequestProgressionState() {
        PacketDistributor.sendToServer(new C2SRtsRequestProgressionStatePayload());
    }

    public static void sendUnlockProgressionNode(net.minecraft.resources.ResourceLocation nodeId) {
        PacketDistributor.sendToServer(new C2SRtsUnlockProgressionNodePayload(nodeId));
    }

    public static void sendSetSurvivalProgression(boolean enabled) {
        PacketDistributor.sendToServer(new C2SRtsSetSurvivalProgressionPayload(enabled));
    }

    public static void sendSetProgressionCost(net.minecraft.resources.ResourceLocation nodeId, String costsText) {
        PacketDistributor.sendToServer(new C2SRtsSetProgressionCostPayload(nodeId, costsText == null ? "" : costsText));
    }

    public static void sendSetHome(BlockPos pos) {
        PacketDistributor.sendToServer(new C2SRtsSetHomePayload(pos));
    }

    public static void sendBeginHomeSelection() {
        PacketDistributor.sendToServer(new C2SRtsBeginHomeSelectionPayload());
    }

    public static void sendToggleCamera(boolean startAtPlayerHead) {
        PacketDistributor.sendToServer(new C2SRtsToggleCameraPayload(startAtPlayerHead));
    }

    public static void sendSetFunnelEnabled(boolean enabled) {
        PacketDistributor.sendToServer(new C2SRtsSetFunnelPayload(enabled));
    }

    public static void sendCameraMove(float forward, float strafe, float vertical, float panX, float panY, float rotateX, float rotateY,
            float scroll, int rotateSteps, boolean fast) {
        PacketDistributor.sendToServer(new C2SRtsCameraMovePayload(
                forward,
                strafe,
                vertical,
                panX,
                panY,
                rotateX,
                rotateY,
                scroll,
                rotateSteps,
                fast));
    }

    public static void sendFunnelTarget(BlockPos target) {
        PacketDistributor.sendToServer(new C2SRtsFunnelTargetPayload(target));
    }

    public static void sendLinkStorage(BlockPos pos, boolean allowStore) {
        PacketDistributor.sendToServer(new C2SRtsLinkStoragePayload(
                pos,
                allowStore ? C2SRtsLinkStoragePayload.MODE_BIDIRECTIONAL : C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY));
    }

    public static void sendRequestStoragePage(int page, String search, String category, RtsStorageSort sort, boolean ascending, int pageSize) {
        boolean pinyinSearchEnabled = isChineseLanguageSelected();
        PacketDistributor.sendToServer(new C2SRtsRequestStoragePagePayload(
                page,
                search,
                category,
                (byte) sort.ordinal(),
                ascending,
                pageSize,
                pinyinSearchEnabled,
                buildLocalizedSearchMatches(search, pinyinSearchEnabled)));
    }

    public static void sendSetAutoStoreMinedDrops(boolean enabled) {
        PacketDistributor.sendToServer(new C2SRtsSetAutoStorePayload(enabled));
    }

    public static void sendCraftRecipe(String recipeId, int craftCount) {
        PacketDistributor.sendToServer(new C2SRtsCraftRecipePayload(recipeId, Math.max(1, craftCount)));
    }

    public static void sendOpenCraftTerminal() {
        PacketDistributor.sendToServer(new C2SRtsOpenCraftTerminalPayload());
    }

    public static void sendCloseRemoteMenu() {
        PacketDistributor.sendToServer(new C2SRtsCloseRemoteMenuPayload());
    }

    public static void sendQuestDetectManual() {
        PacketDistributor.sendToServer(new C2SRtsQuestDetectPayload(C2SRtsQuestDetectPayload.MODE_MANUAL));
    }

    public static void sendPauseWorkflow(int entryId) {
        PacketDistributor.sendToServer(new C2SRtsPauseWorkflowPayload(entryId));
    }

    public static void sendDeleteWorkflow(int workflowEntryId) {
        PacketDistributor.sendToServer(new C2SRtsDeleteWorkflowPayload(workflowEntryId));
    }

    public static void sendScanResumePlacement(int workflowEntryId) {
        PacketDistributor.sendToServer(new C2SRtsScanResumePlacementPayload(workflowEntryId));
    }

    public static void sendResumePlacementAction(int strategy, int workflowEntryId) {
        PacketDistributor.sendToServer(new C2SRtsResumePlacementActionPayload(strategy, workflowEntryId));
    }

    public static void sendRotateBlock(BlockPos pos) {
        PacketDistributor.sendToServer(new C2SRtsRotateBlockPayload(pos));
    }

    public static void sendStoreHotbarSlot(int slot) {
        PacketDistributor.sendToServer(new C2SRtsStoreHotbarSlotPayload((byte) Mth.clamp(slot, 0, 8)));
    }

    public static void sendFillInventory() {
        PacketDistributor.sendToServer(new C2SRtsFillInventoryPayload());
    }

    public static void sendQuickDrop(String itemId, int amount, Vec3 dropPos) {
        PacketDistributor.sendToServer(new C2SRtsQuickDropPayload(
                itemId,
                (byte) Mth.clamp(amount, 1, 64),
                dropPos.x,
                dropPos.y,
                dropPos.z));
    }

    public static void sendRequestCraftables(String search, boolean showUnavailable, int offset, int limit) {
        boolean pinyinSearchEnabled = isChineseLanguageSelected();
        PacketDistributor.sendToServer(new C2SRtsRequestCraftablesPayload(
                search,
                showUnavailable,
                Math.max(0, offset),
                Math.max(1, limit),
                pinyinSearchEnabled,
                buildLocalizedSearchMatches(search, pinyinSearchEnabled)));
    }

    private static boolean isChineseLanguageSelected() {
        Minecraft minecraft = Minecraft.getInstance();
        String language = "";
        if (minecraft != null && minecraft.getLanguageManager() != null) {
            language = minecraft.getLanguageManager().getSelected();
        }
        if ((language == null || language.isBlank()) && minecraft != null && minecraft.options != null) {
            language = minecraft.options.languageCode;
        }
        language = language == null ? "" : language.toLowerCase(Locale.ROOT);
        return language.equals("zh") || language.startsWith("zh_") || language.startsWith("zh-");
    }

    private static List<String> buildLocalizedSearchMatches(String search, boolean pinyinSearchEnabled) {
        if (!pinyinSearchEnabled) {
            return List.of();
        }
        String query = search == null ? "" : search.toLowerCase(Locale.ROOT).trim();
        if (query.isEmpty()) {
            return List.of();
        }

        String[] tokens = query.split("\\s+");
        List<String> matches = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == null) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            if (stack.isEmpty()) {
                continue;
            }
            String label = stack.getHoverName().getString();
            if (matchesLocalizedSearch(id, label, tokens)) {
                matches.add(id.toString());
            }
        }
        return matches;
    }

    private static boolean matchesLocalizedSearch(ResourceLocation id, String label, String[] tokens) {
        String rawId = id.toString().toLowerCase(Locale.ROOT);
        String namespace = id.getNamespace().toLowerCase(Locale.ROOT);
        String normalizedLabel = label == null ? "" : label.toLowerCase(Locale.ROOT);
        boolean matchedAnyToken = false;
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                continue;
            }
            matchedAnyToken = true;
            if (token.startsWith("@")) {
                String modQuery = token.substring(1).trim();
                if (!modQuery.isEmpty() && !namespace.contains(modQuery)) {
                    return false;
                }
                continue;
            }
            if (!rawId.contains(token)
                    && !normalizedLabel.contains(token)
                    && !RtsPinyinSearch.contains(label, token)) {
                return false;
            }
        }
        return matchedAnyToken;
    }

    public static void sendSetQuickSlot(int index, String itemId, ItemStack previewStack) {
        PacketDistributor.sendToServer(new C2SRtsSetQuickSlotPayload(
                (byte) index,
                itemId,
                previewStack == null ? ItemStack.EMPTY : previewStack.copyWithCount(1)));
    }

    public static void sendSetGuiBinding(int index, BlockPos pos, Direction face, String itemIdHint) {
        PacketDistributor.sendToServer(new C2SRtsSetGuiBindingPayload(
                (byte) index,
                false,
                pos,
                (byte) (face == null ? -1 : face.get3DDataValue()),
                itemIdHint == null ? "" : itemIdHint));
    }

    public static void sendClearGuiBinding(int index) {
        PacketDistributor.sendToServer(new C2SRtsSetGuiBindingPayload((byte) index, true, BlockPos.ZERO, (byte) -1, ""));
    }

    public static void sendOpenGuiBinding(int index) {
        PacketDistributor.sendToServer(new C2SRtsOpenGuiBindingPayload((byte) index));
    }

    public static void sendPlace(BlockHitResult hit, boolean forcePlace, boolean skipIfOccupied, String itemId,
            ItemStack itemPrototype, int rotateSteps, Vec3 rayOrigin, Vec3 rayDir) {
        sendPlace(hit, forcePlace, skipIfOccupied, itemId, itemPrototype, rotateSteps, rayOrigin, rayDir, false);
    }

    public static void sendEmptyHandPlace(BlockHitResult hit, Vec3 rayOrigin, Vec3 rayDir) {
        sendPlace(hit, false, false, "", ItemStack.EMPTY, 0, rayOrigin, rayDir, false, true);
    }

    public static void sendPlace(BlockHitResult hit, boolean forcePlace, boolean skipIfOccupied, String itemId,
            ItemStack itemPrototype, int rotateSteps, Vec3 rayOrigin, Vec3 rayDir, boolean quickBuild) {
        sendPlace(hit, forcePlace, skipIfOccupied, itemId, itemPrototype, rotateSteps, rayOrigin, rayDir, quickBuild, false);
    }

    private static void sendPlace(BlockHitResult hit, boolean forcePlace, boolean skipIfOccupied, String itemId,
            ItemStack itemPrototype, int rotateSteps, Vec3 rayOrigin, Vec3 rayDir, boolean quickBuild,
            boolean forceEmptyHand) {
        ItemStack prototype = itemPrototype == null ? ItemStack.EMPTY : itemPrototype.copy();
        if (!prototype.isEmpty()) {
            prototype.setCount(1);
        }
        PacketDistributor.sendToServer(new C2SRtsPlacePayload(
                hit.getBlockPos(),
                (byte) hit.getDirection().get3DDataValue(),
                hit.getLocation().x,
                hit.getLocation().y,
                hit.getLocation().z,
                (byte) rotateSteps,
                forcePlace,
                skipIfOccupied,
                itemId,
                prototype,
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z,
                quickBuild,
                forceEmptyHand));
    }

    public static void sendPlaceBatch(List<BlockHitResult> hits, boolean forcePlace, boolean skipIfOccupied, String itemId,
            ItemStack itemPrototype, int rotateSteps, Vec3 rayOrigin, Vec3 rayDir) {
        if (hits == null || hits.isEmpty()) {
            return;
        }
        Direction face = hits.get(0).getDirection();
        List<BlockPos> positions = new ArrayList<>(Math.min(hits.size(), C2SRtsPlaceBatchPayload.MAX_POSITIONS));
        for (BlockHitResult hit : hits) {
            if (hit == null || hit.getDirection() != face) {
                continue;
            }
            positions.add(hit.getBlockPos().immutable());
            if (positions.size() >= C2SRtsPlaceBatchPayload.MAX_POSITIONS) {
                break;
            }
        }
        if (positions.isEmpty()) {
            return;
        }
        ItemStack prototype = itemPrototype == null ? ItemStack.EMPTY : itemPrototype.copy();
        if (!prototype.isEmpty()) {
            prototype.setCount(1);
        }
        BlockHitResult placementTemplate = hits.get(0);
        double hitOffsetX = placementTemplate.getLocation().x - placementTemplate.getBlockPos().getX();
        double hitOffsetY = placementTemplate.getLocation().y - placementTemplate.getBlockPos().getY();
        double hitOffsetZ = placementTemplate.getLocation().z - placementTemplate.getBlockPos().getZ();
        PacketDistributor.sendToServer(new C2SRtsPlaceBatchPayload(
                positions,
                (byte) face.get3DDataValue(),
                hitOffsetX,
                hitOffsetY,
                hitOffsetZ,
                (byte) rotateSteps,
                forcePlace,
                skipIfOccupied,
                itemId == null ? "" : itemId,
                prototype,
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z));
    }

    public static void sendPlaceFluid(BlockHitResult hit, boolean forcePlace, String fluidId, Vec3 rayOrigin, Vec3 rayDir) {
        PacketDistributor.sendToServer(new C2SRtsPlaceFluidPayload(
                hit.getBlockPos(),
                (byte) hit.getDirection().get3DDataValue(),
                hit.getLocation().x,
                hit.getLocation().y,
                hit.getLocation().z,
                forcePlace,
                fluidId,
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z));
    }

    public static void sendStoreFluid(byte sourceType, int toolSlot, String itemId) {
        PacketDistributor.sendToServer(new C2SRtsStoreFluidPayload(
                sourceType,
                (byte) Mth.clamp(toolSlot, 0, 8),
                itemId == null ? "" : itemId));
    }

    public static void sendInteractBlockWithToolSlot(BlockHitResult hit, int toolSlot, Vec3 rayOrigin, Vec3 rayDir) {
        PacketDistributor.sendToServer(new C2SRtsInteractPayload(
                C2SRtsInteractPayload.NO_ENTITY,
                hit.getBlockPos(),
                (byte) hit.getDirection().get3DDataValue(),
                hit.getLocation().x,
                hit.getLocation().y,
                hit.getLocation().z,
                C2SRtsInteractPayload.SOURCE_TOOL_SLOT,
                (byte) Mth.clamp(toolSlot, 0, 8),
                "",
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z));
    }

    public static void sendUseItemInAirWithToolSlot(BlockHitResult hit, int toolSlot, Vec3 rayOrigin, Vec3 rayDir) {
        PacketDistributor.sendToServer(new C2SRtsInteractPayload(
                C2SRtsInteractPayload.NO_ENTITY,
                hit.getBlockPos(),
                (byte) hit.getDirection().get3DDataValue(),
                hit.getLocation().x,
                hit.getLocation().y,
                hit.getLocation().z,
                C2SRtsInteractPayload.SOURCE_TOOL_SLOT_AIR,
                (byte) Mth.clamp(toolSlot, 0, 8),
                "",
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z));
    }

    public static void sendInteractBlockWithPinnedItem(BlockHitResult hit, String itemId, Vec3 rayOrigin, Vec3 rayDir) {
        PacketDistributor.sendToServer(new C2SRtsInteractPayload(
                C2SRtsInteractPayload.NO_ENTITY,
                hit.getBlockPos(),
                (byte) hit.getDirection().get3DDataValue(),
                hit.getLocation().x,
                hit.getLocation().y,
                hit.getLocation().z,
                C2SRtsInteractPayload.SOURCE_PIN_ITEM,
                (byte) 0,
                itemId,
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z));
    }

    public static void sendInteractEntityWithToolSlot(int entityId, Vec3 hitLocation, int toolSlot, Vec3 rayOrigin, Vec3 rayDir) {
        PacketDistributor.sendToServer(new C2SRtsInteractPayload(
                entityId,
                BlockPos.containing(hitLocation),
                (byte) 1,
                hitLocation.x,
                hitLocation.y,
                hitLocation.z,
                C2SRtsInteractPayload.SOURCE_TOOL_SLOT,
                (byte) Mth.clamp(toolSlot, 0, 8),
                "",
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z));
    }

    public static void sendInteractEntityEmptyHand(int entityId, Vec3 hitLocation, Vec3 rayOrigin, Vec3 rayDir) {
        PacketDistributor.sendToServer(new C2SRtsInteractPayload(
                entityId,
                BlockPos.containing(hitLocation),
                (byte) 1,
                hitLocation.x,
                hitLocation.y,
                hitLocation.z,
                C2SRtsInteractPayload.SOURCE_EMPTY_HAND,
                (byte) 0,
                "",
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z));
    }

    public static void sendInteractEntityWithPinnedItem(int entityId, Vec3 hitLocation, String itemId, Vec3 rayOrigin, Vec3 rayDir) {
        PacketDistributor.sendToServer(new C2SRtsInteractPayload(
                entityId,
                BlockPos.containing(hitLocation),
                (byte) 1,
                hitLocation.x,
                hitLocation.y,
                hitLocation.z,
                C2SRtsInteractPayload.SOURCE_PIN_ITEM,
                (byte) 0,
                itemId,
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z));
    }

    public static void sendBreakPlaced(BlockPos pos, Direction face, boolean allowAdjacentFallback) {
        PacketDistributor.sendToServer(new C2SRtsBreakPayload(
                pos,
                (byte) face.get3DDataValue(),
                allowAdjacentFallback));
    }

    public static void sendMineStart(BlockPos pos, int face, int toolSlot, String toolItemId, ItemStack toolPrototype,
            boolean allowPlacedBlockRecovery, boolean toolProtectionEnabled) {
        PacketDistributor.sendToServer(new C2SRtsMinePayload(
                pos,
                (byte) face,
                true,
                (byte) Mth.clamp(toolSlot, 0, 8),
                toolItemId == null ? "" : toolItemId,
                toolPrototype == null ? ItemStack.EMPTY : toolPrototype,
                allowPlacedBlockRecovery,
                toolProtectionEnabled));
    }

    public static void sendAreaDestroy(List<BlockPos> positions, int toolSlot, String toolItemId, ItemStack toolPrototype,
            boolean toolProtectionEnabled) {
        if (positions == null || positions.isEmpty()) {
            return;
        }
        PacketDistributor.sendToServer(new C2SRtsAreaDestroyPayload(
                positions,
                (byte) Mth.clamp(toolSlot, 0, 8),
                toolItemId == null ? "" : toolItemId,
                toolPrototype == null ? ItemStack.EMPTY : toolPrototype,
                toolProtectionEnabled));
    }

    public static void sendUltimineStart(BlockPos pos, int face, int toolSlot, String toolItemId, ItemStack toolPrototype,
            int limit, byte mode, boolean toolProtectionEnabled) {
        PacketDistributor.sendToServer(new C2SRtsUltiminePayload(
                pos,
                (byte) face,
                (byte) Mth.clamp(toolSlot, 0, 8),
                toolItemId == null ? "" : toolItemId,
                toolPrototype == null ? ItemStack.EMPTY : toolPrototype,
                (short) Mth.clamp(limit, 1, 256),
                mode,
                toolProtectionEnabled));
    }

    public static void sendMineAbort(BlockPos pos, int face, int toolSlot) {
        PacketDistributor.sendToServer(new C2SRtsMinePayload(
                pos,
                (byte) face,
                false,
                (byte) Mth.clamp(toolSlot, 0, 8),
                "",
                ItemStack.EMPTY,
                false,
                false));
    }

    public static void sendUndo() {
        PacketDistributor.sendToServer(new C2SRtsUndoPayload());
    }
}
