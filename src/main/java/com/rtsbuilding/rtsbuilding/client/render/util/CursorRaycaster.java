package com.rtsbuilding.rtsbuilding.client.render.util;

import com.rtsbuilding.rtsbuilding.client.render.ViewCaptureService;
import com.rtsbuilding.rtsbuilding.client.screen.panel.background.ScreenBackgroundPanel;
import com.rtsbuilding.rtsbuilding.client.screen.panel.downbar.DownSidebarLayoutHelper;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

/**
 * 鼠标光标→世界空间射线换算工具。
 *
 * <p>将 BuilderScreen 内世界渲染画面上的鼠标位置转换为 3D 射线，
 * 考虑了 RTS GUI 缩放、窗口布局（顶栏/右栏/底栏）、letterbox 适配以及
 * {@link ScreenBackgroundPanel#CAPTURE_SCALE} 放大偏移。
 *
 * <p>渲染、放置、破坏等操作统一使用此工具进行坐标换算，确保结果一致。
 *
 * <p>用法：
 * <pre>{@code
 * var ray = CursorRaycaster.computeCursorRay(mc, screen);
 * if (ray != null) {
 *     BlockHitResult hit = ray.raycastBlock(mc);
 *     // ...
 * }
 * }</pre>
 */
public final class CursorRaycaster {

    /** 最大射线检测距离（方块） */
    public static final double MAX_REACH = 128.0D;

    private CursorRaycaster() {}

    /**
     * 从相机实体中心计算射线，模拟 {@code mc.hitResult} 的检测方式。
     * <p>在 RTS 模式下 BuilderScreen 打开时，{@code mc.hitResult} 可能不会随相机移动更新，
     * 此方法通过显式计算相机中心射线来替代。</p>
     *
     * @param mc Minecraft 实例
     * @return 相机中心的射线（起点=相机位置，方向=相机朝向），
     *         若 level 或 cameraEntity 为空则返回 {@code null}
     */
    public static CursorRay computeCameraCenterRay(Minecraft mc) {
        if (mc.level == null || mc.getCameraEntity() == null) return null;
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        // nx=0, ny=0 表示屏幕中心，即相机朝向
        Vec3 direction = computeRayDirection(mc, 0.0D, 0.0D);
        return new CursorRay(camPos, direction);
    }

    /**
     * 从鼠标光标计算世界空间射线。
     *
     * @param mc     Minecraft 实例
     * @param screen BuilderScreen（必须）
     * @return 若鼠标不在世界画面上则返回 {@code null}；否则返回射线起点+方向
     */
    public static CursorRay computeCursorRay(Minecraft mc, BuilderScreen screen) {
        if (mc.level == null || mc.getCameraEntity() == null) {
            return null;
        }

        // 1. 虚拟尺寸（与 BuilderScreen.enterFixedRtsGuiScale 一致）
        double rtsScale = screen.getRtsGuiScale();
        var win = mc.getWindow();
        int virtualW = (int) Math.round(win.getScreenWidth() / rtsScale);
        int virtualH = (int) Math.round(win.getScreenHeight() / rtsScale);

        // 2. 鼠标坐标 → 虚拟坐标空间
        double guiMouseX = mc.mouseHandler.xpos() / rtsScale;
        double guiMouseY = mc.mouseHandler.ypos() / rtsScale;

        // 3. 世界画面内容区（与 BuilderScreen.render() / ScreenBackgroundPanel 一致）
        int contentX = 0;
        int topY = ScreenBackgroundPanel.BACKGROUND_TOP_Y;
        int downBarH = DownSidebarLayoutHelper.DOWN_BAR_HEIGHT;
        int contentY = topY + (downBarH - screen.getDownSidebarHeight()) / 2;
        int contentW = virtualW - screen.getRightSidebarWidth();
        int refContentH = virtualH - topY - downBarH;
        if (contentW <= 0 || refContentH <= 0) return null;

        // 4. 捕获画面在内容区内 letterbox + CAPTURE_SCALE 渲染位置
        int capW = ViewCaptureService.getCaptureWidth();
        int capH = ViewCaptureService.getCaptureHeight();
        if (capW <= 0 || capH <= 0) return null;

        double capAspect = (double) capW / capH;
        double destAspect = (double) contentW / refContentH;

        int renderW, renderH, renderX, renderY;
        if (capAspect > destAspect) {
            renderW = contentW;
            renderH = (int) Math.round(contentW / capAspect);
            renderX = contentX;
            renderY = contentY + (refContentH - renderH) / 2;
        } else {
            renderH = refContentH;
            renderW = (int) Math.round(refContentH * capAspect);
            renderX = contentX + (contentW - renderW) / 2;
            renderY = contentY;
        }

        double capScale = ScreenBackgroundPanel.CAPTURE_SCALE;
        renderW = (int) Math.round(renderW * capScale);
        renderH = (int) Math.round(renderH * capScale);
        renderX = contentX + (contentW - renderW) / 2;
        renderY = contentY + (refContentH - renderH) / 2;

        // 5. 鼠标在渲染帧内的相对位置 → NDC
        double relX = (guiMouseX - renderX) / renderW;
        double relY = (guiMouseY - renderY) / renderH;
        if (relX < 0.0 || relX > 1.0 || relY < 0.0 || relY > 1.0) return null;

        double nx = relX * 2.0 - 1.0;
        double ny = 1.0 - relY * 2.0;

        // 6. 射线方向
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 direction = computeRayDirection(mc, nx, ny);

        return new CursorRay(camPos, direction);
    }

    /**
     * 从 NDC 坐标 + 相机 FOV 计算射线方向向量。
     *
     * @param mc Minecraft 实例
     * @param nx NDC 水平坐标 [-1, 1]
     * @param ny NDC 垂直坐标 [-1, 1]
     * @return 归一化的射线方向
     */
    private static Vec3 computeRayDirection(Minecraft mc, double nx, double ny) {
        float yawDeg = mc.gameRenderer.getMainCamera().getYRot();
        float pitchDeg = mc.gameRenderer.getMainCamera().getXRot();
        float yaw = (float) Math.toRadians(yawDeg);
        float pitch = (float) Math.toRadians(pitchDeg);

        float sinYaw = (float) Math.sin(yaw);
        float cosYaw = (float) Math.cos(yaw);
        float sinPitch = (float) Math.sin(pitch);
        float cosPitch = (float) Math.cos(pitch);

        // 前向量（相机朝向）
        Vec3 forward = new Vec3(
                -sinYaw * cosPitch,
                -sinPitch,
                cosYaw * cosPitch).normalize();

        // 右向量
        Vec3 right = new Vec3(cosYaw, 0.0D, sinYaw).normalize();

        // 上向量 = forward × right
        Vec3 up = forward.cross(right).normalize();

        float fovY = (float) Math.toRadians(mc.options.fov().get());
        float tanY = (float) Math.tan(fovY * 0.5f);
        // 使用全帧缓冲区的宽高比（相机渲染到全屏）
        float fbAspect = (float) Math.max(1, mc.getWindow().getScreenWidth())
                / Math.max(1, mc.getWindow().getScreenHeight());
        float tanX = tanY * fbAspect;

        // 前向 + 横向偏移 + 纵向偏移 → 最终方向
        return forward.add(right.scale(-nx * tanX)).add(up.scale(ny * tanY)).normalize();
    }

    // ──────────────────────────────────────────────
    //  射线记录
    // ──────────────────────────────────────────────

    /**
     * 最近命中结果——同时进行方块和实体检测，取最近者。
     */
    public record NearestHit(
            @javax.annotation.Nullable BlockHitResult blockHit,
            @javax.annotation.Nullable EntityHitResult entityHit,
            double blockDist, double entityDist) {
        public boolean hasBlock() { return blockHit != null; }
        public boolean hasEntity() { return entityHit != null; }
    }

    /**
     * 世界空间射线——携带起点和方向，提供便捷方块检测。
     */
    public record CursorRay(Vec3 origin, Vec3 direction) {

        /**
         * 沿此射线进行方块碰撞检测。
         *
         * @param mc Minecraft 实例（需有 level 和 cameraEntity）
         * @return 方块命中结果；若未命中任何方块则返回 {@code null}
         */
        public BlockHitResult raycastBlock(Minecraft mc) {
            if (mc.level == null || mc.getCameraEntity() == null) {
                return null;
            }
            Vec3 end = origin.add(direction.scale(MAX_REACH));
            ClipContext context = new ClipContext(origin, end,
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.getCameraEntity());
            HitResult hit = mc.level.clip(context);
            if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
                return bhr;
            }
            return null;
        }

        /**
         * 沿此射线进行自定义方块碰撞检测（可指定距离）。
         *
         * @param mc      Minecraft 实例
         * @param maxDist 最大检测距离（方块）
         * @return 方块命中结果；若未命中则返回 {@code null}
         */
        public BlockHitResult raycastBlock(Minecraft mc, double maxDist) {
            if (mc.level == null || mc.getCameraEntity() == null) {
                return null;
            }
            Vec3 end = origin.add(direction.scale(maxDist));
            ClipContext context = new ClipContext(origin, end,
                    ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.getCameraEntity());
            HitResult hit = mc.level.clip(context);
            if (hit instanceof BlockHitResult bhr && hit.getType() == HitResult.Type.BLOCK) {
                return bhr;
            }
            return null;
        }

        /**
         * 沿此射线进行实体碰撞检测。
         *
         * @param mc Minecraft 实例
         * @return 实体命中结果；若未命中任何实体则返回 {@code null}
         */
        public EntityHitResult raycastEntity(Minecraft mc) {
            if (mc.level == null || mc.getCameraEntity() == null) return null;
            Vec3 end = origin.add(direction.scale(MAX_REACH));
            // 用射线起点→终点的完整包围盒 + 2格余量，确保高空RTS相机也能搜到沿线生物
            AABB search = new AABB(origin, end).inflate(2.0D);
            return ProjectileUtil.getEntityHitResult(
                    mc.getCameraEntity(),
                    origin, end, search,
                    e -> e != null && e.isAlive() && e.isPickable() && e != mc.getCameraEntity(),
                    MAX_REACH * MAX_REACH);
        }

        /**
         * 同时检测方块和实体，返回最近命中结果。
         */
        public NearestHit raycastNearest(Minecraft mc) {
            BlockHitResult bh = raycastBlock(mc);
            EntityHitResult eh = raycastEntity(mc);
            double bd = bh != null ? origin.distanceTo(bh.getLocation()) : Double.MAX_VALUE;
            double ed = eh != null ? origin.distanceTo(eh.getLocation()) : Double.MAX_VALUE;
            return new NearestHit(bh, eh, bd, ed);
        }
    }
}
