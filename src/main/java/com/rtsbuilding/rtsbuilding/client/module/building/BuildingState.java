package com.rtsbuilding.rtsbuilding.client.module.building;

import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import net.minecraft.world.item.ItemStack;

/**
 * 建造状态——纯数据容器。
 */
public final class BuildingState {

    // Selection
    String selectedItemId = "";
    String selectedItemLabel = "";
    ItemStack selectedItemPreview = ItemStack.EMPTY;
    String selectedFluidId = "";
    String selectedFluidLabel = "";
    ItemStack selectedFluidPreview = ItemStack.EMPTY;
    boolean emptyHandSelected;
    int placeRotateSteps;

    // Builder mode
    BuilderMode currentMode = BuilderMode.INTERACT;

    // Build shape (int ordinal, resolved via BuildShape)
    int buildShapeOrdinal;
}
