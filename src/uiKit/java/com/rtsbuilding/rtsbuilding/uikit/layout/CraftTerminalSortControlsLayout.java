package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.craftterminal.CraftTerminalUiAction;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;

/**
 * 合成终端右侧两个排序按钮的唯一几何契约。
 *
 * <p>按钮沿终端右侧轨道纵向排列，并采用与顶栏图标一致的 24×24 可点击尺寸。
 * 生产端直接绘制预处理完成的 24×24 素材；本类不决定字符、颜色、动画或实际
 * 排序操作。正式输入、正式渲染和离屏预览必须共用这里的半开命中区域。</p>
 */
public final class CraftTerminalSortControlsLayout {
    public static final int BUTTON_X = 197;
    public static final int BUTTON_WIDTH = 24;
    public static final int BUTTON_HEIGHT = 24;
    public static final int BUTTON_GAP = 2;
    public static final int FIRST_BUTTON_Y_OFFSET = 20;

    private CraftTerminalSortControlsLayout() {
    }

    public static Geometry resolve(int visualTop) {
        UiRect field = new UiRect(
                BUTTON_X,
                visualTop + FIRST_BUTTON_Y_OFFSET,
                BUTTON_WIDTH,
                BUTTON_HEIGHT);
        UiRect direction = new UiRect(
                BUTTON_X,
                field.bottom() + BUTTON_GAP,
                BUTTON_WIDTH,
                BUTTON_HEIGHT);
        return new Geometry(field, direction);
    }

    public static final class Geometry {
        public final UiRect field;
        public final UiRect direction;

        private Geometry(UiRect field, UiRect direction) {
            this.field = field;
            this.direction = direction;
        }

        /** 按视觉从上到下解析两个不重叠的半开命中区。 */
        public CraftTerminalUiAction actionAt(double x, double y) {
            if (this.field.contains(x, y)) {
                return CraftTerminalUiAction.SORT;
            }
            if (this.direction.contains(x, y)) {
                return CraftTerminalUiAction.SORT_DIRECTION;
            }
            return null;
        }

        public UiRect bounds(CraftTerminalUiAction action) {
            if (action == CraftTerminalUiAction.SORT) {
                return this.field;
            }
            if (action == CraftTerminalUiAction.SORT_DIRECTION) {
                return this.direction;
            }
            throw new IllegalArgumentException("not a craft-terminal sort action: " + action);
        }
    }
}
