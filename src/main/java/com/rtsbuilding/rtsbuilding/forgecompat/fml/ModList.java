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

    /** 将生产 UI 的模组名称/版本查询保持在一处版本适配边界内。 */
    public java.util.Optional<? extends net.minecraftforge.fml.ModContainer> getModContainerById(final String modId) {
        return net.minecraftforge.fml.ModList.get().getModContainerById(modId);
    }
}
