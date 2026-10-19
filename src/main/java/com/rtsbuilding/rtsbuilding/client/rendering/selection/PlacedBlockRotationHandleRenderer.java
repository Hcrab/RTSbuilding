package com.rtsbuilding.rtsbuilding.client.rendering.selection;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.rtsbuilding.rtsbuilding.client.compat.sable.RtsSableClientSpatialCompat;
import com.rtsbuilding.rtsbuilding.client.rendering.util.RenderingUtil;
import com.rtsbuilding.rtsbuilding.client.screen.mode.PlacedBlockRotationHandles;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeWorldColors;

import java.util.List;

/**
 * 已放置方块增量旋转圆弧的世界渲染入口。
 *
 * <p>使用细分 ribbon 而不是方盒箭头：圆弧不会遮住方块，末端菱形箭头表达旋转方向，
 * 悬停时只增加宽度和亮度。渲染器不 flush 或结束 Minecraft 的共享缓冲。</p>
 */
public final class PlacedBlockRotationHandleRenderer {
    private PlacedBlockRotationHandleRenderer() {
    }

    public static void render(
            Minecraft minecraft,
            PoseStack poseStack,
            VertexConsumer lineBuffer,
            VertexConsumer fillBuffer) {
        if (!(minecraft.screen instanceof BuilderScreen screen)
                || minecraft.level == null) {
            return;
        }
        PlacedBlockRotationHandles handles = screen.getRotationHandles();
        if (!handles.hasTarget()) {
            return;
        }
        // 物理体上的圆弧几何在 plot 逻辑系里：相机射线先变换到逻辑系再悬停判定，
        // 世界系相机朝向也要换算成逻辑系方向；绘制时以目标方块为锚建立局部帧。
        Direction cameraForward = RtsSableClientSpatialCompat.toFrameDirection(
                minecraft.level, handles.targetPos(), screen.currentCameraHorizontalDirection());
        RtsSableClientSpatialCompat.Ray ray = RtsSableClientSpatialCompat.toRenderLocalRay(
                minecraft.level,
                handles.targetPos(),
                screen.currentRayOrigin(),
                screen.computeCursorRayDirection());
        handles.updateHover(
                minecraft.level,
                ray.origin(),
                ray.direction(),
                cameraForward);
        RtsSableClientSpatialCompat.renderInBlockFrame(
                minecraft.level, handles.targetPos(), poseStack, localFrame -> {
                    BlockPos anchor = localFrame ? handles.targetPos() : null;
                    for (PlacedBlockRotationHandles.ArcHandle arc
                            : handles.arcs(minecraft.level, cameraForward)) {
                        renderArc(
                                poseStack,
                                lineBuffer,
                                fillBuffer,
                                arc,
                                arc.gesture() == handles.hoveredGesture(),
                                anchor);
                    }
                });
    }

    private static void renderArc(
            PoseStack poseStack,
            VertexConsumer lineBuffer,
            VertexConsumer fillBuffer,
            PlacedBlockRotationHandles.ArcHandle arc,
            boolean hovered,
            BlockPos anchor) {
        List<Vec3> points = arc.points();
        if (points.size() < 2) {
            return;
        }
        // 局部帧下整体平移到锚点原点；相对关系不变，只影响最终顶点坐标。
        double ox = anchor == null ? 0.0D : anchor.getX();
        double oy = anchor == null ? 0.0D : anchor.getY();
        double oz = anchor == null ? 0.0D : anchor.getZ();
        Vec3 center = arc.center().subtract(ox, oy, oz);
        Color color = axisColor(arc.planeNormal(), hovered);
        double halfWidth = hovered ? 0.105D : 0.072D;
        float fillAlpha = hovered ? 0.72F : 0.38F;
        float lineAlpha = hovered ? 1.0F : 0.78F;

        for (int i = 0; i < points.size() - 1; i++) {
            Vec3 first = points.get(i).subtract(ox, oy, oz);
            Vec3 second = points.get(i + 1).subtract(ox, oy, oz);
            Vec3 firstSide = first.subtract(center).normalize().scale(halfWidth);
            Vec3 secondSide = second.subtract(center).normalize().scale(halfWidth);
            Vec3 firstOuter = first.add(firstSide);
            Vec3 firstInner = first.subtract(firstSide);
            Vec3 secondOuter = second.add(secondSide);
            Vec3 secondInner = second.subtract(secondSide);

            RenderingUtil.quad(
                    fillBuffer,
                    poseStack,
                    firstOuter.x, firstOuter.y, firstOuter.z,
                    secondOuter.x, secondOuter.y, secondOuter.z,
                    secondInner.x, secondInner.y, secondInner.z,
                    firstInner.x, firstInner.y, firstInner.z,
                    color.r(), color.g(), color.b(), fillAlpha);
            addLine(poseStack, lineBuffer, firstOuter, secondOuter, color, lineAlpha);
            addLine(poseStack, lineBuffer, firstInner, secondInner, color, lineAlpha);
        }

        Vec3 last = points.getLast().subtract(ox, oy, oz);
        Vec3 previous = points.get(points.size() - 2).subtract(ox, oy, oz);
        Vec3 tangent = last.subtract(previous).normalize();
        Vec3 side = arc.planeNormal().cross(tangent).normalize();
        Vec3 tip = last.add(tangent.scale(0.19D));
        Vec3 back = last.subtract(tangent.scale(0.13D));
        Vec3 left = last.add(side.scale(hovered ? 0.18D : 0.15D));
        Vec3 right = last.subtract(side.scale(hovered ? 0.18D : 0.15D));
        RenderingUtil.quad(
                fillBuffer,
                poseStack,
                tip.x, tip.y, tip.z,
                left.x, left.y, left.z,
                back.x, back.y, back.z,
                right.x, right.y, right.z,
                color.r(), color.g(), color.b(), hovered ? 0.90F : 0.62F);
        addLine(poseStack, lineBuffer, tip, left, color, lineAlpha);
        addLine(poseStack, lineBuffer, left, back, color, lineAlpha);
        addLine(poseStack, lineBuffer, back, right, color, lineAlpha);
        addLine(poseStack, lineBuffer, right, tip, color, lineAlpha);
    }

    private static void addLine(
            PoseStack poseStack,
            VertexConsumer buffer,
            Vec3 first,
            Vec3 second,
            Color color,
            float alpha) {
        buffer.addVertex(
                        poseStack.last(),
                        (float) first.x,
                        (float) first.y,
                        (float) first.z)
                .setColor(color.r(), color.g(), color.b(), alpha);
        buffer.addVertex(
                        poseStack.last(),
                        (float) second.x,
                        (float) second.y,
                        (float) second.z)
                .setColor(color.r(), color.g(), color.b(), alpha);
    }

    private static Color axisColor(Vec3 planeNormal, boolean hovered) {
        UiColor themed;
        if (Math.abs(planeNormal.y) > 0.5D) {
            themed = UiThemeWorldColors.AXIS_Y;
        } else if (Math.abs(planeNormal.x) > 0.5D) {
            themed = UiThemeWorldColors.AXIS_X;
        } else {
            themed = UiThemeWorldColors.AXIS_Z;
        }
        Color base = new Color(UiThemeWorldColors.red(themed), UiThemeWorldColors.green(themed),
                UiThemeWorldColors.blue(themed));
        if (!hovered) {
            return base;
        }
        return new Color(
                base.r() + (1.0F - base.r()) * 0.22F,
                base.g() + (1.0F - base.g()) * 0.22F,
                base.b() + (1.0F - base.b()) * 0.22F);
    }

    private record Color(float r, float g, float b) {
    }
}
