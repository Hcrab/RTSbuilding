package com.rtsbuilding.rtsbuilding;

import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsDiagnosticLevel;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigChange;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigUpdateRequest;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigUpdateResult;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigValidator;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigView;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigRawSnapshot;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigPersistence;
import com.rtsbuilding.rtsbuilding.common.mining.MiningLimits;
import com.rtsbuilding.rtsbuilding.common.mining.SelectionVolumeLimit;
import com.rtsbuilding.rtsbuilding.common.smartfill.SmartFillLimits;
import com.rtsbuilding.rtsbuilding.server.service.mining.RangeMiningHarvestTier;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.IConfigSpec;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Config {
    private static final ForgeConfigSpec.Builder COMMON_BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.Builder CLIENT_BUILDER = new ForgeConfigSpec.Builder();
    private static final ForgeConfigSpec.Builder SERVER_BUILDER = new ForgeConfigSpec.Builder();
    /** 迁移 schema revision 与当前世界运行期冲突 revision 分离。 */
    private static int SERVER_RUNTIME_REVISION;
    private static RtsServerConfigView SERVER_RUNTIME_BASELINE;
    private static boolean SERVER_RUNTIME_INITIALIZED;
    /** 读取最新 revision 不消费广播义务；只有服务端 tick 轮询确认后才清除。 */
    private static boolean SERVER_RUNTIME_BROADCAST_PENDING;
    private static RtsServerConfigRawSnapshot RAW_SERVER_SNAPSHOT;
    private static RtsServerConfigRawSnapshot RAW_COMMON_SNAPSHOT;
    /** 原始 SERVER 快照只标记待处理；迁移消费固定发生在服务端线程。 */
    private static boolean LEGACY_MIGRATION_PENDING;
    private static boolean LEGACY_MIGRATION_RUNNING;

    public static final ForgeConfigSpec.BooleanValue ENABLE_SURVIVAL_PROGRESSION = SERVER_BUILDER
            .comment("Enable RTS Home anchors and home-radius limits.")
            .translation("rtsbuilding.configuration.enableSurvivalProgression")
            .define("enableSurvivalProgression", false);

    public static final ForgeConfigSpec.BooleanValue SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS = SERVER_BUILDER
            .comment("When RTS Home is enabled, share RTS home anchors and team plugins with the player's FTB Team, OpenPAC party, or vanilla scoreboard team.")
            .translation("rtsbuilding.configuration.shareSurvivalProgressionWithTeams")
            .define("shareSurvivalProgressionWithTeams", false);

    public static final ForgeConfigSpec.IntValue MAX_ACTION_RADIUS_BLOCKS = SERVER_BUILDER
            .comment("Maximum RTS action radius in blocks.")
            .translation("rtsbuilding.configuration.maxActionRadiusBlocks")
            .defineInRange("maxActionRadiusBlocks", 128, 48, 512);

    public static final ForgeConfigSpec.BooleanValue ENABLE_BLUEPRINTS = SERVER_BUILDER
            .comment("Enable the RTS blueprint library tab, local blueprint upload, and server-side blueprint placement.")
            .translation("rtsbuilding.configuration.enableBlueprints")
            .define("enableBlueprints", true);

    public static final ForgeConfigSpec.IntValue MAX_BLUEPRINT_BLOCKS = SERVER_BUILDER
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

    /**
     * 诊断等级只控制日志量，不参与任何玩家操作、任务调度或权限判断。
     * 这里先与 main 建立同名基础入口，完整配置归属和迁移仍由 G04 处理。
     */
    public static final ForgeConfigSpec.EnumValue<RtsDiagnosticLevel> CLIENT_DIAGNOSTIC_LEVEL = CLIENT_BUILDER
            .comment("RTS operation diagnostics. BASIC records bounded lifecycle events; VERBOSE keeps additional detail.")
            .defineEnum("diagnostics.level", RtsDiagnosticLevel.BASIC);

    public static final ForgeConfigSpec.EnumValue<RtsDiagnosticLevel> SERVER_DIAGNOSTIC_LEVEL = SERVER_BUILDER
            .comment("RTS operation diagnostics. VERBOSE adds one-second task progress samples; gameplay is unchanged.")
            .defineEnum("diagnostics.level", RtsDiagnosticLevel.BASIC);

    public static final ForgeConfigSpec.IntValue DIAGNOSTICS_MAX_TRACES = SERVER_BUILDER
            .defineInRange(java.util.List.of("diagnostics", "maxTraces"), 1_024, 1, Integer.MAX_VALUE);
    public static final ForgeConfigSpec.IntValue DIAGNOSTICS_MAX_WORKFLOW_LINKS = SERVER_BUILDER
            .defineInRange(java.util.List.of("diagnostics", "maxWorkflowLinks"), 2_048, 1, Integer.MAX_VALUE);
    public static final ForgeConfigSpec.IntValue DIAGNOSTICS_MAX_TASK_LINKS = SERVER_BUILDER
            .defineInRange(java.util.List.of("diagnostics", "maxTaskLinks"), 2_048, 1, Integer.MAX_VALUE);

    // ---- Server runtime limits ----

    public static final ForgeConfigSpec.IntValue ULTIMINE_MAX_BLOCKS = SERVER_BUILDER
            .comment("Maximum blocks collected by one RTS chain mining request.")
            .translation("rtsbuilding.configuration.ultimineMaxBlocks")
            .defineInRange(java.util.List.of("mining", "ultimineMaxBlocks"), MiningLimits.DEFAULT_CHAIN_LIMIT, 1, MiningLimits.MAX_CHAIN_LIMIT);

    public static final ForgeConfigSpec.IntValue AREA_MINE_MAX_SIZE = SERVER_BUILDER
            .comment("Legacy size key retained only for one-time migration.")
            .translation("rtsbuilding.configuration.areaMineMaxSize")
            .defineInRange(java.util.List.of("mining", "areaMineMaxSize"), 0, 0, 256);

    public static final ForgeConfigSpec.IntValue AREA_MINE_MAX_VOLUME = SERVER_BUILDER
            .comment("Legacy volume key retained only for one-time migration.")
            .translation("rtsbuilding.configuration.areaMineMaxVolume")
            .defineInRange(java.util.List.of("mining", "areaMineMaxVolume"), 0, 0, MiningLimits.MAX_VOLUME);

    public static final ForgeConfigSpec.IntValue AREA_MINE_MAX_WIDTH = SERVER_BUILDER
            .comment("Legacy X-axis key retained only for one-time migration.")
            .translation("rtsbuilding.configuration.areaMineMaxWidth")
            .defineInRange(java.util.List.of("mining", "areaMineMaxWidth"), 0, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue AREA_MINE_MAX_HEIGHT = SERVER_BUILDER
            .comment("Legacy Y-axis key retained only for one-time migration.")
            .translation("rtsbuilding.configuration.areaMineMaxHeight")
            .defineInRange(java.util.List.of("mining", "areaMineMaxHeight"), 0, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue AREA_MINE_MAX_DEPTH = SERVER_BUILDER
            .comment("Legacy Z-axis key retained only for one-time migration.")
            .translation("rtsbuilding.configuration.areaMineMaxDepth")
            .defineInRange(java.util.List.of("mining", "areaMineMaxDepth"), 0, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue MAX_SELECTION_VOLUME = SERVER_BUILDER
            .comment("Canonical volume limit for range selections.")
            .translation("rtsbuilding.configuration.maxSelectionVolume")
            .defineInRange(java.util.List.of("mining", "maxSelectionVolume"), MiningLimits.DEFAULT_VOLUME, 1, MiningLimits.MAX_VOLUME);
    public static final ForgeConfigSpec.IntValue MAX_SELECTION_SIZE_X = SERVER_BUILDER
            .comment("Canonical world X span limit for range selections.")
            .defineInRange(java.util.List.of("mining", "maxSelectionSizeX"), 64, 1, Integer.MAX_VALUE);
    public static final ForgeConfigSpec.IntValue MAX_SELECTION_SIZE_Y = SERVER_BUILDER
            .comment("Canonical world Y span limit for range selections.")
            .defineInRange(java.util.List.of("mining", "maxSelectionSizeY"), 64, 1, Integer.MAX_VALUE);
    public static final ForgeConfigSpec.IntValue MAX_SELECTION_SIZE_Z = SERVER_BUILDER
            .comment("Canonical world Z span limit for range selections.")
            .defineInRange(java.util.List.of("mining", "maxSelectionSizeZ"), 64, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.EnumValue<RangeMiningHarvestTier> AREA_MINE_MAX_HARVEST_TIER = SERVER_BUILDER
            .comment("Server ceiling for harvest-tier plugins used by non-chain RTS range mining.")
            .translation("rtsbuilding.configuration.areaMineMaxHarvestTier")
            .defineEnum(java.util.List.of("mining", "areaMineMaxHarvestTier"), RangeMiningHarvestTier.UNLIMITED);

    public static final ForgeConfigSpec.IntValue MAX_TREE_BLOCKS = SERVER_BUILDER
            .defineInRange(java.util.List.of("mining", "maxTreeBlocks"), MiningLimits.DEFAULT_TREE_BLOCKS, 1, MiningLimits.MAX_TREE_BLOCKS);
    public static final ForgeConfigSpec.IntValue HOME_SELECTION_RADIUS_BLOCKS = SERVER_BUILDER
            .defineInRange(java.util.List.of("progression", "homeSelectionRadiusBlocks"), 34, 1, Integer.MAX_VALUE);
    public static final ForgeConfigSpec.IntValue HOME_RELOCATION_COOLDOWN_DAYS = SERVER_BUILDER
            .defineInRange(java.util.List.of("progression", "homeRelocationCooldownDays"), 20, 0, Integer.MAX_VALUE);
    public static final ForgeConfigSpec.IntValue MAX_SHAPE_DIMENSION = SERVER_BUILDER
            .defineInRange(java.util.List.of("building", "maxShapeDimension"), 32, 1, Integer.MAX_VALUE);
    public static final ForgeConfigSpec.IntValue MAX_SHAPE_RADIUS = SERVER_BUILDER
            .defineInRange(java.util.List.of("building", "maxShapeRadius"), 32, 1, Integer.MAX_VALUE);
    public static final ForgeConfigSpec.IntValue SMART_FILL_MAX_BLOCKS = SERVER_BUILDER
            .defineInRange(java.util.List.of("smartFill", "maxBlocks"), SmartFillLimits.MAX_BLOCKS, 1, SmartFillLimits.HARD_MAX_BLOCKS);
    public static final ForgeConfigSpec.IntValue SMART_FILL_DEFAULT_BLOCKS = SERVER_BUILDER
            .defineInRange(java.util.List.of("smartFill", "defaultBlocks"), SmartFillLimits.DEFAULT_BLOCKS, 1, SmartFillLimits.HARD_MAX_BLOCKS);
    public static final ForgeConfigSpec.IntValue SMART_FILL_MAX_DIAMETER = SERVER_BUILDER
            .defineInRange(java.util.List.of("smartFill", "maxDiameter"), SmartFillLimits.MAX_DIAMETER, SmartFillLimits.MIN_DIAMETER, SmartFillLimits.HARD_MAX_DIAMETER);
    public static final ForgeConfigSpec.IntValue SMART_FILL_DEFAULT_DIAMETER = SERVER_BUILDER
            .defineInRange(java.util.List.of("smartFill", "defaultDiameter"), SmartFillLimits.DEFAULT_DIAMETER, SmartFillLimits.MIN_DIAMETER, SmartFillLimits.HARD_MAX_DIAMETER);
    public static final ForgeConfigSpec.IntValue MAX_BATCH_BINDING_SELECTION_VOLUME = SERVER_BUILDER
            .defineInRange(java.util.List.of("storage", "maxBatchBindingSelectionVolume"), MiningLimits.MAX_VOLUME, 1, MiningLimits.MAX_VOLUME);
    public static final ForgeConfigSpec.IntValue MAX_BATCH_BINDING_SIZE_X = SERVER_BUILDER
            .defineInRange(java.util.List.of("storage", "maxBatchBindingSizeX"), 64, 1, Integer.MAX_VALUE);
    public static final ForgeConfigSpec.IntValue MAX_BATCH_BINDING_SIZE_Y = SERVER_BUILDER
            .defineInRange(java.util.List.of("storage", "maxBatchBindingSizeY"), 64, 1, Integer.MAX_VALUE);
    public static final ForgeConfigSpec.IntValue MAX_BATCH_BINDING_SIZE_Z = SERVER_BUILDER
            .defineInRange(java.util.List.of("storage", "maxBatchBindingSizeZ"), 64, 1, Integer.MAX_VALUE);
    public static final ForgeConfigSpec.DoubleValue FUNNEL_PICKUP_RADIUS_BLOCKS = SERVER_BUILDER
            .defineInRange(java.util.List.of("funnel", "pickupRadiusBlocks"), 2.0D, 0.0D, 32.0D);
    public static final ForgeConfigSpec.IntValue WORKFLOWS_MAX_ACTIVE_PER_PLAYER = SERVER_BUILDER
            .defineInRange(java.util.List.of("workflows", "maxActivePerPlayer"), 8, 1, 1024);
    public static final ForgeConfigSpec.IntValue HISTORY_MAX_ENTRIES_PER_STACK = SERVER_BUILDER
            .defineInRange(java.util.List.of("history", "maxEntriesPerStack"), 3, 1, Integer.MAX_VALUE);
    public static final ForgeConfigSpec.IntValue HISTORY_RETENTION_SECONDS = SERVER_BUILDER
            .defineInRange(java.util.List.of("history", "retentionSeconds"), 600, 1, Integer.MAX_VALUE);
    public static final ForgeConfigSpec.IntValue FUNNEL_MAX_ENTITIES_PER_TICK = SERVER_BUILDER
            .defineInRange(java.util.List.of("funnel", "maxEntitiesPerTick"), 24, 1, Integer.MAX_VALUE);
    public static final ForgeConfigSpec.IntValue FUNNEL_MAX_ITEMS_PER_TICK = SERVER_BUILDER
            .defineInRange(java.util.List.of("funnel", "maxItemsPerTick"), 48, 1, Integer.MAX_VALUE);
    public static final ForgeConfigSpec.IntValue FUNNEL_BUFFER_MAX_STACKS = SERVER_BUILDER
            .defineInRange(java.util.List.of("funnel", "bufferMaxStacks"), 16, 1, Integer.MAX_VALUE);
    public static final ForgeConfigSpec.IntValue FUNNEL_TICK_INTERVAL = SERVER_BUILDER
            .defineInRange(java.util.List.of("funnel", "tickInterval"), 2, 1, Integer.MAX_VALUE);
    public static final ForgeConfigSpec.IntValue STORAGE_DROP_CACHE_SOFT_CAPACITY = SERVER_BUILDER
            .defineInRange(java.util.List.of("storage", "dropCacheSoftCapacity"), 4_096, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue AE2_NETWORK_REFRESH_THROTTLE = SERVER_BUILDER
            .comment("Number of storage cache refresh cycles between expensive AE2 network snapshots.")
            .translation("rtsbuilding.configuration.ae2NetworkRefreshThrottle")
            .defineInRange(java.util.List.of("storage", "ae2NetworkRefreshThrottle"), 10, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue REFINED_STORAGE_NETWORK_REFRESH_THROTTLE = SERVER_BUILDER
            .comment("Number of storage cache refresh cycles between expensive Refined Storage network snapshots.")
            .translation("rtsbuilding.configuration.refinedStorageNetworkRefreshThrottle")
            .defineInRange(java.util.List.of("storage", "refinedStorageNetworkRefreshThrottle"), 10, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue MAX_LINKED_STORAGES = SERVER_BUILDER
            .comment("Maximum linked storage endpoints retained for one player.")
            .translation("rtsbuilding.configuration.maxLinkedStorages")
            .defineInRange(java.util.List.of("storage", "maxLinkedStorages"), 200, 1, 4096);

    public static final ForgeConfigSpec.BooleanValue ENABLE_CROSS_DIMENSION_STORAGE = SERVER_BUILDER
            .comment("Allow the cross-dimension storage plugin to wake and access linked storage in other dimensions.")
            .translation("rtsbuilding.configuration.enableCrossDimensionStorage")
            .define(java.util.List.of("storage", "enableCrossDimensionStorage"), true);

    public static final ForgeConfigSpec.IntValue MAX_CROSS_DIMENSION_AWAKE_CHUNKS = SERVER_BUILDER
            .comment("Maximum short-lived cross-dimension storage chunk tickets retained for one player.")
            .translation("rtsbuilding.configuration.maxCrossDimensionAwakeChunks")
            .defineInRange(java.util.List.of("storage", "maxCrossDimensionAwakeChunks"), 32, 1, 256);

    public static final ForgeConfigSpec.IntValue PAGE_CACHE_MAX_PLAYERS = SERVER_BUILDER
            .comment("Maximum player count retained by the storage page LRU cache.")
            .translation("rtsbuilding.configuration.pageCacheMaxPlayers")
            .defineInRange(java.util.List.of("storage", "pageCacheMaxPlayers"), 256, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue DEFAULT_STORAGE_PAGE_SIZE = SERVER_BUILDER
            .comment("Default number of item/fluid entries shown per RTS storage page.")
            .translation("rtsbuilding.configuration.defaultStoragePageSize")
            .defineInRange(java.util.List.of("storage", "defaultStoragePageSize"), 90, 1, 4096);

    public static final ForgeConfigSpec.IntValue MAX_STORAGE_PAGE_SIZE = SERVER_BUILDER
            .comment("Maximum allowed item/fluid entries per RTS storage page request.")
            .translation("rtsbuilding.configuration.maxStoragePageSize")
            .defineInRange(java.util.List.of("storage", "maxStoragePageSize"), 180, 1, 8192);

    public static final ForgeConfigSpec.IntValue AREA_DESTROY_MAX_TARGETS = SERVER_BUILDER
            .comment("Legacy explicit target count retained only for one-time migration.")
            .translation("rtsbuilding.configuration.areaDestroyMaxTargets")
            .defineInRange(java.util.List.of("mining", "areaDestroyMaxTargets"), 0, 0, MiningLimits.MAX_VOLUME);

    public static final ForgeConfigSpec.IntValue ULTIMINE_BLOCKS_PER_TICK = SERVER_BUILDER
            .comment("Maximum queued mining targets processed by one mining task slice.")
            .translation("rtsbuilding.configuration.ultimineBlocksPerTick")
            .defineInRange(java.util.List.of("mining", "ultimineBlocksPerTick"), 32, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue BUILD_BATCH_BLOCKS_PER_TICK = SERVER_BUILDER
            .comment("Maximum queued remote placement targets processed per player per server tick.")
            .translation("rtsbuilding.configuration.buildBatchBlocksPerTick")
            .defineInRange(java.util.List.of("placement", "buildBatchBlocksPerTick"), 64, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue BUILD_BATCH_MAX_QUEUED_JOBS = SERVER_BUILDER
            .comment("Maximum queued quick-build placement jobs per player.")
            .translation("rtsbuilding.configuration.buildBatchMaxQueuedJobs")
            .defineInRange(java.util.List.of("placement", "buildBatchMaxQueuedJobs"), 4, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue TASK_ENGINE_MAX_UNITS_PER_TICK = SERVER_BUILDER
            .comment("Hard global RTS work-unit limit across all players in one server tick.")
            .translation("rtsbuilding.configuration.taskEngineMaxUnitsPerTick")
            .defineInRange(java.util.List.of("taskEngine", "maxUnitsPerTick"), 256, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.IntValue TASK_ENGINE_MAX_UNITS_PER_SLICE = SERVER_BUILDER
            .comment("Maximum RTS work units granted to one player before rotating to another player.")
            .translation("rtsbuilding.configuration.taskEngineMaxUnitsPerSlice")
            .defineInRange(java.util.List.of("taskEngine", "maxUnitsPerSlice"), 32, 1, Integer.MAX_VALUE);

    public static final ForgeConfigSpec.LongValue TASK_ENGINE_MAX_NANOS_PER_TICK = SERVER_BUILDER
            .comment("Cooperative RTS main-thread time budget per server tick in nanoseconds.")
            .translation("rtsbuilding.configuration.taskEngineMaxNanosPerTick")
            .defineInRange(java.util.List.of("taskEngine", "maxNanosPerTick"), 8_000_000L, 1L, Long.MAX_VALUE);

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
            .defineInRange(java.util.List.of("internal", "configRevision"), 0, 0, Integer.MAX_VALUE);

    public static final ForgeConfigSpec SPEC = COMMON_BUILDER.build();
    public static final ForgeConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();
    public static final ForgeConfigSpec SERVER_SPEC = SERVER_BUILDER.build();
    /** 注册给 Forge 的薄适配器；SERVER_SPEC 仍是唯一 typed 值与 save 所有者。 */
    public static final RtsForgeServerConfigSpecAdapter SERVER_CONFIG_SPEC = new RtsForgeServerConfigSpecAdapter(SERVER_SPEC);

    /** 同时识别 loader 注册的薄适配器和仍由业务持有的真实 SERVER_SPEC。 */
    public static boolean isServerSpec(Object spec) {
        return spec == SERVER_SPEC || spec == SERVER_CONFIG_SPEC;
    }

    /**
     * 运行期发给远端客户端的最小 SERVER 投影。这里只列客户端实际读取的世界规则；
     * tick/slice 预算、队列、缓存、页请求和诊断采样仍只留在服务端文件，不借同步凑一份
     * 客户端镜像。typed 配置会话消费该投影；Forge 原生登录握手仍按 loader 契约读取完整
     * SERVER 文件，但客户端业务只使用下列字段，投影本身绝不写入客户端磁盘。
     */
    public static String clientServerConfigProjectionToml() {
        StringBuilder toml = new StringBuilder(768);
        toml.append("enableBlueprints = ").append(areBlueprintsEnabled()).append('\n');
        toml.append("maxBlueprintBlocks = ").append(maxBlueprintBlocks()).append('\n');
        toml.append('\n').append("[mining]\n");
        toml.append("maxSelectionVolume = ").append(areaMineMaxVolume()).append('\n');
        toml.append("maxSelectionSizeX = ").append(areaMineMaxWidth()).append('\n');
        toml.append("maxSelectionSizeY = ").append(areaMineMaxHeight()).append('\n');
        toml.append("maxSelectionSizeZ = ").append(areaMineMaxDepth()).append('\n');
        toml.append("ultimineMaxBlocks = ").append(ultimineMaxBlocks()).append('\n');
        toml.append("areaMineMaxHarvestTier = \"").append(areaMineMaxHarvestTier().name()).append("\"\n");
        toml.append("maxTreeBlocks = ").append(maxTreeBlocks()).append('\n');
        toml.append('\n').append("[building]\n");
        toml.append("maxShapeDimension = ").append(maxShapeDimension()).append('\n');
        toml.append("maxShapeRadius = ").append(maxShapeRadius()).append('\n');
        toml.append('\n').append("[smartFill]\n");
        toml.append("maxBlocks = ").append(smartFillMaxBlocks()).append('\n');
        toml.append("defaultBlocks = ").append(smartFillDefaultBlocks()).append('\n');
        toml.append("maxDiameter = ").append(smartFillMaxDiameter()).append('\n');
        toml.append("defaultDiameter = ").append(smartFillDefaultDiameter()).append('\n');
        toml.append('\n').append("[storage]\n");
        toml.append("maxBatchBindingSelectionVolume = ").append(maxBatchBindingSelectionVolume()).append('\n');
        toml.append("maxBatchBindingSizeX = ").append(maxBatchBindingSizeX()).append('\n');
        toml.append("maxBatchBindingSizeY = ").append(maxBatchBindingSizeY()).append('\n');
        toml.append("maxBatchBindingSizeZ = ").append(maxBatchBindingSizeZ()).append('\n');
        return toml.toString();
    }

    public static void setSurvivalProgressionEnabled(boolean enabled) {
        if (!canWriteServerConfig()) return;
        ENABLE_SURVIVAL_PROGRESSION.set(enabled);
        SERVER_SPEC.save();
    }

    public static int maxActionRadiusBlocks() {
        return MAX_ACTION_RADIUS_BLOCKS.get();
    }

    public static void setMaxActionRadiusBlocks(int radiusBlocks) {
        if (!canWriteServerConfig()) return;
        MAX_ACTION_RADIUS_BLOCKS.set(Math.max(48, Math.min(512, radiusBlocks)));
        SERVER_SPEC.save();
    }

    public static boolean areBlueprintsEnabled() {
        return ENABLE_BLUEPRINTS.get();
    }

    public static int maxBlueprintBlocks() {
        return MAX_BLUEPRINT_BLOCKS.get();
    }

    public static void saveGeneralSettings(boolean survivalEnabled, boolean shareWithTeams, int radiusBlocks,
            boolean blueprintsEnabled, int maxBlueprintBlocks) {
        if (!canWriteServerConfig()) return;
        ENABLE_SURVIVAL_PROGRESSION.set(survivalEnabled);
        SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS.set(shareWithTeams);
        MAX_ACTION_RADIUS_BLOCKS.set(clampInt(radiusBlocks, 48, 512));
        ENABLE_BLUEPRINTS.set(blueprintsEnabled);
        MAX_BLUEPRINT_BLOCKS.set(clampInt(maxBlueprintBlocks, 1, 200000));
        SERVER_SPEC.save();
    }

    public static void saveAreaMineLimitSettings(int maxWidth, int maxHeight, int maxDepth,
            int maxVolume, int maxTargets, RangeMiningHarvestTier maxHarvestTier) {
        saveAreaMineLimitSettings(maxVolume, maxWidth, maxHeight, maxDepth, maxHarvestTier);
    }

    public static void saveAreaMineLimitSettings(int maxVolume, RangeMiningHarvestTier maxHarvestTier) {
        saveAreaMineLimitSettings(maxVolume, areaMineMaxWidth(), areaMineMaxHeight(), areaMineMaxDepth(), maxHarvestTier);
    }

    public static void saveAreaMineLimitSettings(int maxVolume, int maxSizeX, int maxSizeY, int maxSizeZ,
            RangeMiningHarvestTier maxHarvestTier) {
        if (!canWriteServerConfig()) return;
        MAX_SELECTION_SIZE_X.set(Math.max(1, maxSizeX));
        MAX_SELECTION_SIZE_Y.set(Math.max(1, maxSizeY));
        MAX_SELECTION_SIZE_Z.set(Math.max(1, maxSizeZ));
        MAX_SELECTION_VOLUME.set(MiningLimits.clampVolume(maxVolume));
        AREA_MINE_MAX_HARVEST_TIER.set(
                maxHarvestTier == null ? RangeMiningHarvestTier.UNLIMITED : maxHarvestTier);
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
        return MiningLimits.clampChainLimit(ULTIMINE_MAX_BLOCKS.get());
    }

    public static int areaMineMaxSize() {
        return Math.max(areaMineMaxWidth(), Math.max(areaMineMaxHeight(), areaMineMaxDepth()));
    }

    public static int areaMineMaxVolume() {
        return MiningLimits.clampVolume(MAX_SELECTION_VOLUME.get());
    }

    public static int areaMineMaxWidth() {
        return MAX_SELECTION_SIZE_X.get();
    }

    public static int areaMineMaxHeight() {
        return MAX_SELECTION_SIZE_Y.get();
    }

    public static int areaMineMaxDepth() {
        return MAX_SELECTION_SIZE_Z.get();
    }

    public static SelectionVolumeLimit areaMineSelectionLimit() {
        return new SelectionVolumeLimit(areaMineMaxVolume(), areaMineMaxWidth(), areaMineMaxHeight(), areaMineMaxDepth());
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

    public static int maxLinkedStorages() {
        return MAX_LINKED_STORAGES.get();
    }

    public static boolean isCrossDimensionStorageEnabled() {
        return ENABLE_CROSS_DIMENSION_STORAGE.get();
    }

    public static int maxCrossDimensionAwakeChunks() {
        return MAX_CROSS_DIMENSION_AWAKE_CHUNKS.get();
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
        return areaMineMaxVolume();
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

    public static int maxTreeBlocks() { return Math.min(MiningLimits.MAX_TREE_BLOCKS, Math.max(1, MAX_TREE_BLOCKS.get())); }
    public static int homeSelectionRadiusBlocks() { return HOME_SELECTION_RADIUS_BLOCKS.get(); }
    public static int homeRelocationCooldownDays() { return HOME_RELOCATION_COOLDOWN_DAYS.get(); }
    public static int maxShapeDimension() { return MAX_SHAPE_DIMENSION.get(); }
    public static int maxShapeRadius() { return MAX_SHAPE_RADIUS.get(); }
    public static int smartFillMaxBlocks() { return Math.min(SmartFillLimits.HARD_MAX_BLOCKS, Math.max(SmartFillLimits.MIN_BLOCKS, SMART_FILL_MAX_BLOCKS.get())); }
    public static int smartFillDefaultBlocks() { return Math.min(SMART_FILL_DEFAULT_BLOCKS.get(), smartFillMaxBlocks()); }
    public static int smartFillMaxDiameter() { return Math.min(SmartFillLimits.HARD_MAX_DIAMETER, Math.max(SmartFillLimits.MIN_DIAMETER, SMART_FILL_MAX_DIAMETER.get())); }
    public static int smartFillDefaultDiameter() { return Math.max(SmartFillLimits.MIN_DIAMETER, Math.min(SMART_FILL_DEFAULT_DIAMETER.get(), smartFillMaxDiameter())); }
    public static int maxBatchBindingSelectionVolume() { return MiningLimits.clampVolume(MAX_BATCH_BINDING_SELECTION_VOLUME.get()); }
    public static int maxBatchBindingSizeX() { return MAX_BATCH_BINDING_SIZE_X.get(); }
    public static int maxBatchBindingSizeY() { return MAX_BATCH_BINDING_SIZE_Y.get(); }
    public static int maxBatchBindingSizeZ() { return MAX_BATCH_BINDING_SIZE_Z.get(); }
    public static SelectionVolumeLimit batchBindingSelectionLimit() { return new SelectionVolumeLimit(maxBatchBindingSelectionVolume(), maxBatchBindingSizeX(), maxBatchBindingSizeY(), maxBatchBindingSizeZ()); }
    public static double funnelPickupRadiusBlocks() { return FUNNEL_PICKUP_RADIUS_BLOCKS.get(); }
    public static int maxActiveWorkflowsPerPlayer() { return WORKFLOWS_MAX_ACTIVE_PER_PLAYER.get(); }
    public static int historyMaxEntriesPerStack() { return HISTORY_MAX_ENTRIES_PER_STACK.get(); }
    public static int historyRetentionSeconds() { return HISTORY_RETENTION_SECONDS.get(); }
    public static int funnelMaxEntitiesPerTick() { return FUNNEL_MAX_ENTITIES_PER_TICK.get(); }
    public static int funnelMaxItemsPerTick() { return FUNNEL_MAX_ITEMS_PER_TICK.get(); }
    public static int funnelBufferMaxStacks() { return FUNNEL_BUFFER_MAX_STACKS.get(); }
    public static int funnelTickInterval() { return FUNNEL_TICK_INTERVAL.get(); }
    public static int dropCacheSoftCapacity() { return STORAGE_DROP_CACHE_SOFT_CAPACITY.get(); }
    public static int diagnosticsMaxTraces() { return DIAGNOSTICS_MAX_TRACES.get(); }
    public static int diagnosticsMaxWorkflowLinks() { return DIAGNOSTICS_MAX_WORKFLOW_LINKS.get(); }
    public static int diagnosticsMaxTaskLinks() { return DIAGNOSTICS_MAX_TASK_LINKS.get(); }

    /** 返回当前世界的有效服务端规则；客户端文件不参与权限推断。 */
    public static synchronized RtsServerConfigView currentServerSettings(boolean requesterCanEdit) {
        boolean loaded = ServerLifecycleHooks.getCurrentServer() != null;
        ensureRuntimeBaseline();
        refreshRuntimeBaseline();
        return serverSettings(SERVER_RUNTIME_REVISION, loaded && canWriteServerConfig() && requesterCanEdit, loaded);
    }

    private static RtsServerConfigView serverSettings(int revision, boolean editable, boolean loaded) {
        return new RtsServerConfigView(revision, editable, loaded,
                ENABLE_SURVIVAL_PROGRESSION.get(), SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS.get(), maxActionRadiusBlocks(),
                areBlueprintsEnabled(), maxBlueprintBlocks(), areaMineMaxVolume(), areaMineMaxWidth(), areaMineMaxHeight(),
                areaMineMaxDepth(), ultimineMaxBlocks(), ultimineBlocksPerTick(), areaMineMaxHarvestTier().name(), maxTreeBlocks(), homeSelectionRadiusBlocks(), homeRelocationCooldownDays(),
                maxShapeDimension(), maxShapeRadius(), smartFillMaxBlocks(), smartFillDefaultBlocks(), smartFillMaxDiameter(),
                smartFillDefaultDiameter(), maxBatchBindingSelectionVolume(), maxBatchBindingSizeX(), maxBatchBindingSizeY(),
                maxBatchBindingSizeZ(), maxLinkedStorages(), funnelPickupRadiusBlocks(), maxActiveWorkflowsPerPlayer(),
                historyMaxEntriesPerStack(), historyRetentionSeconds(), funnelMaxEntitiesPerTick(), funnelMaxItemsPerTick(),
                funnelBufferMaxStacks(), funnelTickInterval(), dropCacheSoftCapacity(), buildBatchBlocksPerTick(),
                buildBatchMaxQueuedJobs(), taskEngineMaxUnitsPerTick(), taskEngineMaxUnitsPerSlice(), taskEngineMaxNanosPerTick(),
                defaultStoragePageSize(), maxStoragePageSize(), pageCacheMaxPlayers(), ae2NetworkRefreshThrottle(),
                refinedStorageNetworkRefreshThrottle(), diagnosticsMaxTraces(), diagnosticsMaxWorkflowLinks(), diagnosticsMaxTaskLinks());
    }

    /** 在服务端安全边界初始化本次世界的运行配置 revision。 */
    public static synchronized void beginServerConfigRuntime() {
        SERVER_RUNTIME_REVISION = 1;
        SERVER_RUNTIME_BASELINE = effectiveRuntimeSettings();
        SERVER_RUNTIME_INITIALIZED = true;
        SERVER_RUNTIME_BROADCAST_PENDING = false;
    }

    /** 服务端停机时清理世界级 revision，避免单人换存档复用旧会话。 */
    public static synchronized void endServerConfigRuntime() {
        SERVER_RUNTIME_REVISION = 0;
        SERVER_RUNTIME_BASELINE = null;
        SERVER_RUNTIME_INITIALIZED = false;
        SERVER_RUNTIME_BROADCAST_PENDING = false;
    }

    /** 只比较 loader 已载入内存中的有效值，不在 tick 读盘；true 表示需要广播。 */
    public static synchronized boolean pollServerConfigRuntimeChange() {
        ensureRuntimeBaseline();
        refreshRuntimeBaseline();
        boolean pending = SERVER_RUNTIME_BROADCAST_PENDING;
        SERVER_RUNTIME_BROADCAST_PENDING = false;
        return pending;
    }

    private static void markRuntimeConfigSaved() {
        ensureRuntimeBaseline();
        SERVER_RUNTIME_BASELINE = effectiveRuntimeSettings();
        SERVER_RUNTIME_REVISION = nextRuntimeRevision();
        SERVER_RUNTIME_BROADCAST_PENDING = true;
    }

    private static int nextRuntimeRevision() {
        return SERVER_RUNTIME_REVISION == Integer.MAX_VALUE ? 1 : SERVER_RUNTIME_REVISION + 1;
    }

    private static void ensureRuntimeBaseline() {
        if (!SERVER_RUNTIME_INITIALIZED) {
            SERVER_RUNTIME_REVISION = 0;
            SERVER_RUNTIME_BASELINE = effectiveRuntimeSettings();
            SERVER_RUNTIME_INITIALIZED = true;
        }
    }

    /** 在验证权威视图前吸收已经进入 typed spec 的外部重载，不把冲突推迟到下一 tick。 */
    private static void refreshRuntimeBaseline() {
        RtsServerConfigView current = effectiveRuntimeSettings();
        if (SERVER_RUNTIME_BASELINE == null || !current.equals(SERVER_RUNTIME_BASELINE)) {
            SERVER_RUNTIME_BASELINE = current;
            SERVER_RUNTIME_REVISION = nextRuntimeRevision();
            SERVER_RUNTIME_BROADCAST_PENDING = true;
        }
    }

    private static RtsServerConfigView effectiveRuntimeSettings() {
        return serverSettings(0, false, false);
    }

    public static synchronized RtsServerConfigUpdateResult applyServerConfig(RtsServerConfigUpdateRequest request, boolean requesterCanEdit) {
        RtsServerConfigView current = currentServerSettings(requesterCanEdit);
        if (!requesterCanEdit || !canWriteServerConfig()) return RtsServerConfigUpdateResult.notEditable(current.readOnly());
        String error = RtsServerConfigValidator.validate(current, request, true);
        if (error != null) return "configuration revision changed".equals(error)
                ? RtsServerConfigUpdateResult.revisionConflict(current)
                : RtsServerConfigUpdateResult.invalid(current, error);
        // 保存窗口内再次读取，覆盖同 tick 外部 reload 或另一位管理员的先行写入。
        RtsServerConfigView latest = currentServerSettings(requesterCanEdit);
        if (latest.revision() != current.revision() || !latest.equals(current)) return RtsServerConfigUpdateResult.revisionConflict(latest);
        RtsServerConfigView candidate = RtsServerConfigValidator.apply(current, request.changes());
        if (candidate.equals(current)) {
            return new RtsServerConfigUpdateResult(
                    RtsServerConfigUpdateResult.Status.NO_CHANGE, current, "configuration is unchanged");
        }
        for (RtsServerConfigChange change : request.changes()) applyServerChange(change);
        RtsServerConfigPersistence.Result persistence = RtsServerConfigPersistence.saveWithRollback(
                SERVER_SPEC::save,
                () -> {
                    for (RtsServerConfigChange change : request.changes()) {
                        applyServerChange(new RtsServerConfigChange(change.key(), valueFrom(current, change.key())));
                    }
                },
                SERVER_SPEC::save);
        if (!persistence.committed()) {
            return RtsServerConfigUpdateResult.saveFailed(currentServerSettings(requesterCanEdit),
                    "server config save failed; previous value restore was "
                            + (persistence.rollbackCompleted() ? "completed" : "not confirmed"));
        }
        markRuntimeConfigSaved();
        return RtsServerConfigUpdateResult.applied(currentServerSettings(requesterCanEdit));
    }

    /** 生产网络入口：权限由当前连接的 ServerPlayer 重新计算，调用方不能注入布尔值。 */
    public static RtsServerConfigUpdateResult applyServerConfig(RtsServerConfigUpdateRequest request, ServerPlayer player) {
        MinecraftServer server = player == null ? null : player.getServer();
        if (server == null) return RtsServerConfigUpdateResult.notEditable(currentServerSettings(false).readOnly());
        if (server.getRunningThread() != Thread.currentThread()) {
            return RtsServerConfigUpdateResult.saveFailed(currentServerSettings(false).readOnly(), "server configuration request must run on the server thread");
        }
        return applyServerConfig(request, canEditServerConfig(player));
    }

    /**
     * 将服务端已确认的视图投影到当前客户端内存中的 SERVER spec，供既有消费者立即读取。
     * 这里只更新内存，不调用 save；远程客户端不能把服务端值写成本地配置文件。
     */
    public static synchronized void applySynchronizedServerView(RtsServerConfigView view) {
        if (view == null) return;
        for (RtsServerConfigChange.Key key : RtsServerConfigChange.Key.values()) {
            if (isClientConsumedServerKey(key)) {
                applyServerChange(new RtsServerConfigChange(key, valueFrom(view, key)));
            }
        }
    }

    /** 只把客户端现有消费者需要的世界规则写入远端 SERVER 内存镜像。 */
    private static boolean isClientConsumedServerKey(RtsServerConfigChange.Key key) {
        return switch (key) {
            case ENABLE_BLUEPRINTS, MAX_BLUEPRINT_BLOCKS,
                    MAX_SELECTION_VOLUME, MAX_SELECTION_SIZE_X, MAX_SELECTION_SIZE_Y, MAX_SELECTION_SIZE_Z,
                    ULTIMINE_MAX_BLOCKS, MAX_TREE_BLOCKS,
                    MAX_SHAPE_DIMENSION, MAX_SHAPE_RADIUS,
                    SMART_FILL_MAX_BLOCKS, SMART_FILL_DEFAULT_BLOCKS,
                    SMART_FILL_MAX_DIAMETER, SMART_FILL_DEFAULT_DIAMETER,
                    MAX_BATCH_BINDING_SELECTION_VOLUME, MAX_BATCH_BINDING_SIZE_X,
                    MAX_BATCH_BINDING_SIZE_Y, MAX_BATCH_BINDING_SIZE_Z -> true;
            default -> false;
        };
    }

    public static boolean canEditServerConfig(ServerPlayer player) {
        if (player == null || player.getServer() == null) return false;
        return player.hasPermissions(2) || (player.getServer().isSingleplayer() && player.getServer().isSingleplayerOwner(player.getGameProfile()));
    }

    private static RtsServerConfigChange.Value valueFrom(RtsServerConfigView view, RtsServerConfigChange.Key key) {
        return switch (key) {
            case ENABLE_SURVIVAL_PROGRESSION -> new RtsServerConfigChange.BooleanValue(view.enableSurvivalProgression());
            case SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS -> new RtsServerConfigChange.BooleanValue(view.shareSurvivalProgressionWithTeams());
            case ENABLE_BLUEPRINTS -> new RtsServerConfigChange.BooleanValue(view.enableBlueprints());
            case FUNNEL_PICKUP_RADIUS_BLOCKS -> new RtsServerConfigChange.DoubleValue(view.funnelPickupRadiusBlocks());
            case TASK_ENGINE_MAX_NANOS_PER_TICK -> new RtsServerConfigChange.LongValue(view.taskEngineMaxNanosPerTick());
            case AREA_MINE_MAX_HARVEST_TIER -> new RtsServerConfigChange.StringValue(view.areaMineMaxHarvestTier());
            default -> new RtsServerConfigChange.IntValue(intValueFrom(view, key));
        };
    }

    private static int intValueFrom(RtsServerConfigView view, RtsServerConfigChange.Key key) {
        return switch (key) {
            case MAX_ACTION_RADIUS_BLOCKS -> view.maxActionRadiusBlocks();
            case MAX_BLUEPRINT_BLOCKS -> view.maxBlueprintBlocks();
            case MAX_SELECTION_VOLUME -> view.maxSelectionVolume();
            case MAX_SELECTION_SIZE_X -> view.maxSelectionSizeX();
            case MAX_SELECTION_SIZE_Y -> view.maxSelectionSizeY();
            case MAX_SELECTION_SIZE_Z -> view.maxSelectionSizeZ();
            case ULTIMINE_MAX_BLOCKS -> view.ultimineMaxBlocks();
            case ULTIMINE_BLOCKS_PER_TICK -> view.ultimineBlocksPerTick();
            case MAX_TREE_BLOCKS -> view.maxTreeBlocks();
            case HOME_SELECTION_RADIUS_BLOCKS -> view.homeSelectionRadiusBlocks();
            case HOME_RELOCATION_COOLDOWN_DAYS -> view.homeRelocationCooldownDays();
            case MAX_SHAPE_DIMENSION -> view.maxShapeDimension();
            case MAX_SHAPE_RADIUS -> view.maxShapeRadius();
            case SMART_FILL_MAX_BLOCKS -> view.smartFillMaxBlocks();
            case SMART_FILL_DEFAULT_BLOCKS -> view.smartFillDefaultBlocks();
            case SMART_FILL_MAX_DIAMETER -> view.smartFillMaxDiameter();
            case SMART_FILL_DEFAULT_DIAMETER -> view.smartFillDefaultDiameter();
            case MAX_BATCH_BINDING_SELECTION_VOLUME -> view.maxBatchBindingSelectionVolume();
            case MAX_BATCH_BINDING_SIZE_X -> view.maxBatchBindingSizeX();
            case MAX_BATCH_BINDING_SIZE_Y -> view.maxBatchBindingSizeY();
            case MAX_BATCH_BINDING_SIZE_Z -> view.maxBatchBindingSizeZ();
            case MAX_LINKED_STORAGES -> view.maxLinkedStorages();
            case WORKFLOWS_MAX_ACTIVE_PER_PLAYER -> view.workflowsMaxActivePerPlayer();
            case HISTORY_MAX_ENTRIES_PER_STACK -> view.historyMaxEntriesPerStack();
            case HISTORY_RETENTION_SECONDS -> view.historyRetentionSeconds();
            case FUNNEL_MAX_ENTITIES_PER_TICK -> view.funnelMaxEntitiesPerTick();
            case FUNNEL_MAX_ITEMS_PER_TICK -> view.funnelMaxItemsPerTick();
            case FUNNEL_BUFFER_MAX_STACKS -> view.funnelBufferMaxStacks();
            case FUNNEL_TICK_INTERVAL -> view.funnelTickInterval();
            case STORAGE_DROP_CACHE_SOFT_CAPACITY -> view.storageDropCacheSoftCapacity();
            case BUILD_BATCH_BLOCKS_PER_TICK -> view.buildBatchBlocksPerTick();
            case BUILD_BATCH_MAX_QUEUED_JOBS -> view.buildBatchMaxQueuedJobs();
            case TASK_ENGINE_MAX_UNITS_PER_TICK -> view.taskEngineMaxUnitsPerTick();
            case TASK_ENGINE_MAX_UNITS_PER_SLICE -> view.taskEngineMaxUnitsPerSlice();
            case DEFAULT_STORAGE_PAGE_SIZE -> view.defaultStoragePageSize();
            case MAX_STORAGE_PAGE_SIZE -> view.maxStoragePageSize();
            case PAGE_CACHE_MAX_PLAYERS -> view.pageCacheMaxPlayers();
            case AE2_NETWORK_REFRESH_THROTTLE -> view.ae2NetworkRefreshThrottle();
            case REFINED_STORAGE_NETWORK_REFRESH_THROTTLE -> view.refinedStorageNetworkRefreshThrottle();
            case DIAGNOSTICS_MAX_TRACES -> view.diagnosticsMaxTraces();
            case DIAGNOSTICS_MAX_WORKFLOW_LINKS -> view.diagnosticsMaxWorkflowLinks();
            case DIAGNOSTICS_MAX_TASK_LINKS -> view.diagnosticsMaxTaskLinks();
            default -> throw new IllegalArgumentException("not an int configuration key: " + key);
        };
    }

    private static void applyServerChange(RtsServerConfigChange change) {
        switch (change.key()) {
            case ENABLE_SURVIVAL_PROGRESSION -> ENABLE_SURVIVAL_PROGRESSION.set(((RtsServerConfigChange.BooleanValue) change.value()).value());
            case SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS -> SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS.set(((RtsServerConfigChange.BooleanValue) change.value()).value());
            case MAX_ACTION_RADIUS_BLOCKS -> MAX_ACTION_RADIUS_BLOCKS.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case ENABLE_BLUEPRINTS -> ENABLE_BLUEPRINTS.set(((RtsServerConfigChange.BooleanValue) change.value()).value());
            case MAX_BLUEPRINT_BLOCKS -> MAX_BLUEPRINT_BLOCKS.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case MAX_SELECTION_VOLUME -> MAX_SELECTION_VOLUME.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case MAX_SELECTION_SIZE_X -> MAX_SELECTION_SIZE_X.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case MAX_SELECTION_SIZE_Y -> MAX_SELECTION_SIZE_Y.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case MAX_SELECTION_SIZE_Z -> MAX_SELECTION_SIZE_Z.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case ULTIMINE_MAX_BLOCKS -> ULTIMINE_MAX_BLOCKS.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case ULTIMINE_BLOCKS_PER_TICK -> ULTIMINE_BLOCKS_PER_TICK.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case AREA_MINE_MAX_HARVEST_TIER -> AREA_MINE_MAX_HARVEST_TIER.set(RangeMiningHarvestTier.valueOf(((RtsServerConfigChange.StringValue) change.value()).value()));
            case MAX_TREE_BLOCKS -> MAX_TREE_BLOCKS.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case HOME_SELECTION_RADIUS_BLOCKS -> HOME_SELECTION_RADIUS_BLOCKS.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case HOME_RELOCATION_COOLDOWN_DAYS -> HOME_RELOCATION_COOLDOWN_DAYS.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case MAX_SHAPE_DIMENSION -> MAX_SHAPE_DIMENSION.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case MAX_SHAPE_RADIUS -> MAX_SHAPE_RADIUS.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case SMART_FILL_MAX_BLOCKS -> SMART_FILL_MAX_BLOCKS.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case SMART_FILL_DEFAULT_BLOCKS -> SMART_FILL_DEFAULT_BLOCKS.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case SMART_FILL_MAX_DIAMETER -> SMART_FILL_MAX_DIAMETER.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case SMART_FILL_DEFAULT_DIAMETER -> SMART_FILL_DEFAULT_DIAMETER.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case MAX_BATCH_BINDING_SELECTION_VOLUME -> MAX_BATCH_BINDING_SELECTION_VOLUME.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case MAX_BATCH_BINDING_SIZE_X -> MAX_BATCH_BINDING_SIZE_X.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case MAX_BATCH_BINDING_SIZE_Y -> MAX_BATCH_BINDING_SIZE_Y.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case MAX_BATCH_BINDING_SIZE_Z -> MAX_BATCH_BINDING_SIZE_Z.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case MAX_LINKED_STORAGES -> MAX_LINKED_STORAGES.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case FUNNEL_PICKUP_RADIUS_BLOCKS -> FUNNEL_PICKUP_RADIUS_BLOCKS.set(((RtsServerConfigChange.DoubleValue) change.value()).value());
            case WORKFLOWS_MAX_ACTIVE_PER_PLAYER -> WORKFLOWS_MAX_ACTIVE_PER_PLAYER.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case HISTORY_MAX_ENTRIES_PER_STACK -> HISTORY_MAX_ENTRIES_PER_STACK.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case HISTORY_RETENTION_SECONDS -> HISTORY_RETENTION_SECONDS.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case FUNNEL_MAX_ENTITIES_PER_TICK -> FUNNEL_MAX_ENTITIES_PER_TICK.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case FUNNEL_MAX_ITEMS_PER_TICK -> FUNNEL_MAX_ITEMS_PER_TICK.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case FUNNEL_BUFFER_MAX_STACKS -> FUNNEL_BUFFER_MAX_STACKS.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case FUNNEL_TICK_INTERVAL -> FUNNEL_TICK_INTERVAL.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case STORAGE_DROP_CACHE_SOFT_CAPACITY -> STORAGE_DROP_CACHE_SOFT_CAPACITY.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case BUILD_BATCH_BLOCKS_PER_TICK -> BUILD_BATCH_BLOCKS_PER_TICK.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case BUILD_BATCH_MAX_QUEUED_JOBS -> BUILD_BATCH_MAX_QUEUED_JOBS.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case TASK_ENGINE_MAX_UNITS_PER_TICK -> TASK_ENGINE_MAX_UNITS_PER_TICK.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case TASK_ENGINE_MAX_UNITS_PER_SLICE -> TASK_ENGINE_MAX_UNITS_PER_SLICE.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case TASK_ENGINE_MAX_NANOS_PER_TICK -> TASK_ENGINE_MAX_NANOS_PER_TICK.set(((RtsServerConfigChange.LongValue) change.value()).value());
            case DEFAULT_STORAGE_PAGE_SIZE -> DEFAULT_STORAGE_PAGE_SIZE.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case MAX_STORAGE_PAGE_SIZE -> MAX_STORAGE_PAGE_SIZE.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case PAGE_CACHE_MAX_PLAYERS -> PAGE_CACHE_MAX_PLAYERS.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case AE2_NETWORK_REFRESH_THROTTLE -> AE2_NETWORK_REFRESH_THROTTLE.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case REFINED_STORAGE_NETWORK_REFRESH_THROTTLE -> REFINED_STORAGE_NETWORK_REFRESH_THROTTLE.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case DIAGNOSTICS_MAX_TRACES -> DIAGNOSTICS_MAX_TRACES.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case DIAGNOSTICS_MAX_WORKFLOW_LINKS -> DIAGNOSTICS_MAX_WORKFLOW_LINKS.set(((RtsServerConfigChange.IntValue) change.value()).value());
            case DIAGNOSTICS_MAX_TASK_LINKS -> DIAGNOSTICS_MAX_TASK_LINKS.set(((RtsServerConfigChange.IntValue) change.value()).value());
        }
    }

    /**
     * 把旧版本真正落盘的保守默认值迁移到当前默认值，同时保留玩家主动设置的其他数值。
     *
     * @return 本次是否写入了新的迁移版本
     */
    /** 在服务端线程消费 loader 适配器留下的待迁移快照；watcher 回调只负责截获原文。 */
    public static synchronized boolean consumePendingLegacyServerMigration() {
        if (!LEGACY_MIGRATION_PENDING || LEGACY_MIGRATION_RUNNING) return false;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getRunningThread() != Thread.currentThread()) return false;
        LEGACY_MIGRATION_PENDING = false;
        LEGACY_MIGRATION_RUNNING = true;
        try { return migrateLegacyServerDefaultsInternal(null, null); }
        finally { LEGACY_MIGRATION_RUNNING = false; clearRawConfigSnapshots(); }
    }

    /** 兼容旧调用方的同步门面；生产生命周期应使用 {@link #consumePendingLegacyServerMigration()}。 */
    public static synchronized boolean migrateLegacyServerDefaults() {
        if (RAW_SERVER_SNAPSHOT == null || RAW_COMMON_SNAPSHOT == null) return false;
        return migrateLegacyServerDefaults(null, null);
    }

    /** 使用显式 SERVER 原文执行一次幂等迁移，保留旧 API 供回归夹具调用。 */
    public static synchronized boolean migrateLegacyServerDefaults(String rawToml) {
        return migrateLegacyServerDefaults(rawToml, null);
    }

    /** 在当前世界首次加载时把旧 COMMON 世界规则迁入 SERVER；真正 loader 回调不得调用。 */
    public static synchronized boolean migrateLegacyServerDefaults(String rawToml, String rawCommonToml) {
        if (LEGACY_MIGRATION_RUNNING) return false;
        LEGACY_MIGRATION_PENDING = false;
        LEGACY_MIGRATION_RUNNING = true;
        try { return migrateLegacyServerDefaultsInternal(rawToml, rawCommonToml); }
        finally { LEGACY_MIGRATION_RUNNING = false; clearRawConfigSnapshots(); }
    }

    private static boolean migrateLegacyServerDefaultsInternal(String rawToml, String rawCommonToml) {
        if (rawToml == null && RAW_SERVER_SNAPSHOT != null && !RAW_SERVER_SNAPSHOT.failure()) rawToml = RAW_SERVER_SNAPSHOT.contents();
        if (rawCommonToml == null && RAW_COMMON_SNAPSHOT != null && !RAW_COMMON_SNAPSHOT.failure()) rawCommonToml = RAW_COMMON_SNAPSHOT.contents();
        if ((RAW_SERVER_SNAPSHOT != null && RAW_SERVER_SNAPSHOT.failure())
                || (RAW_COMMON_SNAPSHOT != null && RAW_COMMON_SNAPSHOT.failure()) || !canWriteServerConfig()) return false;
        RtsServerConfigView previous = effectiveRuntimeSettings();
        int previousSchemaRevision = SERVER_CONFIG_REVISION.get();
        boolean changed = migrateLegacyCommonWorldRules(rawToml, rawCommonToml);
        ServerConfigMigration.Values migrated = ServerConfigMigration.migrate(
                previousSchemaRevision, ULTIMINE_BLOCKS_PER_TICK.get(), TASK_ENGINE_MAX_NANOS_PER_TICK.get());
        if (migrated.revision() != previousSchemaRevision) {
            ULTIMINE_BLOCKS_PER_TICK.set(migrated.miningSlice());
            TASK_ENGINE_MAX_NANOS_PER_TICK.set(migrated.taskBudgetNanos());
            SERVER_CONFIG_REVISION.set(migrated.revision());
            changed = true;
        }
        MiningSelectionConfigMigration.Source source = rawToml != null
                ? MiningSelectionConfigMigration.Source.fromRawToml(rawToml) : miningSelectionSourceFromTypedValues();
        MiningSelectionConfigMigration.Result selection = MiningSelectionConfigMigration.resolve(source);
        if (selection.changed() && (MAX_SELECTION_VOLUME.get() != selection.volume()
                || MAX_SELECTION_SIZE_X.get() != selection.sizeX() || MAX_SELECTION_SIZE_Y.get() != selection.sizeY()
                || MAX_SELECTION_SIZE_Z.get() != selection.sizeZ())) {
            MAX_SELECTION_VOLUME.set(selection.volume()); MAX_SELECTION_SIZE_X.set(selection.sizeX());
            MAX_SELECTION_SIZE_Y.set(selection.sizeY()); MAX_SELECTION_SIZE_Z.set(selection.sizeZ()); changed = true;
        }
        if (!changed) return false;
        // 先清空旧快照，再调用 save；save 触发的回调不能重新消费同一份 raw。
        clearRawConfigSnapshots();
        RtsServerConfigPersistence.Result persistence = RtsServerConfigPersistence.saveWithRollback(
                SERVER_SPEC::save,
                () -> restoreLegacyMigrationValues(previous, previousSchemaRevision),
                SERVER_SPEC::save);
        return persistence.committed();
    }

    private static MiningSelectionConfigMigration.Source miningSelectionSourceFromTypedValues() {
        boolean legacyPresent = AREA_MINE_MAX_VOLUME.get() > 0 || AREA_MINE_MAX_WIDTH.get() > 0
                || AREA_MINE_MAX_HEIGHT.get() > 0 || AREA_MINE_MAX_DEPTH.get() > 0 || AREA_MINE_MAX_SIZE.get() > 0
                || AREA_DESTROY_MAX_TARGETS.get() > 0;
        boolean canonicalFresh = MAX_SELECTION_VOLUME.get() == MiningLimits.DEFAULT_VOLUME
                && MAX_SELECTION_SIZE_X.get() == 64 && MAX_SELECTION_SIZE_Y.get() == 64 && MAX_SELECTION_SIZE_Z.get() == 64;
        boolean canonicalPresent = !legacyPresent || !canonicalFresh;
        return new MiningSelectionConfigMigration.Source(canonicalPresent, MAX_SELECTION_VOLUME.get(), canonicalPresent,
                MAX_SELECTION_SIZE_X.get(), canonicalPresent, MAX_SELECTION_SIZE_Y.get(), canonicalPresent,
                MAX_SELECTION_SIZE_Z.get(), AREA_MINE_MAX_VOLUME.get() > 0, AREA_MINE_MAX_VOLUME.get(),
                AREA_MINE_MAX_WIDTH.get() > 0, AREA_MINE_MAX_WIDTH.get(), AREA_MINE_MAX_HEIGHT.get() > 0,
                AREA_MINE_MAX_HEIGHT.get(), AREA_MINE_MAX_DEPTH.get() > 0, AREA_MINE_MAX_DEPTH.get(),
                AREA_MINE_MAX_SIZE.get() > 0, AREA_MINE_MAX_SIZE.get(), AREA_DESTROY_MAX_TARGETS.get() > 0,
                AREA_DESTROY_MAX_TARGETS.get());
    }

    private static void restoreLegacyMigrationValues(RtsServerConfigView previous, int schemaRevision) {
        ENABLE_SURVIVAL_PROGRESSION.set(previous.enableSurvivalProgression());
        SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS.set(previous.shareSurvivalProgressionWithTeams());
        MAX_ACTION_RADIUS_BLOCKS.set(previous.maxActionRadiusBlocks()); ENABLE_BLUEPRINTS.set(previous.enableBlueprints());
        MAX_BLUEPRINT_BLOCKS.set(previous.maxBlueprintBlocks()); MAX_SELECTION_VOLUME.set(previous.maxSelectionVolume());
        MAX_SELECTION_SIZE_X.set(previous.maxSelectionSizeX()); MAX_SELECTION_SIZE_Y.set(previous.maxSelectionSizeY());
        MAX_SELECTION_SIZE_Z.set(previous.maxSelectionSizeZ()); ULTIMINE_BLOCKS_PER_TICK.set(previous.ultimineBlocksPerTick());
        TASK_ENGINE_MAX_NANOS_PER_TICK.set(previous.taskEngineMaxNanosPerTick()); SERVER_CONFIG_REVISION.set(schemaRevision);
    }

    /** loader 适配器在 ConfigSpec 回调或服务器世界边界的第一步调用。 */
    public static synchronized void captureRawServerConfig(RtsServerConfigRawSnapshot snapshot) {
        if (snapshot == null) return;
        // 每次 loader load/reload 都替换快照；旧世界 raw 不能泄漏到下一个世界。
        RAW_SERVER_SNAPSHOT = snapshot;
        LEGACY_MIGRATION_PENDING = true;
    }
    public static synchronized void captureRawCommonConfig(RtsServerConfigRawSnapshot snapshot) {
        if (snapshot == null) return;
        // COMMON 是跨世界的本机来源，但同一进程切换存档时仍要刷新快照。
        RAW_COMMON_SNAPSHOT = snapshot;
    }
    public static synchronized void clearRawConfigSnapshots() {
        RAW_SERVER_SNAPSHOT = null;
        RAW_COMMON_SNAPSHOT = null;
        LEGACY_MIGRATION_PENDING = false;
    }

    private static boolean migrateLegacyCommonWorldRules(String rawServerToml, String rawCommonToml) {
        if (rawCommonToml == null || rawCommonToml.isBlank()) return false;
        boolean changed = false;
        if (!rawKeyPresent(rawServerToml, "enableSurvivalProgression")
                && rawKeyPresent(rawCommonToml, "enableSurvivalProgression")) {
            ENABLE_SURVIVAL_PROGRESSION.set(rawBoolean(rawCommonToml, "enableSurvivalProgression", ENABLE_SURVIVAL_PROGRESSION.get()));
            changed = true;
        }
        if (!rawKeyPresent(rawServerToml, "shareSurvivalProgressionWithTeams")
                && rawKeyPresent(rawCommonToml, "shareSurvivalProgressionWithTeams")) {
            SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS.set(rawBoolean(rawCommonToml, "shareSurvivalProgressionWithTeams", SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS.get()));
            changed = true;
        }
        if (!rawKeyPresent(rawServerToml, "maxActionRadiusBlocks")
                && rawKeyPresent(rawCommonToml, "maxActionRadiusBlocks")) {
            MAX_ACTION_RADIUS_BLOCKS.set(clampInt(rawInt(rawCommonToml, "maxActionRadiusBlocks", MAX_ACTION_RADIUS_BLOCKS.get()), 48, 512));
            changed = true;
        }
        if (!rawKeyPresent(rawServerToml, "enableBlueprints")
                && rawKeyPresent(rawCommonToml, "enableBlueprints")) {
            ENABLE_BLUEPRINTS.set(rawBoolean(rawCommonToml, "enableBlueprints", ENABLE_BLUEPRINTS.get()));
            changed = true;
        }
        if (!rawKeyPresent(rawServerToml, "maxBlueprintBlocks")
                && rawKeyPresent(rawCommonToml, "maxBlueprintBlocks")) {
            MAX_BLUEPRINT_BLOCKS.set(clampInt(rawInt(rawCommonToml, "maxBlueprintBlocks", MAX_BLUEPRINT_BLOCKS.get()), 1, 200_000));
            changed = true;
        }
        return changed;
    }

    private static boolean rawKeyPresent(String rawToml, String key) {
        if (rawToml == null || rawToml.isBlank()) return false;
        return Pattern.compile("(?m)^\\s*[\\\"']?" + Pattern.quote(key) + "[\\\"']?\\s*=").matcher(rawToml).find();
    }

    private static int rawInt(String rawToml, String key, int fallback) {
        Matcher matcher = Pattern.compile("(?m)^\\s*[\\\"']?" + Pattern.quote(key) + "[\\\"']?\\s*=\\s*(-?\\d+)").matcher(rawToml == null ? "" : rawToml);
        if (!matcher.find()) return fallback;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean rawBoolean(String rawToml, String key, boolean fallback) {
        Matcher matcher = Pattern.compile("(?mi)^\\s*[\\\"']?" + Pattern.quote(key) + "[\\\"']?\\s*=\\s*(true|false)").matcher(rawToml == null ? "" : rawToml);
        return matcher.find() ? Boolean.parseBoolean(matcher.group(1)) : fallback;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean canWriteServerConfig() {
        return ServerLifecycleHooks.getCurrentServer() != null;
    }

}

