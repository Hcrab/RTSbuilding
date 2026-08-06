package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.shape.ShapeDataRecords;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.service.destruction.RtsDestroyPreviewPlanner;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroyMode;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroyPlanner;
import com.rtsbuilding.rtsbuilding.common.destruction.RtsConvenienceDestroySettings;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiCatalogPage;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceParameter;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceSettings;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceTool;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;

import java.util.Collections;

/**
 * Quick Build 便利破坏目录的客户端 owner。
 *
 * <p>它负责工具/参数偏好、短时预览缓存与确认后的骨架视觉状态；不拥有窗口绘制、Minecraft 控件、
 * 服务端扫描或实际破坏。确认仍将既有预览目标交给 1.12 的 AREA_DESTROY 客户端入口，绝不引入
 * 第二条服务端业务链。</p>
 */
final class QuickBuildConvenienceController {
    private final QuickBuildPreferenceState preferences;
    private final RtsDestroyPreviewPlanner previewPlanner = new RtsDestroyPreviewPlanner();
    private BuilderScreen screen;
    private ClientRtsController controller;
    private RtsConvenienceDestroyPlanner.Plan confirmedPlan;
    private long confirmedAt;

    QuickBuildConvenienceController(QuickBuildPreferenceState preferences) {
        this.preferences = preferences;
    }

    void init(BuilderScreen screen, ClientRtsController controller) {
        this.screen = screen;
        this.controller = controller;
    }

    QuickBuildUiCatalogPage page(boolean destroyMode) {
        return destroyMode ? this.preferences.catalogPage() : QuickBuildUiCatalogPage.SHAPES;
    }

    QuickBuildUiConvenienceTool tool() {
        return this.preferences.convenienceTool();
    }

    QuickBuildUiConvenienceSettings settings() {
        return this.preferences.convenienceSettings();
    }

    void setPage(QuickBuildUiCatalogPage page, boolean destroyMode) {
        this.preferences.catalogPage(!destroyMode || page == null
                ? QuickBuildUiCatalogPage.SHAPES : page);
        clearConfirmedPlan();
        this.previewPlanner.invalidate();
    }

    void setTool(QuickBuildUiConvenienceTool tool) {
        this.preferences.convenienceTool(tool);
        this.preferences.catalogPage(QuickBuildUiCatalogPage.CONVENIENCE_TOOLS);
        clearConfirmedPlan();
        this.previewPlanner.invalidate();
    }

    void setParameter(QuickBuildUiConvenienceParameter parameter, int value) {
        this.preferences.convenienceParameter(parameter, value);
        clearConfirmedPlan();
        this.previewPlanner.invalidate();
    }

    boolean isActive(boolean destroyMode) {
        return destroyMode
                && this.preferences.catalogPage() == QuickBuildUiCatalogPage.CONVENIENCE_TOOLS;
    }

    RtsConvenienceDestroyPlanner.Plan preview(boolean destroyMode) {
        if (!isActive(destroyMode) || this.screen == null) {
            return invalidPlan();
        }
        return this.previewPlanner.preview(Minecraft.getMinecraft(), commonMode(),
                this.screen.pickBlockHit(), commonSettings());
    }

    ShapeDataRecords.GhostPreview ghostPreview(boolean destroyMode) {
        if (!isActive(destroyMode)) {
            return ShapeDataRecords.GhostPreview.EMPTY;
        }
        RtsConvenienceDestroyPlanner.Plan confirmed = activeConfirmedPlan();
        return confirmed == null ? ghost(preview(true), false) : ghost(confirmed, true);
    }

    /**
     * 保留 1.12 已有的确认入口：客户端只提交已预览的目标列表，服务端职责不在本类扩展。
     */
    boolean submit(boolean destroyMode, RayTraceResult hit) {
        if (!isActive(destroyMode) || this.controller == null || hit == null) {
            return false;
        }
        RtsConvenienceDestroyPlanner.Plan plan = this.previewPlanner.preview(
                Minecraft.getMinecraft(), commonMode(), hit, commonSettings());
        if (!plan.ready() || plan.targets().isEmpty()) {
            return true;
        }
        this.confirmedPlan = plan;
        this.confirmedAt = System.currentTimeMillis();
        this.controller.confirmShapeAreaDestroy(plan.targets(),
                this.screen == null ? 0 : this.screen.getSelectedToolSlot());
        this.previewPlanner.invalidate();
        return true;
    }

    String dimensionLabel() {
        QuickBuildUiConvenienceSettings settings = this.preferences.convenienceSettings();
        switch (this.preferences.convenienceTool()) {
            case CHUNK_QUARRY:
                return "16x" + (settings.chunkUp() + settings.chunkDown() + 1) + "x16";
            case TREE_FELL:
                return "<=" + settings.treeMaxBlocks();
            case REPEAT_BOX:
            default:
                return settings.sizeX() + "x" + settings.sizeY() + "x" + settings.sizeZ();
        }
    }

    String hintKey(boolean destroyMode) {
        RtsConvenienceDestroyPlanner.ResultCode code = preview(destroyMode).code();
        if (code == RtsConvenienceDestroyPlanner.ResultCode.OVER_LIMIT) {
            return "screen.rtsbuilding.quick_build.convenience.over_limit";
        }
        if (code == RtsConvenienceDestroyPlanner.ResultCode.UNLOADED_CHUNK) {
            return "screen.rtsbuilding.quick_build.convenience.unloaded";
        }
        if (code == RtsConvenienceDestroyPlanner.ResultCode.INVALID_TARGET
                && this.preferences.convenienceTool() == QuickBuildUiConvenienceTool.TREE_FELL) {
            return "screen.rtsbuilding.quick_build.convenience.tree_invalid";
        }
        return "screen.rtsbuilding.quick_build.convenience.hint";
    }

    void invalidate() {
        clearConfirmedPlan();
        this.previewPlanner.invalidate();
    }

    private RtsConvenienceDestroyMode commonMode() {
        return RtsConvenienceDestroyMode.valueOf(this.preferences.convenienceTool().name());
    }

    private RtsConvenienceDestroySettings commonSettings() {
        QuickBuildUiConvenienceSettings settings = this.preferences.convenienceSettings();
        return new RtsConvenienceDestroySettings(
                settings.sizeX(), settings.sizeY(), settings.sizeZ(),
                settings.chunkUp(), settings.chunkDown(), settings.treeMaxBlocks());
    }

    private RtsConvenienceDestroyPlanner.Plan activeConfirmedPlan() {
        if (this.confirmedPlan == null) {
            return null;
        }
        boolean working = this.controller != null
                && this.controller.findActiveDestroyWorkflow() != null;
        if (!working && System.currentTimeMillis() - this.confirmedAt > 1500L) {
            clearConfirmedPlan();
            return null;
        }
        return this.confirmedPlan;
    }

    private void clearConfirmedPlan() {
        this.confirmedPlan = null;
        this.confirmedAt = 0L;
    }

    private static ShapeDataRecords.GhostPreview ghost(
            RtsConvenienceDestroyPlanner.Plan plan, boolean confirmed) {
        if (plan == null || plan.targets().isEmpty()) {
            return ShapeDataRecords.GhostPreview.EMPTY;
        }
        return new ShapeDataRecords.GhostPreview(plan.targets(), true, true,
                Collections.<BlockPos>emptyList(), false, confirmed);
    }

    private static RtsConvenienceDestroyPlanner.Plan invalidPlan() {
        return new RtsConvenienceDestroyPlanner.Plan(
                RtsConvenienceDestroyPlanner.ResultCode.INVALID_TARGET,
                Collections.<BlockPos>emptyList(), 0);
    }
}
