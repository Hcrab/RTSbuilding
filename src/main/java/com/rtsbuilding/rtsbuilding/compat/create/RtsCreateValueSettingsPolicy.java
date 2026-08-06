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

    /**
     * 将 Create 全局输入反射结果收敛为 RTS 是否可以继续扫描行为的纯决策。
     *
     * <p>反射失败由运行时门面直接回落；这里仅表达已经取得的 Create 原生许可与剪贴板让行结果，
     * 不用 RTS 自己的潜行、冒险模式或物品规则猜测替代。</p>
     */
    static boolean allowsCreateGlobalInput(boolean createCanInteract, boolean holdingCreateClipboard) {
        return createCanInteract && !holdingCreateClipboard;
    }

    public static boolean shouldApplyOnServer(
            boolean activeRtsSession, boolean targetChunkLoaded, boolean eligibleBehaviour, boolean legalValue) {
        return activeRtsSession && targetChunkLoaded && eligibleBehaviour && legalValue;
    }
}
