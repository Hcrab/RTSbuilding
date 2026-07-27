package com.rtsbuilding.rtsbuilding.network.feedback;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** 服务端下发的受伤反馈；数值只用于客户端视觉和声音强度。 */
public final class S2CRtsDamageFeedbackPayload implements IMessage {
    private float amount;
    private boolean lowHealth;

    public S2CRtsDamageFeedbackPayload() {
    }

    public S2CRtsDamageFeedbackPayload(float amount, boolean lowHealth) {
        this.amount = sanitizeAmount(amount);
        this.lowHealth = lowHealth;
    }

    public float amount() { return this.amount; }
    public boolean lowHealth() { return this.lowHealth; }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.amount = sanitizeAmount(buffer.readFloat());
        this.lowHealth = buffer.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeFloat(sanitizeAmount(this.amount));
        buffer.writeBoolean(this.lowHealth);
    }

    private static float sanitizeAmount(float value) {
        return Float.isNaN(value) || Float.isInfinite(value) || value < 0.0F ? 0.0F : value;
    }
}
