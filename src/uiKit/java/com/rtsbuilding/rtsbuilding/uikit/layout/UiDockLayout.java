package com.rtsbuilding.rtsbuilding.uikit.layout;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiInsets;
import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;

/**
 * 为顶部、底部、左侧和右侧固定 UI 预留内容区。
 *
 * <p>预留量超过屏幕时按实际可用空间依次钳制，保证返回的每个矩形都在屏内且
 * 中央内容区永不出现负尺寸。该类不决定固定栏里有哪些业务按钮。</p>
 */
public final class UiDockLayout {
    private final UiRect screen;
    private final UiInsets requested;
    private final UiInsets applied;
    private final UiRect content;

    public UiDockLayout(UiRect screen, UiInsets requested) {
        if (screen == null || screen.isEmpty()) {
            throw new IllegalArgumentException("screen must be non-empty");
        }
        if (requested == null) {
            throw new IllegalArgumentException("requested reservations must not be null");
        }
        this.screen = screen;
        this.requested = requested;

        double left = Math.min(requested.getLeft(), screen.getWidth());
        double right = Math.min(requested.getRight(), screen.getWidth() - left);
        double top = Math.min(requested.getTop(), screen.getHeight());
        double bottom = Math.min(requested.getBottom(), screen.getHeight() - top);
        this.applied = new UiInsets(left, top, right, bottom);
        this.content = screen.inset(applied);
    }

    public UiRect getScreen() { return screen; }
    public UiInsets getRequested() { return requested; }
    public UiInsets getApplied() { return applied; }
    public UiRect getContent() { return content; }

    public UiRect top() {
        return new UiRect(screen.getX(), screen.getY(), screen.getWidth(), applied.getTop());
    }

    public UiRect bottom() {
        return new UiRect(screen.getX(), screen.bottom() - applied.getBottom(),
                screen.getWidth(), applied.getBottom());
    }

    public UiRect left() {
        return new UiRect(screen.getX(), content.getY(), applied.getLeft(), content.getHeight());
    }

    public UiRect right() {
        return new UiRect(screen.right() - applied.getRight(), content.getY(),
                applied.getRight(), content.getHeight());
    }
}
