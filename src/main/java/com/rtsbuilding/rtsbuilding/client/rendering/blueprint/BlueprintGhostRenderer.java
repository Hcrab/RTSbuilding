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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * 蓝图预览的渲染适配入口，只协调缓存、现有 RTS 裁剪、材料配色和包围框。
 * 静止帧不遍历蓝图方块，移动帧复用局部网格；模型和缺失线框由私有网格统一持有。
 */
@EventBusSubscriber(modid = RtsbuildingMod.MODID, value = Dist.CLIENT)
public final class BlueprintGhostRenderer {
    private static final BlueprintGhostMeshCache CACHE = new BlueprintGhostMeshCache();
    private static volatile boolean invalidated;

    private BlueprintGhostRenderer() { }

    public static void renderBlueprintGhostPreview(Minecraft minecraft, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer) {
        renderBlueprintGhostPreview(minecraft, poseStack, lineBuffer, fillBuffer, null);
    }

    public static void renderBlueprintGhostPreview(Minecraft minecraft, PoseStack poseStack,
            VertexConsumer lineBuffer, VertexConsumer fillBuffer, Frustum frustum) {
        if (invalidated) { CACHE.close(); invalidated = false; }
        if (minecraft.level == null || !(minecraft.screen instanceof BuilderScreen builderScreen)) {
            CACHE.close();
            return;
        }
        BlueprintGhostPreview preview = builderScreen.getBlueprintGhostPreview();
        if (preview.localBlocks().isEmpty() || preview.localBounds() == null) {
            CACHE.close();
            return;
        }
        ClientRtsController controller = ClientRtsController.get();
        double ax = controller.getAnchorX(), az = controller.getAnchorZ(), radius = controller.getMaxRadius();
        BlueprintPreviewClip clip = BlueprintPreviewClip.of(preview.localBounds(), preview.anchor(),
                Mth.floor(ax - radius), Mth.ceil(ax + radius) - 1,
                Mth.floor(az - radius), Mth.ceil(az + radius) - 1);
        if (clip.isEmpty()) { CACHE.close(); return; }

        CACHE.prepare(minecraft.level, preview, clip, System.nanoTime());
        BlockPos anchor = preview.anchor();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition()
                .subtract(anchor.getX(), anchor.getY(), anchor.getZ());
        CACHE.building().advance(minecraft, camera);
        UiColor color = preview.materialsReady() ? UiThemeWorldColors.BLUEPRINT_VALID : UiThemeWorldColors.BLUEPRINT_INVALID;
        float r = UiThemeWorldColors.red(color), g = UiThemeWorldColors.green(color), b = UiThemeWorldColors.blue(color);
        CACHE.visible().draw(poseStack, anchor, frustum, camera, r, g, b,
                UiThemeWorldColors.red(UiThemeWorldColors.BLUEPRINT_MISSING),
                UiThemeWorldColors.green(UiThemeWorldColors.BLUEPRINT_MISSING),
                UiThemeWorldColors.blue(UiThemeWorldColors.BLUEPRINT_MISSING));

        // 首轮构建期间先画内容范围，完成后改用精确裁剪范围；不让加载看起来像没选中。
        AABB bounds = CACHE.visible().complete() ? CACHE.visible().bounds() : clip.bounds(preview.localBounds());
        if (bounds != null) {
            BlueprintGhostEnvelopeRenderer.render(poseStack, lineBuffer,
                    (int) bounds.minX + anchor.getX(), (int) bounds.minY + anchor.getY(), (int) bounds.minZ + anchor.getZ(),
                    (int) bounds.maxX + anchor.getX(), (int) bounds.maxY + anchor.getY(), (int) bounds.maxZ + anchor.getZ(),
                    r, g, b, preview.truncated() ? .22F : BlueprintGhostBlockModelRenderer.GHOST_ALPHA);
        }
    }

    /** 重载回调只标记失效，GL 资源在下一个客户端帧/tick 安全释放。 */
    public static void invalidate() { invalidated = true; }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
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
