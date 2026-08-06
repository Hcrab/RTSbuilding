package com.rtsbuilding.rtsbuilding.client.theme;

import com.rtsbuilding.rtsbuilding.uikit.theme.UiColor;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeDefinition;
import com.rtsbuilding.rtsbuilding.uikit.theme.UiThemeToken;

/** 主题的可访问性硬门槛；装饰性颜色由截图评审继续把关。 */
public final class UiThemeValidator {
    public static void validateContrast(UiThemeDefinition theme) {
        requireContrast(theme, UiThemeToken.TEXT_PRIMARY, UiThemeToken.SURFACE, 4.5D);
        requireContrast(theme, UiThemeToken.TEXT_SECONDARY, UiThemeToken.SURFACE, 4.5D);
        requireContrast(theme, UiThemeToken.TEXT_ON_ACCENT, UiThemeToken.ACCENT_PRIMARY, 3.0D);
        requireContrast(theme, UiThemeToken.FOCUS_RING, UiThemeToken.SURFACE, 3.0D);
    }

    public static double contrast(UiColor first, UiColor second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05D) / (Math.min(a, b) + 0.05D);
    }

    private static void requireContrast(UiThemeDefinition theme, UiThemeToken foreground,
                                        UiThemeToken background, double minimum) {
        double ratio = contrast(theme.color(foreground), theme.color(background));
        if (ratio + 1.0E-9D < minimum) {
            throw new IllegalArgumentException("contrast " + foreground.serializedId() + "/"
                    + background.serializedId() + " is "
                    + String.format(java.util.Locale.ROOT, "%.2f", ratio)
                    + ", requires " + minimum);
        }
    }

    private static double luminance(UiColor color) {
        return 0.2126D * channel(color.red())
                + 0.7152D * channel(color.green())
                + 0.0722D * channel(color.blue());
    }

    private static double channel(int value) {
        double normalized = value / 255.0D;
        return normalized <= 0.04045D ? normalized / 12.92D
                : Math.pow((normalized + 0.055D) / 1.055D, 2.4D);
    }

    private UiThemeValidator() {
    }
}
