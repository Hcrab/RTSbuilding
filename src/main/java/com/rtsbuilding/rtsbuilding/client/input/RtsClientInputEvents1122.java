package com.rtsbuilding.rtsbuilding.client.input;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** 把 Forge 1.12 的聚合键鼠事件转换成 RTS 路由器需要的按下、保持、释放与滚轮语义。 */
@Mod.EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Side.CLIENT)
public final class RtsClientInputEvents1122 {
    private RtsClientInputEvents1122() {
    }

    @SubscribeEvent
    public static void onKeyboardInput(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!Keyboard.getEventKeyState()) return;
        int key = Keyboard.getEventKey() == Keyboard.KEY_NONE
                ? Keyboard.getEventCharacter() + 256 : Keyboard.getEventKey();
        if (RtsClientInputRouter.onScreenKeyPressed(
                event.getGui(), key, Keyboard.getEventCharacter())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        ScaledResolution scaled = new ScaledResolution(minecraft);
        double mouseX = Mouse.getEventX() * (double) scaled.getScaledWidth() / minecraft.displayWidth;
        double mouseY = scaled.getScaledHeight()
                - Mouse.getEventY() * (double) scaled.getScaledHeight() / minecraft.displayHeight - 1.0D;
        int button = Mouse.getEventButton();
        int wheel = Mouse.getEventDWheel();

        if (wheel != 0) {
            RtsPointerEvent pointer = new RtsPointerEvent(
                    event.getGui(), mouseX, mouseY, button, wheel);
            RtsClientPointerRouter.onScreenMouseScrolled(pointer);
            if (pointer.isCanceled()) event.setCanceled(true);
        }

        if (button >= 0) {
            RtsPointerEvent pointer = new RtsPointerEvent(
                    event.getGui(), mouseX, mouseY, button, 0.0D);
            if (Mouse.getEventButtonState()) {
                RtsClientPointerRouter.onScreenMousePressed(pointer);
            } else {
                RtsClientPointerRouter.onScreenMouseReleased(pointer);
            }
            if (pointer.isCanceled()) event.setCanceled(true);
        } else if (Mouse.isButtonDown(0) || Mouse.isButtonDown(1)) {
            RtsPointerEvent pointer = new RtsPointerEvent(
                    event.getGui(), mouseX, mouseY, -1, 0.0D);
            RtsClientPointerRouter.onScreenMouseDragged(pointer);
            if (pointer.isCanceled()) event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onGuiOpen(GuiOpenEvent event) {
        final Minecraft minecraft = Minecraft.getMinecraft();
        GuiScreen previous = minecraft.currentScreen;
        if (previous != null && previous != event.getGui()) {
            RtsClientInputRouter.onScreenClosing(previous);
        }
        if (event.getGui() == null) {
            minecraft.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    if (minecraft.currentScreen == null && !minecraft.inGameHasFocus) {
                        minecraft.setIngameFocus();
                    }
                }
            });
        }
    }
}
