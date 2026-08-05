package com.rtsbuilding.rtsbuilding.platform.client;

import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.inventory.Slot;
import org.lwjgl.input.Mouse;

import java.lang.reflect.Field;

/** 旧版 GuiContainer 的私有/受保护布局读取入口，不要求脆弱的客户端 mixin。 */
@SideOnly(Side.CLIENT)
public final class GuiContainerCompat {
    private static final Field GUI_LEFT = ReflectionHelper.findField(
            GuiContainer.class, "guiLeft", "field_147003_i");
    private static final Field GUI_TOP = ReflectionHelper.findField(
            GuiContainer.class, "guiTop", "field_147009_r");

    private GuiContainerCompat() {}

    public static int guiLeft(GuiContainer screen) {
        return readInt(GUI_LEFT, screen);
    }

    public static int guiTop(GuiContainer screen) {
        return readInt(GUI_TOP, screen);
    }

    public static Slot slotUnderMouse(GuiContainer screen) {
        Minecraft minecraft = Minecraft.getMinecraft();
        ScaledResolution scaled = new ScaledResolution(
                minecraft, minecraft.displayWidth, minecraft.displayHeight);
        double mouseX = Mouse.getX() * scaled.getScaledWidth() / (double) minecraft.displayWidth;
        double mouseY = scaled.getScaledHeight()
                - Mouse.getY() * scaled.getScaledHeight() / (double) minecraft.displayHeight - 1.0D;
        return slotAt(screen, mouseX, mouseY);
    }

    public static Slot slotAt(GuiContainer screen, double mouseX, double mouseY) {
        if (screen == null || screen.inventorySlots == null) return null;
        int left = guiLeft(screen);
        int top = guiTop(screen);
        for (Object value : screen.inventorySlots.inventorySlots) {
            if (!(value instanceof Slot)) continue;
            Slot slot = (Slot) value;
            if (!slot.func_111238_b()) continue;
            int x = left + slot.xDisplayPosition;
            int y = top + slot.yDisplayPosition;
            if (mouseX >= x && mouseY >= y && mouseX < x + 16 && mouseY < y + 16) {
                return slot;
            }
        }
        return null;
    }

    private static int readInt(Field field, GuiContainer screen) {
        if (screen == null) return 0;
        try {
            return field.getInt(screen);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("无法读取 1.7.10 容器 GUI 布局", failure);
        }
    }
}
