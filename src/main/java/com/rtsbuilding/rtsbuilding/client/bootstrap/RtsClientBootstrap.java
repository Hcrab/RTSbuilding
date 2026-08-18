package com.rtsbuilding.rtsbuilding.client.bootstrap;


import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsModClientConfigScreen;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.RtsModServerConfigScreen;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import net.minecraft.client.Minecraft;

public final class RtsClientBootstrap {
    private RtsClientBootstrap() {
    }

    public static void registerConfigUi(ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, parent) -> {
                    boolean inGame = Minecraft.getInstance().level != null;
                    if (inGame) {
                        return new RtsModServerConfigScreen(parent);
                    } else {
                        return new RtsModClientConfigScreen(parent);
                    }
                }
            );
    }
}
