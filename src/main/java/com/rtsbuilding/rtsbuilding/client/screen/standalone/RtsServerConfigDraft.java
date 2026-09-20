package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigChange;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigUpdateRequest;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigValidator;
import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigView;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 设置窗口的服务端草稿。
 *
 * <p>baseline 是最近一次成功确认的视图，values 是玩家当前正在编辑的文本。这个类
 * 不读文件、不发包，也不把广播视为保存；因此切组、滚动、关闭和失败都可以只保留
 * 草稿，而把真正的权限、revision 与落盘交给服务端 typed API。</p>
 */
public final class RtsServerConfigDraft {
    private RtsServerConfigView baseline;
    private final EnumMap<RtsServerConfigChange.Key, String> values =
            new EnumMap<>(RtsServerConfigChange.Key.class);

    private RtsServerConfigDraft(RtsServerConfigView view) {
        accept(view);
    }

    public static RtsServerConfigDraft from(RtsServerConfigView view) {
        return new RtsServerConfigDraft(view == null ? RtsServerConfigView.defaults().readOnly() : view);
    }

    /** 仅在收到本次保存的成功 ACK 后调用，失败/冲突不得调用。 */
    public void accept(RtsServerConfigView view) {
        this.baseline = view == null ? RtsServerConfigView.defaults().readOnly() : view;
        this.values.clear();
        for (RtsServerConfigChange.Key key : RtsServerConfigChange.Key.values()) {
            this.values.put(key, valueText(this.baseline, key));
        }
    }

    public RtsServerConfigView baseline() {
        return baseline;
    }

    public int baselineRevision() {
        return baseline.revision();
    }

    public String text(RtsServerConfigChange.Key key) {
        return values.get(key);
    }

    public void setText(RtsServerConfigChange.Key key, String value) {
        if (key == null) throw new IllegalArgumentException("configuration key is required");
        values.put(key, value == null ? "" : value);
    }

    public boolean isDirty() {
        for (RtsServerConfigChange.Key key : RtsServerConfigChange.Key.values()) {
            if (!sameTypedValue(key, values.get(key), valueText(baseline, key))) return true;
        }
        return false;
    }

    /**
     * 将文本转换为 typed 变更；非法输入直接抛出给 UI 显示本地化错误，绝不自动限幅
     * 或把一个无效输入改写成旧值。
     */
    public List<RtsServerConfigChange> changes() {
        java.util.ArrayList<RtsServerConfigChange> changes = new java.util.ArrayList<>();
        for (RtsServerConfigChange.Key key : RtsServerConfigChange.Key.values()) {
            String raw = values.get(key);
            String old = valueText(baseline, key);
            if (sameTypedValue(key, raw, old)) continue;
            changes.add(new RtsServerConfigChange(key, parse(key, raw)));
        }
        return List.copyOf(changes);
    }

    /** 返回跨字段约束错误；null 表示可提交。 */
    public String validationError() {
        List<RtsServerConfigChange> changes = changes();
        if (changes.isEmpty()) return null;
        return RtsServerConfigValidator.validate(baseline,
                new RtsServerConfigUpdateRequest(baseline.revision(), changes), baseline.editable());
    }

    private static boolean sameTypedValue(RtsServerConfigChange.Key key, String left, String right) {
        if (Objects.equals(left, right)) return true;
        try {
            return Objects.equals(parse(key, left), parse(key, right));
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static RtsServerConfigChange.Value parse(RtsServerConfigChange.Key key, String raw) {
        if (raw == null) throw new IllegalArgumentException("empty value for " + key);
        String value = raw.trim();
        try {
            return switch (key.valueType()) {
                case BOOLEAN -> {
                    if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                        throw new IllegalArgumentException("invalid boolean for " + key);
                    }
                    yield new RtsServerConfigChange.BooleanValue(Boolean.parseBoolean(value));
                }
                case INT -> new RtsServerConfigChange.IntValue(Integer.parseInt(value));
                case LONG -> new RtsServerConfigChange.LongValue(Long.parseLong(value));
                case DOUBLE -> {
                    double parsed = Double.parseDouble(value);
                    if (!Double.isFinite(parsed)) throw new IllegalArgumentException("invalid number for " + key);
                    yield new RtsServerConfigChange.DoubleValue(parsed);
                }
                case STRING -> new RtsServerConfigChange.StringValue(value.toUpperCase(Locale.ROOT));
            };
        } catch (NumberFormatException invalidNumber) {
            throw new IllegalArgumentException("invalid value for " + key, invalidNumber);
        }
    }

    private static String valueText(RtsServerConfigView view, RtsServerConfigChange.Key key) {
        return switch (key) {
            case ENABLE_SURVIVAL_PROGRESSION -> Boolean.toString(view.enableSurvivalProgression());
            case SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS -> Boolean.toString(view.shareSurvivalProgressionWithTeams());
            case MAX_ACTION_RADIUS_BLOCKS -> Integer.toString(view.maxActionRadiusBlocks());
            case ENABLE_BLUEPRINTS -> Boolean.toString(view.enableBlueprints());
            case MAX_BLUEPRINT_BLOCKS -> Integer.toString(view.maxBlueprintBlocks());
            case MAX_SELECTION_VOLUME -> Integer.toString(view.maxSelectionVolume());
            case MAX_SELECTION_SIZE_X -> Integer.toString(view.maxSelectionSizeX());
            case MAX_SELECTION_SIZE_Y -> Integer.toString(view.maxSelectionSizeY());
            case MAX_SELECTION_SIZE_Z -> Integer.toString(view.maxSelectionSizeZ());
            case ULTIMINE_MAX_BLOCKS -> Integer.toString(view.ultimineMaxBlocks());
            case ULTIMINE_BLOCKS_PER_TICK -> Integer.toString(view.ultimineBlocksPerTick());
            case AREA_MINE_MAX_HARVEST_TIER -> view.areaMineMaxHarvestTier();
            case MAX_TREE_BLOCKS -> Integer.toString(view.maxTreeBlocks());
            case HOME_SELECTION_RADIUS_BLOCKS -> Integer.toString(view.homeSelectionRadiusBlocks());
            case HOME_RELOCATION_COOLDOWN_DAYS -> Integer.toString(view.homeRelocationCooldownDays());
            case MAX_SHAPE_DIMENSION -> Integer.toString(view.maxShapeDimension());
            case MAX_SHAPE_RADIUS -> Integer.toString(view.maxShapeRadius());
            case SMART_FILL_MAX_BLOCKS -> Integer.toString(view.smartFillMaxBlocks());
            case SMART_FILL_DEFAULT_BLOCKS -> Integer.toString(view.smartFillDefaultBlocks());
            case SMART_FILL_MAX_DIAMETER -> Integer.toString(view.smartFillMaxDiameter());
            case SMART_FILL_DEFAULT_DIAMETER -> Integer.toString(view.smartFillDefaultDiameter());
            case MAX_BATCH_BINDING_SELECTION_VOLUME -> Integer.toString(view.maxBatchBindingSelectionVolume());
            case MAX_BATCH_BINDING_SIZE_X -> Integer.toString(view.maxBatchBindingSizeX());
            case MAX_BATCH_BINDING_SIZE_Y -> Integer.toString(view.maxBatchBindingSizeY());
            case MAX_BATCH_BINDING_SIZE_Z -> Integer.toString(view.maxBatchBindingSizeZ());
            case MAX_LINKED_STORAGES -> Integer.toString(view.maxLinkedStorages());
            case FUNNEL_PICKUP_RADIUS_BLOCKS -> Double.toString(view.funnelPickupRadiusBlocks());
            case WORKFLOWS_MAX_ACTIVE_PER_PLAYER -> Integer.toString(view.workflowsMaxActivePerPlayer());
            case HISTORY_MAX_ENTRIES_PER_STACK -> Integer.toString(view.historyMaxEntriesPerStack());
            case HISTORY_RETENTION_SECONDS -> Integer.toString(view.historyRetentionSeconds());
            case FUNNEL_MAX_ENTITIES_PER_TICK -> Integer.toString(view.funnelMaxEntitiesPerTick());
            case FUNNEL_MAX_ITEMS_PER_TICK -> Integer.toString(view.funnelMaxItemsPerTick());
            case FUNNEL_BUFFER_MAX_STACKS -> Integer.toString(view.funnelBufferMaxStacks());
            case FUNNEL_TICK_INTERVAL -> Integer.toString(view.funnelTickInterval());
            case STORAGE_DROP_CACHE_SOFT_CAPACITY -> Integer.toString(view.storageDropCacheSoftCapacity());
            case BUILD_BATCH_BLOCKS_PER_TICK -> Integer.toString(view.buildBatchBlocksPerTick());
            case BUILD_BATCH_MAX_QUEUED_JOBS -> Integer.toString(view.buildBatchMaxQueuedJobs());
            case TASK_ENGINE_MAX_UNITS_PER_TICK -> Integer.toString(view.taskEngineMaxUnitsPerTick());
            case TASK_ENGINE_MAX_UNITS_PER_SLICE -> Integer.toString(view.taskEngineMaxUnitsPerSlice());
            case TASK_ENGINE_MAX_NANOS_PER_TICK -> Long.toString(view.taskEngineMaxNanosPerTick());
            case DEFAULT_STORAGE_PAGE_SIZE -> Integer.toString(view.defaultStoragePageSize());
            case MAX_STORAGE_PAGE_SIZE -> Integer.toString(view.maxStoragePageSize());
            case PAGE_CACHE_MAX_PLAYERS -> Integer.toString(view.pageCacheMaxPlayers());
            case AE2_NETWORK_REFRESH_THROTTLE -> Integer.toString(view.ae2NetworkRefreshThrottle());
            case REFINED_STORAGE_NETWORK_REFRESH_THROTTLE -> Integer.toString(view.refinedStorageNetworkRefreshThrottle());
            case DIAGNOSTICS_MAX_TRACES -> Integer.toString(view.diagnosticsMaxTraces());
            case DIAGNOSTICS_MAX_WORKFLOW_LINKS -> Integer.toString(view.diagnosticsMaxWorkflowLinks());
            case DIAGNOSTICS_MAX_TASK_LINKS -> Integer.toString(view.diagnosticsMaxTaskLinks());
        };
    }
}
