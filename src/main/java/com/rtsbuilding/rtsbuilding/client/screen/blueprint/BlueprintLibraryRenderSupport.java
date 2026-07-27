package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.client.input.overlay.LegacyGuiGraphics;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.BlueprintLibraryStyle;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.I18n;

/**
 * 蓝图库生产绘制的无状态字体与本地化支持。
 *
 * <p>本类不拥有任何蓝图状态或布局，只防止顶栏、条目和详情 renderer 各复制一套
 * 无阴影居中、裁字和翻译规则。</p>
 */
final class BlueprintLibraryRenderSupport {
    private BlueprintLibraryRenderSupport() {
    }

    static void drawCentered(
            LegacyGuiGraphics graphics,
            FontRenderer font,
            UiRect bounds,
            String label) {
        String fitted = trim(font, label, Math.max(8, (int) bounds.getWidth() - 6));
        graphics.drawString(font, fitted,
                (int) bounds.getX() + ((int) bounds.getWidth() - font.getStringWidth(fitted)) / 2,
                (int) bounds.getY() + 3, BlueprintLibraryStyle.BUTTON_TEXT.toArgb(), false);
    }

    static String text(String key) {
        return I18n.format(key);
    }

    static String trim(
            FontRenderer font,
            String value,
            int width) {
        return BlueprintPanelUi.trim(font, value, width);
    }
}
