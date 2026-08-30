package com.rtsbuilding.rtsbuilding.client.diagnostic;

import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.common.entity.RtsCameraEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.Optional;

/**
 * RTS 摄像机所有权低侵入诊断的客户端出口。
 *
 * <p>这里仅观察摄像机 setter 与每帧最终所有权，并把日志聚合委托给纯状态机。
 * 它不取消或替换任何摄像机调用，不负责抢回摄像机，也不参与输入、恢复冷却
 * 或渲染；第三方模组即使行为有问题，业务兼容策略仍由各自模组负责。</p>
 */
public final class RtsCameraOwnershipDiagnostics {
    private static final CameraOwnershipDiagnosticState STATE = new CameraOwnershipDiagnosticState();
    private static Entity expectedMirrorCamera;

    private RtsCameraOwnershipDiagnostics() {
    }

    /**
     * 在本地镜像摄像机已经准备好后观察一次所有权。
     *
     * <p>expected 必须来自当前世界的 RTS 镜像实体；实体描述只在需要输出事件
     * 时读取，正常帧不会产生日志。观察本身不会触发任何恢复操作。</p>
     */
    public static void observeFrame(Minecraft minecraft, Entity expected, Entity actual) {
        if (!hasActiveObservationSession(minecraft) || !isUsableMirror(minecraft, expected)) {
            clearSession();
            return;
        }
        if (expectedMirrorCamera != expected) {
            clearSession();
        }
        expectedMirrorCamera = expected;

        CameraOwnershipDiagnosticState.Transition transition = STATE.observe(
                expected == actual, System.nanoTime());
        if (transition != CameraOwnershipDiagnosticState.Transition.NONE) {
            logOwnershipTransition(transition, minecraft, expected, actual);
        }
    }

    /**
     * 由 Minecraft#setCameraEntity 的客户端 Mixin 调用，只收集首次外部来源。
     * setter 的目标、返回值和执行顺序不会被修改；高频调用也只遍历一次调用栈。
     */
    public static void observeExternalCameraSetter(Entity target) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null
                || !ClientRtsController.get().isEnabled()
                || minecraft.level == null
                || minecraft.player == null) {
            clearSession();
            return;
        }
        if (target == expectedMirrorCamera) {
            STATE.recordRestoreAttempt();
            return;
        }
        if (!minecraft.player.isAlive() || minecraft.player.isDeadOrDying()) {
            clearSession();
            return;
        }
        if (isUsableMirror(minecraft, expectedMirrorCamera)
                && minecraft.getCameraEntity() == expectedMirrorCamera
                && STATE.claimCallerCapture()) {
            STATE.rememberCaller(findFirstExternalCaller());
        }
    }

    /** 清理 RTS 会话诊断，不读取或改变摄像机。 */
    public static void clearSession() {
        expectedMirrorCamera = null;
        STATE.reset();
    }

    /** 将所有权事件格式化为有限摘要；正常帧不会进入此方法。 */
    private static void logOwnershipTransition(
            CameraOwnershipDiagnosticState.Transition transition,
            Minecraft minecraft,
            Entity expected,
            Entity actual) {
        String event = switch (transition) {
            case LOSS -> "ownership_lost";
            case REMINDER -> "ownership_still_lost";
            case RECOVERY -> "ownership_recovered";
            case NONE -> "ownership_stable";
        };
        String message = "[RTS Camera Diagnostics] {} expected_type={} actual_type={} screen={} riding={} "
                + "consecutive_observations={} restore_attempts={} continuous_observations={} caller={}";
        Object[] arguments = {
                event,
                entityType(expected),
                entityType(actual),
                screenType(minecraft),
                isRiding(minecraft),
                STATE.consecutiveMismatchObservations(),
                STATE.restoreAttempts(),
                STATE.continuousObservations(),
                STATE.firstCaller()
        };
        if (transition == CameraOwnershipDiagnosticState.Transition.RECOVERY) {
            RtsbuildingMod.LOGGER.info(message, arguments);
        } else {
            RtsbuildingMod.LOGGER.warn(message, arguments);
        }
    }

    private static boolean hasActiveObservationSession(Minecraft minecraft) {
        return minecraft != null
                && minecraft.level != null
                && minecraft.player != null
                && minecraft.player.isAlive()
                && !minecraft.player.isDeadOrDying()
                && ClientRtsController.get().isEnabled();
    }

    private static boolean isUsableMirror(Minecraft minecraft, Entity mirror) {
        return minecraft != null
                && minecraft.level != null
                && mirror instanceof RtsCameraEntity
                && mirror.level() == minecraft.level
                && !mirror.isRemoved();
    }

    /**
     * 只取首个有意义的外部类名，不把完整栈或方法/路径写入正式日志。
     * Minecraft、RTS 自身、Mixin 和 Java 基础设施帧都不是责任来源。
     */
    private static String findFirstExternalCaller() {
        try {
            return StackWalker.getInstance().walk(frames -> frames
                    .map(StackWalker.StackFrame::getClassName)
                    .filter(name -> !isIgnoredFrame(name))
                    .findFirst()
                    .orElse("unknown"));
        } catch (RuntimeException ignored) {
            return "unknown";
        }
    }

    private static boolean isIgnoredFrame(String className) {
        // 只保留最早的外部模组类名，避免把 setter/Mixin/基础设施误报成责任来源。
        return className.equals(RtsCameraOwnershipDiagnostics.class.getName())
                || className.equals("com.rtsbuilding.rtsbuilding.mixin.MinecraftCameraEntityMixin")
                || className.startsWith("com.rtsbuilding.rtsbuilding.")
                || className.startsWith("net.minecraft.")
                || className.startsWith("net.neoforged.")
                || className.startsWith("com.mojang.")
                || className.startsWith("org.spongepowered.asm.mixin.")
                || className.startsWith("java.")
                || className.startsWith("javax.")
                || className.startsWith("jdk.")
                || className.startsWith("sun.")
                || className.startsWith("com.sun.");
    }

    /** 实体描述只使用类型对象，不访问位置、level 或其他可能失效的状态。 */
    private static String entityType(Entity entity) {
        if (entity == null) {
            return "null";
        }
        try {
            return Optional.ofNullable(entity.getType())
                    .map(String::valueOf)
                    .filter(value -> !value.isBlank())
                    .orElse("unknown");
        } catch (RuntimeException ignored) {
            return "unknown";
        }
    }

    private static String screenType(Minecraft minecraft) {
        // 只记录界面类名，不读取界面内容或玩家输入，避免隐私和额外副作用。
        return minecraft == null || minecraft.screen == null
                ? "none"
                : minecraft.screen.getClass().getSimpleName();
    }

    private static boolean isRiding(Minecraft minecraft) {
        // isPassenger 是当前实体的轻量状态查询，不依赖摄像机实体仍在 level 中。
        return minecraft != null && minecraft.player != null && minecraft.player.isPassenger();
    }
}
