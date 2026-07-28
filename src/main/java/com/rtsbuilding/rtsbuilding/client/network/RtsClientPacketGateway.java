package com.rtsbuilding.rtsbuilding.client.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.developer.RtsDeveloperScenarioTracker;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.network.RtsPayloadRegistrar;
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
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@SideOnly(Side.CLIENT)
public final class RtsClientPacketGateway {
    private RtsClientPacketGateway() {
    }

    private static void send(Object message) {
        if (!(message instanceof IMessage)) {
            throw new IllegalStateException("客户端消息尚未迁移为 1.12.2 IMessage: "
                    + (message == null ? "null" : message.getClass().getName()));
        }
        RtsPayloadRegistrar.sendToServer((IMessage) message);
    }

    public static void sendSetMode(BuilderMode mode) {
        send(new C2SRtsSetModePayload((byte) mode.ordinal()));
    }

    public static void sendRequestProgressionState() {
        send(new C2SRtsRequestProgressionStatePayload());
    }

    public static void sendSetSurvivalProgression(boolean enabled) {
        send(new C2SRtsSetSurvivalProgressionPayload(enabled));
    }

    public static void sendSetHome(BlockPos pos) {
        send(new C2SRtsSetHomePayload(pos));
    }

    public static void sendBeginHomeSelection() {
        send(new C2SRtsBeginHomeSelectionPayload());
    }

    public static void sendRequestPlugins() {
        send(new C2SRtsRequestPluginsPayload());
    }

    public static void sendInstallPluginFromInventorySlot(int inventorySlot) {
        send(new C2SRtsInstallPluginPayload(inventorySlot));
    }

    public static void sendUninstallPlugin(String pluginId) {
        send(new C2SRtsUninstallPluginPayload(pluginId == null ? "" : pluginId));
    }

    public static void sendToggleCamera(boolean startAtPlayerHead) {
        send(new C2SRtsToggleCameraPayload(startAtPlayerHead));
    }

    public static void sendSetFunnelEnabled(boolean enabled) {
        send(new C2SRtsSetFunnelPayload(enabled));
    }

    public static void sendCameraMove(float forward, float strafe, float vertical, float panX, float panY, float rotateX, float rotateY,
            float scroll, int rotateSteps, boolean fast) {
        send(new C2SRtsCameraMovePayload(
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
        send(new C2SRtsFunnelTargetPayload(target));
    }

    public static void sendLinkStorage(BlockPos pos, boolean allowStore) {
        RtsDeveloperScenarioTracker.getInstance().record(
                "storage_link_request", "pos=" + pos);
        send(new C2SRtsLinkStoragePayload(
                pos,
                allowStore ? C2SRtsLinkStoragePayload.MODE_BIDIRECTIONAL : C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY));
    }

    public static void sendRequestStoragePage(int page, String search, String category, RtsStorageSort sort, boolean ascending, int pageSize) {
        boolean pinyinSearchEnabled = isChineseLanguageSelected();
        send(new C2SRtsRequestStoragePagePayload(
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
        send(new C2SRtsSetAutoStorePayload(enabled));
    }

    public static void sendSetBdNetwork(boolean enabled) {
        send(new C2SRtsSetBdNetworkPayload(enabled));
    }

    public static void sendUnlinkStorage(BlockPos pos) {
        if (pos != null) {
            send(new C2SRtsUnlinkStoragePayload(pos));
        }
    }

    public static void sendUpdateLinkedStorage(BlockPos pos, boolean extractOnly, int priority) {
        if (pos != null) {
            send(new C2SRtsUpdateLinkedStoragePayload(
                    pos,
                    extractOnly ? C2SRtsLinkStoragePayload.MODE_EXTRACT_ONLY : C2SRtsLinkStoragePayload.MODE_BIDIRECTIONAL,
                    MathHelper.clamp(priority, -9999, 9999)));
        }
    }

    public static void sendCraftRecipe(String recipeId, int craftCount) {
        send(new C2SRtsCraftRecipePayload(recipeId, Math.max(1, craftCount)));
    }

    public static void sendOpenCraftTerminal() {
        send(new C2SRtsOpenCraftTerminalPayload());
    }

    public static void sendCloseRemoteMenu() {
        send(new C2SRtsCloseRemoteMenuPayload());
    }

    public static void sendQuestDetectManual() {
        send(new C2SRtsQuestDetectPayload(C2SRtsQuestDetectPayload.MODE_MANUAL));
    }

    public static void sendRotateBlock(BlockPos pos) {
        send(new C2SRtsRotateBlockPayload(pos));
    }

    public static void sendRotateBlockStep(
            BlockPos pos,
            EnumFacing axisEnumFacing,
            int quarterTurns) {
        if (pos != null && axisEnumFacing != null && quarterTurns != 0) {
            send(
                    new C2SRtsOrientBlockPayload(
                            pos, axisEnumFacing, quarterTurns));
        }
    }

    public static void sendStoreHotbarSlot(int slot) {
        send(new C2SRtsStoreHotbarSlotPayload((byte) MathHelper.clamp(slot, 0, 8)));
    }

    public static void sendFillInventory() {
        send(new C2SRtsFillInventoryPayload());
    }

    public static void sendQuickDrop(String itemId, int amount, Vec3d dropPos) {
        send(new C2SRtsQuickDropPayload(
                itemId,
                (byte) MathHelper.clamp(amount, 1, 64),
                dropPos.x,
                dropPos.y,
                dropPos.z));
    }

    public static void sendRequestCraftables(String search, boolean showUnavailable, int offset, int limit) {
        boolean pinyinSearchEnabled = isChineseLanguageSelected();
        send(new C2SRtsRequestCraftablesPayload(
                search,
                showUnavailable,
                Math.max(0, offset),
                Math.max(1, limit),
                pinyinSearchEnabled,
                buildLocalizedSearchMatches(search, pinyinSearchEnabled)));
    }

    private static boolean isChineseLanguageSelected() {
        Minecraft minecraft = Minecraft.getMinecraft();
        String language = "";
        if (minecraft != null && minecraft.getLanguageManager() != null
                && minecraft.getLanguageManager().getCurrentLanguage() != null) {
            language = minecraft.getLanguageManager().getCurrentLanguage().getLanguageCode();
        }
        if (isBlank(language) && minecraft != null && minecraft.gameSettings != null) {
            language = minecraft.gameSettings.language;
        }
        language = language == null ? "" : language.toLowerCase(Locale.ROOT);
        return language.equals("zh") || language.startsWith("zh_") || language.startsWith("zh-");
    }

    private static List<String> buildLocalizedSearchMatches(String search, boolean pinyinSearchEnabled) {
        if (!pinyinSearchEnabled) {
            return java.util.Collections.emptyList();
        }
        String query = search == null ? "" : search.toLowerCase(Locale.ROOT).trim();
        if (query.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        String[] tokens = query.split("\\s+");
        List<String> matches = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS) {
            if (item == null) {
                continue;
            }
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
            if (id == null) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            if (stack.isEmpty()) {
                continue;
            }
            String label = stack.getDisplayName();
            if (matchesLocalizedSearch(id, label, tokens)) {
                matches.add(id.toString());
                if (matches.size() >= C2SRtsRequestStoragePagePayload.MAX_LOCALIZED_SEARCH_MATCHES) {
                    break;
                }
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
            if (isBlank(token)) {
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
        ItemStack preview = previewStack == null ? ItemStack.EMPTY : copyOne(previewStack);
        send(new C2SRtsSetQuickSlotPayload((byte) index, itemId, preview));
    }

    public static void sendSetGuiBinding(int index, BlockPos pos, EnumFacing face, String itemIdHint) {
        send(new C2SRtsSetGuiBindingPayload(
                (byte) index,
                false,
                pos,
                (byte) (face == null ? -1 : face.getIndex()),
                itemIdHint == null ? "" : itemIdHint));
    }

    public static void sendClearGuiBinding(int index) {
        send(new C2SRtsSetGuiBindingPayload((byte) index, true, BlockPos.ORIGIN, (byte) -1, ""));
    }

    public static void sendOpenGuiBinding(int index) {
        send(new C2SRtsOpenGuiBindingPayload((byte) index));
    }

    public static void sendPlace(RayTraceResult hit, boolean forcePlace, boolean skipIfOccupied, String itemId,
            ItemStack itemPrototype, int rotateSteps, String statePreset, Vec3d rayOrigin, Vec3d rayDir) {
        sendPlace(hit, forcePlace, skipIfOccupied, itemId, itemPrototype, rotateSteps, statePreset, rayOrigin, rayDir, false);
    }

    public static void sendEmptyHandPlace(RayTraceResult hit, Vec3d rayOrigin, Vec3d rayDir) {
        sendPlace(hit, false, false, "", ItemStack.EMPTY, 0, "", rayOrigin, rayDir, false, true);
    }

    public static void sendPlace(RayTraceResult hit, boolean forcePlace, boolean skipIfOccupied, String itemId,
            ItemStack itemPrototype, int rotateSteps, String statePreset, Vec3d rayOrigin, Vec3d rayDir, boolean quickBuild) {
        sendPlace(hit, forcePlace, skipIfOccupied, itemId, itemPrototype, rotateSteps, statePreset, rayOrigin, rayDir, quickBuild, false);
    }

    private static void sendPlace(RayTraceResult hit, boolean forcePlace, boolean skipIfOccupied, String itemId,
            ItemStack itemPrototype, int rotateSteps, String statePreset, Vec3d rayOrigin, Vec3d rayDir, boolean quickBuild,
            boolean forceEmptyHand) {
        RtsDeveloperScenarioTracker.getInstance().record("place_request", "count=1");
        ItemStack prototype = itemPrototype == null ? ItemStack.EMPTY : itemPrototype.copy();
        if (!prototype.isEmpty()) {
            prototype.setCount(1);
        }
        if (!isBlank(statePreset)) {
            RtsbuildingMod.LOGGER.debug(
                    "R placement preset send: item={}, preset={}, quickBuild={}, clicked={}",
                    itemId, statePreset, quickBuild, hit.getBlockPos());
        }
        RtsCullingClientState.revealLikelyPlacement(hit.getBlockPos(), hit.sideHit);
        send(new C2SRtsPlacePayload(
                hit.getBlockPos(),
                (byte) hit.sideHit.getIndex(),
                hit.hitVec.x,
                hit.hitVec.y,
                hit.hitVec.z,
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

    public static void sendPlaceBatch(List<RayTraceResult> hits, boolean forcePlace, boolean skipIfOccupied, String itemId,
            ItemStack itemPrototype, int rotateSteps, Vec3d rayOrigin, Vec3d rayDir) {
        sendPlaceBatch(hits, hits == null || hits.isEmpty() ? null : hits.get(0), forcePlace, skipIfOccupied, false,
                itemId, itemPrototype, rotateSteps, "", rayOrigin, rayDir);
    }

    public static void sendPlaceBatch(List<RayTraceResult> hits, RayTraceResult templateHit, boolean forcePlace,
            boolean skipIfOccupied, boolean overwriteExisting, String itemId, ItemStack itemPrototype, int rotateSteps, String statePreset,
            Vec3d rayOrigin, Vec3d rayDir) {
        if (hits == null || hits.isEmpty()) {
            return;
        }
        EnumFacing face = hits.get(0).sideHit;
        RayTraceResult placementTemplate = templateHit == null ? hits.get(0) : templateHit;
        double hitOffsetX = placementTemplate.hitVec.x - placementTemplate.getBlockPos().getX();
        double hitOffsetY = placementTemplate.hitVec.y - placementTemplate.getBlockPos().getY();
        double hitOffsetZ = placementTemplate.hitVec.z - placementTemplate.getBlockPos().getZ();
        List<BlockPos> positions = new ArrayList<>(Math.min(hits.size(), C2SRtsPlaceBatchPayload.MAX_POSITIONS));
        for (RayTraceResult hit : hits) {
            if (hit == null || hit.sideHit != face) {
                continue;
            }
            positions.add(hit.getBlockPos().toImmutable());
            RtsCullingClientState.revealLikelyPlacement(hit.getBlockPos(), hit.sideHit);
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
        send(new C2SRtsPlaceBatchPayload(
                positions,
                (byte) face.getIndex(),
                hitOffsetX,
                hitOffsetY,
                hitOffsetZ,
                (byte) rotateSteps,
                statePreset == null ? "" : statePreset,
                forcePlace,
                skipIfOccupied,
                overwriteExisting,
                itemId == null ? "" : itemId,
                prototype,
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z));
    }

    public static void sendPlaceFluid(RayTraceResult hit, boolean forcePlace, String fluidId, Vec3d rayOrigin, Vec3d rayDir) {
        RtsCullingClientState.revealLikelyPlacement(hit.getBlockPos(), hit.sideHit);
        send(new C2SRtsPlaceFluidPayload(
                hit.getBlockPos(),
                (byte) hit.sideHit.getIndex(),
                hit.hitVec.x,
                hit.hitVec.y,
                hit.hitVec.z,
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
        send(new C2SRtsStoreFluidPayload(
                sourceType,
                (byte) MathHelper.clamp(toolSlot, 0, 8),
                itemId == null ? "" : itemId));
    }

    public static void sendInteractBlockWithToolSlot(RayTraceResult hit, int toolSlot, Vec3d rayOrigin, Vec3d rayDir) {
        send(new C2SRtsInteractPayload(
                C2SRtsInteractPayload.NO_ENTITY,
                hit.getBlockPos(),
                (byte) hit.sideHit.getIndex(),
                hit.hitVec.x,
                hit.hitVec.y,
                hit.hitVec.z,
                C2SRtsInteractPayload.SOURCE_TOOL_SLOT,
                (byte) MathHelper.clamp(toolSlot, 0, 8),
                "",
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z));
    }

    public static void sendUseItemInAirWithToolSlot(RayTraceResult hit, int toolSlot, Vec3d rayOrigin, Vec3d rayDir) {
        send(new C2SRtsInteractPayload(
                C2SRtsInteractPayload.NO_ENTITY,
                hit.getBlockPos(),
                (byte) hit.sideHit.getIndex(),
                hit.hitVec.x,
                hit.hitVec.y,
                hit.hitVec.z,
                C2SRtsInteractPayload.SOURCE_TOOL_SLOT_AIR,
                (byte) MathHelper.clamp(toolSlot, 0, 8),
                "",
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z));
    }

    public static void sendInteractBlockWithPinnedItem(RayTraceResult hit, String itemId, Vec3d rayOrigin, Vec3d rayDir) {
        send(new C2SRtsInteractPayload(
                C2SRtsInteractPayload.NO_ENTITY,
                hit.getBlockPos(),
                (byte) hit.sideHit.getIndex(),
                hit.hitVec.x,
                hit.hitVec.y,
                hit.hitVec.z,
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

    public static void sendInteractEntityWithToolSlot(int entityId, Vec3d hitLocation, int toolSlot, Vec3d rayOrigin, Vec3d rayDir) {
        send(new C2SRtsInteractPayload(
                entityId,
                new BlockPos(hitLocation),
                (byte) 1,
                hitLocation.x,
                hitLocation.y,
                hitLocation.z,
                C2SRtsInteractPayload.SOURCE_TOOL_SLOT,
                (byte) MathHelper.clamp(toolSlot, 0, 8),
                "",
                rayOrigin.x,
                rayOrigin.y,
                rayOrigin.z,
                rayDir.x,
                rayDir.y,
                rayDir.z));
    }

    public static void sendInteractEntityEmptyHand(int entityId, Vec3d hitLocation, Vec3d rayOrigin, Vec3d rayDir) {
        send(new C2SRtsInteractPayload(
                entityId,
                new BlockPos(hitLocation),
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

    public static void sendInteractEntityWithPinnedItem(int entityId, Vec3d hitLocation, String itemId, Vec3d rayOrigin, Vec3d rayDir) {
        send(new C2SRtsInteractPayload(
                entityId,
                new BlockPos(hitLocation),
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

    public static void sendBreakPlaced(BlockPos pos, EnumFacing face, boolean allowAdjacentFallback) {
        send(new C2SRtsBreakPayload(
                pos,
                (byte) face.getIndex(),
                allowAdjacentFallback));
    }

    public static void sendAreaMine(int minX, int maxX, int minY, int maxY, int minZ, int maxZ,
            int toolSlot, String toolItemId, ItemStack toolPrototype, byte shapeType, byte fillType,
            boolean toolProtectionEnabled) {
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        RtsDeveloperScenarioTracker.getInstance().record("mine_request", "volume=" + volume);
        send(new C2SRtsAreaMinePayload(
                minX, maxX, minY, maxY, minZ, maxZ,
                (byte) MathHelper.clamp(toolSlot, 0, 8),
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
        send(new C2SRtsAreaDestroyPayload(
                positions,
                (byte) MathHelper.clamp(toolSlot, 0, 8),
                toolItemId == null ? "" : toolItemId,
                toolPrototype == null ? ItemStack.EMPTY : toolPrototype,
                toolProtectionEnabled));
    }

    public static void sendMineStart(BlockPos pos, int face, int toolSlot, String toolItemId, ItemStack toolPrototype,
            boolean allowPlacedBlockRecovery, boolean toolProtectionEnabled) {
        RtsDeveloperScenarioTracker.getInstance().record("mine_request", "kind=single");
        send(new C2SRtsMinePayload(
                pos,
                (byte) face,
                true,
                (byte) MathHelper.clamp(toolSlot, 0, 8),
                toolItemId == null ? "" : toolItemId,
                toolPrototype == null ? ItemStack.EMPTY : toolPrototype,
                allowPlacedBlockRecovery,
                toolProtectionEnabled));
    }

    public static void sendUltimineStart(BlockPos pos, int face, int toolSlot, String toolItemId, ItemStack toolPrototype,
            int limit, byte mode, boolean toolProtectionEnabled) {
        RtsDeveloperScenarioTracker.getInstance().record("mine_request", "kind=ultimine;limit=" + limit);
        send(new C2SRtsUltiminePayload(
                pos,
                (byte) face,
                (byte) MathHelper.clamp(toolSlot, 0, 8),
                toolItemId == null ? "" : toolItemId,
                toolPrototype == null ? ItemStack.EMPTY : toolPrototype,
                (short) MathHelper.clamp(limit, 1, 256),
                mode,
                toolProtectionEnabled));
    }

    public static void sendUndo() {
        send(new C2SRtsUndoPayload());
    }

    public static void sendPathfindingGoTo(BlockPos target) {
        send(new C2SRtsPathfindingPayload(target));
    }

    public static void sendMineAbort(BlockPos pos, int face, int toolSlot) {
        send(new C2SRtsMinePayload(
                pos,
                (byte) face,
                false,
                (byte) MathHelper.clamp(toolSlot, 0, 8),
                "",
                ItemStack.EMPTY,
                false,
                false));
    }

    private static ItemStack copyOne(ItemStack stack) {
        ItemStack copy = stack.copy();
        copy.setCount(1);
        return copy;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
