package com.rtsbuilding.rtsbuilding.client.input;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.diagnostic.RtsClientTraceTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;

/** 把 Forge 1.12 的聚合键鼠事件转换成 RTS 路由器需要的按下、保持、释放与滚轮语义。 */
@Mod.EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Side.CLIENT)
public final class RtsClientInputEvents1122 {
    private RtsClientInputEvents1122() {
    }

    /*
     * 大型 1.12 整合包常同时安装 Mouse Tweaks、Inventory Tweaks 与 JEI；它们可能先取消聚合输入事件。
     * overlay 是当前鼠标区域的最终 owner，因此必须仍能看到已取消事件，并在其他普通优先级监听器前完成仲裁。
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onKeyboardInput(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (routeCurrentKeyboardInput(event.getGui(), "FORGE_PRE")) {
            event.setCanceled(true);
        }
    }

    /**
     * 直接消费 LWJGL 当前键盘事件。Mixin 与 Forge 事件入口共用这一条路由，避免大型整合包截断其中一条入口。
     */
    public static boolean routeCurrentKeyboardInput(GuiScreen screen, String source) {
        return RtsRawGuiInputAdapter.routeKeyboard(screen, source);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onMouseInput(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (routeCurrentMouseInput(event.getGui(), "FORGE_PRE")) {
            event.setCanceled(true);
        }
    }

    /**
     * 直接消费 LWJGL 当前鼠标事件。返回 true 仅表示 RTS overlay 已取得所有权，调用方此时应阻止底层容器重复点击。
     */
    public static boolean routeCurrentMouseInput(GuiScreen screen, String source) {
        return RtsRawGuiInputAdapter.routeMouse(screen, source);
    }

    @SubscribeEvent
    public static void onGuiOpen(GuiOpenEvent event) {
        final Minecraft minecraft = Minecraft.getMinecraft();
        GuiScreen previous = minecraft.currentScreen;
        if (event.getGui() == null) {
            RtsClientTraceTracker.guiEvent("GUI_EVENT_CLOSE",
                    previous == null ? "null" : previous.getClass().getName());
        } else {
            RtsClientTraceTracker.guiEvent("GUI_EVENT_OPEN", event.getGui().getClass().getName());
        }
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
