package com.rtsbuilding.rtsbuilding;

import com.rtsbuilding.rtsbuilding.server.service.mining.RangeMiningHarvestTier;
import com.rtsbuilding.rtsbuilding.network.RtsProtocolLimits;
import com.rtsbuilding.rtsbuilding.common.diagnostics.RtsDiagnosticLevel;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import net.minecraftforge.fluids.Fluid;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Forge 1.12.2 配置边界。
 *
 * <p>对业务层保留 NeoForge 版本使用的值对象访问形状，但底层全部由 Forge
 * {@link Configuration} 和 {@link Property} 驱动。这样服务器、客户端和通用配置仍然分文件，
 * 其他模块不需要知道加载器版本，也不会在专用服务端加载任何客户端类。</p>
 */
public final class Config {
    private enum Domain { COMMON, CLIENT, SERVER }

    private static final List<Value<?>> VALUES = new ArrayList<>();
    private static Configuration commonConfiguration;
    private static Configuration clientConfiguration;
    private static Configuration serverConfiguration;

    public static final BooleanValue ENABLE_SURVIVAL_PROGRESSION = bool(Domain.COMMON, "general",
            "enableSurvivalProgression", false, "Enable RTS Home anchors and home-radius limits.",
            "rtsbuilding.configuration.enableSurvivalProgression");
    public static final BooleanValue SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS = bool(Domain.COMMON, "general",
            "shareSurvivalProgressionWithTeams", false,
            "When RTS Home is enabled, share RTS home anchors and team plugins with the player's FTB Team, OpenPAC party, or vanilla scoreboard team.",
            "rtsbuilding.configuration.shareSurvivalProgressionWithTeams");
    public static final IntValue MAX_ACTION_RADIUS_BLOCKS = integer(Domain.COMMON, "general",
            "maxActionRadiusBlocks", 128, 48, 512, "Maximum RTS action radius in blocks.",
            "rtsbuilding.configuration.maxActionRadiusBlocks");
    public static final BooleanValue ENABLE_BLUEPRINTS = bool(Domain.COMMON, "general", "enableBlueprints", true,
            "Enable the RTS blueprint library tab, local blueprint upload, and server-side blueprint placement.",
            "rtsbuilding.configuration.enableBlueprints");
    public static final IntValue MAX_BLUEPRINT_BLOCKS = integer(Domain.COMMON, "general", "maxBlueprintBlocks",
            20000, 1, 200000, "Maximum non-air blocks allowed in one RTS blueprint import, capture, or placement job.",
            "rtsbuilding.configuration.maxBlueprintBlocks");

    public static final BooleanValue ENABLE_UI_ANIMATIONS = bool(Domain.CLIENT, "rendering", "enableUiAnimations",
            true, "Enable short visual-only hover and selection transitions in the RTS UI.",
            "rtsbuilding.configuration.enableUiAnimations");
    public static final BooleanValue USE_BLOCK_GHOST_PREVIEW = bool(Domain.CLIENT, "rendering", "useBlockGhostPreview",
            false, "Render translucent block ghost models for placement previews before the player confirms placement.",
            "rtsbuilding.configuration.useBlockGhostPreview");
    public static final BooleanValue USE_PLACE_BLOCK_GHOST_ANIMATION = bool(Domain.CLIENT, "rendering",
            "usePlaceBlockGhostAnimation", true,
            "Render translucent grow-in block ghosts after server-confirmed block placement.",
            "rtsbuilding.configuration.usePlaceBlockGhostAnimation");
    public static final BooleanValue USE_DESTROY_BLOCK_GHOST_ANIMATION = bool(Domain.CLIENT, "rendering",
            "useDestroyBlockGhostAnimation", true,
            "Render translucent shrink-out block ghosts after server-confirmed block destruction.",
            "rtsbuilding.configuration.useDestroyBlockGhostAnimation");
    public static final BooleanValue USE_WIREFRAME_PREVIEW = bool(Domain.CLIENT, "rendering", "useWireframePreview",
            false, "Render wireframe outlines for placement previews before the player confirms placement.",
            "rtsbuilding.configuration.useWireframePreview");
    public static final BooleanValue USE_PLACE_WIREFRAME_ANIMATION = bool(Domain.CLIENT, "rendering",
            "usePlaceWireframeAnimation", false,
            "Render grow-in wireframe outlines after server-confirmed block placement.",
            "rtsbuilding.configuration.usePlaceWireframeAnimation");
    public static final BooleanValue USE_DESTROY_WIREFRAME_ANIMATION = bool(Domain.CLIENT, "rendering",
            "useDestroyWireframeAnimation", false,
            "Render shrink-out wireframe outlines after server-confirmed block destruction.",
            "rtsbuilding.configuration.useDestroyWireframeAnimation");
    public static final BooleanValue USE_RANGE_DESTROY_SKELETON = bool(Domain.CLIENT, "rendering",
            "useRangeDestroySkeleton", true,
            "Render merged skeleton borders for non-chain range destroy previews. Chain mining always uses the skeleton style.",
            "rtsbuilding.configuration.useRangeDestroySkeleton");
    public static final BooleanValue SHOW_INVENTORY_RTS_BUTTON = bool(Domain.CLIENT, "interface",
            "showInventoryRtsButton", true, "Show the RTS plugin button on the vanilla inventory screen.",
            "rtsbuilding.configuration.showInventoryRtsButton");
    public static final BooleanValue REQUIRE_KEYBOARD_BATCH_CONFIRM = bool(Domain.CLIENT, "controls",
            "requireKeyboardBatchConfirm", true,
            "Require a configurable keyboard key for the final multi-block placement/destroy confirmation.",
            "rtsbuilding.configuration.requireKeyboardBatchConfirm");
    public static final BooleanValue DEVELOPER_MODE = bool(Domain.CLIENT, "general", "developerMode", false,
            "Show the developer scenario task entry and write local diagnostic logs.",
            "rtsbuilding.configuration.developerMode");
    public static final EnumValue<RtsDiagnosticLevel> CLIENT_DIAGNOSTIC_LEVEL = enumeration(Domain.CLIENT,
            "diagnostics", "level", RtsDiagnosticLevel.BASIC,
            "Client diagnostic detail: OFF, BASIC, or VERBOSE.", null);
    public static final EnumValue<RtsDiagnosticLevel> SERVER_DIAGNOSTIC_LEVEL = enumeration(Domain.SERVER,
            "diagnostics", "level", RtsDiagnosticLevel.BASIC,
            "Server diagnostic detail: OFF, BASIC, or VERBOSE.", null);

    public static final IntValue ULTIMINE_MAX_BLOCKS = integer(Domain.SERVER, "mining", "ultimineMaxBlocks",
            256, 1, 4096, "Maximum blocks collected by one RTS chain mining request.",
            "rtsbuilding.configuration.ultimineMaxBlocks");
    public static final IntValue AREA_MINE_MAX_SIZE = integer(Domain.SERVER, "mining", "areaMineMaxSize",
            36, 1, 64, "Maximum block count per dimension for RTS area mining selections.",
            "rtsbuilding.configuration.areaMineMaxSize");
    public static final IntValue AREA_MINE_MAX_VOLUME = integer(Domain.SERVER, "mining", "areaMineMaxVolume",
            46656, 1, RtsProtocolLimits.AREA_MINE_MAX_VOLUME,
            "Maximum covered volume accepted by one RTS area mining selection.",
            "rtsbuilding.configuration.areaMineMaxVolume");
    public static final IntValue AREA_MINE_MAX_WIDTH = integer(Domain.SERVER, "mining", "areaMineMaxWidth",
            36, 1, 256, "Maximum X-axis width accepted by one RTS area mining selection.",
            "rtsbuilding.configuration.areaMineMaxWidth");
    public static final IntValue AREA_MINE_MAX_HEIGHT = integer(Domain.SERVER, "mining", "areaMineMaxHeight",
            36, 1, 256, "Maximum Y-axis height accepted by one RTS area mining selection.",
            "rtsbuilding.configuration.areaMineMaxHeight");
    public static final IntValue AREA_MINE_MAX_DEPTH = integer(Domain.SERVER, "mining", "areaMineMaxDepth",
            36, 1, 256, "Maximum Z-axis depth accepted by one RTS area mining selection.",
            "rtsbuilding.configuration.areaMineMaxDepth");
    public static final EnumValue<RangeMiningHarvestTier> AREA_MINE_MAX_HARVEST_TIER = enumeration(Domain.SERVER,
            "mining", "areaMineMaxHarvestTier", RangeMiningHarvestTier.UNLIMITED,
            "Server ceiling for harvest-tier plugins used by non-chain RTS range mining.",
            "rtsbuilding.configuration.areaMineMaxHarvestTier");
    public static final IntValue AE2_NETWORK_REFRESH_THROTTLE = integer(Domain.SERVER, "storage",
            "ae2NetworkRefreshThrottle", 10, 1, 200,
            "Number of storage cache refresh cycles between expensive AE2 network snapshots.",
            "rtsbuilding.configuration.ae2NetworkRefreshThrottle");
    public static final IntValue REFINED_STORAGE_NETWORK_REFRESH_THROTTLE = integer(Domain.SERVER, "storage",
            "refinedStorageNetworkRefreshThrottle", 10, 1, 200,
            "Number of storage cache refresh cycles between expensive Refined Storage network snapshots.",
            "rtsbuilding.configuration.refinedStorageNetworkRefreshThrottle");
    /** 异维储存只保留短期区块票据；该总开关不会放宽同维 RTS 操作半径。 */
    public static final BooleanValue ENABLE_CROSS_DIMENSION_STORAGE = bool(Domain.SERVER, "storage",
            "enableCrossDimensionStorage", true,
            "Allow the cross-dimension storage plugin to wake and access already-linked storage in other dimensions.",
            "rtsbuilding.configuration.enableCrossDimensionStorage");
    public static final IntValue MAX_CROSS_DIMENSION_AWAKE_CHUNKS = integer(Domain.SERVER, "storage",
            "maxCrossDimensionAwakeChunks", 32, 1, 256,
            "Maximum short-lived cross-dimension storage chunk tickets retained for one player.",
            "rtsbuilding.configuration.maxCrossDimensionAwakeChunks");
    public static final IntValue PAGE_CACHE_MAX_PLAYERS = integer(Domain.SERVER, "storage", "pageCacheMaxPlayers",
            256, 1, 4096, "Maximum player count retained by the storage page LRU cache.",
            "rtsbuilding.configuration.pageCacheMaxPlayers");
    public static final IntValue DEFAULT_STORAGE_PAGE_SIZE = integer(Domain.SERVER, "storage",
            "defaultStoragePageSize", 90, 1, 4096, "Default entries shown per RTS storage page.",
            "rtsbuilding.configuration.defaultStoragePageSize");
    public static final IntValue MAX_STORAGE_PAGE_SIZE = integer(Domain.SERVER, "storage", "maxStoragePageSize",
            180, 1, 8192, "Maximum entries accepted by an RTS storage page request.",
            "rtsbuilding.configuration.maxStoragePageSize");
    public static final IntValue AREA_DESTROY_MAX_TARGETS = integer(Domain.SERVER, "mining",
            "areaDestroyMaxTargets", 98304, 1, RtsProtocolLimits.AREA_DESTROY_MAX_POSITIONS,
            "Maximum explicit positions accepted by one RTS area destroy request.",
            "rtsbuilding.configuration.areaDestroyMaxTargets");
    public static final IntValue ULTIMINE_BLOCKS_PER_TICK = integer(Domain.SERVER, "mining",
            "ultimineBlocksPerTick", 32, 1, 128,
            "Maximum queued mining targets processed by one mining task slice.",
            "rtsbuilding.configuration.ultimineBlocksPerTick");
    public static final IntValue BUILD_BATCH_BLOCKS_PER_TICK = integer(Domain.SERVER, "placement",
            "buildBatchBlocksPerTick", 64, 1, 512,
            "Maximum queued remote placement targets processed per player per server tick.",
            "rtsbuilding.configuration.buildBatchBlocksPerTick");
    public static final IntValue BUILD_BATCH_MAX_QUEUED_JOBS = integer(Domain.SERVER, "placement",
            "buildBatchMaxQueuedJobs", 4, 1, 32, "Maximum queued quick-build placement jobs per player.",
            "rtsbuilding.configuration.buildBatchMaxQueuedJobs");
    public static final IntValue TASK_ENGINE_MAX_UNITS_PER_TICK = integer(Domain.SERVER, "taskEngine",
            "maxUnitsPerTick", 256, 1, 4096, "Hard global RTS work-unit limit in one server tick.",
            "rtsbuilding.configuration.taskEngineMaxUnitsPerTick");
    public static final IntValue TASK_ENGINE_MAX_UNITS_PER_SLICE = integer(Domain.SERVER, "taskEngine",
            "maxUnitsPerSlice", 32, 1, 512, "Maximum RTS work units granted before rotating players.",
            "rtsbuilding.configuration.taskEngineMaxUnitsPerSlice");
    public static final LongValue TASK_ENGINE_MAX_NANOS_PER_TICK = longValue(Domain.SERVER, "taskEngine",
            "maxNanosPerTick", 8_000_000L, 250_000L, 20_000_000L,
            "Cooperative RTS main-thread time budget per server tick in nanoseconds.",
            "rtsbuilding.configuration.taskEngineMaxNanosPerTick");
    public static final DoubleValue REMOTE_POV_BLOCK_REACH = decimal(Domain.SERVER, "interaction",
            "remotePovBlockReach", 4.0D, 1.0D, 16.0D,
            "Temporary interaction reach used while replaying a remote player action.",
            "rtsbuilding.configuration.remotePovBlockReach");
    public static final DoubleValue DROP_SCAN_RADIUS = decimal(Domain.SERVER, "mining", "dropScanRadius",
            1.25D, 0.25D, 8.0D, "Radius used to absorb drops around remotely mined blocks.",
            "rtsbuilding.configuration.dropScanRadius");
    public static final IntValue REMOTE_PLACE_SOUNDS_PER_TICK = integer(Domain.SERVER, "placement",
            "remoteBlockActionSoundsPerTick", 16, 0, 16,
            "Maximum RTS remote block action sounds sent per player per tick.",
            "rtsbuilding.configuration.remotePlaceSoundsPerTick");
    public static final IntValue INTERNAL_FLUID_CAPACITY_BUCKETS = integer(Domain.SERVER, "fluid",
            "internalFluidCapacityBuckets", 100, 1, 4096,
            "Fallback internal fluid buffer capacity in buckets when progression data is unavailable.",
            "rtsbuilding.configuration.internalFluidCapacityBuckets");
    private static final IntValue SERVER_CONFIG_REVISION = integer(Domain.SERVER, "internal", "configRevision",
            0, 0, ServerConfigMigration.CURRENT_REVISION,
            "Internal RTSBuilding server configuration migration revision. Do not edit manually.", null);

    private Config() {
    }

    /** 在 preInit 中建立并加载三个独立的 Forge 配置文件。 */
    public static synchronized void initialize(File configDirectory, boolean loadClient) {
        File directory = new File(configDirectory, "rts_building");
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IllegalStateException("无法创建 RTSBuilding 配置目录: " + directory);
        }
        commonConfiguration = new Configuration(new File(directory, "rtsbuilding-common.cfg"));
        serverConfiguration = new Configuration(new File(directory, "rtsbuilding-server.cfg"));
        clientConfiguration = loadClient
                ? new Configuration(new File(directory, "rtsbuilding-client.cfg"))
                : null;
        loadConfiguration(commonConfiguration);
        loadConfiguration(serverConfiguration);
        if (clientConfiguration != null) {
            loadConfiguration(clientConfiguration);
        }
        for (Value<?> value : VALUES) {
            if (configuration(value.domain) != null) {
                value.load(configuration(value.domain));
            }
        }
        saveChangedConfigurations();
    }

    public static synchronized void reload() {
        loadConfiguration(commonConfiguration);
        loadConfiguration(serverConfiguration);
        loadConfiguration(clientConfiguration);
        for (Value<?> value : VALUES) {
            Configuration configuration = configuration(value.domain);
            if (configuration != null) {
                value.load(configuration);
            }
        }
        saveChangedConfigurations();
    }

    private static void loadConfiguration(Configuration configuration) {
        if (configuration != null) {
            configuration.load();
        }
    }

    private static Configuration configuration(Domain domain) {
        switch (domain) {
            case COMMON: return commonConfiguration;
            case CLIENT: return clientConfiguration;
            case SERVER: return serverConfiguration;
            default: throw new IllegalStateException("未知配置域: " + domain);
        }
    }

    private static void saveChangedConfigurations() {
        saveIfChanged(commonConfiguration);
        saveIfChanged(clientConfiguration);
        saveIfChanged(serverConfiguration);
    }

    private static void saveIfChanged(Configuration configuration) {
        if (configuration != null && configuration.hasChanged()) {
            configuration.save();
        }
    }

    private static BooleanValue bool(Domain domain, String category, String key, boolean defaultValue,
            String comment, String translation) {
        return add(new BooleanValue(domain, category, key, defaultValue, comment, translation));
    }

    private static IntValue integer(Domain domain, String category, String key, int defaultValue, int min, int max,
            String comment, String translation) {
        return add(new IntValue(domain, category, key, defaultValue, min, max, comment, translation));
    }

    private static LongValue longValue(Domain domain, String category, String key, long defaultValue, long min,
            long max, String comment, String translation) {
        return add(new LongValue(domain, category, key, defaultValue, min, max, comment, translation));
    }

    private static DoubleValue decimal(Domain domain, String category, String key, double defaultValue, double min,
            double max, String comment, String translation) {
        return add(new DoubleValue(domain, category, key, defaultValue, min, max, comment, translation));
    }

    private static <E extends Enum<E>> EnumValue<E> enumeration(Domain domain, String category, String key,
            E defaultValue, String comment, String translation) {
        return add(new EnumValue<>(domain, category, key, defaultValue, comment, translation));
    }

    private static <T extends Value<?>> T add(T value) {
        VALUES.add(value);
        return value;
    }

    public abstract static class Value<T> {
        private final Domain domain;
        protected final String category;
        protected final String key;
        protected final T defaultValue;
        protected final String comment;
        protected final String translation;
        protected Property property;
        protected T value;

        Value(Domain domain, String category, String key, T defaultValue, String comment, String translation) {
            this.domain = domain;
            this.category = category;
            this.key = key;
            this.defaultValue = defaultValue;
            this.comment = comment;
            this.translation = translation;
            this.value = defaultValue;
        }

        abstract void load(Configuration configuration);

        protected final void finishProperty() {
            if (translation != null) {
                property.setLanguageKey(translation);
            }
        }

        protected final void saveDomain() {
            Configuration configuration = configuration(domain);
            if (configuration != null) {
                configuration.save();
            }
        }
    }

    public static final class BooleanValue extends Value<Boolean> {
        BooleanValue(Domain domain, String category, String key, boolean defaultValue, String comment,
                String translation) {
            super(domain, category, key, defaultValue, comment, translation);
        }

        @Override
        void load(Configuration configuration) {
            property = configuration.get(category, key, defaultValue, comment);
            finishProperty();
            value = property.getBoolean(defaultValue);
        }

        public boolean getAsBoolean() { return value; }
        public Boolean get() { return value; }
        public void set(boolean newValue) {
            value = newValue;
            if (property != null) property.set(newValue);
        }
    }

    public static final class IntValue extends Value<Integer> {
        private final int min;
        private final int max;

        IntValue(Domain domain, String category, String key, int defaultValue, int min, int max, String comment,
                String translation) {
            super(domain, category, key, defaultValue, comment, translation);
            this.min = min;
            this.max = max;
        }

        @Override
        void load(Configuration configuration) {
            property = configuration.get(category, key, defaultValue, comment, min, max);
            finishProperty();
            value = clampInt(property.getInt(defaultValue), min, max);
        }

        public int getAsInt() { return value; }
        public Integer get() { return value; }
        public void set(int newValue) {
            value = clampInt(newValue, min, max);
            if (property != null) property.set(value);
        }
    }

    public static final class LongValue extends Value<Long> {
        private final long min;
        private final long max;

        LongValue(Domain domain, String category, String key, long defaultValue, long min, long max, String comment,
                String translation) {
            super(domain, category, key, defaultValue, comment, translation);
            this.min = min;
            this.max = max;
        }

        @Override
        void load(Configuration configuration) {
            property = configuration.get(category, key, Long.toString(defaultValue), comment);
            property.setMinValue((double) min).setMaxValue((double) max);
            finishProperty();
            value = Math.max(min, Math.min(max, property.getLong(defaultValue)));
        }

        public long getAsLong() { return value; }
        public Long get() { return value; }
        public void set(long newValue) {
            value = Math.max(min, Math.min(max, newValue));
            if (property != null) property.set(value);
        }
    }

    public static final class DoubleValue extends Value<Double> {
        private final double min;
        private final double max;

        DoubleValue(Domain domain, String category, String key, double defaultValue, double min, double max,
                String comment, String translation) {
            super(domain, category, key, defaultValue, comment, translation);
            this.min = min;
            this.max = max;
        }

        @Override
        void load(Configuration configuration) {
            property = configuration.get(category, key, defaultValue, comment, min, max);
            finishProperty();
            value = Math.max(min, Math.min(max, property.getDouble(defaultValue)));
        }

        public double getAsDouble() { return value; }
        public Double get() { return value; }
        public void set(double newValue) {
            value = Math.max(min, Math.min(max, newValue));
            if (property != null) property.set(value);
        }
    }

    public static final class EnumValue<E extends Enum<E>> extends Value<E> {
        private final Class<E> enumType;

        @SuppressWarnings("unchecked")
        EnumValue(Domain domain, String category, String key, E defaultValue, String comment, String translation) {
            super(domain, category, key, defaultValue, comment, translation);
            this.enumType = (Class<E>) defaultValue.getDeclaringClass();
        }

        @Override
        void load(Configuration configuration) {
            E[] constants = enumType.getEnumConstants();
            String[] validValues = new String[constants.length];
            for (int i = 0; i < constants.length; i++) validValues[i] = constants[i].name();
            property = configuration.get(category, key, defaultValue.name(), comment, validValues);
            finishProperty();
            try {
                value = Enum.valueOf(enumType, property.getString());
            } catch (IllegalArgumentException invalid) {
                value = defaultValue;
                property.set(defaultValue.name());
            }
        }

        public E get() { return value; }
        public void set(E newValue) {
            value = newValue == null ? defaultValue : newValue;
            if (property != null) property.set(value.name());
        }
    }

    public static void setSurvivalProgressionEnabled(boolean enabled) {
        ENABLE_SURVIVAL_PROGRESSION.set(enabled);
        ENABLE_SURVIVAL_PROGRESSION.saveDomain();
    }

    public static int maxActionRadiusBlocks() { return MAX_ACTION_RADIUS_BLOCKS.getAsInt(); }

    public static void setMaxActionRadiusBlocks(int radiusBlocks) {
        MAX_ACTION_RADIUS_BLOCKS.set(radiusBlocks);
        MAX_ACTION_RADIUS_BLOCKS.saveDomain();
    }

    public static boolean areBlueprintsEnabled() { return ENABLE_BLUEPRINTS.getAsBoolean(); }
    public static int maxBlueprintBlocks() { return MAX_BLUEPRINT_BLOCKS.getAsInt(); }

    public static void saveGeneralSettings(boolean survivalEnabled, boolean shareWithTeams, int radiusBlocks,
            boolean blueprintsEnabled, int maxBlueprintBlocks) {
        ENABLE_SURVIVAL_PROGRESSION.set(survivalEnabled);
        SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS.set(shareWithTeams);
        MAX_ACTION_RADIUS_BLOCKS.set(radiusBlocks);
        ENABLE_BLUEPRINTS.set(blueprintsEnabled);
        MAX_BLUEPRINT_BLOCKS.set(maxBlueprintBlocks);
        saveIfChanged(commonConfiguration);
    }

    public static void saveAreaMineLimitSettings(int maxWidth, int maxHeight, int maxDepth, int maxVolume,
            int maxTargets, RangeMiningHarvestTier maxHarvestTier) {
        AREA_MINE_MAX_WIDTH.set(maxWidth);
        AREA_MINE_MAX_HEIGHT.set(maxHeight);
        AREA_MINE_MAX_DEPTH.set(maxDepth);
        AREA_MINE_MAX_VOLUME.set(maxVolume);
        AREA_DESTROY_MAX_TARGETS.set(maxTargets);
        AREA_MINE_MAX_HARVEST_TIER.set(maxHarvestTier);
        AREA_MINE_MAX_SIZE.set(Math.max(maxWidth, Math.max(maxHeight, maxDepth)));
        saveIfChanged(serverConfiguration);
    }

    public static boolean isPlacementBlockGhostPreviewEnabled() { return USE_BLOCK_GHOST_PREVIEW.getAsBoolean(); }
    public static boolean isUiAnimationsEnabled() { return ENABLE_UI_ANIMATIONS.getAsBoolean(); }
    public static void setUiAnimationsEnabled(boolean value) { setAndSave(ENABLE_UI_ANIMATIONS, value); }
    public static void setPlacementBlockGhostPreviewEnabled(boolean value) { setAndSave(USE_BLOCK_GHOST_PREVIEW, value); }
    public static boolean isPlaceBlockGhostAnimationEnabled() { return USE_PLACE_BLOCK_GHOST_ANIMATION.getAsBoolean(); }
    public static void setPlaceBlockGhostAnimationEnabled(boolean value) { setAndSave(USE_PLACE_BLOCK_GHOST_ANIMATION, value); }
    public static boolean isDestroyBlockGhostAnimationEnabled() { return USE_DESTROY_BLOCK_GHOST_ANIMATION.getAsBoolean(); }
    public static void setDestroyBlockGhostAnimationEnabled(boolean value) { setAndSave(USE_DESTROY_BLOCK_GHOST_ANIMATION, value); }
    public static boolean isPlacementWireframePreviewEnabled() { return USE_WIREFRAME_PREVIEW.getAsBoolean(); }
    public static void setPlacementWireframePreviewEnabled(boolean value) { setAndSave(USE_WIREFRAME_PREVIEW, value); }
    public static boolean isPlaceWireframeAnimationEnabled() { return USE_PLACE_WIREFRAME_ANIMATION.getAsBoolean(); }
    public static void setPlaceWireframeAnimationEnabled(boolean value) { setAndSave(USE_PLACE_WIREFRAME_ANIMATION, value); }
    public static boolean isDestroyWireframeAnimationEnabled() { return USE_DESTROY_WIREFRAME_ANIMATION.getAsBoolean(); }
    public static void setDestroyWireframeAnimationEnabled(boolean value) { setAndSave(USE_DESTROY_WIREFRAME_ANIMATION, value); }
    public static boolean isRangeDestroySkeletonEnabled() { return USE_RANGE_DESTROY_SKELETON.getAsBoolean(); }
    public static void setRangeDestroySkeletonEnabled(boolean value) { setAndSave(USE_RANGE_DESTROY_SKELETON, value); }
    public static boolean isInventoryRtsButtonEnabled() { return SHOW_INVENTORY_RTS_BUTTON.getAsBoolean(); }
    public static void setInventoryRtsButtonEnabled(boolean value) { setAndSave(SHOW_INVENTORY_RTS_BUTTON, value); }
    public static boolean isKeyboardBatchConfirmEnabled() { return REQUIRE_KEYBOARD_BATCH_CONFIRM.getAsBoolean(); }
    public static void setKeyboardBatchConfirmEnabled(boolean value) { setAndSave(REQUIRE_KEYBOARD_BATCH_CONFIRM, value); }
    public static boolean isDeveloperModeEnabled() { return DEVELOPER_MODE.getAsBoolean(); }
    public static void setDeveloperModeEnabled(boolean value) { setAndSave(DEVELOPER_MODE, value); }

    private static void setAndSave(BooleanValue option, boolean value) {
        option.set(value);
        option.saveDomain();
    }

    public static int ultimineMaxBlocks() { return ULTIMINE_MAX_BLOCKS.getAsInt(); }
    public static int areaMineMaxSize() { return AREA_MINE_MAX_SIZE.getAsInt(); }
    public static int areaMineMaxVolume() { return AREA_MINE_MAX_VOLUME.getAsInt(); }
    public static int areaMineMaxWidth() { return AREA_MINE_MAX_WIDTH.getAsInt(); }
    public static int areaMineMaxHeight() { return AREA_MINE_MAX_HEIGHT.getAsInt(); }
    public static int areaMineMaxDepth() { return AREA_MINE_MAX_DEPTH.getAsInt(); }
    public static RangeMiningHarvestTier areaMineMaxHarvestTier() { return AREA_MINE_MAX_HARVEST_TIER.get(); }
    public static int ae2NetworkRefreshThrottle() { return AE2_NETWORK_REFRESH_THROTTLE.getAsInt(); }
    public static int refinedStorageNetworkRefreshThrottle() { return REFINED_STORAGE_NETWORK_REFRESH_THROTTLE.getAsInt(); }
    public static boolean isCrossDimensionStorageEnabled() { return ENABLE_CROSS_DIMENSION_STORAGE.getAsBoolean(); }
    public static int maxCrossDimensionAwakeChunks() { return MAX_CROSS_DIMENSION_AWAKE_CHUNKS.getAsInt(); }
    public static int pageCacheMaxPlayers() { return PAGE_CACHE_MAX_PLAYERS.getAsInt(); }
    public static int defaultStoragePageSize() { return Math.min(DEFAULT_STORAGE_PAGE_SIZE.getAsInt(), maxStoragePageSize()); }
    public static int maxStoragePageSize() { return MAX_STORAGE_PAGE_SIZE.getAsInt(); }
    public static int areaDestroyMaxTargets() { return AREA_DESTROY_MAX_TARGETS.getAsInt(); }
    public static int ultimineBlocksPerTick() { return ULTIMINE_BLOCKS_PER_TICK.getAsInt(); }
    public static int buildBatchBlocksPerTick() { return BUILD_BATCH_BLOCKS_PER_TICK.getAsInt(); }
    public static int buildBatchMaxQueuedJobs() { return BUILD_BATCH_MAX_QUEUED_JOBS.getAsInt(); }
    public static int taskEngineMaxUnitsPerTick() { return TASK_ENGINE_MAX_UNITS_PER_TICK.getAsInt(); }
    public static int taskEngineMaxUnitsPerSlice() { return TASK_ENGINE_MAX_UNITS_PER_SLICE.getAsInt(); }
    public static long taskEngineMaxNanosPerTick() { return TASK_ENGINE_MAX_NANOS_PER_TICK.get(); }
    public static double remotePovBlockReach() { return REMOTE_POV_BLOCK_REACH.getAsDouble(); }
    public static double dropScanRadius() { return DROP_SCAN_RADIUS.getAsDouble(); }
    public static int remotePlaceSoundsPerTick() { return REMOTE_PLACE_SOUNDS_PER_TICK.getAsInt(); }
    public static long internalFluidCapacityMb() {
        return Math.max(1L, (long) INTERNAL_FLUID_CAPACITY_BUCKETS.getAsInt()) * Fluid.BUCKET_VOLUME;
    }

    public static boolean migrateLegacyServerDefaults() {
        ServerConfigMigration.Values migrated = ServerConfigMigration.migrate(SERVER_CONFIG_REVISION.getAsInt(),
                ULTIMINE_BLOCKS_PER_TICK.getAsInt(), TASK_ENGINE_MAX_NANOS_PER_TICK.get());
        if (migrated.revision() == SERVER_CONFIG_REVISION.getAsInt()) return false;
        ULTIMINE_BLOCKS_PER_TICK.set(migrated.miningSlice());
        TASK_ENGINE_MAX_NANOS_PER_TICK.set(migrated.taskBudgetNanos());
        SERVER_CONFIG_REVISION.set(migrated.revision());
        if (serverConfiguration != null) serverConfiguration.save();
        return true;
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
