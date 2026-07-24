package com.rtsbuilding.rtsbuilding.client.infrastructure.di;

import com.rtsbuilding.rtsbuilding.client.application.service.ScreenCoordinator;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.client.kernel.StateEvent;
import com.rtsbuilding.rtsbuilding.client.render.RenderPipeline;

public final class CompositionRoot {
    private static CompositionRoot INSTANCE;

    private final ScreenCoordinator screenCoordinator;

    private CompositionRoot() {
        this.screenCoordinator = new ScreenCoordinator();
    }

    public static void init() {
        INSTANCE = new CompositionRoot();
    }

    public static CompositionRoot get() {
        return INSTANCE;
    }

    public RtsClientKernel kernel() {
        return RtsClientKernel.get();
    }

    public ScreenCoordinator screenCoordinator() {
        return screenCoordinator;
    }

    @SuppressWarnings("unchecked")
    public <T> T module(Class<T> type) {
        return (T) RtsClientKernel.get().module((Class) type);
    }

    public void dispatch(StateEvent event) {
        RtsClientKernel.get().dispatch(event);
    }

    public RenderPipeline renderPipeline() {
        return RtsClientKernel.get().renderPipeline();
    }
}
