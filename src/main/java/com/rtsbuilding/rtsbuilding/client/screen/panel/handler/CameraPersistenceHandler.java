package com.rtsbuilding.rtsbuilding.client.screen.panel.handler;

import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.screen.panel.base.api.RtsPanelApi;
import com.rtsbuilding.rtsbuilding.client.screen.panel.topbar.TopBarPanel;
import com.rtsbuilding.rtsbuilding.client.screen.state.RtsScreenUiStateManager;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * 相机持久化处理器——管理相机模式/目标坐标的状态持久化。
 *
 * <p>从 {@link TopBarPanel}
 * 提取，消除 TopBarPanel「管 UI 还管相机」的越权问题。
 * 本类仅实现 {@link RtsPanelApi} 的 {@link #persistableProperties()} 方法，
 * 其余方法均为空实现。</p>
 *
 * <p>注册到 {@link RtsScreenUiStateManager}，
 * 与各面板并列，统一在 BuilderScreen init/save 时加载/保存。</p>
 */
public final class CameraPersistenceHandler implements RtsPanelApi {

    private CameraModule cameraModule;

    /** 懒初始化相机模块引用（init 时注入） */
    public void initCamera(CameraModule cam) {
        this.cameraModule = cam;
    }

    // ======================== 持久化属性 ========================

    @Override
    public List<PersistableProperty> persistableProperties() {
        List<PersistableProperty> props = new ArrayList<>();

        if (cameraModule == null) {
            // 未就绪时尝试从内核获取
            cameraModule = RtsClientKernel.get().module(CameraModule.class);
            if (cameraModule == null) return props;
        }

        // 玩家环绕模式
        props.add(PersistableProperty.boolField(
                "camera.playerOrbitMode",
                state -> state.camera.playerOrbitMode,
                (state, v) -> state.camera.playerOrbitMode = v,
                () -> cameraModule.isPlayerOrbitMode(),
                v -> {
                    if (v) cameraModule.enablePlayerOrbitMode();
                    else cameraModule.disablePlayerOrbitMode();
                }));

        // 方块轨道环绕目标坐标——保存/恢复 target 坐标，避免重进时 mc.hitResult 不准
        props.add(new PersistableProperty.FieldProperty<>(
                "camera.orbitTargetX",
                state -> state.camera.orbitTargetX,
                (state, v) -> state.camera.orbitTargetX = v,
                () -> cameraModule.getState().getOrbitTargetX(),
                v -> cameraModule.getState().setOrbitTargetX(v)));
        props.add(new PersistableProperty.FieldProperty<>(
                "camera.orbitTargetY",
                state -> state.camera.orbitTargetY,
                (state, v) -> state.camera.orbitTargetY = v,
                () -> cameraModule.getState().getOrbitTargetY(),
                v -> cameraModule.getState().setOrbitTargetY(v)));
        props.add(new PersistableProperty.FieldProperty<>(
                "camera.orbitTargetZ",
                state -> state.camera.orbitTargetZ,
                (state, v) -> state.camera.orbitTargetZ = v,
                () -> cameraModule.getState().getOrbitTargetZ(),
                v -> cameraModule.getState().setOrbitTargetZ(v)));

        // 方块轨道模式开关（恢复时使用已恢复的目标坐标，不依赖 mc.hitResult）
        props.add(PersistableProperty.boolField(
                "camera.orbitMode",
                state -> state.camera.orbitMode,
                (state, v) -> state.camera.orbitMode = v,
                () -> cameraModule.isOrbitMode(),
                v -> {
                    if (!cameraModule.isPlayerOrbitMode()) {
                        if (v) {
                            cameraModule.restoreOrbitMode(
                                    cameraModule.getState().getOrbitTargetX(),
                                    cameraModule.getState().getOrbitTargetY(),
                                    cameraModule.getState().getOrbitTargetZ());
                        } else {
                            cameraModule.disableOrbitMode();
                        }
                    }
                }));

        return props;
    }

    // ======================== RtsPanelApi 空实现 ========================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // CameraPersistenceHandler 不渲染任何 UI
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }
}
