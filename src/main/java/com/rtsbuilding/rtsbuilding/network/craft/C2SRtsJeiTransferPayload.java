package com.rtsbuilding.rtsbuilding.network.craft;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class C2SRtsJeiTransferPayload implements IMessage {
    private static final int GRID_SIZE = 9;
    private static final int MAX_RECIPE_ID_CHARS = 256;
    private String recipeId = "";
    private List<ItemStack> ingredientPrototypes = emptyGrid();
    private boolean maxTransfer;
    private boolean clearGridFirst;

    public C2SRtsJeiTransferPayload() {
    }
    public C2SRtsJeiTransferPayload(String recipeId, List<ItemStack> ingredientPrototypes,
                                    boolean maxTransfer, boolean clearGridFirst) {
        this.recipeId = recipeId == null ? "" : recipeId;
        this.ingredientPrototypes = normalizeGrid(ingredientPrototypes);
        this.maxTransfer = maxTransfer;
        this.clearGridFirst = clearGridFirst;
    }
    public String recipeId() { return recipeId; }
    public List<ItemStack> ingredientPrototypes() { return ingredientPrototypes; }
    public boolean maxTransfer() { return maxTransfer; }
    public boolean clearGridFirst() { return clearGridFirst; }
    public boolean isValid() {
        return !recipeId.trim().isEmpty() && recipeId.length() <= MAX_RECIPE_ID_CHARS
                && ingredientPrototypes.size() == GRID_SIZE;
    }
    @Override public void fromBytes(ByteBuf buffer) {
        recipeId = RtsPacketBuffer.readString(buffer, MAX_RECIPE_ID_CHARS, "JEI recipe id");
        List<ItemStack> decoded = new ArrayList<>(GRID_SIZE);
        for (int i = 0; i < GRID_SIZE; i++) {
            decoded.add(buffer.readBoolean() ? normalizeStack(RtsPacketBuffer.readItemStack(buffer)) : null);
        }
        ingredientPrototypes = Collections.unmodifiableList(decoded);
        maxTransfer = buffer.readBoolean();
        clearGridFirst = buffer.readBoolean();
    }
    @Override public void toBytes(ByteBuf buffer) {
        if (!isValid()) throw new IllegalArgumentException("JEI transfer request is invalid");
        RtsPacketBuffer.writeString(buffer, recipeId, MAX_RECIPE_ID_CHARS, "JEI recipe id");
        for (ItemStack stack : ingredientPrototypes) {
            boolean present = stack != null && !com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack);
            buffer.writeBoolean(present);
            if (present) RtsPacketBuffer.writeItemStack(buffer, normalizeStack(stack));
        }
        buffer.writeBoolean(maxTransfer);
        buffer.writeBoolean(clearGridFirst);
    }
    private static List<ItemStack> normalizeGrid(List<ItemStack> values) {
        List<ItemStack> result = new ArrayList<>(GRID_SIZE);
        for (int i = 0; i < GRID_SIZE; i++) {
            result.add(values != null && i < values.size() ? normalizeStack(values.get(i)) : null);
        }
        return Collections.unmodifiableList(result);
    }
    private static List<ItemStack> emptyGrid() { return normalizeGrid(null); }
    private static ItemStack normalizeStack(ItemStack stack) {
        if (stack == null || com.rtsbuilding.rtsbuilding.platform.storage.StackCompat.isEmpty(stack)) return null;
        ItemStack copy = stack.copy();
        copy.stackSize = 1;
        return copy;
    }
}
