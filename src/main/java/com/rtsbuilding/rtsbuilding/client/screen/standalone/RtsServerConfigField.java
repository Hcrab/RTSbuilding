package com.rtsbuilding.rtsbuilding.client.screen.standalone;

import com.rtsbuilding.rtsbuilding.common.config.RtsServerConfigChange;

/** 普通玩家设置页允许编辑的世界字段目录；技术预算和内部节流永不进入此目录。 */
enum RtsServerConfigField {
    SURVIVAL(RtsServerConfigGroup.RULES, RtsServerConfigChange.Key.ENABLE_SURVIVAL_PROGRESSION, "survival", InputKind.BOOLEAN),
    SHARE_TEAMS(RtsServerConfigGroup.RULES, RtsServerConfigChange.Key.SHARE_SURVIVAL_PROGRESSION_WITH_TEAMS, "share_teams", InputKind.BOOLEAN),
    ACTION_RADIUS(RtsServerConfigGroup.RULES, RtsServerConfigChange.Key.MAX_ACTION_RADIUS_BLOCKS, "action_radius", InputKind.INTEGER),

    HOME_RADIUS(RtsServerConfigGroup.HOME, RtsServerConfigChange.Key.HOME_SELECTION_RADIUS_BLOCKS, "home_radius", InputKind.INTEGER),
    HOME_COOLDOWN(RtsServerConfigGroup.HOME, RtsServerConfigChange.Key.HOME_RELOCATION_COOLDOWN_DAYS, "home_cooldown", InputKind.INTEGER),

    SHAPE_DIMENSION(RtsServerConfigGroup.BUILDING, RtsServerConfigChange.Key.MAX_SHAPE_DIMENSION, "shape_dimension", InputKind.INTEGER),
    SHAPE_RADIUS(RtsServerConfigGroup.BUILDING, RtsServerConfigChange.Key.MAX_SHAPE_RADIUS, "shape_radius", InputKind.INTEGER),
    SMART_MAX_BLOCKS(RtsServerConfigGroup.BUILDING, RtsServerConfigChange.Key.SMART_FILL_MAX_BLOCKS, "smart_max_blocks", InputKind.INTEGER),
    SMART_DEFAULT_BLOCKS(RtsServerConfigGroup.BUILDING, RtsServerConfigChange.Key.SMART_FILL_DEFAULT_BLOCKS, "smart_default_blocks", InputKind.INTEGER),
    SMART_MAX_DIAMETER(RtsServerConfigGroup.BUILDING, RtsServerConfigChange.Key.SMART_FILL_MAX_DIAMETER, "smart_max_diameter", InputKind.INTEGER),
    SMART_DEFAULT_DIAMETER(RtsServerConfigGroup.BUILDING, RtsServerConfigChange.Key.SMART_FILL_DEFAULT_DIAMETER, "smart_default_diameter", InputKind.INTEGER),

    SELECTION_VOLUME(RtsServerConfigGroup.MINING, RtsServerConfigChange.Key.MAX_SELECTION_VOLUME, "selection_volume", InputKind.INTEGER),
    SELECTION_X(RtsServerConfigGroup.MINING, RtsServerConfigChange.Key.MAX_SELECTION_SIZE_X, "selection_x", InputKind.INTEGER),
    SELECTION_Y(RtsServerConfigGroup.MINING, RtsServerConfigChange.Key.MAX_SELECTION_SIZE_Y, "selection_y", InputKind.INTEGER),
    SELECTION_Z(RtsServerConfigGroup.MINING, RtsServerConfigChange.Key.MAX_SELECTION_SIZE_Z, "selection_z", InputKind.INTEGER),
    ULTIMINE_MAX(RtsServerConfigGroup.MINING, RtsServerConfigChange.Key.ULTIMINE_MAX_BLOCKS, "ultimine_max", InputKind.INTEGER),
    HARVEST_TIER(RtsServerConfigGroup.MINING, RtsServerConfigChange.Key.AREA_MINE_MAX_HARVEST_TIER, "harvest_tier", InputKind.HARVEST_TIER),
    TREE_MAX(RtsServerConfigGroup.MINING, RtsServerConfigChange.Key.MAX_TREE_BLOCKS, "tree_max", InputKind.INTEGER),

    BLUEPRINTS(RtsServerConfigGroup.BLUEPRINT, RtsServerConfigChange.Key.ENABLE_BLUEPRINTS, "blueprints", InputKind.BOOLEAN),
    BLUEPRINT_MAX(RtsServerConfigGroup.BLUEPRINT, RtsServerConfigChange.Key.MAX_BLUEPRINT_BLOCKS, "blueprint_max", InputKind.INTEGER),

    BATCH_VOLUME(RtsServerConfigGroup.STORAGE, RtsServerConfigChange.Key.MAX_BATCH_BINDING_SELECTION_VOLUME, "batch_volume", InputKind.INTEGER),
    BATCH_X(RtsServerConfigGroup.STORAGE, RtsServerConfigChange.Key.MAX_BATCH_BINDING_SIZE_X, "batch_x", InputKind.INTEGER),
    BATCH_Y(RtsServerConfigGroup.STORAGE, RtsServerConfigChange.Key.MAX_BATCH_BINDING_SIZE_Y, "batch_y", InputKind.INTEGER),
    BATCH_Z(RtsServerConfigGroup.STORAGE, RtsServerConfigChange.Key.MAX_BATCH_BINDING_SIZE_Z, "batch_z", InputKind.INTEGER),
    LINKED_STORAGE_MAX(RtsServerConfigGroup.STORAGE, RtsServerConfigChange.Key.MAX_LINKED_STORAGES, "linked_storage_max", InputKind.INTEGER),
    FUNNEL_RADIUS(RtsServerConfigGroup.STORAGE, RtsServerConfigChange.Key.FUNNEL_PICKUP_RADIUS_BLOCKS, "funnel_radius", InputKind.DECIMAL),

    WORKFLOW_MAX(RtsServerConfigGroup.WORKFLOWS, RtsServerConfigChange.Key.WORKFLOWS_MAX_ACTIVE_PER_PLAYER, "workflow_max", InputKind.INTEGER),
    HISTORY_ENTRIES(RtsServerConfigGroup.HISTORY, RtsServerConfigChange.Key.HISTORY_MAX_ENTRIES_PER_STACK, "history_entries", InputKind.INTEGER),
    HISTORY_RETENTION(RtsServerConfigGroup.HISTORY, RtsServerConfigChange.Key.HISTORY_RETENTION_SECONDS, "history_retention", InputKind.INTEGER);

    enum InputKind { BOOLEAN, INTEGER, DECIMAL, HARVEST_TIER }

    final RtsServerConfigGroup group;
    final RtsServerConfigChange.Key key;
    final String translationId;
    final InputKind inputKind;

    RtsServerConfigField(RtsServerConfigGroup group, RtsServerConfigChange.Key key,
                         String translationId, InputKind inputKind) {
        this.group = group;
        this.key = key;
        this.translationId = translationId;
        this.inputKind = inputKind;
    }

    String labelKey() {
        return "screen.rtsbuilding.world.field." + translationId;
    }

    String hintKey() {
        return labelKey() + ".hint";
    }
}
