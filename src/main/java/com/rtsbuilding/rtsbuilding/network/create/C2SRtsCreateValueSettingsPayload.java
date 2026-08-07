package com.rtsbuilding.rtsbuilding.network.create;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * RTS 专用的 Create Value Settings 提交包。
 *
 * <p>这个包只传递 Create 原生屏幕已选择的行为 netId、坐标和设置值（或短按标记）；服务端会重新解析
 * 真实方块实体与行为。它刻意不携带、也不复用 Create 的 BlockEntityConfigurationPacket，因此不会触发
 * 后者面向普通近距交互的 20 格检查。</p>
 */
public record C2SRtsCreateValueSettingsPayload(
        BlockPos pos,
        int behaviourNetId,
        int row,
        int value,
        boolean shortInteraction,
        Direction face,
        double hitX,
        double hitY,
        double hitZ,
        boolean ctrlDown) implements CustomPacketPayload {

    public static final Type<C2SRtsCreateValueSettingsPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_create_value_settings"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsCreateValueSettingsPayload> STREAM_CODEC =
            StreamCodec.of(C2SRtsCreateValueSettingsPayload::encode, C2SRtsCreateValueSettingsPayload::decode);

    private static void encode(RegistryFriendlyByteBuf buffer, C2SRtsCreateValueSettingsPayload payload) {
        buffer.writeBlockPos(payload.pos);
        buffer.writeVarInt(payload.behaviourNetId);
        buffer.writeVarInt(payload.row);
        buffer.writeVarInt(payload.value);
        buffer.writeBoolean(payload.shortInteraction);
        buffer.writeByte(payload.face.get3DDataValue());
        buffer.writeDouble(payload.hitX);
        buffer.writeDouble(payload.hitY);
        buffer.writeDouble(payload.hitZ);
        buffer.writeBoolean(payload.ctrlDown);
    }

    private static C2SRtsCreateValueSettingsPayload decode(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        int behaviourNetId = buffer.readVarInt();
        int row = buffer.readVarInt();
        int value = buffer.readVarInt();
        boolean shortInteraction = buffer.readBoolean();
        int encodedFace = buffer.readUnsignedByte();
        return new C2SRtsCreateValueSettingsPayload(
                pos,
                behaviourNetId,
                row,
                value,
                shortInteraction,
                encodedFace >= 0 && encodedFace < Direction.values().length
                        ? Direction.from3DDataValue(encodedFace)
                        : Direction.UP,
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
