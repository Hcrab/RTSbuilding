package com.rtsbuilding.rtsbuilding.client.rendering.util;

import net.minecraft.client.renderer.BufferBuilder;

/** 向 1.12 POSITION_COLOR 私有缓冲追加有体感宽度的包围盒角框。 */
public final class CornerBracketRenderer {
    private static final double BRACKET_THICKNESS = 0.04D;
    private static final double THICKNESS_SCALE_DISTANCE = 16.0D;

    private enum Axis { X, Y, Z }

    private CornerBracketRenderer() {
    }

    public static void renderCornerBrackets(BufferBuilder buffer,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float red, float green, float blue, double distance) {
        renderCornerBrackets(buffer, minX, minY, minZ, maxX, maxY, maxZ,
                red, green, blue, 1.0F, distance, 1.0D);
    }

    public static void renderCornerBrackets(BufferBuilder buffer,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float red, float green, float blue, float alpha, double distance) {
        renderCornerBrackets(buffer, minX, minY, minZ, maxX, maxY, maxZ,
                red, green, blue, alpha, distance, 1.0D);
    }

    public static void renderCornerBrackets(BufferBuilder buffer,
            double minX, double minY, double minZ,
            double maxX, double maxY, double maxZ,
            float red, float green, float blue, float alpha,
            double distance, double thicknessMultiplier) {
        if (buffer == null) return;
        double thickness = BRACKET_THICKNESS * Math.max(0.25D, thicknessMultiplier)
                * Math.max(1.0D, distance / THICKNESS_SCALE_DISTANCE) * 0.5D;
        horizontalRing(buffer, minX, minZ, maxX, maxZ, minY,
                red, green, blue, alpha, thickness);
        horizontalRing(buffer, minX, minZ, maxX, maxZ, maxY,
                red, green, blue, alpha, thickness);
        segment(buffer, minX,minY,minZ,minX,maxY,minZ,red,green,blue,alpha,Axis.Y,thickness);
        segment(buffer, maxX,minY,minZ,maxX,maxY,minZ,red,green,blue,alpha,Axis.Y,thickness);
        segment(buffer, maxX,minY,maxZ,maxX,maxY,maxZ,red,green,blue,alpha,Axis.Y,thickness);
        segment(buffer, minX,minY,maxZ,minX,maxY,maxZ,red,green,blue,alpha,Axis.Y,thickness);
    }

    private static void horizontalRing(BufferBuilder buffer,
            double minX, double minZ, double maxX, double maxZ, double y,
            float red, float green, float blue, float alpha, double thickness) {
        segment(buffer,minX,y,minZ,maxX,y,minZ,red,green,blue,alpha,Axis.X,thickness);
        segment(buffer,maxX,y,minZ,maxX,y,maxZ,red,green,blue,alpha,Axis.Z,thickness);
        segment(buffer,maxX,y,maxZ,minX,y,maxZ,red,green,blue,alpha,Axis.X,thickness);
        segment(buffer,minX,y,maxZ,minX,y,minZ,red,green,blue,alpha,Axis.Z,thickness);
    }

    private static void segment(BufferBuilder buffer,
            double x1,double y1,double z1,double x2,double y2,double z2,
            float red,float green,float blue,float alpha,Axis axis,double t) {
        switch (axis) {
            case X:
                RenderingUtil.quad(buffer,x1,y1-t,z1,x1,y1+t,z1,x2,y2+t,z2,x2,y2-t,z2,red,green,blue,alpha);
                RenderingUtil.quad(buffer,x1,y1,z1-t,x1,y1,z1+t,x2,y2,z2+t,x2,y2,z2-t,red,green,blue,alpha);
                break;
            case Y:
                RenderingUtil.quad(buffer,x1,y1,z1-t,x1,y1,z1+t,x2,y2,z2+t,x2,y2,z2-t,red,green,blue,alpha);
                RenderingUtil.quad(buffer,x1-t,y1,z1,x1+t,y1,z1,x2+t,y2,z2,x2-t,y2,z2,red,green,blue,alpha);
                break;
            case Z:
                RenderingUtil.quad(buffer,x1-t,y1,z1,x1+t,y1,z1,x2+t,y2,z2,x2-t,y2,z2,red,green,blue,alpha);
                RenderingUtil.quad(buffer,x1,y1-t,z1,x1,y1+t,z1,x2,y2+t,z2,x2,y2-t,z2,red,green,blue,alpha);
                break;
            default:
                break;
        }
    }
}
