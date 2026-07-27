package com.rtsbuilding.rtsbuilding.network.message;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

public record S2CStateUpdate(
        String key,
        @Nullable CompoundTag data
) implements CustomPacketPayload {
    public static final Type<S2CStateUpdate> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_state_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CStateUpdate> STREAM_CODEC = StreamCodec.of(
            S2CStateUpdate::encode,
            S2CStateUpdate::decode);

    private static void encode(RegistryFriendlyByteBuf buf, S2CStateUpdate p) {
        buf.writeUtf(p.key());
        buf.writeNullable(p.data(), (b, tag) -> b.writeNbt(tag));
    }

    private static S2CStateUpdate decode(RegistryFriendlyByteBuf buf) {
        String key = buf.readUtf();
        CompoundTag data = buf.readNullable(b -> b.readNbt());
        return new S2CStateUpdate(key, data);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
