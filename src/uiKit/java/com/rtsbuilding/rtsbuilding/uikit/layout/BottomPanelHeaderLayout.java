package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.bottom.BottomBarUiTab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 底栏页签、选中状态与右侧入口的纯 Java 头部布局。
 *
 * <p>这些控件共享同一条有限宽度：页签从左侧依次展开，选中状态占中间空间，插件、
 * 刷新和指南入口从右侧反向保留。本类统一它们的绘制矩形、可见性和半开命中，不读取
 * Minecraft 字体、配置或控制器，也不执行切页、刷新和开窗动作。</p>
 */
public final class BottomPanelHeaderLayout {
    public static final int HEADER_HEIGHT = RtsMainlineLayout.BOTTOM_PANEL_HEADER_H;
    public static final int LOGO_X_INSET = 8;
    public static final int LOGO_Y_INSET = 5;
    public static final int TAB_START_X_INSET = 38;
    public static final int TAB_Y_INSET = 2;
    public static final int TAB_HEIGHT = HEADER_HEIGHT - 3;
    public static final int TAB_GAP = 4;
    public static final int TAB_TEXT_INSET = 4;
    public static final int STATUS_GAP = 10;
    public static final int STATUS_Y_INSET = 6;
    public static final int STATUS_MIN_WIDTH = 96;
    public static final int STATUS_MAX_WIDTH = 190;
    public static final int STATUS_TEXT_PADDING = 8;
    public static final int STATUS_RIGHT_RESERVE = 126;
    public static final int ACTION_SIZE = 12;
    public static final int ACTION_Y_INSET = 3;
    public static final int GUIDE_RIGHT_INSET = 20;
    public static final int ACTION_GAP = 4;
    public static final int PLUGIN_WIDTH = 72;
    public static final int PLUGIN_GAP = 6;
    public static final int PLUGIN_STATUS_MIN_SPACE = 72;
    public static final int TAB_ANIMATION_INSET = 1;

    public final Area panel;
    public final Area header;
    public final List<TabArea> tabs;
    public final Area selectedStatus;
    public final Area refresh;
    public final Area guide;
    public final Area plugin;
    public final boolean pluginVisible;

    private BottomPanelHeaderLayout(
            Area panel,
            Area header,
            List<TabArea> tabs,
            Area selectedStatus,
            Area refresh,
            Area guide,
            Area plugin,
            boolean pluginVisible) {
        this.panel = panel;
        this.header = header;
        this.tabs = Collections.unmodifiableList(new ArrayList<TabArea>(tabs));
        this.selectedStatus = selectedStatus;
        this.refresh = refresh;
        this.guide = guide;
        this.plugin = plugin;
        this.pluginVisible = pluginVisible;
    }

    public static BottomPanelHeaderLayout resolve(
            int panelX,
            int panelY,
            int panelWidth,
            int panelHeight,
            boolean creativeAccess,
            boolean blueprintAccess,
            int selectedTextWidth,
            boolean pluginRequested) {
        requirePositive(panelWidth, "panelWidth");
        requirePositive(panelHeight, "panelHeight");

        Area panel = new Area(panelX, panelY, panelWidth, panelHeight);
        Area header = new Area(
                panelX + 1, panelY + 1,
                Math.max(0, panelWidth - 2),
                Math.min(Math.max(0, panelHeight - 1), HEADER_HEIGHT - 1));
        List<TabArea> tabs = new ArrayList<TabArea>();
        int tabX = panelX + TAB_START_X_INSET;
        if (creativeAccess) {
            tabs.add(new TabArea(
                    BottomBarUiTab.CREATIVE,
                    new Area(tabX, panelY + TAB_Y_INSET,
                            tabWidth(BottomBarUiTab.CREATIVE), TAB_HEIGHT)));
            tabX += tabWidth(BottomBarUiTab.CREATIVE) + TAB_GAP;
        }
        tabs.add(new TabArea(
                BottomBarUiTab.STORAGE,
                new Area(tabX, panelY + TAB_Y_INSET,
                        tabWidth(BottomBarUiTab.STORAGE), TAB_HEIGHT)));
        tabX += tabWidth(BottomBarUiTab.STORAGE) + TAB_GAP;
        if (blueprintAccess) {
            tabs.add(new TabArea(
                    BottomBarUiTab.BLUEPRINTS,
                    new Area(tabX, panelY + TAB_Y_INSET,
                            tabWidth(BottomBarUiTab.BLUEPRINTS), TAB_HEIGHT)));
            tabX += tabWidth(BottomBarUiTab.BLUEPRINTS) + TAB_GAP;
        }

        int statusX = tabs.get(tabs.size() - 1).area.right() + STATUS_GAP;
        int available = Math.max(
                0, panelX + panelWidth - statusX - STATUS_RIGHT_RESERVE);
        int statusWidth = available <= 0
                ? 0
                : clamp(
                        Math.max(0, selectedTextWidth) + STATUS_TEXT_PADDING,
                        Math.min(STATUS_MIN_WIDTH,
                                Math.min(STATUS_MAX_WIDTH, available)),
                        Math.min(STATUS_MAX_WIDTH, available));
        Area selectedStatus = new Area(
                statusX, panelY + STATUS_Y_INSET,
                statusWidth,
                Math.max(0, HEADER_HEIGHT - STATUS_Y_INSET));

        int guideX = panelX + panelWidth - GUIDE_RIGHT_INSET;
        Area guide = new Area(
                guideX, panelY + ACTION_Y_INSET, ACTION_SIZE, ACTION_SIZE);
        Area refresh = new Area(
                guideX - ACTION_SIZE - ACTION_GAP,
                panelY + ACTION_Y_INSET,
                ACTION_SIZE,
                ACTION_SIZE);
        Area plugin = new Area(
                refresh.x - PLUGIN_WIDTH - PLUGIN_GAP,
                panelY + ACTION_Y_INSET,
                PLUGIN_WIDTH,
                ACTION_SIZE);
        boolean pluginVisible = pluginRequested
                && plugin.x > statusX + PLUGIN_STATUS_MIN_SPACE;
        return new BottomPanelHeaderLayout(
                panel, header, tabs, selectedStatus,
                refresh, guide, plugin, pluginVisible);
    }

    public int logoX() {
        return panel.x + LOGO_X_INSET;
    }

    public int logoY() {
        return panel.y + LOGO_Y_INSET;
    }

    public BottomBarUiTab tabAt(double mouseX, double mouseY) {
        for (TabArea tab : tabs) {
            if (tab.area.contains(mouseX, mouseY)) {
                return tab.tab;
            }
        }
        return null;
    }

    public Control controlAt(double mouseX, double mouseY) {
        if (refresh.contains(mouseX, mouseY)) {
            return Control.REFRESH;
        }
        if (guide.contains(mouseX, mouseY)) {
            return Control.OPEN_GUIDE;
        }
        if (pluginVisible && plugin.contains(mouseX, mouseY)) {
            return Control.OPEN_PLUGINS;
        }
        return null;
    }

    public boolean containsHeader(double mouseX, double mouseY) {
        return mouseX >= panel.x && mouseX < panel.x + panel.width
                && mouseY >= panel.y
                && mouseY < panel.y + Math.min(panel.height, HEADER_HEIGHT);
    }

    public static int tabWidth(BottomBarUiTab tab) {
        if (tab == BottomBarUiTab.CREATIVE) {
            return 58;
        }
        return tab == BottomBarUiTab.STORAGE ? 76 : 86;
    }

    /**
     * 供同一底栏下的子布局复用半开矩形语义，避免每个布局重新实现边界判断。
     */
    public static Area area(int x, int y, int width, int height) {
        return new Area(x, y, width, height);
    }

    public enum Control {
        REFRESH,
        OPEN_GUIDE,
        OPEN_PLUGINS
    }

    public static final class TabArea {
        public final BottomBarUiTab tab;
        public final Area area;

        private TabArea(BottomBarUiTab tab, Area area) {
            this.tab = tab;
            this.area = area;
        }
    }

    public static final class Area {
        public final int x;
        public final int y;
        public final int width;
        public final int height;

        private Area(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public int right() {
            return x + width;
        }

        public boolean contains(double mouseX, double mouseY) {
            return width > 0 && height > 0
                    && mouseX >= x && mouseX < x + width
                    && mouseY >= y && mouseY < y + height;
        }
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
