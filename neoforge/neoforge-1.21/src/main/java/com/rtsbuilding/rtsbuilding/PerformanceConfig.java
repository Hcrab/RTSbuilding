package com.rtsbuilding.rtsbuilding;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Performance configuration class - designed to control performance-related settings in RTS mode.
 * 
 * <p>Through this configuration, GPU-intensive rendering features can be disabled or adjusted to improve performance.</p>
 */
public class PerformanceConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Rendering performance settings
    public static final ModConfigSpec.BooleanValue RENDER_BOUNDARY_WALLS = BUILDER
            .comment("Render RTS zone boundary walls. Disabling can significantly improve performance, but will lose boundary visual cues.")
            .translation("rtsbuilding.performance.renderBoundaryWalls")
            .define("renderBoundaryWalls", true);

    public static final ModConfigSpec.BooleanValue RENDER_INTERACTION_HIGHLIGHTS = BUILDER
            .comment("Render interaction target highlights (corner brackets on hovered blocks/entities). Disabling reduces GPU load.")
            .translation("rtsbuilding.performance.renderInteractionHighlights")
            .define("renderInteractionHighlights", true);

    public static final ModConfigSpec.BooleanValue RENDER_STORAGE_LINKS = BUILDER
            .comment("Render visual feedback for storage links (colored wireframes on bound blocks). Disabling improves performance.")
            .translation("rtsbuilding.performance.renderStorageLinks")
            .define("renderStorageLinks", true);

    public static final ModConfigSpec.BooleanValue RENDER_BOX_SELECTION = BUILDER
            .comment("Render box selection preview (dashed outline for three-point selection). Disabling reduces rendering overhead.")
            .translation("rtsbuilding.performance.renderBoxSelection")
            .define("renderBoxSelection", true);

    public static final ModConfigSpec.BooleanValue RENDER_ENTITY_HIGHLIGHTS = BUILDER
            .comment("Render entity selection highlights. Disabling reduces rendering effects around entities.")
            .translation("rtsbuilding.performance.renderEntityHighlights")
            .define("renderEntityHighlights", true);

    public static final ModConfigSpec.IntValue BOUNDARY_SCAN_CACHE_TIMEOUT = BUILDER
            .comment("Boundary heightmap scan cache timeout (milliseconds). Higher values reduce computation but result in slower updates.")
            .translation("rtsbuilding.performance.boundaryScanCacheTimeout")
            .defineInRange("boundaryScanCacheTimeout", 1000, 100, 5000);

    public static final ModConfigSpec.BooleanValue ENABLE_RENDER_DISTANCE_CULLING = BUILDER
            .comment("Enable render distance culling. Distant objects will not be rendered to improve performance.")
            .translation("rtsbuilding.performance.enableRenderDistanceCulling")
            .define("enableRenderDistanceCulling", true);

    public static final ModConfigSpec.DoubleValue MAX_RENDER_DISTANCE = BUILDER
            .comment("Maximum render distance (blocks). Objects beyond this distance will not be rendered.")
            .translation("rtsbuilding.performance.maxRenderDistance")
            .defineInRange("maxRenderDistance", 200.0, 16.0, 512.0);

    public static final ModConfigSpec SPEC = BUILDER.build();

    // Getter methods
    public static boolean shouldRenderBoundaryWalls() {
        try {
            return RENDER_BOUNDARY_WALLS.getAsBoolean();
        } catch (IllegalStateException e) {
            // Return default value when config is not loaded
            return true;
        }
    }

    public static boolean shouldRenderInteractionHighlights() {
        try {
            return RENDER_INTERACTION_HIGHLIGHTS.getAsBoolean();
        } catch (IllegalStateException e) {
            // Return default value when config is not loaded
            return true;
        }
    }

    public static boolean shouldRenderStorageLinks() {
        try {
            return RENDER_STORAGE_LINKS.getAsBoolean();
        } catch (IllegalStateException e) {
            // Return default value when config is not loaded
            return true;
        }
    }

    public static boolean shouldRenderBoxSelection() {
        try {
            return RENDER_BOX_SELECTION.getAsBoolean();
        } catch (IllegalStateException e) {
            // Return default value when config is not loaded
            return true;
        }
    }

    public static boolean shouldRenderEntityHighlights() {
        try {
            return RENDER_ENTITY_HIGHLIGHTS.getAsBoolean();
        } catch (IllegalStateException e) {
            // Return default value when config is not loaded
            return true;
        }
    }

    public static int getBoundaryScanCacheTimeout() {
        try {
            return BOUNDARY_SCAN_CACHE_TIMEOUT.getAsInt();
        } catch (IllegalStateException e) {
            // Return default value when config is not loaded
            return 1000;
        }
    }

    public static boolean shouldEnableRenderDistanceCulling() {
        try {
            return ENABLE_RENDER_DISTANCE_CULLING.getAsBoolean();
        } catch (IllegalStateException e) {
            // Return default value when config is not loaded
            return true;
        }
    }

    public static double getMaxRenderDistance() {
        try {
            return MAX_RENDER_DISTANCE.getAsDouble();
        } catch (IllegalStateException e) {
            // Return default value when config is not loaded
            return 64.0;
        }
    }

    public static void savePerformanceSettings(boolean renderBoundaryWalls, 
            boolean renderInteractionHighlights, 
            boolean renderStorageLinks,
            boolean renderBoxSelection,
            boolean renderEntityHighlights,
            int boundaryScanCacheTimeout,
            boolean enableRenderDistanceCulling,
            double maxRenderDistance) {
        RENDER_BOUNDARY_WALLS.set(renderBoundaryWalls);
        RENDER_INTERACTION_HIGHLIGHTS.set(renderInteractionHighlights);
        RENDER_STORAGE_LINKS.set(renderStorageLinks);
        RENDER_BOX_SELECTION.set(renderBoxSelection);
        RENDER_ENTITY_HIGHLIGHTS.set(renderEntityHighlights);
        BOUNDARY_SCAN_CACHE_TIMEOUT.set(boundaryScanCacheTimeout);
        ENABLE_RENDER_DISTANCE_CULLING.set(enableRenderDistanceCulling);
        MAX_RENDER_DISTANCE.set(maxRenderDistance);
        SPEC.save();
    }
}