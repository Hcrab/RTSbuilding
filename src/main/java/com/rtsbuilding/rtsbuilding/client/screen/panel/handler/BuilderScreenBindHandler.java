package com.rtsbuilding.rtsbuilding.client.screen.panel.handler;

import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.building.BuildingModule;
import com.rtsbuilding.rtsbuilding.client.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.render.pass.BoxSelector;
import com.rtsbuilding.rtsbuilding.client.render.util.CursorRaycaster;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 容器绑定交互处理器——处理存储绑定相关的鼠标/键盘交互。
 *
 * <p>从 {@link BuilderScreen} 提取，职责包括：</p>
 * <ul>
 *   <li>点击模式单点绑定/解绑（含射线检测）</li>
 *   <li>框选模式批量绑定/解绑</li>
 *   <li>绑定确认后的框选状态清除</li>
 * </ul>
 */
public final class BuilderScreenBindHandler {

    private final RtsClientKernel kernel;

    public BuilderScreenBindHandler() {
        this.kernel = RtsClientKernel.get();
    }

    // ======================== 单点绑定/解绑 ========================

    /**
     * 点击模式单点绑定（含模式循环）。
     * 未绑定 → 双向链接；已绑定 → 循环切换双向/仅提取。
     */
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

    /**
     * 点击模式单点解绑——左键点击已链接容器时解除存储链接。
     */
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

    // ======================== 批量绑定/解绑 ========================

    /**
     * 确认框选批量绑定——检查框选是否已完成且处于绑定模式，
     * 若是则执行批量链接并清除框选状态。
     *
     * @return true 表示已执行批量绑定并清除框选
     */
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

    /**
     * 确认框选批量解绑——框选完成 + 绑定模式下，左键批量解绑选区内全部已链接容器。
     *
     * @return true 表示已执行批量解绑并清除框选
     */
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

    // ======================== 内部委托 ========================

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
