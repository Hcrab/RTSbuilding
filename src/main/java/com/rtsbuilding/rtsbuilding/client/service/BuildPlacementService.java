package com.rtsbuilding.rtsbuilding.client.service;

import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.client.record.FluidEntry;
import com.rtsbuilding.rtsbuilding.client.record.RecentEntry;
import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import com.rtsbuilding.rtsbuilding.client.screen.quickbuild.BuildShape;
import com.rtsbuilding.rtsbuilding.network.builder.C2SRtsStoreFluidPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.function.BooleanSupplier;

public final class BuildPlacementService {

    // =========================================================================
    //  Placement item state
    // =========================================================================

    private String selectedItemId = "";
    private String selectedItemLabel = "";
    private ItemStack selectedItemPreview = ItemStack.EMPTY;
    private String selectedFluidId = "";
    private String selectedFluidLabel = "";
    private ItemStack selectedFluidPreview = ItemStack.EMPTY;
    private boolean emptyHandSelected = false;
    private int placeRotateSteps;

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
    public boolean hasSelectedItem() { return !this.selectedItemId.isBlank(); }
    public boolean hasSelectedFluid() { return !this.selectedFluidId.isBlank(); }
    public boolean isEmptyHandSelected() { return this.emptyHandSelected; }
    public int getPlaceRotateDegrees() { return this.placeRotateSteps * 90; }

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
        setSelectedItem(entry.itemId(), entry.stack().getHoverName().getString(), entry.stack().copy());
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
    }

    public void selectEmptyHand(Runnable setModeInteract) {
        clearSelectedItemOnly();
        clearSelectedFluid();
        this.emptyHandSelected = true;
        this.placeRotateSteps = 0;
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
        if (qsItemId == null || qsItemId.isBlank()) {
            return;
        }
        ItemStack preview = qsPreview;
        if (preview.isEmpty()) {
            ResourceLocation id = ResourceLocation.tryParse(qsItemId);
            if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
                return;
            }
            preview = new ItemStack(BuiltInRegistries.ITEM.get(id));
        }
        String label = qsLabel;
        if (label == null || label.isBlank()) {
            label = preview.getHoverName().getString();
        }
        setSelectedItem(qsItemId, label, preview.copy());
        clearSelectedFluid();
        setModeInteract.run();
    }

    public void selectItemForPlacement(String itemId, String label, ItemStack preview,
                                       Runnable setModeInteract) {
        if (itemId == null || itemId.isBlank() || preview == null || preview.isEmpty()) {
            return;
        }
        ItemStack safePreview = preview.copy();
        safePreview.setCount(1);
        setSelectedItem(itemId, label == null || label.isBlank() ? safePreview.getHoverName().getString() : label, safePreview);
        clearSelectedFluid();
        setModeInteract.run();
    }

    // =========================================================================
    //  Placement operations
    // =========================================================================

    public void placeSelected(BlockHitResult hit, boolean forcePlace, Vec3 rayOrigin, Vec3 rayDir,
                              boolean skipIfOccupied, boolean quickBuild,
                              Runnable beginRemoteMenuOpenGrace,
                              BooleanSupplier shouldAutoClearSelectedItemWhenUnavailable,
                              Runnable requestStoragePage,
                              boolean isLocalPlayerCreative, long storageTotalCount, boolean hasStoragePageSnapshot) {
        beginRemoteMenuOpenGrace.run();
        String itemId = this.selectedItemId == null ? "" : this.selectedItemId;
        long selectedCount = getSelectedItemCountForPlacement(itemId, isLocalPlayerCreative, storageTotalCount, hasStoragePageSnapshot);
        boolean autoClearUnavailable = shouldAutoClearSelectedItemWhenUnavailable.getAsBoolean();
        if (!itemId.isBlank() && autoClearUnavailable && selectedCount <= 0L) {
            selectEmptyHandPreserveMode();
            itemId = "";
        }

        String payloadItemId = itemId;
        if (payloadItemId.isBlank()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                int slot = Mth.clamp(mc.player.getInventory().selected, 0, 8);
                ItemStack toolStack = mc.player.getInventory().getItem(slot);
                if (!toolStack.isEmpty() && toolStack.getItem() instanceof BlockItem) {
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(toolStack.getItem());
                    if (id != null) {
                        payloadItemId = id.toString();
                    }
                }
            }
        }
        boolean clearAfterPlace = !payloadItemId.isBlank() && autoClearUnavailable && selectedCount <= 1L;
        ItemStack itemPrototype = payloadItemId.isBlank() ? ItemStack.EMPTY : this.selectedItemPreview;

        RtsClientPacketGateway.sendPlace(hit, forcePlace, skipIfOccupied, payloadItemId, itemPrototype,
                payloadItemId.isBlank() ? 0 : this.placeRotateSteps, rayOrigin, rayDir, quickBuild);
        if (clearAfterPlace) {
            selectEmptyHandPreserveMode();
            requestStoragePage.run();
        }
    }

    public void placeSelectedBatch(List<BlockHitResult> hits, BlockHitResult templateHit,
                                   boolean forcePlace, Vec3 rayOrigin, Vec3 rayDir,
                                   boolean skipIfOccupied,
                                   Runnable beginRemoteMenuOpenGrace,
                                   BooleanSupplier shouldAutoClearSelectedItemWhenUnavailable,
                                   Runnable requestStoragePage,
                                   boolean isLocalPlayerCreative, long storageTotalCount,
                                   boolean hasStoragePageSnapshot) {
        beginRemoteMenuOpenGrace.run();
        String itemId = this.selectedItemId == null ? "" : this.selectedItemId;
        long selectedCount = getSelectedItemCountForPlacement(itemId, isLocalPlayerCreative, storageTotalCount, hasStoragePageSnapshot);
        boolean autoClearUnavailable = shouldAutoClearSelectedItemWhenUnavailable.getAsBoolean();
        if (!itemId.isBlank() && autoClearUnavailable && selectedCount <= 0L) {
            selectEmptyHandPreserveMode();
            itemId = "";
        }
        int attemptedPlacements = hits == null ? 0 : hits.size();
        boolean clearAfterPlace = !itemId.isBlank()
                && autoClearUnavailable
                && selectedCount <= Math.max(1, attemptedPlacements);

        String payloadItemId = itemId;
        if (payloadItemId.isBlank()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                int slot = Mth.clamp(mc.player.getInventory().selected, 0, 8);
                ItemStack toolStack = mc.player.getInventory().getItem(slot);
                if (!toolStack.isEmpty() && toolStack.getItem() instanceof BlockItem) {
                    ResourceLocation id = BuiltInRegistries.ITEM.getKey(toolStack.getItem());
                    if (id != null) {
                        payloadItemId = id.toString();
                    }
                }
            }
        }

        RtsClientPacketGateway.sendPlaceBatch(hits, templateHit, forcePlace, skipIfOccupied,
                payloadItemId,
                payloadItemId.isBlank() ? ItemStack.EMPTY : this.selectedItemPreview,
                payloadItemId.isBlank() ? 0 : this.placeRotateSteps, rayOrigin, rayDir);
        if (clearAfterPlace) {
            selectEmptyHandPreserveMode();
            requestStoragePage.run();
        }
    }

    public void placeSelectedFluid(BlockHitResult hit, boolean forcePlace, Vec3 rayOrigin, Vec3 rayDir) {
        if (hit == null || this.selectedFluidId.isBlank()) {
            return;
        }
        RtsClientPacketGateway.sendPlaceFluid(hit, forcePlace, this.selectedFluidId, rayOrigin, rayDir);
    }

    // =========================================================================
    //  Fluid storage
    // =========================================================================

    public void storeFluidFromStorageItem(String itemId) {
        if (itemId == null || itemId.isBlank()) return;
        RtsClientPacketGateway.sendStoreFluid(C2SRtsStoreFluidPayload.SOURCE_STORAGE_ITEM, 0, itemId);
    }

    public void storeFluidFromPinnedItem(String itemId) {
        if (itemId == null || itemId.isBlank()) return;
        RtsClientPacketGateway.sendStoreFluid(C2SRtsStoreFluidPayload.SOURCE_PIN_ITEM, 0, itemId);
    }

    public void storeFluidFromToolSlot(int toolSlot) {
        RtsClientPacketGateway.sendStoreFluid(C2SRtsStoreFluidPayload.SOURCE_TOOL_SLOT, toolSlot, "");
    }

    // =========================================================================
    //  Interaction operations
    // =========================================================================

    public void interactEmpty(BlockHitResult hit, Vec3 rayOrigin, Vec3 rayDir,
                              Runnable beginRemoteMenuOpenGrace) {
        beginRemoteMenuOpenGrace.run();
        RtsClientPacketGateway.sendEmptyHandPlace(hit, rayOrigin, rayDir);
    }

    public void interactEntityEmpty(int entityId, Vec3 hitLocation, Vec3 rayOrigin, Vec3 rayDir,
                                    Runnable beginRemoteMenuOpenGrace) {
        beginRemoteMenuOpenGrace.run();
        RtsClientPacketGateway.sendInteractEntityEmptyHand(entityId, hitLocation, rayOrigin, rayDir);
    }

    public void interactBlockWithToolSlot(BlockHitResult hit, int toolSlot, Vec3 rayOrigin, Vec3 rayDir,
                                          Runnable beginRemoteMenuOpenGrace) {
        if (hit == null) return;
        beginRemoteMenuOpenGrace.run();
        RtsClientPacketGateway.sendInteractBlockWithToolSlot(hit, toolSlot, rayOrigin, rayDir);
    }

    public void useItemInAirWithToolSlot(BlockHitResult hit, int toolSlot, Vec3 rayOrigin, Vec3 rayDir,
                                         Runnable beginRemoteMenuOpenGrace) {
        if (hit == null) return;
        beginRemoteMenuOpenGrace.run();
        RtsClientPacketGateway.sendUseItemInAirWithToolSlot(hit, toolSlot, rayOrigin, rayDir);
    }

    public void interactBlockWithPinnedItem(BlockHitResult hit, String itemId, Vec3 rayOrigin, Vec3 rayDir,
                                            Runnable beginRemoteMenuOpenGrace) {
        if (hit == null || itemId == null || itemId.isBlank()) return;
        beginRemoteMenuOpenGrace.run();
        RtsClientPacketGateway.sendInteractBlockWithPinnedItem(hit, itemId, rayOrigin, rayDir);
    }

    public void interactEntityWithToolSlot(int entityId, Vec3 hitLocation, int toolSlot, Vec3 rayOrigin, Vec3 rayDir,
                                           Runnable beginRemoteMenuOpenGrace) {
        if (entityId < 0 || hitLocation == null) return;
        beginRemoteMenuOpenGrace.run();
        RtsClientPacketGateway.sendInteractEntityWithToolSlot(entityId, hitLocation, toolSlot, rayOrigin, rayDir);
    }

    public void interactEntityWithPinnedItem(int entityId, Vec3 hitLocation, String itemId, Vec3 rayOrigin, Vec3 rayDir,
                                             Runnable beginRemoteMenuOpenGrace) {
        if (entityId < 0 || hitLocation == null || itemId == null || itemId.isBlank()) return;
        beginRemoteMenuOpenGrace.run();
        RtsClientPacketGateway.sendInteractEntityWithPinnedItem(entityId, hitLocation, itemId, rayOrigin, rayDir);
    }

    // =========================================================================
    //  Break operations
    // =========================================================================

    public void breakPlaced(BlockPos pos, Direction face, boolean allowAdjacentFallback) {
        if (pos == null) return;
        Direction resolvedFace = face == null ? Direction.UP : face;
        RtsClientPacketGateway.sendBreakPlaced(pos, resolvedFace, allowAdjacentFallback);
    }

    // =========================================================================
    //  Rotation
    // =========================================================================

    public void rotateBlock(BlockPos pos) {
        if (pos == null) return;
        RtsClientPacketGateway.sendRotateBlock(pos);
    }

    public void rotatePlacementClockwise() {
        this.placeRotateSteps = (this.placeRotateSteps + 1) & 3;
    }

    public void rotatePlacementCounterClockwise() {
        this.placeRotateSteps = (this.placeRotateSteps + 3) & 3;
    }

    // =========================================================================
    //  Internal helpers
    // =========================================================================

    private void clearSelectedItemOnly() {
        setSelectedItem("", "", ItemStack.EMPTY);
    }

    private void clearSelectedFluid() {
        setSelectedFluid("", "", ItemStack.EMPTY);
    }

    private void selectEmptyHandPreserveMode() {
        clearSelectedItemOnly();
        clearSelectedFluid();
        this.emptyHandSelected = true;
        this.placeRotateSteps = 0;
    }

    private long getSelectedItemCountForPlacement(String itemId, boolean isLocalPlayerCreative,
                                                  long storageTotalCount, boolean hasStoragePageSnapshot) {
        if (itemId == null || itemId.isBlank()) return Long.MAX_VALUE;
        if (isLocalPlayerCreative) return Long.MAX_VALUE;
        return hasStoragePageSnapshot ? storageTotalCount : Long.MAX_VALUE;
    }

    private void setSelectedItem(String itemId, String label, ItemStack preview) {
        this.selectedItemId = itemId == null ? "" : itemId;
        this.selectedItemLabel = label == null ? "" : label;
        this.selectedItemPreview = preview == null ? ItemStack.EMPTY : preview;
        if (!this.selectedItemId.isBlank()) {
            this.emptyHandSelected = false;
        }
    }

    private void setSelectedFluid(String fluidId, String label, ItemStack preview) {
        this.selectedFluidId = fluidId == null ? "" : fluidId;
        this.selectedFluidLabel = label == null ? "" : label;
        this.selectedFluidPreview = preview == null ? ItemStack.EMPTY : preview;
        if (!this.selectedFluidId.isBlank()) {
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
        if (this.selectedItemId == null || this.selectedItemId.isBlank()) {
            return;
        }
        for (StorageEntry entry : entries) {
            if (entry != null && this.selectedItemId.equals(entry.itemId())) {
                this.selectedItemPreview = entry.stack().copy();
                this.selectedItemPreview.setCount(1);
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
                && !this.selectedItemPreview.isEmpty()
                && this.selectedItemPreview.getItem() instanceof BlockItem
                && hasStoragePageSnapshot
                && storageTotalCount <= 0L;
    }

    private static boolean isLocalPlayerCreative() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.player != null && minecraft.player.isCreative();
    }
}
