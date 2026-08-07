package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** 合成终端批量存取请求；客户端原型只用于筛选，不能作为物品来源。 */
public record C2SRtsBulkStorageOpPayload(byte action, ItemStack prototype, int amount)
        implements CustomPacketPayload {
    public static final byte WITHDRAW = 0;
    public static final byte DEPOSIT_INVENTORY = 1;
    public static final byte DEPOSIT_HOTBAR = 2;
    public static final byte DEPOSIT_ALL = 3;

    public static final Type<C2SRtsBulkStorageOpPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_bulk_storage_op"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsBulkStorageOpPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeByte(payload.action());
                        ItemStack.OPTIONAL_STREAM_CODEC.encode(buf,
                                payload.prototype() == null
                                        ? ItemStack.EMPTY : payload.prototype().copyWithCount(1));
                        buf.writeVarInt(Math.max(0, payload.amount()));
                    },
                    buf -> new C2SRtsBulkStorageOpPayload(
                            buf.readByte(), ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
