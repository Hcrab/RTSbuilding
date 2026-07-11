package com.rtsbuilding.rtsbuilding.client.rendering.culling;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 让隐藏区域对应的区块网格失效并重新编译。
 *
 * <p>这里只负责渲染刷新，不读取或修改隐藏盒。Forge 1.20.1 的 Embeddium 仍使用
 * Sodium 旧命名空间，因此安装时调用它的区域重建入口；未安装时继续调用原版
 * {@code LevelRenderer}。可选模组通过一次性反射适配，不会成为运行前置。
 */
public final class RtsCullingRenderInvalidator {
    private static final RendererMethods EMBEDDIUM = RendererMethods.find(
            "me.jellysquid.mods.sodium.client.render.SodiumWorldRenderer");

    private RtsCullingRenderInvalidator() {
    }

    public static void markBlocksDirty(BlockPos min, BlockPos max) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.levelRenderer == null || min == null || max == null) {
            return;
        }

        int minX = Math.min(min.getX(), max.getX()) - 1;
        int minY = Math.min(min.getY(), max.getY()) - 1;
        int minZ = Math.min(min.getZ(), max.getZ()) - 1;
        int maxX = Math.max(min.getX(), max.getX()) + 1;
        int maxY = Math.max(min.getY(), max.getY()) + 1;
        int maxZ = Math.max(min.getZ(), max.getZ()) + 1;

        if (EMBEDDIUM.schedule(minX, minY, minZ, maxX, maxY, maxZ)) {
            return;
        }
        minecraft.levelRenderer.setBlocksDirty(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private record RendererMethods(Method instanceNullable, Method scheduleRebuild) {
        private static RendererMethods find(String className) {
            try {
                Class<?> renderer = Class.forName(
                        className,
                        false,
                        RtsCullingRenderInvalidator.class.getClassLoader());
                return new RendererMethods(
                        renderer.getMethod("instanceNullable"),
                        renderer.getMethod("scheduleRebuildForBlockArea",
                                int.class, int.class, int.class, int.class, int.class, int.class, boolean.class));
            } catch (ClassNotFoundException | NoSuchMethodException ignored) {
                return new RendererMethods(null, null);
            }
        }

        private boolean schedule(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
            if (instanceNullable == null || scheduleRebuild == null) {
                return false;
            }
            try {
                Object renderer = instanceNullable.invoke(null);
                if (renderer == null) {
                    return false;
                }
                scheduleRebuild.invoke(renderer, minX, minY, minZ, maxX, maxY, maxZ, false);
                return true;
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                return false;
            }
        }
    }
}
