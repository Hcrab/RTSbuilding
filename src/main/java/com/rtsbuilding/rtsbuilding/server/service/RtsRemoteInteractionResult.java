package com.rtsbuilding.rtsbuilding.server.service;

import com.rtsbuilding.rtsbuilding.network.builder.S2CRtsRemoteMenuResultPayload;

/** 服务层返回给网络边界的稳定远程交互结论，不携带第三方异常文本。 */
public final class RtsRemoteInteractionResult {
    private final byte outcome;
    private final short reason;
    private final int windowId;

    private RtsRemoteInteractionResult(byte outcome, short reason, int windowId) {
        this.outcome = outcome;
        this.reason = reason;
        this.windowId = windowId;
    }

    public static RtsRemoteInteractionResult menuOpened(int windowId) {
        return new RtsRemoteInteractionResult(
                S2CRtsRemoteMenuResultPayload.MENU_OPENED,
                S2CRtsRemoteMenuResultPayload.REASON_NONE,
                windowId);
    }

    public static RtsRemoteInteractionResult noMenu(short reason) {
        return new RtsRemoteInteractionResult(
                S2CRtsRemoteMenuResultPayload.NO_MENU, reason, -1);
    }

    public static RtsRemoteInteractionResult rejected(short reason) {
        return new RtsRemoteInteractionResult(
                S2CRtsRemoteMenuResultPayload.REJECTED, reason, -1);
    }

    public static RtsRemoteInteractionResult failed() {
        return new RtsRemoteInteractionResult(
                S2CRtsRemoteMenuResultPayload.FAILED,
                S2CRtsRemoteMenuResultPayload.REASON_EXCEPTION,
                -1);
    }

    public byte outcome() { return outcome; }
    public short reason() { return reason; }
    public int windowId() { return windowId; }
}
