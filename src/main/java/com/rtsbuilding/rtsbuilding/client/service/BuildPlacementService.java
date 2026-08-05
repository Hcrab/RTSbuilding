package com.rtsbuilding.rtsbuilding.client.service;

import com.rtsbuilding.rtsbuilding.client.compat.RtsClientOnlyBlockGuiCompat;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.record.FluidEntry;
import com.rtsbuilding.rtsbuilding.client.record.RecentEntry;
import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.common.placement.PlacementStatePreset;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsStoreFluidPayload;
import net.minecraft.client.Minecraft;
import com.rtsbuilding.rtsbuilding.platform.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import net.minecraft.util.ResourceLocation;
import com.rtsbuilding.rtsbuilding.platform.math.BlockPos;
import com.rtsbuilding.rtsbuilding.platform.math.MathHelper;
import com.rtsbuilding.rtsbuilding.platform.math.RayTraceResult;
import com.rtsbuilding.rtsbuilding.platform.math.Vec3d;
import com.rtsbuilding.rtsbuilding.platform.registry.RtsRegistries;

import java.util.List;
import java.util.function.BooleanSupplier;

public final class BuildPlacementService {

    // =========================================================================
    //  Placement item state
    // =========================================================================

    private String selectedItemId = "";
    private String selectedItemLabel = "";
    private ItemStack selectedItemPreview = null;
    private String selectedFluidId = "";
    private String selectedFluidLabel = "";
    private ItemStack selectedFluidPreview = null;
    private boolean emptyHandSelected = false;
    private int placeRotateSteps;
    private String placementStatePreset = "";
    private String placementStateItemId = "";

    // =========================================================================
    //  Build shape
    // =========================================================================

    private BuildShape buildShape = BuildShape.BLOCK;

    // =========================================================================
    //  Item/fluid selection access
    // =========================================================================

    public String getSelectedItemId() { return this.selectedItemId; }
    public String getSelectedItemLabel() { return this.selectedItemLabel; }
    public ItemStack getSelectedItemPreview() { return this.selectedItemPreview; }
    public String getSelectedFluidId() { return this.selectedFluidId; }
    public String getSelectedFluidLabel() { return this.selectedFluidLabel; }
    public ItemStack getSelectedFluidPreview() { return this.selectedFluidPreview; }
    public boolean hasSelectedItem() { return !isBlank(this.selectedItemId); }
    public boolean hasSelectedFluid() { return !isBlank(this.selectedFluidId); }
    public boolean isEmptyHandSelected() { return this.emptyHandSelected; }
    public int getPlaceRotateDegrees() { return this.placeRotateSteps * 90; }
    public String getPlacementStatePreset() { return this.placementStatePreset; }

    // =========================================================================
    //  Build shape access
    // =========================================================================

    public BuildShape getBuildShape() { return this.buildShape; }

    public void setBuildShape(BuildShape shape) {
        this.buildShape = shape == null ? BuildShape.BLOCK : shape;
    }

    public void cycleBuildShape(int step) {
        BuildShape[] values = BuildShape.values();
        int index = this.buildShape.ordinal();
        int next = Math.floorMod(index + step, values.length);
        this.buildShape = values[next];
    }

    // =========================================================================
    //  Item selection
    // =========================================================================

    public void selectStorageEntry(int index, List<StorageEntry> entries,
                                   Runnable setModeInteract) {
        if (index < 0 || index >= entries.size()) {
            return;
        }
        StorageEntry entry = entries.get(index);
        setSelectedItem(entry.itemId(), entry.stack().getDisplayName(), entry.stack().copy());
        clearSelectedFluid();
        setModeInteract.run();
    }

    public void selectFluidEntry(int index, List<FluidEntry> entries,
                                 Runnable setModeInteract) {
        if (index < 0 || index >= entries.size()) {
            return;
        }
        FluidEntry entry = entries.get(index);
        setSelectedFluid(entry.fluidId(), entry.label(), entry.preview().copy());
        clearSelectedItemOnly();
        setModeInteract.run();
    }

    public void clearSelectedItem(Runnable setModeInteract) {
        clearPlacementSelectionPreserveMode();
        setModeInteract.run();
    }

    public void clearPlacementSelectionPreserveMode() {
        clearSelectedItemOnly();
        clearSelectedFluid();
        this.emptyHandSelected = false;
        this.placeRotateSteps = 0;
        this.placementStatePreset = "";
        this.placementStateItemId = "";
    }

    public void selectEmptyHand(Runnable setModeInteract) {
        clearSelectedItemOnly();
        clearSelectedFluid();
        this.emptyHandSelected = true;
        this.placeRotateSteps = 0;
        this.placementStatePreset = "";
        this.placementStateItemId = "";
        setModeInteract.run();
    }

    public void selectRecentEntry(int index, List<RecentEntry> entries,
                                  Runnable setModeInteract) {
        if (index < 0 || index >= entries.size()) {
            return;
        }
        RecentEntry entry = entries.get(index);
        if (entry.fluid()) {
            setSelectedFluid(entry.id(), entry.label(), entry.preview().copy());
            clearSelectedItemOnly();
        } else {
            setSelectedItem(entry.id(), entry.label(), entry.preview().copy());
            clearSelectedFluid();
        }
        setModeInteract.run();
    }

    public void selectQuickSlot(int index, String qsItemId, ItemStack qsPreview, String qsLabel,
                                Runnable setModeInteract) {
        if (isBlank(qsItemId)) {
            return;
        }
        ItemStack preview = qsPreview;
        if (com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(preview)) {
            ResourceLocation id = parseResourceLocation(qsItemId);
            Item item = id == null ? null : RtsRegistries.ITEMS.getValue(id);
            if (item == null) {
                return;
            }
            preview = new ItemStack(item);
        }
        String label = qsLabel;
        if (isBlank(label)) {
            label = preview.getDisplayName();
        }
        setSelectedItem(qsItemId, label, preview.copy());
        clearSelectedFluid();
        setModeInteract.run();
    }

    public void selectItemForPlacement(String itemId, String label, ItemStack preview,
                                       Runnable setModeInteract) {
        if (isBlank(itemId) || preview == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(preview)) {
            return;
        }
        ItemStack safePreview = preview.copy();
        safePreview.stackSize = 1;
        setSelectedItem(itemId, isBlank(label) ? safePreview.getDisplayName() : label, safePreview);
        clearSelectedFluid();
        setModeInteract.run();
    }

    // =========================================================================
    //  Placement operations
    // =========================================================================

    public void placeSelected(RayTraceResult hit, boolean forcePlace, Vec3d rayOrigin, Vec3d rayDir,
                              boolean skipIfOccupied, boolean quickBuild,
                              Runnable beginRemoteMenuOpenGrace,
                              BooleanSupplier shouldAutoClearSelectedItemWhenUnavailable,
                              Runnable requestStoragePage,
                              boolean isLocalPlayerCreative, long storageTotalCount, boolean hasStoragePageSnapshot) {
        beginRemoteMenuOpenGrace.run();
        String itemId = this.selectedItemId == null ? "" : this.selectedItemId;
        long selectedCount = getSelectedItemCountForPlacement(itemId, isLocalPlayerCreative, storageTotalCount, hasStoragePageSnapshot);
        boolean autoClearUnavailable = shouldAutoClearSelectedItemWhenUnavailable.getAsBoolean();
        if (!isBlank(itemId) && autoClearUnavailable && selectedCount <= 0L) {
            selectEmptyHandPreserveMode();
            itemId = "";
        }

        String payloadItemId = itemId;
        if (isBlank(payloadItemId)) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null) {
                int slot = MathHelper.clamp(mc.thePlayer.inventory.currentItem, 0, 8);
                ItemStack toolStack = mc.thePlayer.inventory.getStackInSlot(slot);
                if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(toolStack) && toolStack.getItem() instanceof ItemBlock) {
                    ResourceLocation id = RtsRegistries.ITEMS.getKey(toolStack.getItem());
                    if (id != null) {
                        payloadItemId = id.toString();
                    }
                }
            }
        }
        boolean clearAfterPlace = !isBlank(payloadItemId) && autoClearUnavailable && selectedCount <= 1L;
        ItemStack itemPrototype = isBlank(payloadItemId) ? null : this.selectedItemPreview;

        RtsClientPacketGateway.sendPlace(hit, forcePlace, skipIfOccupied, payloadItemId, itemPrototype,
                isBlank(payloadItemId) ? 0 : this.placeRotateSteps,
                isBlank(payloadItemId) ? "" : this.placementStatePreset,
                rayOrigin, rayDir, quickBuild);
        if (clearAfterPlace) {
            selectEmptyHandPreserveMode();
            requestStoragePage.run();
        }
    }

    public void placeSelectedBatch(List<RayTraceResult> hits, RayTraceResult templateHit,
                                   boolean forcePlace, Vec3d rayOrigin, Vec3d rayDir,
                                   boolean skipIfOccupied, boolean overwriteExisting,
                                   Runnable beginRemoteMenuOpenGrace,
                                   BooleanSupplier shouldAutoClearSelectedItemWhenUnavailable,
                                   Runnable requestStoragePage,
                                   boolean isLocalPlayerCreative, long storageTotalCount,
                                   boolean hasStoragePageSnapshot) {
        beginRemoteMenuOpenGrace.run();
        String itemId = this.selectedItemId == null ? "" : this.selectedItemId;
        long selectedCount = getSelectedItemCountForPlacement(itemId, isLocalPlayerCreative, storageTotalCount, hasStoragePageSnapshot);
        boolean autoClearUnavailable = shouldAutoClearSelectedItemWhenUnavailable.getAsBoolean();
        if (!isBlank(itemId) && autoClearUnavailable && selectedCount <= 0L) {
            selectEmptyHandPreserveMode();
            itemId = "";
        }
        int attemptedPlacements = hits == null ? 0 : hits.size();
        boolean clearAfterPlace = !isBlank(itemId)
                && autoClearUnavailable
                && selectedCount <= Math.max(1, attemptedPlacements);

        String payloadItemId = itemId;
        if (isBlank(payloadItemId)) {
            Minecraft mc = Minecraft.getMinecraft();
            if (mc.thePlayer != null) {
                int slot = MathHelper.clamp(mc.thePlayer.inventory.currentItem, 0, 8);
                ItemStack toolStack = mc.thePlayer.inventory.getStackInSlot(slot);
                if (!com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(toolStack) && toolStack.getItem() instanceof ItemBlock) {
                    ResourceLocation id = RtsRegistries.ITEMS.getKey(toolStack.getItem());
                    if (id != null) {
                        payloadItemId = id.toString();
                    }
                }
            }
        }

        RtsClientPacketGateway.sendPlaceBatch(hits, templateHit, forcePlace, skipIfOccupied, overwriteExisting,
                payloadItemId,
                isBlank(payloadItemId) ? null : this.selectedItemPreview,
                isBlank(payloadItemId) ? 0 : this.placeRotateSteps,
                isBlank(payloadItemId) ? "" : this.placementStatePreset,
                rayOrigin, rayDir);
        if (clearAfterPlace) {
            selectEmptyHandPreserveMode();
            requestStoragePage.run();
        }
    }

    public void placeSelectedFluid(RayTraceResult hit, boolean forcePlace, Vec3d rayOrigin, Vec3d rayDir) {
        if (hit == null || isBlank(this.selectedFluidId)) {
            return;
        }
        RtsClientPacketGateway.sendPlaceFluid(hit, forcePlace, this.selectedFluidId, rayOrigin, rayDir);
    }

    // =========================================================================
    //  Fluid storage
    // =========================================================================

    public void storeFluidFromStorageItem(String itemId) {
        if (isBlank(itemId)) return;
        RtsClientPacketGateway.sendStoreFluid(C2SRtsStoreFluidPayload.SOURCE_STORAGE_ITEM, 0, itemId);
    }

    public void storeFluidFromPinnedItem(String itemId) {
        if (isBlank(itemId)) return;
        RtsClientPacketGateway.sendStoreFluid(C2SRtsStoreFluidPayload.SOURCE_PIN_ITEM, 0, itemId);
    }

    public void storeFluidFromToolSlot(int toolSlot) {
        RtsClientPacketGateway.sendStoreFluid(C2SRtsStoreFluidPayload.SOURCE_TOOL_SLOT, toolSlot, "");
    }

    // =========================================================================
    //  Interaction operations
    // =========================================================================

    public void interactEmpty(RayTraceResult hit, Vec3d rayOrigin, Vec3d rayDir,
                              Runnable beginRemoteMenuOpenGrace) {
        if (hit == null) return;
        beginRemoteMenuOpenGrace.run();
        RtsClientPacketGateway.sendInteractBlockEmptyHand(hit, rayOrigin, rayDir);
        RtsClientOnlyBlockGuiCompat.tryOpenAfterAuthoritativeSend(hit);
    }

    public void interactEntityEmpty(int entityId, Vec3d hitLocation, Vec3d rayOrigin, Vec3d rayDir,
                                    Runnable beginRemoteMenuOpenGrace) {
        beginRemoteMenuOpenGrace.run();
        RtsClientPacketGateway.sendInteractEntityEmptyHand(entityId, hitLocation, rayOrigin, rayDir);
    }

    public void interactBlockWithToolSlot(RayTraceResult hit, int toolSlot, Vec3d rayOrigin, Vec3d rayDir,
                                          Runnable beginRemoteMenuOpenGrace) {
        if (hit == null) return;
        beginRemoteMenuOpenGrace.run();
        RtsClientPacketGateway.sendInteractBlockWithToolSlot(hit, toolSlot, rayOrigin, rayDir);
    }

    public void useItemInAirWithToolSlot(RayTraceResult hit, int toolSlot, Vec3d rayOrigin, Vec3d rayDir,
                                         Runnable beginRemoteMenuOpenGrace) {
        if (hit == null) return;
        beginRemoteMenuOpenGrace.run();
        RtsClientPacketGateway.sendUseItemInAirWithToolSlot(hit, toolSlot, rayOrigin, rayDir);
    }

    public void interactBlockWithPinnedItem(RayTraceResult hit, String itemId, Vec3d rayOrigin, Vec3d rayDir,
                                            Runnable beginRemoteMenuOpenGrace) {
        if (hit == null || isBlank(itemId)) return;
        beginRemoteMenuOpenGrace.run();
        RtsClientPacketGateway.sendInteractBlockWithPinnedItem(hit, itemId, rayOrigin, rayDir);
    }

    public void interactEntityWithToolSlot(int entityId, Vec3d hitLocation, int toolSlot, Vec3d rayOrigin, Vec3d rayDir,
                                           Runnable beginRemoteMenuOpenGrace) {
        if (entityId < 0 || hitLocation == null) return;
        beginRemoteMenuOpenGrace.run();
        RtsClientPacketGateway.sendInteractEntityWithToolSlot(entityId, hitLocation, toolSlot, rayOrigin, rayDir);
    }

    public void interactEntityWithPinnedItem(int entityId, Vec3d hitLocation, String itemId, Vec3d rayOrigin, Vec3d rayDir,
                                             Runnable beginRemoteMenuOpenGrace) {
        if (entityId < 0 || hitLocation == null || isBlank(itemId)) return;
        beginRemoteMenuOpenGrace.run();
        RtsClientPacketGateway.sendInteractEntityWithPinnedItem(entityId, hitLocation, itemId, rayOrigin, rayDir);
    }

    // =========================================================================
    //  Break operations
    // =========================================================================

    public void breakPlaced(BlockPos pos, EnumFacing face, boolean allowAdjacentFallback) {
        if (pos == null) return;
        EnumFacing resolvedFace = face == null ? EnumFacing.UP : face;
        RtsClientPacketGateway.sendBreakPlaced(pos, resolvedFace, allowAdjacentFallback);
    }

    // =========================================================================
    //  Rotation
    // =========================================================================

    public void rotateBlock(BlockPos pos) {
        if (pos == null) return;
        RtsClientPacketGateway.sendRotateBlock(pos);
    }

    public void rotateBlockStep(
            BlockPos pos,
            EnumFacing axisDirection,
            int quarterTurns) {
        if (pos != null && axisDirection != null && quarterTurns != 0) {
            RtsClientPacketGateway.sendRotateBlockStep(
                    pos, axisDirection, quarterTurns);
        }
    }

    public void rotatePlacementClockwise() {
        this.placeRotateSteps = (this.placeRotateSteps + 1) & 3;
    }

    public void rotatePlacementCounterClockwise() {
        this.placeRotateSteps = (this.placeRotateSteps + 3) & 3;
    }

    public void setPlacementStateProperty(String propertyName, String valueName) {
        this.placementStatePreset = PlacementStatePreset.withValue(
                this.placementStatePreset, propertyName, valueName);
        this.placementStateItemId = this.selectedItemId;
    }

    public void copyPlacementState(BlockState state) {
        this.placeRotateSteps = 0;
        this.placementStatePreset = PlacementStatePreset.fromBlockState(state);
        Item item = Item.getItemFromBlock(state.getBlock());
        ResourceLocation itemId = item == null ? null : RtsRegistries.ITEMS.getKey(item);
        this.placementStateItemId = itemId == null ? "" : itemId.toString();
    }

    // =========================================================================
    //  Internal helpers
    // =========================================================================

    private void clearSelectedItemOnly() {
        setSelectedItem("", "", null);
    }

    private void clearSelectedFluid() {
        setSelectedFluid("", "", null);
    }

    private void selectEmptyHandPreserveMode() {
        clearSelectedItemOnly();
        clearSelectedFluid();
        this.emptyHandSelected = true;
        this.placeRotateSteps = 0;
        this.placementStatePreset = "";
        this.placementStateItemId = "";
    }

    private long getSelectedItemCountForPlacement(String itemId, boolean isLocalPlayerCreative,
                                                  long storageTotalCount, boolean hasStoragePageSnapshot) {
        if (isBlank(itemId)) return Long.MAX_VALUE;
        if (isLocalPlayerCreative) return Long.MAX_VALUE;
        return hasStoragePageSnapshot ? storageTotalCount : Long.MAX_VALUE;
    }

    private void setSelectedItem(String itemId, String label, ItemStack preview) {
        String nextItemId = itemId == null ? "" : itemId;
        if (!nextItemId.equals(this.selectedItemId)) {
            this.placeRotateSteps = 0;
            // R 轮盘可在“手持方块”状态下先预选，再从 RTS 列表选择同一种物品。
            // 只有真正换成另一种物品时才清除预选，避免选好的上半砖在放置前悄悄丢失。
            if (!nextItemId.equals(this.placementStateItemId)) {
                this.placementStatePreset = "";
                this.placementStateItemId = "";
            }
        }
        this.selectedItemId = nextItemId;
        this.selectedItemLabel = label == null ? "" : label;
        this.selectedItemPreview = preview == null ? null : preview;
        if (!isBlank(this.selectedItemId)) {
            this.emptyHandSelected = false;
        }
    }

    private void setSelectedFluid(String fluidId, String label, ItemStack preview) {
        this.selectedFluidId = fluidId == null ? "" : fluidId;
        this.selectedFluidLabel = label == null ? "" : label;
        this.selectedFluidPreview = preview == null ? null : preview;
        if (!isBlank(this.selectedFluidId)) {
            this.emptyHandSelected = false;
        }
    }

    // =========================================================================
    //  Public helpers for controller callbacks
    // =========================================================================

    /** Refreshes the selected item preview from storage entries */
    public void syncSelectedPreviewFromStorage(List<StorageEntry> entries,
                                               boolean hasStoragePageSnapshot,
                                               long storageTotalCount) {
        if (isBlank(this.selectedItemId)) {
            return;
        }
        for (StorageEntry entry : entries) {
            if (entry != null && this.selectedItemId.equals(entry.itemId())) {
                this.selectedItemPreview = entry.stack().copy();
                this.selectedItemPreview.stackSize = 1;
                return;
            }
        }
        if (shouldAutoClearSelectedItemWhenUnavailable(hasStoragePageSnapshot, storageTotalCount)) {
            selectEmptyHandPreserveMode();
        }
    }

    private boolean shouldAutoClearSelectedItemWhenUnavailable(boolean hasStoragePageSnapshot,
                                                                long storageTotalCount) {
        if (isLocalPlayerCreative()) return false;
        return this.selectedItemPreview != null
                && !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(this.selectedItemPreview)
                && this.selectedItemPreview.getItem() instanceof ItemBlock
                && hasStoragePageSnapshot
                && storageTotalCount <= 0L;
    }

    private static boolean isLocalPlayerCreative() {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft != null && minecraft.thePlayer != null && minecraft.thePlayer.capabilities.isCreativeMode;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static ResourceLocation parseResourceLocation(String value) {
        if (isBlank(value)) return null;
        try {
            return new ResourceLocation(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
