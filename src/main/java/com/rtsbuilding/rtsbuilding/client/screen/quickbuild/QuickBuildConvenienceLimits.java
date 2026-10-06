package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.Config;
import com.rtsbuilding.rtsbuilding.common.mining.SelectionVolumeLimit;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceParameter;
import com.rtsbuilding.rtsbuilding.uicore.quickbuild.QuickBuildUiConvenienceSettings;

/**
 * 把当前服务器的便捷破坏限制转换成滑条刻度和本次操作的有效参数。
 *
 * <p>纯 UI 设置允许保存较大的跨服务器偏好，不能把它的整数存储容量当成滑条范围。
 * 本类让控件、尺寸文字、预览和提交使用同一份有效值；不修改保存的偏好，也不代替
 * 共享规划器对完整体积、世界高度和实际目标的最终检查。</p>
 */
record QuickBuildConvenienceLimits(SelectionVolumeLimit selection, int treeMaxBlocks) {
    static QuickBuildConvenienceLimits current() {
        return new QuickBuildConvenienceLimits(Config.areaMineSelectionLimit(), Config.maxTreeBlocks());
    }

    int minimum(QuickBuildUiConvenienceParameter parameter) {
        return switch (parameter) {
            case CHUNK_UP, CHUNK_DOWN -> 0;
            default -> 1;
        };
    }

    /** 单轴不可能超过整体积；区块工具的固定横截面是 16×16，延伸高度不包含锚点层。 */
    int maximum(QuickBuildUiConvenienceParameter parameter) {
        return switch (parameter) {
            case SIZE_X -> Math.min(selection.maxSizeX(), selection.maxVolume());
            case SIZE_Y -> Math.min(selection.maxSizeY(), selection.maxVolume());
            case SIZE_Z -> Math.min(selection.maxSizeZ(), selection.maxVolume());
            case CHUNK_UP, CHUNK_DOWN -> Math.max(0,
                    Math.min(selection.maxSizeY(), selection.maxVolume() / 256) - 1);
            case TREE_MAX_BLOCKS -> Math.max(1,
                    Math.min(treeMaxBlocks, QuickBuildUiConvenienceSettings.TREE_MAX));
        };
    }

    int clamp(QuickBuildUiConvenienceParameter parameter, int value) {
        return Math.clamp(value, minimum(parameter), maximum(parameter));
    }

    /** 旧版本保存的异常大数只在当前会话中收敛，合法的大尺寸偏好仍可在上限较高的服务器使用。 */
    QuickBuildUiConvenienceSettings effective(QuickBuildUiConvenienceSettings settings) {
        return new QuickBuildUiConvenienceSettings(
                clamp(QuickBuildUiConvenienceParameter.SIZE_X, settings.sizeX()),
                clamp(QuickBuildUiConvenienceParameter.SIZE_Y, settings.sizeY()),
                clamp(QuickBuildUiConvenienceParameter.SIZE_Z, settings.sizeZ()),
                clamp(QuickBuildUiConvenienceParameter.CHUNK_UP, settings.chunkUp()),
                clamp(QuickBuildUiConvenienceParameter.CHUNK_DOWN, settings.chunkDown()),
                clamp(QuickBuildUiConvenienceParameter.TREE_MAX_BLOCKS, settings.treeMaxBlocks()));
    }
}
