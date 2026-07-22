package com.rtsbuilding.rtsbuilding.client.application.port;

import com.rtsbuilding.rtsbuilding.client.domain.module.capability.RenderFrameAware;

public interface RenderFramePort {
    void registerRenderPass(RenderFrameAware pass);
    void onRenderFrame(float partialTick);
}
