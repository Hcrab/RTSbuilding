package com.rtsbuilding.rtsbuilding.client.rendering.blueprint;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.RtsbuildingMod;
import com.rtsbuilding.rtsbuilding.client.controller.ClientRtsController;
import com.rtsbuilding.rtsbuilding.client.screen.blueprint.BlueprintGhostPreview;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeWorldColors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;

/**
 * Forge 蓝图虚影渲染适配入口。
 * 静止帧不重新遍历蓝图；移动只平移已上传网格，停稳后按预算刷新世界取色网格。
 */
@Mod.EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BlueprintGhostRenderer {
    private static final BlueprintGhostMeshCache CACHE = new BlueprintGhostMeshCache();
    private static volatile boolean invalidated;

    private BlueprintGhostRenderer() {
    }

    public static void renderBlueprintGhostPreview(Minecraft minecraft, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer) {
        renderBlueprintGhostPreview(minecraft, poseStack, lineBuffer, fillBuffer, null);
    }

    public static void renderBlueprintGhostPreview(Minecraft minecraft, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer, Frustum frustum) {
        if (invalidated) {
            CACHE.close();
            invalidated = false;
        }
        if (minecraft == null || minecraft.level == null
                || !(minecraft.screen instanceof BuilderScreen builderScreen)) {
            CACHE.close();
            return;
        }
        BlueprintGhostPreview preview = builderScreen.getBlueprintGhostPreview();
        if (preview.localBlocks().isEmpty() || preview.localBounds() == null) {
            CACHE.close();
            return;
        }
        ClientRtsController controller = ClientRtsController.get();
        if (!controller.hasBounds()) {
            CACHE.close();
            return;
        }
        double ax = controller.getAnchorX();
        double az = controller.getAnchorZ();
        double radius = controller.getMaxRadius();
        BlueprintPreviewClip clip = BlueprintPreviewClip.of(preview.localBounds(), preview.anchor(),
                Mth.floor(ax - radius), Mth.ceil(ax + radius) - 1,
                Mth.floor(az - radius), Mth.ceil(az + radius) - 1);
        if (clip.isEmpty()) {
            CACHE.close();
            return;
        }

        CACHE.prepare(minecraft.level, preview, clip, System.nanoTime());
        BlockPos anchor = preview.anchor();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition()
                .subtract(anchor.getX(), anchor.getY(), anchor.getZ());
        CACHE.building().advance(minecraft, camera);
        Frustum activeFrustum = frustum;
        if (activeFrustum == null && minecraft.levelRenderer != null) {
            // 现有 Forge 共享 overlay 尚未把 RenderLevelStageEvent 的 frustum 参数转发进来；
            // 直接读取当前 LevelRenderer 视锥仍保持实际模型包围盒参与剔除。
            activeFrustum = minecraft.levelRenderer.getFrustum();
        }
        UiColor color = preview.materialsReady()
                ? UiThemeWorldColors.BLUEPRINT_VALID : UiThemeWorldColors.BLUEPRINT_INVALID;
        float red = UiThemeWorldColors.red(color);
        float green = UiThemeWorldColors.green(color);
        float blue = UiThemeWorldColors.blue(color);
        CACHE.visible().draw(poseStack, anchor, activeFrustum, camera, red, green, blue,
                UiThemeWorldColors.red(UiThemeWorldColors.BLUEPRINT_MISSING),
                UiThemeWorldColors.green(UiThemeWorldColors.BLUEPRINT_MISSING),
                UiThemeWorldColors.blue(UiThemeWorldColors.BLUEPRINT_MISSING));

        AABB bounds = CACHE.visible().complete()
                ? CACHE.visible().bounds() : clip.bounds(preview.localBounds());
        if (bounds != null) {
            BlueprintGhostEnvelopeRenderer.render(poseStack, lineBuffer,
                    (int) bounds.minX + anchor.getX(), (int) bounds.minY + anchor.getY(),
                    (int) bounds.minZ + anchor.getZ(),
                    (int) bounds.maxX + anchor.getX(), (int) bounds.maxY + anchor.getY(),
                    (int) bounds.maxZ + anchor.getZ(), red, green, blue,
                    preview.truncated() ? .22F : BlueprintGhostBlockModelRenderer.GHOST_ALPHA);
        }
    }

    /** 资源重载只标记，实际 GL 释放延后到渲染线程。 */
    public static void invalidate() { invalidated = true; }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (invalidated || minecraft.level == null || !(minecraft.screen instanceof BuilderScreen)
                || !ClientRtsController.get().hasBounds()) {
            CACHE.close();
            invalidated = false;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        CACHE.close();
    }
}
