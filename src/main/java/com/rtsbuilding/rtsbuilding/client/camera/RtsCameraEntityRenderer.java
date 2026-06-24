package com.rtsbuilding.rtsbuilding.client.camera;

import com.rtsbuilding.rtsbuilding.common.entity.RtsCameraEntity;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/**
 * RTS 相机实体渲染器——渲染空实现（相机实体不可见）。
 * 必须注册到 {@link EntityRenderersEvent.RegisterRenderers} 中，
 * 否则 EntityRenderDispatcher 在尝试渲染 RtsCameraEntity 时会 NPE。
 */
public class RtsCameraEntityRenderer extends EntityRenderer<RtsCameraEntity> {

    public RtsCameraEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(RtsCameraEntity entity, Frustum frustum, double x, double y, double z) {
        // 相机实体始终不渲染
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(RtsCameraEntity entity) {
        return ResourceLocation.withDefaultNamespace("textures/misc/underwater.png");
    }
}
