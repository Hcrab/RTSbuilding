package com.rtsbuilding.rtsbuilding.client.screen.quickbuild;

import com.rtsbuilding.rtsbuilding.client.screen.ultimine.AreaMineShape;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;

import java.util.List;

/**
 * 声明 Quick Build 与客户端 UI 状态文件之间的字段绑定。
 *
 * <p>本类只描述序列化映射，不负责保存时机、插件权限或控制器副作用。载入时仍写入
 * 原始偏好状态，面板在既有生命周期节点统一应用到生产控制器。</p>
 */
final class QuickBuildPersistenceBindings {
    static List<PersistableProperty> create(
            QuickBuildPanel panel,
            QuickBuildPreferenceState preferences) {
        return List.of(
                PersistableProperty.boolField(
                        "quick_build_open",
                        state -> state.quickBuild.quickBuildOpen,
                        (state, value) -> state.quickBuild.quickBuildOpen = value,
                        panel::isOpen,
                        panel::setOpen),
                PersistableProperty.enumField(
                        "quick_build_mode",
                        state -> state.quickBuild.quickBuildMode,
                        (state, value) -> state.quickBuild.quickBuildMode = value,
                        preferences::mode,
                        preferences::mode,
                        QuickBuildMode.BUILD,
                        QuickBuildMode.class),
                PersistableProperty.intField(
                        "chain_destroy_limit",
                        state -> state.quickBuild.mining.ultimineLimit,
                        (state, value) -> state.quickBuild.mining.ultimineLimit = value,
                        preferences::chainLimit,
                        preferences::chainLimit),
                PersistableProperty.enumField(
                        "area_mine_shape",
                        state -> state.quickBuild.mining.areaMineShape,
                        (state, value) -> state.quickBuild.mining.areaMineShape = value,
                        preferences::destroyShape,
                        preferences::destroyShape,
                        AreaMineShape.CHAIN,
                        AreaMineShape.class),
                PersistableProperty.boolField(
                        "advanced_range_destroy_square",
                        state -> state.quickBuild.mining.advancedRangeDestroySquare,
                        (state, value) -> state.quickBuild.mining.advancedRangeDestroySquare = value,
                        () -> preferences.advanced(BuildShape.SQUARE),
                        value -> preferences.advanced(BuildShape.SQUARE, value)),
                PersistableProperty.boolField(
                        "advanced_range_destroy_wall",
                        state -> state.quickBuild.mining.advancedRangeDestroyWall,
                        (state, value) -> state.quickBuild.mining.advancedRangeDestroyWall = value,
                        () -> preferences.advanced(BuildShape.WALL),
                        value -> preferences.advanced(BuildShape.WALL, value)),
                PersistableProperty.boolField(
                        "advanced_range_destroy_circle",
                        state -> state.quickBuild.mining.advancedRangeDestroyCircle,
                        (state, value) -> state.quickBuild.mining.advancedRangeDestroyCircle = value,
                        () -> preferences.advanced(BuildShape.CIRCLE),
                        value -> preferences.advanced(BuildShape.CIRCLE, value)),
                PersistableProperty.boolField(
                        "advanced_range_destroy_cylinder",
                        state -> state.quickBuild.mining.advancedRangeDestroyCylinder,
                        (state, value) -> state.quickBuild.mining.advancedRangeDestroyCylinder = value,
                        () -> preferences.advanced(BuildShape.CYLINDER),
                        value -> preferences.advanced(BuildShape.CYLINDER, value)),
                PersistableProperty.boolField(
                        "line_vertical",
                        state -> state.quickBuild.mining.lineVertical,
                        (state, value) -> state.quickBuild.mining.lineVertical = value,
                        () -> preferences.vertical(BuildShape.LINE),
                        value -> preferences.vertical(BuildShape.LINE, value)),
                PersistableProperty.boolField(
                        "round_shape_circle_vertical",
                        state -> state.quickBuild.mining.circleVertical,
                        (state, value) -> state.quickBuild.mining.circleVertical = value,
                        () -> preferences.vertical(BuildShape.CIRCLE),
                        value -> preferences.vertical(BuildShape.CIRCLE, value)),
                PersistableProperty.boolField(
                        "round_shape_cylinder_vertical",
                        state -> state.quickBuild.mining.cylinderVertical,
                        (state, value) -> state.quickBuild.mining.cylinderVertical = value,
                        () -> preferences.vertical(BuildShape.CYLINDER),
                        value -> preferences.vertical(BuildShape.CYLINDER, value)),
                PersistableProperty.boolField(
                        "advanced_range_destroy_ball",
                        state -> state.quickBuild.mining.advancedRangeDestroyBall,
                        (state, value) -> state.quickBuild.mining.advancedRangeDestroyBall = value,
                        () -> preferences.advanced(BuildShape.BALL),
                        value -> preferences.advanced(BuildShape.BALL, value)),
                PersistableProperty.boolField(
                        "advanced_range_destroy_box",
                        state -> state.quickBuild.mining.advancedRangeDestroyBox,
                        (state, value) -> state.quickBuild.mining.advancedRangeDestroyBox = value,
                        () -> preferences.advanced(BuildShape.BOX),
                        value -> preferences.advanced(BuildShape.BOX, value)),
                PersistableProperty.bounds("quick_build", panel)
        );
    }

    private QuickBuildPersistenceBindings() {
    }
}
