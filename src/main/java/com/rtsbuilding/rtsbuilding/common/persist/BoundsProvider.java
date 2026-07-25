package com.rtsbuilding.rtsbuilding.common.persist;

/**
 * 提供面板边界信息的接口——供 {@link PersistableProperty.BoundsProperty} 使用。
 * <p>将 {@link com.rtsbuilding.rtsbuilding.client.screen.panel.RtsWindowPanel} 的边界方法抽象为接口，
 * 避免 common 层对 client 层的直接依赖。
 */
public interface BoundsProvider {
    int getWindowX();
    int getWindowY();
    int getWindowWidth();
    int getWindowHeight();
    void setBounds(int x, int y, int width, int height);
    boolean hasUserBoundsPreference();
}
