package com.rtsbuilding.rtsbuilding.uikit.theme;

/** 不依赖渲染 API 的 ARGB 颜色值。 */
public final class UiColor {
    private final int argb;
    private final UiThemeToken themeToken;
    private final UiThemeCoverageCatalog.ComponentFamily componentFamily;
    private final boolean preserveLegacyAlpha;

    public UiColor(int argb) {
        this.argb = argb;
        this.themeToken = null;
        this.componentFamily = null;
        this.preserveLegacyAlpha = false;
    }

    private UiColor(UiThemeCoverageCatalog.ComponentFamily componentFamily,
                    UiThemeToken themeToken, int legacyArgb, boolean preserveLegacyAlpha) {
        if (themeToken == null) throw new IllegalArgumentException("themeToken must not be null");
        this.argb = legacyArgb;
        this.themeToken = themeToken;
        this.componentFamily = componentFamily;
        this.preserveLegacyAlpha = preserveLegacyAlpha;
    }

    /**
     * 建立兼容旧静态 style API 的动态主题视图。
     *
     * <p>Legacy Direct 返回逐组件旧值，保证像素基线；Palette 返回核心语义 token。该对象不持有
     * 活动主题副本，因此切换后现有屏幕和已创建控件会立即读取新值。</p>
     */
    public static UiColor theme(UiThemeToken token, int legacyArgb) {
        return new UiColor(null, token, legacyArgb, false);
    }

    /** Palette 使用 token 的 RGB，但保留该组件旧值的透明度层级。 */
    public static UiColor themeWithLegacyAlpha(UiThemeToken token, int legacyArgb) {
        return new UiColor(null, token, legacyArgb, true);
    }

    public static UiColor themeComponent(UiThemeCoverageCatalog.ComponentFamily family,
                                         UiThemeToken token, int legacyArgb) {
        if (family == null) throw new IllegalArgumentException("family must not be null");
        return new UiColor(family, token, legacyArgb, false);
    }

    public static UiColor themeComponentWithLegacyAlpha(
            UiThemeCoverageCatalog.ComponentFamily family,
            UiThemeToken token, int legacyArgb) {
        if (family == null) throw new IllegalArgumentException("family must not be null");
        return new UiColor(family, token, legacyArgb, true);
    }

    public static UiColor opaque(int red, int green, int blue) {
        return argb(255, red, green, blue);
    }

    public static UiColor argb(int alpha, int red, int green, int blue) {
        requireChannel(alpha, "alpha");
        requireChannel(red, "red");
        requireChannel(green, "green");
        requireChannel(blue, "blue");
        return new UiColor(alpha << 24 | red << 16 | green << 8 | blue);
    }

    public int toArgb() {
        if (themeToken == null) return argb;
        UiThemeDefinition active = UiThemeRuntime.manager().active();
        if (active.renderMode() == UiThemeRenderMode.LEGACY_DIRECT) return argb;
        int themed = (componentFamily == null
                ? active.color(themeToken)
                : active.componentColor(componentFamily, themeToken)).toArgb();
        return preserveLegacyAlpha ? argb & 0xFF000000 | themed & 0x00FFFFFF : themed;
    }

    public int alpha() {
        return toArgb() >>> 24 & 0xFF;
    }

    public int red() {
        return toArgb() >>> 16 & 0xFF;
    }

    public int green() {
        return toArgb() >>> 8 & 0xFF;
    }

    public int blue() {
        return toArgb() & 0xFF;
    }

    public UiColor withAlpha(int alpha) {
        return argb(alpha, red(), green(), blue());
    }

    /** 固定输入即可重复的线性通道插值；进度会钳制到 0..1。 */
    public static UiColor interpolate(UiColor from, UiColor to, double progress) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("colors must not be null");
        }
        if (Double.isNaN(progress) || Double.isInfinite(progress)) {
            throw new IllegalArgumentException("progress must be finite");
        }
        double t = Math.max(0.0D, Math.min(1.0D, progress));
        return argb(
                channel(from.alpha(), to.alpha(), t),
                channel(from.red(), to.red(), t),
                channel(from.green(), to.green(), t),
                channel(from.blue(), to.blue(), t));
    }

    private static int channel(int from, int to, double progress) {
        return (int) Math.round(from + (to - from) * progress);
    }

    private static void requireChannel(int channel, String name) {
        if (channel < 0 || channel > 255) {
            throw new IllegalArgumentException(name + " must be in 0..255");
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof UiColor && toArgb() == ((UiColor) other).toArgb();
    }

    @Override
    public int hashCode() {
        return toArgb();
    }

    @Override
    public String toString() {
        return String.format("UiColor{%08X}", toArgb());
    }
}
