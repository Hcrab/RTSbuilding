package com.rtsbuilding.rtsbuilding.network.plugin;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

public record S2CRtsPluginStatePayload(
        List<String> pluginIds,
        List<String> families,
        List<Integer> radiusBlocks,
        List<Boolean> fieldDeployment,
        List<Boolean> personal,
        List<String> ownerNames,
        List<ItemStack> stacks,
        String teamName) implements CustomPacketPayload {
    private static final int MAX_PLUGIN_COUNT = 64;
    private static final int MAX_PLUGIN_ID_CHARS = 128;
    private static final int MAX_FAMILY_CHARS = 64;
    private static final int MAX_OWNER_NAME_CHARS = 64;
    private static final int MAX_TEAM_NAME_CHARS = 128;

    public static final Type<S2CRtsPluginStatePayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(RtsbuildingMod.MODID, "s2c_rts_plugin_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CRtsPluginStatePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                int size = Math.min(MAX_PLUGIN_COUNT, Math.min(payload.pluginIds().size(),
                        Math.min(payload.families().size(),
                                Math.min(payload.radiusBlocks().size(),
                                        Math.min(payload.fieldDeployment().size(),
                                                Math.min(payload.personal().size(),
                                                        Math.min(payload.ownerNames().size(), payload.stacks().size())))))));
                buf.writeVarInt(size);
                for (int i = 0; i < size; i++) {
                    buf.writeUtf(RtsPluginPayloadText.fit(payload.pluginIds().get(i), MAX_PLUGIN_ID_CHARS),
                            MAX_PLUGIN_ID_CHARS);
                    buf.writeUtf(RtsPluginPayloadText.fit(payload.families().get(i), MAX_FAMILY_CHARS),
                            MAX_FAMILY_CHARS);
                    buf.writeVarInt(Math.max(0, payload.radiusBlocks().get(i)));
                    buf.writeBoolean(Boolean.TRUE.equals(payload.fieldDeployment().get(i)));
                    buf.writeBoolean(Boolean.TRUE.equals(payload.personal().get(i)));
                    buf.writeUtf(RtsPluginPayloadText.fit(payload.ownerNames().get(i), MAX_OWNER_NAME_CHARS),
                            MAX_OWNER_NAME_CHARS);
                    ItemStack stack = payload.stacks().get(i);
                    ItemStack.STREAM_CODEC.encode(buf, stack == null ? ItemStack.EMPTY : stack.copyWithCount(1));
                }
                buf.writeUtf(RtsPluginPayloadText.fit(payload.teamName(), MAX_TEAM_NAME_CHARS),
                        MAX_TEAM_NAME_CHARS);
            },
            (buf) -> {
                int size = Math.min(MAX_PLUGIN_COUNT, Math.max(0, buf.readVarInt()));
                List<String> pluginIds = new ArrayList<>(size);
                List<String> families = new ArrayList<>(size);
                List<Integer> radiusBlocks = new ArrayList<>(size);
                List<Boolean> fieldDeployment = new ArrayList<>(size);
                List<Boolean> personal = new ArrayList<>(size);
                List<String> ownerNames = new ArrayList<>(size);
                List<ItemStack> stacks = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    pluginIds.add(buf.readUtf(MAX_PLUGIN_ID_CHARS));
                    families.add(buf.readUtf(MAX_FAMILY_CHARS));
                    radiusBlocks.add(buf.readVarInt());
                    fieldDeployment.add(buf.readBoolean());
                    personal.add(buf.readBoolean());
                    ownerNames.add(buf.readUtf(MAX_OWNER_NAME_CHARS));
                    stacks.add(ItemStack.STREAM_CODEC.decode(buf));
                }
                return new S2CRtsPluginStatePayload(pluginIds, families, radiusBlocks, fieldDeployment, personal,
                        ownerNames, stacks, buf.readUtf(MAX_TEAM_NAME_CHARS));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
