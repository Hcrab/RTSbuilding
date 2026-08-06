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

    /**
     * 将 Create 全局输入反射结果收敛为 RTS 是否继续扫描行为的纯决策。
     *
     * <p>潜行、冒险模式等许可由 Create 的 {@code canInteract} 独占决定；Clipboard 仍由
     * Create 原生路径处理，避免 RTS 抢走它的右键。反射不能确认事实时由运行时门面让行。</p>
     */
    static boolean allowsCreateGlobalInput(boolean createCanInteract, boolean holdingCreateClipboard) {
        return createCanInteract && !holdingCreateClipboard;
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
