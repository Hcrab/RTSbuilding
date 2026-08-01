package com.rtsbuilding.rtsbuilding.client.bootstrap;


import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsModConfigScreen;
import net.minecraft.client.gui.screens.Screen;

public final class RtsClientBootstrap {
    private RtsClientBootstrap() {
    }

    /** 供 Fabric 的 Mod Menu 可选入口和模组自身按钮共用。 */
    public static Screen createConfigScreen(Screen parent) {
        return new RtsModConfigScreen(parent);
    }
}
