package com.rtsbuilding.rtsbuilding.common.config;

/** 一项白名单化、带明确类型的服务端配置修改。 */
public record RtsServerConfigChange(Key key, Value value) {
    public RtsServerConfigChange {
        if (key == null || value == null || key.valueType() != value.type()) throw new IllegalArgumentException("configuration change type does not match key");
    }
    public enum Key {
        ENABLE_SURVIVAL_PROGRESSION(Type.BOOLEAN), SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS(Type.BOOLEAN),
        MAX_ACTION_RADIUS_BLOCKS(Type.INT), ENABLE_BLUEPRINTS(Type.BOOLEAN), MAX_BLUEPRINT_BLOCKS(Type.INT),
        MAX_SELECTION_VOLUME(Type.INT), MAX_SELECTION_SIZE_X(Type.INT), MAX_SELECTION_SIZE_Y(Type.INT), MAX_SELECTION_SIZE_Z(Type.INT),
        ULTIMINE_MAX_BLOCKS(Type.INT), ULTIMINE_BLOCKS_PER_TICK(Type.INT), AREA_MINE_MAX_HARVEST_TIER(Type.STRING), MAX_TREE_BLOCKS(Type.INT), HOME_SELECTION_RADIUS_BLOCKS(Type.INT), HOME_RELOCATION_COOLDOWN_DAYS(Type.INT),
        MAX_SHAPE_DIMENSION(Type.INT), MAX_SHAPE_RADIUS(Type.INT), SMART_FILL_MAX_BLOCKS(Type.INT), SMART_FILL_DEFAULT_BLOCKS(Type.INT),
        SMART_FILL_MAX_DIAMETER(Type.INT), SMART_FILL_DEFAULT_DIAMETER(Type.INT), MAX_BATCH_BINDING_SELECTION_VOLUME(Type.INT),
        MAX_BATCH_BINDING_SIZE_X(Type.INT), MAX_BATCH_BINDING_SIZE_Y(Type.INT), MAX_BATCH_BINDING_SIZE_Z(Type.INT), MAX_LINKED_STORAGES(Type.INT),
        FUNNEL_PICKUP_RADIUS_BLOCKS(Type.DOUBLE), WORKFLOWS_MAX_ACTIVE_PER_PLAYER(Type.INT), HISTORY_MAX_ENTRIES_PER_STACK(Type.INT),
        HISTORY_RETENTION_SECONDS(Type.INT), FUNNEL_MAX_ENTITIES_PER_TICK(Type.INT), FUNNEL_MAX_ITEMS_PER_TICK(Type.INT),
        FUNNEL_BUFFER_MAX_STACKS(Type.INT), FUNNEL_TICK_INTERVAL(Type.INT), STORAGE_DROP_CACHE_SOFT_CAPACITY(Type.INT),
        BUILD_BATCH_BLOCKS_PER_TICK(Type.INT), BUILD_BATCH_MAX_QUEUED_JOBS(Type.INT), TASK_ENGINE_MAX_UNITS_PER_TICK(Type.INT),
        TASK_ENGINE_MAX_UNITS_PER_SLICE(Type.INT), TASK_ENGINE_MAX_NANOS_PER_TICK(Type.LONG), DEFAULT_STORAGE_PAGE_SIZE(Type.INT),
        MAX_STORAGE_PAGE_SIZE(Type.INT), PAGE_CACHE_MAX_PLAYERS(Type.INT), AE2_NETWORK_REFRESH_THROTTLE(Type.INT),
        REFINED_STORAGE_NETWORK_REFRESH_THROTTLE(Type.INT), DIAGNOSTICS_MAX_TRACES(Type.INT), DIAGNOSTICS_MAX_WORKFLOW_LINKS(Type.INT),
        DIAGNOSTICS_MAX_TASK_LINKS(Type.INT);
        private final Type valueType;
        Key(Type valueType) { this.valueType = valueType; }
        public Type valueType() { return valueType; }
    }
    public enum Type { BOOLEAN, INT, LONG, DOUBLE, STRING }
    public record BooleanValue(boolean value) implements Value { public Type type() { return Type.BOOLEAN; } }
    public record IntValue(int value) implements Value { public Type type() { return Type.INT; } }
    public record LongValue(long value) implements Value { public Type type() { return Type.LONG; } }
    public record DoubleValue(double value) implements Value { public Type type() { return Type.DOUBLE; } }
    public record StringValue(String value) implements Value { public StringValue { if (value == null) throw new IllegalArgumentException("string configuration value cannot be null"); } public Type type() { return Type.STRING; } }
    public sealed interface Value permits BooleanValue, IntValue, LongValue, DoubleValue, StringValue { Type type(); }
}
