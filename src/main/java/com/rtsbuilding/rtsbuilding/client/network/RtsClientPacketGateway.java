package com.rtsbuilding.rtsbuilding.client.network;

import com.rtsbuilding.rtsbuilding.client.developer.RtsDeveloperScenarioTracker;
import com.rtsbuilding.rtsbuilding.client.diagnostic.RtsClientOperationDiagnostics;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsMiningStopOrigin;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceIds;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsTraceInputKind;
import com.rtsbuilding.rtsbuilding.network.builder.*;
import com.rtsbuilding.rtsbuilding.network.camera.C2SRtsCameraMovePayload;
import com.rtsbuilding.rtsbuilding.network.camera.C2SRtsToggleCameraPayload;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsCraftRecipePayload;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsOpenCraftTerminalPayload;
import com.rtsbuilding.rtsbuilding.network.craft.C2SRtsRequestCraftablesPayload;
import com.rtsbuilding.rtsbuilding.network.pathfinding.C2SRtsPathfindingPayload;
import com.rtsbuilding.rtsbuilding.network.plugin.C2SRtsInstallPluginPayload;
import com.rtsbuilding.rtsbuilding.network.plugin.C2SRtsRequestPluginsPayload;
import com.rtsbuilding.rtsbuilding.network.plugin.C2SRtsUninstallPluginPayload;
import com.rtsbuilding.rtsbuilding.network.progression.*;
import com.rtsbuilding.rtsbuilding.network.storage.*;
import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingClientState;
import com.rtsbuilding.rtsbuilding.util.RtsPinyinSearch;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientNetworkBridge;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RtsClientPacketGateway {
    private static long activeMineTraceId = RtsTraceIds.NONE;
    private static long activeMineStartedNanos;
    private static long activeMineClientTick = -1L;
    private static RtsTraceInputKind activeMineInput = RtsTraceInputKind.UNKNOWN;

    private RtsClientPacketGateway() {
    }

    public static void sendSetMode(BuilderMode mode) {
        RtsClientNetworkBridge.send(new C2SRtsSetModePayload((byte) mode.ordinal()));
    }

    public static void sendRequestProgressionState() {
        RtsClientNetworkBridge.send(new C2SRtsRequestProgressionStatePayload());
    }

    public static void sendSetSurvivalProgression(boolean enabled) {
        RtsClientNetworkBridge.send(new C2SRtsSetSurvivalProgressionPayload(enabled));
    }

    public static void sendSetHome(BlockPos pos) {
        RtsClientNetworkBridge.send(new C2SRtsSetHomePayload(pos));
    }

    public static void sendBeginHomeSelection() {
        RtsClientNetworkBridge.send(new C2SRtsBeginHomeSelectionPayload());
    }

    public static void sendRequestPlugins() {
        RtsClientNetworkBridge.send(new C2SRtsRequestPluginsPayload());
    }

    public static void sendInstallPluginFromInventorySlot(int inventorySlot) {
        RtsClientNetworkBridge.send(new C2SRtsInstallPluginPayload(inventorySlot));
    }

    public static void sendUninstallPlugin(String pluginId) {
        RtsClientNetworkBridge.send(new C2SRtsUninstallPluginPayload(pluginId == null ? "" : pluginId));
    }

    public static void sendToggleCamera(boolean startAtPlayerHead) {
        RtsClientNetworkBridge.send(new C2SRtsToggleCameraPayload(startAtPlayerHead));
    }

    public static void sendSetFunnelEnabled(boolean enabled) {
        RtsClientNetworkBridge.send(new C2SRtsSetFunnelPayload(enabled));
    }

    public static void sendCameraMove(float forward, float strafe, float vertical, float panX, float panY, float rotateX, float rotateY,
            float scroll, int rotateSteps, boolean fast) {
        RtsClientNetworkBridge.send(new C2SRtsCameraMovePayload(
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
        RtsClientNetworkBridge.send(new C2SRtsFunnelTargetPayload(target));
    }

    public static void sendLinkStorage(BlockPos pos, boolean allowStore) {
        RtsDeveloperScenarioTracker.getInstance().record(
                "storage_link_request", "pos=" + pos.toShortString());
        RtsClientNetworkBridge.send(new C2SRtsLinkStoragePayload(
                pos,
                allowStore ? C2SRtsLinkStoragePayload.MODE_BIDIRECTIONAL : C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY));
    }

    /** 客户端只发送两角和链接模式，储存候选必须由服务端在当前世界重扫。 */
    public static void sendBatchLinkStorage(
            BlockPos first, BlockPos second, boolean allowStore) {
        if (first == null || second == null) {
            return;
        }
        RtsDeveloperScenarioTracker.getInstance().record(
                "storage_batch_link_request",
                "first=" + first.toShortString() + ",second=" + second.toShortString());
        RtsClientNetworkBridge.send(new C2SRtsBatchLinkStoragePayload(
                first.immutable(),
                second.immutable(),
                allowStore ? C2SRtsLinkStoragePayload.MODE_BIDIRECTIONAL : C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY));
    }

    public static void sendRequestStoragePage(int page, String search, String category, RtsStorageSort sort, boolean ascending, int pageSize) {
        boolean pinyinSearchEnabled = isChineseLanguageSelected();
        RtsClientNetworkBridge.send(new C2SRtsRequestStoragePagePayload(
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
        RtsClientNetworkBridge.send(new C2SRtsSetAutoStorePayload(enabled));
    }

    public static void sendSetBdNetwork(boolean enabled) {
        RtsClientNetworkBridge.send(new C2SRtsSetBdNetworkPayload(enabled));
    }

    public static void sendUnlinkStorage(BlockPos pos) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            sendUnlinkStorage(minecraft.level.dimension().identifier().toString(), pos);
        }
    }

    public static void sendUnlinkStorage(String dimensionId, BlockPos pos) {
        Identifier dimension = Identifier.tryParse(dimensionId);
        if (dimension != null && pos != null) {
            RtsClientNetworkBridge.send(new C2SRtsUnlinkStoragePayload(dimension, pos));
        }
    }

    public static void sendUpdateLinkedStorage(BlockPos pos, boolean extractOnly, int priority) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            sendUpdateLinkedStorage(minecraft.level.dimension().identifier().toString(), pos, extractOnly, priority);
        }
    }

    public static void sendUpdateLinkedStorage(
            String dimensionId, BlockPos pos, boolean extractOnly, int priority) {
        Identifier dimension = Identifier.tryParse(dimensionId);
        if (dimension != null && pos != null) {
            RtsClientNetworkBridge.send(new C2SRtsUpdateLinkedStoragePayload(
                    dimension,
                    pos,
                    extractOnly ? C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY : C2SRtsLinkStoragePayload.MODE_BIDIRECTIONAL,
                    Mth.clamp(priority, -9999, 9999)));
        }
    }

    public static void sendCraftRecipe(String recipeId, int craftCount) {
        RtsClientNetworkBridge.send(new C2SRtsCraftRecipePayload(recipeId, Math.max(1, craftCount)));
    }

    public static void sendOpenCraftTerminal() {
        RtsClientNetworkBridge.send(new C2SRtsOpenCraftTerminalPayload());
    }

    public static void sendCloseRemoteMenu() {
        RtsClientNetworkBridge.send(new C2SRtsCloseRemoteMenuPayload());
    }

    public static void sendQuestDetectManual() {
        RtsClientNetworkBridge.send(new C2SRtsQuestDetectPayload(C2SRtsQuestDetectPayload.MODE_MANUAL));
    }

    public static void sendRotateBlock(BlockPos pos) {
        RtsClientNetworkBridge.send(new C2SRtsRotateBlockPayload(pos));
    }

    public static void sendRotateBlockStep(
            BlockPos pos, Direction axisDirection, int quarterTurns) {
        RtsClientNetworkBridge.send(new C2SRtsOrientBlockPayload(
                pos, axisDirection, quarterTurns));
    }

    public static void sendStoreHotbarSlot(int slot) {
        RtsClientNetworkBridge.send(new C2SRtsStoreHotbarSlotPayload((byte) Mth.clamp(slot, 0, 8)));
    }

    public static void sendFillInventory() {
        RtsClientNetworkBridge.send(new C2SRtsFillInventoryPayload());
    }

    public static void sendQuickDrop(String itemId, int amount, Vec3 dropPos) {
        RtsClientNetworkBridge.send(new C2SRtsQuickDropPayload(
                itemId,
                (byte) Mth.clamp(amount, 1, 64),
                dropPos.x,
                dropPos.y,
                dropPos.z));
    }

    public static void sendRequestCraftables(String search, boolean showUnavailable, int offset, int limit) {
        boolean pinyinSearchEnabled = isChineseLanguageSelected();
        RtsClientNetworkBridge.send(new C2SRtsRequestCraftablesPayload(
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
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
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
                if (matches.size() >= C2SRtsRequestStoragePagePayload.MAX_LOCALIZED_SEARCH_MATCHES) {
                    break;
                }
            }
        }
        return matches;
    }

    private static boolean matchesLocalizedSearch(Identifier id, String label, String[] tokens) {
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
        ItemStack preview = previewStack == null ? ItemStack.EMPTY : previewStack.copyWithCount(1);
        RtsClientNetworkBridge.send(new C2SRtsSetQuickSlotPayload((byte) index, itemId, preview));
    }

    public static void sendSetGuiBinding(int index, BlockPos pos, Direction face, String itemIdHint) {
        RtsClientNetworkBridge.send(new C2SRtsSetGuiBindingPayload(
                (byte) index,
                false,
                pos,
                (byte) (face == null ? -1 : face.get3DDataValue()),
                itemIdHint == null ? "" : itemIdHint));
    }

    public static void sendClearGuiBinding(int index) {
        RtsClientNetworkBridge.send(new C2SRtsSetGuiBindingPayload((byte) index, true, BlockPos.ZERO, (byte) -1, ""));
    }

    public static void sendOpenGuiBinding(int index) {
        RtsClientNetworkBridge.send(new C2SRtsOpenGuiBindingPayload((byte) index));
    }

    public static void sendPlace(BlockHitResult hit, boolean forcePlace, boolean skipIfOccupied, String itemId,
            ItemStack itemPrototype, int rotateSteps, Vec3 rayOrigin, Vec3 rayDir) {
        sendPlace(hit, forcePlace, skipIfOccupied, itemId, itemPrototype,
                rotateSteps, "", rayOrigin, rayDir, false);
    }

    public static void sendEmptyHandPlace(BlockHitResult hit, Vec3 rayOrigin, Vec3 rayDir) {
        sendPlace(hit, false, false, "", ItemStack.EMPTY, 0, "",
                rayOrigin, rayDir, false, true);
    }

    public static void sendPlace(BlockHitResult hit, boolean forcePlace, boolean skipIfOccupied, String itemId,
            ItemStack itemPrototype, int rotateSteps, Vec3 rayOrigin, Vec3 rayDir, boolean quickBuild) {
        sendPlace(hit, forcePlace, skipIfOccupied, itemId, itemPrototype,
                rotateSteps, "", rayOrigin, rayDir, quickBuild);
    }

    public static void sendPlace(BlockHitResult hit, boolean forcePlace,
            boolean skipIfOccupied, String itemId, ItemStack itemPrototype,
            int rotateSteps, String statePreset, Vec3 rayOrigin, Vec3 rayDir,
            boolean quickBuild) {
        sendPlace(hit, forcePlace, skipIfOccupied, itemId, itemPrototype,
                rotateSteps, statePreset, rayOrigin, rayDir, quickBuild, false);
    }

    private static void sendPlace(BlockHitResult hit, boolean forcePlace, boolean skipIfOccupied, String itemId,
            ItemStack itemPrototype, int rotateSteps, String statePreset,
            Vec3 rayOrigin, Vec3 rayDir, boolean quickBuild,
            boolean forceEmptyHand) {
        RtsDeveloperScenarioTracker.getInstance().record("place_request", "count=1");
        ItemStack prototype = itemPrototype == null ? ItemStack.EMPTY : itemPrototype.copy();
        if (!prototype.isEmpty()) {
            prototype.setCount(1);
        }
        RtsCullingClientState.revealLikelyPlacement(hit.getBlockPos(), hit.getDirection());
        RtsClientNetworkBridge.send(new C2SRtsPlacePayload(
                hit.getBlockPos(),
                (byte) hit.getDirection().get3DDataValue(),
                hit.getLocation().x,
                hit.getLocation().y,
                hit.getLocation().z,
                (byte) rotateSteps,
                statePreset == null ? "" : statePreset,
                forcePlace,
                skipIfOccupied,
                itemId == null ? "" : itemId,
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
        sendPlaceBatch(hits, hits == null || hits.isEmpty() ? null : hits.get(0), forcePlace, skipIfOccupied,
                itemId, itemPrototype, rotateSteps, "", rayOrigin, rayDir);
    }

    public static void sendPlaceBatch(List<BlockHitResult> hits, BlockHitResult templateHit, boolean forcePlace,
            boolean skipIfOccupied, String itemId, ItemStack itemPrototype, int rotateSteps, Vec3 rayOrigin, Vec3 rayDir) {
        sendPlaceBatch(hits, templateHit, forcePlace, skipIfOccupied, itemId,
                itemPrototype, rotateSteps, "", rayOrigin, rayDir);
    }

    public static void sendPlaceBatch(List<BlockHitResult> hits,
            BlockHitResult templateHit, boolean forcePlace,
            boolean skipIfOccupied, String itemId, ItemStack itemPrototype,
            int rotateSteps, String statePreset, Vec3 rayOrigin, Vec3 rayDir) {
        if (hits == null || hits.isEmpty()) {
            return;
        }
        Direction face = hits.get(0).getDirection();
        BlockHitResult placementTemplate = templateHit == null ? hits.get(0) : templateHit;
        double hitOffsetX = placementTemplate.getLocation().x - placementTemplate.getBlockPos().getX();
        double hitOffsetY = placementTemplate.getLocation().y - placementTemplate.getBlockPos().getY();
        double hitOffsetZ = placementTemplate.getLocation().z - placementTemplate.getBlockPos().getZ();
        List<BlockPos> positions = new ArrayList<>(Math.min(hits.size(), C2SRtsPlaceBatchPayload.MAX_POSITIONS));
        for (BlockHitResult hit : hits) {
            if (hit == null || hit.getDirection() != face) {
                continue;
            }
            positions.add(hit.getBlockPos().immutable());
            RtsCullingClientState.revealLikelyPlacement(hit.getBlockPos(), hit.getDirection());
            if (positions.size() >= C2SRtsPlaceBatchPayload.MAX_POSITIONS) {
                break;
            }
        }
        if (positions.isEmpty()) {
            return;
        }
        RtsDeveloperScenarioTracker.getInstance().record(
                "place_batch_request", "count=" + positions.size());
        ItemStack prototype = itemPrototype == null ? ItemStack.EMPTY : itemPrototype.copy();
        if (!prototype.isEmpty()) {
            prototype.setCount(1);
        }
        RtsClientNetworkBridge.send(new C2SRtsPlaceBatchPayload(
                positions,
                (byte) face.get3DDataValue(),
                hitOffsetX,
                hitOffsetY,
                hitOffsetZ,
                (byte) rotateSteps,
                statePreset == null ? "" : statePreset,
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

    /** 只发送智能填坑意图；客户端预览坐标绝不进入网络包。 */
    public static void sendConfirmSmartFill(
            BlockHitResult hit,
            int maxBlocks,
            int detectionDiameter,
            String itemId,
            ItemStack itemPrototype,
            int rotateSteps,
            String statePreset,
            Vec3 rayOrigin,
            Vec3 rayDirection) {
        if (hit == null || rayOrigin == null || rayDirection == null) {
            return;
        }
        ItemStack prototype = itemPrototype == null ? ItemStack.EMPTY : itemPrototype.copy();
        if (!prototype.isEmpty()) {
            prototype.setCount(1);
        }
        RtsCullingClientState.revealLikelyPlacement(hit.getBlockPos(), hit.getDirection());
        RtsClientNetworkBridge.send(new C2SRtsConfirmSmartFillPayload(
                hit.getBlockPos(),
                (byte) hit.getDirection().get3DDataValue(),
                Mth.clamp(maxBlocks, 1, 1024),
                Mth.clamp(detectionDiameter, 3, 32),
                hit.getLocation().x - hit.getBlockPos().getX(),
                hit.getLocation().y - hit.getBlockPos().getY(),
                hit.getLocation().z - hit.getBlockPos().getZ(),
                (byte) rotateSteps,
                statePreset == null ? "" : statePreset,
                itemId == null ? "" : itemId,
                prototype,
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDirection.x,
                rayDirection.y,
                rayDirection.z));
    }

    public static void sendPlaceFluid(BlockHitResult hit, boolean forcePlace, String fluidId, Vec3 rayOrigin, Vec3 rayDir) {
        RtsCullingClientState.revealLikelyPlacement(hit.getBlockPos(), hit.getDirection());
        RtsClientNetworkBridge.send(new C2SRtsPlaceFluidPayload(
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
        RtsClientNetworkBridge.send(new C2SRtsStoreFluidPayload(
                sourceType,
                (byte) Mth.clamp(toolSlot, 0, 8),
                itemId == null ? "" : itemId));
    }

    public static void sendInteractBlockWithToolSlot(BlockHitResult hit, int toolSlot, Vec3 rayOrigin, Vec3 rayDir) {
        RtsClientNetworkBridge.send(new C2SRtsInteractPayload(
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
        RtsClientNetworkBridge.send(new C2SRtsInteractPayload(
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
        RtsClientNetworkBridge.send(new C2SRtsInteractPayload(
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
        RtsClientNetworkBridge.send(new C2SRtsInteractPayload(
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
        RtsClientNetworkBridge.send(new C2SRtsInteractPayload(
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
        RtsClientNetworkBridge.send(new C2SRtsInteractPayload(
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
        RtsClientNetworkBridge.send(new C2SRtsBreakPayload(
                pos,
                (byte) face.get3DDataValue(),
                allowAdjacentFallback));
    }

    public static void sendAreaMine(int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
            int toolSlot, String toolItemId, ItemStack toolPrototype, byte shapeType, byte fillType,
            boolean toolProtectionEnabled) {
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        RtsDeveloperScenarioTracker.getInstance().record("mine_request", "volume=" + volume);
        var trace = beginTrace("AREA_MINE", "shape", (int) Math.min(Integer.MAX_VALUE, volume));
        int sequence = RtsClientOperationDiagnostics.packetSend(
                trace.traceId(), "AREA_MINE", 0, trace.inputKind(), RtsMiningStopOrigin.NONE,
                (int) Math.min(Integer.MAX_VALUE, volume));
        RtsClientNetworkBridge.send(new C2SRtsAreaMineTracePayload(
                trace.traceId(), sequence, trace.clientTick(), 0,
                trace.inputKind().wireId(), RtsMiningStopOrigin.NONE.wireId(),
                minX, maxX, minY, maxY, minZ, maxZ,
                (byte) Mth.clamp(toolSlot, 0, 8),
                toolItemId == null ? "" : toolItemId,
                toolPrototype == null ? ItemStack.EMPTY : toolPrototype,
                shapeType,
                fillType,
                toolProtectionEnabled));
    }

    public static void sendAreaDestroy(List<BlockPos> positions, int toolSlot, String toolItemId, ItemStack toolPrototype,
            boolean toolProtectionEnabled) {
        if (positions == null || positions.isEmpty()) {
            return;
        }
        var trace = beginTrace("AREA_DESTROY", "selection", positions.size());
        int sequence = RtsClientOperationDiagnostics.packetSend(
                trace.traceId(), "AREA_DESTROY", 0, trace.inputKind(), RtsMiningStopOrigin.NONE,
                positions.size());
        RtsClientNetworkBridge.send(new C2SRtsAreaDestroyTracePayload(
                trace.traceId(), sequence, trace.clientTick(), 0,
                trace.inputKind().wireId(), RtsMiningStopOrigin.NONE.wireId(),
                positions,
                (byte) Mth.clamp(toolSlot, 0, 8),
                toolItemId == null ? "" : toolItemId,
                toolPrototype == null ? ItemStack.EMPTY : toolPrototype,
                toolProtectionEnabled));
    }

    /** 便捷破坏协议只发送声明参数，不信任客户端预览的坐标列表。 */
    public static void sendConvenienceDestroy(
            com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroyMode mode,
            BlockPos anchor,
            Direction face,
            com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroySettings settings,
            int toolSlot,
            String toolItemId,
            ItemStack toolPrototype,
            boolean toolProtectionEnabled) {
        if (mode == null || anchor == null) {
            return;
        }
        var trace = beginTrace("CONVENIENCE_DESTROY", anchor.toShortString(), 1);
        int sequence = RtsClientOperationDiagnostics.packetSend(
                trace.traceId(), "CONVENIENCE_DESTROY", 0, trace.inputKind(),
                RtsMiningStopOrigin.NONE, 1);
        RtsClientNetworkBridge.send(new C2SRtsConvenienceDestroyTracePayload(
                trace.traceId(), sequence, trace.clientTick(), 0,
                trace.inputKind().wireId(), RtsMiningStopOrigin.NONE.wireId(), trace.traceId(),
                mode,
                anchor.immutable(),
                (byte) (face == null ? Direction.UP : face).get3DDataValue(),
                settings == null
                        ? com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroySettings.DEFAULT
                        : settings,
                (byte) Mth.clamp(toolSlot, 0, 8),
                toolItemId == null ? "" : toolItemId,
                toolPrototype == null ? ItemStack.EMPTY : toolPrototype,
                toolProtectionEnabled));
    }

    public static void sendMineStart(BlockPos pos, int face, int toolSlot, String toolItemId, ItemStack toolPrototype,
            boolean allowPlacedBlockRecovery, boolean toolProtectionEnabled) {
        RtsDeveloperScenarioTracker.getInstance().record("mine_request", "kind=single");
        supersedeActiveMineTrace();
        var trace = beginTrace("MINE_SINGLE", pos.toShortString(), 1);
        rememberActiveMineTrace(trace);
        int sequence = RtsClientOperationDiagnostics.packetSend(
                trace.traceId(), "MINE_START", 0, trace.inputKind(), RtsMiningStopOrigin.NONE, 1);
        Vec3 center = Vec3.atCenterOf(pos);
        RtsClientNetworkBridge.send(new C2SRtsMineTracePayload(
                trace.traceId(), sequence, trace.clientTick(), 0,
                trace.inputKind().wireId(), RtsMiningStopOrigin.NONE.wireId(),
                pos,
                (byte) face,
                true,
                (byte) Mth.clamp(toolSlot, 0, 8),
                toolItemId == null ? "" : toolItemId,
                toolPrototype == null ? ItemStack.EMPTY : toolPrototype,
                allowPlacedBlockRecovery,
                toolProtectionEnabled,
                false,
                center.x, center.y, center.z,
                center.x, center.y, center.z,
                0.0D, 0.0D, 0.0D));
    }

    public static void sendUltimineStart(BlockPos pos, int face, int toolSlot, String toolItemId, ItemStack toolPrototype,
            int limit, byte mode, boolean toolProtectionEnabled) {
        RtsDeveloperScenarioTracker.getInstance().record("mine_request", "kind=ultimine;limit=" + limit);
        supersedeActiveMineTrace();
        var trace = beginTrace("ULTIMINE", pos.toShortString(), Math.max(1, limit));
        rememberActiveMineTrace(trace);
        int sequence = RtsClientOperationDiagnostics.packetSend(
                trace.traceId(), "ULTIMINE", 0, trace.inputKind(), RtsMiningStopOrigin.NONE,
                Math.max(1, limit));
        RtsClientNetworkBridge.send(new C2SRtsUltimineTracePayload(
                trace.traceId(), sequence, trace.clientTick(), 0,
                trace.inputKind().wireId(), RtsMiningStopOrigin.NONE.wireId(),
                pos,
                (byte) face,
                (byte) Mth.clamp(toolSlot, 0, 8),
                toolItemId == null ? "" : toolItemId,
                toolPrototype == null ? ItemStack.EMPTY : toolPrototype,
                (short) Mth.clamp(limit, 1, 256),
                mode,
                toolProtectionEnabled));
    }

    public static void sendUndo() {
        RtsClientNetworkBridge.send(new C2SRtsUndoPayload());
    }

    public static void sendRedo() {
        RtsClientNetworkBridge.send(new C2SRtsRedoPayload());
    }

    public static void sendPathfindingGoTo(BlockPos target) {
        RtsClientNetworkBridge.send(new C2SRtsPathfindingPayload(target));
    }

    public static void sendMineAbort(BlockPos pos, int face, int toolSlot) {
        if (activeMineTraceId == RtsTraceIds.NONE) {
            RtsClientNetworkBridge.send(new C2SRtsMinePayload(
                    pos, (byte) face, false, (byte) Mth.clamp(toolSlot, 0, 8),
                    "", ItemStack.EMPTY, false, false));
            return;
        }
        int heldMs = elapsedMs(activeMineStartedNanos);
        RtsClientOperationDiagnostics.inputRelease(
                activeMineTraceId, heldMs, activeMineInput, RtsMiningStopOrigin.EXPLICIT_CANCEL);
        int sequence = RtsClientOperationDiagnostics.packetSend(
                activeMineTraceId, "MINE_STOP", heldMs, activeMineInput,
                RtsMiningStopOrigin.EXPLICIT_CANCEL, 1);
        Vec3 center = Vec3.atCenterOf(pos);
        RtsClientNetworkBridge.send(new C2SRtsMineTracePayload(
                activeMineTraceId, sequence, activeMineClientTick, heldMs,
                activeMineInput.wireId(), RtsMiningStopOrigin.EXPLICIT_CANCEL.wireId(),
                pos,
                (byte) face,
                false,
                (byte) Mth.clamp(toolSlot, 0, 8),
                "",
                ItemStack.EMPTY,
                false,
                false,
                false,
                center.x, center.y, center.z,
                center.x, center.y, center.z,
                0.0D, 0.0D, 0.0D));
        clearActiveMineTrace();
    }

    /** 服务端已经闭合这次挖掘意图时，同步清理客户端网关保存的活动 trace。 */
    public static void handleOperationTerminal(long traceId) {
        if (traceId != RtsTraceIds.NONE && traceId == activeMineTraceId) {
            clearActiveMineTrace();
        }
    }

    private static RtsClientOperationDiagnostics.TraceStart beginTrace(
            String operation, String target, int targetCount) {
        return RtsClientOperationDiagnostics.begin(
                operation, RtsTraceInputKind.UNKNOWN, "RTS_CLIENT_GATEWAY",
                "RTS", false, target, targetCount);
    }

    private static void rememberActiveMineTrace(RtsClientOperationDiagnostics.TraceStart trace) {
        activeMineTraceId = trace.traceId();
        activeMineStartedNanos = trace.startedNanos();
        activeMineClientTick = trace.clientTick();
        activeMineInput = trace.inputKind();
    }

    private static void supersedeActiveMineTrace() {
        if (activeMineTraceId != RtsTraceIds.NONE) {
            RtsClientOperationDiagnostics.superseded(activeMineTraceId);
            clearActiveMineTrace();
        }
    }

    private static void clearActiveMineTrace() {
        activeMineTraceId = RtsTraceIds.NONE;
        activeMineStartedNanos = 0L;
        activeMineClientTick = -1L;
        activeMineInput = RtsTraceInputKind.UNKNOWN;
    }

    private static int elapsedMs(long startedNanos) {
        if (startedNanos <= 0L) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE,
                Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L));
    }
}
