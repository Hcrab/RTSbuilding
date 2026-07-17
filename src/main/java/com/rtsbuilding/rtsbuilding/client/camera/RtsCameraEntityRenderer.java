package com.rtsbuilding.rtsbuilding.client.camera;

import com.rtsbuilding.rtsbuilding.common.entity.RtsCameraEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.NoopRenderer;

/**
 * RTS 相机实体只承担服务端权威锚点，不提交任何可见模型。
 */
public final class RtsCameraEntityRenderer extends NoopRenderer<RtsCameraEntity> {
    public RtsCameraEntityRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(RtsCameraEntity livingEntity, net.minecraft.client.renderer.culling.Frustum camera,
            double camX, double camY, double camZ) {
        return false;
    }
}
