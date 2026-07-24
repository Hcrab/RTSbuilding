package com.rtsbuilding.rtsbuilding.client.presentation.panel.handler;

import com.rtsbuilding.rtsbuilding.client.infrastructure.di.CompositionRoot;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.base.api.RtsPanelApi;
import com.rtsbuilding.rtsbuilding.common.persist.PersistableProperty;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;


public final class CameraPersistenceHandler implements RtsPanelApi {

    private CameraModule cameraModule;

    
    public void initCamera(CameraModule cam) {
        this.cameraModule = cam;
    }

    

    @Override
    public List<PersistableProperty> persistableProperties() {
        List<PersistableProperty> props = new ArrayList<>();

        if (cameraModule == null) {
            
            cameraModule = CompositionRoot.get().module(CameraModule.class);
            if (cameraModule == null) return props;
        }

        
        props.add(PersistableProperty.boolField(
                "camera.playerOrbitMode",
                state -> state.camera.playerOrbitMode,
                (state, v) -> state.camera.playerOrbitMode = v,
                () -> cameraModule.isPlayerOrbitMode(),
                v -> {
                    if (v) cameraModule.enablePlayerOrbitMode();
                    else cameraModule.disablePlayerOrbitMode();
                }));

        
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

    

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }
}
