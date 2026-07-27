package com.rtsbuilding.rtsbuilding.client.presentation.standalone;

import com.rtsbuilding.rtsbuilding.client.domain.state.StorageEntry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

@Deprecated
public class RtsCraftTerminalScreen extends AbstractContainerScreen<AbstractContainerMenu> {
    public RtsCraftTerminalScreen(AbstractContainerMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
    }

    
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
