package com.rtsbuilding.rtsbuilding.client.camera;

import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** 相机实体只提供视角锚点，不绘制模型、阴影、名称或调试包围盒。 */
@SideOnly(Side.CLIENT)
public final class RtsCameraEntityRenderer extends Render {
    private static final ResourceLocation EMPTY_TEXTURE =
            new ResourceLocation("minecraft", "textures/misc/empty.png");

    public RtsCameraEntityRenderer(RenderManager renderManager) {
        this.renderManager = renderManager;
        this.shadowSize = 0.0F;
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity entity) {
        return EMPTY_TEXTURE;
    }

    @Override
    public void doRender(Entity entity, double x, double y, double z,
            float entityYaw, float partialTicks) {
        // 无形视角锚点：刻意不提交任何顶点。
    }
}
