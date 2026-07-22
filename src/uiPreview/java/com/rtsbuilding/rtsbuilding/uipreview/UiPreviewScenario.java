package com.rtsbuilding.rtsbuilding.uipreview;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** 无头预览的确定性场景参数；测试数据不会进入生产 source set。 */
public final class UiPreviewScenario {
    public enum Variant {
        BASELINE,
        HIGH_SCALE,
        NARROW_TW,
        COMPACT_HK,
        SETTINGS_TOOLTIP,
        OVERLAPPING_WINDOWS,
        LONG_ENGLISH,
        EMPTY_LOADING_FAILED_DISABLED,
        STORAGE_2000,
        SCROLL_CAPTURE,
        PLUGIN_MANAGER_STATES,
        QUICK_BUILD_STATES,
        BLUEPRINT_STATES,
        BLUEPRINT_CAPTURE_WAITING,
        BLUEPRINT_CAPTURE_READY,
        BLUEPRINT_CAPTURE_SAVING,
        BLUEPRINT_MATERIALS,
        BLUEPRINT_MATERIALS_READY,
        BLUEPRINT_MATERIALS_EMPTY,
        BLUEPRINT_NAME_CAPTURE,
        BLUEPRINT_NAME_RENAME,
        BLUEPRINT_LIBRARY,
        BLUEPRINT_LIBRARY_EMPTY_SEARCH,
        BLUEPRINT_LIBRARY_PARSE_ERROR,
        JADE_MODES,
        CREATIVE_CATALOG,
        TOP_CONTEXT_ACTIONBAR,
        WHEEL_WORLD_HANDLE_RESERVE,
        DENSE_WORKFLOW_NARROW,
        SETTINGS_CONTROLS,
        SETTINGS_SOUND,
        SETTINGS_ANIMATION,
        SETTINGS_NO_JADE,
        SETTINGS_LONG_HINT,
        QUICK_BUILD_BUILD,
        QUICK_BUILD_DESTROY_CHAIN,
        QUICK_BUILD_LOCKED,
        QUICK_BUILD_PROGRESS,
        CULLING_EMPTY,
        CULLING_DRAFT,
        CULLING_SELECTED,
        STORAGE_LINKS_READY,
        STORAGE_LINKS_EMPTY,
        STORAGE_LINKS_LOADING,
        STORAGE_LINKS_2000,
        STORAGE_LINKS_FAILED,
        WORKFLOW_ACTIVE,
        WORKFLOW_PAUSED,
        WORKFLOW_SUSPENDED,
        WORKFLOW_MIXED,
        GUIDE_TOP,
        GUIDE_BOTTOM,
        GUIDE_SETTINGS,
        FUNNEL_EMPTY,
        FUNNEL_ROWS,
        CRAFT_QUANTITY_READY,
        CRAFT_QUANTITY_MISSING,
        CRAFT_QUANTITY_MAX
    }

    private final String id;
    private final int width;
    private final int height;
    private final double rtsScale;
    private final String language;
    private final boolean darkTheme;
    private final Variant variant;
    private final int storageCount;
    private final int blueprintCount;
    private final String stateLabel;
    private final boolean debugOverlay;

    public UiPreviewScenario(String id, int width, int height, double rtsScale,
                             String language, boolean darkTheme, Variant variant,
                             int storageCount, int blueprintCount, String stateLabel,
                             boolean debugOverlay) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.rtsScale = rtsScale;
        this.language = language;
        this.darkTheme = darkTheme;
        this.variant = variant;
        this.storageCount = storageCount;
        this.blueprintCount = blueprintCount;
        this.stateLabel = stateLabel;
        this.debugOverlay = debugOverlay;
    }

    public String id() {
        return id;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public double rtsScale() {
        return rtsScale;
    }

    public String language() {
        return language;
    }

    public boolean darkTheme() {
        return darkTheme;
    }

    public Variant variant() {
        return variant;
    }

    public int storageCount() {
        return storageCount;
    }

    public int blueprintCount() {
        return blueprintCount;
    }

    public String stateLabel() {
        return stateLabel;
    }

    public boolean debugOverlay() {
        return debugOverlay;
    }

    public static List<UiPreviewScenario> firstBatch() {
        return Collections.unmodifiableList(Arrays.asList(
                scene("01_zh_cn_1920x1080", 1920, 1080, 1.0D, "zh_cn", true,
                        Variant.BASELINE, 240, 24, "ready", false),
                scene("02_en_us_high_scale", 1920, 1080, 1.3D, "en_us", false,
                        Variant.HIGH_SCALE, 240, 24, "ready", false),
                scene("03_zh_tw_1280x720", 1280, 720, 1.0D, "zh_tw", true,
                        Variant.NARROW_TW, 80, 12, "ready", false),
                scene("04_zh_hk_1024x768", 1024, 768, 1.1D, "zh_hk", false,
                        Variant.COMPACT_HK, 80, 12, "ready", false),
                scene("05_settings_palette_tooltip", 1600, 900, 1.2D, "zh_cn", true,
                        Variant.SETTINGS_TOOLTIP, 120, 12, "disabled reason", true),
                scene("06_overlapping_windows", 1600, 900, 1.0D, "en_us", true,
                        Variant.OVERLAPPING_WINDOWS, 120, 20, "overlap / topmost", true),
                scene("07_long_english", 1280, 720, 1.25D, "en_us", false,
                        Variant.LONG_ENGLISH, 500, 36, "extra long translated state description", true),
                scene("08_empty_loading_failed_disabled", 1280, 720, 1.0D, "zh_cn", true,
                        Variant.EMPTY_LOADING_FAILED_DISABLED, 0, 0, "empty / loading / failed / disabled", false),
                scene("09_storage_2000", 1920, 1080, 1.0D, "zh_cn", true,
                        Variant.STORAGE_2000, 2000, 48, "visible-range scan only", false),
                scene("10_scroll_capture", 1280, 720, 1.1D, "en_us", true,
                        Variant.SCROLL_CAPTURE, 2000, 30, "scroll owned by window", true),
                scene("11_plugin_manager_states", 1600, 900, 1.1D, "zh_tw", false,
                        Variant.PLUGIN_MANAGER_STATES, 300, 22, "personal / team / dependency", false),
                scene("12_quick_build_states", 1600, 900, 1.1D, "zh_cn", true,
                        Variant.QUICK_BUILD_STATES, 300, 22, "build / destroy / locked / tier", false),
                scene("13_blueprint_states", 1600, 900, 1.1D, "zh_hk", true,
                        Variant.BLUEPRINT_STATES, 300, 96, "capture / pinned / missing materials", false),
                scene("14_jade_modes", 1600, 900, 1.2D, "en_us", false,
                        Variant.JADE_MODES, 120, 18, "anchored / follow / hidden", true),
                scene("15_creative_catalog", 1600, 900, 1.0D, "zh_cn", false,
                        Variant.CREATIVE_CATALOG, 0, 18, "first load / category / empty search", false),
                scene("16_top_context_actionbar", 1600, 900, 1.2D, "zh_tw", true,
                        Variant.TOP_CONTEXT_ACTIONBAR, 160, 18, "context + actionbar + window", true),
                scene("17_wheel_world_handle_reserve", 1600, 900, 1.0D, "en_us", true,
                        Variant.WHEEL_WORLD_HANDLE_RESERVE, 160, 18, "2D reservation only", true),
                scene("18_dense_workflow_narrow", 1024, 768, 1.35D, "zh_hk", true,
                        Variant.DENSE_WORKFLOW_NARROW, 2000, 80, "workflow + settings + plugins", true),
                scene("19_blueprint_capture_waiting", 1600, 900, 1.1D, "zh_cn", true,
                        Variant.BLUEPRINT_CAPTURE_WAITING, 300, 96, "capture waiting second point", false),
                scene("20_blueprint_capture_ready", 1600, 900, 1.1D, "en_us", true,
                        Variant.BLUEPRINT_CAPTURE_READY, 300, 96, "capture ready", false),
                scene("21_blueprint_capture_saving", 1600, 900, 1.1D, "zh_tw", true,
                        Variant.BLUEPRINT_CAPTURE_SAVING, 300, 96, "capture saving", false),
                scene("22_blueprint_materials_missing", 1920, 1080, 1.0D, "zh_cn", true,
                        Variant.BLUEPRINT_MATERIALS, 300, 96, "materials missing", false),
                scene("23_blueprint_name_capture", 1600, 900, 1.1D, "en_us", true,
                        Variant.BLUEPRINT_NAME_CAPTURE, 300, 96, "capture name", false),
                scene("24_blueprint_name_rename", 1600, 900, 1.1D, "zh_hk", true,
                        Variant.BLUEPRINT_NAME_RENAME, 300, 96, "rename selected text", false),
                scene("25_blueprint_library", 1920, 1080, 1.0D, "zh_cn", true,
                        Variant.BLUEPRINT_LIBRARY, 300, 12, "library selected", false),
                scene("26_blueprint_library_empty_search", 1600, 900, 1.1D, "en_us", true,
                        Variant.BLUEPRINT_LIBRARY_EMPTY_SEARCH, 300, 12, "no search results", false),
                scene("27_blueprint_library_parse_error", 1600, 900, 1.1D, "zh_tw", true,
                        Variant.BLUEPRINT_LIBRARY_PARSE_ERROR, 300, 12, "parse error selected", false),
                scene("28_blueprint_materials_ready", 1920, 1080, 1.0D, "en_us", true,
                        Variant.BLUEPRINT_MATERIALS_READY, 300, 96, "materials ready", false),
                scene("29_blueprint_materials_empty", 1600, 900, 1.1D, "zh_hk", true,
                        Variant.BLUEPRINT_MATERIALS_EMPTY, 300, 96, "no required materials", false),
                scene("30_settings_controls", 1920, 1080, 1.0D, "zh_cn", true,
                        Variant.SETTINGS_CONTROLS, 120, 12, "all sensitivity controls", false),
                scene("31_settings_sound", 1600, 900, 1.1D, "en_us", true,
                        Variant.SETTINGS_SOUND, 120, 12, "sound toggles and limit", false),
                scene("32_settings_animation", 1920, 1080, 1.0D, "zh_tw", true,
                        Variant.SETTINGS_ANIMATION, 120, 12, "animation catalog", false),
                scene("33_settings_no_jade", 1600, 900, 1.1D, "en_us", true,
                        Variant.SETTINGS_NO_JADE, 120, 12, "display without Jade", false),
                scene("34_settings_long_hint", 1920, 1080, 1.0D, "en_us", true,
                        Variant.SETTINGS_LONG_HINT, 120, 12, "expanded long hint", false),
                scene("35_quick_build_build", 1600, 900, 1.0D, "zh_cn", true,
                        Variant.QUICK_BUILD_BUILD, 300, 12, "build fill and missing", false),
                scene("36_quick_build_destroy_chain", 1600, 900, 1.0D, "en_us", true,
                        Variant.QUICK_BUILD_DESTROY_CHAIN, 300, 12, "chain limit", false),
                scene("37_quick_build_locked", 1600, 900, 1.0D, "zh_tw", true,
                        Variant.QUICK_BUILD_LOCKED, 300, 12, "destroy plugin locked", false),
                scene("38_quick_build_progress", 1920, 1080, 1.0D, "zh_hk", true,
                        Variant.QUICK_BUILD_PROGRESS, 300, 12, "destroy workflow progress", false),
                scene("39_culling_empty", 1600, 900, 1.0D, "zh_cn", true,
                        Variant.CULLING_EMPTY, 300, 12, "no culling boxes", false),
                scene("40_culling_draft", 1600, 900, 1.0D, "en_us", true,
                        Variant.CULLING_DRAFT, 300, 12, "height draft", false),
                scene("41_culling_selected", 1920, 1080, 1.0D, "zh_tw", true,
                        Variant.CULLING_SELECTED, 300, 12, "selected with axis handle", false),
                scene("42_storage_links_ready", 1600, 900, 1.0D, "zh_cn", true,
                        Variant.STORAGE_LINKS_READY, 4, 12, "linked storage rows", false),
                scene("43_storage_links_empty", 1600, 900, 1.0D, "en_us", true,
                        Variant.STORAGE_LINKS_EMPTY, 0, 12, "no linked storage", false),
                scene("44_storage_links_loading", 1600, 900, 1.0D, "zh_tw", true,
                        Variant.STORAGE_LINKS_LOADING, 0, 12, "loading snapshot", false),
                scene("45_storage_links_2000", 1920, 1080, 1.0D, "en_us", true,
                        Variant.STORAGE_LINKS_2000, 2000, 12, "bounded visible rows", false),
                scene("46_storage_links_failed", 1600, 900, 1.0D, "zh_hk", true,
                        Variant.STORAGE_LINKS_FAILED, 0, 12, "snapshot failed", false),
                scene("47_workflow_active", 1600, 900, 1.0D, "zh_cn", true,
                        Variant.WORKFLOW_ACTIVE, 300, 12, "active progress", false),
                scene("48_workflow_paused", 1600, 900, 1.0D, "en_us", true,
                        Variant.WORKFLOW_PAUSED, 300, 12, "paused and protected", false),
                scene("49_workflow_suspended", 1600, 900, 1.0D, "zh_tw", true,
                        Variant.WORKFLOW_SUSPENDED, 300, 12, "suspended blueprint", false),
                scene("50_workflow_mixed", 1920, 1080, 1.0D, "zh_hk", true,
                        Variant.WORKFLOW_MIXED, 2000, 12, "mixed workflow rows", false),
                scene("51_guide_top", 1600, 900, 1.0D, "zh_cn", true,
                        Variant.GUIDE_TOP, 300, 12, "top guide", false),
                scene("52_guide_bottom", 1600, 900, 1.0D, "en_us", true,
                        Variant.GUIDE_BOTTOM, 300, 12, "bottom guide", false),
                scene("53_guide_settings", 1600, 900, 1.0D, "zh_tw", true,
                        Variant.GUIDE_SETTINGS, 300, 12, "settings guide", false),
                scene("54_funnel_empty", 1600, 900, 1.0D, "zh_hk", true,
                        Variant.FUNNEL_EMPTY, 0, 12, "funnel empty", false),
                scene("55_funnel_rows", 1920, 1080, 1.0D, "en_us", true,
                        Variant.FUNNEL_ROWS, 2000, 12, "bounded funnel rows", false),
                scene("56_craft_quantity_ready", 1600, 900, 1.0D, "zh_cn", true,
                        Variant.CRAFT_QUANTITY_READY, 300, 12, "craftable recipe", false),
                scene("57_craft_quantity_missing", 1600, 900, 1.0D, "en_us", true,
                        Variant.CRAFT_QUANTITY_MISSING, 300, 12, "missing recipe", false),
                scene("58_craft_quantity_max", 1920, 1080, 1.0D, "zh_tw", true,
                        Variant.CRAFT_QUANTITY_MAX, 300, 12, "quantity 999", false)
        ));
    }

    private static UiPreviewScenario scene(String id, int width, int height, double scale,
                                           String language, boolean darkTheme, Variant variant,
                                           int storageCount, int blueprintCount, String stateLabel,
                                           boolean debugOverlay) {
        return new UiPreviewScenario(id, width, height, scale, language, darkTheme, variant,
                storageCount, blueprintCount, stateLabel, debugOverlay);
    }
}
