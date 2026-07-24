package com.rtsbuilding.rtsbuilding.client.util.theme;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


public final class ThemeManager {
    private static final ThemeManager INSTANCE = new ThemeManager();

    private volatile boolean lightMode;
    private final List<ThemeListener> listeners = new CopyOnWriteArrayList<>();

    private ThemeManager() {
    }

    

    public static ThemeManager getInstance() {
        return INSTANCE;
    }

    

    public boolean isLightMode() {
        return lightMode;
    }

    public void setLightMode(boolean mode) {
        if (this.lightMode != mode) {
            this.lightMode = mode;
            notifyListeners();
        }
    }

    
    public void toggle() {
        setLightMode(!this.lightMode);
    }

    

    public void addListener(ThemeListener listener) {
        listeners.add(listener);
    }

    public void removeListener(ThemeListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners() {
        for (ThemeListener l : listeners) {
            l.onThemeChanged(this.lightMode);
        }
    }

    

    
    public int themeU(int frameWidth) {
        return lightMode ? frameWidth : 0;
    }

    

    
    private static final int LIGHT_TEXT_COLOR = 0xFF333333;
    
    private static final int DARK_TEXT_COLOR = 0xFFCCCCCC;
    
    private static final int LIGHT_HOVER_TEXT_COLOR = 0xFF555555;
    
    private static final int DARK_HOVER_TEXT_COLOR = 0xFFE8E8E8;

    
    public static int getTextColor() {
        return getInstance().isLightMode() ? LIGHT_TEXT_COLOR : DARK_TEXT_COLOR;
    }

    
    public static int getHoverTextColor() {
        return getInstance().isLightMode() ? LIGHT_HOVER_TEXT_COLOR : DARK_HOVER_TEXT_COLOR;
    }
}
