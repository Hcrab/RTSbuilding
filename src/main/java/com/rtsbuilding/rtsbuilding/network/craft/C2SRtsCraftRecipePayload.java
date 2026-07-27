package com.rtsbuilding.rtsbuilding.network.craft;

import com.rtsbuilding.rtsbuilding.network.RtsPacketBuffer;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

public final class C2SRtsCraftRecipePayload implements IMessage {
    private static final int MAX_RECIPE_ID_CHARS = 256;
    private static final int MAX_CRAFT_COUNT = 4096;
    private String recipeId = "";
    private int craftCount = 1;

    public C2SRtsCraftRecipePayload() {
    }
    public C2SRtsCraftRecipePayload(String recipeId, int craftCount) {
        this.recipeId = recipeId == null ? "" : recipeId;
        this.craftCount = craftCount;
    }
    public String recipeId() { return recipeId; }
    public int craftCount() { return craftCount; }
    public boolean isValid() {
        return !recipeId.trim().isEmpty() && recipeId.length() <= MAX_RECIPE_ID_CHARS
                && craftCount >= 1 && craftCount <= MAX_CRAFT_COUNT;
    }
    @Override public void fromBytes(ByteBuf buffer) {
        recipeId = RtsPacketBuffer.readString(buffer, MAX_RECIPE_ID_CHARS, "craft recipe id");
        craftCount = RtsPacketBuffer.readBoundedCount(buffer, MAX_CRAFT_COUNT, "craft count");
    }
    @Override public void toBytes(ByteBuf buffer) {
        if (!isValid()) throw new IllegalArgumentException("craft recipe request is invalid");
        RtsPacketBuffer.writeString(buffer, recipeId, MAX_RECIPE_ID_CHARS, "craft recipe id");
        RtsPacketBuffer.writeVarInt(buffer, craftCount);
    }
}
