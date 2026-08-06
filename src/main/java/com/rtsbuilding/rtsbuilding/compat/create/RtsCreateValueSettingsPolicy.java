package com.rtsbuilding.rtsbuilding.compat.create;

/**
 * Create Value Settings 兼容链路的纯决策器。
 *
 * <p>它只表达客户端是否接管主键，以及服务端窄安全门是否全部满足；不包含玩家实体
 * 到目标的近距参数。RTS 远程设置的产品边界由当前会话的可配置操作范围负责。</p>
 */
public final class RtsCreateValueSettingsPolicy {
    private RtsCreateValueSettingsPolicy() {
    }

    public static boolean shouldStartHold(
            boolean primaryActionMouse, boolean worldArea, boolean eligibleBehaviour) {
        return primaryActionMouse && worldArea && eligibleBehaviour;
    }

    public static boolean shouldApplyOnServer(
            boolean activeRtsSession,
            boolean exactDimension,
            boolean targetChunkLoaded,
            boolean withinRtsActionRange,
            boolean mayInteract,
            boolean eligibleBehaviour,
            boolean legalValue) {
        return activeRtsSession
                && exactDimension
                && targetChunkLoaded
                && withinRtsActionRange
                && mayInteract
                && eligibleBehaviour
                && legalValue;
    }
}
