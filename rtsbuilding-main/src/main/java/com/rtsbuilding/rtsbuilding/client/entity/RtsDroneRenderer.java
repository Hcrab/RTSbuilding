package com.rtsbuilding.rtsbuilding.client.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.infrastructure.module.camera.CameraModule;
import com.rtsbuilding.rtsbuilding.client.kernel.RtsClientKernel;
import com.rtsbuilding.rtsbuilding.common.entity.RtsDroneEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * RTS 无人机实体渲染器 —— 使用 Blockbench 导出的 {@link rts_drone} 模型。
 * <p>
 * 渲染时通过 {@link RtsDroneEntity#MODEL_HEIGHT_OFFSET} 将模型中心对准实体位置，
 * 并叠加正弦悬停浮动；螺旋桨旋转动画由模型 {@code setupAnim} 驱动。
 * <p>
 * 注意：实体模型坐标系 Y 轴向下为正，必须像原版 {@code MobRenderer} 一样
 * 先执行 {@code scale(-1, -1, 1)} 翻转，否则模型会上下颠倒。
 */
public class RtsDroneRenderer extends EntityRenderer<RtsDroneEntity> {

    /** 模型图层位置（与 {@link rts_drone#LAYER_LOCATION} 对应） */
    public static final ModelLayerLocation LAYER_LOCATION =
            new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "rts_drone"), "main");

    private final rts_drone<RtsDroneEntity> model;

    public RtsDroneRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.model = new rts_drone<>(context.bakeLayer(LAYER_LOCATION));
    }

    @Override
    public void render(RtsDroneEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // 所属玩家处于 RTS 摄像机视角时隐藏自己的无人机：无人机就在相机位置，会挡住视线。
        // 只影响该玩家客户端，其他玩家仍可看到。
        if (isHiddenFromOwner(entity)) {
            return;
        }
        poseStack.pushPose();
        // 实体模型坐标系 Y 轴向下为正，必须先翻转（与原版 MobRenderer 一致），否则模型上下颠倒
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        // 模型中心对准实体位置 + 悬停上下浮动
        float age = entity.tickCount + partialTick;
        float hover = (float) Math.sin(age * 0.12F) * 0.15F;
        poseStack.translate(0.0F, RtsDroneEntity.MODEL_HEIGHT_OFFSET + hover, 0.0F);

        VertexConsumer vertexConsumer = buffer.getBuffer(model.renderType(getTextureLocation(entity)));
        // limbSwingAmount 槽位传入 partialTick，供模型对上一/当前 tick 的飞行倾角插值（保证高帧率下动画流畅）
        model.setupAnim(entity, 0.0F, partialTick, age, 0.0F, 0.0F);
        model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(RtsDroneEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(RtsbuildingMod.MODID, "textures/entity/rts_drone.png");
    }

    /**
     * 判断当前查看者是否为该无人机的所属玩家且正处于 RTS 摄像机视角。
     */
    private static boolean isHiddenFromOwner(RtsDroneEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || entity.getOwnerUuid() == null) {
            return false;
        }
        if (!mc.player.getUUID().equals(entity.getOwnerUuid())) {
            return false;
        }
        CameraModule cm = RtsClientKernel.get().module(CameraModule.class);
        return cm != null && cm.getState().isEnabled();
    }
}
