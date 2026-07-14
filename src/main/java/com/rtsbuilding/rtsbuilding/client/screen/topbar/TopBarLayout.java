package com.rtsbuilding.rtsbuilding.client.screen.topbar;

import java.util.EnumMap;
import java.util.Map;

/**
 * 顶部栏的纯像素布局源。
 *
 * <p>本类只决定按钮 X、统一按钮 Y、齿轮右对齐和两行状态文字坐标；不决定按钮是否激活、
 * 文本内容或点击行为。这样以后调整高缩放布局时不会在绘制、点击和弹窗锚点中漏改某个数字。</p>
 */
public final class TopBarLayout {
    public static final int LEFT_MARGIN = 8;
    public static final int RIGHT_MARGIN = 8;
    public static final int BUTTON_Y = 4;
    public static final int MODE_ACTION_GROUP_GAP = 8;
    public static final int STATUS_X = 8;
    public static final int STATUS_RIGHT_MARGIN = 8;
    public static final int STATUS_ROW_1_Y = 33;
    public static final int STATUS_ROW_2_Y = 44;

    private TopBarLayout() {
    }

    public static Buttons buttons(int screenWidth, int modeButtonWidth, int iconButtonWidth, int gap,
            boolean quickBuild, boolean questDetect, boolean rangeCulling, boolean developer) {
        EnumMap<TopBarTypes.TopBarButtonId, Integer> positions =
                new EnumMap<>(TopBarTypes.TopBarButtonId.class);
        int x = LEFT_MARGIN;
        x = put(positions, TopBarTypes.TopBarButtonId.INTERACT, x, modeButtonWidth, gap);
        x = put(positions, TopBarTypes.TopBarButtonId.LINK, x, modeButtonWidth, gap);
        x = put(positions, TopBarTypes.TopBarButtonId.FUNNEL, x, modeButtonWidth, gap);
        x = put(positions, TopBarTypes.TopBarButtonId.ROTATE, x, modeButtonWidth, gap);
        x += MODE_ACTION_GROUP_GAP;
        if (quickBuild) x = put(positions, TopBarTypes.TopBarButtonId.QUICK_BUILD, x, iconButtonWidth, gap);
        if (questDetect) x = put(positions, TopBarTypes.TopBarButtonId.QUEST_DETECT, x, iconButtonWidth, gap);
        x = put(positions, TopBarTypes.TopBarButtonId.CHUNK_VIEW, x, iconButtonWidth, gap);
        if (rangeCulling) x = put(positions, TopBarTypes.TopBarButtonId.RANGE_CULLING, x, iconButtonWidth, gap);
        x = put(positions, TopBarTypes.TopBarButtonId.GUIDE, x, iconButtonWidth, gap);
        if (developer) x = put(positions, TopBarTypes.TopBarButtonId.DEVELOPER, x, iconButtonWidth, gap);
        int gearX = screenWidth - iconButtonWidth - RIGHT_MARGIN;
        int opModeX = gearX - iconButtonWidth - gap;
        positions.put(TopBarTypes.TopBarButtonId.OPERATION_MODE, opModeX);
        positions.put(TopBarTypes.TopBarButtonId.GEAR, gearX);
        return new Buttons(Map.copyOf(positions));
    }

    public static Status status(int screenWidth) {
        return new Status(STATUS_X, Math.max(40, screenWidth - STATUS_X - STATUS_RIGHT_MARGIN),
                STATUS_ROW_1_Y, STATUS_ROW_2_Y);
    }

    private static int put(EnumMap<TopBarTypes.TopBarButtonId, Integer> positions,
            TopBarTypes.TopBarButtonId id, int x, int width, int gap) {
        positions.put(id, x);
        return x + width + gap;
    }

    public record Buttons(Map<TopBarTypes.TopBarButtonId, Integer> xById) {
        public int x(TopBarTypes.TopBarButtonId id) {
            Integer x = xById.get(id);
            if (x == null) throw new IllegalArgumentException("Button is not present in this layout: " + id);
            return x;
        }
    }

    public record Status(int x, int width, int row1Y, int row2Y) {
    }
}
