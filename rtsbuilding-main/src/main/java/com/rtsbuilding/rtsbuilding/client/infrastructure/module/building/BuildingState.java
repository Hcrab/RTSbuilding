package com.rtsbuilding.rtsbuilding.client.infrastructure.module.building;

import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import net.minecraft.world.item.ItemStack;

public final class BuildingState {

    
    public String selectedItemId = "";
    public String selectedItemLabel = "";
    public ItemStack selectedItemPreview = ItemStack.EMPTY;
    public String selectedFluidId = "";
    public String selectedFluidLabel = "";
    public ItemStack selectedFluidPreview = ItemStack.EMPTY;
    public boolean emptyHandSelected;
    public int placeRotateSteps;

    
    public BuilderMode currentMode = BuilderMode.INTERACT;
}
