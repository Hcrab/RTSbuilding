package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import com.rtsbuilding.rtsbuilding.client.util.RtsClientUiUtil;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;
import com.rtsbuilding.rtsbuilding.uikit.theme.BlueprintLibraryStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

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
            GuiGraphics graphics,
            Font font,
            UiRect bounds,
            String label) {
        RtsClientUiUtil.drawCenteredStringNoShadow(
                graphics,
                font,
                trim(
                        font,
                        label,
                        Math.max(
                                8,
                                (int) bounds.getWidth() - 6)),
                (int) bounds.getX()
                        + (int) bounds.getWidth() / 2,
                (int) bounds.getY() + 3,
                BlueprintLibraryStyle.BUTTON_TEXT.toArgb());
    }

    static String text(String key) {
        return Component.translatable(key).getString();
    }

    static String trim(
            Font font,
            String value,
            int width) {
        return RtsClientUiUtil.trimToWidth(
                font,
                value == null ? "" : value,
                width);
    }
}
