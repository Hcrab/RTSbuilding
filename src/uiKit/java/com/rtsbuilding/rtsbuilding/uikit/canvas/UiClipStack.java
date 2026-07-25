package com.rtsbuilding.rtsbuilding.uikit.canvas;

import com.rtsbuilding.rtsbuilding.uicore.geometry.UiRect;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 生产 Canvas 的平台无关嵌套裁剪状态。
 *
 * <p>每次 push 保存与父裁剪的交集，防止子控件把 scissor 扩张到窗口之外；它不调用
 * 图形 API，也不处理变换矩阵。平台 pop 后只需重新应用 {@link #current()}。</p>
 */
public final class UiClipStack {
    private final Deque<UiRect> effectiveClips = new ArrayDeque<UiRect>();

    public UiRect push(UiRect requested) {
        if (requested == null) {
            throw new IllegalArgumentException("requested clip must not be null");
        }
        UiRect effective = effectiveClips.isEmpty()
                ? requested : effectiveClips.peek().intersection(requested);
        effectiveClips.push(effective);
        return effective;
    }

    public UiRect pop() {
        if (effectiveClips.isEmpty()) {
            throw new IllegalStateException("clip stack underflow");
        }
        effectiveClips.pop();
        return effectiveClips.peek();
    }

    public UiRect current() {
        return effectiveClips.peek();
    }

    public boolean isEmpty() {
        return effectiveClips.isEmpty();
    }
}
