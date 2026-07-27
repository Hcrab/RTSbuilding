package com.rtsbuilding.rtsbuilding.client.camera;

import com.rtsbuilding.rtsbuilding.common.entity.RtsCameraEntity;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** 相机实体只提供视角锚点，不绘制模型、阴影、名称或调试包围盒。 */
@SideOnly(Side.CLIENT)
public final class RtsCameraEntityRenderer extends Render<RtsCameraEntity> {
    private static final ResourceLocation EMPTY_TEXTURE =
            new ResourceLocation("minecraft", "textures/misc/empty.png");

    public RtsCameraEntityRenderer(RenderManager renderManager) {
        super(renderManager);
        this.shadowSize = 0.0F;
    }

    @Override
    protected ResourceLocation getEntityTexture(RtsCameraEntity entity) {
        return EMPTY_TEXTURE;
    }

    @Override
    public boolean shouldRender(RtsCameraEntity entity, ICamera camera,
                                double camX, double camY, double camZ) {
        return false;
    }

    @Override
    public void doRender(RtsCameraEntity entity, double x, double y, double z,
                         float entityYaw, float partialTicks) {
        // 无形视角锚点：刻意不提交任何顶点。
    }
}
