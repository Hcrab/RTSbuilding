package com.rtsbuilding.rtsbuilding.forgecompat.network;


import net.minecraft.resources.ResourceLocation;

public interface CustomPacketPayload {
    Type<? extends CustomPacketPayload> type();

    final class Type<T extends CustomPacketPayload> {
        private final ResourceLocation id;
        private final Class<T> payloadClass;

        public Type(final ResourceLocation id) {
            this(id, null);
        }

        public Type(final ResourceLocation id, final Class<T> payloadClass) {
            this.id = id;
            this.payloadClass = payloadClass;
        }

        public ResourceLocation id() {
            return this.id;
        }

        public Class<T> payloadClass() {
            return this.payloadClass;
        }
    }
}
