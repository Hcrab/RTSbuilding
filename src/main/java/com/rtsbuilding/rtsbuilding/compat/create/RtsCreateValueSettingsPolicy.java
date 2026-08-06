package com.rtsbuilding.rtsbuilding.compat.create;

/**
 * Create Value Settings 兼容链路的极小纯决策器。
 *
 * <p>它只表达客户端是否消费主键，以及服务端是否已具备提交前置条件；Create 反射、屏幕生命周期和
 * 网络实现仍在各自职责类中。这里刻意没有距离参数，RTS 会话不是近距交互的替代距离检查。</p>
 */
public final class RtsCreateValueSettingsPolicy {
    private RtsCreateValueSettingsPolicy() {
    }

    public static boolean shouldStartHold(boolean primaryActionMouse, boolean worldArea, boolean eligibleBehaviour) {
        return primaryActionMouse && worldArea && eligibleBehaviour;
    }

    public static boolean shouldApplyOnServer(
            boolean activeRtsSession, boolean targetChunkLoaded, boolean eligibleBehaviour, boolean legalValue) {
        return activeRtsSession && targetChunkLoaded && eligibleBehaviour && legalValue;
    }
}
