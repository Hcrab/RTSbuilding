package com.rtsbuilding.rtsbuilding.client.screen.topbar;


/**
 * Container for top-bar data types.
 * <p>
 * Groups the button identifier enum and the layout parameter record that are
 * always used together by {@link TopBarPanel}, {@link TopBarIconRenderer},
 * {@link com.rtsbuilding.rtsbuilding.client.screen.BuilderScreen},
 * and the {@link com.rtsbuilding.rtsbuilding.client.screen.guide.GuidePanel guide system}.
 * <p>
 * <b>Why combined:</b> {@link TopBarButtonLayout} references {@link TopBarButtonId}
 * directly in its single field, and every call site imports both types from the
 * same package. Keeping them in one file reduces file count without hurting clarity.
 */
public final class TopBarTypes {

    /**
     * Top-bar button identifier enum.
     * <p>
     * Defines every possible button type in the top bar, used for layout construction,
     * icon rendering dispatch, and click-event routing.
     */
    public enum TopBarButtonId {
        INTERACT,
        LINK,
        FUNNEL,
        ROTATE,
        QUICK_BUILD,
        CHUNK_VIEW,
        RANGE_CULLING,
        GEAR,
        SENSITIVITY,
        AUTO_STORE,
        SHAPE,
        SHAPE_ROTATE,
        GUIDE,
        DEVELOPER,
        QUEST_DETECT
    }

    /**
     * Top-bar button layout parameters (immutable).
     * <p>
     * Defines the on-screen position, width, label, and visual state of a single
     * top-bar button. Produced by {@link TopBarPanel#buildTopBarButtonLayouts()}
     * and consumed by its render and click methods.
     *
     * @param id       the button identifier
     * @param x        button left-edge X coordinate
     * @param width    button width in pixels
     * @param label    display label (empty for icon-only buttons)
     * @param iconOnly true if this button draws an icon instead of a text label
     * @param active   true if the button should appear highlighted (toggled on)
     */
    public static final class TopBarButtonLayout {
        private final TopBarButtonId id;
        private final int x;
        private final int width;
        private final String label;
        private final boolean iconOnly;
        private final boolean active;

        public TopBarButtonLayout(TopBarButtonId id, int x, int width, String label,
                                  boolean iconOnly, boolean active) {
            if (id == null) throw new IllegalArgumentException("id");
            this.id = id;
            this.x = x;
            this.width = width;
            this.label = label == null ? "" : label;
            this.iconOnly = iconOnly;
            this.active = active;
        }

        public TopBarButtonId id() { return id; }
        public int x() { return x; }
        public int width() { return width; }
        public String label() { return label; }
        public boolean iconOnly() { return iconOnly; }
        public boolean active() { return active; }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof TopBarButtonLayout)) return false;
            TopBarButtonLayout that = (TopBarButtonLayout) other;
            return x == that.x && width == that.width && iconOnly == that.iconOnly
                    && active == that.active && id == that.id && label.equals(that.label);
        }

        @Override
        public int hashCode() {
            int result = id.hashCode();
            result = 31 * result + x;
            result = 31 * result + width;
            result = 31 * result + label.hashCode();
            result = 31 * result + (iconOnly ? 1 : 0);
            return 31 * result + (active ? 1 : 0);
        }

        @Override
        public String toString() {
            return "TopBarButtonLayout[id=" + id + ", x=" + x + ", width=" + width
                    + ", label=" + label + ", iconOnly=" + iconOnly + ", active=" + active + "]";
        }
    }

    private TopBarTypes() {}
}
