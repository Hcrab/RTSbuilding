package com.rtsbuilding.rtsbuilding.forgecompat.network;

import java.util.function.BiConsumer;

import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Forge-side adapter for the mainline payload registrar shape.
 *
 * <p>Mainline NeoForge groups packet registration by gameplay domain
 * (camera, storage, crafting, builder, progression). Forge 1.20.1 still uses a
 * {@link SimpleChannel}, so this adapter keeps those domain registrars
 * structurally identical while translating each registration into an
 * incrementing SimpleChannel message id.
 */
public final class ForgePayloadRegistrar {
    private final SimpleChannel channel;
    private int nextId;

    public ForgePayloadRegistrar(SimpleChannel channel) {
        this.channel = channel;
    }

    public <T extends CustomPacketPayload> void playToServer(
            CustomPacketPayload.Type<T> type,
            StreamCodec<RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, IPayloadContext> handler) {
        register(type, codec, handler);
    }

    public <T extends CustomPacketPayload> void playToClient(
            CustomPacketPayload.Type<T> type,
            StreamCodec<RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, IPayloadContext> handler) {
        register(type, codec, handler);
    }

    private <T extends CustomPacketPayload> void register(
            CustomPacketPayload.Type<T> type,
            StreamCodec<RegistryFriendlyByteBuf, T> codec,
            BiConsumer<T, IPayloadContext> handler) {
        if (type.payloadClass() == null) {
            throw new IllegalArgumentException("Forge payload type " + type.id() + " does not declare a payload class");
        }
        this.channel.registerMessage(
                this.nextId++,
                type.payloadClass(),
                (message, buffer) -> codec.encode(new RegistryFriendlyByteBuf(buffer), message),
                buffer -> codec.decode(new RegistryFriendlyByteBuf(buffer)),
                (message, contextSupplier) -> {
                    NetworkEvent.Context context = contextSupplier.get();
                    handler.accept(message, new PayloadContextAdapter(context));
                    context.setPacketHandled(true);
                });
    }

    private record PayloadContextAdapter(NetworkEvent.Context context) implements IPayloadContext {
        @Override
        public net.minecraft.world.entity.player.Player player() {
            return this.context.getSender();
        }

        @Override
        public void enqueueWork(Runnable runnable) {
            this.context.enqueueWork(runnable);
        }
    }
}
