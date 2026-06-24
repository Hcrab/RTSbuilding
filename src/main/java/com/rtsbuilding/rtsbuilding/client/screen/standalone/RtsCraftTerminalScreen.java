package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * 合成终端屏幕——占位实现。
 *
 * <p>TODO: 完整实现迁移自 {@code client_old}：</p>
 * <ol>
 *   <li>迁移 {@code client_old/screen/craft/} 下的合成 UI 逻辑</li>
 *   <li>接入 {@code RtsClientKernel} 的合成模块数据</li>
 *   <li>实现 {@link #renderBg} 绘制合成网格和结果槽位</li>
 *   <li>补全 JEI 兼容方法（面板区域、链接槽位检测）</li>
 *   <li>集成 {@link com.rtsbuilding.rtsbuilding.client.popup.RtsCraftQuantityDialog} 数量选择弹窗</li>
 *   <li>添加容器同步处理（{@code containerTick}）</li>
 * </ol>
 *
 * @deprecated 占位实现，功能不完整。待 {@link #renderBg} 和 JEI 兼容方法全部实现后移除此注解。
 */
@Deprecated
public class RtsCraftTerminalScreen extends AbstractContainerScreen<AbstractContainerMenu> {
    public RtsCraftTerminalScreen(AbstractContainerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
    }

    // JEI 兼容方法
    public Rect2i getLinkedPanelArea() {
        return new Rect2i(0, 0, 0, 0);
    }

    public StorageEntry getLinkedEntryAt(double mouseX, double mouseY) {
        return null;
    }

    public Rect2i getLinkedSlotAreaAt(double mouseX, double mouseY) {
        return null;
    }
}
