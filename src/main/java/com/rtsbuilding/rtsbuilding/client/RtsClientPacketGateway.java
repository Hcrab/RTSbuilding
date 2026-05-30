package com.rtsbuilding.rtsbuilding.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.rtsbuilding.rtsbuilding.common.BuilderMode;
import com.rtsbuilding.rtsbuilding.network.C2SRtsBreakPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsBeginHomeSelectionPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsCameraMovePayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsCraftRecipePayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsFillInventoryPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsFunnelTargetPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsInteractPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsLinkStoragePayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsMinePayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsOpenCraftTerminalPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsOpenGuiBindingPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsPlaceBatchPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsPlaceFluidPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsPlacePayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsQuestDetectPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsQuickDropPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsRequestProgressionStatePayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsRequestCraftablesPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsRequestStoragePagePayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsRotateBlockPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsSetHomePayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsSetAutoStorePayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsSetFunnelPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsSetGuiBindingPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsSetModePayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsSetProgressionCostPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsSetQuickSlotPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsSetSurvivalProgressionPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsStoreFluidPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsStoreHotbarSlotPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsToggleCameraPayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsUltiminePayload;
import com.rtsbuilding.rtsbuilding.network.C2SRtsUnlockProgressionNodePayload;
import com.rtsbuilding.rtsbuilding.network.RtsStorageSort;
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

final class RtsClientPacketGateway {
    private RtsClientPacketGateway() {
    }

    static void sendSetMode(BuilderMode mode) {
        PacketDistributor.sendToServer(new C2SRtsSetModePayload((byte) mode.ordinal()));
    }

    static void sendRequestProgressionState() {
        PacketDistributor.sendToServer(new C2SRtsRequestProgressionStatePayload());
    }

    static void sendUnlockProgressionNode(net.minecraft.resources.ResourceLocation nodeId) {
        PacketDistributor.sendToServer(new C2SRtsUnlockProgressionNodePayload(nodeId));
    }

    static void sendSetSurvivalProgression(boolean enabled) {
        PacketDistributor.sendToServer(new C2SRtsSetSurvivalProgressionPayload(enabled));
    }

    static void sendSetProgressionCost(net.minecraft.resources.ResourceLocation nodeId, String costsText) {
        PacketDistributor.sendToServer(new C2SRtsSetProgressionCostPayload(nodeId, costsText == null ? "" : costsText));
    }

    static void sendSetHome(BlockPos pos) {
        PacketDistributor.sendToServer(new C2SRtsSetHomePayload(pos));
    }

    static void sendBeginHomeSelection() {
        PacketDistributor.sendToServer(new C2SRtsBeginHomeSelectionPayload());
    }

    static void sendToggleCamera(boolean startAtPlayerHead) {
        PacketDistributor.sendToServer(new C2SRtsToggleCameraPayload(startAtPlayerHead));
    }

    static void sendSetFunnelEnabled(boolean enabled) {
        PacketDistributor.sendToServer(new C2SRtsSetFunnelPayload(enabled));
    }

    static void sendCameraMove(float forward, float strafe, float vertical, float panX, float panY, float rotateX, float rotateY,
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

    static void sendFunnelTarget(BlockPos target) {
        PacketDistributor.sendToServer(new C2SRtsFunnelTargetPayload(target));
    }

    static void sendLinkStorage(BlockPos pos, boolean allowStore) {
        PacketDistributor.sendToServer(new C2SRtsLinkStoragePayload(
                pos,
                allowStore ? C2SRtsLinkStoragePayload.MODE_BIDIRECTIONAL : C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY));
    }

    static void sendRequestStoragePage(int page, String search, String category, RtsStorageSort sort, boolean ascending) {
        boolean pinyinSearchEnabled = isChineseLanguageSelected();
        PacketDistributor.sendToServer(new C2SRtsRequestStoragePagePayload(
                page,
                search,
                category,
                (byte) sort.ordinal(),
                ascending,
                pinyinSearchEnabled,
                buildLocalizedSearchMatches(search, pinyinSearchEnabled)));
    }

    static void sendSetAutoStoreMinedDrops(boolean enabled) {
        PacketDistributor.sendToServer(new C2SRtsSetAutoStorePayload(enabled));
    }

    static void sendCraftRecipe(String recipeId, int craftCount) {
        PacketDistributor.sendToServer(new C2SRtsCraftRecipePayload(recipeId, Math.max(1, craftCount)));
    }

    static void sendOpenCraftTerminal() {
        PacketDistributor.sendToServer(new C2SRtsOpenCraftTerminalPayload());
    }

    static void sendQuestDetectManual() {
        PacketDistributor.sendToServer(new C2SRtsQuestDetectPayload(C2SRtsQuestDetectPayload.MODE_MANUAL));
    }

    static void sendRotateBlock(BlockPos pos) {
        PacketDistributor.sendToServer(new C2SRtsRotateBlockPayload(pos));
    }

    static void sendStoreHotbarSlot(int slot) {
        PacketDistributor.sendToServer(new C2SRtsStoreHotbarSlotPayload((byte) Mth.clamp(slot, 0, 8)));
    }

    static void sendFillInventory() {
        PacketDistributor.sendToServer(new C2SRtsFillInventoryPayload());
    }

    static void sendQuickDrop(String itemId, int amount, Vec3 dropPos) {
        PacketDistributor.sendToServer(new C2SRtsQuickDropPayload(
                itemId,
                (byte) Mth.clamp(amount, 1, 64),
                dropPos.x,
                dropPos.y,
                dropPos.z));
    }

    static void sendRequestCraftables(String search, boolean showUnavailable, int offset, int limit) {
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

    static void sendSetQuickSlot(int index, String itemId) {
        PacketDistributor.sendToServer(new C2SRtsSetQuickSlotPayload((byte) index, itemId));
    }

    static void sendSetGuiBinding(int index, BlockPos pos, Direction face, String itemIdHint) {
        PacketDistributor.sendToServer(new C2SRtsSetGuiBindingPayload(
                (byte) index,
                false,
                pos,
                (byte) (face == null ? -1 : face.get3DDataValue()),
                itemIdHint == null ? "" : itemIdHint));
    }

    static void sendClearGuiBinding(int index) {
        PacketDistributor.sendToServer(new C2SRtsSetGuiBindingPayload((byte) index, true, BlockPos.ZERO, (byte) -1, ""));
    }

    static void sendOpenGuiBinding(int index) {
        PacketDistributor.sendToServer(new C2SRtsOpenGuiBindingPayload((byte) index));
    }

    static void sendPlace(BlockHitResult hit, boolean forcePlace, boolean skipIfOccupied, String itemId, int rotateSteps,
            Vec3 rayOrigin, Vec3 rayDir) {
        sendPlace(hit, forcePlace, skipIfOccupied, itemId, rotateSteps, rayOrigin, rayDir, false);
    }

    static void sendPlace(BlockHitResult hit, boolean forcePlace, boolean skipIfOccupied, String itemId, int rotateSteps,
            Vec3 rayOrigin, Vec3 rayDir, boolean quickBuild) {
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
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z,
                quickBuild));
    }

    static void sendPlaceBatch(List<BlockHitResult> hits, boolean forcePlace, boolean skipIfOccupied, String itemId,
            int rotateSteps, Vec3 rayOrigin, Vec3 rayDir) {
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
        PacketDistributor.sendToServer(new C2SRtsPlaceBatchPayload(
                positions,
                (byte) face.get3DDataValue(),
                (byte) rotateSteps,
                forcePlace,
                skipIfOccupied,
                itemId == null ? "" : itemId,
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z));
    }

    static void sendPlaceFluid(BlockHitResult hit, boolean forcePlace, String fluidId, Vec3 rayOrigin, Vec3 rayDir) {
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

    static void sendStoreFluid(byte sourceType, int toolSlot, String itemId) {
        PacketDistributor.sendToServer(new C2SRtsStoreFluidPayload(
                sourceType,
                (byte) Mth.clamp(toolSlot, 0, 8),
                itemId == null ? "" : itemId));
    }

    static void sendInteractBlockWithToolSlot(BlockHitResult hit, int toolSlot, Vec3 rayOrigin, Vec3 rayDir) {
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

    static void sendInteractBlockWithPinnedItem(BlockHitResult hit, String itemId, Vec3 rayOrigin, Vec3 rayDir) {
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

    static void sendInteractEntityWithToolSlot(int entityId, Vec3 hitLocation, int toolSlot, Vec3 rayOrigin, Vec3 rayDir) {
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

    static void sendInteractEntityWithPinnedItem(int entityId, Vec3 hitLocation, String itemId, Vec3 rayOrigin, Vec3 rayDir) {
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

    static void sendBreakPlaced(BlockPos pos, Direction face, boolean allowAdjacentFallback) {
        PacketDistributor.sendToServer(new C2SRtsBreakPayload(
                pos,
                (byte) face.get3DDataValue(),
                allowAdjacentFallback));
    }

    static void sendMineStart(BlockPos pos, int face, int toolSlot, String toolItemId, ItemStack toolPrototype,
            boolean allowPlacedBlockRecovery) {
        PacketDistributor.sendToServer(new C2SRtsMinePayload(
                pos,
                (byte) face,
                true,
                (byte) Mth.clamp(toolSlot, 0, 8),
                toolItemId == null ? "" : toolItemId,
                toolPrototype == null ? ItemStack.EMPTY : toolPrototype,
                allowPlacedBlockRecovery));
    }

    static void sendUltimineStart(BlockPos pos, int face, int toolSlot, String toolItemId, ItemStack toolPrototype,
            int limit) {
        PacketDistributor.sendToServer(new C2SRtsUltiminePayload(
                pos,
                (byte) face,
                (byte) Mth.clamp(toolSlot, 0, 8),
                toolItemId == null ? "" : toolItemId,
                toolPrototype == null ? ItemStack.EMPTY : toolPrototype,
                (short) Mth.clamp(limit, 1, 256)));
    }

    static void sendMineAbort(BlockPos pos, int face, int toolSlot) {
        PacketDistributor.sendToServer(new C2SRtsMinePayload(
                pos,
                (byte) face,
                false,
                (byte) Mth.clamp(toolSlot, 0, 8),
                "",
                ItemStack.EMPTY,
                false));
    }
}
