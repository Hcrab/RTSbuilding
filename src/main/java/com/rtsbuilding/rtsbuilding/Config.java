package com.rtsbuilding.rtsbuilding;

import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsDiagnosticLevel;
import com.rtsbuilding.rtsbuilding.server.service.mining.RangeMiningHarvestTier;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fluids.FluidType;

public class Config {
    private static final ForgeConfigSpec.Builder COMMON_BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.Builder CLIENT_BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.Builder SERVER_BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLE_SURVIVAL_PROGRESSION = COMMON_BUILDER
            .comment("Enable RTS Home anchors and home-radius limits.")
            .translation("rtsbuilding.configuration.enableSurvivalProgression")
            .define("enableSurvivalProgression", false);

    public static final ForgeConfigSpec.BooleanValue SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS = COMMON_BUILDER
            .comment("When RTS Home is enabled, share RTS home anchors and team plugins with the player's FTB Team, OpenPAC party, or vanilla scoreboard team.")
            .translation("rtsbuilding.configuration.shareSurvivalProgressionWithTeams")
            .define("shareSurvivalProgressionWithTeams", false);

    public static final ForgeConfigSpec.IntValue MAX_ACTION_RADIUS_BLOCKS = COMMON_BUILDER
            .comment("Maximum RTS action radius in blocks.")
            .translation("rtsbuilding.configuration.maxActionRadiusBlocks")
            .defineInRange("maxActionRadiusBlocks", 128, 48, 512);

    public static final ForgeConfigSpec.BooleanValue ENABLE_BLUEPRINTS = COMMON_BUILDER
            .comment("Enable the RTS blueprint library tab, local blueprint upload, and server-side blueprint placement.")
            .translation("rtsbuilding.configuration.enableBlueprints")
            .define("enableBlueprints", true);

    public static final ForgeConfigSpec.IntValue MAX_BLUEPRINT_BLOCKS = COMMON_BUILDER
            .comment("Maximum non-air blocks allowed in one RTS blueprint import, capture, or placement job.")
            .translation("rtsbuilding.configuration.maxBlueprintBlocks")
            .defineInRange("maxBlueprintBlocks", 20000, 1, 200000);

    // ---- Rendering options ----

    public static final ForgeConfigSpec.BooleanValue ENABLE_UI_ANIMATIONS = CLIENT_BUILDER
            .comment("Enable short visual-only hover and selection transitions in the RTS UI.")
            .translation("rtsbuilding.configuration.enableUiAnimations")
            .define("enableUiAnimations", true);

    public static final ForgeConfigSpec.BooleanValue USE_BLOCK_GHOST_PREVIEW = CLIENT_BUILDER
            .comment("Render translucent block ghost models for placement previews before the player confirms placement.")
            .translation("rtsbuilding.configuration.useBlockGhostPreview")
            .define("useBlockGhostPreview", false);

    public static final ForgeConfigSpec.BooleanValue USE_PLACE_BLOCK_GHOST_ANIMATION = CLIENT_BUILDER
            .comment("Render translucent grow-in block ghosts after server-confirmed block placement.")
            .translation("rtsbuilding.configuration.usePlaceBlockGhostAnimation")
            .define("usePlaceBlockGhostAnimation", true);

    public static final ForgeConfigSpec.BooleanValue USE_DESTROY_BLOCK_GHOST_ANIMATION = CLIENT_BUILDER
            .comment("Render translucent shrink-out block ghosts after server-confirmed block destruction.")
            .translation("rtsbuilding.configuration.useDestroyBlockGhostAnimation")
            .define("useDestroyBlockGhostAnimation", true);

    public static final ForgeConfigSpec.BooleanValue USE_WIREFRAME_PREVIEW = CLIENT_BUILDER
            .comment("Render wireframe outlines for placement previews before the player confirms placement.")
            .translation("rtsbuilding.configuration.useWireframePreview")
            .define("useWireframePreview", false);

    public static final ForgeConfigSpec.BooleanValue USE_PLACE_WIREFRAME_ANIMATION = CLIENT_BUILDER
            .comment("Render grow-in wireframe outlines after server-confirmed block placement.")
            .translation("rtsbuilding.configuration.usePlaceWireframeAnimation")
            .define("usePlaceWireframeAnimation", false);

    public static final ForgeConfigSpec.BooleanValue USE_DESTROY_WIREFRAME_ANIMATION = CLIENT_BUILDER
            .comment("Render shrink-out wireframe outlines after server-confirmed block destruction.")
            .translation("rtsbuilding.configuration.useDestroyWireframeAnimation")
            .define("useDestroyWireframeAnimation", false);

    public static final ForgeConfigSpec.BooleanValue USE_RANGE_DESTROY_SKELETON = CLIENT_BUILDER
            .comment("Render merged skeleton borders for non-chain range destroy previews. Chain mining always uses the skeleton style.")
            .translation("rtsbuilding.configuration.useRangeDestroySkeleton")
            .define("useRangeDestroySkeleton", true);

    public static final ForgeConfigSpec.BooleanValue SHOW_INVENTORY_RTS_BUTTON = CLIENT_BUILDER
            .comment("Show the RTS plugin button on the vanilla inventory screen.")
            .translation("rtsbuilding.configuration.showInventoryRtsButton")
            .define("showInventoryRtsButton", true);

    // ---- Control options ----

    public static final ForgeConfigSpec.BooleanValue REQUIRE_KEYBOARD_BATCH_CONFIRM = CLIENT_BUILDER
            .comment("Require a configurable keyboard key for the final multi-block placement/destroy confirmation instead of confirming with the mouse click used to select the range.")
            .translation("rtsbuilding.configuration.requireKeyboardBatchConfirm")
            .define("requireKeyboardBatchConfirm", true);

    public static final ForgeConfigSpec.BooleanValue DEVELOPER_MODE = CLIENT_BUILDER
            .comment("Show the developer scenario task entry and write local diagnostic logs.")
            .translation("rtsbuilding.configuration.developerMode")
            .define("developerMode", false);

    public static final ForgeConfigSpec.EnumValue<RtsDiagnosticLevel> CLIENT_DIAGNOSTIC_LEVEL = CLIENT_BUILDER
            .comment("RTS operation diagnostics. BASIC records bounded lifecycle events; VERBOSE keeps additional detail.")
            .defineEnum("diagnostics.level", RtsDiagnosticLevel.BASIC);

    // ---- Server runtime limits ----

    public static final ForgeConfigSpec.EnumValue<RtsDiagnosticLevel> SERVER_DIAGNOSTIC_LEVEL = SERVER_BUILDER
            .comment("RTS operation diagnostics. VERBOSE adds one-second task progress samples; gameplay is unchanged.")
            .defineEnum("diagnostics.level", RtsDiagnosticLevel.BASIC);

    public static final ForgeConfigSpec.IntValue ULTIMINE_MAX_BLOCKS = SERVER_BUILDER
            .comment("Maximum blocks collected by one RTS chain mining request.")
            .translation("rtsbuilding.configuration.ultimineMaxBlocks")
            .defineInRange(java.util.List.of("mining", "ultimineMaxBlocks"), 256, 1, 4096);

    public static final ForgeConfigSpec.IntValue AREA_MINE_MAX_SIZE = SERVER_BUILDER
            .comment("Maximum block count per dimension for RTS area mining selections.")
            .translation("rtsbuilding.configuration.areaMineMaxSize")
            .defineInRange(java.util.List.of("mining", "areaMineMaxSize"), 36, 1, 64);

    public static final ForgeConfigSpec.IntValue AREA_MINE_MAX_VOLUME = SERVER_BUILDER
            .comment("Maximum covered volume, width * height * depth, accepted by one RTS area mining selection.")
            .translation("rtsbuilding.configuration.areaMineMaxVolume")
            .defineInRange(java.util.List.of("mining", "areaMineMaxVolume"), 46656, 1, 262144);

    public static final ForgeConfigSpec.IntValue AREA_MINE_MAX_WIDTH = SERVER_BUILDER
            .comment("Maximum X-axis width accepted by one RTS area mining selection.")
            .translation("rtsbuilding.configuration.areaMineMaxWidth")
            .defineInRange(java.util.List.of("mining", "areaMineMaxWidth"), 36, 1, 256);

    public static final ForgeConfigSpec.IntValue AREA_MINE_MAX_HEIGHT = SERVER_BUILDER
            .comment("Maximum Y-axis height accepted by one RTS area mining selection.")
            .translation("rtsbuilding.configuration.areaMineMaxHeight")
            .defineInRange(java.util.List.of("mining", "areaMineMaxHeight"), 36, 1, 256);

    public static final ForgeConfigSpec.IntValue AREA_MINE_MAX_DEPTH = SERVER_BUILDER
            .comment("Maximum Z-axis depth accepted by one RTS area mining selection.")
            .translation("rtsbuilding.configuration.areaMineMaxDepth")
            .defineInRange(java.util.List.of("mining", "areaMineMaxDepth"), 36, 1, 256);

    public static final ForgeConfigSpec.EnumValue<RangeMiningHarvestTier> AREA_MINE_MAX_HARVEST_TIER = SERVER_BUILDER
            .comment("Server ceiling for harvest-tier plugins used by non-chain RTS range mining.")
            .translation("rtsbuilding.configuration.areaMineMaxHarvestTier")
            .defineEnum(java.util.List.of("mining", "areaMineMaxHarvestTier"), RangeMiningHarvestTier.UNLIMITED);

    public static final ForgeConfigSpec.IntValue AE2_NETWORK_REFRESH_THROTTLE = SERVER_BUILDER
            .comment("Number of storage cache refresh cycles between expensive AE2 network snapshots.")
            .translation("rtsbuilding.configuration.ae2NetworkRefreshThrottle")
            .defineInRange(java.util.List.of("storage", "ae2NetworkRefreshThrottle"), 10, 1, 200);

    public static final ForgeConfigSpec.IntValue REFINED_STORAGE_NETWORK_REFRESH_THROTTLE = SERVER_BUILDER
            .comment("Number of storage cache refresh cycles between expensive Refined Storage network snapshots.")
            .translation("rtsbuilding.configuration.refinedStorageNetworkRefreshThrottle")
            .defineInRange(java.util.List.of("storage", "refinedStorageNetworkRefreshThrottle"), 10, 1, 200);

    public static final ForgeConfigSpec.IntValue PAGE_CACHE_MAX_PLAYERS = SERVER_BUILDER
            .comment("Maximum player count retained by the storage page LRU cache.")
            .translation("rtsbuilding.configuration.pageCacheMaxPlayers")
            .defineInRange(java.util.List.of("storage", "pageCacheMaxPlayers"), 256, 1, 4096);

    public static final ForgeConfigSpec.IntValue DEFAULT_STORAGE_PAGE_SIZE = SERVER_BUILDER
            .comment("Default number of item/fluid entries shown per RTS storage page.")
            .translation("rtsbuilding.configuration.defaultStoragePageSize")
            .defineInRange(java.util.List.of("storage", "defaultStoragePageSize"), 90, 1, 4096);

    public static final ForgeConfigSpec.IntValue MAX_STORAGE_PAGE_SIZE = SERVER_BUILDER
            .comment("Maximum allowed item/fluid entries per RTS storage page request.")
            .translation("rtsbuilding.configuration.maxStoragePageSize")
            .defineInRange(java.util.List.of("storage", "maxStoragePageSize"), 180, 1, 8192);

    public static final ForgeConfigSpec.IntValue AREA_DESTROY_MAX_TARGETS = SERVER_BUILDER
            .comment("Maximum explicit positions accepted by one RTS area destroy request.")
            .translation("rtsbuilding.configuration.areaDestroyMaxTargets")
            .defineInRange(java.util.List.of("mining", "areaDestroyMaxTargets"), 98304, 1, 262144);

    public static final ForgeConfigSpec.IntValue ULTIMINE_BLOCKS_PER_TICK = SERVER_BUILDER
            .comment("Maximum queued mining targets processed by one mining task slice.")
            .translation("rtsbuilding.configuration.ultimineBlocksPerTick")
            .defineInRange(java.util.List.of("mining", "ultimineBlocksPerTick"), 32, 1, 128);

    public static final ForgeConfigSpec.IntValue BUILD_BATCH_BLOCKS_PER_TICK = SERVER_BUILDER
            .comment("Maximum queued remote placement targets processed per player per server tick.")
            .translation("rtsbuilding.configuration.buildBatchBlocksPerTick")
            .defineInRange(java.util.List.of("placement", "buildBatchBlocksPerTick"), 64, 1, 512);

    public static final ForgeConfigSpec.IntValue BUILD_BATCH_MAX_QUEUED_JOBS = SERVER_BUILDER
            .comment("Maximum queued quick-build placement jobs per player.")
            .translation("rtsbuilding.configuration.buildBatchMaxQueuedJobs")
            .defineInRange(java.util.List.of("placement", "buildBatchMaxQueuedJobs"), 4, 1, 32);

    public static final ForgeConfigSpec.IntValue TASK_ENGINE_MAX_UNITS_PER_TICK = SERVER_BUILDER
            .comment("Hard global RTS work-unit limit across all players in one server tick.")
            .translation("rtsbuilding.configuration.taskEngineMaxUnitsPerTick")
            .defineInRange(java.util.List.of("taskEngine", "maxUnitsPerTick"), 256, 1, 4096);

    public static final ForgeConfigSpec.IntValue TASK_ENGINE_MAX_UNITS_PER_SLICE = SERVER_BUILDER
            .comment("Maximum RTS work units granted to one player before rotating to another player.")
            .translation("rtsbuilding.configuration.taskEngineMaxUnitsPerSlice")
            .defineInRange(java.util.List.of("taskEngine", "maxUnitsPerSlice"), 32, 1, 512);

    public static final ForgeConfigSpec.LongValue TASK_ENGINE_MAX_NANOS_PER_TICK = SERVER_BUILDER
            .comment("Cooperative RTS main-thread time budget per server tick in nanoseconds.")
            .translation("rtsbuilding.configuration.taskEngineMaxNanosPerTick")
            .defineInRange(java.util.List.of("taskEngine", "maxNanosPerTick"), 8_000_000L, 250_000L, 20_000_000L);

    public static final ForgeConfigSpec.DoubleValue REMOTE_POV_BLOCK_REACH = SERVER_BUILDER
            .comment("Temporary interaction reach used while RTSBuilding replays a remote player action.")
            .translation("rtsbuilding.configuration.remotePovBlockReach")
            .defineInRange(java.util.List.of("interaction", "remotePovBlockReach"), 4.0D, 1.0D, 16.0D);

    public static final ForgeConfigSpec.DoubleValue DROP_SCAN_RADIUS = SERVER_BUILDER
            .comment("Radius used to absorb drops around remotely mined blocks.")
            .translation("rtsbuilding.configuration.dropScanRadius")
            .defineInRange(java.util.List.of("mining", "dropScanRadius"), 1.25D, 0.25D, 8.0D);

    public static final ForgeConfigSpec.IntValue REMOTE_PLACE_SOUNDS_PER_TICK = SERVER_BUILDER
            .comment("Maximum RTS remote block action sounds sent per player per tick. Excess sounds are dropped.")
            .translation("rtsbuilding.configuration.remotePlaceSoundsPerTick")
            .defineInRange(java.util.List.of("placement", "remoteBlockActionSoundsPerTick"), 16, 0, 16);

    public static final ForgeConfigSpec.IntValue INTERNAL_FLUID_CAPACITY_BUCKETS = SERVER_BUILDER
            .comment("Fallback internal fluid buffer capacity in buckets when progression data is unavailable.")
            .translation("rtsbuilding.configuration.internalFluidCapacityBuckets")
            .defineInRange(java.util.List.of("fluid", "internalFluidCapacityBuckets"), 100, 1, 4096);

    private static final ForgeConfigSpec.IntValue SERVER_CONFIG_REVISION = SERVER_BUILDER
            .comment("Internal RTSBuilding server configuration migration revision. Do not edit manually.")
            .defineInRange(java.util.List.of("internal", "configRevision"), 0, 0, ServerConfigMigration.CURRENT_REVISION);

    public static final ForgeConfigSpec SPEC = COMMON_BUILDER.build();
    public static final ForgeConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();
    public static final ForgeConfigSpec SERVER_SPEC = SERVER_BUILDER.build();

    public static void setSurvivalProgressionEnabled(boolean enabled) {
        ENABLE_SURVIVAL_PROGRESSION.set(enabled);
        SPEC.save();
    }

    public static int maxActionRadiusBlocks() {
        return MAX_ACTION_RADIUS_BLOCKS.get();
    }

    public static void setMaxActionRadiusBlocks(int radiusBlocks) {
        MAX_ACTION_RADIUS_BLOCKS.set(Math.max(48, Math.min(512, radiusBlocks)));
        SPEC.save();
    }

    public static boolean areBlueprintsEnabled() {
        return ENABLE_BLUEPRINTS.get();
    }

    public static int maxBlueprintBlocks() {
        return MAX_BLUEPRINT_BLOCKS.get();
    }

    public static void saveGeneralSettings(boolean survivalEnabled, boolean shareWithTeams, int radiusBlocks,
            boolean blueprintsEnabled, int maxBlueprintBlocks) {
        ENABLE_SURVIVAL_PROGRESSION.set(survivalEnabled);
        SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS.set(shareWithTeams);
        MAX_ACTION_RADIUS_BLOCKS.set(clampInt(radiusBlocks, 48, 512));
        ENABLE_BLUEPRINTS.set(blueprintsEnabled);
        MAX_BLUEPRINT_BLOCKS.set(clampInt(maxBlueprintBlocks, 1, 200000));
        SPEC.save();
    }

    public static void saveAreaMineLimitSettings(int maxWidth, int maxHeight, int maxDepth,
            int maxVolume, int maxTargets, RangeMiningHarvestTier maxHarvestTier) {
        int width = clampInt(maxWidth, 1, 256);
        int height = clampInt(maxHeight, 1, 256);
        int depth = clampInt(maxDepth, 1, 256);
        AREA_MINE_MAX_WIDTH.set(width);
        AREA_MINE_MAX_HEIGHT.set(height);
        AREA_MINE_MAX_DEPTH.set(depth);
        AREA_MINE_MAX_VOLUME.set(clampInt(maxVolume, 1, 262144));
        AREA_DESTROY_MAX_TARGETS.set(clampInt(maxTargets, 1, 262144));
        AREA_MINE_MAX_HARVEST_TIER.set(
                maxHarvestTier == null ? RangeMiningHarvestTier.UNLIMITED : maxHarvestTier);
        AREA_MINE_MAX_SIZE.set(clampInt(Math.max(width, Math.max(height, depth)), 1, 64));
        SERVER_SPEC.save();
    }

    public static boolean isPlacementBlockGhostPreviewEnabled() {
        return USE_BLOCK_GHOST_PREVIEW.get();
    }

    public static boolean isUiAnimationsEnabled() {
        return ENABLE_UI_ANIMATIONS.get();
    }

    public static void setUiAnimationsEnabled(boolean enabled) {
        ENABLE_UI_ANIMATIONS.set(enabled);
        CLIENT_SPEC.save();
    }

    public static void setPlacementBlockGhostPreviewEnabled(boolean enabled) {
        USE_BLOCK_GHOST_PREVIEW.set(enabled);
        CLIENT_SPEC.save();
    }

    public static boolean isPlaceBlockGhostAnimationEnabled() {
        return USE_PLACE_BLOCK_GHOST_ANIMATION.get();
    }

    public static void setPlaceBlockGhostAnimationEnabled(boolean enabled) {
        USE_PLACE_BLOCK_GHOST_ANIMATION.set(enabled);
        CLIENT_SPEC.save();
    }

    public static boolean isDestroyBlockGhostAnimationEnabled() {
        return USE_DESTROY_BLOCK_GHOST_ANIMATION.get();
    }

    public static void setDestroyBlockGhostAnimationEnabled(boolean enabled) {
        USE_DESTROY_BLOCK_GHOST_ANIMATION.set(enabled);
        CLIENT_SPEC.save();
    }

    public static boolean isPlacementWireframePreviewEnabled() {
        return USE_WIREFRAME_PREVIEW.get();
    }

    public static void setPlacementWireframePreviewEnabled(boolean enabled) {
        USE_WIREFRAME_PREVIEW.set(enabled);
        CLIENT_SPEC.save();
    }

    public static boolean isPlaceWireframeAnimationEnabled() {
        return USE_PLACE_WIREFRAME_ANIMATION.get();
    }

    public static void setPlaceWireframeAnimationEnabled(boolean enabled) {
        USE_PLACE_WIREFRAME_ANIMATION.set(enabled);
        CLIENT_SPEC.save();
    }

    public static boolean isDestroyWireframeAnimationEnabled() {
        return USE_DESTROY_WIREFRAME_ANIMATION.get();
    }

    public static void setDestroyWireframeAnimationEnabled(boolean enabled) {
        USE_DESTROY_WIREFRAME_ANIMATION.set(enabled);
        CLIENT_SPEC.save();
    }

    public static boolean isRangeDestroySkeletonEnabled() {
        return USE_RANGE_DESTROY_SKELETON.get();
    }

    public static void setRangeDestroySkeletonEnabled(boolean enabled) {
        USE_RANGE_DESTROY_SKELETON.set(enabled);
        CLIENT_SPEC.save();
    }

    public static boolean isInventoryRtsButtonEnabled() {
        return SHOW_INVENTORY_RTS_BUTTON.get();
    }

    public static void setInventoryRtsButtonEnabled(boolean enabled) {
        SHOW_INVENTORY_RTS_BUTTON.set(enabled);
        CLIENT_SPEC.save();
    }

    public static boolean isKeyboardBatchConfirmEnabled() {
        return REQUIRE_KEYBOARD_BATCH_CONFIRM.get();
    }

    public static void setKeyboardBatchConfirmEnabled(boolean enabled) {
        REQUIRE_KEYBOARD_BATCH_CONFIRM.set(enabled);
        CLIENT_SPEC.save();
    }

    public static int ultimineMaxBlocks() {
        return ULTIMINE_MAX_BLOCKS.get();
    }

    public static int areaMineMaxSize() {
        return AREA_MINE_MAX_SIZE.get();
    }

    public static int areaMineMaxVolume() {
        return AREA_MINE_MAX_VOLUME.get();
    }

    public static int areaMineMaxWidth() {
        return AREA_MINE_MAX_WIDTH.get();
    }

    public static int areaMineMaxHeight() {
        return AREA_MINE_MAX_HEIGHT.get();
    }

    public static int areaMineMaxDepth() {
        return AREA_MINE_MAX_DEPTH.get();
    }

    public static RangeMiningHarvestTier areaMineMaxHarvestTier() {
        return AREA_MINE_MAX_HARVEST_TIER.get();
    }

    public static int ae2NetworkRefreshThrottle() {
        return AE2_NETWORK_REFRESH_THROTTLE.get();
    }

    public static int refinedStorageNetworkRefreshThrottle() {
        return REFINED_STORAGE_NETWORK_REFRESH_THROTTLE.get();
    }

    public static int pageCacheMaxPlayers() {
        return PAGE_CACHE_MAX_PLAYERS.get();
    }

    public static int defaultStoragePageSize() {
        return Math.min(DEFAULT_STORAGE_PAGE_SIZE.get(), maxStoragePageSize());
    }

    public static int maxStoragePageSize() {
        return MAX_STORAGE_PAGE_SIZE.get();
    }

    public static int areaDestroyMaxTargets() {
        return AREA_DESTROY_MAX_TARGETS.get();
    }

    public static int ultimineBlocksPerTick() {
        return ULTIMINE_BLOCKS_PER_TICK.get();
    }

    public static int buildBatchBlocksPerTick() {
        return BUILD_BATCH_BLOCKS_PER_TICK.get();
    }

    public static boolean isDeveloperModeEnabled() {
        return DEVELOPER_MODE.get();
    }

    public static void setDeveloperModeEnabled(boolean enabled) {
        DEVELOPER_MODE.set(enabled);
        CLIENT_SPEC.save();
    }
    public static int buildBatchMaxQueuedJobs() {
        return BUILD_BATCH_MAX_QUEUED_JOBS.get();
    }

    public static int taskEngineMaxUnitsPerTick() {
        return TASK_ENGINE_MAX_UNITS_PER_TICK.get();
    }

    public static int taskEngineMaxUnitsPerSlice() {
        return TASK_ENGINE_MAX_UNITS_PER_SLICE.get();
    }

    public static long taskEngineMaxNanosPerTick() {
        return TASK_ENGINE_MAX_NANOS_PER_TICK.get();
    }

    public static double remotePovBlockReach() {
        return REMOTE_POV_BLOCK_REACH.get();
    }

    public static double dropScanRadius() {
        return DROP_SCAN_RADIUS.get();
    }

    public static int remotePlaceSoundsPerTick() {
        return REMOTE_PLACE_SOUNDS_PER_TICK.get();
    }

    public static long internalFluidCapacityMb() {
        return Math.max(1L, (long) INTERNAL_FLUID_CAPACITY_BUCKETS.get()) * FluidType.BUCKET_VOLUME;
    }

    /**
     * 把旧版本真正落盘的保守默认值迁移到当前默认值，同时保留玩家主动设置的其他数值。
     *
     * @return 本次是否写入了新的迁移版本
     */
    public static boolean migrateLegacyServerDefaults() {
        ServerConfigMigration.Values migrated = ServerConfigMigration.migrate(
                SERVER_CONFIG_REVISION.get(),
                ULTIMINE_BLOCKS_PER_TICK.get(),
                TASK_ENGINE_MAX_NANOS_PER_TICK.get());
        if (migrated.revision() == SERVER_CONFIG_REVISION.get()) {
            return false;
        }
        ULTIMINE_BLOCKS_PER_TICK.set(migrated.miningSlice());
        TASK_ENGINE_MAX_NANOS_PER_TICK.set(migrated.taskBudgetNanos());
        SERVER_CONFIG_REVISION.set(migrated.revision());
        SERVER_SPEC.save();
        return true;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

}

