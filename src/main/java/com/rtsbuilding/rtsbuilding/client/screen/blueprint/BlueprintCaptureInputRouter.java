package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import com.rtsbuilding.rtsbuilding.client.screen.selection.RtsSelectionNudge;
import com.rtsbuilding.rtsbuilding.network.blueprint.S2CBlueprintStatusPayload;
import org.lwjgl.input.Keyboard;

/**
 * 蓝图捕获会话的键盘输入路由。
 *
 * <p>本类只解释捕获期间的取消、确认和选择框微调键，不拥有捕获区域、保存任务或弹窗状态。
 * 保存与取消继续由 BlueprintPanel 提交，区域变化继续由 BlueprintCaptureController 执行。
 * 捕获激活后未识别的按键仍会被吞掉，保持原先不会泄漏到相机和世界的行为。</p>
 */
final class BlueprintCaptureInputRouter {
    private BlueprintCaptureInputRouter() {
    }

    static boolean keyPressed(
            BlueprintCaptureController capture,
            int keyCode,
            int scanCode,
            BlueprintCaptureController.StatusSink status,
            Runnable cancelCapture,
            Runnable saveCapture) {
        if (!capture.isActive()) {
            return false;
        }
        if (capture.isSaving()) {
            status.set(
                    S2CBlueprintStatusPayload.INFO,
                    "screen.rtsbuilding.blueprints.status.save_busy",
                    "");
            return true;
        }
        boolean cancelKey =
                ClientKeyMappings.BLUEPRINT_CANCEL.getKeyCode() == keyCode
                        || keyCode == Keyboard.KEY_ESCAPE;
        if (cancelKey) {
            if (!capture.releaseActiveHandle()) {
                cancelCapture.run();
            }
            return true;
        }
        if (keyCode == Keyboard.KEY_RETURN
                || keyCode == Keyboard.KEY_NUMPADENTER) {
            saveCapture.run();
            return true;
        }
        RtsSelectionNudge.Delta delta =
                RtsSelectionNudge.fromKey(keyCode, scanCode);
        if (delta != null && capture.isSelectionComplete()) {
            capture.moveSelection(
                    delta.dx(), delta.dy(), delta.dz(), status);
        }
        return true;
    }
}
