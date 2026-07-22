package com.rtsbuilding.rtsbuilding.client.screen.topbar;

import com.rtsbuilding.rtsbuilding.uikit.layout.RtsMainlineLayout;

import java.util.EnumMap;
import java.util.Map;

/**
 * 顶部栏的纯像素布局源。
 *
 * <p>本类只决定按钮 X、统一按钮 Y、齿轮右对齐和两行状态文字坐标；不决定按钮是否激活、
 * 文本内容或点击行为。这样以后调整高缩放布局时不会在绘制、点击和弹窗锚点中漏改某个数字。</p>
 */
public final class TopBarLayout {
    public static final int LEFT_MARGIN = RtsMainlineLayout.TOP_LEFT_MARGIN;
    public static final int RIGHT_MARGIN = RtsMainlineLayout.TOP_RIGHT_MARGIN;
    public static final int BUTTON_Y = RtsMainlineLayout.TOP_BUTTON_Y;
    public static final int MODE_ACTION_GROUP_GAP = RtsMainlineLayout.TOP_MODE_ACTION_GROUP_GAP;
    public static final int STATUS_X = RtsMainlineLayout.TOP_STATUS_X;
    public static final int STATUS_RIGHT_MARGIN = RtsMainlineLayout.TOP_STATUS_RIGHT_MARGIN;
    public static final int STATUS_ROW_1_Y = RtsMainlineLayout.TOP_STATUS_ROW_1_Y;
    public static final int STATUS_ROW_2_Y = RtsMainlineLayout.TOP_STATUS_ROW_2_Y;

    private TopBarLayout() {
    }

    public static Buttons buttons(int screenWidth, int modeButtonWidth, int iconButtonWidth, int gap,
            boolean quickBuild, boolean questDetect, boolean rangeCulling, boolean developer) {
        EnumMap<TopBarTypes.TopBarButtonId, Integer> positions =
                new EnumMap<>(TopBarTypes.TopBarButtonId.class);
        if (modeButtonWidth != RtsMainlineLayout.TOP_MODE_BUTTON_W
                || iconButtonWidth != RtsMainlineLayout.TOP_ICON_BUTTON_W
                || gap != RtsMainlineLayout.TOP_BUTTON_GAP) {
            throw new IllegalArgumentException("顶部栏生产尺寸必须来自 RtsMainlineLayout");
        }
        RtsMainlineLayout.TopButtons shared = RtsMainlineLayout.topButtons(
                screenWidth, quickBuild, questDetect, rangeCulling, developer);
        for (TopBarTypes.TopBarButtonId id : TopBarTypes.TopBarButtonId.values()) {
            int index = layoutIndex(id);
            if (index >= 0 && shared.isPresent(index)) {
                positions.put(id, shared.x(index));
            }
        }
        return new Buttons(Map.copyOf(positions));
    }

    public static Status status(int screenWidth) {
        RtsMainlineLayout.TopStatus shared = RtsMainlineLayout.topStatus(screenWidth);
        return new Status(shared.x, shared.width, shared.row1Y, shared.row2Y);
    }

    /**
     * 将模式提示贴到状态栏右侧；左侧状态没有留下完整提示所需空间时不显示。
     */
    public static int contextualHintX(Status status, int leftTextWidth, int hintWidth, int gap) {
        RtsMainlineLayout.TopStatus shared = RtsMainlineLayout.topStatus(
                status.x() + status.width() + STATUS_RIGHT_MARGIN);
        return RtsMainlineLayout.contextualHintX(shared, leftTextWidth, hintWidth, gap);
    }

    private static int layoutIndex(TopBarTypes.TopBarButtonId id) {
        return switch (id) {
            case INTERACT -> 0;
            case LINK -> 1;
            case FUNNEL -> 2;
            case ROTATE -> 3;
            case QUICK_BUILD -> 4;
            case QUEST_DETECT -> 5;
            case CHUNK_VIEW -> 6;
            case RANGE_CULLING -> 7;
            case GUIDE -> 8;
            case DEVELOPER -> 9;
            case GEAR -> 10;
            default -> -1;
        };
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
