package com.rtsbuilding.rtsbuilding.client.screen.blueprint;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * 蓝图生产适配层仍共用的本地化、裁字与坐标辅助。
 *
 * <p>旧蓝图库按钮与边框已经由 Kit renderer 接管，因此本类不再保留任何绘制或主题色；
 * 玩家状态、文件、材料与网络副作用仍由各自生产 owner 持有。</p>
 */
final class BlueprintPanelUi {
    private BlueprintPanelUi() {
    }

    /**
     * Resolves a translation key to the current language text.
     */
    static String text(String key) {
        return Component.translatable(key).getString();
    }

    /**
     * Resolves a translation key with arguments to the current language text.
     */
    static String text(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    /**
     * Trims a string to fit within a pixel width and appends an ellipsis.
     *
     * <p>Minecraft's bitmap font is not monospaced across all glyphs and active
     * languages, so this uses the live {@link Font} measurement instead of a
     * character count.</p>
     */
    static String trim(Font font, String text, int maxWidth) {
        if (font == null || text == null || font.width(text) <= maxWidth) {
            return text == null ? "" : text;
        }
        String ellipsis = "...";
        int limit = Math.max(0, maxWidth - font.width(ellipsis));
        int cut = text.length();
        while (cut > 0 && font.width(text.substring(0, cut)) > limit) {
            cut--;
        }
        return text.substring(0, cut) + ellipsis;
    }
}
