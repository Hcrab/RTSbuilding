package com.rtsbuilding.rtsbuilding.compat.jei;

import com.rtsbuilding.rtsbuilding.client.input.RtsClientInputGate;
import com.rtsbuilding.rtsbuilding.client.input.overlay.OverlayLayoutHelper;
import mezz.jei.api.gui.IGlobalGuiHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;
import java.awt.Rectangle;
import java.util.Collection;
import java.util.Collections;

/** 让 JEI 4 为普通容器上的 RTS 储存覆盖层预留空间并识别其物品。 */
final class RtsOverlayJeiGlobalGuiHandler implements IGlobalGuiHandler {
    @Override
    public Collection<Rectangle> getGuiExtraAreas() {
        Minecraft minecraft = Minecraft.getMinecraft();
        GuiScreen screen = minecraft == null ? null : minecraft.currentScreen;
        return screen == null
                ? Collections.<Rectangle>emptyList()
                : RtsClientInputGate.getJeiOverlayExtraAreas(screen);
    }

    @Nullable
    @Override
    public Object getIngredientUnderMouse(int mouseX, int mouseY) {
        OverlayLayoutHelper.JeiOverlayIngredient ingredient =
                RtsClientInputGate.getJeiOverlayIngredientUnderMouse(mouseX, mouseY);
        if (ingredient == null) {
            return null;
        }
        ItemStack stack = ingredient.stack();
        return stack == null || stack.isEmpty() ? null : stack.copy();
    }
}
