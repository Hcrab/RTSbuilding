package com.rtsbuilding.rtsbuilding.platform.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.Tessellator;

/** 复刻后续版本 GUI 纹理 blit；坐标均按当前 GUI 矩阵解释。 */
@SideOnly(Side.CLIENT)
public final class GuiCompat {
    private GuiCompat() {}

    public static void drawModalRectWithCustomSizedTexture(int x, int y, float u, float v,
            int width, int height, float textureWidth, float textureHeight) {
        drawScaledCustomSizeModalRect(x, y, u, v, width, height,
                width, height, textureWidth, textureHeight);
    }

    public static void drawScaledCustomSizeModalRect(int x, int y, float u, float v,
            int sourceWidth, int sourceHeight, int width, int height,
            float textureWidth, float textureHeight) {
        float minU = u / textureWidth;
        float maxU = (u + sourceWidth) / textureWidth;
        float minV = v / textureHeight;
        float maxV = (v + sourceHeight) / textureHeight;
        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.addVertexWithUV(x, y + height, 0.0D, minU, maxV);
        tessellator.addVertexWithUV(x + width, y + height, 0.0D, maxU, maxV);
        tessellator.addVertexWithUV(x + width, y, 0.0D, maxU, minV);
        tessellator.addVertexWithUV(x, y, 0.0D, minU, minV);
        tessellator.draw();
    }
}
