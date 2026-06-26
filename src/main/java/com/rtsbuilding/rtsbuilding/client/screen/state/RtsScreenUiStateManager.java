package com.rtsbuilding.rtsbuilding.client.screen.state;

import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsPanelApi;
import com.rtsbuilding.rtsbuilding.client.util.ThemeManager;
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
 * <p>与 {@link RtsClientUiStateStore} 的关系：</p>
 * <ul>
 *   <li>Store 层只做 I/O 和编解码，不含业务逻辑。</li>
 *   <li>Manager 层负责业务编排：知道哪些面板、哪些属性需要持久化。</li>
 * </ul>
 *
 * <p>与 {@link com.rtsbuilding.rtsbuilding.client.screen.panel.base.RtsFloatingWindowLayer} 的关系：</p>
 * <ul>
 *   <li>Manager 直接持有所有面板的扁平列表（包括浮动窗口面板和 TopBarPanel），
 *       不依赖浮动层的 Z 顺序逻辑。</li>
 * </ul>
 */
public final class RtsScreenUiStateManager {
    private static final Logger LOG = LoggerFactory.getLogger("RtsScreenUiState");

    /** 受管理的所有面板实例（扁平列表） */
    private final List<? extends RtsPanelApi> panels;

    /**
     * @param panels 所有需要持久化的面板实例（包括浮动窗口面板和顶部栏面板）
     */
    public RtsScreenUiStateManager(List<? extends RtsPanelApi> panels) {
        this.panels = List.copyOf(panels);
    }

    /**
     * 从持久化存储加载所有面板的状态。
     * <p>在 {@code BuilderScreen.init()} 中面板初始化完毕后调用。</p>
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
        // 恢复全局主题状态
        ThemeManager.getInstance().setLightMode(state.lightMode);
        // 恢复相机灵敏度
        restoreCameraSensitivity(state);
    }

    /**
     * 将所有面板的当前状态保存到持久化存储。
     * <p>在面板关闭、边界变更、或屏幕关闭时调用。</p>
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
        // 收集全局状态
        state.lightMode = ThemeManager.getInstance().isLightMode();
        collectCameraSensitivity(state);
        RtsClientUiStateStore.cache().flushIfDirty();
    }

    // ======================== 相机 ========================

    private static void restoreCameraSensitivity(RtsClientUiStateStore.UiState state) {
        CameraModule cam = cameraModule();
        if (cam != null) {
            cam.setInputSensitivity((float) state.camera.inputSensitivity);
        }
    }

    private static void collectCameraSensitivity(RtsClientUiStateStore.UiState state) {
        CameraModule cam = cameraModule();
        if (cam != null) {
            state.camera.inputSensitivity = cam.getInputSensitivity();
        }
    }

    @javax.annotation.Nullable
    private static CameraModule cameraModule() {
        try {
            return RtsClientKernel.get().module("camera");
        } catch (Exception e) {
            LOG.warn("获取 CameraModule 失败", e);
            return null;
        }
    }
}
