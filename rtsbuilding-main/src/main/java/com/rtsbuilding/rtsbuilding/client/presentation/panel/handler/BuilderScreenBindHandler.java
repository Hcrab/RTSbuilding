package com.rtsbuilding.rtsbuilding.client.presentation.panel.handler;

import com.rtsbuilding.rtsbuilding.client.infrastructure.module.building.BuildingModule;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.pass.BoxSelector;
import com.rtsbuilding.rtsbuilding.client.render.util.CursorRaycaster;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;

public final class BuilderScreenBindHandler {

    private final RtsClientKernel kernel;

    public BuilderScreenBindHandler() {
        this.kernel = RtsClientKernel.get();
    }

    

    
    public boolean handleClickModeBind(BuilderScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        var ray = CursorRaycaster.computeCursorRay(mc, screen);
        if (ray == null) return false;
        BlockHitResult hit = ray.raycastBlock(mc);
        if (hit == null) return false;
        if (mc.level == null) return false;
        StorageModule sm = kernel.module(StorageModule.class);
        return sm != null && sm.handleClickModeBind(mc.level, hit.getBlockPos());
    }

    
    public boolean handleClickModeUnbind(BuilderScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        var ray = CursorRaycaster.computeCursorRay(mc, screen);
        if (ray == null) return false;
        BlockHitResult hit = ray.raycastBlock(mc);
        if (hit == null) return false;
        if (mc.level == null) return false;
        StorageModule sm = kernel.module(StorageModule.class);
        return sm != null && sm.handleClickModeUnbind(mc.level, hit.getBlockPos());
    }

    

    
    public boolean confirmBatchBind() {
        var sel = kernel.renderPipeline().boxSelector;
        if (sel.getPhase() != BoxSelector.Phase.COMPLETE) return false;
        BlockPos min = sel.getMinCorner();
        BlockPos max = sel.getMaxCorner();
        if (min == null || max == null) return false;

        BuildingModule bm = kernel.module(BuildingModule.class);
        if (bm == null || bm.getMode() != BuilderMode.INTERACT) return false;

        int linked = batchLinkContainers(min, max);
        if (linked > 0) {
            sel.reset();
            return true;
        }
        return false;
    }

    
    public boolean confirmBatchUnbind() {
        var sel = kernel.renderPipeline().boxSelector;
        if (sel.getPhase() != BoxSelector.Phase.COMPLETE) return false;
        BlockPos min = sel.getMinCorner();
        BlockPos max = sel.getMaxCorner();
        if (min == null || max == null) return false;

        BuildingModule bm = kernel.module(BuildingModule.class);
        if (bm == null || bm.getMode() != BuilderMode.INTERACT) return false;

        int unlinked = batchUnbindContainers(min, max);
        if (unlinked > 0) {
            sel.reset();
            return true;
        }
        return false;
    }

    

    private int batchLinkContainers(BlockPos min, BlockPos max) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0;
        StorageModule sm = kernel.module(StorageModule.class);
        return sm != null ? sm.batchLinkContainers(mc.level, min, max) : 0;
    }

    private int batchUnbindContainers(BlockPos min, BlockPos max) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 0;
        StorageModule sm = kernel.module(StorageModule.class);
        return sm != null ? sm.batchUnbindContainers(mc.level, min, max) : 0;
    }
}
