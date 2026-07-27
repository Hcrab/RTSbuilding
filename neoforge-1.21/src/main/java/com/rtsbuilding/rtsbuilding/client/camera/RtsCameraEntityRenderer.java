package com.rtsbuilding.rtsbuilding.client.camera;

import com.rtsbuilding.rtsbuilding.common.entity.RtsCameraEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class RtsCameraEntityRenderer extends EntityRenderer<RtsCameraEntity> {

    public RtsCameraEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(RtsCameraEntity entity, Frustum frustum, double x, double y, double z) {
        
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(RtsCameraEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/underwater.png");
    }
}
