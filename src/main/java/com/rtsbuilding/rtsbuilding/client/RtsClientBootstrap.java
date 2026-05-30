package com.rtsbuilding.rtsbuilding.client;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

public final class RtsClientBootstrap {
    private RtsClientBootstrap() {
    }

    public static void registerConfigUi(ModLoadingContext context) {
        context.registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((minecraft, parent) -> new RtsModConfigScreen(parent)));
    }
}
