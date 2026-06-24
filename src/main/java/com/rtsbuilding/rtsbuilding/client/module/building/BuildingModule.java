package com.rtsbuilding.rtsbuilding.client.module.building;

import com.rtsbuilding.rtsbuilding.client.kernel.FeatureModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.kernel.StateEvent;
import com.rtsbuilding.rtsbuilding.client.network.RtsClientPacketGateway;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 建造模块——管理物品/流体选中、放置操作、形状模式。
 *
 * <p>替代原 {@code BuildPlacementService} + {@code ClientRtsController} 中建造相关逻辑。</p>
 */
public final class BuildingModule implements FeatureModule {

    private final BuildingState state = new BuildingState();

    @Override
    public String moduleId() {
        return "building";
    }

    @Override
    public void onSessionEvent(StateEvent event) {
        if (event instanceof StateEvent.RtsToggled e) {
            if (!e.enabled()) clearSelection();
        } else if (event instanceof StateEvent.BuilderModeChanged e) {
            state.currentMode = e.mode();
        } else if (event instanceof StateEvent.PlayerDied) {
            clearSelection();
        }
    }

    // ======================================================================
    //  Selection
    // ======================================================================

    public void selectItem(String itemId, String label, ItemStack preview) {
        if (itemId == null || itemId.isBlank() || preview == null || preview.isEmpty()) return;
        state.selectedItemId = itemId;
        state.selectedItemLabel = label == null ? "" : label;
        state.selectedItemPreview = preview.copyWithCount(1);
        state.selectedFluidId = "";
        state.selectedFluidPreview = ItemStack.EMPTY;
        state.emptyHandSelected = false;
        kernel().dispatch(new StateEvent.ItemSelected(itemId, label));
    }

    public void selectFluid(String fluidId, String label, ItemStack preview) {
        state.selectedFluidId = fluidId == null ? "" : fluidId;
        state.selectedFluidLabel = label == null ? "" : label;
        state.selectedFluidPreview = preview == null ? ItemStack.EMPTY : preview.copyWithCount(1);
        state.selectedItemId = "";
        state.selectedItemPreview = ItemStack.EMPTY;
        state.emptyHandSelected = false;
    }

    public void clearSelection() {
        state.selectedItemId = "";
        state.selectedItemLabel = "";
        state.selectedItemPreview = ItemStack.EMPTY;
        state.selectedFluidId = "";
        state.selectedFluidLabel = "";
        state.selectedFluidPreview = ItemStack.EMPTY;
        state.emptyHandSelected = false;
        state.placeRotateSteps = 0;
    }

    public void selectEmptyHand() {
        clearSelection();
        state.emptyHandSelected = true;
    }

    // ======================================================================
    //  Placement
    // ======================================================================

    public void placeSelected(BlockHitResult hit, boolean forcePlace, Vec3 origin, Vec3 dir) {
        if (hit == null) return;
        RtsClientPacketGateway.sendPlace(hit, forcePlace, false,
                state.selectedItemId, state.selectedItemPreview,
                state.placeRotateSteps, origin, dir);
    }

    public void placeFluid(BlockHitResult hit, boolean forcePlace, Vec3 origin, Vec3 dir) {
        if (hit == null || state.selectedFluidId.isBlank()) return;
        RtsClientPacketGateway.sendPlaceFluid(hit, forcePlace, state.selectedFluidId, origin, dir);
    }

    // ======================================================================
    //  Rotation
    // ======================================================================

    public void rotateClockwise() {
        state.placeRotateSteps = (state.placeRotateSteps + 1) & 3;
    }

    public void rotateCounterClockwise() {
        state.placeRotateSteps = (state.placeRotateSteps + 3) & 3;
    }

    public void rotateBlock(BlockPos pos) {
        if (pos != null) RtsClientPacketGateway.sendRotateBlock(pos);
    }

    // ======================================================================
    //  State accessors
    // ======================================================================

    public BuildingState getState() { return this.state; }
    public boolean hasSelectedItem() { return !state.selectedItemId.isBlank(); }
    public boolean hasSelectedFluid() { return !state.selectedFluidId.isBlank(); }
    public boolean isEmptyHandSelected() { return state.emptyHandSelected; }
    public String getSelectedItemId() { return state.selectedItemId; }
    public String getSelectedItemLabel() { return state.selectedItemLabel; }
    public ItemStack getSelectedItemPreview() { return state.selectedItemPreview; }
    public String getSelectedFluidId() { return state.selectedFluidId; }
    public ItemStack getSelectedFluidPreview() { return state.selectedFluidPreview; }
    public int getPlaceRotateDegrees() { return state.placeRotateSteps * 90; }
    public BuilderMode getMode() { return state.currentMode; }
    public void setMode(BuilderMode mode) {
        state.currentMode = mode;
        RtsClientPacketGateway.sendSetMode(mode);
        kernel().dispatch(new StateEvent.BuilderModeChanged(mode));
    }

    // ======================================================================
    //  Convenience
    // ======================================================================

    private RtsClientKernel kernel() {
        return RtsClientKernel.get();
    }
}
