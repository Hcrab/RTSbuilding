package com.rtsbuilding.rtsbuilding.client.screen.culling;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * 验证可选渲染模组的真实 Mixin 转换结果。
 *
 * <p>这里只验证连接是否成立，不参与每帧渲染。开发环境直接失败可以让自动化和本地运行立刻发现回归；
 * 生产环境只记录错误，避免兼容性提示本身阻止玩家进入游戏。
 */
public final class RtsCullingMixinVerifier {
    private static final String EMBEDDIUM_WORLD_SLICE =
            "me.jellysquid.mods.sodium.client.world.WorldSlice";

    private RtsCullingMixinVerifier() {
    }

    public static void verifyOptionalRendererHooks() {
        if (!ModList.get().isLoaded("embeddium")) {
            return;
        }
        try {
            Class<?> worldSlice = Class.forName(EMBEDDIUM_WORLD_SLICE, false,
                    RtsCullingMixinVerifier.class.getClassLoader());
            if (hasWorldSliceBridge(worldSlice)) {
                RtsbuildingMod.LOGGER.info("RTS range-culling hook verified for Embeddium WorldSlice");
                return;
            }
            reportFailure("RTS range-culling Mixin was not applied to Embeddium WorldSlice", null);
        } catch (ClassNotFoundException exception) {
            reportFailure("Embeddium is loaded but its WorldSlice class was not found", exception);
        }
    }

    static boolean hasWorldSliceBridge(Class<?> worldSlice) {
        return RtsCullingWorldSliceBridge.class.isAssignableFrom(worldSlice);
    }

    private static void reportFailure(String message, Throwable cause) {
        if (!FMLEnvironment.production) {
            throw new IllegalStateException(message, cause);
        }
        RtsbuildingMod.LOGGER.error(message, cause);
    }
}
