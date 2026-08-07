package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.BlueprintLibraryStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** 蓝图库渲染共用的无阴影文字与本地化帮助方法。 */
final class BlueprintLibraryRenderSupport {
    private BlueprintLibraryRenderSupport() {
    }

    static void drawCentered(
            GuiGraphicsExtractor graphics,
            Font font,
            UiRect bounds,
            String label) {
        RtsClientUiUtil.drawCenteredStringNoShadow(
                graphics, font,
                trim(font, label, Math.max(8, (int) bounds.getWidth() - 6)),
                (int) bounds.getX() + (int) bounds.getWidth() / 2,
                (int) bounds.getY() + 3,
                BlueprintLibraryStyle.BUTTON_TEXT.toArgb());
    }

    static String text(String key) {
        return Component.translatable(key).getString();
    }

    static String trim(Font font, String value, int width) {
        return RtsClientUiUtil.trimToWidth(font,
                value == null ? "" : value, width);
    }
}
