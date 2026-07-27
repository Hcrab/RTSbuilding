package com.rtsbuilding.rtsbuilding.compat.jei;

import com.rtsbuilding.rtsbuilding.client.record.StorageEntry;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsCraftTerminalScreen;
import mezz.jei.api.gui.IAdvancedGuiHandler;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.List;

/** 让 JEI 4 识别 RTS 合成终端右侧的额外面板和虚拟物品槽。 */
final class RtsCraftTerminalJeiGuiHandler implements IAdvancedGuiHandler<RtsCraftTerminalScreen> {
    @Override
    public Class<RtsCraftTerminalScreen> getGuiContainerClass() {
        return RtsCraftTerminalScreen.class;
    }

    @Override
    public List<Rectangle> getGuiExtraAreas(RtsCraftTerminalScreen screen) {
        Rectangle area = screen.getLinkedPanelArea();
        return area == null ? Collections.<Rectangle>emptyList() : Collections.singletonList(area);
    }

    @Nullable
    @Override
    public Object getIngredientUnderMouse(RtsCraftTerminalScreen screen, int mouseX, int mouseY) {
        StorageEntry entry = screen.getLinkedEntryAt(mouseX, mouseY);
        if (entry == null) {
            return null;
        }
        ItemStack stack = entry.stack();
        return stack == null || stack.isEmpty() ? null : stack.copy();
    }
}
