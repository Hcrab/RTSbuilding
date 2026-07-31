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
import net.minecraft.world.phys.BlockHitResult;

import java.util.List;

/**
 * Quick Build 便捷破坏页的客户端 owner。
 *
 * <p>本类拥有页签/工具/参数偏好、短时世界预览缓存和声明式提交组装；它明确不拥有窗口
 * 绘制、Minecraft 控件、服务端扫描、工具借用或实际破坏。抽离后 QuickBuildPanel 仍只做
 * 窗口生命周期与 Core action 接线，后续增加便捷工具也不会把扫描状态塞回面板大类。</p>
 */
final class QuickBuildConvenienceController {
    private final QuickBuildPreferenceState preferences;
    private final RtsDestroyPreviewPlanner previewPlanner = new RtsDestroyPreviewPlanner();
    private BuilderScreen screen;
    private ClientRtsController controller;

    QuickBuildConvenienceController(QuickBuildPreferenceState preferences) {
        this.preferences = preferences;
    }

    void init(BuilderScreen screen, ClientRtsController controller) {
        this.screen = screen;
        this.controller = controller;
    }

    QuickBuildUiCatalogPage page(boolean destroyMode) {
        return destroyMode ? preferences.catalogPage() : QuickBuildUiCatalogPage.SHAPES;
    }

    QuickBuildUiConvenienceTool tool() {
        return preferences.convenienceTool();
    }

    QuickBuildUiConvenienceSettings settings() {
        return preferences.convenienceSettings();
    }

    void setPage(QuickBuildUiCatalogPage page, boolean destroyMode) {
        preferences.catalogPage(!destroyMode || page == null
                ? QuickBuildUiCatalogPage.SHAPES : page);
        previewPlanner.invalidate();
    }

    void setTool(QuickBuildUiConvenienceTool tool) {
        preferences.convenienceTool(tool);
        preferences.catalogPage(QuickBuildUiCatalogPage.CONVENIENCE_TOOLS);
        previewPlanner.invalidate();
    }

    void setParameter(QuickBuildUiConvenienceParameter parameter, int value) {
        preferences.convenienceParameter(parameter, value);
        previewPlanner.invalidate();
    }

    boolean isActive(boolean destroyMode) {
        return destroyMode
                && preferences.catalogPage() == QuickBuildUiCatalogPage.CONVENIENCE_TOOLS;
    }

    RtsConvenienceDestroyPlanner.Plan preview(boolean destroyMode) {
        if (!isActive(destroyMode) || screen == null) {
            return invalidPlan();
        }
        return previewPlanner.preview(
                screen.getMinecraft(), commonMode(), screen.pickBlockHit(), commonSettings());
    }

    ShapeDataRecords.GhostPreview ghostPreview(boolean destroyMode) {
        RtsConvenienceDestroyPlanner.Plan plan = preview(destroyMode);
        if (plan.targets().isEmpty()) {
            return ShapeDataRecords.GhostPreview.EMPTY;
        }
        List<net.minecraft.core.BlockPos> bounded = screen.filterToBounds(plan.targets());
        return bounded == null || bounded.isEmpty()
                ? ShapeDataRecords.GhostPreview.EMPTY
                : new ShapeDataRecords.GhostPreview(bounded, true, true, List.of());
    }

    boolean submit(boolean destroyMode, BlockHitResult hit, int toolSlot) {
        if (!isActive(destroyMode) || hit == null || controller == null) {
            return false;
        }
        RtsConvenienceDestroyPlanner.Plan preview = previewPlanner.preview(
                screen.getMinecraft(), commonMode(), hit, commonSettings());
        if (!preview.ready()) {
            return true;
        }
        controller.confirmConvenienceDestroy(commonMode(), hit, commonSettings(), toolSlot);
        previewPlanner.invalidate();
        return true;
    }

    String dimensionLabel() {
        QuickBuildUiConvenienceSettings settings = preferences.convenienceSettings();
        return switch (preferences.convenienceTool()) {
            case REPEAT_BOX -> settings.sizeX() + "×" + settings.sizeY() + "×" + settings.sizeZ();
            case CHUNK_QUARRY -> "16×" + (settings.chunkUp() + settings.chunkDown() + 1) + "×16";
            case TREE_FELL -> "≤ " + settings.treeMaxBlocks();
        };
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
                && preferences.convenienceTool() == QuickBuildUiConvenienceTool.TREE_FELL) {
            return "screen.rtsbuilding.quick_build.convenience.tree_invalid";
        }
        return "screen.rtsbuilding.quick_build.convenience.hint";
    }

    void invalidate() {
        previewPlanner.invalidate();
    }

    private RtsConvenienceDestroyMode commonMode() {
        return RtsConvenienceDestroyMode.valueOf(preferences.convenienceTool().name());
    }

    private RtsConvenienceDestroySettings commonSettings() {
        QuickBuildUiConvenienceSettings settings = preferences.convenienceSettings();
        return new RtsConvenienceDestroySettings(
                settings.sizeX(), settings.sizeY(), settings.sizeZ(),
                settings.chunkUp(), settings.chunkDown(), settings.treeMaxBlocks());
    }

    private static RtsConvenienceDestroyPlanner.Plan invalidPlan() {
        return new RtsConvenienceDestroyPlanner.Plan(
                RtsConvenienceDestroyPlanner.ResultCode.INVALID_TARGET, List.of(), 0);
    }
}
