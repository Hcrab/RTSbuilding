package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bulk storage operation triggered by Space+Click shortcuts.
 *
 * <pre>
 * Actions:
 *   0 = MassPickup  — extract all of itemId (up to amount) from storage to player inventory
 *   1 = DepositInv  — deposit all player inventory slots (not hotbar) to linked storage
 *   2 = DepositHotbar — deposit all hotbar slots to linked storage
 * </pre>
 */
public record C2SRtsBulkStorageOpPayload(byte action, String itemId, int amount) implements CustomPacketPayload {
    public static final Type<C2SRtsBulkStorageOpPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_bulk_storage_op"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsBulkStorageOpPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeByte(p.action);
                        buf.writeUtf(p.itemId == null ? "" : p.itemId);
                        buf.writeVarInt(p.amount);
                    },
                    buf -> new C2SRtsBulkStorageOpPayload(
                            buf.readByte(),
                            buf.readUtf(),
                            buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
