package com.rtsbuilding.rtsbuilding.client.screen.state;

import com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsPanelApi;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 屏幕 UI 状态管理器——统筹面板持久化属性的加载与保存。
 *
 * <p>职责：</p>
 * <ul>
 *   <li><b>加载</b> — 屏幕初始化时，从 {@link RtsClientUiStateStore} 读取已持久化的
 *       {@link RtsClientUiStateStore.UiState}，遍历所有面板的
 *       {@link RtsPanelApi#persistableProperties()} 并逐个调用
 *       {@link PersistableProperty#applyToRuntime} 恢复到运行时组件。</li>
 *   <li><b>保存</b> — 面板边界变更或关闭时，遍历所有面板的持久化属性，
 *       调用 {@link PersistableProperty#collectFromRuntime} 收集当前值，
 *       写入 Store 并标记缓存为脏。</li>
 * </ul>
 *
 * <p>注意：全局状态（主题、相机灵敏度等）的持久化由 {@code BuilderScreen} 直接管理，
 * 不在此类职责范围内。</p>
 */
public final class RtsScreenUiStateManager {
    private static final Logger LOG = LoggerFactory.getLogger("RtsScreenUiState");

    /** 受管理的所有面板实例（扁平列表） */
    private final List<? extends RtsPanelApi> panels;

    public RtsScreenUiStateManager(List<? extends RtsPanelApi> panels) {
        this.panels = List.copyOf(panels);
    }

    /**
     * 从持久化存储加载所有面板的状态。
     */
    public void load() {
        RtsClientUiStateStore.UiState state = RtsClientUiStateStore.load();
        for (RtsPanelApi panel : panels) {
            for (PersistableProperty prop : panel.persistableProperties()) {
                try {
                    prop.applyToRuntime(state);
                } catch (Exception e) {
                    LOG.warn("应用面板 {} 的持久化属性 {} 时出错",
                            panel.getClass().getSimpleName(), prop.jsonKey(), e);
                }
            }
        }
        LOG.info("已从持久化存储加载 {} 个面板的状态", panels.size());
    }

    /**
     * 将所有面板的当前状态保存到持久化存储。
     */
    public void save() {
        RtsClientUiStateStore.UiState state = RtsClientUiStateStore.load();
        for (RtsPanelApi panel : panels) {
            for (PersistableProperty prop : panel.persistableProperties()) {
                try {
                    prop.collectFromRuntime(state);
                } catch (Exception e) {
                    LOG.warn("收集面板 {} 的持久化属性 {} 时出错",
                            panel.getClass().getSimpleName(), prop.jsonKey(), e);
                }
            }
        }
        RtsClientUiStateStore.cache().markDirty();
        RtsClientUiStateStore.cache().flushIfDirty();
    }
}
