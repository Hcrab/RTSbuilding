package com.rtsbuilding.rtsbuilding.network.craft;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class C2SRtsCraftRefillPayload implements IMessage {
    private static final int BLUEPRINT_SIZE = 9;
    private static final int MAX_ITEM_ID_CHARS = 128;
    private static final int MAX_CRAFTED_COUNT = 1_000_000;
    private List<ItemStack> blueprintStacks = emptyBlueprint();
    private String craftedItemId = "";
    private int craftedCount;

    public C2SRtsCraftRefillPayload() {
    }
    public C2SRtsCraftRefillPayload(List<ItemStack> blueprintStacks,
                                    String craftedItemId, int craftedCount) {
        this.blueprintStacks = normalizeBlueprint(blueprintStacks);
        this.craftedItemId = craftedItemId == null ? "" : craftedItemId;
        this.craftedCount = craftedCount;
    }
    public List<ItemStack> blueprintStacks() { return blueprintStacks; }
    public String craftedItemId() { return craftedItemId; }
    public int craftedCount() { return craftedCount; }
    public boolean isValid() {
        return blueprintStacks.size() == BLUEPRINT_SIZE
                && !craftedItemId.trim().isEmpty() && craftedItemId.length() <= MAX_ITEM_ID_CHARS
                && craftedCount >= 0 && craftedCount <= MAX_CRAFTED_COUNT;
    }
    @Override public void fromBytes(ByteBuf buffer) {
        List<ItemStack> decoded = new ArrayList<>(BLUEPRINT_SIZE);
        for (int i = 0; i < BLUEPRINT_SIZE; i++) {
            decoded.add(buffer.readBoolean() ? normalizeStack(RtsPacketBuffer.readItemStack(buffer)) : null);
        }
        blueprintStacks = Collections.unmodifiableList(decoded);
        craftedItemId = RtsPacketBuffer.readString(buffer, MAX_ITEM_ID_CHARS, "crafted item id");
        craftedCount = RtsPacketBuffer.readBoundedCount(buffer, MAX_CRAFTED_COUNT, "crafted item count");
    }
    @Override public void toBytes(ByteBuf buffer) {
        if (!isValid()) throw new IllegalArgumentException("craft refill request is invalid");
        for (int i = 0; i < BLUEPRINT_SIZE; i++) {
            ItemStack stack = blueprintStacks.get(i);
            boolean present = stack != null && !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack);
            buffer.writeBoolean(present);
            if (present) RtsPacketBuffer.writeItemStack(buffer, normalizeStack(stack));
        }
        RtsPacketBuffer.writeString(buffer, craftedItemId, MAX_ITEM_ID_CHARS, "crafted item id");
        RtsPacketBuffer.writeVarInt(buffer, craftedCount);
    }
    private static List<ItemStack> normalizeBlueprint(List<ItemStack> values) {
        List<ItemStack> result = new ArrayList<>(BLUEPRINT_SIZE);
        for (int i = 0; i < BLUEPRINT_SIZE; i++) {
            result.add(values != null && i < values.size() ? normalizeStack(values.get(i)) : null);
        }
        return Collections.unmodifiableList(result);
    }
    private static List<ItemStack> emptyBlueprint() { return normalizeBlueprint(null); }
    private static ItemStack normalizeStack(ItemStack stack) {
        if (stack == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack)) return null;
        ItemStack copy = stack.copy();
        copy.stackSize = 1;
        return copy;
    }
}
