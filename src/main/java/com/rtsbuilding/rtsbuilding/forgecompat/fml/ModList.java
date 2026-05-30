package com.rtsbuilding.rtsbuilding.forgecompat.fml;

public final class ModList {
    private static final ModList INSTANCE = new ModList();

    private ModList() {
    }

    public static ModList get() {
        return INSTANCE;
    }

    public boolean isLoaded(final String modId) {
        return net.minecraftforge.fml.ModList.get().isLoaded(modId);
    }
}

