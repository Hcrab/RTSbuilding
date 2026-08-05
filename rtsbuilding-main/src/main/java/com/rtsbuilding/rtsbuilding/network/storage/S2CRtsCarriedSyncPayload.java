package com.rtsbuilding.rtsbuilding.network.storage;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Server → client carried-stack sync for the open container menu.
 *
 * <p>When the server sets {@code player.containerMenu.carried} (e.g. via
 * linked-storage pickup/return), the client-side {@code carried} field is not
 * updated automatically - the vanilla ContainerSetSlotPacket(-1) only touches
 * {@code remoteCarried}. This payload lets the client mirror the authoritative
 * server carried state so the container overlay screen and drag interactions
 * stay in sync.
 */
public record S2CRtsCarriedSyncPayload(ItemStack stack) implements CustomPacketPayload {
    public static final Type<S2CRtsCarriedSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_rts_carried_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsCarriedSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    // OPTIONAL_STREAM_CODEC：允许空物品栈。RETURN_CARRIED 后 carried 会被清空，
                    // 若用 STREAM_CODEC 编码空栈会抛 "Empty ItemStack not allowed" 导致断线。
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    S2CRtsCarriedSyncPayload::stack,
                    S2CRtsCarriedSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
