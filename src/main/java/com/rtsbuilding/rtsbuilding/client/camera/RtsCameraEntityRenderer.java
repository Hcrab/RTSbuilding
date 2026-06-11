package com.rtsbuilding.rtsbuilding.client.camera;


import com.rtsbuilding.rtsbuilding.entity.RtsCameraEntity;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public final class RtsCameraEntityRenderer extends EntityRenderer<RtsCameraEntity> {
    public RtsCameraEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(RtsCameraEntity entity) {
        return new ResourceLocation("minecraft", "textures/misc/empty.png");
    }

    @Override
    public boolean shouldRender(RtsCameraEntity livingEntity, net.minecraft.client.renderer.culling.Frustum camera,
            double camX, double camY, double camZ) {
        return false;
    }
}
