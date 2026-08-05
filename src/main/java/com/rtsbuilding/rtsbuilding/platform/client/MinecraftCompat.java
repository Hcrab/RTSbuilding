package com.rtsbuilding.rtsbuilding.platform.client;

import cpw.mods.fml.relauncher.ReflectionHelper;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.Entity;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.util.Timer;

import java.io.File;
import java.lang.reflect.Field;

/** 集中收口 1.7.10 Minecraft 客户端字段名与后续版本访问器的差异。 */
@SideOnly(Side.CLIENT)
public final class MinecraftCompat {
    private static final Field TIMER = ReflectionHelper.findField(
            Minecraft.class, "timer", "field_71428_T");

    private MinecraftCompat() {}

    public static float renderPartialTicks(Minecraft minecraft) {
        if (minecraft == null) return 0.0F;
        try {
            return ((Timer) TIMER.get(minecraft)).renderPartialTicks;
        } catch (IllegalAccessException | RuntimeException ignored) {
            return 0.0F;
        }
    }

    public static File gameDir(Minecraft minecraft) {
        return minecraft == null ? null : minecraft.mcDataDir;
    }

    public static ServerData currentServerData(Minecraft minecraft) {
        return minecraft == null ? null : minecraft.func_147104_D();
    }

    public static NetHandlerPlayClient connection(Minecraft minecraft) {
        return minecraft == null || minecraft.thePlayer == null
                ? null : minecraft.thePlayer.sendQueue;
    }

    public static RenderItem renderItem() {
        return RenderItem.getInstance();
    }

    public static void setRenderViewEntity(Minecraft minecraft, Entity entity) {
        if (minecraft != null && entity instanceof net.minecraft.entity.EntityLivingBase) {
            minecraft.renderViewEntity = (net.minecraft.entity.EntityLivingBase) entity;
        }
    }
}
