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
        QUEST_DETECT,
        OPERATION_MODE
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
    public record TopBarButtonLayout(
            TopBarButtonId id,
            int x,
            int width,
            String label,
            boolean iconOnly,
            boolean active) {}

    private TopBarTypes() {}
}
