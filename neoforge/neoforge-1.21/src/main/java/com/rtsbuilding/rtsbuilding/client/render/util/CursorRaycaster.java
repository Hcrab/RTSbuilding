package com.rtsbuilding.rtsbuilding.client.render.util;

import com.rtsbuilding.rtsbuilding.client.presentation.panel.background.ScreenBackgroundPanel;
import com.rtsbuilding.rtsbuilding.client.presentation.panel.downbar.DownSidebarLayoutHelper;
import com.rtsbuilding.rtsbuilding.client.presentation.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.render.ViewCaptureService;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;

public final class CursorRaycaster {

    
    public static final double MAX_REACH = 128.0D;

    private CursorRaycaster() {}

    
    public static CursorRay computeCameraCenterRay(Minecraft mc) {
        if (mc.level == null || mc.getCameraEntity() == null) return null;
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        
        Vec3 direction = computeRayDirection(mc, 0.0D, 0.0D);
        return new CursorRay(camPos, direction);
    }

    
    public static CursorRay computeCursorRay(Minecraft mc, BuilderScreen screen) {
        if (mc.level == null || mc.getCameraEntity() == null) {
            return null;
        }

        
        double rtsScale = screen.getRtsGuiScale();
        var win = mc.getWindow();
        int virtualW = (int) Math.round(win.getScreenWidth() / rtsScale);
        int virtualH = (int) Math.round(win.getScreenHeight() / rtsScale);

        
        double guiMouseX = mc.mouseHandler.xpos() / rtsScale;
        double guiMouseY = mc.mouseHandler.ypos() / rtsScale;

        
        int contentX = 0;
        int topY = ScreenBackgroundPanel.BACKGROUND_TOP_Y;
        int downBarH = DownSidebarLayoutHelper.DOWN_BAR_HEIGHT;
        int contentY = topY + (downBarH - screen.getDownSidebarHeight()) / 2;
        int contentW = virtualW - screen.getRightSidebarWidth();
        int refContentH = virtualH - topY - downBarH;
        if (contentW <= 0 || refContentH <= 0) return null;

        
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

        
        double relX = (guiMouseX - renderX) / renderW;
        double relY = (guiMouseY - renderY) / renderH;
        if (relX < 0.0 || relX > 1.0 || relY < 0.0 || relY > 1.0) return null;

        double nx = relX * 2.0 - 1.0;
        double ny = 1.0 - relY * 2.0;

        
        Vec3 camPos = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 direction = computeRayDirection(mc, nx, ny);

        return new CursorRay(camPos, direction);
    }

    
    private static Vec3 computeRayDirection(Minecraft mc, double nx, double ny) {
        float yawDeg = mc.gameRenderer.getMainCamera().getYRot();
        float pitchDeg = mc.gameRenderer.getMainCamera().getXRot();
        float yaw = (float) Math.toRadians(yawDeg);
        float pitch = (float) Math.toRadians(pitchDeg);

        float sinYaw = (float) Math.sin(yaw);
        float cosYaw = (float) Math.cos(yaw);
        float sinPitch = (float) Math.sin(pitch);
        float cosPitch = (float) Math.cos(pitch);

        
        Vec3 forward = new Vec3(
                -sinYaw * cosPitch,
                -sinPitch,
                cosYaw * cosPitch).normalize();

        
        Vec3 right = new Vec3(cosYaw, 0.0D, sinYaw).normalize();

        
        Vec3 up = forward.cross(right).normalize();

        float fovY = (float) Math.toRadians(mc.options.fov().get());
        float tanY = (float) Math.tan(fovY * 0.5f);
        
        float fbAspect = (float) Math.max(1, mc.getWindow().getScreenWidth())
                / Math.max(1, mc.getWindow().getScreenHeight());
        float tanX = tanY * fbAspect;

        
        return forward.add(right.scale(-nx * tanX)).add(up.scale(ny * tanY)).normalize();
    }

    
    
    

    
    public record NearestHit(
            @javax.annotation.Nullable BlockHitResult blockHit,
            @javax.annotation.Nullable EntityHitResult entityHit,
            double blockDist, double entityDist) {
        public boolean hasBlock() { return blockHit != null; }
        public boolean hasEntity() { return entityHit != null; }
    }

    
    public record CursorRay(Vec3 origin, Vec3 direction) {

        
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

        
        public EntityHitResult raycastEntity(Minecraft mc) {
            if (mc.level == null || mc.getCameraEntity() == null) return null;
            Vec3 end = origin.add(direction.scale(MAX_REACH));
            
            AABB search = new AABB(origin, end).inflate(2.0D);
            return ProjectileUtil.getEntityHitResult(
                    mc.getCameraEntity(),
                    origin, end, search,
                    e -> e != null && e.isAlive() && e.isPickable() && e != mc.getCameraEntity(),
                    MAX_REACH * MAX_REACH);
        }

        
        public NearestHit raycastNearest(Minecraft mc) {
            BlockHitResult bh = raycastBlock(mc);
            EntityHitResult eh = raycastEntity(mc);
            double bd = bh != null ? origin.distanceTo(bh.getLocation()) : Double.MAX_VALUE;
            double ed = eh != null ? origin.distanceTo(eh.getLocation()) : Double.MAX_VALUE;
            return new NearestHit(bh, eh, bd, ed);
        }
    }
}
