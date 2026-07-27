package com.rtsbuilding.rtsbuilding.client.screen.selection;

import com.rtsbuilding.rtsbuilding.client.bootstrap.ClientKeyMappings;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumFacing;
import org.lwjgl.input.Keyboard;

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
        EnumFacing forward = currentHorizontalFacingDirection();
        EnumFacing right = rightOf(forward);
        if (matches(ClientKeyMappings.SELECTION_NUDGE_FORWARD, keyCode)
                || keyCode == Keyboard.KEY_NUMPAD8) {
            return Delta.of(forward, step);
        }
        if (matches(ClientKeyMappings.SELECTION_NUDGE_BACK, keyCode)
                || keyCode == Keyboard.KEY_NUMPAD2) {
            return Delta.of(forward, -step);
        }
        if (matches(ClientKeyMappings.SELECTION_NUDGE_LEFT, keyCode)
                || keyCode == Keyboard.KEY_NUMPAD4) {
            return Delta.of(right, -step);
        }
        if (matches(ClientKeyMappings.SELECTION_NUDGE_RIGHT, keyCode)
                || keyCode == Keyboard.KEY_NUMPAD6) {
            return Delta.of(right, step);
        }
        if (matches(ClientKeyMappings.SELECTION_NUDGE_UP, keyCode)) {
            return new Delta(0, step, 0);
        }
        if (matches(ClientKeyMappings.SELECTION_NUDGE_DOWN, keyCode)) {
            return new Delta(0, -step, 0);
        }
        return null;
    }

    private static int fastStep() {
        return Keyboard.isKeyDown(Keyboard.KEY_LMENU)
                || Keyboard.isKeyDown(Keyboard.KEY_RMENU)
                ? 4 : 1;
    }

    private static EnumFacing currentHorizontalFacingDirection() {
        Minecraft minecraft = Minecraft.getMinecraft();
        Entity camera = minecraft == null ? null : minecraft.getRenderViewEntity();
        if (camera != null) {
            return EnumFacing.fromAngle(camera.rotationYaw);
        }
        if (minecraft != null && minecraft.player != null) {
            return EnumFacing.fromAngle(minecraft.player.rotationYaw);
        }
        return EnumFacing.SOUTH;
    }

    private static EnumFacing rightOf(EnumFacing forward) {
        switch (forward) {
            case NORTH: return EnumFacing.EAST;
            case EAST: return EnumFacing.SOUTH;
            case SOUTH: return EnumFacing.WEST;
            case WEST: return EnumFacing.NORTH;
            default: return EnumFacing.WEST;
        }
    }

    private static boolean matches(net.minecraft.client.settings.KeyBinding binding, int keyCode) {
        return binding != null && binding.getKeyCode() == keyCode;
    }

    public static final class Delta {
        private final int dx;
        private final int dy;
        private final int dz;

        private Delta(int dx, int dy, int dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }

        public int dx() { return this.dx; }
        public int dy() { return this.dy; }
        public int dz() { return this.dz; }

        static Delta of(EnumFacing direction, int amount) {
            return new Delta(
                    direction.getXOffset() * amount,
                    direction.getYOffset() * amount,
                    direction.getZOffset() * amount);
        }
    }
}
