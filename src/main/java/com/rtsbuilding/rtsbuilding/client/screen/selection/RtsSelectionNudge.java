package com.rtsbuilding.rtsbuilding.client.screen.selection;

import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Direction;
import org.lwjgl.glfw.GLFW;

/**
 * 统一处理世界空间选择框的键盘微调。
 *
 * <p>蓝图预览、范围剔除盒、快速建造/破坏选择框都需要“按当前镜头方向理解上下左右”的手感。
 * 这里不保存任何业务状态，只把一次按键转换成方块坐标增量。</p>
 */
public final class RtsSelectionNudge {
    private RtsSelectionNudge() {
    }

    public static Delta fromKey(int keyCode, int scanCode) {
        int step = fastStep();
        Direction forward = currentHorizontalFacingDirection();
        Direction right = rightOf(forward);
        if (ClientKeyMappings.SELECTION_NUDGE_FORWARD.matches(keyCode, scanCode)
                || keyCode == GLFW.GLFW_KEY_KP_8) {
            return Delta.of(forward, step);
        }
        if (ClientKeyMappings.SELECTION_NUDGE_BACK.matches(keyCode, scanCode)
                || keyCode == GLFW.GLFW_KEY_KP_2) {
            return Delta.of(forward, -step);
        }
        if (ClientKeyMappings.SELECTION_NUDGE_LEFT.matches(keyCode, scanCode)
                || keyCode == GLFW.GLFW_KEY_KP_4) {
            return Delta.of(right, -step);
        }
        if (ClientKeyMappings.SELECTION_NUDGE_RIGHT.matches(keyCode, scanCode)
                || keyCode == GLFW.GLFW_KEY_KP_6) {
            return Delta.of(right, step);
        }
        if (ClientKeyMappings.SELECTION_NUDGE_UP.matches(keyCode, scanCode)) {
            return new Delta(0, step, 0);
        }
        if (ClientKeyMappings.SELECTION_NUDGE_DOWN.matches(keyCode, scanCode)) {
            return new Delta(0, -step, 0);
        }
        return null;
    }

    private static int fastStep() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.getWindow() == null) {
            return 1;
        }
        long window = minecraft.getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS
                ? 4 : 1;
    }

    private static Direction currentHorizontalFacingDirection() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.gameRenderer != null) {
            return Direction.fromYRot(minecraft.gameRenderer.getMainCamera().yRot());
        }
        if (minecraft != null && minecraft.getCameraEntity() != null) {
            return Direction.fromYRot(minecraft.getCameraEntity().getYRot());
        }
        if (minecraft != null && minecraft.player != null) {
            return Direction.fromYRot(minecraft.player.getYRot());
        }
        return Direction.SOUTH;
    }

    private static Direction rightOf(Direction forward) {
        return switch (forward) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            default -> Direction.WEST;
        };
    }

    public record Delta(int dx, int dy, int dz) {
        static Delta of(Direction direction, int amount) {
            return new Delta(
                    direction.getStepX() * amount,
                    direction.getStepY() * amount,
                    direction.getStepZ() * amount);
        }
    }
}
