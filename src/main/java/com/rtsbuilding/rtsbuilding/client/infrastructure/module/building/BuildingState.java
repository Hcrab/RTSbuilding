package com.rtsbuilding.rtsbuilding.client.infrastructure.module.building;

import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import net.minecraft.world.item.ItemStack;

/**
 * 建造状态——纯数据容器。
 */
public final class BuildingState {

    // Selection
    public String selectedItemId = "";
    public String selectedItemLabel = "";
    public ItemStack selectedItemPreview = ItemStack.EMPTY;
    public String selectedFluidId = "";
    public String selectedFluidLabel = "";
    public ItemStack selectedFluidPreview = ItemStack.EMPTY;
    public boolean emptyHandSelected;
    public int placeRotateSteps;

    // Builder mode
    public BuilderMode currentMode = BuilderMode.INTERACT;

    // Build shape (int ordinal, resolved via BuildShape)
    public int buildShapeOrdinal;
}
