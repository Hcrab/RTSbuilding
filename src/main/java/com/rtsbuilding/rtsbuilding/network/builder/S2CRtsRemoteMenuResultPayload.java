package com.rtsbuilding.rtsbuilding.network.builder;

import com.rtsbuilding.rtsbuilding.network.RtsTracedPayload;
import io.netty.buffer.ByteBuf;
import cpw.mods.fml.common.network.simpleimpl.IMessage;

/**
 * 远程交互的稳定服务端结论。
 *
 * <p>异常详情只写服务端 latest.log；网络只传稳定枚举，避免把第三方异常或路径泄漏给客户端。</p>
 */
public final class S2CRtsRemoteMenuResultPayload implements IMessage, RtsTracedPayload {
    public static final byte MENU_OPENED = 1;
    public static final byte NO_MENU = 2;
    public static final byte REJECTED = 3;
    public static final byte FAILED = 4;

    public static final short REASON_NONE = 0;
    public static final short REASON_RTS_INACTIVE = 1;
    public static final short REASON_OUT_OF_RANGE = 2;
    public static final short REASON_PROGRESSION_LOCKED = 3;
    public static final short REASON_NO_SESSION = 4;
    public static final short REASON_TARGET_MISSING = 5;
    public static final short REASON_TARGET_UNAVAILABLE = 6;
    public static final short REASON_CLAIM_DENIED = 7;
    public static final short REASON_NO_EFFECT = 8;
    public static final short REASON_ACTION_CONSUMED = 9;
    public static final short REASON_EXCEPTION = 10;

    private long traceId;
    private byte outcome;
    private short reason;
    private int windowId = -1;

    public S2CRtsRemoteMenuResultPayload() {
    }

    public S2CRtsRemoteMenuResultPayload(long traceId, byte outcome, short reason, int windowId) {
        this.traceId = traceId;
        this.outcome = outcome;
        this.reason = reason;
        this.windowId = windowId;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        traceId = buffer.readLong();
        outcome = buffer.readByte();
        reason = buffer.readShort();
        windowId = buffer.readInt();
        if (!isValid()) throw new IllegalArgumentException("invalid remote menu result");
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (!isValid()) throw new IllegalArgumentException("invalid remote menu result");
        buffer.writeLong(traceId);
        buffer.writeByte(outcome);
        buffer.writeShort(reason);
        buffer.writeInt(windowId);
    }

    public boolean isValid() {
        return traceId > 0L && outcome >= MENU_OPENED && outcome <= FAILED
                && reason >= REASON_NONE && reason <= REASON_EXCEPTION
                && windowId >= -1;
    }

    @Override public long traceId() { return traceId; }
    public byte outcome() { return outcome; }
    public short reason() { return reason; }
    public int windowId() { return windowId; }

    public static String outcomeName(byte value) {
        switch (value) {
            case MENU_OPENED: return "MENU_OPENED";
            case NO_MENU: return "NO_MENU";
            case REJECTED: return "REJECTED";
            case FAILED: return "FAILED";
            default: return "UNKNOWN";
        }
    }

    public static String reasonName(short value) {
        switch (value) {
            case REASON_NONE: return "NONE";
            case REASON_RTS_INACTIVE: return "RTS_INACTIVE";
            case REASON_OUT_OF_RANGE: return "OUT_OF_RANGE";
            case REASON_PROGRESSION_LOCKED: return "PROGRESSION_LOCKED";
            case REASON_NO_SESSION: return "NO_SESSION";
            case REASON_TARGET_MISSING: return "TARGET_MISSING";
            case REASON_TARGET_UNAVAILABLE: return "TARGET_UNAVAILABLE";
            case REASON_CLAIM_DENIED: return "CLAIM_DENIED";
            case REASON_NO_EFFECT: return "NO_EFFECT";
            case REASON_ACTION_CONSUMED: return "ACTION_CONSUMED";
            case REASON_EXCEPTION: return "EXCEPTION";
            default: return "UNKNOWN";
        }
    }
}
