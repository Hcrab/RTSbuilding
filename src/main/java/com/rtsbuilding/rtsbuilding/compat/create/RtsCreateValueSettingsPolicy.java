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
     * 只有 Create 自己允许交互且主手没有占用 Clipboard 工作流时，RTS 才接管这次输入。
     *
     * <p>这不是另一套物品或玩家状态规则：两个布尔值都来自 Create 0.5.1 的原生入口，
     * 这里只把“必须让行”的组合固定成可回归测试的纯决策。</p>
     */
    public static boolean allowsCreateGlobalInput(
            boolean createCanInteract, boolean holdingCreateClipboard) {
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
