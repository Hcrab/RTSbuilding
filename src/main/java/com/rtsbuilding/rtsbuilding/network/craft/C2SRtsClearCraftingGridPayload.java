package com.rtsbuilding.rtsbuilding.network.craft;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 请求服务端把当前 RTS 合成终端的九格材料安全移回储存或玩家背包。 */
public record C2SRtsClearCraftingGridPayload(boolean toPlayerInventory) implements CustomPacketPayload {
    public static final Type<C2SRtsClearCraftingGridPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_clear_crafting_grid"));
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsClearCraftingGridPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeBoolean(payload.toPlayerInventory()),
                    buf -> new C2SRtsClearCraftingGridPayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
