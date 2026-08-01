package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.network.blueprint.S2CBlueprintStatusPayload;
import net.minecraft.world.level.Level;

import java.nio.file.Path;

/**
 * 协调蓝图捕获的异步保存启动、轮询和仓储写回。
 *
 * <p>本类不拥有捕获选区、面板弹窗、选中项或渲染状态。捕获状态机仍由
 * {@link BlueprintCaptureController} 管理，UI 只消费这里返回的完成结果。</p>
 */
final class BlueprintCaptureSaveCoordinator {
    private BlueprintCaptureSaveCoordinator() {
    }

    static void start(
            BlueprintCaptureController capture,
            Level level,
            String requestedName,
            StatusSink status) {
        if (capture.isSaving()) {
            status.set(
                    S2CBlueprintStatusPayload.INFO,
                    "screen.rtsbuilding.blueprints.status.save_busy",
                    "");
            return;
        }
        if (level == null) {
            status.set(
                    S2CBlueprintStatusPayload.ERROR,
                    "screen.rtsbuilding.blueprints.status.save_failed",
                    "No world");
            return;
        }
        if (!capture.isSelectionComplete()) {
            status.set(
                    S2CBlueprintStatusPayload.ERROR,
                    "screen.rtsbuilding.blueprints.status.capture_incomplete",
                    "");
            return;
        }

        String fileName = BlueprintPanelFiles.uniqueNbtFileName(requestedName);
        Path destination = BlueprintPanelFiles.blueprintFolder().resolve(fileName);
        try {
            capture.startSave(level, fileName, destination, status::set);
        } catch (Throwable throwable) {
            if (throwable instanceof Error error) {
                throw error;
            }
            status.set(
                    S2CBlueprintStatusPayload.ERROR,
                    "screen.rtsbuilding.blueprints.status.save_failed",
                    failureDetail(throwable));
        }
    }

    static Completion poll(
            BlueprintCaptureController capture,
            BlueprintLibraryRepository library) {
        BlueprintCaptureController.SaveResult result = capture.pollSaveResult();
        if (result == null) {
            return null;
        }
        if (!result.success()) {
            return new Completion(
                    "",
                    S2CBlueprintStatusPayload.ERROR,
                    result.messageKey(),
                    result.detail());
        }

        library.addOrReplace(result.path(), result.blueprint());
        return new Completion(
                result.fileName(),
                S2CBlueprintStatusPayload.SUCCESS,
                "screen.rtsbuilding.blueprints.status.saved_blueprint",
                result.fileName());
    }

    private static String failureDetail(Throwable throwable) {
        if (throwable == null) {
            return "Unknown error";
        }
        Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank()
                ? cause.getClass().getSimpleName()
                : message;
    }

    record Completion(
            String selectedFileName,
            byte status,
            String messageKey,
            String detail) {
    }

    @FunctionalInterface
    interface StatusSink {
        void set(byte status, String messageKey, String detail);
    }
}
