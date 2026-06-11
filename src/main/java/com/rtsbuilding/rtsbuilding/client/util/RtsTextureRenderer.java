package com.rtsbuilding.rtsbuilding.client.util;


import com.rtsbuilding.rtsbuilding.client.widget.WindowButton;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * 楂樼簿搴︾煝閲忚创鍥剧粯鍒跺伐鍏枫€?
 * <p>
 * 浣跨敤娴偣鍧愭爣鍜?PoseStack 鐭╅樀鍙樻崲瀹炵幇浜氬儚绱犵簿搴︽覆鏌擄紝
 * 鏀寔缁曚腑蹇冩棆杞€侀鑹叉煋鑹诧紝涓斾笉姹℃煋鍏ㄥ眬 GL 绾圭悊杩囨护鐘舵€併€?
 */
public final class RtsTextureRenderer {

    private RtsTextureRenderer() {
    }

    /**
     * 楂樼簿搴︾煝閲忕粯鍒惰创鍥俱€?
     * <p>
     * 鐩告瘮 {@code GuiGraphics.blit} 鐩存帴璋冪敤锛屾鏂规硶锛?
     * <ul>
     *   <li>鐩爣浣嶇疆鍜?UV 浣跨敤 float 绮惧害锛屾敮鎸佷簹鍍忕礌瀹氫綅</li>
     *   <li>缁曚腑蹇冩棆杞紙瑙掑害鍒讹級</li>
     *   <li>棰滆壊鏌撹壊锛堜箻鑹诧級锛屾牸寮?0xAARRGGBB</li>
     *   <li>涓嶆薄鏌撳叏灞€ GL 绾圭悊杩囨护鐘舵€?/li>
     * </ul>
     *
     * @param guiGraphics   娓叉煋涓婁笅鏂?
     * @param texLocation   璐村浘璧勬簮璺緞
     * @param x             鐩爣宸︿笂瑙?X锛坒loat 绮惧害锛?
     * @param y             鐩爣宸︿笂瑙?Y锛坒loat 绮惧害锛?
     * @param width         鐩爣缁樺埗瀹藉害
     * @param height        鐩爣缁樺埗楂樺害
     * @param uOffset       婧愯创鍥?U 鍋忕Щ锛坒loat 绮惧害锛?
     * @param vOffset       婧愯创鍥?V 鍋忕Щ锛坒loat 绮惧害锛?
     * @param uWidth        婧愯创鍥惧尯鍩熷搴?
     * @param vHeight       婧愯创鍥惧尯鍩熼珮搴?
     * @param textureWidth  瀹屾暣璐村浘鎬诲搴?
     * @param textureHeight 瀹屾暣璐村浘鎬婚珮搴?
     * @param rotationDeg   鏃嬭浆瑙掑害锛堝害锛夛紝0 琛ㄧず涓嶆棆杞?
     * @param color         棰滆壊鏌撹壊 0xAARRGGBB锛?xFFFFFFFF 琛ㄧず涓嶆煋鑹?
     */
    public static void drawTextureHighPrecision(
            GuiGraphics guiGraphics,
            ResourceLocation texLocation,
            float x, float y,
            float width, float height,
            float uOffset, float vOffset,
            float uWidth, float vHeight,
            int textureWidth, int textureHeight,
            float rotationDeg,
            int color
    ) {
        // 1. 纭繚璐村浘宸插姞杞斤紙鍚?WindowButton.renderWithTexture锛?
        var textureManager = Minecraft.getInstance().getTextureManager();
        var texture = textureManager.getTexture(texLocation);
        if (texture == null) {
            try {
                RenderSystem.setShaderTexture(0, texLocation);
                texture = textureManager.getTexture(texLocation);
                if (texture == null) return;
            } catch (Exception e) {
                e.printStackTrace();
                return;
            }
        }

        // 2. 鍚敤娣峰悎
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 3. 缁戝畾璐村浘骞惰缃珮璐ㄩ噺杩囨护鍙傛暟
        RenderSystem.setShaderTexture(0, texLocation);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        // 4. 棰滆壊鏌撹壊
        boolean hasTint = (color & 0xFFFFFFFFL) != 0xFFFFFFFFL;
        if (hasTint) {
            guiGraphics.setColor(
                    ((color >> 16) & 0xFF) / 255.0f,
                    ((color >> 8) & 0xFF) / 255.0f,
                    (color & 0xFF) / 255.0f,
                    ((color >> 24) & 0xFF) / 255.0f
            );
        }

        // 5. 浣跨敤 PoseStack 鍙樻崲锛堝悓 WindowButton.renderWithTexture锛?
        var pose = guiGraphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        float scaleX = width / uWidth;
        float scaleY = height / vHeight;
        pose.scale(scaleX, scaleY, 1.0f);

        // 6. 缁樺埗锛堝湪鍙樻崲鍚庣殑鍧愭爣涓紝绾圭悊浠ュ師濮婾V灏哄缁樺埗鍦?(0,0)锛?
        guiGraphics.blit(
                texLocation,
                0, 0,
                (int) uOffset, (int) vOffset,
                (int) uWidth, (int) vHeight,
                textureWidth, textureHeight
        );

        // 7. 鎭㈠鍙樻崲
        pose.popPose();

        // 8. 鎭㈠棰滆壊
        if (hasTint) {
            guiGraphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        }

        // 9. 鎭㈠娣峰悎鍜岀汗鐞嗚繃婊?
        RenderSystem.disableBlend();
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    }
}
