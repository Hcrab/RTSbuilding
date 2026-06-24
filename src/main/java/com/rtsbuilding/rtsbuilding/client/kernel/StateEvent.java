package com.rtsbuilding.rtsbuilding.client.kernel;

import com.rtsbuilding.rtsbuilding.client.record.CraftFeedbackInfo;
import com.rtsbuilding.rtsbuilding.common.build.BuilderMode;
import com.rtsbuilding.rtsbuilding.network.storage.S2CRtsStoragePagePayload;
import net.minecraft.core.BlockPos;

/**
 * 不可变的会话事件——替代 tick 轮询的推送式状态更新。
 *
 * <p>Feature Module 通过 {@link FeatureModule#onSessionEvent(StateEvent)} 接收
 * 自己关心的事件，不需要在 {@code tick()} 中反复检查状态变化。</p>
 *
 * <p>每个事件是一个 sealed record，携带最小必要的数据。</p>
 */
public sealed interface StateEvent {

    /** RTS 模式开关 */
    record RtsToggled(boolean enabled) implements StateEvent {}

    /** 服务端锚点/边界更新 */
    record AnchorUpdated(double x, double y, double z, double maxRadius) implements StateEvent {}

    /** 建造模式切换 */
    record BuilderModeChanged(BuilderMode mode) implements StateEvent {}

    /** 存储页面加载完成 */
    record StoragePageLoaded(int revision, S2CRtsStoragePagePayload payload) implements StateEvent {}

    /** 合成反馈 */
    record CraftFeedbackReceived(CraftFeedbackInfo info) implements StateEvent {}

    /** 伤害反馈 */
    record DamageTaken(float amount, boolean lowHealth, float health) implements StateEvent {}

    /** 工作流进度更新 */
    record WorkflowProgressed(int slot) implements StateEvent {}

    /** 玩家死亡 */
    record PlayerDied() implements StateEvent {}

    /** 蓝图捕获完成 */
    record BlueprintCaptureComplete(BlockPos min, BlockPos max) implements StateEvent {}

    /** 物品选中 */
    record ItemSelected(String itemId, String label) implements StateEvent {}

    /** 远程菜单打开 */
    record RemoteMenuOpened() implements StateEvent {}

    /** 远程菜单关闭 */
    record RemoteMenuClosed() implements StateEvent {}

    /** 自定义事件（供拓展用） */
    record Custom(String type, Object data) implements StateEvent {}
}
