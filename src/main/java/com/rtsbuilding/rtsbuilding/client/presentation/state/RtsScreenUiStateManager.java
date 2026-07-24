package com.rtsbuilding.rtsbuilding.client.presentation.state;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.api.RtsPanelApi;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import com.rtsbuilding.rtsbuilding.common.persist.RtsClientUiStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;


public final class RtsScreenUiStateManager {
    private static final Logger LOG = LoggerFactory.getLogger("RtsScreenUiState");

    
    private final List<? extends RtsPanelApi> panels;

    public RtsScreenUiStateManager(List<? extends RtsPanelApi> panels) {
        this.panels = List.copyOf(panels);
    }

    
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
