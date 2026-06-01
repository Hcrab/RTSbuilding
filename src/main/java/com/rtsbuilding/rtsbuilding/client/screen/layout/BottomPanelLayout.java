package com.rtsbuilding.rtsbuilding.client.screen.layout;

import static com.rtsbuilding.rtsbuilding.client.screen.BuilderScreenConstants.BOTTOM_PANEL_HEADER_H;

/**
 * 底部面板布局参数。
 *
 * @param panelX      面板 X 坐标
 * @param panelY      面板 Y 坐标
 * @param panelW      面板宽度
 * @param panelH      面板高度
 * @param sortX       排序按钮 X 坐标
 * @param sortY       排序按钮 Y 坐标
 * @param categoryX   分类面板 X 坐标
 * @param categoryY   分类面板 Y 坐标
 * @param categoryH   分类面板高度
 * @param storageX    存储区域 X 坐标
 * @param storageY    存储区域 Y 坐标
 * @param storageW    存储区域宽度
 * @param craftPanelX      合成面板 X 坐标
 * @param mainStorageW     主存储区域宽度
 * @param searchW          搜索框宽度
 * @param pagerX           分页器 X 坐标
 * @param toolY            工具行 Y 坐标
 * @param gridY            网格 Y 坐标
 * @param gridH            网格高度
 * @param storageRows      存储网格行数
 * @param craftPanelY      合成面板 Y 坐标
 * @param craftPanelH      合成面板高度
 */
public record BottomPanelLayout(
        int panelX,
        int panelY,
        int panelW,
        int panelH,
        int sortX,
        int sortY,
        int categoryX,
        int categoryY,
        int categoryH,
        int storageX,
        int storageY,
        int storageW,
        int craftPanelX,
        int mainStorageW,
        int searchW,
        int pagerX,
        int toolY,
        int gridY,
        int gridH,
        int storageRows,
        int craftPanelY,
        int craftPanelH) {

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= this.panelX && mouseX <= this.panelX + this.panelW
                && mouseY >= this.panelY && mouseY <= this.panelY + this.panelH;
    }

    public boolean isInsideHeader(double mouseX, double mouseY) {
        return mouseX >= this.panelX && mouseX <= this.panelX + this.panelW
                && mouseY >= this.panelY && mouseY <= this.panelY + BOTTOM_PANEL_HEADER_H;
    }
}
