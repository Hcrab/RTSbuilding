package com.rtsbuilding.rtsbuilding.client.screen.mode;

import com.rtsbuilding.rtsbuilding.platform.math.EnumFacing;
import org.lwjgl.input.Keyboard;

/**
 * 玩家眼中的四种增量旋转手势。
 *
 * <p>左右始终绕世界 Y 轴旋转；上下面向摄像机所在的竖直平面，
 * 因此绕“画面右侧”对应的带符号水平轴旋转。本枚举只描述输入意图，
 * 不持有目标方块、界面或网络状态。</p>
 */
public enum PlacedBlockRotationGesture {
    HORIZONTAL_LEFT,
    HORIZONTAL_RIGHT,
    VERTICAL_UP,
    VERTICAL_DOWN;

    public EnumFacing axisDirection(EnumFacing cameraForward) {
        switch (this) {
            case HORIZONTAL_LEFT:
            case HORIZONTAL_RIGHT:
                return EnumFacing.UP;
            case VERTICAL_UP:
            case VERTICAL_DOWN:
                return rightOf(cameraForward);
            default:
                throw new AssertionError(this);
        }
    }

    public int quarterTurns() {
        switch (this) {
            case HORIZONTAL_RIGHT:
            case VERTICAL_DOWN:
                return -1;
            case HORIZONTAL_LEFT:
            case VERTICAL_UP:
                return 1;
            default:
                throw new AssertionError(this);
        }
    }

    /**
     * 1.12 的界面键码来自 LWJGL2；方向键和数字小键盘保留相同语义。
     */
    public static PlacedBlockRotationGesture fromKey(int keyCode) {
        switch (keyCode) {
            case Keyboard.KEY_LEFT:
            case Keyboard.KEY_NUMPAD4:
                return HORIZONTAL_LEFT;
            case Keyboard.KEY_RIGHT:
            case Keyboard.KEY_NUMPAD6:
                return HORIZONTAL_RIGHT;
            case Keyboard.KEY_UP:
            case Keyboard.KEY_NUMPAD8:
                return VERTICAL_UP;
            case Keyboard.KEY_DOWN:
            case Keyboard.KEY_NUMPAD2:
                return VERTICAL_DOWN;
            default:
                return null;
        }
    }

    public static EnumFacing rightOf(EnumFacing forward) {
        if (forward == null) {
            return EnumFacing.EAST;
        }
        switch (forward) {
            case NORTH:
                return EnumFacing.EAST;
            case EAST:
                return EnumFacing.SOUTH;
            case SOUTH:
                return EnumFacing.WEST;
            case WEST:
                return EnumFacing.NORTH;
            default:
                return EnumFacing.EAST;
        }
    }
}
