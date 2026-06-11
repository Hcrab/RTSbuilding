package com.rtsbuilding.rtsbuilding.forgecompat.network;


import net.minecraft.resources.ResourceLocation;

public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();

    final class Type<T extends CustomPacketPayload> {
        private final ResourceLocation id;

        public Type(final ResourceLocation id) {
            this.id = id;
        }

        public ResourceLocation id() {
            return this.id;
        }
    }
}
