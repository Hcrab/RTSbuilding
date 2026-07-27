package com.rtsbuilding.rtsbuilding.network.message;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.UUID;

public record S2CProgress(
        UUID workflowId,
        int status,     // 0=running, 1=completed, 2=failed
        int progress,   // 0-100
        @Nullable CompoundTag detail
) implements CustomPacketPayload {
    public static final Type<S2CProgress> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_progress"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CProgress> STREAM_CODEC = StreamCodec.of(
            S2CProgress::encode,
            S2CProgress::decode);

    private static void encode(RegistryFriendlyByteBuf buf, S2CProgress p) {
        buf.writeUUID(p.workflowId());
        buf.writeVarInt(p.status());
        buf.writeVarInt(p.progress());
        buf.writeNullable(p.detail(), RegistryFriendlyByteBuf::writeNbt);
    }

    private static S2CProgress decode(RegistryFriendlyByteBuf buf) {
        UUID workflowId = buf.readUUID();
        int status = buf.readVarInt();
        int progress = buf.readVarInt();
        CompoundTag detail = buf.readNullable(RegistryFriendlyByteBuf::readNbt);
        return new S2CProgress(workflowId, status, progress, detail);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
