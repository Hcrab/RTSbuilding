package com.rtsbuilding.rtsbuilding.client.input;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.diagnostic.RtsClientTraceTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.client.event.GuiOpenEvent;
import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;

/** 把 Forge 1.12 的聚合键鼠事件转换成 RTS 路由器需要的按下、保持、释放与滚轮语义。 */
public final class RtsClientInputEvents1122 {
    private RtsClientInputEvents1122() {
    }

    /*
     * 大型 1.12 整合包常同时安装 Mouse Tweaks、Inventory Tweaks 与 JEI；它们可能先取消聚合输入事件。
     * overlay 是当前鼠标区域的最终 owner，因此必须仍能看到已取消事件，并在其他普通优先级监听器前完成仲裁。
     */
    /**
     * 直接消费 LWJGL 当前键盘事件。Mixin 与 Forge 事件入口共用这一条路由，避免大型整合包截断其中一条入口。
     */
    public static boolean routeCurrentKeyboardInput(GuiScreen screen, String source) {
        return RtsRawGuiInputAdapter.routeKeyboard(screen, source);
    }

    /**
     * 直接消费 LWJGL 当前鼠标事件。返回 true 仅表示 RTS overlay 已取得所有权，调用方此时应阻止底层容器重复点击。
     */
    public static boolean routeCurrentMouseInput(GuiScreen screen, String source) {
        return RtsRawGuiInputAdapter.routeMouse(screen, source);
    }

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        final Minecraft minecraft = Minecraft.getMinecraft();
        GuiScreen previous = minecraft.currentScreen;
        if (event.gui == null) {
            RtsClientTraceTracker.guiEvent("GUI_EVENT_CLOSE",
                    previous == null ? "null" : previous.getClass().getName());
        } else {
            RtsClientTraceTracker.guiEvent("GUI_EVENT_OPEN", event.gui.getClass().getName());
        }
        if (previous != null && previous != event.gui) {
            RtsClientInputRouter.onScreenClosing(previous);
        }
        if (event.gui == null) {
            com.rtsbuilding.rtsbuilding.platform.thread.ThreadCompat.scheduleClient(new Runnable() {
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
