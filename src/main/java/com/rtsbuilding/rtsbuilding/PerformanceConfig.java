package com.rtsbuilding.rtsbuilding;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 性能配置类 - 专门用于控制RTS模式下的性能相关设置
 * 
 * <p>通过此配置可以禁用或调整GPU密集型渲染功能以提高性能</p>
 */
public class PerformanceConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // 渲染性能设置
    public static final ModConfigSpec.BooleanValue RENDER_BOUNDARY_WALLS = BUILDER
            .comment("渲染RTS区域边界墙。禁用可显著提高性能，但会失去边界视觉提示。")
            .translation("rtsbuilding.performance.renderBoundaryWalls")
            .define("renderBoundaryWalls", true);

    public static final ModConfigSpec.BooleanValue RENDER_INTERACTION_HIGHLIGHTS = BUILDER
            .comment("渲染交互目标高亮（悬停方块/实体的角支架）。禁用可减少GPU负载。")
            .translation("rtsbuilding.performance.renderInteractionHighlights")
            .define("renderInteractionHighlights", true);

    public static final ModConfigSpec.BooleanValue RENDER_STORAGE_LINKS = BUILDER
            .comment("渲染存储链接的视觉反馈（绑定方块的彩色线框）。禁用可改善性能。")
            .translation("rtsbuilding.performance.renderStorageLinks")
            .define("renderStorageLinks", true);

    public static final ModConfigSpec.BooleanValue RENDER_BOX_SELECTION = BUILDER
            .comment("渲染框选预览（三点框选的虚线框）。禁用可减少渲染负担。")
            .translation("rtsbuilding.performance.renderBoxSelection")
            .define("renderBoxSelection", true);

    public static final ModConfigSpec.BooleanValue RENDER_ENTITY_HIGHLIGHTS = BUILDER
            .comment("渲染实体选择高亮。禁用可减少实体周围的渲染效果。")
            .translation("rtsbuilding.performance.renderEntityHighlights")
            .define("renderEntityHighlights", true);

    public static final ModConfigSpec.IntValue BOUNDARY_SCAN_CACHE_TIMEOUT = BUILDER
            .comment("边界高度图扫描缓存超时时间（毫秒）。较高的值可减少计算但更新较慢。")
            .translation("rtsbuilding.performance.boundaryScanCacheTimeout")
            .defineInRange("boundaryScanCacheTimeout", 1000, 100, 5000);

    public static final ModConfigSpec.BooleanValue ENABLE_RENDER_DISTANCE_CULLING = BUILDER
            .comment("启用渲染距离剔除。远处的对象将不被渲染以提高性能。")
            .translation("rtsbuilding.performance.enableRenderDistanceCulling")
            .define("enableRenderDistanceCulling", true);

    public static final ModConfigSpec.DoubleValue MAX_RENDER_DISTANCE = BUILDER
            .comment("最大渲染距离（方块）。超过此距离的对象将不会被渲染。")
            .translation("rtsbuilding.performance.maxRenderDistance")
            .defineInRange("maxRenderDistance", 64.0, 16.0, 256.0);

    public static final ModConfigSpec SPEC = BUILDER.build();

    // Getter方法
    public static boolean shouldRenderBoundaryWalls() {
        try {
            return RENDER_BOUNDARY_WALLS.getAsBoolean();
        } catch (IllegalStateException e) {
            // 配置未加载时返回默认值
            return true;
        }
    }

    public static boolean shouldRenderInteractionHighlights() {
        try {
            return RENDER_INTERACTION_HIGHLIGHTS.getAsBoolean();
        } catch (IllegalStateException e) {
            // 配置未加载时返回默认值
            return true;
        }
    }

    public static boolean shouldRenderStorageLinks() {
        try {
            return RENDER_STORAGE_LINKS.getAsBoolean();
        } catch (IllegalStateException e) {
            // 配置未加载时返回默认值
            return true;
        }
    }

    public static boolean shouldRenderBoxSelection() {
        try {
            return RENDER_BOX_SELECTION.getAsBoolean();
        } catch (IllegalStateException e) {
            // 配置未加载时返回默认值
            return true;
        }
    }

    public static boolean shouldRenderEntityHighlights() {
        try {
            return RENDER_ENTITY_HIGHLIGHTS.getAsBoolean();
        } catch (IllegalStateException e) {
            // 配置未加载时返回默认值
            return true;
        }
    }

    public static int getBoundaryScanCacheTimeout() {
        try {
            return BOUNDARY_SCAN_CACHE_TIMEOUT.getAsInt();
        } catch (IllegalStateException e) {
            // 配置未加载时返回默认值
            return 1000;
        }
    }

    public static boolean shouldEnableRenderDistanceCulling() {
        try {
            return ENABLE_RENDER_DISTANCE_CULLING.getAsBoolean();
        } catch (IllegalStateException e) {
            // 配置未加载时返回默认值
            return true;
        }
    }

    public static double getMaxRenderDistance() {
        try {
            return MAX_RENDER_DISTANCE.getAsDouble();
        } catch (IllegalStateException e) {
            // 配置未加载时返回默认值
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