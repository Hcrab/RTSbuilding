package com.rtsbuilding.rtsbuilding.client.bootstrap;


import com.rtsbuilding.rtsbuilding.client.screen.RtsModConfigScreen;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

public final class RtsClientBootstrap {
    private RtsClientBootstrap() {
    }

    public static void registerConfigUi(ModContainer modContainer) {
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, parent) -> new RtsModConfigScreen(parent));
    }
}
