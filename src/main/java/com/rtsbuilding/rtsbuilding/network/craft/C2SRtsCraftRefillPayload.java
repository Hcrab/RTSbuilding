package com.rtsbuilding.rtsbuilding.network.craft;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record C2SRtsCraftRefillPayload(
        List<ItemStack> blueprintStacks,
        String craftedItemId,
        int craftedCount) implements CustomPacketPayload {
    private static final int BLUEPRINT_SIZE = 9;
    public static final Type<C2SRtsCraftRefillPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "c2s_rts_craft_refill"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRtsCraftRefillPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        List<ItemStack> stacks = payload.blueprintStacks();
                        for (int i = 0; i < BLUEPRINT_SIZE; i++) {
                            ItemStack stack = stacks != null && i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY;
                            if (stack == null || stack.isEmpty()) {
                                buf.writeBoolean(false);
                                continue;
                            }
                            buf.writeBoolean(true);
                            ItemStack.STREAM_CODEC.encode(buf, stack.copyWithCount(1));
                        }
                        buf.writeUtf(payload.craftedItemId() == null ? "" : payload.craftedItemId(), 128);
                        buf.writeVarInt(Math.max(0, payload.craftedCount()));
                    },
                    (buf) -> {
                        List<ItemStack> stacks = new ArrayList<>(BLUEPRINT_SIZE);
                        for (int i = 0; i < BLUEPRINT_SIZE; i++) {
                            stacks.add(buf.readBoolean() ? ItemStack.STREAM_CODEC.decode(buf) : ItemStack.EMPTY);
                        }
                        return new C2SRtsCraftRefillPayload(stacks, buf.readUtf(128), buf.readVarInt());
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
