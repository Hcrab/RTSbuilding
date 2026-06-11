package com.rtsbuilding.rtsbuilding.forgecompat.network;


import net.minecraft.world.entity.player.Player;

public interface IPayloadContext {
    Player player();

    void enqueueWork(Runnable runnable);
}
