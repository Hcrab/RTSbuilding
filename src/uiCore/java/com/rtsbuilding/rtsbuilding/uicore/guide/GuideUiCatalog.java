package com.rtsbuilding.rtsbuilding.uicore.guide;

/** 生产与离屏共同消费的正式指南目录。 */
public final class GuideUiCatalog {
    private GuideUiCatalog() {
    }

    public static String titleKey(GuideUiContext context) {
        switch (context) {
            case BOTTOM: return "screen.rtsbuilding.guide.bottom.title";
            case SETTINGS: return "screen.rtsbuilding.guide.settings.title";
            default: return "screen.rtsbuilding.guide.top.title";
        }
    }

    public static GuideUiTopic[] topics(GuideUiContext context) {
        switch (context) {
            case BOTTOM:
                return new GuideUiTopic[]{
                        topic(GuideUiIcon.SORT, "screen.rtsbuilding.guide.bottom.sort.title", "screen.rtsbuilding.guide.bottom.sort.1", "screen.rtsbuilding.guide.bottom.sort.2", "screen.rtsbuilding.guide.bottom.sort.3", "screen.rtsbuilding.guide.bottom.sort.4"),
                        topic(GuideUiIcon.CRAFT, "screen.rtsbuilding.guide.bottom.remote.title", "screen.rtsbuilding.guide.bottom.remote.1", "screen.rtsbuilding.guide.bottom.remote.2", "screen.rtsbuilding.guide.bottom.remote.3"),
                        topic(GuideUiIcon.GRID, "screen.rtsbuilding.guide.bottom.main.title", "screen.rtsbuilding.guide.bottom.main.1", "screen.rtsbuilding.guide.bottom.main.2", "screen.rtsbuilding.guide.bottom.main.3", "screen.rtsbuilding.guide.bottom.main.4"),
                        topic(GuideUiIcon.PIN, "screen.rtsbuilding.guide.bottom.recent_pins.title", "screen.rtsbuilding.guide.bottom.recent_pins.1", "screen.rtsbuilding.guide.bottom.recent_pins.2", "screen.rtsbuilding.guide.bottom.recent_pins.3"),
                        topic(GuideUiIcon.SEARCH, "screen.rtsbuilding.guide.bottom.craft_panel.title", "screen.rtsbuilding.guide.bottom.craft_panel.1", "screen.rtsbuilding.guide.bottom.craft_panel.2")
                };
            case SETTINGS:
                return new GuideUiTopic[]{
                        topic(GuideUiIcon.SLIDER, "screen.rtsbuilding.guide.settings.sensitivity.title", "screen.rtsbuilding.guide.settings.sensitivity.1", "screen.rtsbuilding.guide.settings.sensitivity.2"),
                        topic(GuideUiIcon.GRID, "screen.rtsbuilding.guide.settings.ui_scale.title", "screen.rtsbuilding.guide.settings.ui_scale.1", "screen.rtsbuilding.guide.settings.ui_scale.2"),
                        topic(GuideUiIcon.TOGGLE, "screen.rtsbuilding.guide.settings.autostore.title", "screen.rtsbuilding.guide.settings.autostore.1", "screen.rtsbuilding.guide.settings.autostore.2"),
                        topic(GuideUiIcon.TOGGLE, "screen.rtsbuilding.guide.settings.placed_recovery.title", "screen.rtsbuilding.guide.settings.placed_recovery.1", "screen.rtsbuilding.guide.settings.placed_recovery.2"),
                        topic(GuideUiIcon.GEAR, "screen.rtsbuilding.guide.settings.config.title", "screen.rtsbuilding.guide.settings.config.1", "screen.rtsbuilding.guide.settings.config.2")
                };
            default:
                return new GuideUiTopic[]{
                        topic(GuideUiIcon.HAND, "screen.rtsbuilding.guide.top.interact.title", "screen.rtsbuilding.guide.top.interact.1", "screen.rtsbuilding.guide.top.interact.2", "screen.rtsbuilding.guide.top.interact.3", "screen.rtsbuilding.guide.top.interact.4"),
                        topic(GuideUiIcon.GRID, "screen.rtsbuilding.guide.top.camera.title", "screen.rtsbuilding.guide.top.camera.1", "screen.rtsbuilding.guide.top.camera.2", "screen.rtsbuilding.guide.top.camera.3", "screen.rtsbuilding.guide.top.camera.4"),
                        topic(GuideUiIcon.LINK, "screen.rtsbuilding.guide.top.link.title", "screen.rtsbuilding.guide.top.link.1", "screen.rtsbuilding.guide.top.link.2"),
                        topic(GuideUiIcon.FUNNEL, "screen.rtsbuilding.guide.top.funnel.title", "screen.rtsbuilding.guide.top.funnel.1", "screen.rtsbuilding.guide.top.funnel.2"),
                        topic(GuideUiIcon.ROTATE, "screen.rtsbuilding.guide.top.rotate.title", "screen.rtsbuilding.guide.top.rotate.1"),
                        topic(GuideUiIcon.BUILD, "screen.rtsbuilding.guide.top.build.title", "screen.rtsbuilding.guide.top.build.1", "screen.rtsbuilding.guide.top.build.2", "screen.rtsbuilding.guide.top.build.3"),
                        topic(GuideUiIcon.PICKAXE, "screen.rtsbuilding.guide.top.ultimine.title", "screen.rtsbuilding.guide.top.ultimine.1", "screen.rtsbuilding.guide.top.ultimine.2"),
                        topic(GuideUiIcon.GRID, "screen.rtsbuilding.guide.top.chunk.title", "screen.rtsbuilding.guide.top.chunk.1")
                };
        }
    }

    private static GuideUiTopic topic(GuideUiIcon icon, String titleKey, String... lineKeys) {
        return new GuideUiTopic(icon, titleKey, lineKeys);
    }
}
