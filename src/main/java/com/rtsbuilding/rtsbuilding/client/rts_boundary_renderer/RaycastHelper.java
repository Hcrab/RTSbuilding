package com.rtsbuilding.rtsbuilding.client.rts_boundary_renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/**
 * 射线检测辅助工具类
 * 提供鼠标射线计算和方块/实体检测功能
 */
public final class RaycastHelper {

    /**
     * 私有构造函数，防止实例化
     */
    private RaycastHelper() {
    }

    /**
     * 从相机位置向鼠标方向发射射线，检测命中的方块
     * 
     * @param minecraft Minecraft客户端实例
     * @param camPos 相机起始位置
     * @param to 射线终点位置
     * @param includeFluidSource 是否包含流体源方块
     * @return 方块命中结果，未命中则返回null
     */
    public static BlockHitResult raycastBlockFromCursor(Minecraft minecraft, Vec3 camPos, Vec3 to,
            boolean includeFluidSource) {
        ClipContext.Fluid fluidMode = includeFluidSource ? ClipContext.Fluid.SOURCE_ONLY : ClipContext.Fluid.NONE;
        HitResult hit = null;
        if (minecraft.getCameraEntity() != null) {
            if (minecraft.level != null) {
                hit = minecraft.level.clip(new ClipContext(camPos, to, ClipContext.Block.OUTLINE, fluidMode,
                        minecraft.getCameraEntity()));
            }
        }
        if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
            return bhr;
        }
        return null;
    }

    /**
     * 从相机位置向鼠标方向发射射线，检测命中的实体
     * 
     * @param minecraft Minecraft客户端实例
     * @param camPos 相机起始位置
     * @param to 射线终点位置
     * @param viewDir 视线方向向量
     * @param reach 射线最大距离
     * @return 实体命中结果，未命中则返回null
     */
    public static EntityHitResult raycastEntityFromCursor(Minecraft minecraft, Vec3 camPos, Vec3 to, Vec3 viewDir,
            double reach) {
        Entity cameraEntity = minecraft.getCameraEntity();
        if (cameraEntity == null) {
            return null;
        }

        // 构建搜索范围：以相机为中心，沿视线方向扩展
        AABB search = cameraEntity.getBoundingBox().expandTowards(viewDir.scale(reach)).inflate(1.0D);

        // 执行实体射线检测
        return ProjectileUtil.getEntityHitResult(
                cameraEntity,
                camPos,
                to,
                search,
                entity -> entity != null
                        && entity.isAlive()
                        && entity.isPickable()
                        && entity != cameraEntity
                        && entity != minecraft.player,
                reach * reach);
    }

    /**
     * 计算鼠标光标对应的射线方向向量
     * 考虑FOV、窗口尺寸、相机朝向等因素
     * 
     * @param minecraft Minecraft客户端实例
     * @return 归一化的射线方向向量
     */
    public static Vec3 computeCursorRayDirection(Minecraft minecraft) {
        // 获取鼠标屏幕坐标
        double mouseX = minecraft.mouseHandler.xpos();
        double mouseY = minecraft.mouseHandler.ypos();
        double width = Math.max(1.0D, minecraft.getWindow().getScreenWidth());
        double height = Math.max(1.0D, minecraft.getWindow().getScreenHeight());

        // 转换为NDC（归一化设备坐标），范围[-1, 1]
        double nx = (mouseX / width) * 2.0D - 1.0D;
        double ny = 1.0D - (mouseY / height) * 2.0D;

        // 获取相机朝向角度
        float yawDeg = minecraft.gameRenderer.getMainCamera().getYRot();
        float pitchDeg = minecraft.gameRenderer.getMainCamera().getXRot();
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);

        // 计算前向向量（相机正前方）
        Vec3 forward = new Vec3(
                -Math.sin(yaw) * Math.cos(pitch),
                -Math.sin(pitch),
                Math.cos(yaw) * Math.cos(pitch)).normalize();

        // 计算右向向量
        Vec3 right = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw)).normalize();

        // 计算上向向量（叉乘）
        Vec3 up = forward.cross(right).normalize();

        // 计算FOV相关的缩放因子
        double fovY = Math.toRadians(minecraft.options.fov().get());
        double tanY = Math.tan(fovY * 0.5D);
        double tanX = tanY * (width / height);

        // 组合最终射线方向：前向 + 水平偏移 + 垂直偏移
        // 注意：当前yaw基向量产生的是左向量，因此需要反转X NDC以保持屏幕右侧对应射线右侧
        return forward.add(right.scale(-nx * tanX)).add(up.scale(ny * tanY)).normalize();
    }
}
