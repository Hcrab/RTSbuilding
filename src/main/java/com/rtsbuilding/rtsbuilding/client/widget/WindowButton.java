package com.rtsbuilding.rtsbuilding.client.widget;


import com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel;
import com.mojang.blaze3d.systems.RenderSystem;
import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * 鑷畾涔夌獥鍙ｆ寜閽?
 * 鏀寔璐村浘缁樺埗鍜岀煝閲忕缉鏀?
 */
public class WindowButton extends AbstractButton {

    public interface OnPress {
        void onPress(WindowButton button);
    }

    private final OnPress onPress;
    private final ResourceLocation textureLocation;
    private final int textureU;
    private final int textureV;
    private final int textureWidth;
    private final int textureHeight;
    private final int hoverTextureV;  // 鎮仠鐘舵€佺殑璐村浘V鍧愭??
    private final int hoverTextureHeight;  // 鎮仠鐘舵€佺殑璐村浘楂樺害
    private final int fullTextureWidth;   // 瀹屾暣璐村浘鐨勬€诲搴?
    private final int fullTextureHeight;  // 瀹屾暣璐村浘鐨勬€婚珮搴?

    private static final int TEXT_COLOR = 0xFFD8E3EE;
    private static final int TEXT_COLOR_DISABLED = 0xFF556677;
    private static final int BUTTON_BACKGROUND = 0xDD1A232E;
    private static final int BUTTON_HOVER = 0xDD2A3442;
    private static final int BORDER_LIGHT = 0xFF647B92;
    private static final int BORDER_DARK = 0xFF0D1117;

    /**
     * When set, all WindowButton instances suppress hover/focus effects.
     * Used by RtsWindowPanel when rendering a window that is
     * covered by a higher overlapping window.
     */
    private static boolean globalSkipHover;

    /**
     * 鍒涘缓绾壊鎸夐??
     */
    public WindowButton(int x, int y, int width, int height, Component message, OnPress onPress) {
        this(x, y, width, height, message, null, 0, 0, 0, 0, onPress);
    }

    /**
     * 鍒涘缓甯﹁创鍥剧殑鎸夐挳锛堟敮鎸佹偓鍋滅姸鎬佸垏鎹??
     *
     * @param x X 鍧愭??
     * @param y Y 鍧愭??
     * @param width 鎸夐挳瀹藉??
     * @param height 鎸夐挳楂樺害
     * @param message 鎸夐挳鏂囨湰
     * @param textureLocation 璐村浘璧勬簮浣嶇疆锛坣ull 琛ㄧず浣跨敤绾壊锛?
     * @param textureU 璐村??U 鍧愭??
     * @param textureV 璐村??V 鍧愭爣锛堟甯哥姸鎬侊級
     * @param textureWidth 璐村浘瀹藉??
     * @param textureHeight 璐村浘楂樺害锛堟甯哥姸鎬侊??
     * @param hoverTextureV 鎮仠鐘舵€佺殑璐村??V 鍧愭??
     * @param hoverTextureHeight 鎮仠鐘舵€佺殑璐村浘楂樺害
     * @param fullTextureWidth 瀹屾暣璐村浘鐨勬€诲搴?
     * @param fullTextureHeight 瀹屾暣璐村浘鐨勬€婚珮搴?
     * @param onPress 鐐瑰嚮鍥炶皟
     */
    public WindowButton(int x, int y, int width, int height, Component message,
                       ResourceLocation textureLocation, int textureU, int textureV,
                       int textureWidth, int textureHeight, int hoverTextureV, int hoverTextureHeight,
                       int fullTextureWidth, int fullTextureHeight, OnPress onPress) {
        super(x, y, width, height, message);
        this.onPress = onPress;
        this.textureLocation = textureLocation;
        this.textureU = textureU;
        this.textureV = textureV;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.hoverTextureV = hoverTextureV;
        this.hoverTextureHeight = hoverTextureHeight;
        this.fullTextureWidth = fullTextureWidth;
        this.fullTextureHeight = fullTextureHeight;
    }

    /**
     * 鍒涘缓甯﹁创鍥剧殑鎸夐挳锛堝吋瀹规棫鐗堬紝鎮仠浣跨敤鐩稿悓璐村浘??
     */
    public WindowButton(int x, int y, int width, int height, Component message,
                       ResourceLocation textureLocation, int textureU, int textureV,
                       int textureWidth, int textureHeight, OnPress onPress) {
        this(x, y, width, height, message, textureLocation, textureU, textureV,
             textureWidth, textureHeight, textureV, textureHeight,
             textureWidth, textureHeight, onPress);
    }

    @Override
    public void onPress() {
        this.onPress.onPress(this);
    }

    @Override
    protected void renderWidget(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();

        if (textureLocation != null && textureWidth > 0 && textureHeight > 0) {
            // 浣跨敤璐村浘缁樺埗锛堢煝閲忕缉鏀撅級
            renderWithTexture(guiGraphics);
        } else {
            // 浣跨敤绾壊缁樺??
            renderWithSolidColor(guiGraphics);
        }

        // 璁＄畻鏂囨湰浣嶇疆锛堝眳涓??
        int textColor = this.active ? TEXT_COLOR : TEXT_COLOR_DISABLED;
        String label = RtsClientUiUtil.trimToWidth(minecraft.font, this.getMessage().getString(),
                Math.max(4, this.width - 8));
        int textWidth = minecraft.font.width(label);
        int textX = this.getX() + (this.width - textWidth) / 2;
        int textY = this.getY() + (this.height - 8) / 2;

        // 缁樺埗鏂囨湰
        if (!label.isEmpty()) {
            guiGraphics.drawString(minecraft.font, label, textX, textY, textColor, false);
        }
    }

    /**
     * 浣跨敤璐村浘缁樺埗鎸夐挳锛堟敮鎸佺煝閲忕缉鏀惧拰鎮仠鏁堟灉??
     */
    private void renderWithTexture(GuiGraphics guiGraphics) {
        // 纭繚璐村浘宸插姞杞?
        var textureManager = Minecraft.getInstance().getTextureManager();
        var texture = textureManager.getTexture(textureLocation);

        if (texture == null) {
            // 灏濊瘯瑙﹀彂璐村浘鑷姩鍔犺浇
            try {
                // 浣跨??setShaderTexture 瑙﹀彂璐村浘鍔犺??
                RenderSystem.setShaderTexture(0, textureLocation);

                // 鍐嶆灏濊瘯鑾峰彇璐村浘
                texture = textureManager.getTexture(textureLocation);

                if (texture == null) {
                    // 濡傛灉浠嶇劧鏃犳硶鍔犺浇锛岀粯鍒剁孩鑹叉柟鍧楁彁绀?
                    guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xFFFF0000);
                    return;
                }
            } catch (Exception e) {
                e.printStackTrace();
                // 濡傛灉浠嶇劧鏃犳硶鍔犺浇锛岀粯鍒剁孩鑹叉柟鍧楁彁绀?
                guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xFFFF0000);
                return;
            }
        }

        // 鏍规嵁鎮仠鐘舵€侀€夋嫨涓嶅悓鐨勮创鍥惧尯鍩燂紙琚鐩栫獥鍙ｅ己鍒朵娇鐢ㄩ潪鎮仠璐村浘??
        boolean effectiveHovered = isHovered && !globalSkipHover;
        int currentV = effectiveHovered ? hoverTextureV : textureV;
        int currentHeight = effectiveHovered ? hoverTextureHeight : textureHeight;

        // 鍚敤娣峰悎妯″紡浠ユ敮鎸侀€忔槑搴?
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
            org.lwjgl.opengl.GL11.GL_SRC_ALPHA,
            org.lwjgl.opengl.GL11.GL_ONE_MINUS_SRC_ALPHA,
            org.lwjgl.opengl.GL11.GL_ONE,
            org.lwjgl.opengl.GL11.GL_ZERO
        );

        // 缁戝畾璐村浘锛堝湪璁剧疆鍙傛暟涔嬪墠缁戝畾锛?
        RenderSystem.setShaderTexture(0, textureLocation);

        // 璁剧疆楂樿川閲忕殑绾圭悊杩囨护鍙傛暟
        // 缂╁皬杩囨护锛氫笁绾挎€ц繃婊わ紙mipmap + 绾挎€ф彃鍊硷??
        RenderSystem.texParameter(
            org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
            org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER,
            org.lwjgl.opengl.GL11.GL_LINEAR_MIPMAP_LINEAR
        );
        // 鏀惧ぇ杩囨护锛氱嚎鎬ф彃??
        RenderSystem.texParameter(
            org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
            org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER,
            org.lwjgl.opengl.GL11.GL_LINEAR
        );
        // 灏濊瘯璁剧疆鍚勫悜寮傛€ц繃婊や互鎻愰珮鏂滃悜缂╂斁璐ㄩ噺
        // 娉ㄦ剰锛氬悇鍚戝紓鎬ц繃婊ゆ槸 OpenGL 鎵╁睍锛岄渶瑕佹鏌ユ敮鎸佹儏鍐?
        try {
            // 浣跨??ARB_texture_filter_anisotropic 鎵╁睍甯搁噺
            int GL_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FE;
            int GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT = 0x84FF;

            int maxAniso = org.lwjgl.opengl.GL11.glGetInteger(GL_MAX_TEXTURE_MAX_ANISOTROPY_EXT);
            if (maxAniso > 0) {
                float anisoLevel = Math.min(16.0f, maxAniso);
                org.lwjgl.opengl.GL11.glTexParameterf(
                    org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
                    GL_TEXTURE_MAX_ANISOTROPY_EXT,
                    anisoLevel
                );
            }
        } catch (Exception e) {
            // 蹇界暐涓嶆敮鎸佺殑鍚勫悜寮傛€ц繃??
        }

        // 浣跨??PoseStack 鍙樻崲杩涜缂╂斁锛堥伩鍏嶈鍓棶棰橈??
        guiGraphics.pose().pushPose();

        // 璁＄畻缂╂斁姣斾緥锛堜娇鐢ㄦ寜閽疄闄呭昂瀵稿拰瑕佹覆鏌撶殑绾圭悊灏哄锛?
        float scaleX = (float) this.width / textureWidth;
        float scaleY = (float) this.height / textureHeight;

        // 搴旂敤缂╂斁鍙樻??
        guiGraphics.pose().translate(this.getX(), this.getY(), 0);
        guiGraphics.pose().scale(scaleX, scaleY, 1.0f);

        // 缁樺埗鍘熷灏哄鐨勭汗鐞嗭紙blit 浼氳嚜鍔ㄤ娇鐢ㄥ綋鍓嶇粦瀹氱殑绾圭悊??
        guiGraphics.blit(
            textureLocation,
            0,  // 鐩稿浜庡彉鎹㈠悗鐨勪綅??
            0,  // 鐩稿浜庡彉鎹㈠悗鐨勪綅??
            textureU,
            currentV,      // 浣跨敤瀵瑰簲鐨刅鍧愭爣
            textureWidth,  // 瑕佹覆鏌撶殑瀹藉??
            currentHeight, // 瑕佹覆鏌撶殑楂樺??
            fullTextureWidth,   // 瀹屾暣璐村浘鐨勬€诲搴?
            fullTextureHeight   // 瀹屾暣璐村浘鐨勬€婚珮搴?
        );

        // 鎭㈠鍙樻崲鐘舵??
        guiGraphics.pose().popPose();

        // 鎭㈠榛樿璁剧??
        RenderSystem.disableBlend();
        RenderSystem.texParameter(
            org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
            org.lwjgl.opengl.GL11.GL_TEXTURE_MIN_FILTER,
            org.lwjgl.opengl.GL11.GL_NEAREST
        );
        RenderSystem.texParameter(
            org.lwjgl.opengl.GL11.GL_TEXTURE_2D,
            org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER,
            org.lwjgl.opengl.GL11.GL_NEAREST
        );
    }

    /**
     * 浣跨敤绾壊缁樺埗鎸夐挳锛圧TS 娣辫壊椋庢牸??
     */
    private void renderWithSolidColor(GuiGraphics guiGraphics) {
        // 纭畾鑳屾櫙棰滆壊锛堣瑕嗙洊绐楀彛寮哄埗浣跨敤闈炴偓鍋滈鑹诧級
        int backgroundColor = (!globalSkipHover && this.isHoveredOrFocused()) ? BUTTON_HOVER : BUTTON_BACKGROUND;
        RtsClientUiUtil.drawPanelFrame(guiGraphics,
                this.getX(), this.getY(), this.width, this.height,
                backgroundColor, BORDER_LIGHT, BORDER_DARK);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
        this.defaultButtonNarrationText(narrationElementOutput);
    }

    /**
     * Sets whether all WindowButton instances should globally skip
     * hover/focus visual effects during the next render call.
     */
    public static void setGlobalSkipHover(boolean skip) {
        globalSkipHover = skip;
    }
}
