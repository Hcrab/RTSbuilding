package com.rtsbuilding.rtsbuilding.network.message;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.core.network.ActionType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.UUID;

public record C2SAction(
        ActionType actionType,
        @Nullable UUID workflowId,
        @Nullable CompoundTag params
) implements CustomPacketPayload {
    public static final Type<C2SAction> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SAction> STREAM_CODEC = StreamCodec.of(
            C2SAction::encode,
            C2SAction::decode);

    private static void encode(RegistryFriendlyByteBuf buf, C2SAction p) {
        buf.writeVarInt(p.actionType().ordinal());
        buf.writeNullable(p.workflowId(), (b, id) -> b.writeUUID(id));
        buf.writeNullable(p.params(), (b, tag) -> b.writeNbt(tag));
    }

    private static C2SAction decode(RegistryFriendlyByteBuf buf) {
        ActionType actionType = ActionType.values()[buf.readVarInt()];
        UUID workflowId = buf.readNullable(b -> b.readUUID());
        CompoundTag params = buf.readNullable(b -> b.readNbt());
        return new C2SAction(actionType, workflowId, params);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
