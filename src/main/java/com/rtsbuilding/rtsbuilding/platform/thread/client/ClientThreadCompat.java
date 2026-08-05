package com.rtsbuilding.rtsbuilding.platform.thread.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;

/** 客户端专用调度实现；公共 ThreadCompat 通过字符串边界调用，避免专服解析客户端类。 */
@SideOnly(Side.CLIENT)
public final class ClientThreadCompat {
    private ClientThreadCompat() {}

    public static void schedule(Runnable task) {
        Minecraft.getMinecraft().func_152344_a(task);
    }
}
