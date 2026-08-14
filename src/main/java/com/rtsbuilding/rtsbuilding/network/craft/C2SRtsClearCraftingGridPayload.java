package com.rtsbuilding.rtsbuilding.network.craft;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.forgecompat.network.CustomPacketPayload;
import com.rtsbuilding.rtsbuilding.forgecompat.network.RegistryFriendlyByteBuf;
import com.rtsbuilding.rtsbuilding.forgecompat.network.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/** 请求服务端把当前 RTS 终端九宫格安全移回储存或玩家背包。 */
public record C2SRtsClearCraftingGridPayload(boolean toPlayerInventory)
        implements CustomPacketPayload {
    public static final Type<C2SRtsClearCraftingGridPayload> TYPE = new Type<>(
            new ResourceLocation(RtsbuildingMod.MODID, "c2s_rts_clear_crafting_grid"),
            C2SRtsClearCraftingGridPayload.class);
    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsClearCraftingGridPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeBoolean(payload.toPlayerInventory()),
                    buf -> new C2SRtsClearCraftingGridPayload(buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
