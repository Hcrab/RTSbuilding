package com.rtsbuilding.rtsbuilding.forgecompat.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;

public class RegistryFriendlyByteBuf extends FriendlyByteBuf {
    public RegistryFriendlyByteBuf(final ByteBuf source) {
        super(source);
    }
}

