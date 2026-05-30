package com.rtsbuilding.rtsbuilding.forgecompat.fml;

import java.nio.file.Path;
import java.util.function.Supplier;

public final class FMLPaths {
    public static final Supplier<Path> CONFIGDIR = () -> net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get();

    private FMLPaths() {
    }
}

