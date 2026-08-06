package com.rtsbuilding.rtsbuilding.client.rendering.storage;

import com.rtsbuilding.rtsbuilding.client.screen.culling.RtsCullingBox;
import com.rtsbuilding.rtsbuilding.client.screen.selection.RtsSelectionBoxAnimator;
import com.rtsbuilding.rtsbuilding.client.screen.standalone.BuilderScreen;
import com.rtsbuilding.rtsbuilding.client.screen.storage.StorageBatchSelectionSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.math.AxisAlignedBB;

/**
 * 批量存储框选的世界预览。
 *
 * <p>复用所有框选共用的 {@link RtsSelectionBoxAnimator}，因此第一次点、拖动第二个角点和
 * 每次提交后的下一轮选择都保持同一套缓动手感。这里只向 RTS 的私有 line/fill buffer 追加
 * 顶点，不结束 Minecraft 的共享缓冲。</p>
 */
public final class StorageBatchSelectionRenderer {
    private static final RtsSelectionBoxAnimator ANIMATOR = new RtsSelectionBoxAnimator();

    private StorageBatchSelectionRenderer() { }

    public static void render(Minecraft minecraft, BufferBuilder lines, BufferBuilder fills) {
        GuiScreen current = minecraft == null ? null : minecraft.currentScreen;
        if (!(current instanceof BuilderScreen)) {
            ANIMATOR.clear();
            return;
        }
        StorageBatchSelectionSession.SelectionBox box =
                ((BuilderScreen) current).getStorageBatchSelection().selectionBox();
        if (box == null) {
            ANIMATOR.clear();
            return;
        }
        AxisAlignedBB animated = ANIMATOR.renderAabb(new RtsCullingBox(box.visualRevision(),
                box.min(), box.max()));
        if (animated == null) return;
        AxisAlignedBB draw = animated.grow(0.0125D);
        RenderManager manager = minecraft.getRenderManager();
        double dx = (draw.minX + draw.maxX) * 0.5D - manager.viewerPosX;
        double dy = (draw.minY + draw.maxY) * 0.5D - manager.viewerPosY;
        double dz = (draw.minZ + draw.maxZ) * 0.5D - manager.viewerPosZ;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        float alpha = box.complete() ? 0.96F : 0.78F;
        lineBox(lines, draw, 0.88F, 0.94F, 1.0F, alpha);
        fillBox(fills, draw, 0.28F, 0.56F, 1.0F, box.complete() ? 0.10F : 0.16F);
        cornerBrackets(fills, draw, distance, box.complete() ? 0.95F : 0.72F);
    }

    private static void cornerBrackets(BufferBuilder buffer, AxisAlignedBB box,
            double distance, float alpha) {
        double length = Math.max(0.18D, Math.min(0.90D, 0.16D + distance * 0.006D));
        double minX = box.minX, minY = box.minY, minZ = box.minZ;
        double maxX = box.maxX, maxY = box.maxY, maxZ = box.maxZ;
        corner(buffer, minX, minY, minZ, 1, 1, 1, length, alpha);
        corner(buffer, maxX, minY, minZ, -1, 1, 1, length, alpha);
        corner(buffer, maxX, minY, maxZ, -1, 1, -1, length, alpha);
        corner(buffer, minX, minY, maxZ, 1, 1, -1, length, alpha);
        corner(buffer, minX, maxY, minZ, 1, -1, 1, length, alpha);
        corner(buffer, maxX, maxY, minZ, -1, -1, 1, length, alpha);
        corner(buffer, maxX, maxY, maxZ, -1, -1, -1, length, alpha);
        corner(buffer, minX, maxY, maxZ, 1, -1, -1, length, alpha);
    }

    private static void corner(BufferBuilder buffer, double x, double y, double z,
            int sx, int sy, int sz, double length, float alpha) {
        line(buffer, x, y, z, x + sx * length, y, z, 0.26F, 0.70F, 1.0F, alpha);
        line(buffer, x, y, z, x, y + sy * length, z, 0.26F, 0.70F, 1.0F, alpha);
        line(buffer, x, y, z, x, y, z + sz * length, 0.26F, 0.70F, 1.0F, alpha);
    }

    private static void lineBox(BufferBuilder buffer, AxisAlignedBB box,
            float red, float green, float blue, float alpha) {
        double x1 = box.minX, y1 = box.minY, z1 = box.minZ;
        double x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;
        line(buffer,x1,y1,z1,x2,y1,z1,red,green,blue,alpha); line(buffer,x2,y1,z1,x2,y1,z2,red,green,blue,alpha);
        line(buffer,x2,y1,z2,x1,y1,z2,red,green,blue,alpha); line(buffer,x1,y1,z2,x1,y1,z1,red,green,blue,alpha);
        line(buffer,x1,y2,z1,x2,y2,z1,red,green,blue,alpha); line(buffer,x2,y2,z1,x2,y2,z2,red,green,blue,alpha);
        line(buffer,x2,y2,z2,x1,y2,z2,red,green,blue,alpha); line(buffer,x1,y2,z2,x1,y2,z1,red,green,blue,alpha);
        line(buffer,x1,y1,z1,x1,y2,z1,red,green,blue,alpha); line(buffer,x2,y1,z1,x2,y2,z1,red,green,blue,alpha);
        line(buffer,x2,y1,z2,x2,y2,z2,red,green,blue,alpha); line(buffer,x1,y1,z2,x1,y2,z2,red,green,blue,alpha);
    }

    private static void fillBox(BufferBuilder buffer, AxisAlignedBB box,
            float red, float green, float blue, float alpha) {
        double x1 = box.minX, y1 = box.minY, z1 = box.minZ;
        double x2 = box.maxX, y2 = box.maxY, z2 = box.maxZ;
        quad(buffer,x1,y1,z1,x2,y1,z1,x2,y1,z2,x1,y1,z2,red,green,blue,alpha);
        quad(buffer,x1,y2,z1,x1,y2,z2,x2,y2,z2,x2,y2,z1,red,green,blue,alpha);
        quad(buffer,x1,y1,z1,x1,y2,z1,x2,y2,z1,x2,y1,z1,red,green,blue,alpha);
        quad(buffer,x2,y1,z2,x2,y2,z2,x1,y2,z2,x1,y1,z2,red,green,blue,alpha);
        quad(buffer,x1,y1,z2,x1,y2,z2,x1,y2,z1,x1,y1,z1,red,green,blue,alpha);
        quad(buffer,x2,y1,z1,x2,y2,z1,x2,y2,z2,x2,y1,z2,red,green,blue,alpha);
    }

    private static void line(BufferBuilder buffer, double x1, double y1, double z1,
            double x2, double y2, double z2, float red, float green, float blue, float alpha) {
        buffer.pos(x1, y1, z1).color(red, green, blue, alpha).endVertex();
        buffer.pos(x2, y2, z2).color(red, green, blue, alpha).endVertex();
    }

    private static void quad(BufferBuilder buffer,
            double ax, double ay, double az, double bx, double by, double bz,
            double cx, double cy, double cz, double dx, double dy, double dz,
            float red, float green, float blue, float alpha) {
        buffer.pos(ax, ay, az).color(red, green, blue, alpha).endVertex();
        buffer.pos(bx, by, bz).color(red, green, blue, alpha).endVertex();
        buffer.pos(cx, cy, cz).color(red, green, blue, alpha).endVertex();
        buffer.pos(dx, dy, dz).color(red, green, blue, alpha).endVertex();
    }
}
