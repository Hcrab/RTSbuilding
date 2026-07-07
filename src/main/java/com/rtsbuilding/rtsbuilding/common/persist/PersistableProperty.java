package com.rtsbuilding.rtsbuilding.common.persist;



import com.rtsbuilding.rtsbuilding.client.screen.panel.base.window.RtsPanel;

import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 可持久化属性——由面板自声明，Manager 自动收集/应用。
 * <p>
 * 每个属性封装了一个 {@link RtsClientUiStateStore.UiState} 字段与运行时组件之间的
 * 双向映射。Panel 子类重写 {@link RtsPanel#persistableProperties()}
 * 返回自己的属性列表，Manager 遍历所有面板的属性完成收集与分发，不再硬编码字段。
 */
public sealed interface PersistableProperty {

    /** 属性在 JSON 中的存储键 */
    String jsonKey();

    /** 从 UiState 读取持久化值，应用到运行时组件 */
    void applyToRuntime(RtsClientUiStateStore.UiState state);

    /** 从运行时组件收集当前值，写入 UiState */
    void collectFromRuntime(RtsClientUiStateStore.UiState state);

    // ========================================================================
    //  FieldProperty —— 通用字段映射
    // ========================================================================

    /**
     * 通用字段映射属性——绑定 UiState 上的一个 public 字段，
     * 通过 getter/setter 与运行时组件双向同步。
     */
    record FieldProperty<T>(
            String jsonKey,
            Function<RtsClientUiStateStore.UiState, T> stateReader,
            BiConsumer<RtsClientUiStateStore.UiState, T> stateWriter,
            Supplier<T> runtimeGetter,
            java.util.function.Consumer<T> runtimeSetter
    ) implements PersistableProperty {
        @Override
        public void applyToRuntime(RtsClientUiStateStore.UiState state) {
            T value = stateReader.apply(state);
            runtimeSetter.accept(value);
        }

        @Override
        public void collectFromRuntime(RtsClientUiStateStore.UiState state) {
            T value = runtimeGetter.get();
            stateWriter.accept(state, value);
        }
    }

    // ========================================================================
    //  EnumFieldProperty —— 带安全解析的枚举字段
    // ========================================================================

    /**
     * 枚举字段属性——自动处理字符串到枚举的安全解析和 fallback。
     * <p>UiState 中枚举以字符串形式存储，此属性在 apply 时做安全解析，
     * 非法值使用 fallback 兜底。
     */
    record EnumFieldProperty<E extends Enum<E>>(
            String jsonKey,
            Function<RtsClientUiStateStore.UiState, String> stateReader,
            BiConsumer<RtsClientUiStateStore.UiState, String> stateWriter,
            Supplier<E> runtimeGetter,
            java.util.function.Consumer<E> runtimeSetter,
            E fallback,
            Class<E> enumClass
    ) implements PersistableProperty {
        @Override
        public void applyToRuntime(RtsClientUiStateStore.UiState state) {
            String raw = stateReader.apply(state);
            runtimeSetter.accept(parseSafe(raw));
        }

        @Override
        public void collectFromRuntime(RtsClientUiStateStore.UiState state) {
            E value = runtimeGetter.get();
            stateWriter.accept(state, value.name());
        }

        private E parseSafe(String raw) {
            if (raw == null || raw.isBlank()) return fallback;
            try {
                return Enum.valueOf(enumClass, raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return fallback;
            }
        }
    }

    // ========================================================================
    //  BoundsProperty —— 窗口面板边界
    // ========================================================================

    /**
     * 窗口边界属性——将面板的 x/y/width/height 映射到
     * {@link RtsClientUiStateStore.UiState#windowPanelBounds}。
     */
    record BoundsProperty(
            String panelKey,
            BoundsProvider panel
    ) implements PersistableProperty {
        @Override
        public String jsonKey() {
            return panelKey + ".bounds";
        }

        @Override
        public void applyToRuntime(RtsClientUiStateStore.UiState state) {
            var bounds = state.windowPanelBounds.get(panelKey);
            if (bounds != null) {
                panel.setBounds(bounds.x, bounds.y, bounds.width, bounds.height);
            }
        }

        @Override
        public void collectFromRuntime(RtsClientUiStateStore.UiState state) {
            if (panel.hasUserBoundsPreference()) {
                state.windowPanelBounds.put(panelKey,
                        new RtsClientUiStateStore.UiState.PanelBounds(
                                panel.getWindowX(), panel.getWindowY(),
                                panel.getWindowWidth(), panel.getWindowHeight()));
            }
        }
    }

    // ========================================================================
    //  便捷工厂方法
    // ========================================================================

    /** 创建布尔字段属性 */
    static FieldProperty<Boolean> boolField(
            String jsonKey,
            Function<RtsClientUiStateStore.UiState, Boolean> stateReader,
            BiConsumer<RtsClientUiStateStore.UiState, Boolean> stateWriter,
            Supplier<Boolean> runtimeGetter,
            java.util.function.Consumer<Boolean> runtimeSetter
    ) {
        return new FieldProperty<>(jsonKey, stateReader, stateWriter, runtimeGetter, runtimeSetter);
    }

    /** 创建整数字段属性 */
    static FieldProperty<Integer> intField(
            String jsonKey,
            Function<RtsClientUiStateStore.UiState, Integer> stateReader,
            BiConsumer<RtsClientUiStateStore.UiState, Integer> stateWriter,
            Supplier<Integer> runtimeGetter,
            java.util.function.Consumer<Integer> runtimeSetter
    ) {
        return new FieldProperty<>(jsonKey, stateReader, stateWriter, runtimeGetter, runtimeSetter);
    }

    /** 创建字符串字段属性 */
    static FieldProperty<String> stringField(
            String jsonKey,
            Function<RtsClientUiStateStore.UiState, String> stateReader,
            BiConsumer<RtsClientUiStateStore.UiState, String> stateWriter,
            Supplier<String> runtimeGetter,
            java.util.function.Consumer<String> runtimeSetter
    ) {
        return new FieldProperty<>(jsonKey, stateReader, stateWriter, runtimeGetter, runtimeSetter);
    }

    /** 创建双精度浮点数字段属性 */
    static FieldProperty<Double> doubleField(
            String jsonKey,
            Function<RtsClientUiStateStore.UiState, Double> stateReader,
            BiConsumer<RtsClientUiStateStore.UiState, Double> stateWriter,
            Supplier<Double> runtimeGetter,
            java.util.function.Consumer<Double> runtimeSetter
    ) {
        return new FieldProperty<>(jsonKey, stateReader, stateWriter, runtimeGetter, runtimeSetter);
    }

    /**
     * 创建枚举字段属性。
     *
     * @param jsonKey       JSON 键
     * @param stateReader   UiState 读取器
     * @param stateWriter   UiState 写入器
     * @param runtimeGetter 运行时枚举获取器
     * @param runtimeSetter 运行时枚举设置器
     * @param fallback      解析失败时的默认枚举值
     * @param enumClass     枚举类
     */
    static <E extends Enum<E>> EnumFieldProperty<E> enumField(
            String jsonKey,
            Function<RtsClientUiStateStore.UiState, String> stateReader,
            BiConsumer<RtsClientUiStateStore.UiState, String> stateWriter,
            Supplier<E> runtimeGetter,
            java.util.function.Consumer<E> runtimeSetter,
            E fallback,
            Class<E> enumClass
    ) {
        return new EnumFieldProperty<>(jsonKey, stateReader, stateWriter,
                runtimeGetter, runtimeSetter, fallback, enumClass);
    }

    /** 创建窗口边界属性 */
    static BoundsProperty bounds(String panelKey, BoundsProvider panel) {
        return new BoundsProperty(panelKey, panel);
    }
}
