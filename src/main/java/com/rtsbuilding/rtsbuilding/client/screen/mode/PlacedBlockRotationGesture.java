package com.rtsbuilding.rtsbuilding.client.screen.mode;

import net.minecraft.core.Direction;
import org.lwjgl.glfw.GLFW;

/**
 * 玩家眼中的四种增量旋转按钮。
 *
 * <p>左右始终绕世界 Y 轴旋转；上下面向摄像机所在的竖直平面，
 * 因此绕“画面右侧”对应的带符号水平轴旋转。枚举只描述输入意图，
 * 不持有目标方块或网络状态。</p>
 */
public enum PlacedBlockRotationGesture {
    HORIZONTAL_LEFT,
    HORIZONTAL_RIGHT,
    VERTICAL_UP,
    VERTICAL_DOWN;

    public Direction axisDirection(Direction cameraForward) {
        return switch (this) {
            case HORIZONTAL_LEFT, HORIZONTAL_RIGHT -> Direction.UP;
            case VERTICAL_UP, VERTICAL_DOWN -> rightOf(cameraForward);
        };
    }

    public int quarterTurns() {
        return switch (this) {
            case HORIZONTAL_LEFT, VERTICAL_DOWN -> -1;
            case HORIZONTAL_RIGHT, VERTICAL_UP -> 1;
        };
    }

    public static PlacedBlockRotationGesture fromKey(int keyCode) {
        return switch (keyCode) {
            case GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_KP_4 -> HORIZONTAL_LEFT;
            case GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_KP_6 -> HORIZONTAL_RIGHT;
            case GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_KP_8 -> VERTICAL_UP;
            case GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_KP_2 -> VERTICAL_DOWN;
            default -> null;
        };
    }

    public static Direction rightOf(Direction forward) {
        return switch (forward) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            default -> Direction.EAST;
        };
    }
}
