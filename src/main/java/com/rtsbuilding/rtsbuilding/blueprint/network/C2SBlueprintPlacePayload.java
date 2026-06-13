package com.rtsbuilding.rtsbuilding.blueprint.network;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.BlockPos;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record C2SBlueprintPlacePayload(
        String fileName,
        byte[] data,
        BlockPos anchor,
        byte yRotationSteps,
        byte xRotationSteps,
        byte zRotationSteps) implements CustomPacketPayload {
    public static final int MAX_FILE_NAME_CHARS = 160;
    public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;

    public static final Type<C2SBlueprintPlacePayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_blueprint_place"), C2SBlueprintPlacePayload.class);

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SBlueprintPlacePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.fileName() == null ? "" : payload.fileName(), MAX_FILE_NAME_CHARS);
                buf.writeByteArray(payload.data() == null ? new byte[0] : payload.data());
                buf.writeBlockPos(payload.anchor());
                buf.writeByte(payload.yRotationSteps());
                buf.writeByte(payload.xRotationSteps());
                buf.writeByte(payload.zRotationSteps());
            },
            (buf) -> new C2SBlueprintPlacePayload(
                    buf.readUtf(MAX_FILE_NAME_CHARS),
                    buf.readByteArray(MAX_FILE_BYTES),
                    buf.readBlockPos().immutable(),
                    buf.readByte(),
                    buf.readByte(),
                    buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
