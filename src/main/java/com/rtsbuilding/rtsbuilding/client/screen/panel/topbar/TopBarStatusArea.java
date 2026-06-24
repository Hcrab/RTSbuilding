package com.rtsbuilding.rtsbuilding.client.screen.panel.topbar;

import com.rtsbuilding.rtsbuilding.client.module.building.BuildingModule;
import com.rtsbuilding.rtsbuilding.client.module.storage.StorageModule;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 顶部栏状态区域——构建并渲染两行状态文本（模式、存储链接、漏斗开关等）。
 *
 * <p>将 {@link TopBarPanel} 中的状态文本构建与渲染逻辑抽取为独立类，
 * 降低 {@link TopBarPanel} 的复杂度。</p>
 */
public record TopBarStatusArea(
        BuilderScreen screen,
        BuildingModule buildingModule,
        StorageModule storageModule) {

    /**
     * 在顶部栏下方绘制两行状态信息。
     */
    public void render(GuiGraphics g) {
    }
}
