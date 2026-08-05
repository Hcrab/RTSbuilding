package com.rtsbuilding.rtsbuilding.platform.server;

import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

/** 服务端、玩家列表与维度查询的 1.7.10 入口。 */
public final class ServerCompat {
    private ServerCompat() {}

    public static MinecraftServer getServer(EntityPlayerMP player) {
        return FMLCommonHandler.instance().getMinecraftServerInstance();
    }

    public static MinecraftServer getServer(WorldServer world) {
        return world == null ? null : FMLCommonHandler.instance().getMinecraftServerInstance();
    }

    public static PlayerListCompat getPlayerList(MinecraftServer server) {
        return new PlayerListCompat(server == null ? null : server.getConfigurationManager());
    }

    /** 只返回当前已加载维度，不把普通查询升级成隐式维度加载。 */
    public static WorldServer getWorld(MinecraftServer server, int dimension) {
        return server == null ? null : DimensionManager.getWorld(dimension);
    }

    public static WorldServer[] worlds(MinecraftServer server) {
        if (server == null || server.worldServers == null) return new WorldServer[0];
        return server.worldServers.clone();
    }
}
