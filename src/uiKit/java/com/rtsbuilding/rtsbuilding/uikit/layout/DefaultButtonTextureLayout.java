package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiTextureState;

/**
 * Legacy {@code default_button.png} 的九宫切片定义。
 *
 * <p>本类只描述原素材已有的 4×4 状态块如何拉伸，不生成任何新轮廓。Palette 与
 * Legacy 必须共用这些源矩形；主题系统只允许改变源像素所属颜色，不允许改变切片、
 * 倒角、透明区或状态排列。</p>
 */
public final class DefaultButtonTextureLayout {
    public static final int SHEET_WIDTH = 4;
    public static final int SHEET_HEIGHT = 16;
    public static final int STATE_SIZE = 4;
    public static final int SLICE_COUNT = 9;

    public static Slice[] slices(UiRect target, UiTextureState state) {
        if (target == null || state == null) {
            throw new IllegalArgumentException("target and state must not be null");
        }
        if (target.getWidth() < 2.0D || target.getHeight() < 2.0D) {
            throw new IllegalArgumentException("default button target must be at least 2x2");
        }
        double x = target.getX();
        double y = target.getY();
        double width = target.getWidth();
        double height = target.getHeight();
        int v = stateV(state);
        return new Slice[] {
                slice(0, v, 1, 1, x, y, 1, 1),
                slice(1, v, 2, 1, x + 1, y, width - 2, 1),
                slice(3, v, 1, 1, x + width - 1, y, 1, 1),
                slice(0, v + 1, 1, 2, x, y + 1, 1, height - 2),
                slice(1, v + 1, 2, 2, x + 1, y + 1, width - 2, height - 2),
                slice(3, v + 1, 1, 2, x + width - 1, y + 1, 1, height - 2),
                slice(0, v + 3, 1, 1, x, y + height - 1, 1, 1),
                slice(1, v + 3, 2, 1, x + 1, y + height - 1, width - 2, 1),
                slice(3, v + 3, 1, 1, x + width - 1, y + height - 1, 1, 1)
        };
    }

    public static int stateV(UiTextureState state) {
        switch (state) {
            case HOVER: return STATE_SIZE;
            case PRESSED: return STATE_SIZE * 2;
            case ACTIVE: return STATE_SIZE * 3;
            case INACTIVE:
            default: return 0;
        }
    }

    private static Slice slice(
            double sx, double sy, double sw, double sh,
            double tx, double ty, double tw, double th) {
        return new Slice(new UiRect(sx, sy, sw, sh), new UiRect(tx, ty, tw, th));
    }

    public static final class Slice {
        private final UiRect source;
        private final UiRect target;

        private Slice(UiRect source, UiRect target) {
            this.source = source;
            this.target = target;
        }

        public UiRect source() { return source; }
        public UiRect target() { return target; }
    }

    private DefaultButtonTextureLayout() {
    }
}
